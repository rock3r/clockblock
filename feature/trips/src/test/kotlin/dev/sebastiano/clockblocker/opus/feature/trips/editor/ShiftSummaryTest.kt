package dev.sebastiano.clockblocker.opus.feature.trips.editor

import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/** The "Your shift" card's numbers, straight from the planner's preview. */
class ShiftSummaryTest {

    private fun preview(
        shiftHours: Double,
        direction: ShiftDirection,
        strategy: AdaptationStrategy = AdaptationStrategy.Adapt,
        daysToAdapt: Double = 4.0,
        daysWithoutPlan: Double = 7.0,
    ) = ShiftPreview(shiftHours, direction, strategy, daysToAdapt, daysWithoutPlan)

    @Test
    fun `an eastward trip reads as plus hours east, with and without the plan`() {
        val summary = ShiftSummary.of(preview(9.0, ShiftDirection.Advance, daysToAdapt = 4.4, daysWithoutPlan = 6.6))
            .shouldBeInstanceOf<ShiftSummary.Shift>()
        summary.shiftHours shouldBe 9f
        summary.east shouldBe true
        summary.daysWithPlan shouldBe 4
        summary.daysWithoutPlan shouldBe 7
        summary.longWay shouldBe false
    }

    @Test
    fun `a westward trip keeps its sign`() {
        val summary = ShiftSummary.of(preview(-8.0, ShiftDirection.Delay)).shouldBeInstanceOf<ShiftSummary.Shift>()
        summary.shiftHours shouldBe -8f
        summary.east shouldBe false
        summary.longWay shouldBe false
    }

    @Test
    fun `days round like the plan's stat and never drop below one`() {
        val summary = ShiftSummary.of(preview(2.0, ShiftDirection.Advance, daysToAdapt = 0.3, daysWithoutPlan = 1.5))
            .shouldBeInstanceOf<ShiftSummary.Shift>()
        summary.daysWithPlan shouldBe 1
        summary.daysWithoutPlan shouldBe 2
    }

    @Test
    fun `shifting the long way round is called out`() {
        // East, but the planner delays the body clock (the way round that's easier for a big eastward shift).
        ShiftSummary.of(preview(10.0, ShiftDirection.Delay)).shouldBeInstanceOf<ShiftSummary.Shift>().longWay shouldBe true
        ShiftSummary.of(preview(-11.0, ShiftDirection.Advance)).shouldBeInstanceOf<ShiftSummary.Shift>().longWay shouldBe true
    }

    @Test
    fun `a home-time plan says so, even though the planner reports no shift`() {
        ShiftSummary.of(preview(0.0, ShiftDirection.None, AdaptationStrategy.StayOnHomeTime, 0.0, 0.0)) shouldBe
            ShiftSummary.HomeTime
    }

    @Test
    fun `under an hour, or no direction, is no jet lag`() {
        ShiftSummary.of(preview(0.5, ShiftDirection.Advance)) shouldBe ShiftSummary.NoShift
        ShiftSummary.of(preview(3.0, ShiftDirection.None)) shouldBe ShiftSummary.NoShift
    }
}
