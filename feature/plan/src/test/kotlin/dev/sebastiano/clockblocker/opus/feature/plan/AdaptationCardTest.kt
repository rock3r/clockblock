package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.realPlan
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h880dp-xhdpi")
class AdaptationCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a no-plan estimate at the simulation horizon reads as more than, never as an exact time`() {
        // The model's sentinel for "didn't adapt within the simulated window": shown visually as "21+ d".
        val plan = realPlan.copy(estimatedDaysWithoutPlan = EstimateHorizonDays)
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                AdaptationCard(plan, plan.momentAt(PlanFixtures.MidAdaptation))
            }
        }
        val days = EstimateHorizonDays.toInt()
        // Both the hero comparison and the journey chart say "more than 21 days"…
        compose.onAllNodesWithContentDescription("more than $days days", substring = true).assertCountEquals(2)
        // …and neither claims "about 21 days".
        compose.onAllNodesWithContentDescription("about $days days", substring = true, ignoreCase = true)
            .assertCountEquals(0)
    }

    @Test
    fun `a with-plan estimate at the simulation horizon reads as more than too`() {
        val plan = realPlan.copy(estimatedDaysToAdapt = EstimateHorizonDays, estimatedDaysWithoutPlan = EstimateHorizonDays)
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                AdaptationCard(plan, plan.momentAt(PlanFixtures.MidAdaptation))
            }
        }
        val days = EstimateHorizonDays.toInt()
        compose.onAllNodesWithContentDescription("more than $days days with your plan", substring = true).assertCountEquals(1)
        compose.onAllNodesWithContentDescription("in sync in more than $days days", substring = true).assertCountEquals(1)
        compose.onAllNodesWithContentDescription("about $days days", substring = true, ignoreCase = true)
            .assertCountEquals(0)
    }

    @Test
    fun `the westward long way round admits it covers more hours`() {
        // London → San Francisco is 8 h west; advancing 16 h is further round the clock face, never "fewer hours".
        val plan = realPlan.copy(
            originZoneId = realPlan.destinationZoneId,
            destinationZoneId = realPlan.originZoneId,
            direction = ShiftDirection.Advance,
            shiftHours = 16.0,
        )
        plan.longWayRound() shouldBe LongWayRound.WestByAdvancing
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                AdaptationCard(plan, plan.momentAt(PlanFixtures.MidAdaptation))
            }
        }
        val inCallout = hasAnyAncestor(hasTestTag(PlanTags.LongWayRound))
        compose.onNode(inCallout and hasText("8 h behind", substring = true)).assertExists()
        compose.onNode(inCallout and hasText("16 h earlier", substring = true)).assertExists()
        compose.onNode(inCallout and hasText("further round the clock face", substring = true)).assertExists()
        compose.onAllNodes(inCallout and hasText("fewer", substring = true)).assertCountEquals(0)
    }
}
