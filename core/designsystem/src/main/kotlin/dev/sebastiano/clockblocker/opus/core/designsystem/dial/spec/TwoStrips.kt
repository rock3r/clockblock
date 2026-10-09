package dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec

import dev.sebastiano.clockblocker.opus.core.designsystem.dial.Daylight
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialArc
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialGeometry
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.ZoneLabels
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * **Two strips** (issue #46, concept 3): the Two skies dial unrolled for tight and wide spaces. Two day bars share
 * one time axis: the sky where you are on top, the sky your body thinks it is under below (the same sky shifted by
 * the jet lag, [BodyRingMode]). A now line crosses both, so one glance shows "afternoon here, morning for you", and
 * the bars line up once you've adapted. No curved text.
 *
 * The window is a fixed 24 h with now [LeadFraction] of the way in; live hosts slide the now line along the
 * [TimeAxis] until the next capture. Levels, by the box ([DetailLevel.forStrip]):
 * - [DetailLevel.Glance]: the bars and the now line between the two times (a 1×1 widget, a narrow 2×1).
 * - [DetailLevel.Simple]: the times beside or above the bars, the bars' night labels, the advice in focus.
 * - [DetailLevel.Full]: adds the advice in words, hour rows with the times on the now line, and the jet lag bracket.
 */
object TwoStrips {
    /** Where now sits along the window when captured. */
    const val LeadFraction = 0.3f

    /**
     * Lays the strips out in a [widthDp] × [heightDp] box.
     *
     * @param placeOptions names for where you are, longest first: the first one whose label fits is used. Defaults
     *   to the state's place, then the zone's city.
     * @param textGrowth extra scale for the text under large font sizes (capped so the bars keep their room).
     * @param labelText the smallest size for every word that isn't one of the two times (the bars' labels, the
     *   advice's name, the jet lag), in dp, already grown with the font scale (a widget's). A bar label its bar can't
     *   hold at that size is dropped rather than shrunk; the times beside the bars still say which is which.
     */
    fun spec(
        state: DialState,
        palette: DialPalette,
        labels: DialLabels,
        widthDp: Float,
        heightDp: Float,
        mode: BodyRingMode = BodyRingMode.Simple,
        measurer: DialTextMeasurer = ApproxTextMeasurer,
        textGrowth: Float = 1f,
        placeOptions: List<String> = defaultPlaceOptions(state),
        labelText: Float = 0f,
    ): DialSpec {
        val level = DetailLevel.forStrip(widthDp, heightDp)
        val b = Builder(state, palette, labels, measurer, widthDp, heightDp, mode, textGrowth.coerceIn(1f, 1.15f), placeOptions, labelText)
        when (level) {
            DetailLevel.Glance -> b.glance()
            DetailLevel.Simple -> b.simple()
            DetailLevel.Full -> b.full()
        }
        return DialSpec(
            level, widthDp, heightDp, widthDp / 2f, heightDp / 2f, radius = 0f, hubRadius = 0f, ops = b.ops.toList(),
            nowMinute = b.now, axis = b.axis,
        )
    }

    /** The state's place (a trip stop), then the zone's city. */
    fun defaultPlaceOptions(state: DialState): List<String> =
        listOfNotNull(state.placeName?.takeIf { it.isNotBlank() }, ZoneLabels.city(state.displayZoneId)).distinct()

    /** The first local minute of the window for now at [nowMinute]: [LeadFraction] of a day earlier, on the hour. */
    fun windowStart(nowMinute: Float): Float =
        (floor((nowMinute - LeadFraction * DialGeometry.MinutesPerDay) / 60f) * 60f).mod(DialGeometry.MinutesPerDay)

    private class Builder(
        val state: DialState,
        val p: DialPalette,
        val labels: DialLabels,
        val measurer: DialTextMeasurer,
        val w: Float,
        val h: Float,
        val mode: BodyRingMode,
        val grow: Float,
        val placeOptions: List<String>,
        val labelText: Float,
    ) {
        val ops = mutableListOf<DialOp>()
        val now = state.localMinute
        val ahead = state.bodyAheadMinutes
        val aligned = DefaultDialLabels.isInSync(ahead)
        val localNight = BodySky.localNight(state)
        val bodyNightInBody = BodySky.nightInBody(state, mode)
        val bodyNight = BodySky.nightInLocal(state, mode)
        val focus = state.focusAt(now)
        lateinit var axis: TimeAxis

        // region Levels

        /**
         * Narrow: the local time, the two bars with the now line, the body time. In a wide, short box (a 2×1) the
         * two times share a row above the bars. Text shrinks (to [GlanceMinScale]), then the body time drops, then
         * the local time.
         */
        fun glance() {
            if (labelText > 0f) return glanceFloored()
            val pad = 2f
            val barH = (h * 0.13f).coerceIn(4f, 8f)
            val gap = barH * 0.45f
            val bars = 2f * barH + gap
            var local = TextSpec(17f * grow, weight = 520)
            var body = TextSpec(11.5f * grow, weight = 560, slanted = true)
            val bodyText = if (aligned) labels.inSync() else labels.fullTime(state.bodyMinute)
            val localText = labels.fullTime(now)
            val room = h - bars - 2f * pad
            val row = w >= GlanceRowAspect * h
            if (row) {
                // Width first: both times on one line.
                val natural = measurer.width(localText, local) + RowGap + measurer.width(bodyText, body)
                val sw = if (natural > w - 2f * pad) (w - 2f * pad) / natural else 1f
                val sh = ((room - TextGap) / lineH(local)).coerceAtMost(1f)
                val s = min(sw, sh)
                if (s < GlanceMinScale) return glanceStacked(localText, bodyText, local, body, barH, gap, bars, pad, room)
                local = local.copy(size = local.size * s)
                body = body.copy(size = body.size * s)
                var y = (h - (lineH(local) + TextGap + bars)) / 2f
                val cy = y + lineH(local) / 2f
                ops += DialOp.Text(localText, pad, cy, local, p.ink, h = HAlign.Start, part = DialPart.Readout, live = LiveTime(LiveClock.Local, marker = true))
                bodyText(bodyText, w - pad, cy + (local.size - body.size) * 0.32f, body, HAlign.End)
                y += lineH(local) + TextGap
                axis = TimeAxis(pad + barH / 2f, w - pad - barH / 2f, windowStart(now), DialGeometry.MinutesPerDay)
                bars(y, barH, gap, pad)
                nowLine(y - 1f, y + bars + 1f, width = 1.6f)
                return
            }
            glanceStacked(localText, bodyText, local, body, barH, gap, bars, pad, room)
        }

        fun glanceStacked(
            localText: String, bodyText: String, localSpec: TextSpec, bodySpec: TextSpec,
            barH: Float, gap: Float, bars: Float, pad: Float, room: Float,
        ) {
            // Width first: each time fits the box.
            var local = fitWidth(localText, localSpec, w - 2f * pad)
            var body = fitWidth(bodyText, bodySpec, w - 2f * pad)
            // Then height: shrink both together, then drop the body time, then the local time.
            fun need(withBody: Boolean, s: Float) = lineH(local) * s + if (withBody) lineH(body) * s + 2f * TextGap else TextGap
            var showBody = true
            var showLocal = true
            var s = 1f
            if (need(true, 1f) > room) {
                s = ((room - 2f * TextGap) / (need(true, 1f) - 2f * TextGap)).coerceAtLeast(GlanceMinScale)
                if (need(true, s) > room) {
                    showBody = false
                    s = ((room - TextGap) / (need(false, 1f) - TextGap)).coerceIn(GlanceMinScaleAlone, 1f)
                    if (need(false, s) > room) showLocal = false
                }
            }
            local = local.copy(size = local.size * s)
            body = body.copy(size = body.size * s)
            val stack = bars + (if (showLocal) lineH(local) + TextGap else 0f) + (if (showBody) lineH(body) + TextGap else 0f)
            var y = (h - stack) / 2f
            if (showLocal) {
                ops += DialOp.Text(localText, w / 2f, y + lineH(local) / 2f, local, p.ink, part = DialPart.Readout, live = LiveTime(LiveClock.Local, marker = true))
                y += lineH(local) + TextGap
            }
            axis = TimeAxis(pad + barH / 2f, w - pad - barH / 2f, windowStart(now), DialGeometry.MinutesPerDay)
            bars(y, barH, gap, pad)
            nowLine(y - 1f, y + bars + 1f, width = 1.6f)
            y += bars + TextGap
            if (showBody) bodyText(bodyText, w / 2f, y + lineH(body) / 2f, body, HAlign.Center)
        }

        /**
         * Glance with a host's label floor: both times at least [labelText], shrinking no further. When they don't
         * fit, "in sync" goes first, then the body time's AM/PM, then the local time's; the bars thin down to make
         * room. The body time itself always stays, so the bars can be told apart.
         */
        fun glanceFloored() {
            val pad = 1.5f
            val designedBar = (h * 0.13f).coerceIn(MinGlanceBar, 8f)
            val localSpec = TextSpec(17f * grow, weight = 520)
            val bodySpec = TextSpec(11.5f * grow, weight = 560, slanted = true)
            val localFull = labels.fullTime(now)
            val localShort = labels.time(now)
            val bodyFull = if (aligned) labels.inSync() else labels.fullTime(state.bodyMinute)
            val bodyShort = if (aligned) null else labels.time(state.bodyMinute)
            // Longest first: drop "in sync" (or the body's AM/PM), then the local AM/PM.
            val options = buildList {
                add(localFull to bodyFull)
                add(localFull to bodyShort)
                add(localShort to bodyShort)
            }.distinct()
            fun barsFor(barH: Float) = 2f * barH + barH * 0.45f
            for ((localText, bodyText) in options) for (row in listOf(true, false)) {
                if (row && (w < GlanceRowAspect * h || bodyText == null)) continue
                val fit = glanceFit(localText, bodyText, localSpec, bodySpec, row, pad) ?: continue
                val (l, b, textH) = fit
                val barH = ((h - 2f * pad - textH) / 2.45f).coerceIn(MinGlanceBar, designedBar)
                if (textH + barsFor(barH) > h - 2f * pad + FitSlack) continue
                return drawGlance(localText, bodyText, l, b, row, barH, pad, localText == localFull)
            }
            // Nothing fits: the shortest times at the floor over the thinnest bars.
            val l = localSpec.copy(size = labelText)
            val b = bodySpec.copy(size = labelText)
            drawGlance(localShort, bodyShort, l, b, row = false, barH = MinGlanceBar, pad = pad, localMarker = false)
        }

        /** Sizes for the two times in a glance (at most designed, at least the floor) and the text's height, or null. */
        fun glanceFit(localText: String, bodyText: String?, localSpec: TextSpec, bodySpec: TextSpec, row: Boolean, pad: Float): Triple<TextSpec, TextSpec, Float>? {
            val room = w - 2f * pad
            fun at(spec: TextSpec, s: Float) = spec.copy(size = max(spec.size * s, labelText))
            var s = 1f
            while (s > 0.3f) {
                val l = at(localSpec, s)
                val b = at(bodySpec, s)
                val lw = measurer.width(localText, l)
                val bw = bodyText?.let { measurer.width(it, b) } ?: 0f
                val wide = if (row) lw + RowGap + bw else max(lw, bw)
                val textH = if (row) tightH(l) + TightGap else tightH(l) + TightGap + (if (bodyText != null) tightH(b) + TightGap else 0f)
                val tallest = h - 2f * pad - barsMin()
                if (wide + FitSlack <= room && textH <= tallest) return Triple(l, b, textH)
                if (l.size <= labelText && b.size <= labelText) return null
                s -= 0.02f
            }
            return null
        }

        fun barsMin(): Float = 2.45f * MinGlanceBar

        /** A glance's line: its text size and no leading (the times are digits, with no descenders to clear). */
        fun tightH(spec: TextSpec): Float = spec.size

        fun drawGlance(localText: String, bodyText: String?, l: TextSpec, b: TextSpec, row: Boolean, barH: Float, pad: Float, localMarker: Boolean) {
            val gap = barH * 0.45f
            val bars = 2f * barH + gap
            val localLive = LiveTime(LiveClock.Local, marker = localMarker)
            val bodyMarker = bodyText != null && !aligned && bodyText != labels.time(state.bodyMinute)
            fun body(x: Float, y: Float, h: HAlign) {
                if (bodyText == null) return
                val live = if (aligned) null else LiveTime(LiveClock.Body, marker = bodyMarker)
                ops += DialOp.Text(bodyText, x, y, b, p.body, h = h, part = DialPart.Readout, live = live)
            }
            if (row) {
                var y = (this.h - (tightH(l) + TightGap + bars)) / 2f
                val cy = y + tightH(l) / 2f
                ops += DialOp.Text(localText, pad, cy, l, p.ink, h = HAlign.Start, part = DialPart.Readout, live = localLive)
                body(w - pad, cy + (l.size - b.size) * 0.32f, HAlign.End)
                y += tightH(l) + TightGap
                axis = TimeAxis(pad + barH / 2f, w - pad - barH / 2f, windowStart(now), DialGeometry.MinutesPerDay)
                bars(y, barH, gap, pad)
                nowLine(y - 1f, y + bars + 1f, width = 1.6f)
                return
            }
            val stack = bars + tightH(l) + TightGap + (if (bodyText != null) tightH(b) + TightGap else 0f)
            var y = (this.h - stack) / 2f
            ops += DialOp.Text(localText, w / 2f, y + tightH(l) / 2f, l, p.ink, part = DialPart.Readout, live = localLive)
            y += tightH(l) + TightGap
            axis = TimeAxis(pad + barH / 2f, w - pad - barH / 2f, windowStart(now), DialGeometry.MinutesPerDay)
            bars(y, barH, gap, pad)
            nowLine(y - 1f, y + bars + 1f, width = 1.6f)
            y += bars + TightGap
            body(w / 2f, y + tightH(b) / 2f, HAlign.Center)
        }

        /**
         * The times above the bars when there is height for them, else beside them (a 4×1 row); the advice in focus
         * above the bars when there is room; the bars carry their night labels.
         */
        fun simple() {
            val pad = 2f
            var barH = 14f
            val gap = 4f
            val localSpec = TextSpec(18f * grow, weight = 480)
            val bodySpec = TextSpec(12f * grow, weight = 540, slanted = true)
            val header = lineH(localSpec)
            val advice = 13f
            val minBars = 2f * barH + gap
            val top: Float
            val left: Float
            if (h - 2f * pad >= minBars + header + TextGap && headerFits(localSpec, bodySpec, pad)) {
                // Times above: local, body, and the offset on the right.
                top = pad + header + TextGap
                left = pad
                headerRow(pad + header / 2f, localSpec, bodySpec, pad)
            } else {
                // A short row: the times stacked on the left, the bars beside them.
                barH = min(barH, ((h - 2f * pad - gap) / 2f).coerceAtLeast(5f))
                left = sideTimes(localSpec, bodySpec, pad) + 8f
                top = (h - (2f * barH + gap)) / 2f
            }
            val barsH = 2f * barH + gap
            val withAdvice = top + barsH + advice + 3f <= h - pad
            val barsTop = if (withAdvice) top + advice + 3f else top
            axis = TimeAxis(left + barH / 2f, w - pad - barH / 2f, windowStart(now), DialGeometry.MinutesPerDay)
            if (withAdvice) adviceRow(top, advice, words = false)
            bars(barsTop, barH, gap, pad, labelsAt = label(TextSpec(min(8.5f * grow, barH * 0.62f), weight = 650)))
            nowLine(if (withAdvice) top - 1f else barsTop - 2f, barsTop + barsH + 2f, width = 2f)
        }

        /** Wide and tall: the advice in words, the bars with hour rows, the times on the now line, the bracket. */
        fun full() {
            val pad = 4f
            val barH = 22f
            val hours = label(TextSpec(9.5f * grow, weight = 500))
            val bodyHours = hours.copy(slanted = true)
            val pill = TextSpec(10.5f * grow, weight = 650)
            val rowH = max(lineH(hours), lineH(pill) + 4f) + 4f
            val say = TextSpec(11f * grow, weight = 650, tabular = false)
            val sayH = lineH(say)
            val advice = 16f
            val total = sayH + 2f + advice + 6f + barH + rowH + barH + rowH
            val y0 = max(pad, (h - total) / 2f)
            axis = TimeAxis(pad + 12f, w - pad - 12f, windowStart(now), DialGeometry.MinutesPerDay)
            adviceRow(y0 + sayH + 2f, advice, words = true, sayY = y0 + sayH / 2f, say = say)
            val localTop = y0 + sayH + 2f + advice + 6f
            bar(localTop, barH, localColors(), DialPart.LocalSky)
            barLabels(localTop, barH, local = true, spec = label(TextSpec(10f * grow, weight = 650)), withDay = true)
            val bodyTop = localTop + barH + rowH
            bar(bodyTop, barH, bodyColors(), DialPart.BodySky)
            barLabels(bodyTop, barH, local = false, spec = label(TextSpec(10f * grow, weight = 650, slanted = true)), withDay = true)
            nowLine(localTop - 4f, bodyTop + barH + rowH - 2f, width = 2.2f)
            val nowX = axis.x(now)
            // The times ride on the now line; hour labels steer clear of them.
            val localPill = pillAt(nowX, localTop + barH + rowH / 2f, labels.time(now), pill, p.ink, p.face, LiveTime(LiveClock.Local))
            val bodyPill = pillAt(
                nowX, bodyTop + barH + rowH / 2f, labels.time(state.bodyMinute), pill.copy(slanted = true), p.body, p.face,
                LiveTime(LiveClock.Body),
            )
            hourRow(localTop + barH + rowH / 2f, hours, bodyClock = false, avoid = localPill)
            hourRow(bodyTop + barH + rowH / 2f, bodyHours, bodyClock = true, avoid = bodyPill)
            bracket(localTop + barH, rowH, nowX, localPill)
        }

        // endregion
        // region Marks

        fun bars(top: Float, barH: Float, gap: Float, pad: Float, labelsAt: TextSpec? = null) {
            bar(top, barH, localColors(), DialPart.LocalSky)
            bar(top + barH + gap, barH, bodyColors(), DialPart.BodySky)
            if (labelsAt != null) {
                barLabels(top, barH, local = true, spec = labelsAt, withDay = false)
                barLabels(top + barH + gap, barH, local = false, spec = labelsAt.copy(slanted = true), withDay = false)
            }
        }

        fun bar(top: Float, barH: Float, colors: List<Argb>, part: DialPart) {
            val r = barH / 2f
            ops += DialOp.SkyBar(axis.left - r, top, axis.right + r, top + barH, r, colors, part)
            if (p.dark) ops += DialOp.Rect(axis.left - r, top, axis.right + r, top + barH, r, p.hairline.withAlpha(0.6f), stroke = 0.75f, part = part)
        }

        /** Stops across the bar (one per 2 dp, 24–144), sampled at their local minute. */
        fun stops(): List<Float> {
            val n = ((axis.right - axis.left) / 2f).toInt().coerceIn(24, 144)
            return List(n) { i -> (axis.startMinute + (i + 0.5f) * axis.spanMinutes / n).mod(DialGeometry.MinutesPerDay) }
        }

        fun localColors(): List<Argb> =
            stops().map { m -> p.sky(m, sunrise = localNight.end, sunset = localNight.start, daylight = localNight.daylight) }

        /** The body's sky at each local minute: its own sun times, read on the body clock. */
        fun bodyColors(): List<Argb> = stops().map { m ->
            p.sky((m + ahead).mod(DialGeometry.MinutesPerDay), sunrise = bodyNightInBody.end, sunset = bodyNightInBody.start, daylight = bodyNightInBody.daylight)
        }

        /**
         * The bar's night (and on Full its day) named on the bar: the longest visible run, kept clear of the now line,
         * with the longest name that fits; dropped when none does, or when the bar is too thin for the text.
         */
        fun barLabels(top: Float, barH: Float, local: Boolean, spec: TextSpec, withDay: Boolean) {
            if (spec.size * CapHeight > barH * MaxFill) return
            val night = if (local) localNight else bodyNight
            val nightNames = if (local) placeOptions.map { labels.placeNight(it) } + placeOptions else listOf(labels.bodyNight(), labels.body())
            val dayNames = if (local) placeOptions.map { labels.placeDay(it) } else listOf(labels.bodyDay())
            val cy = top + barH / 2f
            if (night.daylight != Daylight.AlwaysUp) {
                runs(night.start, night.lengthMinutes).maxByOrNull { it.second - it.first }?.let { (x0, x1) ->
                    label(nightNames, x0, x1, cy, spec, p.sky.onNight)
                }
            }
            if (withDay && night.daylight != Daylight.AlwaysDown) {
                runs(night.end, DialGeometry.MinutesPerDay - night.lengthMinutes).maxByOrNull { it.second - it.first }?.let { (x0, x1) ->
                    label(dayNames, x0, x1, cy, spec, p.sky.onDay)
                }
            }
        }

        /** The visible x ranges of the span from local [start] lasting [length] minutes (two when the window cuts it). */
        fun runs(start: Float, length: Float): List<Pair<Float, Float>> {
            val from = (start - axis.startMinute).mod(DialGeometry.MinutesPerDay)
            val ranges = listOf(from to from + length, from - DialGeometry.MinutesPerDay to from - DialGeometry.MinutesPerDay + length)
            return ranges.mapNotNull { (a, b) ->
                val lo = max(a, 0f)
                val hi = min(b, axis.spanMinutes)
                if (hi - lo > 1f) (axis.left + lo * axis.dpPerMinute) to (axis.left + hi * axis.dpPerMinute) else null
            }
        }

        fun label(names: List<String>, x0: Float, x1: Float, cy: Float, spec: TextSpec, color: Argb) {
            val nowX = axis.x(now)
            val inset = 6f
            for (name in names) {
                val tw = measurer.width(name, spec)
                if (tw > x1 - x0 - 2f * inset) continue
                val clear = 4f
                val candidates = listOf((x0 + x1) / 2f, nowX - clear - tw / 2f, nowX + clear + tw / 2f)
                val at = candidates.firstOrNull { c ->
                    c - tw / 2f >= x0 + inset && c + tw / 2f <= x1 - inset && (c + tw / 2f < nowX - clear || c - tw / 2f > nowX + clear)
                } ?: continue
                ops += DialOp.Text(name, at, cy, spec, color, part = DialPart.RingLabel)
                return
            }
        }

        /** The now line across the strips, with a face-coloured halo so it separates from both skies. */
        fun nowLine(y0: Float, y1: Float, width: Float) {
            val x = axis.x(now)
            ops += DialOp.Line(x, y0, x, y1, p.face, width * 2.2f, part = DialPart.Needle)
            ops += DialOp.Line(x, y0, x, y1, p.ink, width, part = DialPart.Needle)
        }

        /** Whether both times fit on the header line (at 0.7 of their size, or the label floor), without the jet lag. */
        fun headerFits(localSpec: TextSpec, bodySpec: TextSpec, pad: Float): Boolean {
            val localText = labels.fullTime(now)
            val bodyText = if (aligned) labels.inSync() else labels.bodyTime(labels.fullTime(state.bodyMinute))
            fun at(spec: TextSpec) = spec.copy(size = max(spec.size * 0.7f, labelText))
            return measurer.width(localText, at(localSpec)) + 6f + measurer.width(bodyText, at(bodySpec)) + FitSlack <= w - 2f * pad - 8f
        }

        /** Local time, body time and the offset on one line above the bars. */
        fun headerRow(cy: Float, localSpec: TextSpec, bodySpec: TextSpec, pad: Float) {
            val localText = labels.fullTime(now)
            val bodyText = if (aligned) labels.inSync() else labels.bodyTime(labels.fullTime(state.bodyMinute))
            val offSpec = label(TextSpec(10f * grow, weight = 650, slanted = true, tabular = false))
            fun at(spec: TextSpec, s: Float) = spec.copy(size = max(spec.size * s, labelText))
            fun widthAt(s: Float) = measurer.width(localText, at(localSpec, s)) + 6f + measurer.width(bodyText, at(bodySpec, s))
            fun roomWith(offW: Float) = w - 2f * pad - offW - 8f
            // The largest scale, down to 0.7, at which both times fit (a little under the room: measured widths
            // don't scale exactly with the size, as glyphs snap to pixels).
            fun scaleFor(room: Float): Float? {
                if (widthAt(1f) + FitSlack <= room) return 1f
                var s = 1f
                while (s > 0.7f) {
                    s = max(0.7f, s - 0.02f)
                    if (widthAt(s) + FitSlack <= room) return s
                }
                return null
            }
            // The jet lag goes before the body time does: the two times are what tell the bars apart.
            val offset = labels.offset(ahead).takeUnless { aligned }
                ?.takeIf { scaleFor(roomWith(measurer.width(it, offSpec) + 16f)) != null }
            val offW = offset?.let { measurer.width(it, offSpec) + 16f } ?: 0f
            val room = roomWith(offW)
            val s = scaleFor(room) ?: 0.7f
            val l = at(localSpec, s)
            val b = at(bodySpec, s)
            val lw = measurer.width(localText, l)
            ops += DialOp.Text(localText, pad, cy, l, p.ink, h = HAlign.Start, part = DialPart.Readout, live = LiveTime(LiveClock.Local, marker = true))
            if (offset != null || lw + 6f + measurer.width(bodyText, b) <= room) {
                bodyText(bodyText, pad + lw + 6f, cy + (l.size - b.size) * 0.32f, b, HAlign.Start, prefixed = true)
            }
            if (offset != null) {
                val x1 = w - pad
                val r = max(9f, lineH(offSpec) / 2f + 1f)
                ops += DialOp.Rect(x1 - offW, cy - r, x1, cy + r, r, p.bodyContainer, part = DialPart.Offset)
                ops += DialOp.Text(offset, x1 - offW / 2f, cy, offSpec, p.onBodyContainer, part = DialPart.Offset)
            }
        }

        /** Local time over body time at the left of a short row. Returns the column's width. */
        fun sideTimes(localSpec: TextSpec, bodySpec: TextSpec, pad: Float): Float {
            val maxCol = w * 0.42f
            var localText = labels.fullTime(now)
            var bodyText: String? = if (aligned) labels.inSync() else labels.fullTime(state.bodyMinute)
            fun floored(spec: TextSpec) = if (spec.size < labelText) spec.copy(size = labelText) else spec
            // At the label floor, a time too wide for the column loses its AM/PM ("in sync" goes instead).
            if (labelText > 0f && measurer.width(localText, floored(localSpec)) > maxCol) localText = labels.time(now)
            if (labelText > 0f && measurer.width(bodyText!!, floored(bodySpec)) > maxCol) bodyText = if (aligned) null else labels.time(state.bodyMinute)
            val l = floored(fitWidth(localText, localSpec, maxCol))
            val b = floored(fitWidth(bodyText ?: localText, bodySpec, maxCol))
            fun total(ls: TextSpec, bs: TextSpec) = lineH(ls) + if (bodyText != null) lineH(bs) + TextGap else 0f
            // Shrink both to the row's height, no further than the floor; "in sync" goes when that's not enough.
            if (labelText > 0f && aligned && total(floored(l.copy(size = 0f)), floored(b.copy(size = 0f))) > h - 2f * pad) bodyText = null
            val s = ((h - 2f * pad) / total(l, b)).coerceAtMost(1f)
            val ls = floored(l.copy(size = l.size * s))
            val bs = floored(b.copy(size = b.size * s))
            if (bodyText == null) {
                val y = (h - lineH(ls)) / 2f
                ops += DialOp.Text(localText, pad, y + lineH(ls) / 2f, ls, p.ink, h = HAlign.Start, part = DialPart.Readout, live = LiveTime(LiveClock.Local, marker = localText != labels.time(now)))
                return pad + measurer.width(localText, ls)
            }
            val y = (h - (lineH(ls) + lineH(bs) + TextGap)) / 2f
            ops += DialOp.Text(localText, pad, y + lineH(ls) / 2f, ls, p.ink, h = HAlign.Start, part = DialPart.Readout, live = LiveTime(LiveClock.Local, marker = localText != labels.time(now)))
            bodyText(bodyText, pad, y + lineH(ls) + TextGap + lineH(bs) / 2f, bs, HAlign.Start, marker = bodyText != labels.time(state.bodyMinute))
            return pad + max(measurer.width(localText, ls), measurer.width(bodyText, bs))
        }

        /** The body time (live), or "in sync" once adapted. [prefixed]: "08:20 body" rather than "08:20". */
        fun bodyText(text: String, x: Float, y: Float, spec: TextSpec, h: HAlign, prefixed: Boolean = false, marker: Boolean = true) {
            val live = when {
                aligned -> null
                prefixed -> liveBodyTime(labels).copy(marker = marker)
                else -> LiveTime(LiveClock.Body, marker = marker)
            }
            ops += DialOp.Text(text, x, y, spec, p.body, h = h, part = DialPart.Readout, live = live)
        }

        /**
         * The advice in focus on the axis: its block as a capsule (hatched for avoid light, the part already past
         * washed back) with its glyph at the start. On Full the next one's glyph follows and the words ride above;
         * on Simple its name sits beside the capsule (never a glyph alone), and the row goes when the name doesn't fit.
         */
        fun adviceRow(top: Float, rowH: Float, words: Boolean, sayY: Float = 0f, say: TextSpec? = null) {
            val current = focus.current
            val next = focus.next
            val shown = current ?: next ?: return
            val cy = top + rowH / 2f
            val discR = rowH / 2f
            // A block the window's seam cuts shows as two pieces; the glyph starts the one in focus (the piece under
            // the now line while it's on).
            val pieces = if (shown.sweepMinutes > 0f) runs(shown.startMinute, shown.sweepMinutes) else emptyList()
            val nowX = axis.x(now)
            val main = pieces.firstOrNull { current != null && nowX >= it.first && nowX <= it.second } ?: pieces.firstOrNull()
            val sx = clampX(main?.first ?: axis.x(shown.startMinute).takeIf { inWindow(shown.startMinute) } ?: axis.left)
            val rowStart = ops.size
            val capsuleEnd = main?.let { capsule(shown, pieces, it, cy, rowH * 0.7f, washPast = current != null) }
            glyph(shown.type, sx, cy, discR)
            if (!words) {
                if (!nameBeside(shown.type, sx - discR, max(sx + discR, capsuleEnd ?: 0f), cy)) {
                    while (ops.size > rowStart) ops.removeAt(ops.lastIndex)
                }
                return
            }
            if (say == null) return
            val label = labels.advice(shown.type)
            val text = when {
                current != null && current.sweepMinutes > 0f -> labels.until(label, labels.fullTime(current.narratedEndMinute))
                else -> labels.at(label, labels.fullTime(shown.startMinute))
            }
            val tw = measurer.width(text, say)
            val thenSpec = say.copy(weight = 550)
            // What follows, when the line has room for it; the line slides left (from under its glyph) to make room.
            val thenText = if (current != null && next != null) {
                val nextLabel = labels.advice(next.type)
                if (startsBeforeEnd(next, current)) labels.at(nextLabel, labels.fullTime(next.startMinute)) else labels.then(nextLabel)
            } else null
            val thenW = thenText?.let { 8f + measurer.width(it, thenSpec) } ?: 0f
            val withThen = thenText != null && tw + thenW <= w - 8f
            val lineW = if (withThen) tw + thenW else tw
            val x = (sx - discR).coerceIn(4f, max(4f, w - 4f - lineW))
            ops += DialOp.Text(text, x, sayY, say, p.ink, h = HAlign.Start, part = DialPart.Narration)
            if (!withThen) return
            ops += DialOp.Text(thenText!!, x + tw + 8f, sayY, thenSpec, p.inkMuted, h = HAlign.Start, part = DialPart.Narration)
            // The next one's glyph, named by the line above.
            if (next != null && current != null && inWindow(next.startMinute)) {
                val nx = axis.x(next.startMinute)
                val endX = axis.x(current.endMinute)
                glyph(next.type, if (kotlin.math.abs(nx - endX) < discR) nx + discR else nx, cy, discR * 0.8f)
            }
        }

        /**
         * The advice's name after its mark (from [x0] to [x1]), or before it when there's no room after; kept off the
         * now line. False when it fits neither side.
         */
        fun nameBeside(type: AdviceType, x0: Float, x1: Float, cy: Float): Boolean {
            val name = labels.advice(type)
            val spec = label(TextSpec(10.5f * grow, weight = 650, tabular = false))
            val tw = measurer.width(name, spec)
            val nowX = axis.x(now)
            val gap = 4f
            fun clear(a: Float) = a > nowX + gap || a + tw < nowX - gap
            val after = x1 + gap
            val before = x0 - gap - tw
            val at = when {
                after + tw <= w - 2f && clear(after) -> after
                before >= 2f && clear(before) -> before
                else -> return false
            }
            ops += DialOp.Text(name, at, cy, spec, p.ink, h = HAlign.Start, part = DialPart.Advice)
            return true
        }

        /** The block as a capsule per visible piece along the axis. Returns where the [main] piece ends. */
        fun capsule(a: DialArc, pieces: List<Pair<Float, Float>>, main: Pair<Float, Float>, cy: Float, height: Float, washPast: Boolean): Float {
            pieces.forEach { (x0, x1) -> capsulePiece(a, x0, x1, cy, height, washPast) }
            return main.second + height / 2f
        }

        fun capsulePiece(a: DialArc, x0: Float, x1: Float, cy: Float, height: Float, washPast: Boolean) {
            val r = height / 2f
            val top = cy - r
            val bottom = cy + r
            if (!p.dark) ops += DialOp.Rect(x0 - r - 0.8f, top - 0.8f, x1 + r + 0.8f, bottom + 0.8f, r + 0.8f, p.advice(a.type).color.withAlpha(0.85f), part = DialPart.Advice)
            ops += DialOp.Rect(x0 - r, top, x1 + r, bottom, r, p.adviceFill(a.type), part = DialPart.Advice)
            if (a.type == AdviceType.AvoidLight) {
                val ink = p.adviceInk(a.type).withAlpha(0.28f)
                val step = height * 0.55f
                var x = x0
                while (x + height * 0.6f < x1 + r) {
                    ops += DialOp.Line(x, bottom - height * 0.12f, x + height * 0.6f, top + height * 0.12f, ink, height * 0.11f, roundCap = false, part = DialPart.Advice)
                    x += step
                }
            }
            if (washPast) {
                val nowX = axis.x(now)
                if (nowX > x0 && nowX < x1) {
                    ops += DialOp.Rect(x0 - r - 1f, top - 1f, nowX, bottom + 1f, 0f, p.face.withAlpha(0.42f), part = DialPart.AdvicePast)
                }
            }
        }

        fun glyph(type: AdviceType, x: Float, cy: Float, discR: Float) {
            val role = p.advice(type)
            ops += DialOp.Glyph(type, x, cy, discR * 1.25f, role.onColor, role.color, discR, ring = p.face)
        }

        /** A time pill centred on the now line (it rides with it). Returns its x range, for the hour row to avoid. */
        fun pillAt(x: Float, cy: Float, text: String, spec: TextSpec, bg: Argb, fg: Argb, live: LiveTime): ClosedFloatingPointRange<Float> {
            val pw = measurer.width(widestTime(), spec) + 10f
            val ph = lineH(spec) + 2f
            ops += DialOp.Rect(x - pw / 2f, cy - ph / 2f, x + pw / 2f, cy + ph / 2f, ph / 2f, bg, part = DialPart.Needle)
            ops += DialOp.Text(text, x, cy, spec, fg, part = DialPart.Needle, live = live)
            return (x - pw / 2f)..(x + pw / 2f)
        }

        /** Every third hour along the row, on the local or the body clock, kept clear of the time pill. */
        fun hourRow(cy: Float, spec: TextSpec, bodyClock: Boolean, avoid: ClosedFloatingPointRange<Float>) {
            val color = if (bodyClock) p.body else p.inkMuted
            for (hour in 0 until 24 step 3) {
                val localMinute = if (bodyClock) (hour * 60f - ahead).mod(DialGeometry.MinutesPerDay) else hour * 60f
                if (!inWindow(localMinute)) continue
                val x = axis.x(localMinute)
                val text = labels.numeral(hour)
                val tw = measurer.width(text, spec)
                if (x - tw / 2f < 2f || x + tw / 2f > w - 2f) continue
                if (x + tw / 2f + 3f > avoid.start && x - tw / 2f - 3f < avoid.endInclusive) continue
                ops += DialOp.Text(text, x, cy, spec, color, part = DialPart.Numeral)
            }
        }

        /** "7 h later" between the two nights' starts, under the local bar; "in sync" once adapted. */
        fun bracket(top: Float, rowH: Float, nowX: Float, avoid: ClosedFloatingPointRange<Float>) {
            val spec = label(TextSpec(9.5f * grow, weight = 650, slanted = true, tabular = false))
            if (aligned) return
            val ls = xIfVisible(localNight.start) ?: return
            val bs = xIfVisible(bodyNight.start) ?: return
            val (x0, x1) = if (ls < bs) ls to bs else bs to ls
            val text = labels.offset(ahead)
            val tw = measurer.width(text, spec) + 12f
            if (x1 - x0 < tw + 8f) return
            val y = top + rowH * 0.78f
            val color = p.body.withAlpha(0.7f)
            ops += DialOp.Line(x0, y, x1, y, color, 1f, part = DialPart.Offset)
            ops += DialOp.Line(x0, top + 2f, x0, y, color, 1f, part = DialPart.Offset)
            ops += DialOp.Line(x1, y, x1, top + rowH + 1f, color, 1f, part = DialPart.Offset)
            val cx = (x0 + x1) / 2f
            if (cx + tw / 2f > avoid.start && cx - tw / 2f < avoid.endInclusive) return
            val ph = lineH(spec) + 1f
            ops += DialOp.Rect(cx - tw / 2f, y - ph / 2f, cx + tw / 2f, y + ph / 2f, ph / 2f, p.bodyContainer, part = DialPart.Offset)
            ops += DialOp.Text(text, cx, y, spec, p.onBodyContainer, part = DialPart.Offset)
        }

        // endregion

        /** Where local [minute] sits on the axis, or null when it's outside the window. */
        fun xIfVisible(minute: Float): Float? = if (inWindow(minute)) axis.x(minute) else null

        fun inWindow(minute: Float): Boolean = (minute - axis.startMinute).mod(DialGeometry.MinutesPerDay) <= axis.spanMinutes

        fun clampX(x: Float): Float = x.coerceIn(axis.left, axis.right)

        /** The widest a live time can get until the next capture ("00:00" / "12:00"), for its pill. */
        fun widestTime(): String = labels.time(12 * 60f).let { t -> if (t.length >= 5) t else "12:00" }

        /** A word's [spec], at least the host's [labelText]. */
        fun label(spec: TextSpec): TextSpec = if (spec.size < labelText) spec.copy(size = labelText) else spec

        fun fitWidth(text: String, spec: TextSpec, maxWidth: Float): TextSpec {
            val tw = measurer.width(text, spec)
            return if (tw > maxWidth && tw > 0f) spec.copy(size = spec.size * maxWidth / tw) else spec
        }

        fun lineH(spec: TextSpec): Float = spec.size * LineHeight
    }

    /** Line height per dp of text size (system fonts run about 1.17). */
    private const val LineHeight = 1.2f

    private const val TextGap = 2f

    /** The gap between a floored glance's times and its bars. */
    private const val TightGap = 1f

    /** The thinnest a floored glance's bars get to make room for its times. */
    private const val MinGlanceBar = 3.5f

    /** Room left over when text is fitted to a width, for measuring that doesn't scale exactly. */
    private const val FitSlack = 1f

    /** Glance puts both times on one row when the box is at least this many times wider than tall. */
    private const val GlanceRowAspect = 1.8f

    private const val RowGap = 8f

    /** How far Glance shrinks its times before dropping the body time, and the local time when alone. */
    private const val GlanceMinScale = 0.7f
    private const val GlanceMinScaleAlone = 0.6f
}
