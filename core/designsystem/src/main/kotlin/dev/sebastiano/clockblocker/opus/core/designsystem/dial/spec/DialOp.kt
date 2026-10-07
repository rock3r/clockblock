package dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec

import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import kotlin.math.roundToInt

/*
 * The dial's renderer-agnostic display list (issue #46). A spec builder ([TwoSkies]) turns a dial state into a
 * [DialSpec]: plain shapes in dp with ARGB colours and no Android UI types, so the in-app Compose renderer, the
 * Remote Compose widget renderer and a future Wear face all replay the same marks. Coordinates are dp, origin
 * top-left; angles are canvas degrees (0° = 3 o'clock, clockwise).
 */

/** A colour as packed ARGB (`0xAARRGGBB`), so the spec carries no UI-toolkit colour type. */
@JvmInline
value class Argb(val value: Int) {
    val alpha: Float get() = ((value ushr 24) and 0xFF) / 255f

    /** The same colour with its alpha replaced by [alpha] (0–1). */
    fun withAlpha(alpha: Float): Argb = Argb((value and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24))

    override fun toString(): String = "Argb(#%08X)".format(value)

    companion object {
        fun of(argb: Long): Argb = Argb(argb.toInt())
        val Transparent = Argb(0)
    }
}

/**
 * A type recipe. Sizes are dp. [slanted] marks the body clock (the app's `slnt −10, ROND 100` recipe, or italic in
 * the system font); upright is local time. [caps] says the text is already upper case (for measuring).
 */
data class TextSpec(
    val size: Float,
    val weight: Int = 500,
    val slanted: Boolean = false,
    val caps: Boolean = false,
    /** Letter spacing in em. */
    val tracking: Float = 0f,
    /** Tabular figures, so times don't jitter as they change. */
    val tabular: Boolean = true,
)

enum class HAlign { Start, Center, End }
enum class VAlign { Top, Center, Baseline, Bottom }

/** What a mark means, so renderers (and tests) can find it: e.g. Remote Compose animates only the [Needle]. */
enum class DialPart {
    Face,
    LocalSky,
    BodySky,
    RingLabel,
    Numeral,
    Advice,
    Narration,
    Needle,
    NowMark,
    Readout,
    Offset,
}

/** One mark. Every op says which [DialPart] it belongs to. */
sealed interface DialOp {
    val part: DialPart

    /** Filled circle, or a ring of width [stroke] when it is > 0. */
    data class Circle(
        val cx: Float,
        val cy: Float,
        val r: Float,
        val color: Argb,
        val stroke: Float = 0f,
        override val part: DialPart,
    ) : DialOp

    /** Stroked arc: centre radius [r], width [width]. */
    data class Arc(
        val cx: Float,
        val cy: Float,
        val r: Float,
        val width: Float,
        val startDeg: Float,
        val sweepDeg: Float,
        val color: Argb,
        val roundCap: Boolean = false,
        override val part: DialPart,
    ) : DialOp

    data class Line(
        val x0: Float,
        val y0: Float,
        val x1: Float,
        val y1: Float,
        val color: Argb,
        val width: Float,
        val roundCap: Boolean = true,
        override val part: DialPart,
    ) : DialOp

    /** Filled (or stroked, when [stroke] > 0) rounded rectangle. */
    data class Rect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val radius: Float,
        val color: Argb,
        val stroke: Float = 0f,
        override val part: DialPart,
    ) : DialOp

    /**
     * An advice glyph (the app's MaterialShape for [type]) of side [size] centred on ([cx], [cy]), drawn in
     * [color] on a disc of [discRadius] in [disc], with an optional [ring] around the disc to lift it off the
     * marks below.
     */
    data class Glyph(
        val type: AdviceType,
        val cx: Float,
        val cy: Float,
        val size: Float,
        val color: Argb,
        val disc: Argb,
        val discRadius: Float,
        val ring: Argb? = null,
        override val part: DialPart = DialPart.Advice,
    ) : DialOp

    /** Straight text anchored at ([x], [y]). RC: drawAnchoredText. */
    data class Text(
        val text: String,
        val x: Float,
        val y: Float,
        val spec: TextSpec,
        val color: Argb,
        val h: HAlign = HAlign.Center,
        val v: VAlign = VAlign.Center,
        override val part: DialPart,
    ) : DialOp

    /**
     * Text along a circle of radius [r], centred on [centerDeg]. On the lower half of the dial it runs the other
     * way ([readsInward]) so it always reads left to right. RC: drawTextOnCircle (fallback: per-glyph rotate +
     * text).
     */
    data class CurvedText(
        val text: String,
        val cx: Float,
        val cy: Float,
        val r: Float,
        val centerDeg: Float,
        val spec: TextSpec,
        val color: Argb,
        override val part: DialPart,
        /**
         * True: the text runs counter-clockwise with its tops towards the centre (the lower half). Defaults from
         * [centerDeg]; a builder sets it to keep a run of labels reading the same way.
         */
        val readsInward: Boolean = readsInwardAt(centerDeg),
    ) : DialOp {
        companion object {
            /** Lower half of the dial (with a little hysteresis round 3 and 9 o'clock). */
            fun readsInwardAt(centerDeg: Float): Boolean = kotlin.math.sin(Math.toRadians(centerDeg.toDouble())) > 0.05
        }
    }

    /**
     * A ring filled with a sweep gradient: [colors] are evenly spaced stops round the full turn, stop `i` at canvas
     * angle `i × 360 / n` before the ring is turned clockwise by [rotationDeg]. Compose: `Brush.sweepGradient`.
     * Remote Compose (no shader from Kotlin yet): [segments].
     */
    data class SweepRing(
        val cx: Float,
        val cy: Float,
        val r: Float,
        val width: Float,
        val colors: List<Argb>,
        val rotationDeg: Float = 0f,
        override val part: DialPart,
    ) : DialOp {
        /** The ring as constant-colour arcs centred on each stop (slightly overlapping, so no seams show). */
        fun segments(): List<Arc> {
            val step = 360f / colors.size
            return colors.mapIndexed { i, c ->
                Arc(cx, cy, r, width, i * step - step / 2f + rotationDeg, step + 0.35f, c, part = part)
            }
        }

        /** The colour drawn at canvas angle [deg] (nearest stop; renderers interpolate between stops). */
        fun colorAt(deg: Float): Argb {
            val step = 360f / colors.size
            val i = Math.round((deg - rotationDeg).mod(360f) / step) % colors.size
            return colors[i]
        }
    }
}

/**
 * A laid-out dial: [ops] in paint order inside a [width] × [height] dp box, the [level] they were built for, and
 * the hit-test geometry hosts need (the hub, where a tap returns to now, has radius [hubRadius] round the centre).
 */
data class DialSpec(
    val level: DetailLevel,
    val width: Float,
    val height: Float,
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val hubRadius: Float,
    val ops: List<DialOp>,
)
