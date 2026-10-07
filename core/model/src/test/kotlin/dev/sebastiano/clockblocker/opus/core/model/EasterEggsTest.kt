package dev.sebastiano.clockblocker.opus.core.model

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class EasterEggsTest {
    private val bedtime = Instant.parse("2026-11-10T22:00:00Z")
    private val wake = Instant.parse("2026-11-11T06:00:00Z")

    private fun advice(type: AdviceType, start: Instant, end: Instant) =
        Advice(id = "$type@$start", type = type, start = start, end = end, reason = AdviceReason.ShiftedSleep)

    private val plan = JetLagPlan(
        tripId = "t",
        generatedAt = bedtime,
        strategy = AdaptationStrategy.Adapt,
        direction = ShiftDirection.Advance,
        shiftHours = 6.0,
        originZoneId = "America/New_York",
        destinationZoneId = "Europe/Rome",
        days = listOf(
            PlanDay(
                index = 1,
                kind = DayKind.Arrival,
                date = LocalDate.of(2026, 11, 10),
                zoneId = "Europe/Rome",
                advice = listOf(
                    advice(AdviceType.AvoidLight, bedtime.minusSeconds(7_200), bedtime.plusSeconds(3_600)),
                    advice(AdviceType.Sleep, bedtime, wake),
                ),
            ),
        ),
        phase = emptyList(),
        estimatedDaysToAdapt = 3.0,
        estimatedDaysWithoutPlan = 6.0,
    )

    @Test
    fun `allowed with no plan and reduce motion off`() {
        easterEggsAllowed(reduceMotion = false, plan = null, now = bedtime).shouldBeTrue()
    }

    @Test
    fun `never with reduce motion on`() {
        easterEggsAllowed(reduceMotion = true, plan = null, now = bedtime).shouldBeFalse()
        easterEggsAllowed(reduceMotion = true, plan = plan, now = wake.plusSeconds(3_600)).shouldBeFalse()
    }

    @Test
    fun `not while the plan says sleep, even when another block overlaps it`() {
        easterEggsAllowed(reduceMotion = false, plan = plan, now = bedtime).shouldBeFalse()
        easterEggsAllowed(reduceMotion = false, plan = plan, now = bedtime.plusSeconds(1_800)).shouldBeFalse()
        easterEggsAllowed(reduceMotion = false, plan = plan, now = wake.minusSeconds(60)).shouldBeFalse()
    }

    @Test
    fun `allowed outside the plan's sleep`() {
        easterEggsAllowed(reduceMotion = false, plan = plan, now = bedtime.minusSeconds(60)).shouldBeTrue()
        easterEggsAllowed(reduceMotion = false, plan = plan, now = wake).shouldBeTrue()
    }
}
