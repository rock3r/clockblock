package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.AlarmManager
import android.app.Application
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.getSystemService
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.PlanSurface
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.notifications.now.TravelPlanner
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderSelector
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.TransitionPlanner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/** What is armed in `AlarmManager` after a reschedule. */
data class ScheduledAlarms(
    val instants: List<Instant>,
    /** `false` when exact alarms aren't allowed and the `setWindow` fallback was used. */
    val exact: Boolean,
)

/**
 * The single source of truth for *when* anything outside the app updates (design §3.1 "one scheduler for
 * everything"). It keeps the next few plan transitions (see [TransitionPlanner]) armed as alarms; at each alarm
 * it re-derives what happens from the **current** plan, posts at most one reminder, refreshes every
 * [PlanSurface] (the Now notification, widgets…) and re-arms the chain.
 *
 * Exact alarms (`setExactAndAllowWhileIdle`) are used when `SCHEDULE_EXACT_ALARM` is granted; otherwise a
 * 10-minute `setWindow` fallback keeps things working, a bit less punctually. Reminders use absolute times so a
 * late alarm never says something false.
 *
 * The app calls [start] once (e.g. from `Application.onCreate`); system events (boot, time/zone change, app
 * update, exact-alarm permission change) arrive through [ScheduleResetReceiver][dev.sebastiano.clockblocker.opus.core.notifications.receiver.ScheduleResetReceiver].
 */
@SingleIn(AppScope::class)
@Inject
class AdviceAlarmScheduler(
    private val application: Application,
    private val planRepository: PlanRepository,
    private val settingsRepository: SettingsRepository,
    private val surfaces: Set<PlanSurface>,
    private val reminders: ReminderNotifier,
    private val snoozeStore: SnoozeStore,
    private val capabilities: PlatformCapabilities,
    private val clock: NotificationClock,
) {
    private val mutex = Mutex()
    private var started: Job? = null
    private val alarmManager: AlarmManager? get() = application.getSystemService()

    /**
     * Starts following the current plan and settings: every change re-arms the alarms and refreshes all
     * surfaces. Idempotent: later calls return the job of the first one.
     */
    @Synchronized
    fun start(scope: CoroutineScope): Job = started ?: scope.launch {
        NotificationChannels.ensureCreated(application)
        combine(planRepository.currentPlan, settingsRepository.settings, ::Pair)
            .distinctUntilChanged()
            .collectLatest { (plan, settings) ->
                arm(plan, settings)
                refreshSurfaces()
            }
    }.also { started = it }

    /** Re-arm from the latest plan and settings and refresh every surface (system events, permission grants). */
    suspend fun resync(): ScheduledAlarms {
        val alarms = arm(planRepository.currentPlan.first(), settingsRepository.settings.first())
        refreshSurfaces()
        return alarms
    }

    /**
     * Handles a fired alarm scheduled for [at]: posts the reminder due at that instant (if still true), brings back
     * a snoozed reminder, refreshes all surfaces and re-arms.
     */
    suspend fun onAlarm(at: Instant) {
        val plan = planRepository.currentPlan.first()
        val settings = settingsRepository.settings.first()
        val now = clock.now()

        val snooze = snoozeStore.current()
        val snoozeElapsed = snooze != null && !snooze.until.isAfter(now)
        if (snoozeElapsed) snoozeStore.clear()

        if (plan != null && settings.remindersEnabled && snoozeStore.active(now) == null) {
            val due = ReminderSelector.select(TransitionPlanner.transitionsAt(plan, settings, at), now)
            val snoozed = snooze?.takeIf { snoozeElapsed }?.adviceId
                ?.let { id -> plan.allAdvice.firstOrNull { it.id == id } }
                ?.let { ReminderSelector.snoozed(it, now) }
            (due ?: snoozed)?.let { reminders.post(it, plan, now) }
        }
        refreshSurfaces()
        arm(plan, settings)
    }

    /** "Snooze 15 min": quiet until then (Now hidden, reminder dismissed), then remind about [adviceId] again. */
    suspend fun snooze(adviceId: String?) {
        snoozeStore.snooze(clock.now(), adviceId)
        reminders.cancel()
        resync()
    }

    /** Refreshes every registered [PlanSurface]; one failing surface doesn't stop the others. */
    suspend fun refreshSurfaces() = coroutineScope {
        surfaces.forEach { surface -> launch { runCatching { surface.refresh() } } }
    }

    /** Arms a one-off test reminder [delay] from now through the same alarm path as real reminders. */
    fun scheduleTestReminder(delay: Duration) {
        val manager = alarmManager ?: return
        set(manager, clock.now().plus(delay), NotificationIntents.testAlarm(application))
    }

    /** Computes and arms the next alarms (plus a pending snooze and the Live Update progress tick). */
    internal suspend fun arm(plan: JetLagPlan?, settings: AppSettings): ScheduledAlarms = mutex.withLock {
        val now = clock.now()
        val planned = plan?.let { TransitionPlanner.upcoming(it, settings, now, PLAN_ALARMS) }.orEmpty().map { it.at }
        val extra = listOfNotNull(
            snoozeStore.active(now)?.until,
            plan?.takeIf { TravelPlanner.isLiveUpdateActive(it, now) }
                ?.let { now.plus(LIVE_UPDATE_TICK).truncatedTo(ChronoUnit.MINUTES) },
        )
        val instants = (planned + extra).distinct().sorted().take(MAX_ALARMS)
        val exact = capabilities.canScheduleExactAlarms()
        val manager = alarmManager ?: return@withLock ScheduledAlarms(emptyList(), exact)
        instants.forEachIndexed { slot, at -> set(manager, at, NotificationIntents.alarm(application, slot, at), exact) }
        for (slot in instants.size until MAX_ALARMS) {
            NotificationIntents.existingAlarm(application, slot)?.let {
                manager.cancel(it)
                it.cancel()
            }
        }
        ScheduledAlarms(instants, exact)
    }

    private fun set(
        manager: AlarmManager,
        at: Instant,
        operation: android.app.PendingIntent,
        exact: Boolean = capabilities.canScheduleExactAlarms(),
    ) {
        val millis = at.toEpochMilli()
        if (exact) {
            try {
                AlarmManagerCompat.setExactAndAllowWhileIdle(manager, AlarmManager.RTC_WAKEUP, millis, operation)
                return
            } catch (_: SecurityException) {
                // Permission revoked between the check and the call: fall through to the inexact path.
            }
        }
        manager.setWindow(AlarmManager.RTC_WAKEUP, millis, FALLBACK_WINDOW.toMillis(), operation)
    }

    companion object {
        /** Plan transitions held at once; the chain re-arms at every alarm. */
        const val PLAN_ALARMS: Int = TransitionPlanner.DEFAULT_LIMIT

        /** Plan transitions + snooze + Live Update tick. */
        const val MAX_ALARMS: Int = PLAN_ALARMS + 2

        /** The platform minimum window for inexact alarms (shorter windows are stretched to this anyway). */
        val FALLBACK_WINDOW: Duration = Duration.ofMinutes(10)

        /** How often the travel-day progress bar advances (the status chip counts down by itself). */
        val LIVE_UPDATE_TICK: Duration = Duration.ofMinutes(15)
    }
}
