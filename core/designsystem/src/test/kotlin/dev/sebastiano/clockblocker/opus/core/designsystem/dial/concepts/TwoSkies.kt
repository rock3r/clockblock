package dev.sebastiano.clockblocker.opus.core.designsystem.dial.concepts

import kotlin.math.min

/**
 * Concept 1, "Two skies": two rings and nothing else structural. Outer = the sky where you are; inner = the same
 * sky turned by the jet lag, i.e. the sky your body thinks it is under. The offset between the two nights *is* the
 * jet lag; once adapted the rings are identical. The current advice is one labelled arc outside.
 *
 * Encodings: (1) day/night colour of the two rings, (2) the now needle, (3) the advice arc + glyph. Text labels
 * sit on the marks.
 */
object TwoSkies {
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

    private fun Ops.rings(
        s: ConceptState, p: ConceptPalette, cx: Float, cy: Float,
        outerR: Float, innerR: Float, ringW: Float,
    ) {
        skyRing(cx, cy, outerR, ringW, s.sunriseMinute, s.sunsetMinute, p)
        skyRing(cx, cy, innerR, ringW, s.bodySunrise(), s.bodySunset(), p)
        if (p.dark) {
            listOf(outerR, innerR).forEach { r ->
                add(DialOp.Circle(cx, cy, r + ringW / 2f, p.hairline.copy(alpha = 0.6f), stroke = 0.75f))
                add(DialOp.Circle(cx, cy, r - ringW / 2f, p.hairline.copy(alpha = 0.6f), stroke = 0.75f))
            }
        }
    }

    private fun Ops.full(s: ConceptState, p: ConceptPalette, cx: Float, cy: Float, k: Float) {
        val rimR = 154f * k
        val laneR = 138.5f * k
        val laneW = 13f * k
        val outerR = 114f * k
        val innerR = 86f * k
        val ringW = 24f * k
        add(DialOp.Circle(cx, cy, 164f * k, p.face))

        rings(s, p, cx, cy, outerR, innerR, ringW)

        // Ring labels, on the marks themselves: who the ring belongs to, and which half is night.
        val ringText = TextSpec(9.5f * k, weight = 650, caps = true, tracking = 0.12f, tabular = false)
        val bodyText = ringText.copy(slanted = true)
        val ln = nightCentre(s.sunriseMinute, s.sunsetMinute)
        val ld = dayCentre(s.sunriseMinute, s.sunsetMinute)
        val bn = nightCentre(s.bodySunrise(), s.bodySunset())
        val bd = dayCentre(s.bodySunrise(), s.bodySunset())
        add(DialOp.CurvedText("${s.city} night", cx, cy, outerR, dialAngle(ln), ringText, p.onNight))
        add(DialOp.CurvedText("${s.city} day", cx, cy, outerR, dialAngle(ld), ringText, p.onDay))
        add(DialOp.CurvedText("your body's night", cx, cy, innerR, dialAngle(bn), bodyText, p.onNight))
        add(DialOp.CurvedText("your body's day", cx, cy, innerR, dialAngle(bd), bodyText, p.onDay))

        // Hour numerals just inside the body ring (local clock).
        val num = TextSpec(8.5f * k, weight = 500)
        listOf(0, 6, 12, 18).forEach { hr ->
            val pt = polar(cx, cy, innerR - ringW / 2f - 8f * k, dialAngle(hr * 60f))
            add(DialOp.Text("%02d".format(hr), pt.x, pt.y, num, p.inkMuted))
        }

        // Current advice: one arc on the outer lane with its glyph; the narration runs along the rim above it.
        s.current?.let { a ->
            adviceArc(cx, cy, laneR, laneW, a, s.localMinute, p)
            val start = dialAngle(a.startMinute)
            val g = polar(cx, cy, laneR, start)
            adviceGlyph(a.type, g.x, g.y, 10f * k, p, ring = p.face)
            val say = TextSpec(10f * k, weight = 650, tabular = true)
            val text = "${a.label} until ${hhmm(a.endMinute)}"
            val deg = arcDegreesFor(text, say, rimR)
            val from = start - 3f
            add(DialOp.CurvedText(text, cx, cy, rimR, from + deg / 2f, say, p.ink))
            s.next?.let { n ->
                val nd = dialAngle(n.startMinute)
                val np = polar(cx, cy, laneR, nd + 4f)
                adviceGlyph(n.type, np.x, np.y, 8f * k, p, ring = p.face)
                val thenSpec = TextSpec(10f * k, weight = 550, tabular = false)
                val thenText = "then ${n.label.lowercase()}"
                val thenDeg = arcDegreesFor(thenText, thenSpec, rimR)
                val thenFrom = maxOf(from + deg + 2.5f, nd)
                add(DialOp.CurvedText(thenText, cx, cy, rimR, thenFrom + thenDeg / 2f, thenSpec, p.inkMuted))
            }
        }

        // Now: one needle across both skies. Where it crosses each ring is the answer.
        val nowDeg = dialAngle(s.localMinute)
        needle(cx, cy, nowDeg, innerR - ringW / 2f - 2f * k, outerR + ringW / 2f + 2.5f * k, 3f * k, p.ink, p.face)
        val tip = polar(cx, cy, outerR + ringW / 2f + 2.5f * k, nowDeg)
        add(DialOp.Circle(tip.x, tip.y, 4.5f * k, p.face))
        add(DialOp.Circle(tip.x, tip.y, 3.2f * k, p.ink))

        // Centre: local upright, body slanted, the offset in words.
        add(DialOp.Text(s.city.uppercase(), cx, cy - 34f * k, TextSpec(9f * k, weight = 600, tracking = 0.14f, tabular = false), p.inkMuted))
        add(DialOp.Text(hhmm(s.localMinute), cx, cy - 10f * k, TextSpec(40f * k, weight = 420), p.ink))
        add(DialOp.Text("${hhmm(s.bodyMinute)} body", cx, cy + 18f * k, TextSpec(15f * k, weight = 480, slanted = true), p.body))
        pill(cx, cy + 40f * k, s.offsetWords(), TextSpec(9.5f * k, weight = 650, slanted = true, tabular = false), p.bodyContainer, p.onBodyContainer, 8f * k, 18f * k)
    }

