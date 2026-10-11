package dev.sebastiano.clockblocker.opus.core.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import dev.sebastiano.clockblocker.opus.core.data.AdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.PlanSurface
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowState
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowStateCalculator
import dev.sebastiano.clockblocker.opus.core.notifications.now.TravelPlanner
import dev.sebastiano.clockblocker.opus.core.notifications.now.TripRoute
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderSpec
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/** What [NowNotificationSurface.refresh] ended up showing; returned for diagnostics and tests. */
enum class NowRendering {
    Hidden,

    /** The ongoing Now notification with the classic custom views (the fallback, see [NowNotificationContent]). */
    Ongoing,

    /** The ongoing Now notification with Remote Compose content ([NowNotificationContent]). */
    RemoteOngoing,
    LiveUpdate,
}

/**
 * The ongoing "Now" notification as a [PlanSurface]: regenerated from the same plan model as the app and the
 * widgets at every advice boundary, so it can't contradict them (design complaint #3). On travel day, while a
 * protocol window is active, it becomes a Live Update in place (same notification id).
 *
 * Hidden when reminders are off, notifications are blocked, the plan isn't in progress, or the user snoozed.
 *
 * Its content is Remote Compose ([content], #49) where the device plays it. Anything that goes wrong on the way falls
 * back to the classic views, with nothing for the user to set: no supported document version, a render or build
 * that throws, a post the platform rejects, or SystemUI dropping the notification because it couldn't inflate it
 * (checked [INFLATION_CHECK] after posting; Remote Compose then stays off until the system is updated). The Live
 * Update stays a standard template: promoted notifications can't have custom content.
 */
