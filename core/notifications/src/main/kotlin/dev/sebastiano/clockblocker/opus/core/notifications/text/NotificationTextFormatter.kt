package dev.sebastiano.clockblocker.opus.core.notifications.text

import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowState
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderKind
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderSpec
import java.time.Instant
import java.time.ZoneId

/** Rendered text of a notification. */
data class NotificationText(
    val title: String,
    val text: String,
    /** The main text again in a second zone (destination, or home once you're there); `null` if same offset. */
    val secondary: String? = null,
    val tip: String? = null,
) {
    /** Expanded (BigText) body: text, then the secondary zone line, then the tip. */
    val bigText: String get() = listOfNotNull(text, secondary, tip).joinToString("\n")
}

/**
 * Pure composition of notification copy from plan state. Primary times are in the user's current zone
 * ([clock]); a secondary line repeats them in the destination zone (or the home zone once local time already
 * matches the destination), so "Times show local time plus a secondary zone" holds on every surface.
 */
class NotificationTextFormatter(
    private val strings: NotificationStrings,
    private val clock: ClockFormat,
) {

    /** The ongoing Now notification. */
    fun now(state: NowState, plan: JetLagPlan, now: Instant): NotificationText {
        val secondary = secondaryZone(plan, now)
        val headline = state.headline
        return NotificationText(
            title = headline?.let(::titleOf) ?: strings.nothingNow(),
            text = nowSentence(state, now, clock),
            secondary = secondary?.let { strings.inZone(ClockFormat.cityOf(it.zone.id), nowSentence(state, now, it)) },
            tip = headline?.let { strings.tip(it.type) },
        )
    }

    /** An alerting reminder. [state] is the Now state at fire time (used by wake-ups to say what's next). */
    fun reminder(spec: ReminderSpec, state: NowState?, plan: JetLagPlan, now: Instant): NotificationText {
        val advice = spec.advice
        val label = titleOf(advice)
        val secondary = secondaryZone(plan, now)
        val also = spec.alsoStarting.takeIf { it.isNotEmpty() }
            ?.let { list -> strings.also(list.joinToString(", ") { strings.label(it.type) }) }
        return when (spec.kind) {
            ReminderKind.Upcoming -> NotificationText(
                title = if (now.isBefore(advice.start)) {
                    strings.upcomingTitle(label, clock.time(advice.start, now))
                } else {
                    strings.nowTitle(label)
                },
                text = clock.range(advice.start, advice.end, now),
                secondary = secondary?.let { zoneLine(it) { f -> f.range(advice.start, advice.end, now) } },
                tip = listOfNotNull(strings.tip(advice.type), also).joinToString("\n"),
            )
            ReminderKind.Moment -> NotificationText(
                title = if (now.isBefore(advice.start)) {
                    strings.upcomingTitle(label, clock.time(advice.start, now))
                } else {
                    strings.nowTitle(label)
                },
                text = listOfNotNull(advice.detail, strings.tip(advice.type)).reduce(strings::join),
                secondary = secondary?.let { zoneLine(it) { f -> f.time(advice.start, now) } },
                tip = also,
            )
            ReminderKind.WakeUp -> NotificationText(
                title = strings.wakeUpTitle(advice.type),
                text = state?.headline?.let { strings.join(strings.nowPrefix(titleOf(it)), nowSentence(state, now, clock)) }
                    ?: state?.next?.let { strings.next(titleOf(it.advice), clock.range(it.from, it.until, now)) }
                    ?: strings.wakeUpFallback(),
                tip = state?.headline?.let { strings.tip(it.type) },
            )
            ReminderKind.Snoozed -> NotificationText(
                title = strings.snoozedTitle(label),
                text = if (advice.type.isMoment) {
                    clock.time(advice.start, now)
                } else {
                    strings.until(clock.time(advice.end, now))
                },
                tip = strings.tip(advice.type),
            )
        }
    }

    /** The test reminder from Settings. */
    fun test(): NotificationText = NotificationText(strings.testTitle(), strings.testText())

    /** "until 18:00 · then Sleep 18:00–02:00", prefixed by a logged outcome; "Next: …" in a gap. */
    private fun nowSentence(state: NowState, now: Instant, format: ClockFormat): String {
        val next = state.next?.let { format.range(it.from, it.until, now) to titleOf(it.advice) }
        if (state.headline == null) {
            return next?.let { (range, label) -> strings.next(label, range) } ?: strings.nothingNow()
        }
        val clauses = listOfNotNull(
            state.outcome?.let(strings::outcome),
            state.until?.let { strings.until(format.time(it, now)) },
            next?.let { (range, label) -> strings.then(label, range) },
        )
        return clauses.reduce(strings::join)
    }

    private fun zoneLine(format: ClockFormat, body: (ClockFormat) -> String): String =
        strings.inZone(ClockFormat.cityOf(format.zone.id), body(format))

    /** Labels plus the detail for flights ("In flight · BA7") and melatonin ("Melatonin · 0.5 mg" is in text). */
    private fun titleOf(advice: Advice): String {
        val label = strings.label(advice.type)
        val detail = advice.detail
        return if (advice.type == AdviceType.Flight && !detail.isNullOrBlank()) strings.join(label, detail) else label
    }

    /**
     * The zone worth showing next to local time: the destination while it differs from local time, otherwise
     * home (once you're at the destination). `null` when all three agree right now.
     */
    private fun secondaryZone(plan: JetLagPlan, now: Instant): ClockFormat? {
        val localOffset = clock.zone.rules.getOffset(now)
        return listOf(plan.destinationZoneId, plan.originZoneId)
            .map(ZoneId::of)
            .firstOrNull { it.rules.getOffset(now) != localOffset }
            ?.let(clock::withZone)
    }
}
