package dev.sebastiano.clockblocker.opus.core.notifications.text

import dev.sebastiano.clockblocker.opus.core.circadian.localZoneAt
import dev.sebastiano.clockblocker.opus.core.circadian.secondaryZoneFor
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowState
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderKind
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderSpec
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** Rendered text of a notification. */
data class NotificationText(
    val title: String,
    /** The main line: "until 20:00", a reminder's range, "Next: Sleep at 23:00" in a gap. */
    val text: String,
    /** [text]'s time in the trip's other zone, as a short tail ("04:00 Tokyo"); `null` if same offset. */
    val secondary: String? = null,
    val tip: String? = null,
    /** Header line next to the app name: the body clock, plus route and phase on travel day. */
    val subText: String? = null,
    /** Now notification, expanded only: other blocks running alongside, "Also now: Avoid caffeine until 20:00". */
    val also: String? = null,
    /** Now notification, expanded only: "Next: Avoid light at 17:00" (in a gap that is [text] itself). */
    val next: String? = null,
    /** Other expanded-only lines before the tip (a melatonin reminder's time in the other zone). */
    val details: List<String> = emptyList(),
    /** [text] with its [secondary] tail: "until 20:00 · 04:00 Tokyo". */
    val line: String = text,
    /** Every time in [line], [also] and [next] that has an other-zone tail, so a layout can choose where to wrap. */
    val zoneTimes: List<ZoneTime> = emptyList(),
) {
    /** Expanded (BigText) body: the main line with its zone tail, also-now, next, other details, then the tip. */
    val bigText: String get() = (listOf(line) + listOfNotNull(also, next) + details + listOfNotNull(tip)).joinToString("\n")
}

/** A local time and its other-zone tail as they appear in a [NotificationText]: [joined] is "02:00 · 18:00 Los Angeles". */
data class ZoneTime(val local: String, val other: String, val joined: String)

/**
 * Pure composition of notification copy from plan state. Primary times are in the plan's local time at that instant
 * ([localZoneAt]: the zone of the plan day, the same zone the plan screen and the widgets show, never the device's);
 * each time is followed by a short tail in the other end of the trip ([secondaryZoneFor]: the destination, or
 * home once local time is the destination's), "until 20:00 · 04:00 Tokyo" like the plan screen's Now card, so "Times
 * show local time plus a secondary zone" holds on every surface without repeating every line in both zones.
 *
 * @param locale and [use24Hour] shape the times; the zone always comes from the plan.
 * @param redact the lock-screen (public) version: keeps labels and times, drops place names (the secondary zone
 *   tail, the route), flight numbers, melatonin (named as a plain plan step, without its dose) and tips.
 */
