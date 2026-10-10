package dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec

import dev.sebastiano.clockblocker.opus.core.designsystem.dial.Daylight
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

    /** The place above the local time on the full dial, in units of side / 328. */
    private val CentrePlaceText = TextSpec(9f, weight = 600, caps = true, tracking = 0.14f, tabular = false)

    /** The hub's width at the place's line (hub radius 74, line 34 above the centre), less a little air. */
    private const val CentrePlaceMaxWidth = 118f

    /**
     * Lays the dial out in a [widthDp] × [heightDp] box.
     *
     * @param scrubMinutes how far the hand is from now (the host owns scrubbing).
     * @param bodyAheadMinutes the body clock relative to local time *as drawn*; hosts animate it when the day
     *   changes so the inner ring turns into place. Defaults to the state's.
     * @param textGrowth extra scale for the centre readouts under large font sizes (kept small so they fit).
     * @param namePlace false on a lock screen that hides details: the local ring and the centre name no place.
     * @param minText the smallest text the host can show, in dp (a widget's): the AM/PM marker and the Simple ring
     *   labels stay at least this big (the local time's digits give way instead). The app's dial (0) keeps the designed proportions.
     * @param labelText the smallest size for the ring labels, in dp, already grown with the font scale (a widget's).
     *   A label its ring can't hold at that size is dropped rather than shrunk. The app's dial (0) keeps its sizes.
     * @param liveReadouts true when the host rewrites the readouts from its own clock between captures (a widget):
     *   the local time and its AM/PM marker are laid out for the widest reading, so longer digits never run into it.
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
        namePlace: Boolean = true,
        minText: Float = 0f,
        labelText: Float = 0f,
        liveReadouts: Boolean = false,
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
            // The body clock runs on real time: when the hand crosses a clock change, its lead on local time changes.
            ahead = DialGeometry.minuteDelta(0f, bodyAheadMinutes - state.clockChangeAt(scrubMinutes)),
            mode = mode,
            grow = textGrowth.coerceIn(1f, 1.15f),
            namePlace = namePlace,
            minText = minText,
            labelText = labelText,
            liveReadouts = liveReadouts,
        )
        val hub = when (level) {
            DetailLevel.Full -> b.full(side / 2f / 164f)
            DetailLevel.Simple -> b.simple(side / 2f / 80f)
            DetailLevel.Glance -> b.glance(side / 2f / 44f)
        }
        return DialSpec(level, widthDp, heightDp, b.cx, b.cy, side / 2f, hub, b.ops.toList(), nowMinute = b.display)
    }

    /** Radius (dp) of the hub inside the body ring for a dial whose smaller side is [sideDp]: taps there return to now. */
    fun hubRadius(sideDp: Float): Float = when (DetailLevel.forSize(sideDp)) {
        DetailLevel.Full -> (86f - 12f) * sideDp / 328f
        DetailLevel.Simple -> (43.5f - 6.5f) * sideDp / 160f
        DetailLevel.Glance -> (32f - 3.25f) * sideDp / 88f
    }

    /** The last few sampled skies, keyed by palette identity (palettes are remembered) and the sun. */
    private object SkyCache {
        private const val MaxEntries = 8
        private class Key(val palette: DialPalette, val sunrise: Float, val sunset: Float, val daylight: Daylight) {
            override fun equals(other: Any?) = other is Key && other.palette === palette &&
                other.sunrise == sunrise && other.sunset == sunset && other.daylight == daylight
            override fun hashCode() =
                ((System.identityHashCode(palette) * 31 + sunrise.hashCode()) * 31 + sunset.hashCode()) * 31 + daylight.hashCode()
        }
        private val entries = LinkedHashMap<Key, List<Argb>>()

        fun get(palette: DialPalette, night: NightSpan, sample: () -> List<Argb>): List<Argb> = synchronized(entries) {
            val key = Key(palette, night.end, night.start, night.daylight)
            entries[key] ?: sample().also {
                if (entries.size >= MaxEntries) entries.remove(entries.keys.first())
                entries[key] = it
            }
        }
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
        val namePlace: Boolean,
        val minText: Float,
        val labelText: Float,
        val liveReadouts: Boolean,
    ) {
        val ops = mutableListOf<DialOp>()
        /**
         * Where you are: the trip's stop (Tromsø keeps Oslo's zone id), else the zone's city. A stop name too wide
         * for the hub falls back to the zone's city at every level, so the rings and the centre always agree.
         */
        val place: String = state.placeName
            ?.takeIf { it.isNotBlank() && measurer.width(it.uppercase(), CentrePlaceText) <= CentrePlaceMaxWidth }
            ?: ZoneLabels.city(state.displayZoneId)
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
            val ringText = TextSpec(maxOf(9.5f * k, labelText), weight = 650, caps = true, tracking = 0.12f, tabular = false)
            val bodyText = ringText.copy(slanted = true)
            if (namePlace) {
                ringLabel(labels.placeNight(place), outerR, ringW, localNight.centre, localNight.lengthMinutes, ringText, p.sky.onNight)
                ringLabel(labels.placeDay(place), outerR, ringW, localNight.dayCentre, 1440f - localNight.lengthMinutes, ringText, p.sky.onDay)
            }
            ringLabel(labels.bodyNight(), innerR, ringW, bodyNight.centre, bodyNight.lengthMinutes, bodyText, p.sky.onNight)
            ringLabel(labels.bodyDay(), innerR, ringW, bodyNight.dayCentre, 1440f - bodyNight.lengthMinutes, bodyText, p.sky.onDay)

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
            if (namePlace) {
                ops += DialOp.Text(
                    place.uppercase(), cx, cy - 34f * k,
                    CentrePlaceText.copy(size = CentrePlaceText.size * k), p.inkMuted, part = DialPart.Readout,
                )
            }
            // The local time sits level with the 06 and 18 numerals: it may grow up to the gap between them.
            val sideNumeral = maxOf(measurer.width(labels.numeral(6), numeral), measurer.width(labels.numeral(18), numeral))
            localTime(
                cy - 10f * k, TextSpec(40f * k * grow, weight = 420), markerSize = 12f * k * grow,
                maxWidth = 2f * (numeralR - sideNumeral / 2f - 3f * k),
            )
            ops += DialOp.Text(
                labels.bodyTime(labels.time(bodyMinute)), cx, cy + 18f * k,
                TextSpec(15f * k * grow, weight = 480, slanted = true), p.body, part = DialPart.Readout,
                live = live(liveBodyTime(labels)),
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
            val ringText = TextSpec(maxOf(7.6f * k, minText, labelText), weight = 700, caps = true, tracking = 0.1f, tabular = false)
            val placeLabel = if (namePlace) shortRingLabel(place, outerR, ringW, localNight, ringText) else null
            placeLabel?.let { ops += it }
            // Side by side on the two rings (the skies match once adapted), "TOKYO" over "BODY" reads as one phrase:
            // the body's label moves to its other half, or goes.
            val bodyText = ringText.copy(slanted = true)
            val bodyLabel = shortRingLabel(labels.body(), innerR, ringW, bodyNight, bodyText)
                ?.takeUnless { placeLabel != null && it.clashes(placeLabel) }
                ?: placeLabel?.let { other ->
                    otherHalfRingLabel(labels.body(), innerR, ringW, bodyNight, bodyText)?.takeUnless { it.clashes(other) }
                }
            bodyLabel?.let { ops += it }
            advice(laneR, laneW, discR = 6.5f * k, nextDiscR = null, rimR = null, sayK = k)
            nowMark(outerR + ringW / 2f + 2.2f * k, 1.5f * k)
            needle(innerR - ringW / 2f - 1f * k, outerR + ringW / 2f + 1.5f * k, 2.2f * k, tip = null)
            val local = localTime(cy - 5f * k, TextSpec(20f * k * grow, weight = 480), markerSize = 7f * k * grow, maxWidth = 62f * k)
            bodyReadout(cy + 12f * k, TextSpec(10.5f * k * grow, weight = 550, slanted = true), innerR - ringW / 2f, local)
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
            val local = localTime(cy - 5f * k, TextSpec(16.5f * k, weight = 520), markerSize = 6.5f * k, maxWidth = 52f * k)
            bodyReadout(cy + 10f * k, TextSpec(11f * k, weight = 580, slanted = true), innerR - ringW / 2f, local)
            return innerR - ringW / 2f
        }

        // endregion
        // region Marks

        /** A clock reading a live host keeps current; none while scrubbing (the readouts show the scrubbed time). */
        fun live(time: LiveTime): LiveTime? = time.takeUnless { scrubbed }

        /**
         * The body time with its AM/PM marker, or "in sync" once adapted. With a host's label floor ([labelText]) it
         * is at least that big and sits under the local time (whose line ends at [above]), inside the hub
         * ([hubR]). When it doesn't fit there, "in sync" goes, then the body time's AM/PM; the body time itself stays.
         */
        fun bodyReadout(y: Float, spec: TextSpec, hubR: Float, above: Float) {
            if (labelText > 0f) return flooredBodyReadout(y, spec, hubR, above)
            ops += if (aligned) {
                DialOp.Text(labels.inSync(), cx, y, spec, p.body, part = DialPart.Readout)
            } else {
                DialOp.Text(labels.fullTime(bodyMinute), cx, y, spec, p.body, part = DialPart.Readout, live = live(LiveTime(LiveClock.Body, marker = true)))
            }
        }

        fun flooredBodyReadout(y0: Float, spec: TextSpec, hubR: Float, above: Float) {
            val sized = if (spec.size < labelText) spec.copy(size = labelText) else spec
            val y = maxOf(y0, above + sized.size / 2f + 1f)
            val edge = kotlin.math.abs(y - cy) + sized.size / 2f
            val room = if (edge < hubR) 2f * kotlin.math.sqrt(hubR * hubR - edge * edge) - 2f else 0f
            fun fits(text: String) = measurer.width(text, sized) <= room

            // A host that rewrites the reading keeps it in the hub in its widest form, either marker included.
            fun fitsLive(text: String) = fits(text) && (!liveReadouts || scrubbed || listOf(10 * 60f, 22 * 60f).all { fits(labels.fullTime(it)) })
            if (aligned) {
                val text = labels.inSync()
                if (fits(text)) ops += DialOp.Text(text, cx, y, sized, p.body, part = DialPart.Readout)
                return
            }
            val full = labels.fullTime(bodyMinute)
            ops += if (fitsLive(full)) {
                DialOp.Text(full, cx, y, sized, p.body, part = DialPart.Readout, live = live(LiveTime(LiveClock.Body, marker = true)))
            } else {
                DialOp.Text(labels.time(bodyMinute), cx, y, sized, p.body, part = DialPart.Readout, live = live(LiveTime(LiveClock.Body)))
            }
        }

        fun face(r: Float) {
            ops += DialOp.Circle(cx, cy, r, p.face, part = DialPart.Face)
        }

        /** The two skies. The body ring is painted in body minutes and turned by the jet lag. */
        fun rings(outerR: Float, innerR: Float, ringW: Float) {
            ops += DialOp.SweepRing(cx, cy, outerR, ringW, skyColors(localNight), part = DialPart.LocalSky)
            val body = BodySky.nightInBody(state, mode)
            ops += DialOp.SweepRing(cx, cy, innerR, ringW, skyColors(body), rotationDeg = -ahead / 4f, part = DialPart.BodySky)
            if (p.dark) {
                listOf(outerR to DialPart.LocalSky, innerR to DialPart.BodySky).forEach { (r, part) ->
                    ops += DialOp.Circle(cx, cy, r + ringW / 2f, p.hairline.withAlpha(0.6f), stroke = 0.75f, part = part)
                    ops += DialOp.Circle(cx, cy, r - ringW / 2f, p.hairline.withAlpha(0.6f), stroke = 0.75f, part = part)
                }
            }
        }

        /**
         * Stop 0 at 3 o'clock (18:00 on the dial's frame), then clockwise. Cached: the spec is rebuilt on every scrub
         * and ring-turn frame, but the skies only change with the palette and the sun.
         */
        fun skyColors(night: NightSpan): List<Argb> = SkyCache.get(p, night) {
            List(SamplesPerRing) { i ->
                p.sky(DialGeometry.minuteForAngle(i * 360f / SamplesPerRing), sunrise = night.end, sunset = night.start, night.daylight)
            }
        }

        /** A Simple-level ring label: on the night, or on the day under the midnight sun, when there is no night. */
        fun shortRingLabel(text: String, r: Float, ringW: Float, night: NightSpan, spec: TextSpec): DialOp.CurvedText? =
            if (night.daylight == Daylight.AlwaysUp) {
                ringLabelOp(text, r, ringW, night.dayCentre, DialGeometry.MinutesPerDay, spec, p.sky.onDay)
            } else {
                ringLabelOp(text, r, ringW, night.centre, night.lengthMinutes, spec, p.sky.onNight)
            }

        /** [shortRingLabel] on the day instead of the night; none under the midnight sun, where it is already there. */
        fun otherHalfRingLabel(text: String, r: Float, ringW: Float, night: NightSpan, spec: TextSpec): DialOp.CurvedText? =
            if (night.daylight == Daylight.AlwaysUp) {
                null
            } else {
                ringLabelOp(text, r, ringW, night.dayCentre, DialGeometry.MinutesPerDay - night.lengthMinutes, spec, p.sky.onDay)
            }

        /** Whether two ring labels (on any rings) sit within [LabelGapDeg] of each other around the dial. */
        fun DialOp.CurvedText.clashes(other: DialOp.CurvedText): Boolean {
            val reach = (degreesFor(text, spec, r) + degreesFor(other.text, other.spec, other.r)) / 2f + LabelGapDeg
            return kotlin.math.abs(DialGeometry.angleDelta(centerDeg, other.centerDeg)) < reach
        }

        fun ringLabel(text: String, r: Float, ringW: Float, centreMinute: Float, spanMinutes: Float, spec: TextSpec, color: Argb) {
            ringLabelOp(text, r, ringW, centreMinute, spanMinutes, spec, color)?.let { ops += it }
        }

        /**
         * A ring label centred on [centreMinute], or null when it doesn't fit inside its half of the sky, or across
         * its ring ([ringW] wide). When the needle would cross it, it steps aside along its half (the side it was
         * already on, else the other one).
         */
        fun ringLabelOp(text: String, r: Float, ringW: Float, centreMinute: Float, spanMinutes: Float, spec: TextSpec, color: Argb): DialOp.CurvedText? {
            if (!fitsAcross(spec, ringW)) return null
            val shown = if (spec.caps) text.uppercase() else text
            val needs = degreesFor(shown, spec, r)
            val span = spanMinutes / 4f
            if (needs > span - 14f) return null
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
            return DialOp.CurvedText(shown, cx, cy, r, at.mod(360f), spec, color, part = DialPart.RingLabel)
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
                current != null && current.sweepMinutes > 0f -> labels.until(label, labels.fullTime(current.narratedEndMinute))
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
            // "then …" only when it really follows: advice that starts before the block in focus ends (sleep inside
            // a long flight) is narrated with its start instead, so the rim never puts them in the wrong order.
            val nextLabel = labels.advice(next.type)
            val thenText = if (startsBeforeEnd(next, current)) labels.at(nextLabel, labels.fullTime(next.startMinute)) else labels.then(nextLabel)
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
                ops += DialOp.Arc(cx, cy, r, w * 1.2f, start - 4f, elapsed / 4f + 4f, p.face.withAlpha(0.42f), part = DialPart.AdvicePast)
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
        fun localTime(y: Float, spec: TextSpec, markerSize: Float, maxWidth: Float = Float.MAX_VALUE): Float {
            val digits = labels.time(display)
            val marker = labels.marker(display)
            val markerSpec = TextSpec(markerSize, weight = 600, tabular = false)
            val gap = spec.size * 0.06f
            // A host that rewrites the reading lays the group out for its widest digits ("10:00", "12:00").
            val steady = liveReadouts && !scrubbed && marker != null
            val wide = if (steady) listOf(digits, labels.time(10 * 60f), labels.time(12 * 60f)).maxBy { measurer.width(it, spec) } else digits
            // …and for the wider of its markers: a locale's evening one can be longer than its morning one.
            val wideMarker = if (steady) {
                listOfNotNull(marker, labels.marker(MorningMinute), labels.marker(EveningMinute)).maxBy { measurer.width(it, markerSpec) }
            } else {
                marker
            }
            val natural = measurer.width(wide, spec) + (wideMarker?.let { gap + measurer.width(it, markerSpec) } ?: 0f)
            var fit = if (natural > maxWidth && natural > 0f) maxWidth / natural else 1f
            fun digitsAlone(fit: Float): Float {
                val digitsSpec = spec.copy(size = maxOf(spec.size * fit, labelText))
                ops += DialOp.Text(digits, cx, y, digitsSpec, p.ink, part = DialPart.Readout, live = live(LiveTime(LiveClock.Local)))
                return y + digitsSpec.size / 2f
            }
            if (marker == null || wideMarker == null) return digitsAlone(fit)
            val smallSpec = markerSpec.copy(size = maxOf(markerSize * fit, minText, labelText))
            if (smallSpec.size > markerSize * fit) {
                // The marker is held at the host's floor: the digits take what is left of the width.
                val room = maxWidth - gap * fit - measurer.width(wideMarker, smallSpec)
                fit = minOf(fit, room / measurer.width(wide, spec)).coerceAtLeast(0f)
            }
            val digitsSpec = spec.copy(size = spec.size * fit)
            if (labelText > 0f && digitsSpec.size < smallSpec.size * MinDigitsToMarker) {
                // The marker at the label floor would dwarf the digits: the time goes without it.
                val alone = measurer.width(wide, spec)
                return digitsAlone(if (alone > maxWidth && alone > 0f) maxWidth / alone else 1f)
            }
            val w = measurer.width(wide, digitsSpec)
            val total = w + gap * fit + measurer.width(wideMarker, smallSpec)
            val left = cx - total / 2f
            if (steady) {
                // Ending at the marker: digits that grow on the host grow away from it.
                ops += DialOp.Text(digits, left + w, y, digitsSpec, p.ink, h = HAlign.End, part = DialPart.Readout, live = live(LiveTime(LiveClock.Local)))
            } else {
                ops += DialOp.Text(digits, left, y, digitsSpec, p.ink, h = HAlign.Start, part = DialPart.Readout, live = live(LiveTime(LiveClock.Local)))
            }
            ops += DialOp.Text(
                marker, left + w + gap * fit, y - digitsSpec.size * 0.18f, smallSpec, p.inkMuted, h = HAlign.Start,
                part = DialPart.Readout, live = live(LiveTime(LiveClock.Local, digits = false, marker = true)),
            )
            return y + digitsSpec.size / 2f
        }

        fun pill(y: Float, text: String, spec: TextSpec, padH: Float, height: Float) {
            val w = measurer.width(text, spec) + 2f * padH
            ops += DialOp.Rect(cx - w / 2f, y - height / 2f, cx + w / 2f, y + height / 2f, height / 2f, p.bodyContainer, part = DialPart.Offset)
            ops += DialOp.Text(text, cx, y, spec, p.onBodyContainer, part = DialPart.Offset)
        }

        // endregion

        /** Whether [spec]'s capitals fit across a ring or bar [thickness] wide, with a little air. */
        fun fitsAcross(spec: TextSpec, thickness: Float): Boolean = spec.size * CapHeight <= thickness * MaxFill

        fun degreesFor(text: String, spec: TextSpec, r: Float): Float =
            Math.toDegrees((measurer.width(text, spec) / r).toDouble()).toFloat()

        fun polar(r: Float, deg: Float): Pair<Float, Float> {
            val rad = Math.toRadians(deg.toDouble())
            return (cx + r * cos(rad).toFloat()) to (cy + r * sin(rad).toFloat())
        }
    }
}

