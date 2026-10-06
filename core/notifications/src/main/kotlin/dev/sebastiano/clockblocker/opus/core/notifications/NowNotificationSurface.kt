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
    /** Returns whether the reminder was posted. [redact]: hide details on the lock screen (see [NotificationFactory.now]). */
    @SuppressLint("MissingPermission")
    fun post(spec: ReminderSpec, plan: JetLagPlan, now: Instant, redact: Boolean = false): Boolean =
        notify(NotificationIds.REMINDER) { factory.reminder(spec, plan, now, redact) }

    @SuppressLint("MissingPermission")
    fun postTest(): Boolean = notify(NotificationIds.TEST) { factory.test() }

    fun cancel() {
        NotificationManagerCompat.from(application).cancel(NotificationIds.REMINDER)
        NotificationGroup.sync(application, factory)
    }

    /** Withdraws the reminder only if it shows details on the lock screen (posted without a redacted public version). */
    fun cancelUnredacted() {
        val showing = NotificationManagerCompat.from(application).activeNotifications
            .firstOrNull { it.id == NotificationIds.REMINDER } ?: return
        if (showing.notification.publicVersion == null) cancel()
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
