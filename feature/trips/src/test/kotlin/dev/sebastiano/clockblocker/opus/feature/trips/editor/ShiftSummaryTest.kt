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
    fun `shifting the long way round keeps the route's offset and is called out`() {
        // +10 h east planned as a 14 h delay: the planner reports −14 (the way round that's easier for a big
        // eastward shift). The card still reads "+10 h east", and says it goes the long way.
        val east = ShiftSummary.of(preview(-14.0, ShiftDirection.Delay)).shouldBeInstanceOf<ShiftSummary.Shift>()
        east.shiftHours shouldBe 10f
        east.east shouldBe true
        east.longWay shouldBe true
        // −11 h west planned as a 13 h advance.
        val west = ShiftSummary.of(preview(13.0, ShiftDirection.Advance)).shouldBeInstanceOf<ShiftSummary.Shift>()
        west.shiftHours shouldBe -11f
        west.east shouldBe false
        west.longWay shouldBe true
    }

    @Test
    fun `the time difference folds into minus 12 to plus 12`() {
        ShiftSummary.timeDifference(-14.0) shouldBe 10.0
        ShiftSummary.timeDifference(13.0) shouldBe -11.0
        ShiftSummary.timeDifference(12.0) shouldBe 12.0
        ShiftSummary.timeDifference(-12.0) shouldBe 12.0
        ShiftSummary.timeDifference(-8.0) shouldBe -8.0
        ShiftSummary.timeDifference(5.5) shouldBe 5.5
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
