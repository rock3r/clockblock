package dev.sebastiano.clockblocker.opus.core.testing

import dev.sebastiano.clockblocker.opus.core.circadian.JetLagPlanner
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PhasePoint
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Deterministic stand-in for the circadian engine: a plausible-looking plan (pre-trip, travel, arrival and
 * adapted days; sleep, light and flight cards; a linear body-clock drift) that is cheap to compute. Not
 * science. Counts invocations so memoisation can be asserted.
 *
 * @param preDays pre-trip days (when the profile adjusts before departure).
 * @param postDays days after the arrival day.
 */
class FakeJetLagPlanner(
    private val preDays: Int = 2,
    private val postDays: Int = 3,
) : JetLagPlanner {
    private val counter = AtomicInteger()

    /** Number of [plan] calls so far. */
    val calls: Int get() = counter.get()

    override fun plan(trip: Trip, profile: UserProfile, now: Instant): JetLagPlan {
        counter.incrementAndGet()
        val home = ZoneId.of(trip.origin.zoneId)
        val dest = ZoneId.of(trip.destination.zoneId)
        val homeOffset = home.rules.getOffset(trip.departure).totalSeconds / 60
        val destOffset = dest.rules.getOffset(trip.arrival).totalSeconds / 60
        val shiftHours = wrap(destOffset - homeOffset) / 60.0
        val direction = when {
            shiftHours > 0 -> ShiftDirection.Advance
            shiftHours < 0 -> ShiftDirection.Delay
            else -> ShiftDirection.None
        }
        val departureDate = trip.departure.atZone(home).toLocalDate()
        val arrivalDate = trip.arrival.atZone(dest).toLocalDate()
        val pre = if (profile.adjustBeforeDeparture) preDays else 0

        val days = buildList {
            for (i in pre downTo 1) add(day(trip, -i, DayKind.PreTrip, departureDate.minusDays(i.toLong()), home, profile))
            add(travelDay(trip, departureDate, home))
            for (i in 0..postDays) {
                val kind = if (i == postDays) DayKind.Adapted else DayKind.Arrival
                add(day(trip, i + 1, kind, arrivalDate.plusDays(i.toLong()), dest, profile))
            }
        }
        val phaseStart = days.first().date.atStartOfDay(home).toInstant()
        val phaseEnd = days.last().date.plusDays(1).atStartOfDay(dest).toInstant()
        val hours = Duration.between(phaseStart, phaseEnd).toHours().coerceAtLeast(1)
        val phase = (0..hours step 6).map { h ->
            val t = h.toDouble() / hours
            val instant = phaseStart.plus(Duration.ofHours(h))
            PhasePoint(instant, (homeOffset + (destOffset - homeOffset) * t).roundToInt(), instant)
        }
        return JetLagPlan(
            tripId = trip.id,
            generatedAt = now,
            strategy = trip.strategyOverride ?: AdaptationStrategy.Adapt,
            direction = direction,
            shiftHours = shiftHours,
            originZoneId = home.id,
            destinationZoneId = dest.id,
            days = days,
            phase = phase,
            estimatedDaysToAdapt = kotlin.math.abs(shiftHours) / 1.5,
            estimatedDaysWithoutPlan = kotlin.math.abs(shiftHours),
        )
    }

    private fun day(trip: Trip, index: Int, kind: DayKind, date: LocalDate, zone: ZoneId, profile: UserProfile): PlanDay {
        val wake = date.atTime(profile.sleep.wake).atZone(zone).toInstant()
        val bed = date.atTime(profile.sleep.bedtime).atZone(zone).toInstant()
        val sleepEnd = date.plusDays(1).atTime(profile.sleep.wake).atZone(zone).toInstant()
        val prefix = "${trip.id}-d$index"
        return PlanDay(
            index = index,
            kind = kind,
            date = date,
            zoneId = zone.id,
            advice = listOf(
                Advice("$prefix-light", AdviceType.SeeBrightLight, wake, wake.plus(Duration.ofHours(2)), AdviceReason.LightAdvancesClock),
                Advice("$prefix-avoid", AdviceType.AvoidLight, bed.minus(Duration.ofHours(2)), bed, AdviceReason.AvoidCounterShift),
                Advice("$prefix-sleep", AdviceType.Sleep, bed, sleepEnd, AdviceReason.ShiftedSleep),
            ),
        )
    }

    private fun travelDay(trip: Trip, date: LocalDate, zone: ZoneId) = PlanDay(
        index = 0,
        kind = DayKind.Travel,
        date = date,
        zoneId = zone.id,
        advice = trip.legs.map {
            Advice("${trip.id}-flight-${it.id}", AdviceType.Flight, it.departure, it.arrival, AdviceReason.TravelMarker, it.flightNumber)
        },
    )

    /** Shortest signed difference on a 24 h circle, in minutes. */
    private fun wrap(minutes: Int): Int {
        var m = minutes % (24 * 60)
        if (m > 12 * 60) m -= 24 * 60
        if (m < -12 * 60) m += 24 * 60
        return m
    }
}