@ContributesIntoSet(AppScope::class, binding<PlanSurface>())
@SingleIn(AppScope::class)
@Inject
class NowNotificationSurface(
    private val application: Application,
    private val planRepository: PlanRepository,
    private val settingsRepository: SettingsRepository,
    private val adviceLogRepository: AdviceLogRepository,
    private val snoozeStore: SnoozeStore,
    private val factory: NotificationFactory,
    private val capabilities: PlatformCapabilities,
    private val clock: NotificationClock,
    private val tripRepository: TripRepository,
    /** Metro optional binding: `:widget` contributes the documents; without them the classic views show. */
    private val content: NowNotificationContent = NowNotificationContent.None,
    private val workScope: NotificationWorkScope = NotificationWorkScope(),
) : PlanSurface {
    private val mutex = Mutex()

    /** Test hook: how a notification is posted (Robolectric's manager never rejects one). */
    @SuppressLint("MissingPermission") // Callers guard with areNotificationsEnabled() and catch SecurityException.
    internal var post: (Int, Notification) -> Unit = { id, notification -> NotificationManagerCompat.from(application).notify(id, notification) }

    /** Counts posts, so a pending inflation check knows whether its notification has been replaced since. */
    private var posts = 0L

    override suspend fun refresh() {
        render()
    }

    /** Posts, updates or removes the Now notification; returns what is showing now. */
    @SuppressLint("MissingPermission") // Guarded by areNotificationsEnabled(); SecurityException is caught.
    suspend fun render(): NowRendering = mutex.withLock {
        renderLocked().also { NotificationGroup.sync(application, factory) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun renderLocked(): NowRendering {
        val manager = NotificationManagerCompat.from(application)
        val settings = settingsRepository.settings.first()
        val plan = planRepository.currentPlan.first()
        val now = clock.now()
        posts++
        if (!settings.remindersEnabled || plan == null || !capabilities.areNotificationsEnabled() ||
            snoozeStore.active(now) != null
        ) {
            manager.cancel(NotificationIds.NOW)
            cancelProgressTick()
            return NowRendering.Hidden
        }
        val logs = adviceLogRepository.logs(plan.tripId).first()
        val lead = Duration.ofMinutes(settings.reminderLeadMinutes.coerceAtLeast(0).toLong())
        val state = NowStateCalculator.compute(plan, now, logs, lead) ?: run {
            manager.cancel(NotificationIds.NOW)
            cancelProgressTick()
            return NowRendering.Hidden
        }
        val progress = TravelPlanner.progress(plan, now)
        val live = progress != null && TravelPlanner.isLiveUpdateActive(plan, now)
        val redact = settings.hideLockScreenDetails
        NotificationChannels.ensureCreated(application)
        val rendering = try {
            if (live) {
                val route = tripRepository.trip(plan.tripId).first()?.let { TripRoute(it.origin.displayCode, it.destination.displayCode) }
                post(NotificationIds.NOW, factory.live(state, plan, now, progress, route, redact))
                NowRendering.LiveUpdate
            } else {
                postNow(state, plan, now, redact)
            }
        } catch (_: SecurityException) {
            cancelProgressTick()
            return NowRendering.Hidden
        }
        val channelOn = manager.getNotificationChannelCompat(ClockblockChannel.Now.id)?.importance != NotificationManagerCompat.IMPORTANCE_NONE
        // The Live Update has its own (wake-up) tick in AdviceAlarmScheduler, and the Remote Compose bar moves by
        // itself; a gap has no bar to move. A blocked Now channel shows nothing to keep current.
        val hasBar = rendering == NowRendering.Ongoing && state.headline != null && channelOn
        if (hasBar) armProgressTick(now) else cancelProgressTick()
        if (rendering == NowRendering.RemoteOngoing && channelOn) checkInflated(posts)
        return rendering
    }

    /** Posts the ongoing Now notification: with Remote Compose content when it can, else the classic views. */
    private suspend fun postNow(state: NowState, plan: JetLagPlan, now: Instant, redact: Boolean): NowRendering {
        val remote = remoteContent(state, plan, now)
        if (remote != null) {
            try {
                post(NotificationIds.NOW, factory.now(state, plan, now, redact, remote))
                return NowRendering.RemoteOngoing
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                // E.g. an IllegalArgumentException for DrawInstructions nested in the notification template.
                Log.w(TAG, "Posting the Remote Compose Now notification failed, posting the standard one", e)
            }
        }
        post(NotificationIds.NOW, factory.now(state, plan, now, redact))
        return NowRendering.Ongoing
    }

    private suspend fun remoteContent(state: NowState, plan: JetLagPlan, now: Instant): NowContentViews? {
        if (remoteFailedOnThisSystem()) return null
        return try {
            content.render(state, plan, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Rendering the Remote Compose Now content failed, posting the standard notification", e)
            null
        } catch (e: LinkageError) {
            Log.w(TAG, "Remote Compose isn't available as expected, posting the standard notification", e)
            null
        }
    }

    /**
     * SystemUI inflates notifications after `notify` returns: when it can't (say, a platform that forbids
     * `DrawInstructions` inside the notification template), it cancels the notification without telling the app.
     * [INFLATION_CHECK] later, if the Remote Compose notification posted as [posted] is gone and nothing has been
     * posted since, Remote Compose is turned off on this system build and the standard notification posted instead.
     * A user who swipes it away within those seconds turns it off too, until the next system update: rare, and the
     * notification is then the standard one, which is what it was before #49.
     */
    private fun checkInflated(posted: Long) {
        workScope.coroutineScope.launch {
            delay(INFLATION_CHECK.toMillis())
            mutex.withLock {
                if (posted != posts) return@withLock
                val showing = NotificationManagerCompat.from(application).activeNotifications.any { it.id == NotificationIds.NOW }
                if (showing) return@withLock
                Log.w(TAG, "SystemUI dropped the Remote Compose Now notification, using the standard one on this system")
                prefs.edit().putString(KEY_REMOTE_FAILED_ON, Build.FINGERPRINT).apply()
                renderLocked()
                NotificationGroup.sync(application, factory)
            }
        }
    }

    private val prefs get() = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun remoteFailedOnThisSystem(): Boolean = prefs.getString(KEY_REMOTE_FAILED_ON, null) == Build.FINGERPRINT

    /**
     * The Now notification's progress bar (how far through the current block you are) only moves when the
     * notification is rebuilt. Plan boundaries rebuild it anyway; in between, this inexact, *non-wakeup* alarm does,
     * every [PROGRESS_TICK]. A sleeping phone isn't woken for it: the alarm is delivered when the phone next wakes up
     * (say, when you pick it up), so the bar is current when you look at it and costs nothing while you don't.
     * Only the classic views need it: the Remote Compose bar and countdown run on the shade's clock.
     */
    private fun armProgressTick(now: Instant) {
        val alarms = application.getSystemService<AlarmManager>() ?: return
        alarms.set(AlarmManager.RTC, now.plus(PROGRESS_TICK).toEpochMilli(), NotificationIntents.progressTick(application))
    }

    private fun cancelProgressTick() {
        application.getSystemService<AlarmManager>()?.cancel(NotificationIntents.progressTick(application))
    }

    companion object {
        /** How often the progress bar of the standard Now notification advances while the phone is awake. */
        val PROGRESS_TICK: Duration = Duration.ofMinutes(5)

        /** How long after posting the Remote Compose notification [NowNotificationSurface] checks it is still there. */
        val INFLATION_CHECK: Duration = Duration.ofSeconds(5)

        private const val TAG = "NowNotification"
        private const val PREFS = "now_notification"

        /** The system build (`Build.FINGERPRINT`) on which SystemUI dropped the Remote Compose notification. */
        private const val KEY_REMOTE_FAILED_ON = "remote_compose_failed_on"
    }
}

/** Posts the single, replaceable alerting reminder (and the test reminder). */
@SingleIn(AppScope::class)
@Inject
class ReminderNotifier(
    private val application: Application,
    private val factory: NotificationFactory,
    private val capabilities: PlatformCapabilities,
) {
    /** What the reminder on screen was built from, while this process knows it (see [redactShowing]). */
    private class Posted(val spec: ReminderSpec, val plan: JetLagPlan)

    /**
     * Guards the reminder slot: posting, cancelling (alarms, snooze, Done, Can't do this) and redacting never
     * interleave, so a reminder the user just handled is never re-posted from a stale [lastPosted].
     */
    private val lock = Any()
    private var lastPosted: Posted? = null

    /** Returns whether the reminder was posted. [redact]: hide details on the lock screen (see [NotificationFactory.now]). */
    @SuppressLint("MissingPermission")
    fun post(spec: ReminderSpec, plan: JetLagPlan, now: Instant, redact: Boolean = false): Boolean = synchronized(lock) {
        notify(NotificationIds.REMINDER) { factory.reminder(spec, plan, now, redact) }
            .also { posted -> if (posted) lastPosted = Posted(spec, plan) }
    }

    @SuppressLint("MissingPermission")
    fun postTest(): Boolean = notify(NotificationIds.TEST) { factory.test() }

    fun cancel(): Unit = synchronized(lock) {
        lastPosted = null
        NotificationManagerCompat.from(application).cancel(NotificationIds.REMINDER)
        NotificationGroup.sync(application, factory)
    }

    /**
     * Hides the details of the reminder on screen from the lock screen, for when the user turns on "Hide details on
     * the lock screen". A reminder this process posted is rebuilt redacted at [now], without alerting again. One left
     * from an earlier process is withdrawn instead, since what it said is no longer known. A reminder that is already
     * redacted, or no longer showing, is left alone.
     */
    @SuppressLint("MissingPermission")
    fun redactShowing(now: Instant): Unit = synchronized(lock) {
        val showing = NotificationManagerCompat.from(application).activeNotifications
            .firstOrNull { it.id == NotificationIds.REMINDER } ?: return
        if (showing.notification.publicVersion != null) return
        val posted = lastPosted
        val rebuilt = posted != null && notify(NotificationIds.REMINDER) {
            factory.reminder(posted.spec, posted.plan, now, redact = true, onlyAlertOnce = true)
        }
        if (!rebuilt) cancel()
    }

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, build: () -> android.app.Notification): Boolean {
        if (!capabilities.areNotificationsEnabled()) return false
        NotificationChannels.ensureCreated(application)
        return try {
            NotificationManagerCompat.from(application).notify(id, build())
            NotificationGroup.sync(application, factory)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

/**
 * Application-lifetime scope for work started by broadcast receivers (`goAsync`). Off the main thread; one
 * failing job doesn't cancel the others.
 */
@SingleIn(AppScope::class)
class NotificationWorkScope internal constructor(val coroutineScope: CoroutineScope) {
    @Inject
    constructor() : this(CoroutineScope(SupervisorJob() + Dispatchers.Default))
}
