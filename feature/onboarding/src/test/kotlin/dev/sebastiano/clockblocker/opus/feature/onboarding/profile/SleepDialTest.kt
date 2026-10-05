package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
}
