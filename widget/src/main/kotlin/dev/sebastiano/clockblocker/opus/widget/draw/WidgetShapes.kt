package dev.sebastiano.clockblocker.opus.widget.draw

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.RoundedPolygon
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.AdviceShapes
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * `MaterialShapes` polygons baked into [DrawOp.Path]s in widget unit space. The app's shape language (design.md §2.4
 * "shapes carry meaning") comes from [AdviceShapes]; the widgets reuse it so a glyph on the home screen has the same
 * silhouette as in the app. Polygons are fitted the same way the app fits them (`ShapeMorph`): the bounds made square
 * and centred, so every shape fills the same box.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object WidgetShapes {

    /** The advice's signature silhouette, rotated so it reads right (pill lying down, bun flat). */
    fun advice(type: AdviceType, color: Int, radius: Float = 1f, cx: Float = 0f, cy: Float = 0f): DrawOp.Path =
        path(AdviceShapes.target(type), color, radius, cx, cy, AdviceShapes.baseRotation(type) + flightRotation(type))

    /** Sun head of the dial hand by day ([MaterialShapes.Sunny]). */
    fun sun(color: Int, radius: Float, cx: Float, cy: Float): DrawOp.Path = path(MaterialShapes.Sunny, color, radius, cx, cy)

    /** The CBTmin marker on the body ring ([MaterialShapes.PuffyDiamond], as in the app's dial). */
    fun puffyDiamond(color: Int, radius: Float, cx: Float, cy: Float): DrawOp.Path =
        path(MaterialShapes.PuffyDiamond, color, radius, cx, cy)

    /** Fully adapted ([MaterialShapes.Flower], the app's "bloom"). */
    fun flower(color: Int, radius: Float = 1f): DrawOp.Path = path(MaterialShapes.Flower, color, radius, 0f, 0f)

    /** A rounded square-ish container for the free / no-trip states ([MaterialShapes.Cookie4Sided] is caffeine's). */
    fun square(color: Int, radius: Float = 1f): DrawOp.Path = path(MaterialShapes.Square, color, radius, 0f, 0f)

    /**
     * [polygon] fitted into a square of half-side [radius] centred on ([cx], [cy]), rotated by [rotationDeg]
     * (clockwise, about its centre).
     */
    fun path(
        polygon: RoundedPolygon,
        color: Int,
        radius: Float,
        cx: Float,
        cy: Float,
        rotationDeg: Float = 0f,
        stroke: Float = 0f,
    ): DrawOp.Path {
        val b = polygon.calculateBounds()
        val extent = max(b[2] - b[0], b[3] - b[1]).coerceAtLeast(1e-6f)
        val mx = (b[0] + b[2]) / 2f
        val my = (b[1] + b[3]) / 2f
        val k = 2f * radius / extent
        val rad = Math.toRadians(rotationDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        fun x(px: Float, py: Float) = cx + ((px - mx) * c - (py - my) * s) * k
        fun y(px: Float, py: Float) = cy + ((px - mx) * s + (py - my) * c) * k
        val cubics = polygon.cubics
        val segments = buildList {
            val first = cubics.first()
            add(PathSegment.MoveTo(x(first.anchor0X, first.anchor0Y), y(first.anchor0X, first.anchor0Y)))
            cubics.forEach { q ->
                add(
                    PathSegment.CubicTo(
                        x(q.control0X, q.control0Y), y(q.control0X, q.control0Y),
                        x(q.control1X, q.control1Y), y(q.control1X, q.control1Y),
                        x(q.anchor1X, q.anchor1Y), y(q.anchor1X, q.anchor1Y),
                    ),
                )
            }
            add(PathSegment.Close)
        }
        return DrawOp.Path(segments, color, stroke)
    }

    /** MaterialShapes' Arrow points up; the widget's flight mark points north-east (take-off). */
    private fun flightRotation(type: AdviceType) = if (type == AdviceType.Flight) 45f else 0f
}
