package dev.sebastiano.clockblocker.opus.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialFonts
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.StaticTwoSkiesDial
import dev.sebastiano.clockblocker.opus.core.designsystem.preview.SamplePlan
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Contact sheets for the Two skies dial: the three detail levels, and the app's dial next to the same spec in the
 * system font (what the Remote Compose widgets will draw; issue #46 asks to see it before it is final).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w760dp-h720dp-xhdpi")
class DialSheetsScreenshotTest : ScreenshotTest() {

    @Test
    fun levels() = snap("dial_levels") { Levels() }

    @Test
    fun levelsDark() = snap("dial_levels_dark", darkTheme = true) { Levels() }

    @Test
    fun levelsTwelveHour() = snap("dial_levels_12h", use24Hour = false) { Levels() }

    @Test
    fun widgetFonts() = snap("dial_widget_fonts") { WidgetFonts() }

    @Test
    fun widgetFontsDark() = snap("dial_widget_fonts_dark", darkTheme = true) { WidgetFonts() }
}

private val Sizes = listOf(280.dp to "Full · 280 dp", 160.dp to "Simple · 160 dp", 96.dp to "Glance · 96 dp")

@Composable
private fun Levels() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
        Sizes.forEach { (size, caption) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StaticTwoSkiesDial(SamplePlan.midAdaptationDial, Modifier.size(size))
                Caption(caption)
            }
        }
    }
}

@Composable
private fun WidgetFonts() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.size(width = 280.dp, height = 20.dp)) { Caption("App · Google Sans Flex") }
            Column(Modifier.size(width = 280.dp, height = 20.dp)) { Caption("Widget · system font") }
        }
        Sizes.forEach { (size, caption) ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(DialFonts.App, DialFonts.System).forEach { fonts ->
                    Column(Modifier.size(width = 280.dp, height = size), horizontalAlignment = Alignment.CenterHorizontally) {
                        StaticTwoSkiesDial(SamplePlan.midAdaptationDial, Modifier.size(size), fonts = fonts)
                    }
                }
                Caption(caption)
            }
        }
    }
}

@Composable
private fun Caption(text: String) =
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
