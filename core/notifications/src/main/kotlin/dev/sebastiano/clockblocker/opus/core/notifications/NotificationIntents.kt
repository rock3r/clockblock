package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.sebastiano.clockblocker.opus.core.model.DeepLinks
import dev.sebastiano.clockblocker.opus.core.notifications.receiver.AdviceActionReceiver
import dev.sebastiano.clockblocker.opus.core.notifications.receiver.AdviceAlarmReceiver
import java.time.Instant

/** Notification ids. One ongoing Now, one (replaceable) reminder: never a stack of stale history. */
object NotificationIds {
    const val NOW: Int = 1001
    const val REMINDER: Int = 1002
    const val TEST: Int = 1003
    const val SUMMARY: Int = 1004
}

/** Buttons on Now / reminder notifications, handled by [AdviceActionReceiver]. [Undo] forgets a logged outcome. */
enum class AdviceAction { Done, CantDo, Snooze, Undo }

/** Where an action button lives; keeps their PendingIntents distinct. */
internal enum class ActionSource { Now, Reminder }

/**
 * Every Intent / PendingIntent this module creates. Actions are derived from the application id at runtime
 * (never hard-coded), and all PendingIntents are immutable and explicit.
 */
object NotificationIntents {
    const val EXTRA_AT: String = "at_epoch_millis"
    const val EXTRA_TRIP_ID: String = "trip_id"
    const val EXTRA_ADVICE_ID: String = "advice_id"

    /** Request code of the delayed test reminder alarm (plan alarms use 0 until [AdviceAlarmScheduler.MAX_ALARMS]). */
    internal const val TEST_ALARM_REQUEST_CODE: Int = 999

    fun alarmAction(context: Context): String = "${context.packageName}.notifications.ADVICE_ALARM"
    fun testAction(context: Context): String = "${context.packageName}.notifications.TEST_REMINDER"
    fun adviceAction(context: Context, action: AdviceAction): String =
        "${context.packageName}.notifications.${action.name.uppercase()}"

    internal fun alarmIntent(context: Context, at: Instant): Intent =
        Intent(context, AdviceAlarmReceiver::class.java)
            .setAction(alarmAction(context))
            .putExtra(EXTRA_AT, at.toEpochMilli())

    /** PendingIntent for alarm [slot]; extras are refreshed on every reschedule. */
    internal fun alarm(context: Context, slot: Int, at: Instant): PendingIntent = PendingIntent.getBroadcast(
        context,
        slot,
        alarmIntent(context, at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The existing PendingIntent of [slot], if armed. */
    internal fun existingAlarm(context: Context, slot: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        slot,
        Intent(context, AdviceAlarmReceiver::class.java).setAction(alarmAction(context)),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Request codes of the allow-while-idle backstops, one per alarm slot (`BACKSTOP_REQUEST_CODE_BASE + slot`).
     * Clear of the plan slots (0 until [AdviceAlarmScheduler.MAX_ALARMS]) and the widget Done code (100).
     */
    internal const val BACKSTOP_REQUEST_CODE_BASE: Int = 200

    /**
     * The backstop of alarm [slot]: the same broadcast for the same instant, armed with `setAndAllowWhileIdle` when
     * exact alarms aren't allowed, so Doze can't hold the reminder until its next maintenance window.
     */
    internal fun backstop(context: Context, slot: Int, at: Instant): PendingIntent = PendingIntent.getBroadcast(
        context,
        BACKSTOP_REQUEST_CODE_BASE + slot,
        alarmIntent(context, at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The existing backstop of [slot], if armed. */
    internal fun existingBackstop(context: Context, slot: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        BACKSTOP_REQUEST_CODE_BASE + slot,
        Intent(context, AdviceAlarmReceiver::class.java).setAction(alarmAction(context)),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun testAlarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        TEST_ALARM_REQUEST_CODE,
        Intent(context, AdviceAlarmReceiver::class.java).setAction(testAction(context)),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Request code of the Now notification's progress tick. */
    internal const val PROGRESS_TICK_REQUEST_CODE: Int = 998

    fun progressTickAction(context: Context): String = "${context.packageName}.notifications.NOW_PROGRESS_TICK"

    /** Re-renders the Now notification so its progress bar keeps up (see [NowNotificationSurface]). */
    internal fun progressTick(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        PROGRESS_TICK_REQUEST_CODE,
        Intent(context, AdviceAlarmReceiver::class.java).setAction(progressTickAction(context)),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun actionIntent(context: Context, action: AdviceAction, tripId: String, adviceId: String): Intent =
        Intent(context, AdviceActionReceiver::class.java)
            .setAction(adviceAction(context, action))
            .putExtra(EXTRA_TRIP_ID, tripId)
            .putExtra(EXTRA_ADVICE_ID, adviceId)

    internal fun action(
        context: Context,
        action: AdviceAction,
        source: ActionSource,
        tripId: String,
        adviceId: String,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        source.ordinal * 10 + action.ordinal,
        actionIntent(context, action, tripId, adviceId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Opens the plan via its deep link ([DeepLinks.plan]), restricted to this app. */
    fun openPlan(context: Context, tripId: String): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(Intent.ACTION_VIEW, Uri.parse(DeepLinks.plan(tripId)))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Opens the current plan ([DeepLinks.CURRENT_PLAN]): for notifications not tied to one trip (the test reminder). */
    fun openCurrentPlan(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(Intent.ACTION_VIEW, Uri.parse(DeepLinks.CURRENT_PLAN))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Request code of the widgets' Done broadcast (notification actions use `source * 10 + action`, < 30). */
    private const val WIDGET_DONE_REQUEST_CODE: Int = 100

    /**
     * Done from a home-screen widget: the same broadcast as the notification's Done button, so the outcome is logged
     * once and every surface (Now notification, widgets) refreshes through the scheduler.
     *
     * The advice is part of the token's identity (a data URI; extras don't count), so rendering another advice's
     * button, e.g. the picker previews' demo plan, can never rewrite the extras of a live widget's button.
     */
    fun widgetDone(context: Context, tripId: String, adviceId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        WIDGET_DONE_REQUEST_CODE,
        actionIntent(context, AdviceAction.Done, tripId, adviceId)
            .setData(Uri.Builder().scheme(WIDGET_DONE_SCHEME).appendPath(tripId).appendPath(adviceId).build()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val WIDGET_DONE_SCHEME = "widget-done"
}
