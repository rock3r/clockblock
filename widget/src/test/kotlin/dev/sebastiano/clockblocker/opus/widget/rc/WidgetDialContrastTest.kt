package dev.sebastiano.clockblocker.opus.widget.rc

import androidx.compose.ui.graphics.Color
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.Argb
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ColorMath
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import org.junit.jupiter.api.Test

/**
 * The widget dial reads on every widget theme: its readouts on the widget's face (WCAG 1.4.3, 4.5:1 for text; the
 * night-safe theme is dimmed on purpose and keeps 3:1, the large-text bar), the sky labels on their skies, and day
 * against night (3:1, WCAG 1.4.11): the skies differ in lightness, not only in hue, so they separate for every kind
 * of colour vision. Night-safe dims the whole sky, like the app's night-safe dial: its day stays apart from its
 * night by lightness, at a lower 1.5:1, and the labels on it still read.
 */
class WidgetDialContrastTest {

    private fun contrast(a: Argb, b: Argb) = ColorMath.contrast(Color(a.value), Color(b.value))

    @Test
    fun `dial text and skies keep their contrast on every widget theme`() = assertSoftly {
        listOf(WidgetPalette.Light, WidgetPalette.Dark, WidgetPalette.NightSafe).forEach { widget ->
            val p = WidgetDial.palette(widget)
            val text = if (widget.theme.name == "NightSafe") 3f else 4.5f
            withClue("${widget.theme} local time on the face") { contrast(p.ink, p.face) shouldBeGreaterThanOrEqual text }
            withClue("${widget.theme} body time on the face") { contrast(p.body, p.face) shouldBeGreaterThanOrEqual text }
            withClue("${widget.theme} AM/PM on the face") { contrast(p.inkMuted, p.face) shouldBeGreaterThanOrEqual 3f }
            withClue("${widget.theme} label on the day sky") { contrast(p.sky.onDay, p.sky.day) shouldBeGreaterThanOrEqual 3f }
            withClue("${widget.theme} label on the night sky") { contrast(p.sky.onNight, p.sky.night) shouldBeGreaterThanOrEqual 3f }
            val sky = if (widget.theme.name == "NightSafe") 1.5f else 3f
            withClue("${widget.theme} day against night") { contrast(p.sky.day, p.sky.night) shouldBeGreaterThanOrEqual sky }
        }
    }
}
