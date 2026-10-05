package dev.sebastiano.clockblocker.opus.core.model

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

class JetLagPlanPhaseTest {
    private val t0 = Instant.parse("2026-11-10T00:00:00Z")

    private fun plan(vararg offsetsHours: Double, dest: String = "Australia/Sydney") = JetLagPlan(
        tripId = "t",
        generatedAt = t0,
        strategy = AdaptationStrategy.Adapt,
        direction = ShiftDirection.Delay,
        shiftHours = -13.0,
        originZoneId = "Europe/London",
        destinationZoneId = dest,
        days = emptyList(),
        phase = offsetsHours.mapIndexed { i, h ->
            PhasePoint(t0.plusSeconds(3600L * i), (h * 60).toInt(), t0)
        },
        estimatedDaysToAdapt = 0.0,
        estimatedDaysWithoutPlan = 0.0,
    )

    @Test
    fun `misalignment is normalised to a half day`() {
        // body clock delayed 13 h from London = Sydney time (+11 in November)
        val p = plan(-13.0)
        p.misalignmentHoursAt(t0) shouldBe (0.0 plusOrMinus 1e-9)
        plan(0.0).misalignmentHoursAt(t0) shouldBe (11.0 plusOrMinus 1e-9)
    }

    @Test
    fun `interpolation takes the short way round when the stored offset wraps`() {
        // -11 h and +12 h are 1 h apart on the clock
        val p = plan(-11.0, 12.0)
        p.bodyOffsetAt(t0.plusSeconds(1800)) shouldBe ZoneOffset.ofHoursMinutes(-11, -30)
        plan(1.0, 3.0).bodyOffsetAt(t0.plusSeconds(1800)) shouldBe ZoneOffset.ofHours(2)
    }
}
