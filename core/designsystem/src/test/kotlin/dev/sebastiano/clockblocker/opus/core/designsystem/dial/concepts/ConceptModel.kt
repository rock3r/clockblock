package dev.sebastiano.clockblocker.opus.core.designsystem.dial.concepts

import androidx.compose.ui.graphics.Color
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import kotlin.math.cos
import kotlin.math.sin

/*
 * DESIGN EXPLORATION for issue #46 (dial redesign). Not production code.
 *
 * Every concept is a pure function `spec(state, palette, widthDp, heightDp) -> List<DialOp>`. The op list is the
 * "one shared spec": the Compose renderer in ConceptRenderer.kt replays it on a Canvas, and a Remote Compose
 * renderer would replay the same list on a RemoteCanvas (every op maps to a RemoteDrawScope call: drawArc,
 * drawLine, drawCircle, drawRoundRect, drawPath, drawAnchoredText, drawTextOnCircle). Coordinates are dp, origin
 * top-left, angles in Canvas degrees (0 = 3 o'clock, clockwise).
 */

/** Detail level, picked from the dial's actual size in dp (not the widget bucket). */
enum class DetailLevel {
    /** < 110 dp: a 1×1 widget. */
    Glance,

    /** 110–250 dp: a 2×2 widget, or the dial inside a 4×2. */
    Simple,

    /** ≥ 250 dp: large widgets and the in-app hero. */
    Full;

    companion object {
        fun forSize(minSideDp: Float): DetailLevel = when {
            minSideDp < 110f -> Glance
            minSideDp < 250f -> Simple
            else -> Full
        }
    }
}

/** One advice window in display-zone local minutes. [label] is the short card label ("Avoid light"). */
data class ConceptAdvice(
    val type: AdviceType,
    val startMinute: Float,
    val endMinute: Float,
    val label: String,
) {
    val sweepMinutes: Float get() = (endMinute - startMinute).mod(1440f)
}

/** The state every concept renders. Minutes are local (display-zone) minutes of the day. */
data class ConceptState(
    val city: String,
    val dayLabel: String,
    val localMinute: Float,
    /** Body clock minus local clock, minutes. −420 = the body is 7 h behind. */
    val bodyOffsetMinutes: Float,
    val sunriseMinute: Float,
    val sunsetMinute: Float,
    val current: ConceptAdvice?,
    val next: ConceptAdvice?,
) {
    val bodyMinute: Float get() = (localMinute + bodyOffsetMinutes).mod(1440f)
    val aligned: Boolean get() = kotlin.math.abs(bodyOffsetMinutes) < 30f

    /** The body's sky is the local sky turned by the jet lag: identical rings once adapted. */
    fun bodySunrise(): Float = (sunriseMinute - bodyOffsetMinutes).mod(1440f)
    fun bodySunset(): Float = (sunsetMinute - bodyOffsetMinutes).mod(1440f)

    /** "7 h behind", "in sync". */
    fun offsetWords(): String = when {
        aligned -> "in sync"
        bodyOffsetMinutes < 0 -> "${hoursText()} behind"
        else -> "${hoursText()} ahead"
    }

    /** "−7 h" (app-wide sign convention: body relative to local). */
    fun offsetSigned(): String = if (aligned) "0 h" else (if (bodyOffsetMinutes < 0) "\u2212" else "+") + hoursText()

    private fun hoursText(): String {
        val halves = Math.round(kotlin.math.abs(bodyOffsetMinutes) / 30f)
        val whole = halves / 2
        return if (halves % 2 == 1) "${whole}\u00BD h" else "$whole h"
    }

    companion object {
        /** Lisbon → Tokyo, day 2, 15:20 in Tokyo, body 7 h behind, avoid light until 16:30, then melatonin. */
        val MidAdaptation = ConceptState(
            city = "Tokyo",
            dayLabel = "Day 2",
            localMinute = 15 * 60f + 20f,
            bodyOffsetMinutes = -420f,
            sunriseMinute = 5 * 60f + 45f,
            sunsetMinute = 17 * 60f + 15f,
            current = ConceptAdvice(AdviceType.AvoidLight, 13 * 60f + 30f, 16 * 60f + 30f, "Avoid light"),
            next = ConceptAdvice(AdviceType.Melatonin, 16 * 60f + 30f, 16 * 60f + 30f, "Melatonin"),
        )

        /** Day 5, 09:10 in Tokyo: adapted. */
        val Adapted = ConceptState(
            city = "Tokyo",
            dayLabel = "Day 5",
            localMinute = 9 * 60f + 10f,
            bodyOffsetMinutes = 0f,
            sunriseMinute = 5 * 60f + 48f,
            sunsetMinute = 17 * 60f + 11f,
            current = ConceptAdvice(AdviceType.SeeLight, 7 * 60f + 30f, 10 * 60f + 30f, "See some light"),
            next = ConceptAdvice(AdviceType.OptionalNap, 14 * 60f, 14 * 60f + 30f, "Nap if tired"),
        )
    }
}

