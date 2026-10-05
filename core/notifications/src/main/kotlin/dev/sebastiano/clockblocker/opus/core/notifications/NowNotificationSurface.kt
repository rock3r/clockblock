package dev.sebastiano.clockblocker.opus.core.notifications

import android.annotation.SuppressLint
import android.app.Application
import androidx.core.app.NotificationManagerCompat
import dev.sebastiano.clockblocker.opus.core.data.AdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.PlanSurface
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowStateCalculator
import dev.sebastiano.clockblocker.opus.core.notifications.now.TravelPlanner
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
) : PlanSurface {
    private val mutex = Mutex()

    override suspend fun refresh() {
        render()
    }

    /** Posts, updates or removes the Now notification; returns what is showing now. */
    @SuppressLint("MissingPermission") // Guarded by areNotificationsEnabled(); SecurityException is caught.
    suspend fun render(): NowRendering = mutex.withLock {
        val manager = NotificationManagerCompat.from(application)
        val settings = settingsRepository.settings.first()
        val plan = planRepository.currentPlan.first()
        val now = clock.now()
        if (!settings.remindersEnabled || plan == null || !capabilities.areNotificationsEnabled() ||
            snoozeStore.active(now) != null
        ) {
            manager.cancel(NotificationIds.NOW)
            return@withLock NowRendering.Hidden
        }
        val logs = adviceLogRepository.logs(plan.tripId).first()
        val lead = Duration.ofMinutes(settings.reminderLeadMinutes.coerceAtLeast(0).toLong())
        val state = NowStateCalculator.compute(plan, now, logs, lead) ?: run {
            manager.cancel(NotificationIds.NOW)
            return@withLock NowRendering.Hidden
        }
        val progress = TravelPlanner.progress(plan, now)
        val live = progress != null && TravelPlanner.isLiveUpdateActive(plan, now)
        val notification = if (live) factory.live(state, plan, now, progress) else factory.now(state, plan, now)
        NotificationChannels.ensureCreated(application)
        try {
            manager.notify(NotificationIds.NOW, notification)
        } catch (_: SecurityException) {
            return@withLock NowRendering.Hidden
        }
        if (live) NowRendering.LiveUpdate else NowRendering.Ongoing
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
    /** Returns whether the reminder was posted. */
    @SuppressLint("MissingPermission")
    fun post(spec: ReminderSpec, plan: JetLagPlan, now: Instant): Boolean =
        notify(NotificationIds.REMINDER) { factory.reminder(spec, plan, now) }

    @SuppressLint("MissingPermission")
    fun postTest(): Boolean = notify(NotificationIds.TEST) { factory.test() }

    fun cancel() = NotificationManagerCompat.from(application).cancel(NotificationIds.REMINDER)

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, build: () -> android.app.Notification): Boolean {
        if (!capabilities.areNotificationsEnabled()) return false
        NotificationChannels.ensureCreated(application)
        return try {
            NotificationManagerCompat.from(application).notify(id, build())
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
