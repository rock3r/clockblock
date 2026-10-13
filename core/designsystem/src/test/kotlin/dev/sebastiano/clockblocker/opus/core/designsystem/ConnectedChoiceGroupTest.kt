package dev.sebastiano.clockblocker.opus.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import dev.sebastiano.clockblocker.opus.core.designsystem.component.HorizontalConnectedChoice
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.component.VerticalConnectedChoice
import dev.sebastiano.clockblocker.opus.core.designsystem.component.ConnectedChoiceDefaults.fittedWidth
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val Options = listOf("Work days", "Days off")

/** The vertical connected button group: selection, radio semantics, 48 dp targets, whole labels, and its looks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h400dp-xxhdpi")
class ConnectedChoiceGroupTest : ScreenshotTest() {

    private fun group(width: androidx.compose.ui.unit.Dp = 120.dp, initial: String = Options.first()) {
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                var selected by remember { mutableStateOf(initial) }
                VerticalConnectedChoice(
                    options = Options,
                    selected = selected,
                    onSelect = { selected = it },
                    label = { it },
                    tag = { it },
                    modifier = Modifier.width(width),
                )
            }
        }
    }

    @Test
    fun tapping_an_option_selects_it_and_only_it() {
        group()
        compose.onNodeWithTag("Work days").assertIsSelected()
        compose.onNodeWithTag("Days off").assertIsNotSelected().performClick()
        compose.onNodeWithTag("Days off").assertIsSelected()
        compose.onNodeWithTag("Work days").assertIsNotSelected()
    }

    @Test
    fun options_are_radio_buttons_in_one_selectable_group() {
        group()
        Options.forEach { compose.onNodeWithTag(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)) }
        compose.onNodeWithTag("Work days").onParent().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
    }

    @Test
    fun every_option_is_at_least_48dp_tall() {
        group()
        Options.forEach { compose.onNodeWithTag(it).assertHeightIsAtLeast(48.dp) }
    }

    @Test
    fun fitted_width_keeps_labels_on_one_line_when_there_is_room_and_wraps_whole_words_otherwise() {
        var oneLine: Int? = -1
        var wrapped: Int? = -1
        var tooNarrow: Int? = -1
        var maxPx = 0
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                val measurer = rememberTextMeasurer()
                val style = MaterialTheme.typography.labelLarge
                with(LocalDensity.current) {
                    maxPx = 200.dp.roundToPx()
                    oneLine = fittedWidth(measurer, Options, style, maxPx)
                    wrapped = fittedWidth(measurer, Options, style, 70.dp.roundToPx())
                    tooNarrow = fittedWidth(measurer, Options, style, 30.dp.roundToPx())
                }
            }
        }
        compose.waitForIdle()
        // One line fits in 200 dp, so the group asks for less than that; at 70 dp the labels wrap at the space.
        (oneLine.shouldNotBeNull() < maxPx) shouldBe true
        (wrapped.shouldNotBeNull() < oneLine!!) shouldBe true
        tooNarrow.shouldBeNull()
    }

    @Test
    fun looks() = snap("connected_choice") { Sample() }

    @Test
    fun looks_dark() = snap("connected_choice_dark", darkTheme = true) { Sample() }

    @Test
    fun looks_large_font() = snap("connected_choice_fontscale_1_5", fontScale = 1.5f) { Sample() }

    @androidx.compose.runtime.Composable
    private fun Sample() {
        // Vertical: roomy (one line each) and narrow (wrapped onto two lines), each with a different option selected;
        // then the horizontal twin.
        Column {
            Row {
                VerticalConnectedChoice(Options, Options[0], {}, { it }, Modifier.width(120.dp))
                Spacer(Modifier.width(24.dp))
                VerticalConnectedChoice(Options, Options[1], {}, { it }, Modifier.width(72.dp))
            }
            Spacer(Modifier.height(24.dp))
            HorizontalConnectedChoice(Options, Options[0], {}, { it }, Modifier.width(300.dp))
        }
    }
}
