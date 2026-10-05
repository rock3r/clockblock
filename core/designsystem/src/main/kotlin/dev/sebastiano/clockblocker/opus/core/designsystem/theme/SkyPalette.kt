package dev.sebastiano.clockblocker.opus.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import java.time.LocalTime
import kotlin.math.abs

/** The six named skies of the ramp (design.md §2.4 "Sky ramp"). */
enum class SkyPhase { Night, PreDawn, Dawn, Day, Golden, Dusk }

/** A two-stop vertical sky: [top] at the zenith, [bottom] at the horizon. */
@Immutable
data class SkyGradient(val top: Color, val bottom: Color) {
    fun verticalBrush(startY: Float = 0f, endY: Float = Float.POSITIVE_INFINITY): Brush =
        Brush.verticalGradient(listOf(top, bottom), startY = startY, endY = endY)

    /** A single representative colour (for thin rings and widgets). */
    val mid: Color get() = ColorMath.mix(top, bottom, 0.5f)
}

/**
 * Sky gradients indexed by clock time (solar time for the local ring, *body* time for the body-clock sky).
 * Keyframes are anchored to sunrise and sunset and interpolated cyclically in Oklab, so the sky never jumps.
 */
@Immutable
class SkyPalette internal constructor(private val skies: Map<SkyPhase, SkyGradient>) {

    operator fun get(phase: SkyPhase): SkyGradient = skies.getValue(phase)

    /** Keyframes (hour of day → phase) for a day with the given sunrise/sunset hours. */
    internal fun keyframes(sunriseHour: Float, sunsetHour: Float): List<Pair<Float, SkyPhase>> = listOf(
        (sunriseHour - 2.0f) to SkyPhase.Night,
        (sunriseHour - 0.75f) to SkyPhase.PreDawn,
        (sunriseHour + 0.25f) to SkyPhase.Dawn,
        (sunriseHour + 2.25f) to SkyPhase.Day,
        (sunsetHour - 1.75f) to SkyPhase.Day,
        (sunsetHour - 0.5f) to SkyPhase.Golden,
        (sunsetHour + 0.5f) to SkyPhase.Dusk,
        (sunsetHour + 1.75f) to SkyPhase.Night,
    ).map { (h, p) -> wrap(h) to p }.sortedBy { it.first }

    /** The dominant (nearest keyframe) phase at [hour] (0–24, fractional). */
    fun phaseAt(hour: Float, sunriseHour: Float = DefaultSunrise, sunsetHour: Float = DefaultSunset): SkyPhase {
        val h = wrap(hour)
        return keyframes(sunriseHour, sunsetHour).minBy { circularDistance(it.first, h) }.second
    }

    /** Smoothly interpolated gradient at [hour] (0–24, fractional). */
    fun gradientAt(hour: Float, sunriseHour: Float = DefaultSunrise, sunsetHour: Float = DefaultSunset): SkyGradient {
        val h = wrap(hour)
        val frames = keyframes(sunriseHour, sunsetHour)
        // Find the segment [a, b) containing h, cyclically.
        val nextIndex = frames.indexOfFirst { it.first > h }.let { if (it == -1) 0 else it }
        val prevIndex = (nextIndex - 1 + frames.size) % frames.size
        val (ha, pa) = frames[prevIndex]
        val (hb, pb) = frames[nextIndex]
        val span = wrap(hb - ha).let { if (it == 0f) 24f else it }
        val t = smoothstep(wrap(h - ha) / span)
        val a = get(pa)
        val b = get(pb)
        return SkyGradient(ColorMath.mix(a.top, b.top, t), ColorMath.mix(a.bottom, b.bottom, t))
    }

    fun gradientAt(time: LocalTime, sunriseHour: Float = DefaultSunrise, sunsetHour: Float = DefaultSunset) =
        gradientAt(time.toHourFloat(), sunriseHour, sunsetHour)

    /** Blends every sky [fraction] towards [color] (design: 15 % towards primary when dynamic colour is on). */
    fun blendedToward(color: Color, fraction: Float = 0.15f): SkyPalette = SkyPalette(
        skies.mapValues { (_, g) -> SkyGradient(ColorMath.mix(g.top, color, fraction), ColorMath.mix(g.bottom, color, fraction)) },
    )

    /** Night-safe: same structure, much lower luminance. */
    fun dimmed(): SkyPalette = SkyPalette(
        skies.mapValues { (_, g) -> SkyGradient(ColorMath.dim(g.top, 0.34f, 0.4f), ColorMath.dim(g.bottom, 0.34f, 0.4f)) },
    )

    /**
     * Dark theme: the same skies with their highlights compressed (Oklab lightness above [DarkKnee] scaled by
     * [DarkRatio], chroma eased), so a daytime body sky sits among dark surfaces instead of glaring above them.
     * Night skies are already below the knee and stay as they are; hues are kept, so the meaning (what time it is
     * inside you) survives.
     */
    fun forDarkTheme(): SkyPalette = SkyPalette(
        skies.mapValues { (_, g) -> SkyGradient(g.top.compressedForDark(), g.bottom.compressedForDark()) },
    )

    private fun Color.compressedForDark(): Color {
        val l = ColorMath.lightness(this)
        if (l <= DarkKnee) return this
        return ColorMath.withLightness(this, DarkKnee + (l - DarkKnee) * DarkRatio, chromaFactor = 0.85f)
    }

    /** Per-sky blend towards [to] (theme cross-fade). */
    internal fun lerp(to: SkyPalette, t: Float): SkyPalette = SkyPalette(
        skies.mapValues { (phase, g) ->
            val o = to[phase]
            SkyGradient(androidx.compose.ui.graphics.lerp(g.top, o.top, t), androidx.compose.ui.graphics.lerp(g.bottom, o.bottom, t))
        },
    )

    override fun equals(other: Any?): Boolean = other is SkyPalette && other.skies == skies
    override fun hashCode(): Int = skies.hashCode()

    companion object {
        const val DefaultSunrise = 6.5f
        const val DefaultSunset = 19.0f

        /** Oklab lightness above which dark-theme skies are compressed, and by how much. */
        private const val DarkKnee = 0.30f
        private const val DarkRatio = 0.45f

        val Default: SkyPalette = SkyPalette(
            mapOf(
                SkyPhase.Night to SkyGradient(Color(0xFF0B1026), Color(0xFF1B1F4B)),
                SkyPhase.PreDawn to SkyGradient(Color(0xFF2A2E6E), Color(0xFF6B4E9B)),
                SkyPhase.Dawn to SkyGradient(Color(0xFFF49D6E), Color(0xFFFFD29D)),
                SkyPhase.Day to SkyGradient(Color(0xFF8EC5FF), Color(0xFFDDEFFF)),
                SkyPhase.Golden to SkyGradient(Color(0xFFFFB36B), Color(0xFFFF7E6B)),
                SkyPhase.Dusk to SkyGradient(Color(0xFF6B4E9B), Color(0xFF2A2E6E)),
            ),
        )

        internal fun wrap(hour: Float): Float = ((hour % 24f) + 24f) % 24f

        private fun circularDistance(a: Float, b: Float): Float {
            val d = abs(a - b) % 24f
            return if (d > 12f) 24f - d else d
        }

        private fun smoothstep(x: Float): Float {
            val t = x.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}

/** 14:30 → 14.5f */
fun LocalTime.toHourFloat(): Float = hour + minute / 60f + second / 3600f
