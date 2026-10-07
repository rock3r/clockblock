package dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec

import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialArc
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialGeometry
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.ZoneLabels
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * **Two skies** (issue #46): two rings and nothing else structural. The outer ring is the sky where you are; the
 * inner ring is the sky your body thinks it is under, i.e. the same sky turned by the jet lag ([BodyRingMode]).
 * The angle between the two nights *is* the jet lag, and once adapted the rings are identical. One needle crosses
 * both; the advice under it is one labelled arc outside the rings.
 *
 * Three encodings only: the day/night colour of the two rings, the needle, and the advice arc with its glyph.
 * Every mark carries its own label.
 */
object TwoSkies {
    /** Sweep-gradient stops per sky ring: one every 2° (8 minutes of sky). */
    private const val SamplesPerRing = 180

    /**
     * Lays the dial out in a [widthDp] × [heightDp] box.
     *
     * @param scrubMinutes how far the hand is from now (the host owns scrubbing).
     * @param bodyAheadMinutes the body clock relative to local time *as drawn*; hosts animate it when the day
     *   changes so the inner ring turns into place. Defaults to the state's.
     * @param textGrowth extra scale for the centre readouts under large font sizes (kept small so they fit).
     */
    fun spec(
        state: DialState,
        palette: DialPalette,
        labels: DialLabels,
        widthDp: Float,
        heightDp: Float,
        scrubMinutes: Float = 0f,
        bodyAheadMinutes: Float = state.bodyAheadMinutes,
        mode: BodyRingMode = BodyRingMode.Simple,
        measurer: DialTextMeasurer = ApproxTextMeasurer,
        textGrowth: Float = 1f,
    ): DialSpec {
        val side = min(widthDp, heightDp)
        val level = DetailLevel.forSize(side)
        val b = Builder(
            state = state,
            p = palette,
            labels = labels,
            measurer = measurer,
            cx = widthDp / 2f,
            cy = heightDp / 2f,
            display = (state.localMinute + scrubMinutes).mod(DialGeometry.MinutesPerDay),
            scrubbed = scrubMinutes != 0f,
            ahead = bodyAheadMinutes,
            mode = mode,
            grow = textGrowth.coerceIn(1f, 1.15f),
        )
        val hub = when (level) {
            DetailLevel.Full -> b.full(side / 2f / 164f)
            DetailLevel.Simple -> b.simple(side / 2f / 80f)
            DetailLevel.Glance -> b.glance(side / 2f / 44f)
        }
        return DialSpec(level, widthDp, heightDp, b.cx, b.cy, side / 2f, hub, b.ops.toList())
    }

    /** Radius (dp) of the hub inside the body ring for a dial whose smaller side is [sideDp]: taps there return to now. */
    fun hubRadius(sideDp: Float): Float = when (DetailLevel.forSize(sideDp)) {
        DetailLevel.Full -> (86f - 12f) * sideDp / 328f
        DetailLevel.Simple -> (43.5f - 6.5f) * sideDp / 160f
        DetailLevel.Glance -> (32f - 3.25f) * sideDp / 88f
    }

    private class Builder(
        val state: DialState,
        val p: DialPalette,
        val labels: DialLabels,
        val measurer: DialTextMeasurer,
        val cx: Float,
        val cy: Float,
        val display: Float,
        val scrubbed: Boolean,
        val ahead: Float,
        val mode: BodyRingMode,
        val grow: Float,
    ) {
        val ops = mutableListOf<DialOp>()
        val place: String = ZoneLabels.city(state.displayZoneId)
        val localNight = BodySky.localNight(state)
        val bodyNight = BodySky.nightInLocal(state, mode, ahead)
        val focus = state.focusAt(display)
        val aligned = DefaultDialLabels.isInSync(ahead)
        val bodyMinute = (display + ahead).mod(DialGeometry.MinutesPerDay)

        // region Levels

        /** ≥ 250 dp. Units of [k] = side / 328. Returns the hub radius. */
        fun full(k: Float): Float {
            val rimR = 154f * k
            val laneR = 138.5f * k
            val laneW = 13f * k
            val outerR = 114f * k
            val innerR = 86f * k
            val ringW = 24f * k
            face(164f * k)
            rings(outerR, innerR, ringW)

            // Ring labels, on the marks themselves: whose sky it is, and which half is night.
            val ringText = TextSpec(9.5f * k, weight = 650, caps = true, tracking = 0.12f, tabular = false)
            val bodyText = ringText.copy(slanted = true)
            ringLabel(labels.placeNight(place), outerR, localNight.centre, localNight.lengthMinutes, ringText, p.sky.onNight)
            ringLabel(labels.placeDay(place), outerR, localNight.dayCentre, 1440f - localNight.lengthMinutes, ringText, p.sky.onDay)
            ringLabel(labels.bodyNight(), innerR, bodyNight.centre, bodyNight.lengthMinutes, bodyText, p.sky.onNight)
            ringLabel(labels.bodyDay(), innerR, bodyNight.dayCentre, 1440f - bodyNight.lengthMinutes, bodyText, p.sky.onDay)

            // The local clock's numerals, just inside the body ring.
            val numeral = TextSpec(8.5f * k, weight = 500)
            val numeralR = innerR - ringW / 2f - 8f * k
            listOf(0, 6, 12, 18).forEach { hour ->
                val (x, y) = polar(numeralR, DialGeometry.angleForMinute(hour * 60f))
                ops += DialOp.Text(labels.numeral(hour), x, y, numeral, p.inkMuted, part = DialPart.Numeral)
            }

            advice(laneR, laneW, discR = 10f * k, nextDiscR = 8f * k, rimR = rimR, sayK = k)
            nowMark(outerR + ringW / 2f + 3.5f * k, 2.4f * k)
            needle(innerR - ringW / 2f - 2f * k, outerR + ringW / 2f + 2.5f * k, 3f * k, tip = 4.5f * k)

            // Centre: where you are, local time upright, body time slanted, the offset in words.
            ops += DialOp.Text(
                place.uppercase(), cx, cy - 34f * k,
                TextSpec(9f * k, weight = 600, caps = true, tracking = 0.14f, tabular = false), p.inkMuted, part = DialPart.Readout,
            )
            // The local time sits level with the 06 and 18 numerals: it may grow up to the gap between them.
            val sideNumeral = maxOf(measurer.width(labels.numeral(6), numeral), measurer.width(labels.numeral(18), numeral))
            localTime(
                cy - 10f * k, TextSpec(40f * k * grow, weight = 420), markerSize = 12f * k * grow,
                maxWidth = 2f * (numeralR - sideNumeral / 2f - 3f * k),
            )
            ops += DialOp.Text(
                labels.bodyTime(labels.time(bodyMinute)), cx, cy + 18f * k,
                TextSpec(15f * k * grow, weight = 480, slanted = true), p.body, part = DialPart.Readout,
            )
            pill(cy + 41f * k, labels.offset(ahead), TextSpec(9.5f * k, weight = 650, slanted = true, tabular = false), 8f * k, 18f * k)
            return innerR - ringW / 2f
        }

        /** 110–250 dp. Units of [k] = side / 160. */
        fun simple(k: Float): Float {
            val laneR = 72.5f * k
            val laneW = 9f * k
            val outerR = 58.5f * k
            val innerR = 43.5f * k
            val ringW = 13f * k
            face(80f * k)
            rings(outerR, innerR, ringW)
            val ringText = TextSpec(7.6f * k, weight = 700, caps = true, tracking = 0.1f, tabular = false)
            ringLabel(place, outerR, localNight.centre, localNight.lengthMinutes, ringText, p.sky.onNight)
            ringLabel(labels.body(), innerR, bodyNight.centre, bodyNight.lengthMinutes, ringText.copy(slanted = true), p.sky.onNight)
            advice(laneR, laneW, discR = 6.5f * k, nextDiscR = null, rimR = null, sayK = k)
            nowMark(outerR + ringW / 2f + 2.2f * k, 1.5f * k)
            needle(innerR - ringW / 2f - 1f * k, outerR + ringW / 2f + 1.5f * k, 2.2f * k, tip = null)
            ops += DialOp.Text(labels.time(display), cx, cy - 5f * k, TextSpec(20f * k * grow, weight = 480), p.ink, part = DialPart.Readout)
            ops += DialOp.Text(
                if (aligned) labels.inSync() else labels.time(bodyMinute), cx, cy + 12f * k,
                TextSpec(10.5f * k * grow, weight = 550, slanted = true), p.body, part = DialPart.Readout,
            )
            return innerR - ringW / 2f
        }

        /** Under 110 dp. Units of [k] = side / 88. */
        fun glance(k: Float): Float {
            val outerR = 39.5f * k
            val innerR = 32f * k
            val ringW = 6.5f * k
            face(44f * k)
            rings(outerR, innerR, ringW)
            needle(innerR - ringW / 2f, outerR + ringW / 2f, 1.8f * k, tip = null)
            ops += DialOp.Text(labels.time(display), cx, cy - 5f * k, TextSpec(16.5f * k, weight = 520), p.ink, part = DialPart.Readout)
            ops += DialOp.Text(
                if (aligned) labels.inSync() else labels.time(bodyMinute), cx, cy + 10f * k,
                TextSpec(11f * k, weight = 580, slanted = true), p.body, part = DialPart.Readout,
            )
            return innerR - ringW / 2f
        }

        // endregion
        // region Marks

        fun face(r: Float) {
            ops += DialOp.Circle(cx, cy, r, p.face, part = DialPart.Face)
        }

        /** The two skies. The body ring is painted in body minutes and turned by the jet lag. */
        fun rings(outerR: Float, innerR: Float, ringW: Float) {
            ops += DialOp.SweepRing(cx, cy, outerR, ringW, skyColors(state.sunriseMinute, state.sunsetMinute), part = DialPart.LocalSky)
            val body = BodySky.nightInBody(state, mode)
            ops += DialOp.SweepRing(cx, cy, innerR, ringW, skyColors(body.end, body.start), rotationDeg = -ahead / 4f, part = DialPart.BodySky)
            if (p.dark) {
                listOf(outerR to DialPart.LocalSky, innerR to DialPart.BodySky).forEach { (r, part) ->
                    ops += DialOp.Circle(cx, cy, r + ringW / 2f, p.hairline.withAlpha(0.6f), stroke = 0.75f, part = part)
                    ops += DialOp.Circle(cx, cy, r - ringW / 2f, p.hairline.withAlpha(0.6f), stroke = 0.75f, part = part)
                }
            }
        }

        /** Stop 0 at 3 o'clock (18:00 on the dial's frame), then clockwise. */
        fun skyColors(sunrise: Float, sunset: Float): List<Argb> = List(SamplesPerRing) { i ->
            p.sky(DialGeometry.minuteForAngle(i * 360f / SamplesPerRing), sunrise, sunset)
        }

        /**
         * A ring label centred on [centreMinute], dropped when it doesn't fit inside its half of the sky. When the
         * needle would cross it, it steps aside along its half (the side it was already on, else the other one).
         */
        fun ringLabel(text: String, r: Float, centreMinute: Float, spanMinutes: Float, spec: TextSpec, color: Argb) {
            val shown = if (spec.caps) text.uppercase() else text
            val needs = degreesFor(shown, spec, r)
            val span = spanMinutes / 4f
            if (needs > span - 14f) return
            val centre = DialGeometry.angleForMinute(centreMinute)
            val needle = DialGeometry.angleForMinute(display)
            val clear = needs / 2f + 4f
            val d = DialGeometry.angleDelta(centre, needle)
            var at = centre
            if (kotlin.math.abs(d) < clear) {
                // Room for the label's centre on either side of the needle, inside its half of the sky.
                val slack = span / 2f - needs / 2f - 3f
                val away = if (d > 0f) -1f else 1f
                at = listOf(away, -away)
                    .map { side -> needle + side * clear }
                    .firstOrNull { kotlin.math.abs(DialGeometry.angleDelta(centre, it)) <= slack }
                    ?: centre
            }
            ops += DialOp.CurvedText(shown, cx, cy, r, at.mod(360f), spec, color, part = DialPart.RingLabel)
        }

        /**
         * The advice in focus: the block under the hand as one arc on the lane with its glyph at the start (or,
         * when nothing is on, the next block), and on the full dial its narration along the rim.
         */
        fun advice(laneR: Float, laneW: Float, discR: Float, nextDiscR: Float?, rimR: Float?, sayK: Float) {
            val current = focus.current
            val next = focus.next
            val shown = current ?: next ?: return
            val start = DialGeometry.angleForMinute(shown.startMinute)
            if (shown.sweepMinutes > 0f) adviceArc(laneR, laneW, shown, washPast = current != null)
            glyph(shown, laneR, start, discR)
            val say = TextSpec(10f * sayK, weight = 650)
            val label = labels.advice(shown.type)
            val text = when {
                current != null -> labels.until(label, labels.fullTime(current.endMinute))
                else -> labels.at(label, labels.fullTime(shown.startMinute))
            }
            val from = start - 3f
            val sayDeg = rimR?.let { degreesFor(text, say, it) } ?: 0f
            // The narration runs clockwise from the glyph, along its arc; on the lower half it reads the other way.
            val inward = DialOp.CurvedText.readsInwardAt(from + sayDeg / 2f)
            if (rimR != null) {
                ops += DialOp.CurvedText(text, cx, cy, rimR, from + sayDeg / 2f, say, p.ink, part = DialPart.Narration, readsInward = inward)
            }
            if (current == null || next == null) return
            // What follows: its glyph where it starts, and "then …" on the rim, read straight after the narration.
            val endDeg = start + current.sweepMinutes / 4f
            val nextDeg = start + (DialGeometry.angleForMinute(next.startMinute) - start).mod(360f)
            val glyphDeg = if (kotlin.math.abs(nextDeg - endDeg) < 4f) nextDeg + 4f else nextDeg
            nextDiscR?.let { glyph(next, laneR, glyphDeg, it) }
            if (rimR == null) return
            val thenSpec = TextSpec(10f * sayK, weight = 550, tabular = false)
            val thenText = labels.then(labels.advice(next.type))
            val thenDeg = degreesFor(thenText, thenSpec, rimR)
            val thenCentre = if (inward) {
                // Reading right to left round the dial: "then …" continues on the far side of the glyph.
                from - 2.5f - thenDeg / 2f
            } else {
                maxOf(from + sayDeg + 2.5f, nextDeg) + thenDeg / 2f
            }
            // Never run round into the narration itself.
            val runs = if (inward) sayDeg + 2.5f + thenDeg else thenCentre + thenDeg / 2f - from
            if (runs > 330f) return
            ops += DialOp.CurvedText(thenText, cx, cy, rimR, thenCentre, thenSpec, p.inkMuted, part = DialPart.Narration, readsInward = inward)
        }

        /**
         * One advice arc: an edge in the strong tone, the fill, a hatch for avoid light (the colour-blind carrier),
         * and the part already behind the hand washed back towards the face.
         */
        fun adviceArc(r: Float, w: Float, a: DialArc, washPast: Boolean) {
            val start = DialGeometry.angleForMinute(a.startMinute)
            val sweep = a.sweepMinutes / 4f
            if (!p.dark) ops += DialOp.Arc(cx, cy, r, w + w * 0.16f, start, sweep, p.advice(a.type).color.withAlpha(0.85f), roundCap = true, part = DialPart.Advice)
            ops += DialOp.Arc(cx, cy, r, w, start, sweep, p.adviceFill(a.type), roundCap = true, part = DialPart.Advice)
            if (a.type == AdviceType.AvoidLight) {
                val ink = p.adviceInk(a.type).withAlpha(0.28f)
                val skew = Math.toDegrees((w * 0.8f / r).toDouble()).toFloat()
                val step = Math.toDegrees((w * 0.32f / r).toDouble()).toFloat()
                val inset = w * 0.09f
                var t = start + 2f
                while (t + skew < start + sweep - 1f) {
                    val (x0, y0) = polar(r + w / 2f - inset, t)
                    val (x1, y1) = polar(r - w / 2f + inset, t + skew)
                    ops += DialOp.Line(x0, y0, x1, y1, ink, w * 0.085f, roundCap = false, part = DialPart.Advice)
                    t += step
                }
            }
            if (!washPast) return
            val elapsed = (display - a.startMinute).mod(DialGeometry.MinutesPerDay).coerceAtMost(a.sweepMinutes)
            if (elapsed > 0f && elapsed < a.sweepMinutes) {
                ops += DialOp.Arc(cx, cy, r, w * 1.2f, start - 4f, elapsed / 4f + 4f, p.face.withAlpha(0.42f), part = DialPart.Advice)
            }
        }

        fun glyph(a: DialArc, r: Float, deg: Float, discR: Float) {
            val (x, y) = polar(r, deg)
            val role = p.advice(a.type)
            ops += DialOp.Glyph(a.type, x, y, discR * 1.25f, role.onColor, role.color, discR, ring = p.face)
        }

        /** While scrubbing: a dot where the real now is. */
        fun nowMark(r: Float, dotR: Float) {
            if (!scrubbed) return
            val (x, y) = polar(r, DialGeometry.angleForMinute(state.localMinute))
            ops += DialOp.Circle(x, y, dotR, p.inkMuted, part = DialPart.NowMark)
        }

        /** One needle across both skies, with a face-coloured halo so it separates from every ring it crosses. */
        fun needle(r0: Float, r1: Float, width: Float, tip: Float?) {
            val deg = DialGeometry.angleForMinute(display)
            val (x0, y0) = polar(r0, deg)
            val (x1, y1) = polar(r1, deg)
            ops += DialOp.Line(x0, y0, x1, y1, p.face, width * 2f, part = DialPart.Needle)
            ops += DialOp.Line(x0, y0, x1, y1, p.ink, width, part = DialPart.Needle)
            if (tip != null) {
                ops += DialOp.Circle(x1, y1, tip, p.face, part = DialPart.Needle)
                ops += DialOp.Circle(x1, y1, tip * 0.71f, p.ink, part = DialPart.Needle)
            }
        }

        /**
         * The local time, centred as a group with its AM/PM marker on 12-hour clocks, scaled down as a whole when
         * it would be wider than [maxWidth].
         */
        fun localTime(y: Float, spec: TextSpec, markerSize: Float, maxWidth: Float = Float.MAX_VALUE) {
            val digits = labels.time(display)
            val marker = labels.marker(display)
            val markerSpec = TextSpec(markerSize, weight = 600, tabular = false)
            val gap = spec.size * 0.06f
            val natural = measurer.width(digits, spec) + (marker?.let { gap + measurer.width(it, markerSpec) } ?: 0f)
            val fit = if (natural > maxWidth && natural > 0f) maxWidth / natural else 1f
            val digitsSpec = spec.copy(size = spec.size * fit)
            if (marker == null) {
                ops += DialOp.Text(digits, cx, y, digitsSpec, p.ink, part = DialPart.Readout)
                return
            }
            val smallSpec = markerSpec.copy(size = markerSize * fit)
            val w = measurer.width(digits, digitsSpec)
            val total = w + gap * fit + measurer.width(marker, smallSpec)
            val left = cx - total / 2f
            ops += DialOp.Text(digits, left, y, digitsSpec, p.ink, h = HAlign.Start, part = DialPart.Readout)
            ops += DialOp.Text(marker, left + w + gap * fit, y - digitsSpec.size * 0.18f, smallSpec, p.inkMuted, h = HAlign.Start, part = DialPart.Readout)
        }

        fun pill(y: Float, text: String, spec: TextSpec, padH: Float, height: Float) {
            val w = measurer.width(text, spec) + 2f * padH
            ops += DialOp.Rect(cx - w / 2f, y - height / 2f, cx + w / 2f, y + height / 2f, height / 2f, p.bodyContainer, part = DialPart.Offset)
            ops += DialOp.Text(text, cx, y, spec, p.onBodyContainer, part = DialPart.Offset)
        }

        // endregion

        fun degreesFor(text: String, spec: TextSpec, r: Float): Float =
            Math.toDegrees((measurer.width(text, spec) / r).toDouble()).toFloat()

        fun polar(r: Float, deg: Float): Pair<Float, Float> {
            val rad = Math.toRadians(deg.toDouble())
            return (cx + r * cos(rad).toFloat()) to (cy + r * sin(rad).toFloat())
        }
    }
}
