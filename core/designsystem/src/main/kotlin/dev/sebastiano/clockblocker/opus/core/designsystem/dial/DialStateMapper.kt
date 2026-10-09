package dev.sebastiano.clockblocker.opus.core.designsystem.dial

import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Sun
import dev.sebastiano.clockblocker.opus.core.model.SunDay
import kotlinx.collections.immutable.toImmutableList
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset


/**
 * Projects a plan onto the Two skies dial at [instant], with the outer ring in [displayZone] local time.
 *
 * - Advice inside the 24 h window [instant − 8 h, instant + 16 h) becomes [DialArc]s, clipped to the window so
 *   arcs never overlap where the window wraps. Window and arcs are measured on the wall-clock face, which differs
 *   from real time across a DST change (#66).
 * - The body clock comes from [JetLagPlan.bodyOffsetAt]; CBTmin from the nearest [dev.sebastiano.clockblocker.opus.core.model.PhasePoint].
 * - The sky rings get the real sunrise and sunset at [place] (the trip's stop in [displayZone]) that fall inside the
 *   window, from the local date at [instant] or the days either side, and the dial names it; without a place they
 *   keep [DialState]'s default sun and the zone's city.
 */
fun JetLagPlan.toDialState(instant: Instant, displayZone: ZoneId, place: Place? = null): DialState {
    val localOffset: ZoneOffset = displayZone.rules.getOffset(instant)
    val local = instant.atOffset(localOffset).toLocalTime()
    val bodyOffset = bodyOffsetAt(instant)
    val bodyAhead = DialGeometry.minuteDelta(0f, (bodyOffset.totalSeconds - localOffset.totalSeconds) / 60f)

    // The window is 24 h of the wall-clock face (23 or 25 h of real time on a DST change day), so it never overlaps
    // itself where it wraps. Advice is placed and clipped by its offset from now on the face, which keeps the
    // window's edges where they belong even when they fall in an hour the clocks skip.
    val nowMinute = DialGeometry.minuteOfDay(local)
    val windowStart = -DialState.PastWindowMinutes
    val windowEnd = DialGeometry.MinutesPerDay - DialState.PastWindowMinutes

    fun minuteOf(at: Instant): Float =
        DialGeometry.minuteOfDay(at.atZone(displayZone).toLocalTime())

    fun face(at: Instant): Float = faceMinutesFrom(instant, at, displayZone)

    val arcs = allAdvice
        .sortedBy { it.start }
        .mapNotNull { advice ->
            if (advice.type.isMoment || advice.start == advice.end) {
                val at = face(advice.start)
                if (at < windowStart || at >= windowEnd) return@mapNotNull null
                DialArc(
                    advice.id, advice.type, minuteOf(advice.start), 0f, isNow = advice.start == instant,
                    startInstant = advice.start, endInstant = advice.end,
                )
            } else {
                val from = face(advice.start)
                // Inside a repeated hour (fall back) a block can end earlier on the face than it starts
                // (01:50 EDT → 01:10 EST): it keeps its real length instead.
                val to = face(advice.end).let { if (it > from) it else from + Duration.between(advice.start, advice.end).seconds / 60f }
                val s = maxOf(from, windowStart)
                val e = minOf(to, windowEnd)
                if (s >= e) return@mapNotNull null
                DialArc(
                    adviceId = advice.id,
                    type = advice.type,
                    startMinute = if (s == from) minuteOf(advice.start) else (nowMinute + s).mod(DialGeometry.MinutesPerDay),
                    sweepMinutes = e - s,
                    isNow = instant in advice,
                    narratedEndMinute = minuteOf(advice.end),
                    startInstant = advice.start,
                    endInstant = advice.end,
                )
            }
        }
        .toImmutableList()

    fun narrate(advice: dev.sebastiano.clockblocker.opus.core.model.Advice) = DialAdvice(
        type = advice.type,
        start = advice.start.atZone(displayZone).toLocalTime(),
        end = advice.end.atZone(displayZone).toLocalTime(),
        startInstant = advice.start,
        endInstant = advice.end,
    )

    val nearestPhase = phase.minByOrNull { Duration.between(it.instant, instant).abs() }
    val cbtMinBody = nearestPhase?.let { p ->
        val atCbt = p.cbtMin.atOffset(bodyOffsetAt(p.cbtMin)).toLocalTime()
        DialGeometry.minuteOfDay(atCbt)
    } ?: DefaultCbtMinBodyMinute

    val day = days.firstOrNull { d ->
        instant.atZone(ZoneId.of(d.zoneId)).toLocalDate() == d.date
    }

    // The sunrise and sunset inside the dial's window: around the midnight sun, the small hours belong to last night's
    // sunset (after midnight) and the evening to tomorrow's sunrise (#80).
    val sun = place?.let {
        Sun.on(
            instant.atZone(displayZone).toLocalDate(), displayZone, it.latitude, it.longitude,
            start = faceInstant(instant, displayZone, windowStart),
            end = faceInstant(instant, displayZone, windowEnd),
        )
    }

    return DialState(
        instant = instant,
        displayZoneId = displayZone.id,
        localMinute = DialGeometry.minuteOfDay(local),
        bodyAheadMinutes = bodyAhead,
        cbtMinBodyMinute = cbtMinBody,
        arcs = arcs,
        now = activeAt(instant).firstOrNull()?.let(::narrate),
        next = nextAfter(instant)?.let(::narrate),
        dayKind = day?.kind,
        dayIndex = day?.index,
        placeName = place?.city?.takeIf { it.isNotBlank() },
    ).withSun(sun, ::minuteOf)
}

private fun DialState.withSun(sun: SunDay?, minuteOf: (Instant) -> Float): DialState = when (sun) {
    null -> this
    is SunDay.RisesAndSets -> copy(sunriseMinute = minuteOf(sun.sunrise), sunsetMinute = minuteOf(sun.sunset))
    is SunDay.AlwaysUp -> minuteOf(sun.solarNoon).let { copy(sunriseMinute = it, sunsetMinute = it, daylight = Daylight.AlwaysUp) }
    is SunDay.AlwaysDown -> minuteOf(sun.solarNoon).let { copy(sunriseMinute = it, sunsetMinute = it, daylight = Daylight.AlwaysDown) }
}

/** Typical CBTmin for an intermediate chronotype sleeping 23:00–07:00: about 04:30 on the body clock. */
private const val DefaultCbtMinBodyMinute = 270f
