package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h880dp-xhdpi")
class AdaptiveLinesTest {
    @get:Rule
    val compose = createComposeRule()

    private fun pair(width: Int) = compose.setContent {
        ClockblockTheme(dynamicColor = false, reduceMotion = true) {
            InlineOrStacked(
                first = { Text("62% adapted", Modifier.testTag("first")) },
                second = { Text("about 2 days to go", Modifier.testTag("second")) },
                modifier = Modifier.width(width.dp),
            )
        }
    }

    @Test
    fun `fits on one line with the separator between`() {
        pair(360)
        shown(compose.onAllNodesWithTag(InlineSeparatorTag, useUnmergedTree = true)) shouldBe true
        compose.onNodeWithTag("first").getBoundsInRoot().top shouldBe compose.onNodeWithTag("second").getBoundsInRoot().top
    }

    @Test
    fun `stacks without a dangling separator when it does not fit`() {
        pair(120)
        shown(compose.onAllNodesWithTag(InlineSeparatorTag, useUnmergedTree = true)) shouldBe false
        compose.onNodeWithTag("second").getBoundsInRoot().top shouldBeGreaterThan compose.onNodeWithTag("first").getBoundsInRoot().top
    }

    @Test
    fun `priority line drops the optional part before truncating the essential one`() {
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                PriorityLine(
                    optional = "Day 1 · Adapting",
                    essential = "body 2½ h ahead",
                    modifier = Modifier.width(140.dp).testTag("line"),
                )
            }
        }
        compose.onNodeWithText("body 2½ h ahead", useUnmergedTree = true).assertIsDisplayed()
        shown(compose.onAllNodesWithText("Day 1 · Adapting", substring = true, useUnmergedTree = true)) shouldBe false
    }

    @Test
    fun `priority line shows both when there is room`() {
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                PriorityLine(optional = "Day 1 · Adapting", essential = "body 2½ h ahead", modifier = Modifier.width(380.dp))
            }
        }
        compose.onNodeWithText("Day 1 · Adapting · ", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("body 2½ h ahead", useUnmergedTree = true).assertIsDisplayed()
    }

    /** True when any matching node is placed with a visible size (unplaced children don't count). */
    private fun shown(nodes: SemanticsNodeInteractionCollection): Boolean =
        nodes.fetchSemanticsNodes().any { it.layoutInfo.isPlaced && it.boundsInRoot.width > 0f }
}