class NotificationTextFormatter(
    private val strings: NotificationStrings,
    private val locale: Locale,
    private val use24Hour: Boolean = true,
    private val redact: Boolean = false,
) {

    /** Formats times in [plan]'s local time at [now] (see [localZoneAt]). */
    fun localClock(plan: JetLagPlan, now: Instant): ClockFormat = ClockFormat(plan.localZoneAt(now), locale, use24Hour)

    /**
     * The ongoing Now notification: the advice label, "until" its own end (as on the Now card and the widgets) with
     * the other zone's time as a tail, then (expanded) other blocks running alongside, what starts next, and a tip.
     * In a gap: "Nothing right now" and when the next block starts.
     */
    fun now(state: NowState, plan: JetLagPlan, now: Instant): NotificationText {
        val clock = localClock(plan, now)
        val secondary = secondaryClock(plan, clock, now)
        val headline = state.headline
        val until = state.until
        if (headline == null || until == null) {
            val next = state.next
            return withLine(
                NotificationText(
                    title = strings.nothingNow(),
                    text = next?.let { strings.next(titleOf(it), clock.time(it.start, now)) } ?: strings.nothingNow(),
                    secondary = next?.let { n -> secondary?.let { zoneTail(it, n.start) } },
                    subText = bodyClock(plan, now),
                    zoneTimes = listOfNotNull(next?.let { zoneTime(clock, secondary, it.start, now) }),
                ),
            )
        }
        val alongside = state.alongside.take(MAX_ALONGSIDE).takeUnless { redact }.orEmpty()
        val next = state.next
        return withLine(
            NotificationText(
                title = titleOf(headline),
                text = listOfNotNull(state.outcome?.let(strings::outcome), strings.until(clock.time(until, now))).reduce(strings::join),
                secondary = secondary?.let { zoneTail(it, until) },
                tip = tipOf(headline),
                subText = bodyClock(plan, now),
                // Like tips, what runs alongside is a detail the lock screen leaves out.
                also = alongside.takeIf { it.isNotEmpty() }?.let { list ->
                    strings.alsoNow(list.joinToString(", ") { strings.labelUntil(titleOf(it), timeWithTail(clock, secondary, it.end, now)) })
                },
                next = next?.let { strings.next(titleOf(it), timeWithTail(clock, secondary, it.start, now)) },
                zoneTimes = (listOf(until) + alongside.map { it.end } + listOfNotNull(next?.start))
                    .mapNotNull { zoneTime(clock, secondary, it, now) }
                    .distinct(),
            ),
        )
    }

    /** "02:00", or with the other zone's time when there is one: "02:00 · 18:00 Los Angeles". */
    private fun timeWithTail(clock: ClockFormat, secondary: ClockFormat?, instant: Instant, now: Instant): String =
        zoneTime(clock, secondary, instant, now)?.joined ?: clock.time(instant, now)

    private fun zoneTime(clock: ClockFormat, secondary: ClockFormat?, instant: Instant, now: Instant): ZoneTime? {
        if (secondary == null) return null
        val local = clock.time(instant, now)
        val other = zoneTail(secondary, instant)
        return ZoneTime(local, other, strings.join(local, other))
    }

    /** An alerting reminder. [state] is the Now state at fire time (used by wake-ups to say what's next). */
    fun reminder(spec: ReminderSpec, state: NowState?, plan: JetLagPlan, now: Instant): NotificationText {
        val advice = spec.advice
        val label = titleOf(advice)
        val clock = localClock(plan, now)
        val secondary = secondaryClock(plan, clock, now)
        val also = spec.alsoStarting.takeIf { it.isNotEmpty() && !redact }
            ?.let { list -> strings.also(list.joinToString(", ") { strings.label(it.type) }) }
        val text = when (spec.kind) {
            ReminderKind.Upcoming -> NotificationText(
                title = if (now.isBefore(advice.start)) {
                    strings.upcomingTitle(label, clock.time(advice.start, now))
                } else {
                    strings.nowTitle(label)
                },
                text = clock.range(advice.start, advice.end, now),
                secondary = secondary?.let { strings.zoneTail(it.range(advice.start, advice.end, now), cityOf(it)) },
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
                details = listOfNotNull(secondary?.let { zoneTail(it, advice.start) }),
                tip = also,
            )
            ReminderKind.WakeUp -> NotificationText(
                title = strings.wakeUpTitle(advice.type),
                text = state?.headline?.let { h ->
                    listOfNotNull(strings.nowPrefix(titleOf(h)), state.until?.let { strings.until(clock.time(it, now)) })
                        .reduce(strings::join)
                }
                    ?: state?.next?.let { strings.next(titleOf(it), clock.time(it.start, now)) }
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
        return withLine(text)
    }

    /** The test reminder from Settings. */
    fun test(): NotificationText = NotificationText(strings.testTitle(), strings.testText())

    /**
     * "Body 3½ h behind": the body clock relative to *local* time (the plan's local time, [localZoneAt], like the
     * plan screen and the widgets), rounded to the nearest half hour. Within half an hour it reads as in sync,
     * matching the dial (whose rings then line up). No body time of day, which would change every minute; the
     * scheduler re-renders the notification whenever this rounded reading changes ([BodyClockHeader.nextChange]).
     */
    fun bodyClock(plan: JetLagPlan, now: Instant): String {
        val minutes = BodyClockHeader.minutesFromLocal(plan, now)
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
        val clock = localClock(plan, now)
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

    /** Fills [NotificationText.line]: the main text with its zone tail, "until 20:00 · 04:00 Tokyo". */
    private fun withLine(text: NotificationText): NotificationText =
        text.copy(line = text.secondary?.let { strings.join(text.text, it) } ?: text.text)

    /** "04:00 Tokyo": [instant] in [format]'s zone, then the city; no weekday, like the plan screen's tail. */
    private fun zoneTail(format: ClockFormat, instant: Instant): String =
        strings.zoneTail(format.plainTime(instant), cityOf(format))

    private fun cityOf(format: ClockFormat): String = ClockFormat.cityOf(format.zone.id)

    /** Labels plus the detail for flights ("In flight · BA7"); redacted: no flight number, melatonin unnamed. */
    private fun titleOf(advice: Advice): String {
        if (redact) return if (advice.type == AdviceType.Melatonin) strings.redactedLabel() else strings.label(advice.type)
        val label = strings.label(advice.type)
        val detail = advice.detail
        return if (advice.type == AdviceType.Flight && !detail.isNullOrBlank()) strings.join(label, detail) else label
    }

    private fun tipOf(advice: Advice): String? = if (redact) null else strings.tip(advice.type)

    /**
     * The secondary zone next to local time ([secondaryZoneFor], the same choice as the plan screen and the
     * widgets). `null` when it reads the same as local time right now (nothing to add), and when redacting (a city
     * name gives the trip away).
     */
    private fun secondaryClock(plan: JetLagPlan, local: ClockFormat, now: Instant): ClockFormat? {
        if (redact) return null
        val secondary = plan.secondaryZoneFor(local.zone)
        return secondary.takeIf { it.rules.getOffset(now) != local.zone.rules.getOffset(now) }?.let(local::withZone)
    }

    private companion object {
        /** At most this many blocks on the "Also now" line; more would turn it back into a wall of text. */
        const val MAX_ALONGSIDE = 2

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
 * half-hour step; the scheduler arms a refresh at [nextChange] so the header never goes stale. Local time is the
 * plan's at each instant ([localZoneAt]), so a switch to the next plan day's zone is a change too.
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

    /** Body clock minus the plan's local time at [at], the short way round the clock (negative = behind). */
    fun minutesFromLocal(plan: JetLagPlan, at: Instant): Int {
        val local = plan.localZoneAt(at).rules.getOffset(at).totalSeconds / 60
        val body = plan.bodyOffsetAt(at).totalSeconds / 60
        return Math.floorMod(body - local + HALF_DAY_MINUTES, DAY_MINUTES) - HALF_DAY_MINUTES
    }

    /** Non-negative [minutes] in half-hour steps, rounded to the nearest. */
    internal fun halfHourSteps(minutes: Int): Int = (minutes + 15) / 30

    /** The header as a number: 0 = in sync, otherwise signed half-hour steps. Equal steps read the same. */
    fun step(plan: JetLagPlan, at: Instant): Int {
        val minutes = minutesFromLocal(plan, at)
        if (abs(minutes) < IN_SYNC_MINUTES) return 0
        val steps = halfHourSteps(abs(minutes))
        return if (minutes < 0) -steps else steps
    }

    /**
     * The first instant after [from], on a [SCAN_STEP] grid within [HORIZON], whose header reads differently from
     * the one at [from]; `null` when it holds all the way.
     */
    fun nextChange(plan: JetLagPlan, from: Instant): Instant? {
        val current = step(plan, from)
        val start = from.truncatedTo(ChronoUnit.MINUTES)
        val steps = HORIZON.toMinutes() / SCAN_STEP.toMinutes()
        return (1..steps).asSequence()
            .map { start.plus(SCAN_STEP.multipliedBy(it)) }
            .firstOrNull { step(plan, it) != current }
    }
}
