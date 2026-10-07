package dev.sebastiano.clockblocker.opus.core.designsystem.preview

import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.toDialState
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PhasePoint
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * A believable Lisbon → Tokyo (+8 h) plan for previews, screenshot tests and generated widget previews.
 * Not produced by the planner: hand-written to exercise every visual state.
 */
object SamplePlan {
    val Lisbon: ZoneId = ZoneId.of("Europe/Lisbon")
    val Tokyo: ZoneId = ZoneId.of("Asia/Tokyo")
    private val departure: LocalDate = LocalDate.of(2026, 10, 12)

    private fun at(zone: ZoneId, dayOffset: Int, time: String): Instant =
        LocalDateTime.of(departure.plusDays(dayOffset.toLong()), LocalTime.parse(time)).atZone(zone).toInstant()

    private var counter = 0
    private fun block(
        type: AdviceType,
        zone: ZoneId,
        day: Int,
        from: String,
        to: String,
        endDayShift: Int = 0,
        reason: AdviceReason = AdviceReason.LightAdvancesClock,
    ) = Advice("s${counter++}", type, at(zone, day, from), at(zone, day + endDayShift, to), reason)

    private fun moment(type: AdviceType, zone: ZoneId, day: Int, time: String) =
        Advice("s${counter++}", type, at(zone, day, time), at(zone, day, time), AdviceReason.MelatoninAdvances, "0.5 mg")

    val plan: JetLagPlan by lazy {
        counter = 0
        val days = listOf(
            PlanDay(
                -1, DayKind.PreTrip, departure.minusDays(1), Lisbon.id,
                listOf(
                    block(AdviceType.Sleep, Lisbon, -2, "22:30", "06:30", endDayShift = 1, reason = AdviceReason.ShiftedSleep),
                    block(AdviceType.SeeBrightLight, Lisbon, -1, "06:30", "09:00"),
                    block(AdviceType.Caffeine, Lisbon, -1, "07:00", "11:00", reason = AdviceReason.AlertnessSupport),
                    block(AdviceType.AvoidCaffeine, Lisbon, -1, "14:00", "22:00", reason = AdviceReason.ProtectSleep),
                    moment(AdviceType.Melatonin, Lisbon, -1, "17:30"),
                    block(AdviceType.AvoidLight, Lisbon, -1, "19:00", "21:30", reason = AdviceReason.AvoidCounterShift),
                    block(AdviceType.Sleep, Lisbon, -1, "22:00", "06:00", endDayShift = 1, reason = AdviceReason.ShiftedSleep),
                ),
            ),
            PlanDay(
                0, DayKind.Travel, departure, Lisbon.id,
                listOf(
                    block(AdviceType.SeeBrightLight, Lisbon, 0, "06:00", "08:30"),
                    block(AdviceType.Flight, Lisbon, 0, "11:40", "08:10", endDayShift = 1, reason = AdviceReason.TravelMarker),
                    block(AdviceType.Sleep, Lisbon, 0, "16:00", "22:00", reason = AdviceReason.DestinationSleep),
                ),
            ),
            PlanDay(
                2, DayKind.Arrival, departure.plusDays(2), Tokyo.id,
                listOf(
                    block(AdviceType.Sleep, Tokyo, 1, "23:30", "07:30", endDayShift = 1, reason = AdviceReason.DestinationSleep),
                    block(AdviceType.AvoidLight, Tokyo, 2, "07:30", "11:30", reason = AdviceReason.AvoidCounterShift),
                    block(AdviceType.SeeBrightLight, Tokyo, 2, "11:30", "15:00"),
                    block(AdviceType.Caffeine, Tokyo, 2, "11:30", "14:30", reason = AdviceReason.AlertnessSupport),
                    block(AdviceType.SeeLight, Tokyo, 2, "15:00", "18:00"),
                    block(AdviceType.PeakFatigue, Tokyo, 2, "16:00", "17:00", reason = AdviceReason.CircadianLow),
                    block(AdviceType.Nap, Tokyo, 2, "16:10", "16:40", reason = AdviceReason.SleepPressure),
                    moment(AdviceType.Melatonin, Tokyo, 2, "20:30"),
                    block(AdviceType.Sleep, Tokyo, 2, "23:00", "07:00", endDayShift = 1, reason = AdviceReason.DestinationSleep),
                ),
            ),
            PlanDay(
                5, DayKind.Adapted, departure.plusDays(5), Tokyo.id,
                listOf(
                    block(AdviceType.Sleep, Tokyo, 4, "23:00", "07:00", endDayShift = 1, reason = AdviceReason.DestinationSleep),
                    block(AdviceType.SeeLight, Tokyo, 5, "07:30", "10:30"),
                    block(AdviceType.Caffeine, Tokyo, 5, "08:00", "12:00", reason = AdviceReason.AlertnessSupport),
                    block(AdviceType.OptionalNap, Tokyo, 5, "14:00", "14:30", reason = AdviceReason.SleepPressure),
                    block(AdviceType.Sleep, Tokyo, 5, "23:00", "07:00", endDayShift = 1, reason = AdviceReason.DestinationSleep),
                ),
            ),
        )
        // Body clock as a UTC offset: Lisbon (WEST, +60) drifting to Tokyo (+540).
        val offsets = listOf(-2 to 60, -1 to 100, 0 to 150, 1 to 200, 2 to 300, 3 to 390, 4 to 510, 5 to 540, 6 to 540)
        val phase = offsets.flatMap { (day, minutes) ->
            val noon = at(Lisbon, day, "12:00")
            val cbtMin = noon.minusSeconds(7L * 3600 + 30 * 60 + (minutes - 60) * 60L)
            listOf(PhasePoint(noon, minutes, cbtMin))
        }
        JetLagPlan(
            tripId = "sample-lis-hnd",
            generatedAt = at(Lisbon, -3, "09:00"),
            strategy = AdaptationStrategy.Adapt,
            direction = ShiftDirection.Advance,
            shiftHours = 8.0,
            originZoneId = Lisbon.id,
            destinationZoneId = Tokyo.id,
            days = days,
            phase = phase,
            estimatedDaysToAdapt = 4.5,
            estimatedDaysWithoutPlan = 7.5,
        )
    }

    /** The day before departure, 07:40 in Lisbon: body ≈ local, bright light now. */
    val preTripDial: DialState by lazy { plan.toDialState(at(Lisbon, -1, "07:40"), Lisbon) }

    /** Arrival day 2, 14:20 in Tokyo: body ~4 h behind ("4 h behind"), bright light now. */
    val midAdaptationDial: DialState by lazy { plan.toDialState(at(Tokyo, 2, "14:20"), Tokyo) }

    /** Day 5, 09:10 in Tokyo: rings aligned. */
    val adaptedDial: DialState by lazy { plan.toDialState(at(Tokyo, 5, "09:10"), Tokyo) }

    /** Mid-adaptation at night (avoid-light / sleep): for night-safe previews. */
    val nightDial: DialState by lazy { plan.toDialState(at(Tokyo, 2, "23:40"), Tokyo) }
}
