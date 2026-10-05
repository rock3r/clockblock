package dev.sebastiano.clockblocker.opus.core.designsystem.illustration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ArtColors
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.LocalReduceMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusFonts
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyPalette
import kotlin.math.max

/*
 * Illustration kit (design.md §2.4 "Illustration style"): a 120 × 120 art board on an 8 dp grid, 2–3 flat fills
 * from colour roles, one accent, gradients only for skies, 2 dp ink detail strokes with round caps, and depth via
 * a cut-paper offset shadow (the same path, translated 3 × 3 in a darker tone). No blur, no grain, no faces.
 */

/** Art board units → pixels. All illustration geometry is authored on a 120-unit square. */
internal class ArtScope(
    val draw: DrawScope,
    val colors: ArtColors,
    val sky: SkyPalette,
    private val textMeasurer: TextMeasurer,
    /** True while an ambient loop is running; false for the still (rest) frame. */
    val moving: Boolean,
) {
    val u: Float = minOf(draw.size.width, draw.size.height) / 120f
    private val ox = (draw.size.width - 120f * u) / 2f
    private val oy = (draw.size.height - 120f * u) / 2f

    fun x(v: Float) = ox + v * u
    fun y(v: Float) = oy + v * u
    fun p(px: Float, py: Float) = Offset(x(px), y(py))

    fun circle(cx: Float, cy: Float, r: Float) = Path().apply { addOval(Rect(p(cx, cy), r * u)) }

    fun oval(l: Float, t: Float, w: Float, h: Float) = Path().apply { addOval(Rect(x(l), y(t), x(l + w), y(t + h))) }

    fun rect(l: Float, t: Float, w: Float, h: Float) = Path().apply { addRect(Rect(x(l), y(t), x(l + w), y(t + h))) }

    fun roundRect(l: Float, t: Float, w: Float, h: Float, r: Float) = Path().apply {
        addRoundRect(RoundRect(x(l), y(t), x(l + w), y(t + h), CornerRadius(r * u)))
    }

    /** Builds a path in art units: `path { moveTo(…); lineTo(…) }` with coordinates on the 120 board. */
    inline fun path(block: ArtPath.() -> Unit): Path = ArtPath(this).apply(block).path

    /** A MaterialShape fitted into a [size]-unit square centred on ([cx], [cy]). */
    fun polygon(
        polygon: RoundedPolygon,
        cx: Float,
        cy: Float,
        size: Float,
        rotation: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
    ): Path {
        val path = polygon.unitPath()
        val m = Matrix()
        m.translate(x(cx), y(cy))
        if (rotation != 0f) m.rotateZ(rotation)
        m.scale(size * u * scaleX, size * u * scaleY)
        path.transform(m)
        return path
    }

    /** [path] rotated by [degrees] about the art-board point ([cx], [cy]) (shadows stay screen-aligned). */
    fun rotated(path: Path, degrees: Float, cx: Float, cy: Float): Path {
        if (degrees == 0f) return path
        val m = Matrix()
        m.translate(x(cx), y(cy))
        m.rotateZ(degrees)
        m.translate(-x(cx), -y(cy))
        path.transform(m)
        return path
    }

    /** [path] translated by art units. */
    fun moved(path: Path, dx: Float, dy: Float): Path = path.apply { translate(Offset(dx * u, dy * u)) }

    fun minus(a: Path, b: Path) = Path().apply { op(a, b, PathOperation.Difference) }
    fun intersect(a: Path, b: Path) = Path().apply { op(a, b, PathOperation.Intersect) }
    fun union(a: Path, b: Path) = Path().apply { op(a, b, PathOperation.Union) }

    /** Fill with the cut-paper shadow underneath. */
    fun paper(path: Path, color: Color, shadow: Boolean = true) = with(draw) {
        if (shadow) translate(3f * u, 3f * u) { drawPath(path, colors.shadow) }
        drawPath(path, color)
    }

    fun fill(path: Path, color: Color, alpha: Float = 1f) = draw.drawPath(path, color, alpha = alpha)

    fun fill(path: Path, brush: Brush) = draw.drawPath(path, brush)

    /** 2 dp ink detail stroke, round caps. */
    fun ink(path: Path, width: Float = 2f, color: Color = colors.ink, dash: FloatArray? = null) =
        draw.drawPath(
            path,
            color,
            style = Stroke(
                width * u,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = dash?.let { d -> PathEffect.dashPathEffect(FloatArray(d.size) { d[it] * u }) },
            ),
        )

    fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float = 2f, color: Color = colors.ink, alpha: Float = 1f) =
        draw.drawLine(color, p(x0, y0), p(x1, y1), width * u, cap = StrokeCap.Round, alpha = alpha)

    fun dot(cx: Float, cy: Float, r: Float, color: Color, alpha: Float = 1f) =
        draw.drawCircle(color, r * u, p(cx, cy), alpha = alpha)

    /** Fraunces (editorial serif) glyphs centred on ([cx], [cy]); [size] in art units. */
    fun serifText(text: String, cx: Float, cy: Float, size: Float, color: Color, alpha: Float = 1f, weight: Int = 600) {
        val style = TextStyle(
            fontFamily = OpusFonts.serif(weight, opticalSize = 72f),
            fontSize = with(draw) { (size * u).toSp() },
            color = color.copy(alpha = color.alpha * alpha),
        )
        val layout = textMeasurer.measure(text, style)
        draw.drawText(layout, topLeft = Offset(x(cx) - layout.size.width / 2f, y(cy) - layout.size.height / 2f))
    }
}

