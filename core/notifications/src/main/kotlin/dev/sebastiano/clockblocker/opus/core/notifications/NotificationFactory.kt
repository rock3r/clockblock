package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.Application
import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.notifications.now.LiveChip
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowState
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowStateCalculator
import dev.sebastiano.clockblocker.opus.core.notifications.now.TravelPlanner
import dev.sebastiano.clockblocker.opus.core.notifications.now.TravelProgress
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderKind
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderSpec
import dev.sebastiano.clockblocker.opus.core.notifications.text.ClockFormat
import dev.sebastiano.clockblocker.opus.core.notifications.text.NotificationText
import dev.sebastiano.clockblocker.opus.core.notifications.text.NotificationTextFormatter
import dev.sebastiano.clockblocker.opus.core.notifications.text.ResourceNotificationStrings
import dev.zacsweers.metro.Inject
import java.time.Duration
import java.time.Instant

/**
 * Builds every notification from pure plan state. Text comes from [NotificationTextFormatter] (current zone,
 * locale and 12/24 h read at build time, so a zone change re-renders correctly).
 */
@Inject
class NotificationFactory(
    private val application: Application,
    private val capabilities: PlatformCapabilities,
    private val clock: NotificationClock,
) {
    private val context: Context get() = application

    private fun clockFormat() = ClockFormat(clock.zone(), capabilities.locale(), capabilities.is24HourFormat())

    private fun formatter() = NotificationTextFormatter(ResourceNotificationStrings(context), clockFormat())

    /** The standard ongoing "Now" notification. */
    fun now(state: NowState, plan: JetLagPlan, now: Instant): Notification {
        val text = formatter().now(state, plan, now)
        return ongoingBuilder(OpusChannel.Now, state, text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.bigText))
            .build()
    }

    /**
     * The travel-day Live Update: a `ProgressStyle` across the travel window with advice-coloured segments,
     * take-off/landing points and a plane tracker. Promotion is only requested when the user allows it; when
     * not, the same notification is posted unpromoted (still a progress notification in the shade).
     */
    fun live(state: NowState, plan: JetLagPlan, now: Instant, progress: TravelProgress): Notification {
        val text = formatter().now(state, plan, now)
        val style = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(
                progress.blocks.map { block ->
                    NotificationCompat.ProgressStyle.Segment(block.minutes)
                        .setColor(block.type?.style?.color ?: NEUTRAL_SEGMENT_COLOR)
                },
            )
            .setProgressPoints(
                progress.marks.map { mark ->
                    NotificationCompat.ProgressStyle.Point(mark.minute).setColor(AdviceType.Flight.style.color)
                },
            )
            .setProgress(progress.progressMinutes)
            .setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_notif_flight))

        val builder = ongoingBuilder(OpusChannel.TravelLive, state, text)
            .setStyle(style)
            .setRequestPromotedOngoing(capabilities.canPostPromotedNotifications())
        state.until?.let { until ->
            when (val chip = TravelPlanner.chip(until, now)) {
                is LiveChip.Countdown -> builder
                    .setWhen(chip.until.toEpochMilli())
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                is LiveChip.EndsAt -> builder.setShortCriticalText(clockFormat().compact(chip.until))
            }
        }
        return builder.build()
    }

    /** An alerting reminder on the advice's own channel; expires by itself once it would be untrue. */
    fun reminder(spec: ReminderSpec, plan: JetLagPlan, now: Instant): Notification {
        val state = NowStateCalculator.compute(plan, now)
        val text = formatter().reminder(spec, state, plan, now)
        val advice = spec.advice
        val style = advice.type.style
        val builder = NotificationCompat.Builder(context, style.channel.id)
            .setSmallIcon(style.icon)
            .setColor(style.color)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.bigText))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setWhen(advice.start.toEpochMilli())
            .setShowWhen(true)
            .setTimeoutAfter(Duration.between(now, spec.expiresAt).toMillis().coerceAtLeast(MIN_TIMEOUT_MS))
            .setContentIntent(NotificationIntents.openPlan(context, plan.tripId))
        val actions = when (spec.kind) {
            ReminderKind.Upcoming, ReminderKind.Snoozed -> listOf(AdviceAction.CantDo, AdviceAction.Snooze)
            ReminderKind.Moment -> listOf(AdviceAction.Done, AdviceAction.Snooze)
            ReminderKind.WakeUp -> emptyList()
        }
        actions.forEach { builder.addAction(action(it, ActionSource.Reminder, plan.tripId, advice.id)) }
        return builder.build()
    }

    /** "Send test reminder" from Settings, on a real alerting channel. */
    fun test(): Notification {
        val text = formatter().test()
        return NotificationCompat.Builder(context, OpusChannel.Light.id)
            .setSmallIcon(R.drawable.ic_notif_clock)
            .setColor(BRAND_COLOR)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(Duration.ofMinutes(10).toMillis())
            .setContentIntent(NotificationIntents.openCurrentPlan(context))
            .build()
    }

    private fun ongoingBuilder(channel: OpusChannel, state: NowState, text: NotificationText): NotificationCompat.Builder {
        val headline = state.headline
        val builder = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(headline?.type?.style?.icon ?: R.drawable.ic_notif_clock)
            .setColor(headline?.type?.style?.color ?: BRAND_COLOR)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(NotificationIntents.openPlan(context, state.tripId))
        if (headline != null && headline.type != AdviceType.Flight && state.outcome == null) {
            listOf(AdviceAction.Done, AdviceAction.CantDo, AdviceAction.Snooze).forEach {
                builder.addAction(action(it, ActionSource.Now, state.tripId, headline.id))
            }
        }
        return builder
    }

    private fun action(action: AdviceAction, source: ActionSource, tripId: String, adviceId: String) =
        NotificationCompat.Action.Builder(
            when (action) {
                AdviceAction.Done -> R.drawable.ic_notif_action_done
                AdviceAction.CantDo -> R.drawable.ic_notif_action_cant_do
                AdviceAction.Snooze -> R.drawable.ic_notif_clock
            },
            context.getString(
                when (action) {
                    AdviceAction.Done -> R.string.action_done
                    AdviceAction.CantDo -> R.string.action_cant_do
                    AdviceAction.Snooze -> R.string.action_snooze
                },
            ),
            NotificationIntents.action(context, action, source, tripId, adviceId),
        ).build()

    private companion object {
        const val MIN_TIMEOUT_MS = 60_000L
    }
}
