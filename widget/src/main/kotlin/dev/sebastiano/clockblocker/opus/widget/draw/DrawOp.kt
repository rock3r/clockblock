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
}
