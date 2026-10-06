package dev.sebastiano.clockblocker.opus.core.notifications.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.sebastiano.clockblocker.opus.core.data.AdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.notifications.AdviceAction
import dev.sebastiano.clockblocker.opus.core.notifications.AdviceAlarmScheduler
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationChannels
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationIntents
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationWorkScope
import dev.sebastiano.clockblocker.opus.core.notifications.ReminderNotifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.BroadcastReceiverKey
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Runs [block] off the main thread while keeping the broadcast alive (`goAsync`). `goAsync()` is `null` when
 * `onReceive` is invoked directly (tests), which is fine.
 */
internal fun BroadcastReceiver.launchAsync(workScope: NotificationWorkScope, block: suspend () -> Unit) {
    val pending: BroadcastReceiver.PendingResult? = goAsync()
    workScope.coroutineScope.launch {
        try {
            block()
        } finally {
            pending?.finish()
        }
    }
}

/** Fired by the alarms [AdviceAlarmScheduler] arms (and by the delayed test reminder). Not exported. */
@ContributesIntoMap(AppScope::class, binding<BroadcastReceiver>())
@BroadcastReceiverKey
@Inject
class AdviceAlarmReceiver(
    private val scheduler: AdviceAlarmScheduler,
    private val reminders: ReminderNotifier,
    private val workScope: NotificationWorkScope,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationIntents.alarmAction(context) -> {
                val at = Instant.ofEpochMilli(intent.getLongExtra(NotificationIntents.EXTRA_AT, 0L))
                launchAsync(workScope) { scheduler.onAlarm(at) }
            }
            NotificationIntents.testAction(context) -> launchAsync(workScope) { reminders.postTest() }
        }
    }
}

/**
 * Re-arms everything after events that invalidate alarms or rendered times: reboot, wall-clock or zone change
 * (landing!), app update, exact-alarm permission change, and locale change (channel names, copy).
 */
@ContributesIntoMap(AppScope::class, binding<BroadcastReceiver>())
@BroadcastReceiverKey
@Inject
class ScheduleResetReceiver(
    private val scheduler: AdviceAlarmScheduler,
    private val workScope: NotificationWorkScope,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        if (intent.action == Intent.ACTION_LOCALE_CHANGED) NotificationChannels.ensureCreated(context)
        launchAsync(workScope) { scheduler.resync() }
    }

    companion object {
        val HANDLED_ACTIONS: Set<String> = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED, // "android.intent.action.TIME_SET"
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_LOCALE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

/** Done / Can't do this / Snooze 15 / Undo on the Now notification and reminders. Not exported. */
@ContributesIntoMap(AppScope::class, binding<BroadcastReceiver>())
@BroadcastReceiverKey
@Inject
class AdviceActionReceiver(
    private val adviceLogRepository: AdviceLogRepository,
    private val scheduler: AdviceAlarmScheduler,
    private val reminders: ReminderNotifier,
    private val workScope: NotificationWorkScope,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = AdviceAction.entries.firstOrNull { NotificationIntents.adviceAction(context, it) == intent.action }
            ?: return
        val tripId = intent.getStringExtra(NotificationIntents.EXTRA_TRIP_ID) ?: return
        val adviceId = intent.getStringExtra(NotificationIntents.EXTRA_ADVICE_ID) ?: return
        launchAsync(workScope) {
            when (action) {
                AdviceAction.Done -> log(tripId, adviceId, AdviceOutcome.Done)
                AdviceAction.CantDo -> log(tripId, adviceId, AdviceOutcome.CantDo)
                AdviceAction.Snooze -> scheduler.snooze(adviceId)
                AdviceAction.Undo -> undo(tripId, adviceId)
            }
        }
    }

    private suspend fun log(tripId: String, adviceId: String, outcome: AdviceOutcome) {
        adviceLogRepository.log(tripId, adviceId, outcome)
        reminders.cancel()
        // Same source of truth: the Now notification (and widgets) re-render with the logged outcome.
        scheduler.refreshSurfaces()
    }

    /** The notification only offers Undo for a logged outcome, so forgetting it restores the state before. */
    private suspend fun undo(tripId: String, adviceId: String) {
        adviceLogRepository.clear(tripId, adviceId)
        scheduler.refreshSurfaces()
    }
}
