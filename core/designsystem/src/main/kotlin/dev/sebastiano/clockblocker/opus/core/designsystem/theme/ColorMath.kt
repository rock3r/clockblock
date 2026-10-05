package dev.sebastiano.clockblocker.opus.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Small perceptual colour helpers in Oklab/OkLCh. Pure functions (unit-tested) so the advice palette and the sky
 * can follow dynamic colour without pulling in material-color-utilities.
 */
object ColorMath {

    /** OkLCh hue in degrees [0, 360). */
    fun hue(color: Color): Float {
        val lab = color.convert(ColorSpaces.Oklab)
        val h = Math.toDegrees(atan2(lab.blue.toDouble(), lab.green.toDouble())).toFloat()
        return (h + 360f) % 360f
    }

    /** OkLCh chroma. */
    fun chroma(color: Color): Float {
        val lab = color.convert(ColorSpaces.Oklab)
        return hypot(lab.green, lab.blue)
    }

    /** Oklab lightness 0..1. */
    fun lightness(color: Color): Float = color.convert(ColorSpaces.Oklab).red

    /** Signed shortest angular distance from [from] to [to], in (-180, 180]. */
    fun hueDelta(from: Float, to: Float): Float {
        var d = (to - from) % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /**
     * Rotates [color]'s hue towards [toward] by half the hue distance, capped at [maxDegrees] (same contract as
     * Material's `Blend.harmonize`, but in OkLCh). Lightness and chroma are preserved, so contrast pairs survive.
     */
    fun harmonize(color: Color, toward: Color, maxDegrees: Float = 15f): Color {
        if (chroma(color) < 0.02f || chroma(toward) < 0.02f) return color
        val from = hue(color)
        val delta = hueDelta(from, hue(toward))
        val rotation = min(abs(delta) * 0.5f, maxDegrees) * if (delta < 0) -1f else 1f
        return withLch(color, hue = from + rotation)
    }

    /** Scales Oklab lightness and chroma; used to derive dim night-safe roles from regular ones. */
    fun dim(color: Color, lightnessFactor: Float, chromaFactor: Float = lightnessFactor): Color {
        val lab = color.convert(ColorSpaces.Oklab)
        return Color(
            red = (lab.red * lightnessFactor).coerceIn(0f, 1f),
            green = lab.green * chromaFactor,
            blue = lab.blue * chromaFactor,
            alpha = color.alpha,
            colorSpace = ColorSpaces.Oklab,
        ).convert(ColorSpaces.Srgb).clampSrgb()
    }

    /** Linear interpolation in Oklab (perceptually even, no muddy midpoints). */
    fun mix(a: Color, b: Color, t: Float): Color {
        val x = a.convert(ColorSpaces.Oklab)
        val y = b.convert(ColorSpaces.Oklab)
        val f = t.coerceIn(0f, 1f)
        return Color(
            red = x.red + (y.red - x.red) * f,
            green = x.green + (y.green - x.green) * f,
            blue = x.blue + (y.blue - x.blue) * f,
            alpha = x.alpha + (y.alpha - x.alpha) * f,
            colorSpace = ColorSpaces.Oklab,
        ).convert(ColorSpaces.Srgb).clampSrgb()
    }

    /** Sets Oklab lightness to [lightness] and scales chroma by [chromaFactor], keeping the hue. */
    fun withLightness(color: Color, lightness: Float, chromaFactor: Float = 1f): Color {
        val lab = color.convert(ColorSpaces.Oklab)
        return Color(
            red = lightness.coerceIn(0f, 1f),
            green = lab.green * chromaFactor,
            blue = lab.blue * chromaFactor,
            alpha = color.alpha,
            colorSpace = ColorSpaces.Oklab,
        ).convert(ColorSpaces.Srgb).clampSrgb()
    }

    /** [foreground] (possibly translucent) composited over an opaque [background]. */
    fun over(foreground: Color, background: Color): Color {
        val a = foreground.alpha
        return Color(
            red = foreground.red * a + background.red * (1f - a),
            green = foreground.green * a + background.green * (1f - a),
            blue = foreground.blue * a + background.blue * (1f - a),
        )
    }

    /** WCAG 2 contrast ratio (1..21) between two opaque colours. */
    fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    private fun withLch(color: Color, hue: Float): Color {
        val lab = color.convert(ColorSpaces.Oklab)
        val c = hypot(lab.green, lab.blue)
        val rad = Math.toRadians(hue.toDouble())
        return Color(
            red = lab.red,
            green = (c * cos(rad)).toFloat(),
            blue = (c * sin(rad)).toFloat(),
            alpha = color.alpha,
            colorSpace = ColorSpaces.Oklab,
        ).convert(ColorSpaces.Srgb).clampSrgb()
    }

    private fun Color.clampSrgb(): Color =
        Color(red.coerceIn(0f, 1f), green.coerceIn(0f, 1f), blue.coerceIn(0f, 1f), alpha.coerceIn(0f, 1f))
}
