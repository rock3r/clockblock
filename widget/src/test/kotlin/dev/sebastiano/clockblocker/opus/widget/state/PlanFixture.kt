package dev.sebastiano.clockblocker.opus.widget.state

import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PhasePoint
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Small DSL to build plans for widget tests. Times are wall-clock in the day's zone. */
class PlanFixture(
    val origin: String = "Europe/Lisbon",
    val destination: String = "Asia/Tokyo",
) {
    private val days = mutableListOf<PlanDay>()
    private val phase = mutableListOf<PhasePoint>()
    private var counter = 0

    fun day(index: Int, date: String, zone: String, block: DayScope.() -> Unit) {
        val scope = DayScope(ZoneId.of(zone)).apply(block)
        days += PlanDay(index, DayKind.Arrival, LocalDate.parse(date), zone, scope.advice)
    }

    fun phase(at: Instant, bodyOffsetMinutes: Int, cbtMin: Instant) {
        phase += PhasePoint(at, bodyOffsetMinutes, cbtMin)
    }

    inner class DayScope(private val zone: ZoneId) {
        val advice = mutableListOf<Advice>()

        fun advice(type: AdviceType, start: String, end: String) {
            val s = local(start)
            var e = local(end)
            if (!e.isAfter(s) && !type.isMoment) e = e.plus(Duration.ofDays(1))
            advice += Advice("a${counter++}", type, s, e, AdviceReason.TravelMarker)
        }

        /** "2026-10-05T15:00" in this day's zone. */
        fun local(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()
    }

    fun build() = JetLagPlan(
        tripId = "trip-1",
        generatedAt = Instant.parse("2026-10-01T00:00:00Z"),
        strategy = AdaptationStrategy.Adapt,
        direction = ShiftDirection.Advance,
        shiftHours = 8.0,
        originZoneId = origin,
        destinationZoneId = destination,
        days = days.toList(),
        phase = phase.toList(),
        estimatedDaysToAdapt = 4.0,
        estimatedDaysWithoutPlan = 7.0,
    )
}

fun plan(origin: String = "Europe/Lisbon", destination: String = "Asia/Tokyo", block: PlanFixture.() -> Unit) =
    PlanFixture(origin, destination).apply(block).build()

fun at(zone: String, local: String): Instant = LocalDateTime.parse(local).atZone(ZoneId.of(zone)).toInstant()
