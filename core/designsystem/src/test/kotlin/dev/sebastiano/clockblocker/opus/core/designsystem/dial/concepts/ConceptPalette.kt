package dev.sebastiano.clockblocker.opus.core.designsystem.dial.concepts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.AdviceColors
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.model.AdviceType

/**
 * Colours for the concepts, resolved once from the theme into plain values (the widget would resolve the same
 * roles from its WidgetPalette). Day and night differ mostly in lightness, so they separate for every kind of
 * colour vision; the advice arc adds a pattern and a label, never colour alone.
 */
data class ConceptPalette(
    val dark: Boolean,
    val card: Color,
    val face: Color,
    val ink: Color,
    val inkMuted: Color,
    val hairline: Color,
    val body: Color,
    val onBody: Color,
    val bodyContainer: Color,
    val onBodyContainer: Color,
    val night: Color,
    val day: Color,
    val dawn: Color,
    val dusk: Color,
    val twilight: Color,
    val onDay: Color,
    val onNight: Color,
    val sun: Color,
    val sunEdge: Color,
    val advice: AdviceColors,
) {
    /** Sky colour at a local minute: night → violet → warm glow → day, and back (≈ 2 h of twilight each side). */
    fun sky(minute: Float, sunrise: Float, sunset: Float): Color {
        val frames = listOf(
            sunrise - 75f to night,
            sunrise - 30f to twilight,
            sunrise + 10f to dawn,
            sunrise + 55f to day,
            sunset - 55f to day,
            sunset - 10f to dusk,
            sunset + 30f to twilight,
            sunset + 75f to night,
        ).map { (m, c) -> m.mod(1440f) to c }.sortedBy { it.first }
        val m = minute.mod(1440f)
        val nextIndex = frames.indexOfFirst { it.first > m }.let { if (it == -1) 0 else it }
        val prevIndex = (nextIndex - 1 + frames.size) % frames.size
        val (ma, ca) = frames[prevIndex]
        val (mb, cb) = frames[nextIndex]
        val span = (mb - ma).mod(1440f).let { if (it == 0f) 1440f else it }
        val t = ((m - ma).mod(1440f) / span).coerceIn(0f, 1f)
        return lerp(ca, cb, t * t * (3f - 2f * t))
    }

    fun isNight(minute: Float, sunrise: Float, sunset: Float): Boolean {
        val m = minute.mod(1440f)
        return if (sunrise < sunset) m < sunrise || m >= sunset else m in sunset..sunrise
    }

    /** Text colour on top of [sky]. */
    fun onSky(minute: Float, sunrise: Float, sunset: Float) = if (isNight(minute, sunrise, sunset)) onNight else onDay

    /** Arc fill for an advice type: chosen to stay apart from both the night and the day sky. */
    fun adviceFill(type: AdviceType): Color {
        val role = advice[type]
        return if (type == AdviceType.AvoidLight && !dark) role.container else role.color
    }

    /** Text / pattern colour on top of [adviceFill]. */
    fun adviceInk(type: AdviceType): Color {
        val role = advice[type]
        return if (type == AdviceType.AvoidLight && !dark) role.color else role.onColor
    }

    /** The strong tone of an advice type (glyph discs, edges). */
    fun adviceStrong(type: AdviceType): Color = advice[type].color

    companion object {
        @Composable
        fun current(dark: Boolean): ConceptPalette {
            val c = MaterialTheme.colorScheme
            return if (!dark) {
                ConceptPalette(
                    dark = false,
                    card = c.surfaceContainer,
                    face = c.surfaceContainerLowest,
                    ink = c.onSurface,
                    inkMuted = c.onSurfaceVariant,
                    hairline = c.outlineVariant,
                    body = c.primary,
                    onBody = c.onPrimary,
                    bodyContainer = c.primaryContainer,
                    onBodyContainer = c.onPrimaryContainer,
                    night = Color(0xFF1E2461),
                    day = Color(0xFFA6D2FF),
                    dawn = Color(0xFFF7A27C),
                    dusk = Color(0xFFFFAE70),
                    twilight = Color(0xFF7458A6),
                    onDay = Color(0xFF0D1A3C),
                    onNight = Color(0xFFE6E8FF),
                    sun = Color(0xFFFFB000),
                    sunEdge = Color(0xFF8A5A00),
                    advice = OpusTheme.adviceColors,
                )
            } else {
                ConceptPalette(
                    dark = true,
                    card = c.surfaceContainer,
                    face = c.surfaceContainerLow,
                    ink = c.onSurface,
                    inkMuted = c.onSurfaceVariant,
                    hairline = c.outlineVariant,
                    body = c.primary,
                    onBody = c.onPrimary,
                    bodyContainer = c.primaryContainer,
                    onBodyContainer = c.onPrimaryContainer,
                    night = Color(0xFF2E3680),
                    day = Color(0xFF7FA9DC),
                    dawn = Color(0xFFD08A66),
                    dusk = Color(0xFFD49460),
                    twilight = Color(0xFF5E4D94),
                    onDay = Color(0xFF0A1530),
                    onNight = Color(0xFFE6E8FF),
                    sun = Color(0xFFFFB000),
                    sunEdge = Color(0x00000000),
                    advice = OpusTheme.adviceColors,
                )
            }
        }
    }
}
