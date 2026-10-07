package dev.sebastiano.clockblocker.opus.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.AdviceGlyph
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.label
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w360dp-h900dp-xhdpi")
class GlyphScreenshotTest : ScreenshotTest() {

    @Test
    fun glyphsLight() = snap("glyphs_light") { GlyphTable() }

    @Test
    fun glyphsDark() = snap("glyphs_dark", darkTheme = true) { GlyphTable() }

    @Test
    fun glyphsNightSafe() = snap("glyphs_night_safe", nightSafe = true) { GlyphTable() }
}

@Composable
private fun GlyphTable() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AdviceType.entries.forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AdviceGlyph(type, active = false, size = 44.dp)
                AdviceGlyph(type, active = true, size = 44.dp)
                Text(
                    type.label(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(200.dp),
                )
            }
        }
    }
}
