package dev.sebastiano.clockblocker.opus.core.designsystem.dial.concepts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon as Polygon
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.AdviceShapes
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.ShapeMorph
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.FlexAxes
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusFonts
import kotlin.math.sin

/** Replays a [DialOp] list on a Compose Canvas of [widthDp] × [heightDp]. */
@Composable
fun ConceptCanvas(ops: List<DialOp>, widthDp: Float, heightDp: Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 256)
    Canvas(modifier.size(widthDp.dp, heightDp.dp)) {
        ops.forEach { render(it, measurer) }
    }
}

private val morphCache = HashMap<Polygon, ShapeMorph>()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun polygonFor(kind: GlyphKind): Pair<Polygon, Float>? = when (kind) {
    is GlyphKind.Advice -> AdviceShapes.target(kind.type) to AdviceShapes.baseRotation(kind.type)
    GlyphKind.Sun -> MaterialShapes.Sunny to 0f
    GlyphKind.Adapted -> MaterialShapes.Flower to 0f
    GlyphKind.Moon -> null
}

fun textStyle(spec: TextSpec, fontScale: Float): TextStyle = TextStyle(
    fontFamily = OpusFonts.sans(
        FlexAxes(
            weight = spec.weight,
            round = if (spec.slanted) 100f else 0f,
            slant = if (spec.slanted) -10f else 0f,
            opticalSize = spec.size.coerceIn(6f, 144f),
        ),
    ),
    fontWeight = FontWeight(spec.weight),
    fontSize = (spec.size / fontScale).sp,
    letterSpacing = spec.tracking.em,
    fontFeatureSettings = if (spec.tabular) "tnum" else null,
)

private fun DrawScope.render(op: DialOp, measurer: TextMeasurer) {
    val d = density
    when (op) {
        is DialOp.Circle -> if (op.stroke > 0f) {
            drawCircle(op.color, op.r * d, Offset(op.cx * d, op.cy * d), style = Stroke(op.stroke * d))
        } else {
            drawCircle(op.color, op.r * d, Offset(op.cx * d, op.cy * d))
        }
        is DialOp.Arc -> drawArc(
            color = op.color,
            startAngle = op.startDeg,
            sweepAngle = op.sweepDeg,
            useCenter = false,
            topLeft = Offset((op.cx - op.r) * d, (op.cy - op.r) * d),
            size = Size(2 * op.r * d, 2 * op.r * d),
            style = Stroke(op.width * d, cap = if (op.roundCap) StrokeCap.Round else StrokeCap.Butt),
        )
        is DialOp.Line -> drawLine(
            op.color,
            Offset(op.x0 * d, op.y0 * d),
            Offset(op.x1 * d, op.y1 * d),
            strokeWidth = op.width * d,
            cap = if (op.roundCap) StrokeCap.Round else StrokeCap.Butt,
        )
        is DialOp.Rect -> drawRoundRect(
            op.color,
            topLeft = Offset(op.left * d, op.top * d),
            size = Size((op.right - op.left) * d, (op.bottom - op.top) * d),
            cornerRadius = CornerRadius(op.radius * d),
            style = if (op.stroke > 0f) Stroke(op.stroke * d) else androidx.compose.ui.graphics.drawscope.Fill,
        )
        is DialOp.Glyph -> glyph(op)
        is DialOp.Text -> text(op, measurer)
        is DialOp.CurvedText -> curved(op, measurer)
        is DialOp.Clip -> {
            val path = Path().apply {
                addRoundRect(
                    RoundRect(op.left * d, op.top * d, op.right * d, op.bottom * d, CornerRadius(op.radius * d)),
                )
            }
            clipPath(path) { op.ops.forEach { render(it, measurer) } }
        }
        is DialOp.SweepRing -> drawCircle(
            brush = Brush.sweepGradient(op.colors + op.colors.first(), center = Offset(op.cx * d, op.cy * d)),
            radius = op.r * d,
            center = Offset(op.cx * d, op.cy * d),
            style = Stroke(op.width * d),
        )
        is DialOp.GradientBar -> drawRoundRect(
            brush = Brush.horizontalGradient(op.colors, startX = op.left * d, endX = op.right * d),
            topLeft = Offset(op.left * d, op.top * d),
            size = Size((op.right - op.left) * d, (op.bottom - op.top) * d),
            cornerRadius = CornerRadius(op.radius * d),
        )
    }
}

