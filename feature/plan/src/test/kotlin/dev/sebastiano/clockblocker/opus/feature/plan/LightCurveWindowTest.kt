package dev.sebastiano.clockblocker.opus.feature.plan

import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PhasePoint
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.realPlan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class LightCurveWindowTest {

    private val dayStart = Instant.parse("2026-05-01T00:00:00Z")

    /** Hourly phase points whose coldest point moves from 04:00 to 03:00 UTC over the two days. */
    private val plan = JetLagPlan(
        tripId = "t",
        generatedAt = dayStart,
        strategy = AdaptationStrategy.Adapt,
        direction = ShiftDirection.Advance,
        shiftHours = 1.0,
        originZoneId = "UTC",
        destinationZoneId = "UTC",
        days = emptyList(),
        phase = (0..47).map { h ->
            val instant = dayStart.plus(Duration.ofHours(h.toLong()))
            val cbtMin = if (h < 16) Instant.parse("2026-05-01T04:00:00Z") else Instant.parse("2026-05-02T03:00:00Z")
            PhasePoint(instant, 0, cbtMin)
        },
        estimatedDaysToAdapt = 1.0,
        estimatedDaysWithoutPlan = 2.0,
    )

    private fun advice(type: AdviceType, start: String, end: String) =
        Advice("a", type, Instant.parse(start), Instant.parse(end), AdviceReason.LightAdvancesClock)

    @Test
    fun `light advice is placed around the coldest point nearest to it`() {
        lightCurveWindow(plan, advice(AdviceType.SeeBrightLight, "2026-05-01T05:00:00Z", "2026-05-01T08:00:00Z")) shouldBe 1.0..4.0
        lightCurveWindow(plan, advice(AdviceType.AvoidLight, "2026-05-01T21:00:00Z", "2026-05-02T00:30:00Z")) shouldBe -6.0..-2.5
        lightCurveWindow(plan, advice(AdviceType.SeeLight, "2026-05-02T04:00:00Z", "2026-05-02T05:00:00Z")) shouldBe 1.0..2.0
    }

    @Test
    fun `only light advice gets the curve`() {
        lightCurveWindow(plan, advice(AdviceType.Sleep, "2026-05-01T22:00:00Z", "2026-05-02T06:00:00Z")).shouldBeNull()
        lightCurveWindow(plan, advice(AdviceType.Caffeine, "2026-05-01T08:00:00Z", "2026-05-01T12:00:00Z")).shouldBeNull()
    }

    @Test
    fun `a plan without a body clock track has no curve`() {
        val empty = plan.copy(phase = emptyList(), days = listOf(PlanDay(0, DayKind.Travel, LocalDate.of(2026, 5, 1), "UTC", emptyList())))
        lightCurveWindow(empty, advice(AdviceType.SeeBrightLight, "2026-05-01T05:00:00Z", "2026-05-01T08:00:00Z")).shouldBeNull()
    }

    @Test
    fun `every light block of a real plan lands on the matching side of the curve`() {
        val light = realPlan.allAdvice.filter { it.type == AdviceType.SeeBrightLight || it.type == AdviceType.SeeLight }
        light.forEach { advice ->
            val window = lightCurveWindow(realPlan, advice).shouldNotBeNull()
            val middle = (window.start + window.endInclusive) / 2
            when (advice.reason) {
                AdviceReason.LightAdvancesClock -> (middle > 0) shouldBe true
                AdviceReason.LightDelaysClock -> (middle < 0) shouldBe true
                else -> Unit
            }
        }
    }
}
