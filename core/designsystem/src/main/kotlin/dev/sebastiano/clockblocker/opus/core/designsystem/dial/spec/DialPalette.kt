package dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec

import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt

/** Measures text for layout decisions (where a label goes, how wide a pill is). Widths are dp. */
fun interface DialTextMeasurer {
    fun width(text: String, spec: TextSpec): Float
}

/**
 * A font-free estimate: ≈ 0.56 em per character (0.66 for capitals) plus tracking. Close enough for Google Sans
 * Flex and Roboto at dial sizes; renderers with real fonts pass an exact measurer.
 */
object ApproxTextMeasurer : DialTextMeasurer {
    override fun width(text: String, spec: TextSpec): Float {
        val perChar = if (spec.caps) 0.66f else 0.56f
        return text.length * spec.size * (perChar + spec.tracking)
    }
}

/** One advice type's colours: the vivid [color] (glyph discs, arc edges), [onColor] on it, the [container]. */
data class DialAdviceColors(val color: Argb, val onColor: Argb, val container: Argb)

/**
 * The sky's keyframe colours. Day and night differ mostly in lightness, so they separate for every kind of colour
 * vision; [onDay] / [onNight] are the label inks on top of them.
 */
data class DialSkyColors(
    val night: Argb,
    val twilight: Argb,
    val dawn: Argb,
    val day: Argb,
    val dusk: Argb,
    val onDay: Argb,
    val onNight: Argb,
)

/**
 * Everything the dial paints with, resolved to plain ARGB. The app builds it from the theme (light, dark,
 * Night-safe, Opus), the widget from its own palette, a watch face from its own.
 */
data class DialPalette(
    /** Dark surfaces: the rings get hairline edges and the avoid-light arc uses its vivid tone. */
    val dark: Boolean,
    val face: Argb,
    val ink: Argb,
    val inkMuted: Argb,
    val hairline: Argb,
    /** The body clock's colour (slanted body time). */
    val body: Argb,
    val bodyContainer: Argb,
    val onBodyContainer: Argb,
    val sky: DialSkyColors,
    val advice: Map<AdviceType, DialAdviceColors>,
) {
    fun advice(type: AdviceType): DialAdviceColors = advice.getValue(type)

    /**
     * Sky colour at [minute] for a day with the given [sunrise] and [sunset] (all minutes of the day): night →
     * violet twilight → warm glow → day, and back, about 2 h of twilight each side. Interpolated in Oklab with a
     * smoothstep so the ring never bands.
     */
    fun sky(minute: Float, sunrise: Float, sunset: Float): Argb {
        val frames = listOf(
            sunrise - 75f to sky.night,
            sunrise - 30f to sky.twilight,
            sunrise + 10f to sky.dawn,
            sunrise + 55f to sky.day,
            sunset - 55f to sky.day,
            sunset - 10f to sky.dusk,
            sunset + 30f to sky.twilight,
            sunset + 75f to sky.night,
        ).map { (m, c) -> m.mod(1440f) to c }.sortedBy { it.first }
        val m = minute.mod(1440f)
        val nextIndex = frames.indexOfFirst { it.first > m }.let { if (it == -1) 0 else it }
        val prevIndex = (nextIndex - 1 + frames.size) % frames.size
        val (ma, ca) = frames[prevIndex]
        val (mb, cb) = frames[nextIndex]
        val span = (mb - ma).mod(1440f).let { if (it == 0f) 1440f else it }
        val t = ((m - ma).mod(1440f) / span).coerceIn(0f, 1f)
        return ArgbMath.lerp(ca, cb, t * t * (3f - 2f * t))
    }

    /** Whether [minute] falls between sunset and sunrise. */
    fun isNight(minute: Float, sunrise: Float, sunset: Float): Boolean {
        val m = minute.mod(1440f)
        return if (sunrise < sunset) m < sunrise || m >= sunset else m >= sunset && m < sunrise
    }

    /** Arc fill for an advice type, chosen to stay apart from both the night and the day sky. */
    fun adviceFill(type: AdviceType): Argb {
        val role = advice(type)
        return if (type == AdviceType.AvoidLight && !dark) role.container else role.color
    }

    /** Pattern ink on top of [adviceFill]. */
    fun adviceInk(type: AdviceType): Argb {
        val role = advice(type)
        return if (type == AdviceType.AvoidLight && !dark) role.color else role.onColor
    }
}

/** Colour arithmetic without a UI toolkit. */
object ArgbMath {
    /** Oklab interpolation from [a] to [b] (alpha linearly). */
    fun lerp(a: Argb, b: Argb, t: Float): Argb {
        if (t <= 0f) return a
        if (t >= 1f) return b
        val la = toOklab(a)
        val lb = toOklab(b)
        val mixed = DoubleArray(3) { la[it] + (lb[it] - la[it]) * t }
        val alpha = a.alpha + (b.alpha - a.alpha) * t
        return fromOklab(mixed, alpha)
    }

    private fun channel(c: Int, shift: Int): Double = ((c shr shift) and 0xFF) / 255.0

    private fun toLinear(v: Double): Double = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    private fun toGamma(v: Double): Double = if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1 / 2.4) - 0.055

    private fun toOklab(c: Argb): DoubleArray {
        val r = toLinear(channel(c.value, 16))
        val g = toLinear(channel(c.value, 8))
        val b = toLinear(channel(c.value, 0))
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private fun fromOklab(lab: DoubleArray, alpha: Float): Argb {
        val l = (lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2]).pow(3)
        val m = (lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2]).pow(3)
        val s = (lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2]).pow(3)
        val r = toGamma(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s)
        val g = toGamma(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s)
        val b = toGamma(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)
        fun byte(v: Double) = (v.coerceIn(0.0, 1.0) * 255.0).roundToInt()
        return Argb(((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b))
    }
}
