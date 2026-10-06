package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.feature.onboarding.OpusScreenshotTest
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import kotlin.math.absoluteValue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h800dp-xxhdpi")
class SleepDialTest : OpusScreenshotTest() {

    @Test
    fun dial_default() = snap("sleep_dial_default") {
        SleepDial(SleepWindow.Default, {}, Modifier.padding(16.dp))
    }

    @Test
    fun dial_dark_late_short() = snap("sleep_dial_dark_short", darkTheme = true) {
        SleepDial(SleepWindow(LocalTime.of(2, 30), LocalTime.of(6, 45)), {}, Modifier.padding(16.dp))
    }

    @Test
    fun dial_fontscale() = snap("sleep_dial_fontscale_1_5", fontScale = 1.5f) {
        SleepDial(SleepWindow(LocalTime.of(22, 15), LocalTime.of(6, 30)), {}, Modifier.padding(16.dp))
    }

    @Test
    fun talkback_adjusts_handles_by_15_minutes() {
        var window by mutableStateOf(SleepWindow.Default)
        setContent { SleepDial(window, { window = it }) }
        compose.onNodeWithTag(SleepDialTags.Wake)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "07:00"))
        compose.onNodeWithTag(SleepDialTags.Wake).performSemanticsAction(SemanticsActions.SetProgress) { it(7 * 60f + 15f) }
        window shouldBe SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 15))
        val actions = compose.onNodeWithTag(SleepDialTags.Bedtime).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.first { it.label == "15 minutes earlier" }.action() }
        window shouldBe SleepWindow(LocalTime.of(22, 45), LocalTime.of(7, 15))
    }

    @Test
    fun keyboard_arrows_move_the_focused_handle() {
        var window by mutableStateOf(SleepWindow.Default)
        setContent { SleepDial(window, { window = it }) }
        compose.onNodeWithTag(SleepDialTags.Bedtime).requestFocus()
        compose.onNodeWithTag(SleepDialTags.Bedtime).performKeyInput { pressKey(Key.DirectionLeft) }
        window shouldBe SleepWindow(LocalTime.of(22, 45), LocalTime.of(7, 0))
    }

    @Test
    fun pills_follow_the_window_and_say_what_a_tap_does() {
        var window by mutableStateOf(SleepWindow.Default)
        setContent { SleepDial(window, { window = it }) }
        compose.onNodeWithTag(SleepDialTags.BedtimePill).assert(hasText("Bedtime")).assert(hasText("23:00"))
        compose.onNodeWithTag(SleepDialTags.WakePill).assert(hasText("Wake")).assert(hasText("07:00"))
        compose.onNodeWithTag(SleepDialTags.BedtimePill).fetchSemanticsNode().config[SemanticsActions.OnClick].label shouldBe "set bedtime"
        compose.onNodeWithTag(SleepDialTags.WakePill).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        // A TalkBack nudge on a handle shows up in its pill.
        compose.onNodeWithTag(SleepDialTags.Wake).performSemanticsAction(SemanticsActions.SetProgress) { it(7 * 60f + 15f) }
        compose.onNodeWithTag(SleepDialTags.WakePill).assert(hasText("07:15"))
        compose.onNodeWithTag(SleepDialTags.Duration).assert(hasText("8 h 15 m"))
    }

    @Test
    fun tapping_a_pill_opens_the_time_picker_and_cancel_keeps_the_window() {
        var window by mutableStateOf(SleepWindow.Default)
        setContent { SleepDial(window, { window = it }) }
        compose.onNodeWithTag(SleepDialTags.WakePill).performClick()
        compose.onNodeWithText("Wake-up time").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag(SleepDialTags.PickerConfirm).assertDoesNotExist()
        window shouldBe SleepWindow.Default
    }

    @Test
    fun confirming_the_picker_sets_that_end_of_the_window() {
        var window by mutableStateOf(SleepWindow(LocalTime.of(22, 30), LocalTime.of(6, 45)))
        setContent { SleepDial(window, { window = it }) }
        compose.onNodeWithTag(SleepDialTags.BedtimePill).performClick()
        compose.onNodeWithTag(SleepDialTags.PickerConfirm).performClick()
        compose.onNodeWithTag(SleepDialTags.PickerConfirm).assertDoesNotExist()
        // The picker opened on the current bedtime, so confirming keeps it and leaves wake alone.
        window shouldBe SleepWindow(LocalTime.of(22, 30), LocalTime.of(6, 45))
    }

    @Test
    fun the_ring_shrinks_so_ring_and_pills_fit_max_height() = assertFits(fontScale = 1f)

    @Test
    fun the_ring_shrinks_so_ring_and_pills_fit_max_height_at_a_large_font() = assertFits(fontScale = 1.5f)

    private fun assertFits(fontScale: Float) {
        val maxHeight = 380.dp
        setContent(fontScale = fontScale) { SleepDial(SleepWindow.Default, {}, maxHeight = maxHeight) }
        val dial = compose.onNodeWithTag(SleepDialTags.Dial).getUnclippedBoundsInRoot()
        val pill = compose.onNodeWithTag(SleepDialTags.WakePill).getUnclippedBoundsInRoot()
        (pill.bottom - dial.top <= maxHeight + 0.5.dp) shouldBe true
        (dial.right - dial.left < 360.dp) shouldBe true
        (((dial.right - dial.left) - (dial.bottom - dial.top)).value.absoluteValue < 0.01f) shouldBe true
    }

    @Test
    fun dial_12_hour_large_font() = snap("sleep_dial_12h_fontscale_1_5", fontScale = 1.5f, is24Hour = false) {
        // The widest times ("10:55 PM") at a large font: the AM/PM pills must fit without clipping.
        SleepDial(SleepWindow(LocalTime.of(22, 55), LocalTime.of(10, 55)), {}, Modifier.padding(16.dp))
    }
}
