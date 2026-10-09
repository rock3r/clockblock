package dev.sebastiano.clockblocker.opus.widget.rc

import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.text.RemoteTypeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.Argb
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialOp
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialPart
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialSpec
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.HAlign
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.LiveClock
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.LiveTime
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.TextSpec
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.VAlign
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetShapes
import dev.sebastiano.clockblocker.opus.widget.text.DialType
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * What the host's own clock drives on a widget dial: the clocks' UTC offsets at capture (display zone, body clock)
 * and how times are written. [am] / [pm] are the locale's markers, shown on 12-hour clocks only.
 */
internal data class LiveClocks(
    val localOffsetMinutes: Int,
    val bodyOffsetMinutes: Int,
    val is24Hour: Boolean,
    val am: String,
    val pm: String,
)

/**
 * Replays a dial spec ([dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.TwoSkies],
 * [dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.TwoStrips]) into a Remote Compose canvas: the
 * widget draws the same marks as the app, in the system font ([type]).
 *
 * The spec's box is scaled uniformly to fit the canvas and centred. Everything is drawn in that dp space under one
 * translate + scale with constant coordinates: alpha20 documents resolved per-op `cx + unit * k` expressions to the
 * wrong values once enough similar ones existed (see [drawOps]).
 *
 * Live parts follow the host's clock between captures, so the widget never shows a stale time:
 * - the [DialPart.Needle] (and whatever rides on it) turns round the centre, or slides along the strips' axis;
 * - clock readings ([DialOp.Text.live]) are written from the host clock;
 * - [DialPart.AdvicePast] (the wash over the part of the block already behind the needle) is left out: it can't
 *   follow the needle.
 * Labels that stepped aside for the needle at capture stay put; the next capture (at every advice boundary) moves them.
 *
 * Remote Compose gaps, and the fallbacks used:
 * - no shaders from Kotlin: [DialOp.SweepRing] and [DialOp.SkyBar] are drawn as short constant-colour segments;
 * - no letter spacing: tracked text and [DialOp.CurvedText] are set glyph by glyph (curved text turned per glyph,
 *   rather than `drawTextOnCircle`, which can't be checked on a real host yet);
 * - four typeface styles only: weights round to regular or bold ([DialType]).
 */
internal fun RemoteDrawScope.drawDialSpec(spec: DialSpec, type: DialType, clocks: LiveClocks) {
    val unit = (width / spec.width.rf).min(height / spec.height.rf)
    val ox = (width - unit * spec.width.rf) / 2f.rf
    val oy = (height - unit * spec.height.rf) / 2f.rf
    translate(ox, oy) {
        scale(unit) {
            val ops = spec.ops.filter { it.part != DialPart.AdvicePast }
            var i = 0
            while (i < ops.size) {
                if (ops[i].part != DialPart.Needle) {
                    drawDialOp(ops[i], type, clocks)
                    i++
                    continue
                }
                // A run of needle ops moves as one, by how far the host clock is past the capture.
                val run = ops.drop(i).takeWhile { it.part == DialPart.Needle }
                withNeedle(spec, clocks) { run.forEach { drawDialOp(it, type, clocks) } }
                i += run.size
            }
        }
    }
}

/** Runs [block] with the needle moved from where the spec drew it to the host clock's now. */
private fun RemoteDrawScope.withNeedle(spec: DialSpec, clocks: LiveClocks, block: RemoteDrawScope.() -> Unit) {
    // Minutes since the capture (−1 … 0 right at capture, as the spec's now carries seconds).
    val captured = floor(spec.nowMinute)
    val since = (HostTime.minuteOfDayAt(clocks.localOffsetMinutes) - captured.rf + 1440f.rf) % 1440f.rf -
        (spec.nowMinute - captured).rf
    val axis = spec.axis
    if (axis != null) {
        // Along the strips, and no further than their end (the next capture recentres the window).
        val room = axis.right - axis.x(spec.nowMinute)
        translate((since * axis.dpPerMinute.rf).min(room.rf), 0f.rf) { block() }
    } else {
        // Round the dial: a quarter of a degree a minute, clockwise.
        rotate(since / 4f.rf, RemoteOffset(spec.cx.rf, spec.cy.rf)) { block() }
    }
}