private fun DrawScope.glyph(op: DialOp.Glyph) {
    val d = density
    val c = Offset(op.cx * d, op.cy * d)
    op.disc?.let { drawCircle(it, op.discRadius * d, c) }
    op.ring?.let { drawCircle(it, op.discRadius * d, c, style = Stroke(1.5f * d)) }
    val s = op.size * d
    val poly = polygonFor(op.kind)
    if (poly != null) {
        val morph = morphCache.getOrPut(poly.first) { ShapeMorph(poly.first, poly.first) }
        val path = morph.toPath(0f, Size(s, s), poly.second)
        translate(c.x - s / 2f, c.y - s / 2f) { drawPath(path, op.color) }
    } else {
        // Crescent: a disc minus an offset disc.
        val r = s / 2f
        val a = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
        val b = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.42f, c.y - r * 0.30f), r * 0.86f)) }
        val crescent = Path().apply { op(a, b, PathOperation.Difference) }
        drawPath(crescent, op.color)
    }
}

private fun DrawScope.text(op: DialOp.Text, measurer: TextMeasurer) {
    val d = density
    val s = if (op.spec.caps) op.text.uppercase() else op.text
    val layout = measurer.measure(s, textStyle(op.spec, fontScale))
    val w = layout.size.width.toFloat()
    val h = layout.size.height.toFloat()
    val x = when (op.h) {
        HAlign.Start -> op.x * d
        HAlign.Center -> op.x * d - w / 2f
        HAlign.End -> op.x * d - w
    }
    val y = when (op.v) {
        VAlign.Top -> op.y * d
        VAlign.Center -> op.y * d - h / 2f
        VAlign.Baseline -> op.y * d - layout.firstBaseline
        VAlign.Bottom -> op.y * d - h
    }
    drawText(layout, op.color, topLeft = Offset(x, y))
}

/** Per-character placement along the circle; flips on the lower half so the label reads left to right. */
private fun DrawScope.curved(op: DialOp.CurvedText, measurer: TextMeasurer) {
    val d = density
    val s = if (op.spec.caps) op.text.uppercase() else op.text
    val style = textStyle(op.spec, fontScale)
    val full = measurer.measure(s, style)
    val total = full.size.width.toFloat()
    val r = op.r * d
    val lower = sin(Math.toRadians(op.centerDeg.toDouble())) > 0.05
    val cx = op.cx * d
    val cy = op.cy * d
    for (i in s.indices) {
        if (s[i] == ' ') continue
        val box = full.getBoundingBox(i)
        val along = box.center.x - total / 2f
        val deltaDeg = Math.toDegrees((along / r).toDouble()).toFloat()
        val a = if (lower) op.centerDeg - deltaDeg else op.centerDeg + deltaDeg
        val p = polar(cx, cy, r, a)
        val ch = measurer.measure(s[i].toString(), style)
        rotate(degrees = if (lower) a - 90f else a + 90f, pivot = Offset(p.x, p.y)) {
            drawText(
                ch,
                op.color,
                topLeft = Offset(p.x - ch.size.width / 2f, p.y - full.size.height / 2f),
            )
        }
    }
}

/** Angular length (degrees) a curved label of [spec] needs at radius [r] (approximation for layout decisions). */
fun approxTextWidth(text: String, spec: TextSpec): Float {
    val perChar = if (spec.caps) 0.66f else 0.56f
    return text.length * spec.size * (perChar + spec.tracking)
}

fun arcDegreesFor(text: String, spec: TextSpec, r: Float): Float =
    Math.toDegrees((approxTextWidth(text, spec) / r).toDouble()).toFloat()
