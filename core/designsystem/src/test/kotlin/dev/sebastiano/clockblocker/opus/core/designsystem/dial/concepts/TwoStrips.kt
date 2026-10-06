package dev.sebastiano.clockblocker.opus.core.designsystem.dial.concepts

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Concept 3, "Two strips": two day bars on one time axis (6 h of past, 18 h ahead). Top = the sky where you are,
 * bottom = the sky your body thinks it is under. A vertical "now" line crosses both and is labelled with each clock's
 * reading in its own row. The offset between the nights is bracketed ("7 h"). Everything is straight text and
 * rectangles, so it needs no curved text in Remote Compose.
 *
 * Encodings: (1) day/night colour of the two bars, (2) the now line, (3) the advice capsule + glyph, plus the
 * bracket at Full.
 */
object TwoStrips {
    fun spec(s: ConceptState, p: ConceptPalette, w: Float, h: Float): List<DialOp> {
        val ops = mutableListOf<DialOp>()
        when {
            w < 110f -> ops.glance(s, p, w, h)
            w < 250f -> ops.simple(s, p, w, h)
            else -> ops.full(s, p, w, h)
        }
        return ops
    }

    private class Axis(val left: Float, val right: Float, val start: Float) {
        val width get() = right - left
        fun rel(m: Float) = (m - start).mod(1440f)
        fun x(m: Float) = left + rel(m) / 1440f * width
    }

    private fun axisFor(s: ConceptState, left: Float, right: Float) =
        Axis(left, right, (Math.floorDiv((s.localMinute - 360f).toInt(), 60) * 60).toFloat().mod(1440f))

    /** A sky bar: one gradient op sampled every 5 min. */
    private fun Ops.bar(ax: Axis, top: Float, bottom: Float, sunrise: Float, sunset: Float, p: ConceptPalette) {
        val colors = (0 until 288).map { i -> p.sky(ax.start + i * 5f + 2.5f, sunrise, sunset) }
        add(DialOp.GradientBar(ax.left, top, ax.right, bottom, (bottom - top) / 2.4f, colors))
    }

    /** The longest stretch of the bar inside [from, to) (local minutes), excluding [avoid] ± [gap] dp. */
    private fun labelSpot(ax: Axis, from: Float, to: Float, avoidX: Float?, gap: Float): Pair<Float, Float> {
        // Pieces in axis space (relative minutes), split where the interval wraps around the window edge.
        val a = ax.rel(from)
        val len = (to - from).mod(1440f)
        val pieces = if (a + len <= 1440f) listOf(a to a + len) else listOf(a to 1440f, 0f to a + len - 1440f)
        val xs = pieces.map { (r0, r1) -> ax.left + r0 / 1440f * ax.width to ax.left + r1 / 1440f * ax.width }
        val split = xs.flatMap { (x0, x1) ->
            if (avoidX != null && avoidX > x0 && avoidX < x1) listOf(x0 to avoidX - gap, avoidX + gap to x1) else listOf(x0 to x1)
        }
        return split.maxBy { it.second - it.first }
    }

    private fun Ops.barLabel(text: String, spot: Pair<Float, Float>, y: Float, spec: TextSpec, color: Color) {
        if (approxTextWidth(text, spec) > spot.second - spot.first - 8f) return
        add(DialOp.Text(text, (spot.first + spot.second) / 2f, y, spec, color))
    }

    private fun Ops.nowLine(x: Float, y0: Float, y1: Float, width: Float, p: ConceptPalette) {
        add(DialOp.Line(x, y0, x, y1, p.card, width + 3f))
        add(DialOp.Line(x, y0, x, y1, p.ink, width))
    }