private fun paint(color: Argb, stroke: Float = 0f, cap: StrokeCap = StrokeCap.Butt) = RemotePaint {
    this.color = Color(color.value).rc
    if (stroke > 0f) {
        style = PaintingStyle.Stroke
        strokeWidth = stroke.rf
        strokeCap = cap
    } else {
        style = PaintingStyle.Fill
    }
}

private fun RemoteDrawScope.drawDialOp(op: DialOp, type: DialType, clocks: LiveClocks) {
    when (op) {
        is DialOp.Circle -> drawCircle(paint = paint(op.color, op.stroke), center = RemoteOffset(op.cx.rf, op.cy.rf), radius = op.r.rf)
        is DialOp.Arc -> drawArc(
            paint = paint(op.color, op.width, if (op.roundCap) StrokeCap.Round else StrokeCap.Butt),
            startAngle = op.startDeg.rf,
            sweepAngle = op.sweepDeg.rf,
            useCenter = false,
            topLeft = RemoteOffset((op.cx - op.r).rf, (op.cy - op.r).rf),
            size = RemoteSize((2f * op.r).rf, (2f * op.r).rf),
        )
        is DialOp.Line -> drawLine(
            paint = paint(op.color, op.width, if (op.roundCap) StrokeCap.Round else StrokeCap.Butt),
            start = RemoteOffset(op.x0.rf, op.y0.rf),
            end = RemoteOffset(op.x1.rf, op.y1.rf),
        )
        is DialOp.Rect -> roundRect(op.left, op.top, op.right, op.bottom, op.radius, paint(op.color, op.stroke))
        is DialOp.Glyph -> {
            drawCircle(paint = paint(op.disc), center = RemoteOffset(op.cx.rf, op.cy.rf), radius = op.discRadius.rf)
            op.ring?.let { drawCircle(paint = paint(it, GLYPH_RING_DP), center = RemoteOffset(op.cx.rf, op.cy.rf), radius = op.discRadius.rf) }
            drawOps(listOf(WidgetShapes.advice(op.type, op.color.value)), op.cx.rf, op.cy.rf, (op.size / 2f).rf)
        }
        is DialOp.Text -> text(op, type, clocks)
        is DialOp.CurvedText -> curved(op, type)
        is DialOp.SweepRing -> op.segments().forEach { drawDialOp(it, type, clocks) }
        is DialOp.SkyBar -> skyBar(op)
    }
}

private fun RemoteDrawScope.roundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, paint: RemotePaint) =
    drawRoundRect(
        paint = paint,
        topLeft = RemoteOffset(left.rf, top.rf),
        size = RemoteSize((right - left).rf, (bottom - top).rf),
        cornerRadius = RemoteOffset(radius.rf, radius.rf),
    )

/**
 * The bar's gradient as constant-colour slices between its round ends, and the ends as discs in the first and last
 * colours (no clip path needed: the ends are only as wide as one or two stops).
 */
private fun RemoteDrawScope.skyBar(op: DialOp.SkyBar) {
    val r = op.radius
    val cy = (op.top + op.bottom) / 2f
    drawCircle(paint = paint(op.colors.first()), center = RemoteOffset((op.left + r).rf, cy.rf), radius = r.rf)
    drawCircle(paint = paint(op.colors.last()), center = RemoteOffset((op.right - r).rf, cy.rf), radius = r.rf)
    op.segments().forEach { s ->
        val l = s.left.coerceAtLeast(op.left + r)
        val rt = s.right.coerceAtMost(op.right - r)
        if (rt > l) roundRect(l, s.top, rt, s.bottom, 0f, paint(s.color))
    }
}

private fun textPaint(spec: TextSpec, color: Argb, type: DialType) = RemotePaint {
    this.color = Color(color.value).rc
    style = PaintingStyle.Fill
    textSize = spec.size.rf
    typeface = RemoteTypeface.fromAndroidTypeface(type.typeface(spec))
}

