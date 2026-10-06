package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.realPlan
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
        val plan = realPlan.copy(estimatedDaysWithoutPlan = NoPlanHorizonDays)
        compose.setContent {
            OpusTheme(dynamicColor = false, reduceMotion = true) {
                AdaptationCard(plan, plan.momentAt(PlanFixtures.MidAdaptation))
            }
        }
        val days = NoPlanHorizonDays.toInt()
        // Both the hero comparison and the journey chart say "more than 21 days"…
        compose.onAllNodesWithContentDescription("more than $days days", substring = true).assertCountEquals(2)
        // …and neither claims "about 21 days".
        compose.onAllNodesWithContentDescription("about $days days", substring = true, ignoreCase = true)
            .assertCountEquals(0)
    }
}