fun hhmm(minute: Float): String {
    val m = Math.round(minute).mod(1440)
    return "%02d:%02d".format(m / 60, m % 60)
}

/** Noon at the top, midnight at the bottom, clockwise. */
fun dialAngle(minute: Float): Float = (minute / 4f + 90f).mod(360f)

data class Pt(val x: Float, val y: Float)

fun polar(cx: Float, cy: Float, r: Float, deg: Float): Pt {
    val rad = Math.toRadians(deg.toDouble())
    return Pt(cx + r * cos(rad).toFloat(), cy + r * sin(rad).toFloat())
}

/** Glyph identities. Advice glyphs reuse the app's MaterialShapes (AdviceShapes); the rest are dial marks. */
sealed interface GlyphKind {
    data class Advice(val type: AdviceType) : GlyphKind
    data object Sun : GlyphKind
    data object Moon : GlyphKind
    data object Adapted : GlyphKind
}

enum class HAlign { Start, Center, End }
enum class VAlign { Top, Center, Baseline, Bottom }

/** Type recipe. [slanted] = body clock (`slnt −10, ROND 100`); upright = local. Sizes are dp. */
data class TextSpec(
    val size: Float,
    val weight: Int = 500,
    val slanted: Boolean = false,
    val caps: Boolean = false,
    val tracking: Float = 0f,
    val tabular: Boolean = true,
)

/** The shared display list. All coordinates are dp. */
sealed interface DialOp {
    /** Filled circle, or a ring when [stroke] > 0. */
    data class Circle(val cx: Float, val cy: Float, val r: Float, val color: Color, val stroke: Float = 0f) : DialOp

    /** Stroked arc (centre radius [r], width [width]). */
    data class Arc(
        val cx: Float,
        val cy: Float,
        val r: Float,
        val width: Float,
        val startDeg: Float,
        val sweepDeg: Float,
        val color: Color,
        val roundCap: Boolean = false,
    ) : DialOp

    data class Line(
        val x0: Float,
        val y0: Float,
        val x1: Float,
        val y1: Float,
        val color: Color,
        val width: Float,
        val roundCap: Boolean = true,
    ) : DialOp

    /** Filled (or stroked) rounded rectangle. */
    data class Rect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val radius: Float,
        val color: Color,
        val stroke: Float = 0f,
    ) : DialOp

    /** A glyph silhouette of side [size] centred on ([cx], [cy]); optional disc behind it. */
    data class Glyph(
        val kind: GlyphKind,
        val cx: Float,
        val cy: Float,
        val size: Float,
        val color: Color,
        val disc: Color? = null,
        val discRadius: Float = 0f,
        val ring: Color? = null,
    ) : DialOp

    /** Straight text anchored at ([x], [y]). RC: drawAnchoredText. */
    data class Text(
        val text: String,
        val x: Float,
        val y: Float,
        val spec: TextSpec,
        val color: Color,
        val h: HAlign = HAlign.Center,
        val v: VAlign = VAlign.Center,
    ) : DialOp

    /**
     * Text along a circle of radius [r], centred on [centerDeg]. Flips automatically on the lower half so it
     * always reads left to right. RC: drawTextOnCircle (fallback: per-label rotate() + drawAnchoredText).
     */
    data class CurvedText(
        val text: String,
        val cx: Float,
        val cy: Float,
        val r: Float,
        val centerDeg: Float,
        val spec: TextSpec,
        val color: Color,
    ) : DialOp

    /** [ops] clipped to a rounded rectangle. RC: clipPath(RemotePath) around the children. */
    data class Clip(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val radius: Float,
        val ops: List<DialOp>,
    ) : DialOp

    /**
     * A ring filled with a sweep gradient: [colors] spread evenly over the full turn starting at 3 o'clock.
     * Compose: Brush.sweepGradient. RC: RemotePaint shader if the host takes it, else [segments] (constant arcs).
     */
    data class SweepRing(val cx: Float, val cy: Float, val r: Float, val width: Float, val colors: List<Color>) : DialOp {
        fun segments(): List<Arc> {
            val step = 360f / colors.size
            return colors.mapIndexed { i, c -> Arc(cx, cy, r, width, i * step, step + 0.35f, c) }
        }
    }

    /** A rounded bar filled with a left-to-right gradient of evenly spaced [colors]. RC: shader or segment rects. */
    data class GradientBar(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val radius: Float,
        val colors: List<Color>,
    ) : DialOp
}
