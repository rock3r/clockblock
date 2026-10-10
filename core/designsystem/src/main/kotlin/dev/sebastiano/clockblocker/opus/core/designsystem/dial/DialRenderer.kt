package dev.sebastiano.clockblocker.opus.core.designsystem.dial

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.graphics.shapes.RoundedPolygon
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.AdviceShapes
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.Argb
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialOp
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialSpec
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialTextMeasurer
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.HAlign
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.TextSpec
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.VAlign
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.ShapeMorph
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockFonts
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockThemeVariant
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.FlexAxes
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Turns a [TextSpec] into a Compose [TextStyle] (family, weight, slant). The renderer adds the size, tracking and
 * figures. [App] uses the app's Google Sans Flex recipes; [System] is what a Remote Compose widget gets (the
 * device's font, italic for the body clock).
 */
fun interface DialFonts {
    fun style(spec: TextSpec): TextStyle

    companion object {
        /** Google Sans Flex: body time on the `slnt −10, ROND 100` recipe, optical size following the size. */
        val App = DialFonts { spec ->
            TextStyle(
                fontFamily = ClockblockFonts.sans(
                    FlexAxes(
                        weight = spec.weight,
                        round = if (spec.slanted) 100f else 0f,
                        slant = if (spec.slanted) -10f else 0f,
                        opticalSize = spec.size.roundToInt().coerceIn(6, 144).toFloat(),
                    ),
                ),
                fontWeight = FontWeight(spec.weight),
            )
        }

        /** The device font, as on a widget: weights as they come, the body clock in italic. */
        val System = DialFonts { spec ->
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight(spec.weight),
                fontStyle = if (spec.slanted) FontStyle.Italic else FontStyle.Normal,
            )
        }

        /** Opus mode: Fraunces everywhere (the body clock synthesised italic). */
        val Opus = DialFonts { spec ->
            TextStyle(
                fontFamily = ClockblockFonts.serif(spec.weight, spec.size.roundToInt().coerceIn(9, 144).toFloat()),
                fontWeight = FontWeight(spec.weight),
                fontStyle = if (spec.slanted) FontStyle.Italic else FontStyle.Normal,
            )
        }

        /** The 8-bit easter egg: times go monospace, words keep the app's face. */
        val Pixel = DialFonts { spec ->
            if (spec.tabular) TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight(spec.weight)) else App.style(spec)
        }
    }
}

/** The dial's fonts for the current theme (Opus mode and the 8-bit egg have their own). */
@Composable
fun rememberDialFonts(): DialFonts = when {
    ClockblockTheme.pixelMode -> DialFonts.Pixel
    ClockblockTheme.variant == ClockblockThemeVariant.Opus -> DialFonts.Opus
    else -> DialFonts.App
}

/**
 * The full [TextStyle] for [spec]: sized in dp whatever the font scale (the spec already sized it). [Density]
 * does the dp → sp step so that non-linear font scaling (large text grows less) doesn't shrink the big readouts.
 */
internal fun DialFonts.textStyle(spec: TextSpec, density: Density): TextStyle = style(spec).copy(
    fontSize = with(density) { spec.size.dp.toSp() },
    letterSpacing = spec.tracking.em,
    fontFeatureSettings = if (spec.tabular) "tnum" else null,
)

/** Exact text widths from Compose, for the spec's layout decisions. */
class ComposeDialTextMeasurer(
    private val measurer: TextMeasurer,
    private val fonts: DialFonts,
    private val density: Density,
) : DialTextMeasurer {
    override fun width(text: String, spec: TextSpec): Float =
        measurer.measure(text, fonts.textStyle(spec, density)).size.width / density.density
}

/** Paints every op of [spec] (dp) in this draw scope (px). [pixelGlyphs]: the 8-bit egg's pixel shapes. */
fun DrawScope.drawDialSpec(spec: DialSpec, measurer: TextMeasurer, fonts: DialFonts, pixelGlyphs: Boolean = false) {
    spec.ops.forEach { render(it, measurer, fonts, pixelGlyphs) }
}

private val Argb.color: Color get() = Color(value)

