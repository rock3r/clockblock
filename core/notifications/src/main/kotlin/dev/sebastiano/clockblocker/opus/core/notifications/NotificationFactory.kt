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
import dev.sebastiano.clockblocker.opus.core.notifications.now.TripRoute
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
 * Builds every notification from pure plan state. Text comes from [NotificationTextFormatter]: times in the plan's
 * local time (the plan day's zone, like the plan screen and the widgets, never the device's), with locale and
 * 12/24 h read at build time.
 */
@Inject
class NotificationFactory(
    private val application: Application,
    private val capabilities: PlatformCapabilities,
) {
    private val context: Context get() = application

    private fun formatter(redact: Boolean = false) =
        NotificationTextFormatter(ResourceNotificationStrings(context), capabilities.locale(), capabilities.is24HourFormat(), redact)

    /**
     * The standard ongoing "Now" notification. Its header carries the body clock ("Body 3½ h behind").
     *
     * @param redact hide details on the lock screen: the notification becomes `VISIBILITY_PRIVATE` with a public
     *   version that keeps labels and times but drops places, flight numbers and supplements.
     */
    fun now(state: NowState, plan: JetLagPlan, now: Instant, redact: Boolean = false): Notification {
        fun build(publicText: Boolean): NotificationCompat.Builder {
            val text = formatter(publicText).now(state, plan, now)
            return ongoingBuilder(ClockblockChannel.Now, state, text, redacted = redact)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.bigText))
        }
        return build(publicText = false).withPublicVersion(redact) { build(publicText = true).clearActions().build() }.build()
    }

    /**
     * The travel-day Live Update: a `ProgressStyle` across the travel window with advice-coloured segments,
     * take-off/landing points and a plane tracker. Promotion is only requested when the user allows it; when
     * not, the same notification is posted unpromoted (still a progress notification in the shade). The header
     * names the [route], the travel phase and the body clock.
     */
    fun live(
        state: NowState,
        plan: JetLagPlan,
        now: Instant,
        progress: TravelProgress,
        route: TripRoute? = null,
        redact: Boolean = false,
    ): Notification {
        // The private (full) version keeps every detail in its text; only icons follow the redaction setting.
        fun build(publicText: Boolean): NotificationCompat.Builder {
            val formatter = formatter(publicText)
            val text = formatter.now(state, plan, now).copy(
                subText = formatter.travelSubText(plan, now, route?.let { formatter.route(it.from, it.to) }),
            )
            return liveBuilder(state, now, progress, text, formatter.localClock(plan, now), redacted = redact)
        }
        return build(publicText = false).withPublicVersion(redact) { build(publicText = true).clearActions().build() }.build()
    }

    private fun liveBuilder(
        state: NowState,
        now: Instant,
        progress: TravelProgress,
        text: NotificationText,
        clock: ClockFormat,
        redacted: Boolean,
    ): NotificationCompat.Builder {
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

        val builder = ongoingBuilder(ClockblockChannel.TravelLive, state, text, redacted)
            .setStyle(style)
            .setRequestPromotedOngoing(capabilities.canPostPromotedNotifications())
        state.until?.let { until ->
            when (val chip = TravelPlanner.chip(until, now)) {
                is LiveChip.Countdown -> builder
                    .setWhen(chip.until.toEpochMilli())
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                is LiveChip.EndsAt -> builder.setShortCriticalText(clock.compact(chip.until))
            }
        }
        return builder
    }

    /**
     * An alerting reminder on the advice's own channel; expires by itself once it would be untrue.
     *
     * @param redact see [now]; a melatonin reminder also swaps its pill icon for the plain clock.
     */
    fun reminder(
        spec: ReminderSpec,
        plan: JetLagPlan,
        now: Instant,
        redact: Boolean = false,
        onlyAlertOnce: Boolean = false,
    ): Notification {
        val state = NowStateCalculator.compute(plan, now)
        fun build(publicText: Boolean) =
            reminderBuilder(spec, plan, now, formatter(publicText).reminder(spec, state, plan, now), redacted = redact)
                .setOnlyAlertOnce(onlyAlertOnce)
        return build(publicText = false).withPublicVersion(redact) { build(publicText = true).clearActions().build() }.build()
    }

    private fun reminderBuilder(
        spec: ReminderSpec,
        plan: JetLagPlan,
        now: Instant,
        text: NotificationText,
        redacted: Boolean,
    ): NotificationCompat.Builder {
        val advice = spec.advice
        val style = advice.type.style
        val builder = NotificationCompat.Builder(context, style.channel.id)
            .setSmallIcon(iconOf(advice.type, redacted))
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
            .setGroup(NotificationGroup.KEY)
        val actions = when (spec.kind) {
            ReminderKind.Upcoming, ReminderKind.Snoozed -> listOf(AdviceAction.CantDo, AdviceAction.Snooze)
            ReminderKind.Moment -> listOf(AdviceAction.Done, AdviceAction.Snooze)
            ReminderKind.WakeUp -> emptyList()
        }
        actions.forEach { builder.addAction(action(it, ActionSource.Reminder, plan.tripId, advice.id)) }
        return builder
    }

    /** "Send test reminder" from Settings, on a real alerting channel. */
    fun test(): Notification {
        val text = formatter().test()
        return NotificationCompat.Builder(context, ClockblockChannel.Light.id)
            .setSmallIcon(R.drawable.ic_notif_clock)
            .setColor(BRAND_COLOR)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(Duration.ofMinutes(10).toMillis())
            .setContentIntent(NotificationIntents.openCurrentPlan(context))
            .setGroup(NotificationGroup.KEY)
            .build()
    }

    /**
     * The summary of our explicit group: while more than one of our notifications shows, the shade bundles them
     * under this, and tapping the bundle opens the current plan (not the launcher, which is what the system's
     * own autogroup did). Silent; only the children alert.
     *
     * @param timeoutMillis when every child expires by itself, the summary expires with the last of them.
     */
    fun summary(timeoutMillis: Long?): Notification {
        val builder = NotificationCompat.Builder(context, ClockblockChannel.Now.id)
            .setSmallIcon(R.drawable.ic_notif_clock)
            .setColor(BRAND_COLOR)
            .setContentTitle(context.getString(R.string.notif_summary_title))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(NotificationGroup.KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(NotificationIntents.openCurrentPlan(context))
        timeoutMillis?.let { builder.setTimeoutAfter(it.coerceAtLeast(MIN_TIMEOUT_MS)) }
        return builder.build()
    }

    private fun ongoingBuilder(
        channel: ClockblockChannel,
        state: NowState,
        text: NotificationText,
        redacted: Boolean,
    ): NotificationCompat.Builder {
        val headline = state.headline
        val builder = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(headline?.type?.let { iconOf(it, redacted) } ?: R.drawable.ic_notif_clock)
            .setColor(headline?.type?.style?.color ?: BRAND_COLOR)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setSubText(text.subText)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(NotificationIntents.openPlan(context, state.tripId))
            // Our own group (setSilent would otherwise file it under a summary-less "silent" group, which the
            // system force-regroups with the reminders into a bundle that opens the launcher).
            .setGroup(NotificationGroup.KEY)
        if (headline != null && headline.type != AdviceType.Flight) {
            // Once something is logged, one Undo stays so an accidental lock-screen tap is one tap from reverted.
            val actions = if (state.outcome == null) {
                listOf(AdviceAction.Done, AdviceAction.CantDo, AdviceAction.Snooze)
            } else {
                listOf(AdviceAction.Undo)
            }
            actions.forEach { builder.addAction(action(it, ActionSource.Now, state.tripId, headline.id)) }
        }
        return builder
    }

    /**
     * Lock-screen redaction: private, with [public] (built only when needed) standing in on a secure keyguard. The
     * stand-in carries no actions: their set alone can name the advice (Done + Snooze is melatonin), and acting on
     * a step you can't see invites mistakes. Unlocking brings the full notification and its actions back.
     */
    private inline fun NotificationCompat.Builder.withPublicVersion(
        redact: Boolean,
        public: () -> Notification,
    ): NotificationCompat.Builder = apply {
        if (redact) setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public())
    }

    /** The advice's glyph; melatonin's pill would name it in the status bar, so redaction shows the clock. */
    private fun iconOf(type: AdviceType, redacted: Boolean): Int =
        if (redacted && type == AdviceType.Melatonin) R.drawable.ic_notif_clock else type.style.icon

    private fun action(action: AdviceAction, source: ActionSource, tripId: String, adviceId: String) =
        NotificationCompat.Action.Builder(
            when (action) {
                AdviceAction.Done -> R.drawable.ic_notif_action_done
                AdviceAction.CantDo -> R.drawable.ic_notif_action_cant_do
                AdviceAction.Snooze -> R.drawable.ic_notif_clock
                AdviceAction.Undo -> R.drawable.ic_notif_action_undo
            },
            context.getString(
                when (action) {
                    AdviceAction.Done -> R.string.action_done
                    AdviceAction.CantDo -> R.string.action_cant_do
                    AdviceAction.Snooze -> R.string.action_snooze
                    AdviceAction.Undo -> R.string.action_undo
                },
            ),
            NotificationIntents.action(context, action, source, tripId, adviceId),
        ).build()

    private companion object {
        const val MIN_TIMEOUT_MS = 60_000L
    }
}
