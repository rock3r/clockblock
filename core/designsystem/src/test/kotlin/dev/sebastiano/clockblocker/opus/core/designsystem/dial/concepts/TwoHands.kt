package dev.sebastiano.clockblocker.opus.core.designsystem.dial.concepts

import kotlin.math.abs
import kotlin.math.min

/**
 * Concept 2, "Two hands": one 24 h face, read like a dual-time (GMT) watch. The bezel is the local sky. A sun hand
 * points at the local time, a moon hand at the time your body thinks it is. The gap between the hands is the jet
 * lag, filled and labelled ("body 7 h behind"). When adapted the hands overlap.
 *
 * Encodings: (1) bezel day/night, (2) two hands, (3) the labelled gap, (4) the advice arc + glyph.
 */
object TwoHands {
    fun spec(s: ConceptState, p: ConceptPalette, w: Float, h: Float): List<DialOp> {
        val side = min(w, h)
        val ops = mutableListOf<DialOp>()
        when (DetailLevel.forSize(side)) {
            DetailLevel.Full -> ops.full(s, p, w / 2f, h / 2f, side / 2f / 164f)
            DetailLevel.Simple -> ops.simple(s, p, w / 2f, h / 2f, side / 2f / 80f)
            DetailLevel.Glance -> ops.glance(s, p, w / 2f, h / 2f, side / 2f / 44f)
        }
        return ops
    }

    /** Signed sweep (degrees) from the body hand to the local hand, shortest way. */
    private fun gapSweep(s: ConceptState): Float {
        val d = (dialAngle(s.localMinute) - dialAngle(s.bodyMinute)).mod(360f)
        return if (d > 180f) d - 360f else d
    }

    private fun Ops.hand(cx: Float, cy: Float, deg: Float, r0: Float, r1: Float, width: Float, color: androidx.compose.ui.graphics.Color, halo: androidx.compose.ui.graphics.Color) {
        needle(cx, cy, deg, r0, r1, width, color, halo)
    }

    private fun Ops.sunHead(cx: Float, cy: Float, deg: Float, r: Float, size: Float, p: ConceptPalette) {
        val c = polar(cx, cy, r, deg)
        add(DialOp.Circle(c.x, c.y, size * 0.62f, p.face))
        if (!p.dark) add(DialOp.Glyph(GlyphKind.Sun, c.x, c.y, size * 1.08f, p.sunEdge))
        add(DialOp.Glyph(GlyphKind.Sun, c.x, c.y, size, p.sun))
    }

    private fun Ops.moonHead(cx: Float, cy: Float, deg: Float, r: Float, size: Float, p: ConceptPalette) {
        val c = polar(cx, cy, r, deg)
        add(DialOp.Circle(c.x, c.y, size * 0.62f, p.face))
        add(DialOp.Circle(c.x, c.y, size * 0.52f, p.body))
        add(DialOp.Glyph(GlyphKind.Moon, c.x, c.y, size * 0.58f, p.onBody))
    }