/** Path builder in art units. */
internal class ArtPath(private val scope: ArtScope) {
    val path = Path()
    fun moveTo(x: Float, y: Float) = path.moveTo(scope.x(x), scope.y(y))
    fun lineTo(x: Float, y: Float) = path.lineTo(scope.x(x), scope.y(y))
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) = path.quadraticTo(scope.x(x1), scope.y(y1), scope.x(x2), scope.y(y2))
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        path.cubicTo(scope.x(x1), scope.y(y1), scope.x(x2), scope.y(y2), scope.x(x3), scope.y(y3))

    fun arcTo(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, forceMoveTo: Boolean = false) =
        path.arcTo(Rect(scope.p(cx, cy), r * scope.u), startDeg, sweepDeg, forceMoveTo)

    fun close() = path.close()
}

/**
 * The polygon's outline as a path normalised to a unit square centred on the origin (−0.5…0.5). Built straight
 * from the cubics, so drawing a static MaterialShape allocates no [androidx.graphics.shapes.Morph].
 */
private val unitPathCache = HashMap<RoundedPolygon, Path>()

internal fun RoundedPolygon.unitPath(): Path = Path().apply { addPath(sharedUnitPath()) }

/** Like [unitPath] but returns the shared cached instance: draw it (under a transform), never mutate it. */
internal fun RoundedPolygon.sharedUnitPath(): Path =
    synchronized(unitPathCache) {
        unitPathCache.getOrPut(this) {
            val b = calculateBounds()
            val extent = max(b[2] - b[0], b[3] - b[1]).coerceAtLeast(1e-6f)
            val cx = (b[0] + b[2]) / 2f
            val cy = (b[1] + b[3]) / 2f
            fun nx(v: Float) = (v - cx) / extent
            fun ny(v: Float) = (v - cy) / extent
            Path().apply {
                cubics.forEachIndexed { i, c ->
                    if (i == 0) moveTo(nx(c.anchor0X), ny(c.anchor0Y))
                    cubicTo(nx(c.control0X), ny(c.control0Y), nx(c.control1X), ny(c.control1Y), nx(c.anchor1X), ny(c.anchor1Y))
                }
                close()
            }
        }
    }

/**
 * Hosts an illustration. When [animated] and motion is allowed, [ambientPeriodMillis] drives a 0→1 looping
 * phase; otherwise the phase rests at [restPhase] (the still is always a complete picture). The phase is only
 * read inside the draw lambda.
 */
@Composable
internal fun ArtCanvas(
    modifier: Modifier,
    contentDescription: String?,
    animated: Boolean,
    ambientPeriodMillis: Int,
    restPhase: Float = 0f,
    reverse: Boolean = false,
    draw: ArtScope.(phase: Float) -> Unit,
) {
    val colors = OpusTheme.artColors
    val sky = OpusTheme.sky
    val measurer = rememberTextMeasurer()
    val moving = animated && !LocalReduceMotion.current && ambientPeriodMillis > 0
    val phase = rememberArtPhase(moving, ambientPeriodMillis, restPhase, reverse)
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.size(120.dp).then(semantics)) {
        ArtScope(this, colors, sky, measurer, moving).draw(phase.value)
    }
}

@Composable
private fun rememberArtPhase(enabled: Boolean, periodMillis: Int, rest: Float, reverse: Boolean): State<Float> {
    if (!enabled || periodMillis <= 0) return remember(rest) { mutableFloatStateOf(rest) }
    val transition = rememberInfiniteTransition(label = "art")
    return transition.animateFloat(0f, 1f, OpusTheme.motion.ambientLoop(periodMillis, reverse), label = "artPhase")
}

/**
 * Plays an entrance from 0 to [target] once (art-entrance token, or the bouncy glyph-morph token) when
 * [animated] and motion is allowed; otherwise sits at [target]. The "played" flag is saveable (T-017): coming
 * back to a screen (back stack, configuration change) shows the art at rest instead of replaying its entrance.
 * Later changes of [target] still animate.
 */
@Composable
internal fun rememberEntrance(target: Float, animated: Boolean, bouncy: Boolean = false): State<Float> {
    val reduce = LocalReduceMotion.current
    val motion = OpusTheme.motion
    val play = animated && !reduce
    var played by rememberSaveable { mutableStateOf(false) }
    val value = remember { Animatable(if (play && !played) 0f else target) }
    LaunchedEffect(target, play) {
        if (play) value.animateTo(target, if (bouncy) motion.glyphMorph() else motion.artEntrance()) else value.snapTo(target)
        played = true
    }
    return value.asState()
}

/**
 * Follows [target]: starts there (no entrance) and animates later changes with the container-spatial token when
 * [animated] and motion is allowed; snaps otherwise.
 */
@Composable
internal fun rememberArtValue(target: Float, animated: Boolean): State<Float> {
    val play = animated && !LocalReduceMotion.current
    val motion = OpusTheme.motion
    val value = remember { Animatable(target) }
    LaunchedEffect(target, play) {
        if (play) value.animateTo(target, motion.containerSpatial()) else value.snapTo(target)
    }
    return value.asState()
}

internal fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
