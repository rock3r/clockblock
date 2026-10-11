package dev.sebastiano.clockblocker.opus.widget.rc

import androidx.compose.remote.creation.RemotePath
import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import dev.sebastiano.clockblocker.opus.widget.draw.DrawOp
import dev.sebastiano.clockblocker.opus.widget.draw.PathSegment
import dev.sebastiano.clockblocker.opus.widget.draw.segments

/**
 * Replays a [DrawOp] list into a Remote Compose canvas, centred on ([cx], [cy]) and scaled by [unit] (both
 * host-evaluated expressions of the canvas size).
 *
 * The ops are drawn in unit space under one translate + scale, with plain constants: building one
 * `cx + unit * k` expression per coordinate (as before) made alpha20 documents resolve some coordinates to the
 * wrong expression once enough similar ones existed. Which op broke depended on the data (on device the jet-lag
 * wedge was drawn half outside the dial), so keep per-op coordinates constant.
 */
internal fun RemoteDrawScope.drawOps(ops: List<DrawOp>, cx: RemoteFloat, cy: RemoteFloat, unit: RemoteFloat) {
    inUnitSpace(cx, cy, unit) { ops.forEach { drawOp(it, Color(it.color).rc) } }
}

/**
 * [light] and [dark], the same display list built from the light and the dark palette, drawn once with [themedColor]s
 * so the player picks each op's colour by its theme (the notification documents, #49). Both lists must have the same
 * ops in the same order (Glyphs do: only their colours depend on the palette).
 */
internal fun RemoteDrawScope.drawThemedOps(
    light: List<DrawOp>,
    dark: List<DrawOp>,
    cx: RemoteFloat,
    cy: RemoteFloat,
    unit: RemoteFloat,
) {
    require(light.size == dark.size && light.zip(dark).all { (l, d) -> l::class == d::class }) {
        "The light and dark display lists differ in shape"
    }
    inUnitSpace(cx, cy, unit) { light.zip(dark).forEach { (l, d) -> drawOp(l, themedColor(l.color, d.color)) } }
}

/** Runs [block] with the origin at ([cx], [cy]) and 1 unit = [unit] px. */
private fun RemoteDrawScope.inUnitSpace(
    cx: RemoteFloat,
    cy: RemoteFloat,
    unit: RemoteFloat,
    block: RemoteDrawScope.() -> Unit,
) {
    translate(cx, cy) { scale(unit) { block() } }
}

private fun paint(color: RemoteColor, stroke: Float, cap: StrokeCap = StrokeCap.Butt) = RemotePaint {
    this.color = color
    if (stroke > 0f) {
        style = PaintingStyle.Stroke
        strokeWidth = stroke.rf
        strokeCap = cap
    } else {
        style = PaintingStyle.Fill
    }
}

private fun cap(round: Boolean) = if (round) StrokeCap.Round else StrokeCap.Butt

/** [op] in [color] (its own colour, or a themed pair). */
private fun RemoteDrawScope.drawOp(op: DrawOp, color: RemoteColor) {
    when (op) {
        is DrawOp.Circle -> drawCircle(
            paint = paint(color, op.stroke),
            radius = op.r.rf,
            center = RemoteOffset(op.cx.rf, op.cy.rf),
        )
        is DrawOp.Arc -> drawArc(
            paint = paint(color, if (op.fill) 0f else op.stroke, cap(op.roundCap)),
            startAngle = op.startDeg.rf,
            sweepAngle = op.sweepDeg.rf,
            useCenter = op.fill,
            topLeft = RemoteOffset((op.cx - op.r).rf, (op.cy - op.r).rf),
            size = RemoteSize((2 * op.r).rf, (2 * op.r).rf),
        )
        is DrawOp.Line -> drawLine(
            paint = paint(color, op.stroke, cap(op.roundCap)),
            start = RemoteOffset(op.x0.rf, op.y0.rf),
            end = RemoteOffset(op.x1.rf, op.y1.rf),
        )
        is DrawOp.RoundRect -> drawRoundRect(
            paint = paint(color, op.stroke),
            topLeft = RemoteOffset(op.left.rf, op.top.rf),
            size = RemoteSize((op.right - op.left).rf, (op.bottom - op.top).rf),
            cornerRadius = RemoteOffset(op.radius.rf, op.radius.rf),
        )
        is DrawOp.Path -> drawPath(op.toRemotePath(), paint(color, op.stroke).apply { strokeJoin = StrokeJoin.Round })
        // alpha20's brush → shader API is not callable from Kotlin, and plain arcs are the safest op on every widget
        // host anyway: approximate the sweep with short constant-colour arcs (5° each, slightly overlapping). Its
        // gradient keeps its own colours (no glyph uses one).
        is DrawOp.SweepRing -> op.segments().forEach { drawOp(it, Color(it.color).rc) }
    }
}

private fun DrawOp.Path.toRemotePath(): RemotePath = RemotePath().apply {
    segments.forEach { s ->
        when (s) {
            is PathSegment.MoveTo -> moveTo(s.x, s.y)
            is PathSegment.LineTo -> lineTo(s.x, s.y)
            is PathSegment.CubicTo -> cubicTo(s.x1, s.y1, s.x2, s.y2, s.x, s.y)
            PathSegment.Close -> close()
        }
    }
}
