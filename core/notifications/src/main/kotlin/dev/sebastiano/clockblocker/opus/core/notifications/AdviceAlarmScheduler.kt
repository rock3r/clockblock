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
import dev.sebastiano.clockblocker.opus.core.notifications.text.BodyClockHeader
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
 * Exact alarms (`setExactAndAllowWhileIdle`) are used when `SCHEDULE_EXACT_ALARM` is granted. Otherwise each instant
 * is armed twice: a 10-minute `setWindow` fallback (punctual enough while the phone is awake, but held by Doze) and
 * an allow-while-idle backstop (`setAndAllowWhileIdle`, which Doze lets through, up to about an hour late). The
 * first delivery handles the instant and re-arms the chain, which replaces the other; a delivery of an instant
 * already handled ([HandledAlarms]) posts nothing. Reminders use absolute times so a late alarm never says something
 * false.
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
    private val handledAlarms: HandledAlarms,
) {
    private val mutex = Mutex()

    /**
     * Serializes posting a reminder with redacting it for lock-screen privacy, and the post re-reads the setting
     * inside it: a reminder is never posted unredacted after privacy was turned on.
     */
    private val reminderMutex = Mutex()
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
                // With privacy on, a reminder on screen without a redacted public version is rebuilt redacted (the
                // Now notification, refreshed below, follows the setting by itself). Checked on every emission, not
                // only when the setting flips, so a process that died before redacting reconciles on its next start.
                if (settings.hideLockScreenDetails) reminderMutex.withLock { reminders.redactShowing(clock.now()) }
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

        // The window alarm and its backstop carry the same instant: only the first delivery may remind.
        val firstDelivery = handledAlarms.claim(at)

        val snooze = snoozeStore.current()
        val snoozeElapsed = snooze != null && !snooze.until.isAfter(now)
        if (snoozeElapsed) snoozeStore.clear()

        if (firstDelivery && plan != null && settings.remindersEnabled && snoozeStore.active(now) == null) {
            val due = ReminderSelector.select(TransitionPlanner.transitionsAt(plan, settings, at), now)
            val snoozed = snooze?.takeIf { snoozeElapsed }?.adviceId
                ?.let { id -> plan.allAdvice.firstOrNull { it.id == id } }
                ?.let { ReminderSelector.snoozed(it, now) }
            (due ?: snoozed)?.let { spec ->
                reminderMutex.withLock {
                    reminders.post(spec, plan, now, redact = settingsRepository.settings.first().hideLockScreenDetails)
                }
            }
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

    /**
     * Computes and arms the next alarms, plus a pending snooze, the Live Update progress tick, the next change of
     * the Now notification's body-clock header and the widget dial's next refresh (a block coming into view, or a
     * moment stopping being due).
     */
    internal suspend fun arm(plan: JetLagPlan?, settings: AppSettings): ScheduledAlarms = mutex.withLock {
        val now = clock.now()
        val planned = plan?.let { TransitionPlanner.upcoming(it, settings, now, PLAN_ALARMS) }.orEmpty().map { it.at }
        val extra = listOfNotNull(
            snoozeStore.active(now)?.until,
            plan?.takeIf { TravelPlanner.isLiveUpdateActive(it, now) }
                ?.let { now.plus(LIVE_UPDATE_TICK).truncatedTo(ChronoUnit.MINUTES) },
            plan?.takeIf { settings.remindersEnabled }?.let { BodyClockHeader.nextChange(it, now) },
            plan?.let { TransitionPlanner.nextDialRefresh(it, now) },
        )
        val instants = (planned + extra).distinct().sorted().take(MAX_ALARMS)
        val exact = capabilities.canScheduleExactAlarms()
        val manager = alarmManager ?: return@withLock ScheduledAlarms(emptyList(), exact)
        var allExact = exact
        instants.forEachIndexed { slot, at ->
            val armedExact = set(manager, at, NotificationIntents.alarm(application, slot, at), exact)
            if (armedExact) {
                cancel(manager, NotificationIntents.existingBackstop(application, slot))
            } else {
                allExact = false
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, at.toEpochMilli(), NotificationIntents.backstop(application, slot, at),
                )
            }
        }
        for (slot in instants.size until MAX_ALARMS) {
            cancel(manager, NotificationIntents.existingAlarm(application, slot))
            cancel(manager, NotificationIntents.existingBackstop(application, slot))
        }
        ScheduledAlarms(instants, allExact)
    }

    private fun cancel(manager: AlarmManager, operation: android.app.PendingIntent?) {
        operation ?: return
        manager.cancel(operation)
        operation.cancel()
    }

    /** Arms [operation] at [at]; returns whether it was armed exact (`false`: the 10-minute window fallback). */
    private fun set(
        manager: AlarmManager,
        at: Instant,
        operation: android.app.PendingIntent,
        exact: Boolean = capabilities.canScheduleExactAlarms(),
    ): Boolean {
        val millis = at.toEpochMilli()
        if (exact) {
            try {
                AlarmManagerCompat.setExactAndAllowWhileIdle(manager, AlarmManager.RTC_WAKEUP, millis, operation)
                return true
            } catch (_: SecurityException) {
                // Permission revoked between the check and the call: fall through to the inexact path.
            }
        }
        manager.setWindow(AlarmManager.RTC_WAKEUP, millis, FALLBACK_WINDOW.toMillis(), operation)
        return false
    }

    companion object {
        /** Plan transitions held at once; the chain re-arms at every alarm. */
        const val PLAN_ALARMS: Int = TransitionPlanner.DEFAULT_LIMIT

        /** Plan transitions + snooze + Live Update tick + body-clock header change + the widget dial's next refresh. */
        const val MAX_ALARMS: Int = PLAN_ALARMS + 4

        /** The platform minimum window for inexact alarms (shorter windows are stretched to this anyway). */
        val FALLBACK_WINDOW: Duration = Duration.ofMinutes(10)

        /** How often the travel-day progress bar advances (the status chip counts down by itself). */
        val LIVE_UPDATE_TICK: Duration = Duration.ofMinutes(15)
    }
}
