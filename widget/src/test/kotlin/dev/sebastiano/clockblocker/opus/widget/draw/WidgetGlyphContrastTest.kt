package dev.sebastiano.clockblocker.opus.widget.draw

import androidx.compose.ui.graphics.Color
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ColorMath
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import org.junit.jupiter.api.Test

/**
 * Widget glyphs never fill a shape with the pale vivid advice colour (that is only the dial arc): they draw the
 * advice container silhouette with a mark in the on-container colour, and the mark is what has to read
 * (WCAG 1.4.11, 3:1 for graphical objects), on every widget theme (#32).
 */
class WidgetGlyphContrastTest {

    @Test
    fun `glyph marks stand out from their container on every widget theme`() = assertSoftly {
        listOf(WidgetPalette.Light, WidgetPalette.Dark, WidgetPalette.NightSafe).forEach { palette ->
            AdviceType.entries.forEach { type ->
                val (container, mark) = Glyphs.colors(GlyphKind.Advice(type), palette)
                withClue("${palette.theme} $type mark on container") {
                    ColorMath.contrast(Color(mark), Color(container)) shouldBeGreaterThanOrEqual 3f
                }
            }
        }
    }
}