/** The baseline that puts [op]'s line box where its [VAlign] says, as the Compose renderer does. */
private fun baseline(op: DialOp.Text, type: DialType): Float {
    val fm = type.paint(op.spec).fontMetrics
    return when (op.v) {
        VAlign.Top -> op.y - fm.ascent
        VAlign.Center -> op.y - (fm.ascent + fm.descent) / 2f
        VAlign.Baseline -> op.y
        VAlign.Bottom -> op.y - fm.descent
    }
}

private fun RemoteDrawScope.text(op: DialOp.Text, type: DialType, clocks: LiveClocks) {
    val y = baseline(op, type)
    val paint = textPaint(op.spec, op.color, type)
    val live = op.live
    if (live == null && op.spec.tracking > 0f) {
        // Tracked: glyph by glyph at the advances the measurer used.
        val p = type.paint(op.spec)
        val total = p.measureText(op.text)
        var x = when (op.h) {
            HAlign.Start -> op.x
            HAlign.Center -> op.x - total / 2f
            HAlign.End -> op.x - total
        }
        val widths = FloatArray(op.text.length)
        p.getTextWidths(op.text, widths)
        op.text.forEachIndexed { i, c ->
            if (!c.isWhitespace()) anchored(c.toString().rs, x, y, paint, HAlign.Start)
            x += widths[i]
        }
        return
    }
    anchored(live?.let { liveText(it, clocks) } ?: op.text.rs, op.x, y, paint, op.h)
}

/** Text with its baseline on [y], starting at, centred on or ending at [x] (measured on the host, for live text). */
private fun RemoteDrawScope.anchored(text: RemoteString, x: Float, y: Float, paint: RemotePaint, h: HAlign) {
    val panX = when (h) {
        HAlign.Start -> -1f
        HAlign.Center -> 0f
        HAlign.End -> 1f
    }
    drawAnchoredText(text, x.rf, y.rf, paint, panX.rf, 0f.rf, BASELINE_RELATIVE)
}

/** A clock reading written by the host: "15:20", "3:20 PM", "08:20 body". */
private fun liveText(live: LiveTime, clocks: LiveClocks): RemoteString {
    val offset = if (live.clock == LiveClock.Local) clocks.localOffsetMinutes else clocks.bodyOffsetMinutes
    val minute = HostTime.minuteOfDayAt(offset)
    val parts = buildList {
        if (live.prefix.isNotEmpty()) add(live.prefix.rs)
        if (live.digits) add(HostText.clock(minute, clocks.is24Hour))
        if (live.marker && !clocks.is24Hour) {
            if (live.digits) add(" ".rs)
            add(HostText.marker(minute, clocks.am, clocks.pm))
        }
        if (live.suffix.isNotEmpty()) add(live.suffix.rs)
    }
    return parts.reduceOrNull { a, b -> a + b } ?: "".rs
}

/** Glyph by glyph along the circle, each turned to the tangent (the Compose renderer's layout). */
private fun RemoteDrawScope.curved(op: DialOp.CurvedText, type: DialType) {
    val p = type.paint(op.spec)
    val total = p.measureText(op.text)
    val widths = FloatArray(op.text.length)
    p.getTextWidths(op.text, widths)
    val fm = p.fontMetrics
    val paint = textPaint(op.spec, op.color, type)
    var start = 0f
    op.text.forEachIndexed { i, c ->
        val along = start + widths[i] / 2f - total / 2f
        start += widths[i]
        if (c.isWhitespace()) return@forEachIndexed
        val deltaDeg = Math.toDegrees((along / op.r).toDouble()).toFloat()
        val a = if (op.readsInward) op.centerDeg - deltaDeg else op.centerDeg + deltaDeg
        val rad = Math.toRadians(a.toDouble())
        val px = op.cx + op.r * cos(rad).toFloat()
        val py = op.cy + op.r * sin(rad).toFloat()
        rotate((if (op.readsInward) a - 90f else a + 90f).rf, RemoteOffset(px.rf, py.rf)) {
            anchored(c.toString().rs, px, py - (fm.ascent + fm.descent) / 2f, paint, HAlign.Center)
        }
    }
}

/** `DrawTextAnchored.BASELINE_RELATIVE`: y is the baseline (with a vertical pan of 0). */
private const val BASELINE_RELATIVE = 8

/** The ring round a glyph's disc, as in the app's renderer. */
private const val GLYPH_RING_DP = 1.5f
