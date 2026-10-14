package dev.sebastiano.clockblocker.opus.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.component.ConnectedChoice
import dev.sebastiano.clockblocker.opus.core.designsystem.component.ConnectedChoiceDefaults.fittedWidth
import dev.sebastiano.clockblocker.opus.core.designsystem.component.VerticalConnectedChoice
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

/** The connected button groups: selection, radio semantics, 48 dp targets, whole words, equal heights, and their looks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h400dp-xxhdpi")
class ConnectedChoiceGroupTest : ScreenshotTest() {

    private fun group(width: Dp = 120.dp, initial: String = Options.first()) {
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
        compose.onNodeWithTag("Work days").assertIsOn()
        compose.onNodeWithTag("Days off").assertIsOff().performClick()
        compose.onNodeWithTag("Days off").assertIsOn()
        compose.onNodeWithTag("Work days").assertIsOff()
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

    /** The old hand-rolled group broke "Work" into "Wor" / "k" in a narrow column at 1.5×. */
    @Test
    fun a_narrow_vertical_group_at_a_large_font_never_breaks_a_word() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                    VerticalConnectedChoice(Options, Options[0], {}, { it }, { it }, Modifier.width(72.dp))
                }
            }
        }
        Options.forEach { assertWholeWords(it) }
    }

    @Test
    fun a_narrow_horizontal_group_at_a_large_font_never_breaks_a_word() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                    ConnectedChoice(Options, Options[0], {}, { it }, { it }, Modifier.width(150.dp), maxLines = 2)
                }
            }
        }
        Options.forEach { assertWholeWords(it) }
    }

    @Test
    fun options_are_as_tall_as_the_tallest() {
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                VerticalConnectedChoice(listOf("Work days", "Off"), "Off", {}, { it }, { it }, Modifier.width(72.dp))
            }
        }
        val off = compose.onNodeWithTag("Off").getUnclippedBoundsInRoot()
        val work = compose.onNodeWithTag("Work days").getUnclippedBoundsInRoot()
        (off.bottom - off.top) shouldBe (work.bottom - work.top)
    }

    /** [label]'s lines end only at spaces, and the whole label shows. */
    private fun assertWholeWords(label: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        (0 until layout.lineCount - 1).forEach { line -> label[layout.getLineEnd(line) - 1] shouldBe ' ' }
        layout.getLineEnd(layout.lineCount - 1, visibleEnd = true) shouldBe label.length
    }

    @Test
    fun looks() = snap("connected_choice") { Sample() }

    @Test
    fun looks_dark() = snap("connected_choice_dark", darkTheme = true) { Sample() }

    @Test
    fun looks_large_font() = snap("connected_choice_fontscale_1_5", fontScale = 1.5f) { Sample() }

    @Composable
    private fun Sample() {
        // Vertical: roomy (one line each) and narrow (wrapped between words onto two lines), each with a different option
        // selected; then the horizontal group, roomy and narrow.
        Column {
            Row {
                VerticalConnectedChoice(Options, Options[0], {}, { it }, { it }, Modifier.width(120.dp))
                Spacer(Modifier.width(24.dp))
                VerticalConnectedChoice(Options, Options[1], {}, { it }, { "narrow $it" }, Modifier.width(88.dp))
            }
            Spacer(Modifier.height(24.dp))
            ConnectedChoice(Options, Options[0], {}, { it }, { "row $it" }, Modifier.width(300.dp), maxLines = 2)
            Spacer(Modifier.height(24.dp))
            ConnectedChoice(Options, Options[1], {}, { it }, { "narrow row $it" }, Modifier.width(150.dp), maxLines = 2)
        }
    }
}
