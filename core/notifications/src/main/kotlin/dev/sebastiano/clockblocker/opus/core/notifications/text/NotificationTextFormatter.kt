package dev.sebastiano.clockblocker.opus.core.notifications.text

import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowState
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderKind
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderSpec
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Rendered text of a notification. */
data class NotificationText(
    val title: String,
    val text: String,
    /** The main text again in a second zone (destination, or home once you're there); `null` if same offset. */
    val secondary: String? = null,
    val tip: String? = null,
    /** Header line next to the app name: the body clock, plus route and phase on travel day. */
    val subText: String? = null,
) {
    /** Expanded (BigText) body: text, then the secondary zone line, then the tip. */
    val bigText: String get() = listOfNotNull(text, secondary, tip).joinToString("\n")
}

/**
 * Pure composition of notification copy from plan state. Primary times are in the user's current zone
 * ([clock]); a secondary line repeats them in the destination zone (or the home zone once local time already
 * matches the destination), so "Times show local time plus a secondary zone" holds on every surface.
 *
 * @param redact the lock-screen (public) version: keeps labels and times, drops place names (the secondary zone
 *   line, the route), flight numbers, melatonin (named as a plain plan step, without its dose) and tips.
 */
class NotificationTextFormatter(
    private val strings: NotificationStrings,
    private val clock: ClockFormat,
    private val redact: Boolean = false,
) {

    /** The ongoing Now notification. */
    fun now(state: NowState, plan: JetLagPlan, now: Instant): NotificationText {
        val secondary = secondaryZone(plan, now)
        val headline = state.headline
        return NotificationText(
            title = headline?.let(::titleOf) ?: strings.nothingNow(),
            text = nowSentence(state, now, clock),
            secondary = secondary?.let { strings.inZone(ClockFormat.cityOf(it.zone.id), nowSentence(state, now, it)) },
            tip = headline?.let(::tipOf),
            subText = bodyClock(plan, now),
        )
    }

    /** An alerting reminder. [state] is the Now state at fire time (used by wake-ups to say what's next). */
    fun reminder(spec: ReminderSpec, state: NowState?, plan: JetLagPlan, now: Instant): NotificationText {
        val advice = spec.advice
        val label = titleOf(advice)
        val secondary = secondaryZone(plan, now)
        val also = spec.alsoStarting.takeIf { it.isNotEmpty() && !redact }
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
                tip = listOfNotNull(tipOf(advice), also).takeIf { it.isNotEmpty() }?.joinToString("\n"),
            )
            ReminderKind.Moment -> NotificationText(
                title = if (now.isBefore(advice.start)) {
                    strings.upcomingTitle(label, clock.time(advice.start, now))
                } else {
                    strings.nowTitle(label)
                },
                text = if (redact) {
                    strings.redactedText()
                } else {
                    listOfNotNull(advice.detail, strings.tip(advice.type)).reduce(strings::join)
                },
                secondary = secondary?.let { zoneLine(it) { f -> f.time(advice.start, now) } },
                tip = also,
            )
            ReminderKind.WakeUp -> NotificationText(
                title = strings.wakeUpTitle(advice.type),
                text = state?.headline?.let { strings.join(strings.nowPrefix(titleOf(it)), nowSentence(state, now, clock)) }
                    ?: state?.next?.let { strings.next(titleOf(it.advice), clock.range(it.from, it.until, now)) }
                    ?: strings.wakeUpFallback(),
                tip = state?.headline?.let(::tipOf),
            )
            ReminderKind.Snoozed -> NotificationText(
                title = strings.snoozedTitle(label),
                text = if (advice.type.isMoment) {
                    clock.time(advice.start, now)
                } else {
                    strings.until(clock.time(advice.end, now))
                },
                tip = tipOf(advice),
            )
        }
    }

    /** The test reminder from Settings. */
    fun test(): NotificationText = NotificationText(strings.testTitle(), strings.testText())

    /**
     * "Body 3½ h behind": the body clock relative to *local* time (the app-wide convention), rounded to the
     * nearest half hour. Within half an hour it reads as in sync, matching the dial (whose rings then line up).
     * No body time of day, which would change every minute; the scheduler re-renders the notification whenever
     * this rounded reading changes ([BodyClockHeader.nextChange]).
     */
    fun bodyClock(plan: JetLagPlan, now: Instant): String {
        val minutes = BodyClockHeader.minutesFromLocal(plan, clock.zone, now)
        if (abs(minutes) < BodyClockHeader.IN_SYNC_MINUTES) return strings.bodyInSync()
        val hours = halfHours(abs(minutes))
        return if (minutes < 0) strings.bodyBehind(hours) else strings.bodyAhead(hours)
    }

    /**
     * Travel-day Live Update subtext: "SFO → LHR · Lands 19:00 · Body 5 h behind". The phase uses absolute times
     * (never "in 20 min"), since the notification only re-renders at plan boundaries. [route] is dropped when
     * redacting.
     */
    fun travelSubText(plan: JetLagPlan, now: Instant, route: String?): String {
        val flights = plan.allAdvice.filter { it.type == AdviceType.Flight }.distinctBy { it.id }.sortedBy { it.start }
        val phase = when {
            flights.isEmpty() -> null
            now.isBefore(flights.first().start) -> strings.departs(clock.time(flights.first().start, now))
            else -> flights.firstOrNull { now in it }?.let { strings.lands(clock.time(it.end, now)) }
                ?: flights.firstOrNull { it.start.isAfter(now) }?.let { strings.nextFlight(clock.time(it.start, now)) }
                ?: strings.landed()
        }
        return listOfNotNull(route.takeUnless { redact }, phase, bodyClock(plan, now)).reduce(strings::join)
    }

    /** "SFO → LHR" from two display codes. */
    fun route(from: String, to: String): String = strings.route(from, to)

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

    /** Labels plus the detail for flights ("In flight · BA7"); redacted: no flight number, melatonin unnamed. */
    private fun titleOf(advice: Advice): String {
        if (redact) return if (advice.type == AdviceType.Melatonin) strings.redactedLabel() else strings.label(advice.type)
        val label = strings.label(advice.type)
        val detail = advice.detail
        return if (advice.type == AdviceType.Flight && !detail.isNullOrBlank()) strings.join(label, detail) else label
    }

    private fun tipOf(advice: Advice): String? = if (redact) null else strings.tip(advice.type)

    /**
     * The zone worth showing next to local time: the destination while it differs from local time, otherwise
     * home (once you're at the destination). `null` when all three agree right now, and when redacting (a city
     * name gives the trip away).
     */
    private fun secondaryZone(plan: JetLagPlan, now: Instant): ClockFormat? {
        if (redact) return null
        val localOffset = clock.zone.rules.getOffset(now)
        return listOf(plan.destinationZoneId, plan.originZoneId)
            .map(ZoneId::of)
            .firstOrNull { it.rules.getOffset(now) != localOffset }
            ?.let(clock::withZone)
    }

    private companion object {
        /** "3½ h", "½ h", "8 h": [minutes] (non-negative) rounded to the nearest half hour. */
        fun halfHours(minutes: Int): String {
            val halves = BodyClockHeader.halfHourSteps(minutes)
            val whole = halves / 2
            val half = halves % 2 == 1
            val number = when {
                whole == 0 && half -> "\u00BD"
                half -> "$whole\u00BD"
                else -> "$whole"
            }
            return "$number h"
        }
    }
}

