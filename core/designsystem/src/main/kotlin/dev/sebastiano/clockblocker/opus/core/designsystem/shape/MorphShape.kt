package dev.sebastiano.clockblocker.opus.core.designsystem.shape

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.toPath
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.max

/**
 * A [Morph] between two [RoundedPolygon]s (typically `MaterialShapes`) that knows how to fit itself into a box.
 * Fitting uses the union of both shapes' bounds, made square and centred, so the morph never "breathes"
 * because of normalisation.
 */
@Immutable
class ShapeMorph(val start: RoundedPolygon, val end: RoundedPolygon) {
    val morph: Morph = Morph(start, end)

    private val left: Float
    private val top: Float
    private val extent: Float

    init {
        val a = start.calculateBounds()
        val b = end.calculateBounds()
        val l = minOf(a[0], b[0])
        val t = minOf(a[1], b[1])
        val r = maxOf(a[2], b[2])
        val btm = maxOf(a[3], b[3])
        extent = max(r - l, btm - t).coerceAtLeast(1e-6f)
        left = (l + r) / 2f - extent / 2f
        top = (t + btm) / 2f - extent / 2f
    }

    /**
     * The morph at [progress] (0 = [start], 1 = [end]; values slightly outside follow a spring's overshoot) as a
     * path fitted into [size], rotated by [rotationDegrees] and scaled by [scale] about the centre.
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun toPath(
        progress: Float,
        size: Size,
        rotationDegrees: Float = 0f,
        scale: Float = 1f,
        out: Path = Path(),
    ): Path {
        out.rewind()
        morph.toPath(progress.coerceIn(-0.2f, 1.2f), out)
        val side = minOf(size.width, size.height)
        val k = side / extent * scale
        val matrix = Matrix()
        matrix.translate(size.width / 2f, size.height / 2f)
        if (rotationDegrees != 0f) matrix.rotateZ(rotationDegrees)
        matrix.scale(k, k)
        matrix.translate(-(left + extent / 2f), -(top + extent / 2f))
        out.transform(matrix)
        return out
    }

    override fun equals(other: Any?): Boolean = other is ShapeMorph && other.start == start && other.end == end
    override fun hashCode(): Int = 31 * start.hashCode() + end.hashCode()
}

/** An immutable [Shape] snapshot of a [ShapeMorph] at a fixed progress, for `clip`/`background`/`border`. */
@Immutable
class MorphShape(
    private val shapeMorph: ShapeMorph,
    private val progress: Float,
    private val rotationDegrees: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(shapeMorph.toPath(progress, size, rotationDegrees))

    override fun equals(other: Any?): Boolean =
        other is MorphShape && other.shapeMorph == shapeMorph && other.progress == progress &&
            other.rotationDegrees == rotationDegrees

    override fun hashCode(): Int = (shapeMorph.hashCode() * 31 + progress.hashCode()) * 31 + rotationDegrees.hashCode()
}

/** A [RoundedPolygon] as a fitted [Shape] (static; no morph). */
fun RoundedPolygon.asShape(rotationDegrees: Float = 0f): Shape = MorphShape(ShapeMorph(this, this), 0f, rotationDegrees)

/**
 * Draws [shapeMorph] at [progress] into the current draw scope. Call from a draw lambda so animated values are
 * read in the draw phase.
 */
fun DrawScope.drawMorph(
    shapeMorph: ShapeMorph,
    progress: Float,
    color: Color,
    rotationDegrees: Float = 0f,
    scale: Float = 1f,
    style: DrawStyle = Fill,
    path: Path = Path(),
) {
    drawPath(shapeMorph.toPath(progress, size, rotationDegrees, scale, path), color, style = style)
}

/** Draws a static polygon fitted into [size] at the scope origin. */
fun DrawScope.drawPolygon(
    polygon: RoundedPolygon,
    color: Color,
    size: Size = this.size,
    rotationDegrees: Float = 0f,
    style: DrawStyle = Fill,
) {
    drawPath(ShapeMorph(polygon, polygon).toPath(0f, size, rotationDegrees), color, style = style)
}