private fun DrawScope.render(op: DialOp, measurer: TextMeasurer, fonts: DialFonts, pixelGlyphs: Boolean) {
    val d = density
    when (op) {
        is DialOp.Circle -> drawCircle(
            op.color.color, op.r * d, Offset(op.cx * d, op.cy * d),
            style = if (op.stroke > 0f) Stroke(op.stroke * d) else Fill,
        )
        is DialOp.Arc -> drawArc(
            color = op.color.color,
            startAngle = op.startDeg,
            sweepAngle = op.sweepDeg,
            useCenter = false,
            topLeft = Offset((op.cx - op.r) * d, (op.cy - op.r) * d),
            size = Size(2 * op.r * d, 2 * op.r * d),
            style = Stroke(op.width * d, cap = if (op.roundCap) StrokeCap.Round else StrokeCap.Butt),
        )
        is DialOp.Line -> drawLine(
            op.color.color,
            Offset(op.x0 * d, op.y0 * d),
            Offset(op.x1 * d, op.y1 * d),
            strokeWidth = op.width * d,
            cap = if (op.roundCap) StrokeCap.Round else StrokeCap.Butt,
        )
        is DialOp.Rect -> drawRoundRect(
            op.color.color,
            topLeft = Offset(op.left * d, op.top * d),
            size = Size((op.right - op.left) * d, (op.bottom - op.top) * d),
            cornerRadius = CornerRadius(op.radius * d),
            style = if (op.stroke > 0f) Stroke(op.stroke * d) else Fill,
        )
        is DialOp.Glyph -> glyph(op, pixelGlyphs)
        is DialOp.Text -> text(op, measurer, fonts)
        is DialOp.CurvedText -> curved(op, measurer, fonts)
        is DialOp.SkyBar -> drawRoundRect(
            Brush.horizontalGradient(op.colors.map { it.color }, startX = op.left * d, endX = op.right * d),
            topLeft = Offset(op.left * d, op.top * d),
            size = Size((op.right - op.left) * d, (op.bottom - op.top) * d),
            cornerRadius = CornerRadius(op.radius * d),
        )
        is DialOp.SweepRing -> {
            val c = Offset(op.cx * d, op.cy * d)
            val colors = op.colors.map { it.color }
            rotate(op.rotationDeg, pivot = c) {
                drawCircle(Brush.sweepGradient(colors + colors.first(), center = c), op.r * d, c, style = Stroke(op.width * d))
            }
        }
    }
}

private val morphs = HashMap<RoundedPolygon, ShapeMorph>()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun DrawScope.glyph(op: DialOp.Glyph, pixel: Boolean) {
    val d = density
    val c = Offset(op.cx * d, op.cy * d)
    drawCircle(op.disc.color, op.discRadius * d, c)
    op.ring?.let { drawCircle(it.color, op.discRadius * d, c, style = Stroke(1.5f * d)) }
    val s = op.size * d
    val polygon = if (pixel) AdviceShapes.pixel(op.type) else AdviceShapes.target(op.type)
    val morph = synchronized(morphs) { morphs.getOrPut(polygon) { ShapeMorph(polygon, polygon) } }
    val path: Path = morph.toPath(0f, Size(s, s), if (pixel) 0f else AdviceShapes.baseRotation(op.type))
    translate(c.x - s / 2f, c.y - s / 2f) { drawPath(path, op.color.color) }
}

private fun DrawScope.text(op: DialOp.Text, measurer: TextMeasurer, fonts: DialFonts) {
    val d = density
    val layout = measurer.measure(op.text, fonts.textStyle(op.spec, this))
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
    drawText(layout, op.color.color, topLeft = Offset(x, y))
}

/** Glyph-by-glyph along the circle; on the lower half it runs the other way so it reads left to right. */
private fun DrawScope.curved(op: DialOp.CurvedText, measurer: TextMeasurer, fonts: DialFonts) {
    val d = density
    val style = fonts.textStyle(op.spec, this)
    val full = measurer.measure(op.text, style)
    val total = full.size.width.toFloat()
    val r = op.r * d
    val inward = op.readsInward
    val cx = op.cx * d
    val cy = op.cy * d
    val color = op.color.color
    for (i in op.text.indices) {
        if (op.text[i].isWhitespace()) continue
        val box = full.getBoundingBox(i)
        val along = box.center.x - total / 2f
        val deltaDeg = Math.toDegrees((along / r).toDouble()).toFloat()
        val a = if (inward) op.centerDeg - deltaDeg else op.centerDeg + deltaDeg
        val rad = Math.toRadians(a.toDouble())
        val p = Offset(cx + r * cos(rad).toFloat(), cy + r * sin(rad).toFloat())
        val ch = measurer.measure(op.text[i].toString(), style)
        rotate(degrees = if (inward) a - 90f else a + 90f, pivot = p) {
            drawText(ch, color, topLeft = Offset(p.x - ch.size.width / 2f, p.y - full.size.height / 2f))
        }
    }
}