    private fun Ops.full(s: ConceptState, p: ConceptPalette, cx: Float, cy: Float, k: Float) {
        val bezelR = 151f * k
        val bezelW = 22f * k
        val trackR = 125f * k
        val trackW = 11f * k
        val hubR = 60f * k
        add(DialOp.Circle(cx, cy, 164f * k, p.face))

        // Bezel: the local sky with the four cardinal hours printed on it.
        skyRing(cx, cy, bezelR, bezelW, s.sunriseMinute, s.sunsetMinute, p)
        val num = TextSpec(9.5f * k, weight = 650)
        listOf(0, 6, 12, 18).forEach { hr ->
            val m = hr * 60f
            val pt = polar(cx, cy, bezelR, dialAngle(m))
            add(DialOp.Text("%02d".format(hr), pt.x, pt.y, num, p.onSky(m, s.sunriseMinute, s.sunsetMinute)))
        }
        // Hour ticks inside the bezel.
        for (hr in 0 until 24) {
            if (hr % 6 == 0) continue
            val a = dialAngle(hr * 60f)
            val major = hr % 3 == 0
            val p0 = polar(cx, cy, bezelR - bezelW / 2f - 2.5f * k, a)
            val p1 = polar(cx, cy, bezelR - bezelW / 2f - (if (major) 7f else 4.5f) * k, a)
            add(DialOp.Line(p0.x, p0.y, p1.x, p1.y, p.inkMuted.copy(alpha = if (major) 0.7f else 0.4f), (if (major) 1.4f else 1f) * k))
        }

        // The gap between the hands: the jet lag, filled and labelled.
        val bodyDeg = dialAngle(s.bodyMinute)
        val localDeg = dialAngle(s.localMinute)
        val sweep = gapSweep(s)
        val gapR0 = hubR
        val gapR1 = 99f * k
        if (!s.aligned) {
            add(DialOp.Arc(cx, cy, (gapR0 + gapR1) / 2f, gapR1 - gapR0, bodyDeg, sweep, p.body.copy(alpha = if (p.dark) 0.22f else 0.11f)))
            val label = TextSpec(9.5f * k, weight = 650, slanted = true, caps = true, tracking = 0.1f, tabular = false)
            add(DialOp.CurvedText("body ${s.offsetWords()}", cx, cy, (gapR0 + gapR1) / 2f, bodyDeg + sweep / 2f, label, p.body))
        }

        // Current advice on the inner track, glyph at its start. The label leads into the glyph (it ends where the
        // block starts), so it never collides with the local hand, which is always inside the current block.
        s.current?.let { a ->
            adviceArc(cx, cy, trackR, trackW, a, s.localMinute, p)
            val start = dialAngle(a.startMinute)
            val g = polar(cx, cy, trackR, start)
            adviceGlyph(a.type, g.x, g.y, 10f * k, p, ring = p.face)
            val text = "${a.label} until ${hhmm(a.endMinute)}"
            val spec = TextSpec(9.5f * k, weight = 650, tabular = true)
            val r = trackR
            val deg = arcDegreesFor(text, spec, r)
            val glyphDeg = Math.toDegrees((14f * k / r).toDouble()).toFloat()
            add(DialOp.CurvedText(text, cx, cy, r, start - glyphDeg - deg / 2f, spec, p.ink))
            s.next?.let { n ->
                val nd = dialAngle(n.startMinute)
                val np = polar(cx, cy, trackR, nd + 4f)
                adviceGlyph(n.type, np.x, np.y, 8.5f * k, p, ring = p.face)
                val thenText = "then ${n.label.lowercase()}"
                val thenSpec = TextSpec(9.5f * k, weight = 550, tabular = false)
                val thenDeg = arcDegreesFor(thenText, thenSpec, r)
                add(DialOp.CurvedText(thenText, cx, cy, r, nd + 4f + glyphDeg + thenDeg / 2f, thenSpec, p.inkMuted))
            }
        }

        // Hands: body (moon) under local (sun).
        val handR1 = bezelR - bezelW / 2f - 1f * k
        if (!s.aligned) {
            hand(cx, cy, bodyDeg, hubR, handR1, 4f * k, p.body, p.face)
            moonHead(cx, cy, bodyDeg, bezelR, 22f * k, p)
        }
        hand(cx, cy, localDeg, hubR, handR1, 4f * k, p.ink, p.face)
        sunHead(cx, cy, localDeg, bezelR, 24f * k, p)
        if (s.aligned) {
            // One hand carries both: a small moon badge rides on the sun.
            val c = polar(cx, cy, bezelR - 15f * k, localDeg)
            add(DialOp.Circle(c.x, c.y, 8f * k, p.face))
            add(DialOp.Circle(c.x, c.y, 6.5f * k, p.body))
            add(DialOp.Glyph(GlyphKind.Moon, c.x, c.y, 7f * k, p.onBody))
        }

        // Centre: each readout carries its hand's glyph, so hand ↔ number needs no legend.
        add(DialOp.Glyph(GlyphKind.Sun, cx - 54f * k, cy - 10f * k, 13f * k, p.sun))
        add(DialOp.Text(hhmm(s.localMinute), cx + 8f * k, cy - 10f * k, TextSpec(34f * k, weight = 430), p.ink))
        add(DialOp.Circle(cx - 33f * k, cy + 20f * k, 6.5f * k, p.body))
        add(DialOp.Glyph(GlyphKind.Moon, cx - 33f * k, cy + 20f * k, 7f * k, p.onBody))
        add(DialOp.Text(if (s.aligned) "in sync" else "${hhmm(s.bodyMinute)} body", cx - 23f * k, cy + 20f * k, TextSpec(14f * k, weight = 480, slanted = true), p.body, h = HAlign.Start))
        add(DialOp.Text(s.city.uppercase(), cx, cy - 36f * k, TextSpec(8.5f * k, weight = 600, tracking = 0.14f, tabular = false), p.inkMuted))
    }