/** Capital height per dp of text size (the system font's is about 0.71). */
internal const val CapHeight = 0.72f

/** The most of a ring's or bar's thickness a label's capitals may fill. */
internal const val MaxFill = 0.85f

/** The local time's digits stay at least this many times the size of its AM/PM marker, or the marker goes. */
private const val MinDigitsToMarker = 1.15f

/** A morning and an evening minute, to read a locale's two AM/PM markers. */
private const val MorningMinute = 9 * 60f
private const val EveningMinute = 21 * 60f

/** The least angle between the ends of the Simple dial's two ring labels, so they never read as one phrase. */
private const val LabelGapDeg = 12f

/** "08:20 body" as a [LiveTime]: the body clock's digits inside [DialLabels.bodyTime]'s words. */
internal fun liveBodyTime(labels: DialLabels): LiveTime {
    val template = labels.bodyTime(TimeSlot)
    return LiveTime(LiveClock.Body, prefix = template.substringBefore(TimeSlot), suffix = template.substringAfter(TimeSlot, ""))
}

private const val TimeSlot = "\u0001"

/**
 * Whether [next] starts before [current] ends, in real time when both carry their instants (a block of 24 h or more
 * wraps the face, and a DST change shifts its minutes); otherwise by minutes of the day from [current]'s start.
 */
internal fun startsBeforeEnd(next: DialArc, current: DialArc): Boolean {
    val start = next.startInstant
    val end = current.endInstant
    if (start != null && end != null) return start.isBefore(end)
    val currentRuns = (current.narratedEndMinute - current.startMinute).mod(DialGeometry.MinutesPerDay)
    val nextIn = (next.startMinute - current.startMinute).mod(DialGeometry.MinutesPerDay)
    return nextIn < currentRuns
}
