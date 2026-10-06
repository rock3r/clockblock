package dev.sebastiano.clockblocker.opus.core.notifications

import android.annotation.SuppressLint
import android.app.Application
import androidx.core.app.NotificationManagerCompat
import dev.sebastiano.clockblocker.opus.core.data.AdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.PlanSurface
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/** What [NowNotificationSurface.refresh] ended up showing; returned for diagnostics and tests. */
enum class NowRendering { Hidden, Ongoing, LiveUpdate }

/**
 * The ongoing "Now" notification as a [PlanSurface]: regenerated from the same plan model as the app and the
 * widgets at every advice boundary, so it can't contradict them (design complaint #3). On travel day, while a
 * protocol window is active, it becomes a Live Update in place (same notification id).
 *
 * Hidden when reminders are off, notifications are blocked, the plan isn't in progress, or the user snoozed.
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
) : PlanSurface {
    private val mutex = Mutex()

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
        if (!settings.remindersEnabled || plan == null || !capabilities.areNotificationsEnabled() ||
            snoozeStore.active(now) != null
        ) {
            manager.cancel(NotificationIds.NOW)
            return NowRendering.Hidden
        }
        val logs = adviceLogRepository.logs(plan.tripId).first()
        val lead = Duration.ofMinutes(settings.reminderLeadMinutes.coerceAtLeast(0).toLong())
        val state = NowStateCalculator.compute(plan, now, logs, lead) ?: run {
            manager.cancel(NotificationIds.NOW)
            return NowRendering.Hidden
        }
        val progress = TravelPlanner.progress(plan, now)
        val live = progress != null && TravelPlanner.isLiveUpdateActive(plan, now)
        val redact = settings.hideLockScreenDetails
        val notification = if (live) {
            val route = tripRepository.trip(plan.tripId).first()?.let { TripRoute(it.origin.displayCode, it.destination.displayCode) }
            factory.live(state, plan, now, progress, route, redact)
        } else {
            factory.now(state, plan, now, redact)
        }
        NotificationChannels.ensureCreated(application)
        try {
            manager.notify(NotificationIds.NOW, notification)
        } catch (_: SecurityException) {
            return NowRendering.Hidden
        }
        return if (live) NowRendering.LiveUpdate else NowRendering.Ongoing
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
