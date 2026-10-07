package dev.sebastiano.clockblocker.opus.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import dev.sebastiano.clockblocker.opus.TestApplication
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Direction symmetry (T-024): every reverse motion returns toward the edge the forward motion came from. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = TestApplication::class)
class AppTransitionsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun transitions(direction: LayoutDirection): AppTransitions {
        lateinit var motion: ClockblockMotion
        compose.setContent { ClockblockTheme { motion = ClockblockTheme.motion } }
        compose.waitForIdle()
        return AppTransitions(motion, axisOffsetPx = 30, layoutDirection = direction)
    }

    @Test
    fun `LTR - forward is rightward and every reverse motion returns there`() {
        val t = transitions(LayoutDirection.Ltr)
        t.forwardSign shouldBe 1
        t.predictiveBackDrift shouldBe 1
        t.paneSlideSign shouldBe 1
    }

    @Test
    fun `RTL mirrors every direction`() {
        val t = transitions(LayoutDirection.Rtl)
        t.forwardSign shouldBe -1
        t.predictiveBackDrift shouldBe -1
        t.paneSlideSign shouldBe -1
    }
}
