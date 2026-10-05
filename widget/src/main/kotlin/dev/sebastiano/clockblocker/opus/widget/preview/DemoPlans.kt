package dev.sebastiano.clockblocker.opus.widget.preview

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
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Sample "Lisbon → Tokyo, day 2" plans built around a given instant, for generated widget-picker previews
 * (`AppWidgetManager.setWidgetPreview`) and the debug widget gallery. Not shown as real advice anywhere.
 */
object DemoPlans {
    const val TRIP_ID = "demo-lisbon-tokyo"
    private const val ORIGIN = "Europe/Lisbon"
    private const val DESTINATION = "Asia/Tokyo"

    enum class Scenario { AvoidLight, SeeBrightLight, Sleep, FreeTime, Adapted }

    fun lisbonTokyo(now: Instant, scenario: Scenario = Scenario.AvoidLight): JetLagPlan {
        val base = now.truncatedTo(ChronoUnit.HOURS)
        fun at(hours: Double): Instant = base.plusSeconds((hours * 3600).toLong())
        var n = 0
        fun advice(type: AdviceType, from: Double, to: Double, reason: AdviceReason) =
            Advice("demo-${n++}", type, at(from), at(to), reason)

        // Offsets in hours relative to "now" so the current block always straddles the hand.
        val shift = when (scenario) {
            Scenario.AvoidLight -> 0.0
            Scenario.SeeBrightLight -> 5.0
            Scenario.Sleep -> -7.0
            Scenario.FreeTime -> -2.5
            Scenario.Adapted -> -96.0
        }
        val advice = listOf(
            advice(AdviceType.SeeBrightLight, -5.0 + shift, -2.0 + shift, AdviceReason.LightAdvancesClock),
            advice(AdviceType.AvoidLight, -1.5 + shift, 1.5 + shift, AdviceReason.AvoidCounterShift),
            advice(AdviceType.Caffeine, -4.5 + shift, -3.0 + shift, AdviceReason.AlertnessSupport),
            advice(AdviceType.Melatonin, 3.5 + shift, 3.5 + shift, AdviceReason.MelatoninAdvances),
            advice(AdviceType.Sleep, 5.0 + shift, 13.0 + shift, AdviceReason.ShiftedSleep),
            advice(AdviceType.SeeLight, 14.0 + shift, 16.0 + shift, AdviceReason.LightAdvancesClock),
        )
        val tokyo = ZoneId.of(DESTINATION)
        val day = PlanDay(2, DayKind.Arrival, now.atZone(tokyo).toLocalDate().minusDays(1), DESTINATION, advice)
        // Body clock: home (Lisbon, +1 h in summer) drifting east; about 7 h behind Tokyo today.
        val phase = listOf(
            PhasePoint(at(-24.0), 90, at(-24.0 + 9.0)),
            PhasePoint(at(0.0), 120, at(9.5)),
            PhasePoint(at(24.0), 180, at(24.0 + 8.0)),
        )
        return JetLagPlan(
            tripId = TRIP_ID,
            generatedAt = now.minus(Duration.ofDays(3)),
            strategy = AdaptationStrategy.Adapt,
            direction = ShiftDirection.Advance,
            shiftHours = 8.0,
            originZoneId = ORIGIN,
            destinationZoneId = DESTINATION,
            days = listOf(day),
            phase = phase,
            estimatedDaysToAdapt = 4.5,
            estimatedDaysWithoutPlan = 7.5,
        )
    }
}
