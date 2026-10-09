package dev.sebastiano.clockblocker.opus.core.designsystem.prc

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class LightResponseCurveCardTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(window: ClosedFloatingPointRange<Double>? = null) = compose.setContent {
        ClockblockTheme(dynamicColor = false, reduceMotion = true) {
            LightResponseCurveCard(window = window, windowType = window?.let { AdviceType.SeeBrightLight })
        }
    }

    private val curve get() = compose.onNodeWithTag(LightResponseCurveTags.Curve)

    private fun state(expected: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)

    private fun action(label: String) = curve.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }

    @Test
    fun `the sun starts in the middle of the block and reads it qualitatively`() {
        show(1.0..4.0)
        curve.assert(state("2 h 30 min after your body's coldest point, in this block. Light here moves your clock earlier, strongly."))
    }

    @Test
    fun `TalkBack offers earlier hour, later hour and reset to the block`() {
        show(1.0..4.0)
        curve.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
            .shouldContainExactly("Earlier hour", "Later hour", "Reset to this block")
    }

    @Test
    fun `earlier and later hours move the sun and reset brings it back`() {
        show(1.0..4.0)
        repeat(3) { compose.runOnUiThread { action("Earlier hour").action!!.invoke() } }
        curve.assert(state("30 min before your body's coldest point. Light here moves your clock a little later."))
        repeat(2) { compose.runOnUiThread { action("Later hour").action!!.invoke() } }
        curve.assert(state("1 h 30 min after your body's coldest point, in this block. Light here moves your clock earlier, strongly."))
        compose.runOnUiThread { action("Reset to this block").action!!.invoke() }
        curve.assert(state("2 h 30 min after your body's coldest point, in this block. Light here moves your clock earlier, strongly."))
    }

    @Test
    fun `without a block the reset is plain and the sun stops at the ends of the curve`() {
        show()
        curve.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
            .shouldContainExactly("Earlier hour", "Later hour", "Reset")
        repeat(12) { compose.runOnUiThread { action("Later hour").action!!.invoke() } }
        curve.assert(state("12 h after your body's coldest point. Light here barely moves your clock."))
        compose.runOnUiThread { action("Reset").action!!.invoke() }
        curve.assert(state("2 h 30 min after your body's coldest point. Light here moves your clock earlier, strongly."))
    }
}
