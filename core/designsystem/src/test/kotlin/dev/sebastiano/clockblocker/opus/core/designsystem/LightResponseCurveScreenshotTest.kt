package dev.sebastiano.clockblocker.opus.core.designsystem

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.prc.LightResponseCurveCard
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h900dp-xhdpi")
class LightResponseCurveScreenshotTest : ScreenshotTest() {

    @Test fun about() = snap("light_curve_about") { Card() }

    @Test fun brightLight() = snap("light_curve_bright_light") { Card(1.0..4.0, AdviceType.SeeBrightLight) }

    @Test fun brightLightDark() = snap("light_curve_bright_light_dark", darkTheme = true) { Card(1.0..4.0, AdviceType.SeeBrightLight) }

    @Test fun avoidLight() = snap("light_curve_avoid_light") { Card(-6.0..-2.5, AdviceType.AvoidLight) }

    @Test fun avoidLightDark() = snap("light_curve_avoid_light_dark", darkTheme = true) { Card(-6.0..-2.5, AdviceType.AvoidLight) }

    @Test fun fontScale150() = snap("light_curve_bright_light_font_1_5", fontScale = 1.5f) { Card(1.0..4.0, AdviceType.SeeBrightLight) }

    @Test fun nightSafe() = snap("light_curve_night_safe", nightSafe = true) { Card(-6.0..-2.5, AdviceType.AvoidLight) }
}

@androidx.compose.runtime.Composable
private fun Card(window: ClosedFloatingPointRange<Double>? = null, type: AdviceType? = null) =
    LightResponseCurveCard(Modifier.width(368.dp), window = window, windowType = type)
