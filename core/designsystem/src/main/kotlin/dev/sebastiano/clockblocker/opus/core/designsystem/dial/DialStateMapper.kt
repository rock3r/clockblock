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
 *   arcs never overlap where the window wraps.
 * - The body clock comes from [JetLagPlan.bodyOffsetAt]; CBTmin from the nearest [dev.sebastiano.clockblocker.opus.core.model.PhasePoint].
 * - The sky rings get the real sunrise and sunset at [place] (the trip's stop in [displayZone]) on the local date
 *   at [instant], and the dial names it; without a place they keep [DialState]'s default sun and the zone's city.
 */
fun JetLagPlan.toDialState(instant: Instant, displayZone: ZoneId, place: Place? = null): DialState {
    val localOffset: ZoneOffset = displayZone.rules.getOffset(instant)
    val local = instant.atOffset(localOffset).toLocalTime()
    val bodyOffset = bodyOffsetAt(instant)
    val bodyAhead = DialGeometry.minuteDelta(0f, (bodyOffset.totalSeconds - localOffset.totalSeconds) / 60f)

    val windowStart = instant.minus(Duration.ofMinutes(DialState.PastWindowMinutes.toLong()))
    val windowEnd = windowStart.plus(Duration.ofDays(1))

    fun minuteOf(at: Instant): Float =
        DialGeometry.minuteOfDay(at.atZone(displayZone).toLocalTime())

    val arcs = allAdvice
        .sortedBy { it.start }
        .mapNotNull { advice ->
            if (advice.type.isMoment || advice.start == advice.end) {
                if (advice.start.isBefore(windowStart) || !advice.start.isBefore(windowEnd)) return@mapNotNull null
                DialArc(advice.id, advice.type, minuteOf(advice.start), 0f, isNow = advice.start == instant)
            } else {
                val s = maxOf(advice.start, windowStart)
                val e = minOf(advice.end, windowEnd)
                if (!s.isBefore(e)) return@mapNotNull null
                DialArc(
                    adviceId = advice.id,
                    type = advice.type,
                    startMinute = minuteOf(s),
                    sweepMinutes = Duration.between(s, e).seconds / 60f,
                    isNow = instant in advice,
                    narratedEndMinute = minuteOf(advice.end),
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

    val sun = place?.let { Sun.on(instant.atZone(displayZone).toLocalDate(), displayZone, it.latitude, it.longitude) }

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