    private fun Ops.capsule(ax: Axis, a: ConceptAdvice, now: Float, top: Float, bottom: Float, p: ConceptPalette) {
        val x0 = ax.x(a.startMinute)
        val x1 = ax.x(a.endMinute)
        val hgt = bottom - top
        add(DialOp.Rect(x0 - 1f, top - 1f, x1 + 1f, bottom + 1f, hgt / 2f + 1f, p.adviceStrong(a.type).copy(alpha = if (p.dark) 0f else 0.85f)))
        val inner = mutableListOf<DialOp>()
        inner += DialOp.Rect(x0, top, x1, bottom, 0f, p.adviceFill(a.type))
        if (a.type == dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidLight) {
            var x = x0 - hgt
            while (x < x1) {
                inner += DialOp.Line(x, bottom, x + hgt * 0.8f, top, p.adviceInk(a.type).copy(alpha = 0.28f), 1.1f, roundCap = false)
                x += 4.2f
            }
        }
        val nx = ax.x(now)
        if (nx > x0 && nx < x1) inner += DialOp.Rect(x0, top, nx, bottom, 0f, p.card.copy(alpha = 0.42f))
        add(DialOp.Clip(x0, top, x1, bottom, hgt / 2f, inner))
    }

    private fun Ops.full(s: ConceptState, p: ConceptPalette, w: Float, h: Float) {
        val ax = axisFor(s, 0f, w)
        val nowX = ax.x(s.localMinute)
        val localTop = 46f
        val localBottom = 76f
        val bodyTop = 112f
        val bodyBottom = 142f
        val localHoursY = 87f
        val bodyHoursY = 153f

        // Advice: label above, capsule on its own row, next advice glyph right after it.
        s.current?.let { a ->
            val capTop = 20f
            val capBottom = 38f
            capsule(ax, a, s.localMinute, capTop, capBottom, p)
            val x0 = ax.x(a.startMinute)
            adviceGlyph(a.type, x0 + 9f, (capTop + capBottom) / 2f, 7.5f, p)
            add(DialOp.Text("${a.label} until ${hhmm(a.endMinute)}", x0, 8f, TextSpec(11f, weight = 650), p.ink, h = HAlign.Start))
            s.next?.let { n ->
                val nx = ax.x(n.startMinute) + 11f
                adviceGlyph(n.type, nx, (capTop + capBottom) / 2f, 7.5f, p)
                add(DialOp.Text("then ${n.label.lowercase()}", nx + 11f, (capTop + capBottom) / 2f, TextSpec(10f, weight = 600, tabular = false), p.inkMuted, h = HAlign.Start))
            }
        }

        bar(ax, localTop, localBottom, s.sunriseMinute, s.sunsetMinute, p)
        bar(ax, bodyTop, bodyBottom, s.bodySunrise(), s.bodySunset(), p)

        val lbl = TextSpec(10.5f, weight = 650, tabular = false)
        val bodyLbl = lbl.copy(slanted = true)
        val lc = (localTop + localBottom) / 2f
        val bc = (bodyTop + bodyBottom) / 2f
        barLabel("${s.city} night", labelSpot(ax, s.sunsetMinute, s.sunriseMinute, nowX, 6f), lc, lbl, p.onNight)
        barLabel("${s.city} day", labelSpot(ax, s.sunriseMinute, s.sunsetMinute, nowX, 6f), lc, lbl, p.onDay)
        barLabel("your body's night", labelSpot(ax, s.bodySunset(), s.bodySunrise(), nowX, 6f), bc, bodyLbl, p.onNight)
        barLabel("your body's day", labelSpot(ax, s.bodySunrise(), s.bodySunset(), nowX, 6f), bc, bodyLbl, p.onDay)

        // Hour rows: each clock's own hours under its bar. The now readouts replace the hours they'd overlap.
        val hourSpec = TextSpec(9.5f, weight = 500)
        for (hr in 0 until 24 step 3) {
            val x = ax.x(hr * 60f)
            if (x > 8f && x < w - 8f && abs(x - nowX) > 26f) {
                add(DialOp.Text("%02d".format(hr), x, localHoursY, hourSpec, p.inkMuted))
            }
            val bx = ax.x(hr * 60f - s.bodyOffsetMinutes)
            if (bx > 8f && bx < w - 8f && abs(bx - nowX) > 26f) {
                add(DialOp.Text("%02d".format(hr), bx, bodyHoursY, hourSpec.copy(slanted = true), p.inkMuted))
            }
        }

        // The offset, bracketed between the two night starts.
        if (!s.aligned) {
            val x1 = ax.x(s.sunsetMinute)
            val x2 = ax.x(s.bodySunset())
            val y = 100f
            add(DialOp.Line(x1, localBottom + 2f, x1, y, p.body.copy(alpha = 0.7f), 1.2f))
            add(DialOp.Line(x1, y, x2, y, p.body.copy(alpha = 0.7f), 1.2f))
            add(DialOp.Line(x2, y, x2, bodyTop - 2f, p.body.copy(alpha = 0.7f), 1.2f))
            val label = "${s.offsetWords().replace(" behind", "").replace(" ahead", "")} later"
            pill((x1 + x2) / 2f, y, label, TextSpec(9.5f, weight = 650, slanted = true, tabular = false), p.bodyContainer, p.onBodyContainer, 7f, 15f)
        } else {
            pill(w / 2f, 100f, "in sync", TextSpec(9.5f, weight = 650, slanted = true, tabular = false), p.bodyContainer, p.onBodyContainer, 7f, 15f)
        }

        nowLine(nowX, 16f, bodyBottom + 2f, 2.2f, p)
        pill(nowX, localHoursY, hhmm(s.localMinute), TextSpec(10f, weight = 650), p.ink, p.card, 6f, 16f)
        pill(nowX, bodyHoursY, hhmm(s.bodyMinute), TextSpec(10f, weight = 650, slanted = true), p.body, p.onBody, 6f, 16f)
    }