    private fun Ops.simple(s: ConceptState, p: ConceptPalette, cx: Float, cy: Float, k: Float) {
        val bezelR = 72.5f * k
        val bezelW = 13f * k
        val trackR = 58f * k
        val trackW = 7f * k
        val hubR = 31f * k
        add(DialOp.Circle(cx, cy, 80f * k, p.face))
        skyRing(cx, cy, bezelR, bezelW, s.sunriseMinute, s.sunsetMinute, p)
        val bodyDeg = dialAngle(s.bodyMinute)
        val localDeg = dialAngle(s.localMinute)
        if (!s.aligned) {
            val r0 = hubR
            val r1 = 52f * k
            add(DialOp.Arc(cx, cy, (r0 + r1) / 2f, r1 - r0, bodyDeg, gapSweep(s), p.body.copy(alpha = if (p.dark) 0.24f else 0.12f)))
            add(DialOp.CurvedText(s.offsetSigned(), cx, cy, (r0 + r1) / 2f, bodyDeg + gapSweep(s) / 2f, TextSpec(8.5f * k, weight = 700, slanted = true, tabular = false), p.body))
        }
        s.current?.let { a ->
            adviceArc(cx, cy, trackR, trackW, a, s.localMinute, p)
            val g = polar(cx, cy, trackR, dialAngle(a.startMinute))
            adviceGlyph(a.type, g.x, g.y, 6.5f * k, p, ring = p.face)
        }
        val r1 = bezelR - bezelW / 2f
        if (!s.aligned) {
            hand(cx, cy, bodyDeg, hubR, r1, 2.6f * k, p.body, p.face)
            moonHead(cx, cy, bodyDeg, bezelR, 13f * k, p)
        }
        hand(cx, cy, localDeg, hubR, r1, 2.6f * k, p.ink, p.face)
        sunHead(cx, cy, localDeg, bezelR, 15f * k, p)
        add(DialOp.Text(hhmm(s.localMinute), cx, cy - 4f * k, TextSpec(17f * k, weight = 480), p.ink))
        add(DialOp.Text(if (s.aligned) "in sync" else hhmm(s.bodyMinute), cx, cy + 11f * k, TextSpec(9.5f * k, weight = 550, slanted = true), p.body))
    }

    private fun Ops.glance(s: ConceptState, p: ConceptPalette, cx: Float, cy: Float, k: Float) {
        val bezelR = 40f * k
        val bezelW = 6f * k
        add(DialOp.Circle(cx, cy, 44f * k, p.face))
        skyRing(cx, cy, bezelR, bezelW, s.sunriseMinute, s.sunsetMinute, p)
        val bodyDeg = dialAngle(s.bodyMinute)
        val localDeg = dialAngle(s.localMinute)
        val sweep = gapSweep(s)
        if (!s.aligned) {
            add(DialOp.Arc(cx, cy, 15f * k, 30f * k, bodyDeg, sweep, p.body.copy(alpha = if (p.dark) 0.26f else 0.14f)))
        }
        val r1 = bezelR - bezelW / 2f - 1f * k
        if (!s.aligned) {
            hand(cx, cy, bodyDeg, 0f, r1 - 4f * k, 2.4f * k, p.body, p.face)
            moonHead(cx, cy, bodyDeg, r1 - 4f * k, 10f * k, p)
        }
        hand(cx, cy, localDeg, 0f, r1 - 4f * k, 2.4f * k, p.ink, p.face)
        sunHead(cx, cy, localDeg, r1 - 4f * k, 11f * k, p)
        add(DialOp.Circle(cx, cy, 2.6f * k, p.ink))
        // "−7 h" sits opposite the gap so it never collides with the hands.
        val opposite = bodyDeg + sweep / 2f + 180f
        val t = polar(cx, cy, 19f * k, opposite)
        val label = if (s.aligned) "in sync" else s.offsetSigned()
        add(DialOp.Text(label, t.x, t.y, TextSpec(10f * k, weight = 700, slanted = true, tabular = false), p.body))
        if (abs(sweep) < 1f) Unit
    }
}