    private fun Ops.simple(s: ConceptState, p: ConceptPalette, cx: Float, cy: Float, k: Float) {
        val laneR = 73.5f * k
        val laneW = 9f * k
        val outerR = 59f * k
        val innerR = 43.5f * k
        val ringW = 13f * k
        add(DialOp.Circle(cx, cy, 80f * k, p.face))
        rings(s, p, cx, cy, outerR, innerR, ringW)
        val ringText = TextSpec(7.6f * k, weight = 700, caps = true, tracking = 0.1f, tabular = false)
        add(DialOp.CurvedText(s.city, cx, cy, outerR, dialAngle(nightCentre(s.sunriseMinute, s.sunsetMinute)), ringText, p.onNight))
        add(DialOp.CurvedText("body", cx, cy, innerR, dialAngle(nightCentre(s.bodySunrise(), s.bodySunset())), ringText.copy(slanted = true), p.onNight))
        s.current?.let { a ->
            adviceArc(cx, cy, laneR, laneW, a, s.localMinute, p)
            val g = polar(cx, cy, laneR, dialAngle(a.startMinute))
            adviceGlyph(a.type, g.x, g.y, 7f * k, p, ring = p.face)
        }
        val nowDeg = dialAngle(s.localMinute)
        needle(cx, cy, nowDeg, innerR - ringW / 2f - 1f * k, outerR + ringW / 2f + 1.5f * k, 2.2f * k, p.ink, p.face)
        add(DialOp.Text(hhmm(s.localMinute), cx, cy - 5f * k, TextSpec(20f * k, weight = 480), p.ink))
        add(DialOp.Text(if (s.aligned) "in sync" else hhmm(s.bodyMinute), cx, cy + 12f * k, TextSpec(10.5f * k, weight = 550, slanted = true), p.body))
    }

    private fun Ops.glance(s: ConceptState, p: ConceptPalette, cx: Float, cy: Float, k: Float) {
        val outerR = 39.5f * k
        val innerR = 32f * k
        val ringW = 6.5f * k
        add(DialOp.Circle(cx, cy, 44f * k, p.face))
        rings(s, p, cx, cy, outerR, innerR, ringW)
        needle(cx, cy, dialAngle(s.localMinute), innerR - ringW / 2f, outerR + ringW / 2f, 1.8f * k, p.ink, p.face)
        add(DialOp.Text(hhmm(s.localMinute), cx, cy - 5f * k, TextSpec(16.5f * k, weight = 520), p.ink))
        add(DialOp.Text(if (s.aligned) "in sync" else hhmm(s.bodyMinute), cx, cy + 10f * k, TextSpec(11f * k, weight = 580, slanted = true), p.body))
    }
}