    private fun Ops.simple(s: ConceptState, p: ConceptPalette, w: Float, h: Float) {
        val ax = axisFor(s, 0f, w)
        val nowX = ax.x(s.localMinute)
        add(DialOp.Text(hhmm(s.localMinute), 0f, 12f, TextSpec(22f, weight = 480), p.ink, h = HAlign.Start))
        add(DialOp.Text(if (s.aligned) "body in sync" else "${hhmm(s.bodyMinute)} body", 70f, 14f, TextSpec(12f, weight = 520, slanted = true), p.body, h = HAlign.Start))
        if (!s.aligned) pill(w - 22f, 13f, s.offsetSigned(), TextSpec(10f, weight = 700, slanted = true, tabular = false), p.bodyContainer, p.onBodyContainer, 7f, 17f)
        s.current?.let { a ->
            capsule(ax, a, s.localMinute, 32f, 46f, p)
            adviceGlyph(a.type, ax.x(a.startMinute) + 7f, 39f, 6f, p)
            s.next?.let { n -> adviceGlyph(n.type, ax.x(n.startMinute) + 9f, 39f, 6f, p) }
        }
        bar(ax, 52f, 74f, s.sunriseMinute, s.sunsetMinute, p)
        bar(ax, 80f, 102f, s.bodySunrise(), s.bodySunset(), p)
        val lbl = TextSpec(9.5f, weight = 650, tabular = false)
        barLabel("${s.city} night", labelSpot(ax, s.sunsetMinute, s.sunriseMinute, nowX, 5f), 63f, lbl, p.onNight)
        barLabel("body's night", labelSpot(ax, s.bodySunset(), s.bodySunrise(), nowX, 5f), 91f, lbl.copy(slanted = true), p.onNight)
        nowLine(nowX, 29f, 104f, 2f, p)
    }

    private fun Ops.glance(s: ConceptState, p: ConceptPalette, w: Float, h: Float) {
        val ax = axisFor(s, 6f, w - 6f)
        val nowX = ax.x(s.localMinute)
        add(DialOp.Text(hhmm(s.localMinute), w / 2f, 17f, TextSpec(19f, weight = 500), p.ink))
        bar(ax, 32f, 44f, s.sunriseMinute, s.sunsetMinute, p)
        bar(ax, 48f, 60f, s.bodySunrise(), s.bodySunset(), p)
        nowLine(nowX, 29f, 63f, 1.8f, p)
        add(DialOp.Text(if (s.aligned) "in sync" else hhmm(s.bodyMinute), w / 2f, 72f, TextSpec(11f, weight = 560, slanted = true), p.body))
        if (!s.aligned) add(DialOp.Text(s.offsetSigned(), w / 2f, 83f, TextSpec(8.5f, weight = 650, slanted = true, tabular = false), p.inkMuted))
    }
}