/**
 * When the body-clock header ([NotificationTextFormatter.bodyClock]) reads differently. The body offset moves
 * continuously along the plan's phase trajectory, so a long block can carry the rounded reading across a
 * half-hour step; the scheduler arms a refresh at [nextChange] so the header never goes stale.
 */
object BodyClockHeader {
    private const val DAY_MINUTES = 24 * 60
    private const val HALF_DAY_MINUTES = 12 * 60

    /** Same threshold as the dial's aligned rings (`DialState.AlignedThresholdMinutes`). */
    internal const val IN_SYNC_MINUTES = 30

    /** How finely [nextChange] scans: a step change is picked up at most this late. */
    val SCAN_STEP: Duration = Duration.ofMinutes(10)

    /** How far ahead [nextChange] looks; later changes are found when the chain re-arms. */
    val HORIZON: Duration = Duration.ofHours(36)

    /** Body clock minus local time at [at], the short way round the clock (negative = behind). */
    fun minutesFromLocal(plan: JetLagPlan, zone: ZoneId, at: Instant): Int {
        val local = zone.rules.getOffset(at).totalSeconds / 60
        val body = plan.bodyOffsetAt(at).totalSeconds / 60
        return Math.floorMod(body - local + HALF_DAY_MINUTES, DAY_MINUTES) - HALF_DAY_MINUTES
    }

    /** Non-negative [minutes] in half-hour steps, rounded to the nearest. */
    internal fun halfHourSteps(minutes: Int): Int = (minutes + 15) / 30

    /** The header as a number: 0 = in sync, otherwise signed half-hour steps. Equal steps read the same. */
    fun step(plan: JetLagPlan, zone: ZoneId, at: Instant): Int {
        val minutes = minutesFromLocal(plan, zone, at)
        if (abs(minutes) < IN_SYNC_MINUTES) return 0
        val steps = halfHourSteps(abs(minutes))
        return if (minutes < 0) -steps else steps
    }

    /**
     * The first instant after [from], on a [SCAN_STEP] grid within [HORIZON], whose header reads differently from
     * the one at [from]; `null` when it holds all the way.
     */
    fun nextChange(plan: JetLagPlan, zone: ZoneId, from: Instant): Instant? {
        val current = step(plan, zone, from)
        val start = from.truncatedTo(ChronoUnit.MINUTES)
        val steps = HORIZON.toMinutes() / SCAN_STEP.toMinutes()
        return (1..steps).asSequence()
            .map { start.plus(SCAN_STEP.multipliedBy(it)) }
            .firstOrNull { step(plan, zone, it) != current }
    }
}
