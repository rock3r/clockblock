package dev.sebastiano.clockblocker.opus.core.designsystem.dial.concepts

import androidx.compose.ui.graphics.Color
import dev.sebastiano.clockblocker.opus.core.model.AdviceType

/** Building blocks shared by the concepts. Each emits plain [DialOp]s, so every renderer gets the same marks. */
internal typealias Ops = MutableList<DialOp>

/** A 24 h sky ring: 288 constant-colour arcs (RC-safe: no sweep shader needed), noon at the top. */
internal fun Ops.skyRing(
    cx: Float, cy: Float, r: Float, w: Float,
    sunrise: Float, sunset: Float, p: ConceptPalette,
    segments: Int = 288,
) {
    val step = 360f / segments
    for (i in 0 until segments) {
        val mid = (i + 0.5f) * 1440f / segments
        add(DialOp.Arc(cx, cy, r, w, 90f + i * step, step + 0.35f, p.sky(mid, sunrise, sunset)))
    }
}

/** Midpoint (local minutes) of the night that runs sunset → sunrise, and of the day. */
internal fun nightCentre(sunrise: Float, sunset: Float): Float = (sunset + ((sunrise - sunset).mod(1440f)) / 2f).mod(1440f)
internal fun dayCentre(sunrise: Float, sunset: Float): Float = (sunrise + ((sunset - sunrise).mod(1440f)) / 2f).mod(1440f)

/**
 * The current advice as one arc: an edge in the strong tone, the fill, a hatch for "avoid light" (colour-blind
 * carrier), and the part already behind "now" washed back toward the face.
 */
internal fun Ops.adviceArc(
    cx: Float, cy: Float, r: Float, w: Float,
    a: ConceptAdvice, now: Float, p: ConceptPalette,
) {
    val start = dialAngle(a.startMinute)
    val sweep = a.sweepMinutes / 4f
    val fill = p.adviceFill(a.type)
    val ink = p.adviceInk(a.type)
    add(DialOp.Arc(cx, cy, r, w + 2f, start, sweep, p.adviceStrong(a.type).copy(alpha = if (p.dark) 0f else 0.85f), roundCap = true))
    add(DialOp.Arc(cx, cy, r, w, start, sweep, fill, roundCap = true))
    if (a.type == AdviceType.AvoidLight) {
        val skew = Math.toDegrees((w * 0.8f / r).toDouble()).toFloat()
        var t = start + 2f
        val step = Math.toDegrees((4.2f / r).toDouble()).toFloat()
        while (t + skew < start + sweep - 1f) {
            val p0 = polar(cx, cy, r + w / 2f - 1.2f, t)
            val p1 = polar(cx, cy, r - w / 2f + 1.2f, t + skew)
            add(DialOp.Line(p0.x, p0.y, p1.x, p1.y, ink.copy(alpha = 0.28f), 1.1f, roundCap = false))
            t += step
        }
    }
    val elapsed = ((now - a.startMinute).mod(1440f)).coerceAtMost(a.sweepMinutes)
    if (elapsed > 0f && elapsed < a.sweepMinutes) {
        add(DialOp.Arc(cx, cy, r, w + 2.5f, start - 4f, elapsed / 4f + 4f, p.face.copy(alpha = 0.42f)))
    }
}

/** A "now" needle across [r0]..[r1] with a face-coloured halo so it separates from every ring it crosses. */
internal fun Ops.needle(cx: Float, cy: Float, deg: Float, r0: Float, r1: Float, width: Float, color: Color, halo: Color) {
    val a = polar(cx, cy, r0, deg)
    val b = polar(cx, cy, r1, deg)
    add(DialOp.Line(a.x, a.y, b.x, b.y, halo, width + 3f))
    add(DialOp.Line(a.x, a.y, b.x, b.y, color, width))
}

/** A pill with centred text. */
internal fun Ops.pill(cx: Float, cy: Float, text: String, spec: TextSpec, bg: Color, fg: Color, padH: Float, height: Float) {
    val w = approxTextWidth(text, spec) + 2 * padH
    add(DialOp.Rect(cx - w / 2f, cy - height / 2f, cx + w / 2f, cy + height / 2f, height / 2f, bg))
    add(DialOp.Text(text, cx, cy, spec, fg))
}

/** Advice glyph on a disc in the advice's strong colour (shape carries the meaning, never colour alone). */
internal fun Ops.adviceGlyph(type: AdviceType, x: Float, y: Float, discR: Float, p: ConceptPalette, ring: Color? = null) {
    val role = p.advice[type]
    add(
        DialOp.Glyph(
            GlyphKind.Advice(type), x, y, discR * 1.25f,
            color = role.onColor, disc = role.color, discRadius = discR, ring = ring,
        ),
    )
}
