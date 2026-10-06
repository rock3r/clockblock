package dev.sebastiano.clockblocker.opus.widget.draw

/**
 * A tiny backend-neutral display list. Coordinates are in a unit space centred on (0, 0) with y pointing down;
 * a backend maps them to pixels with `centre + value * unit`. Angles follow android.graphics.Canvas
 * (degrees, 0 = 3 o'clock, clockwise).
 *
 * The same list is replayed into a Remote Compose canvas (host-rendered, API 36+) and into an
 * android.graphics.Canvas bitmap (classic RemoteViews fallback), so both widget backends look identical.
 */
sealed interface DrawOp {
    val color: Int

    /** Filled circle, or a ring when [stroke] > 0. */
    data class Circle(val cx: Float, val cy: Float, val r: Float, override val color: Int, val stroke: Float = 0f) :
        DrawOp

    /** Stroked arc of radius [r] around ([cx], [cy]); a filled pie slice when [fill] is true. */
    data class Arc(
        val cx: Float,
        val cy: Float,
        val r: Float,
        val startDeg: Float,
        val sweepDeg: Float,
        override val color: Int,
        val stroke: Float,
        val roundCap: Boolean = false,
        val fill: Boolean = false,
    ) : DrawOp

    data class Line(
        val x0: Float,
        val y0: Float,
        val x1: Float,
        val y1: Float,
        override val color: Int,
        val stroke: Float,
        val roundCap: Boolean = true,
    ) : DrawOp

    /** Filled rounded rectangle, or an outline when [stroke] > 0. */
    data class RoundRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val radius: Float,
        override val color: Int,
        val stroke: Float = 0f,
    ) : DrawOp

    /**
     * A filled (or, when [stroke] > 0, stroked) path: `MaterialShapes` silhouettes baked into unit space at render
     * time (see [WidgetShapes]), so both backends draw the exact same outline.
     */
    data class Path(val segments: List<PathSegment>, override val color: Int, val stroke: Float = 0f) : DrawOp

    /**
     * A ring of radius [r] and width [stroke] filled with a sweep gradient around ([cx], [cy]). [colors] are spread
     * evenly over the full turn starting at [startDeg] (Canvas angles); the first colour should repeat at the end for a
     * seamless loop. [color] is the flat fallback (and the colour other code can read).
     */
    data class SweepRing(
        val cx: Float,
        val cy: Float,
        val r: Float,
        val stroke: Float,
        val colors: List<Int>,
        val startDeg: Float,
        override val color: Int = colors.first(),
    ) : DrawOp
}

/** One step of a [DrawOp.Path], in the same unit space as every other op. */
sealed interface PathSegment {
    data class MoveTo(val x: Float, val y: Float) : PathSegment

    data class LineTo(val x: Float, val y: Float) : PathSegment

    data class CubicTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x: Float,
        val y: Float,
    ) : PathSegment

    data object Close : PathSegment
}

/**
 * [DrawOp.SweepRing] as [count] constant-colour arcs (each overlapping the next a little so no seam shows), for
 * backends without gradient shaders. Colours are interpolated at each arc's midpoint.
 */
fun DrawOp.SweepRing.segments(count: Int = 72): List<DrawOp.Arc> {
    val step = 360f / count
    val stops = colors.size - 1
    return (0 until count).map { i ->
        val t = (i + 0.5f) / count * stops
        val lo = t.toInt().coerceIn(0, stops - 1)
        val c = WidgetPalette.mix(colors[lo], colors[lo + 1], t - lo)
        DrawOp.Arc(cx, cy, r, startDeg + i * step, step + 0.6f, c, stroke)
    }
}

/** This op scaled by [k] about the origin, then moved by ([dx], [dy]) (unit space). */
fun DrawOp.scaled(k: Float, dy: Float = 0f, dx: Float = 0f): DrawOp {
    if (k == 1f && dx == 0f && dy == 0f) return this
    fun x(v: Float) = v * k + dx
    fun y(v: Float) = v * k + dy
    return when (this) {
        is DrawOp.Circle -> copy(cx = x(cx), cy = y(cy), r = r * k, stroke = stroke * k)
        is DrawOp.Arc -> copy(cx = x(cx), cy = y(cy), r = r * k, stroke = stroke * k)
        is DrawOp.Line -> copy(x0 = x(x0), y0 = y(y0), x1 = x(x1), y1 = y(y1), stroke = stroke * k)
        is DrawOp.RoundRect ->
            copy(left = x(left), top = y(top), right = x(right), bottom = y(bottom), radius = radius * k, stroke = stroke * k)
        is DrawOp.Path -> copy(
            segments = segments.map { s ->
                when (s) {
                    is PathSegment.MoveTo -> PathSegment.MoveTo(x(s.x), y(s.y))
                    is PathSegment.LineTo -> PathSegment.LineTo(x(s.x), y(s.y))
                    is PathSegment.CubicTo -> PathSegment.CubicTo(x(s.x1), y(s.y1), x(s.x2), y(s.y2), x(s.x), y(s.y))
                    PathSegment.Close -> s
                }
            },
            stroke = stroke * k,
        )
        is DrawOp.SweepRing -> copy(cx = x(cx), cy = y(cy), r = r * k, stroke = stroke * k)
    }
}
