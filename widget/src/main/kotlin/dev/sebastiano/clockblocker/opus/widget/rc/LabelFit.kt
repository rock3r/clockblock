package dev.sebastiano.clockblocker.opus.widget.rc

import android.content.Context
import dev.sebastiano.clockblocker.opus.widget.R
import dev.sebastiano.clockblocker.opus.widget.draw.GlyphKind
import dev.sebastiano.clockblocker.opus.widget.text.Fitted
import dev.sebastiano.clockblocker.opus.widget.text.TextFit
import dev.sebastiano.clockblocker.opus.widget.text.UpcomingText
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTexts
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Geometry shared by the layouts in WidgetRemoteContent and the fitting below: change it in one place only.

/** Gap between the stacked lines of a text block. */
internal const val LINE_GAP_DP = 1

/** One-row Next up (2×1, 4×1, the 4×2 ribbon's top row): glyph, then the text column. */
internal const val ROW_START_DP = 12
internal const val ROW_V_PAD_DP = 8
internal const val ROW_GLYPH_DP = 40
internal const val ROW_GLYPH_GAP_DP = 12
internal const val ROW_END_DP = 14
internal const val ROW_END_BESIDE_DONE_DP = 4

/** Width of the widest overlaid countdown ("23h 59m" at 18 sp) at font scale 1; grows with the font scale. */
internal const val COUNTDOWN_TEXT_DP = 68
internal const val COUNTDOWN_GAP_DP = 12
internal const val COUNTDOWN_END_DP = 10

/** Width share of the Done column next to a weight-1 main region (rows only take weights: see [NextUpRemote]). */
internal const val DONE_WEIGHT = 0.3f

/** Height of the Done row in stacked layouts: a 48 dp touch target plus its 6 dp gap. */
internal const val DONE_HEIGHT_DP = 54

/** Padding of the multi-region layouts (Next up 2×2 / 2×3, Two Clocks 4×2 / 4×3). */
internal const val SURFACE_PAD_DP = 10

/** Next up 2×2 / 2×3 stack: glyph with the countdown beside it. */
internal const val STACK_GLYPH_DP = 36
internal const val STACK_COUNTDOWN_GAP_DP = 10

/** Next up 4×2 ribbon: the capsule strip under the now row. */
internal const val RIBBON_BOTTOM_DP = 8
internal const val CAPSULE_HEIGHT_DP = 40
internal const val CAPSULE_SIDE_DP = 8
internal const val CAPSULE_GAP_DP = 3
internal const val CAPSULE_PAD_DP = 6
internal const val CAPSULE_GLYPH_DP = 18
internal const val CAPSULE_GLYPH_GAP_DP = 4

/** "Up next" rows (Next up 2×3 / 4×3, Two Clocks 4×3). */
internal const val UP_NEXT_GLYPH_DP = 20
internal const val UP_NEXT_GLYPH_GAP_DP = 8
internal const val UP_NEXT_SPACING_DP = 4
internal const val UP_NEXT_TOP_STACK_DP = 10
internal const val UP_NEXT_TOP_LARGE_DP = 8
internal const val UP_NEXT_SIDE_LARGE_DP = 2
internal const val ADAPTATION_TOP_DP = 4
internal const val ADAPTATION_BAR_DP = 6

/** Two Clocks now card. */
internal const val CARD_H_PAD_DP = 10
internal const val CARD_V_PAD_DP = 8
internal const val CARD_WEIGHT = 1.15f
internal const val CARD_GAP_DP = 10
internal const val CARD_TOP_DP = 6
internal const val CLOCKS_TALL_PAD_DP = 8
internal const val HEADER_STRIP_BOTTOM_DP = 6

/** Two Clocks 2×3: the dial above the now card keeps at least this much height; "then …" goes first. */
internal const val MIN_TALL_DIAL_DP = 84

/** Two Clocks 2×2: dial with a caption under it. */
internal const val SQUARE_DIAL_PAD_DP = 8

/** Rows of "Up next" in the stacked layouts, at most (two lines each: time + label, then the other zone's time). */
internal const val UP_NEXT_ROWS = 2

/** Capsules in the 4×2 ribbon, at most. */
internal const val CAPSULES = 3

/** Reference cell size (dp) a layout's text is fitted for. */
internal data class CellDp(val width: Float, val height: Float)

/** The current block (label, until / then, the other zone's time) as it fits its slot. */
internal data class NowFit(
    /** Whether the glyph (and in the stacks, the countdown beside it) is shown. */
    val glyph: Boolean,
    val countdown: Boolean,
    /** Whether the Two Clocks card keeps its "Tokyo · Day 2" header. */
    val header: Boolean,
    val title: Fitted,
    /** "until 18:00 · then Sleep" on one line, or one phrase per line, or "until 18:00" alone. */
    val details: List<Fitted>,
    val secondary: Fitted?,
    val heightDp: Float,
    /** False only for the fallback when nothing fits (the matrix test proves real labels never get there). */
    val fits: Boolean,
)

/** The Two Clocks 2×2 caption under the dial. */
internal data class CaptionFit(val title: Fitted, val detail: Fitted?)

/** One "Up next" entry: "18:30  Melatonin" (or "18:30 Melatonin" in a capsule) and the other zone's time. */
internal data class UpNextRowFit(val item: UpcomingText, val line: Fitted, val secondary: Fitted?) {
    val heightDp: Float get() = max(UP_NEXT_GLYPH_DP.toFloat(), line.heightDp + (secondary?.heightDp ?: 0f))
}

/** The "Up next" region: the entries that fit whole, in order, and whether the adaptation bar shows. */
internal data class UpNextFit(
    val rows: List<UpNextRowFit>,
    val capsules: Boolean = false,
    val bar: Boolean = false,
    val route: Boolean = false,
    val heightDp: Float = 0f,
) {
    companion object {
        val None = UpNextFit(emptyList())
    }
}

/** Everything one layout fitted at capture time. */
internal data class WidgetFit(
    /** 1×1 Next up label size ([TextFit.smallLabelSp]). */
    val smallLabel: Int? = null,
    val now: NowFit? = null,
    val caption: CaptionFit? = null,
    val upNext: UpNextFit = UpNextFit.None,
)

/**
 * Capture-time text fitting for every widget layout: labels never clip.
 *
 * Each layout is fitted for a reference cell ([cell]): the real launcher cell its bucket stands for, the same sizes
 * as the widget goldens. Remote Compose text cannot shrink itself on the host, so the sizes, line counts and which
 * optional parts show are decided here, measured with [TextFit] (the player's own `StaticLayout` setup) at the
 * device's font scale.
 *
 * The label and the other zone's time always stay. When space runs out, parts give way in this order:
 * 1. the "then …" tail;
 * 2. the label shrinks, down to [TITLE_MIN_SP] (it may take two lines where the height allows);
 * 3. the countdown, then the glyph (the label still names the block), or the card's "Tokyo · Day 2" header;
 *    as a last resort the other zone's time goes down to [SECONDARY_LAST_RESORT_SP];
 * 4. "Up next" entries, the last one first (they are never cut off mid-label).
 */
internal object LabelFit {
    const val TITLE_SP = 16
    const val TITLE_MIN_SP = 13
    const val DETAIL_SP = 12
    const val DETAIL_MIN_SP = 11
    const val SECONDARY_SP = 11
    const val SECONDARY_MIN_SP = 10

    /** Floor of the other zone's time once nothing else is left to give (a 12-hour time with a long place name). */
    const val SECONDARY_LAST_RESORT_SP = 9
    const val HEADER_SP = 11
    const val CAPTION_SP = 12
    const val CAPTION_MIN_SP = 10
    const val CAPTION_DETAIL_SP = 11
    const val CAPTION_DETAIL_MIN_SP = 10
    const val UP_NEXT_SP = 13
    const val UP_NEXT_MIN_SP = 11
    const val UP_NEXT_SECONDARY_SP = 11
    const val UP_NEXT_SECONDARY_MIN_SP = 10
    const val UP_NEXT_TITLE_SP = 11
    const val CAPSULE_SP = 11
    const val CAPSULE_MIN_SP = 10
    const val CAPSULE_SECONDARY_SP = 10
    const val CAPSULE_SECONDARY_MIN_SP = 9
    const val COUNTDOWN_SP = 18

    fun cell(layout: NextUpLayout): CellDp = when (layout) {
        NextUpLayout.Small -> CellDp(76f, 76f)
        NextUpLayout.Medium -> CellDp(176f, 76f)
        NextUpLayout.Wide -> CellDp(360f, 76f)
        NextUpLayout.Square -> CellDp(176f, 176f)
        NextUpLayout.Ribbon -> CellDp(360f, 172f)
        // One document serves 2×3 and 4×3: fit the narrower.
        NextUpLayout.Tall -> CellDp(176f, 260f)
    }

    fun cell(layout: TwoClocksLayout): CellDp = when (layout) {
        TwoClocksLayout.Compact -> CellDp(76f, 76f)
        TwoClocksLayout.Square -> CellDp(176f, 176f)
        TwoClocksLayout.Tall -> CellDp(176f, 260f)
        TwoClocksLayout.Wide -> CellDp(360f, 172f)
        TwoClocksLayout.Large -> CellDp(360f, 260f)
    }

    /** End padding of a one-row Next up: a Done column beside it already brings its own gap. */
    fun rowEndDp(layout: NextUpLayout, texts: WidgetTexts): Int =
        if (layout != NextUpLayout.Medium && texts.done != null) ROW_END_BESIDE_DONE_DP else ROW_END_DP

    /** Room kept at the end of a one-row Next up for the overlaid countdown; it is sp-sized, so it follows the font scale. */
    fun countdownSlotDp(context: Context, endDp: Int): Int {
        val scale = TextFit.pxForSp(context, COUNTDOWN_SP) / (COUNTDOWN_SP * context.resources.displayMetrics.density)
        return (COUNTDOWN_TEXT_DP * scale.coerceAtLeast(1f)).roundToInt() + endDp + COUNTDOWN_GAP_DP
    }

    fun nextUp(context: Context, texts: WidgetTexts, layout: NextUpLayout): WidgetFit = when (layout) {
        NextUpLayout.Small -> WidgetFit(smallLabel = TextFit.smallLabelSp(context, texts.title))
        NextUpLayout.Medium, NextUpLayout.Wide -> WidgetFit(now = nowRow(context, texts, layout, capsules = false))
        NextUpLayout.Ribbon -> {
            val capsules = capsules(context, texts, cell(layout).width)
            WidgetFit(now = nowRow(context, texts, layout, capsules = capsules.rows.isNotEmpty()), upNext = capsules)
        }
        NextUpLayout.Square -> {
            val cell = cell(layout)
            WidgetFit(now = nowStack(context, texts, cell, cell.height - 2 * SURFACE_PAD_DP - doneDp(texts), withThen = true))
        }
        NextUpLayout.Tall -> nextUpTall(context, texts)
    }

    fun twoClocks(context: Context, texts: WidgetTexts, layout: TwoClocksLayout): WidgetFit {
        // Without a plan every size shows the dial alone: its centre says "No trip / Plan one".
        if (texts.glyph == GlyphKind.NoTrip) return WidgetFit()
        val cell = cell(layout)
        return when (layout) {
            TwoClocksLayout.Compact -> WidgetFit()
            TwoClocksLayout.Square -> WidgetFit(caption = caption(context, texts, cell))
            TwoClocksLayout.Tall -> {
                val budget = cell.height - 2 * CLOCKS_TALL_PAD_DP - doneDp(texts) - CARD_TOP_DP - MIN_TALL_DIAL_DP -
                    2 * CARD_V_PAD_DP
                WidgetFit(now = nowCard(context, texts, cardWidthDp(layout, texts), budget, withHeader = true))
            }
            TwoClocksLayout.Wide -> {
                val budget = cell.height - 2 * SURFACE_PAD_DP - 2 * CARD_V_PAD_DP
                WidgetFit(now = nowCard(context, texts, cardWidthDp(layout, texts), budget.toFloat(), withHeader = true))
            }
            TwoClocksLayout.Large -> twoClocksLarge(context, texts, cell)
        }
    }

    /** Text width inside the Two Clocks now card. */
    fun cardWidthDp(layout: TwoClocksLayout, texts: WidgetTexts): Float {
        val cell = cell(layout)
        if (layout == TwoClocksLayout.Tall) return cell.width - 2 * CLOCKS_TALL_PAD_DP - 2 * CARD_H_PAD_DP
        val inner = cell.width - 2 * SURFACE_PAD_DP
        val main = if (texts.done != null) inner / (1f + DONE_WEIGHT) else inner
        return main * CARD_WEIGHT / (1f + CARD_WEIGHT) - CARD_GAP_DP - 2 * CARD_H_PAD_DP
    }

    // --- Layout families ---------------------------------------------------------------------------------------

    private fun nowRow(context: Context, texts: WidgetTexts, layout: NextUpLayout, capsules: Boolean): NowFit {
        val cell = cell(layout)
        val besideDone = layout != NextUpLayout.Medium && texts.done != null
        val mainWidth = if (besideDone) cell.width / (1f + DONE_WEIGHT) else cell.width
        val mainHeight = if (layout == NextUpLayout.Ribbon) {
            cell.height - RIBBON_BOTTOM_DP - if (capsules) CAPSULE_HEIGHT_DP else 0
        } else {
            cell.height
        }
        val end = rowEndDp(layout, texts)
        val countdownSlot = countdownSlotDp(context, end)
        val height = mainHeight - 2 * ROW_V_PAD_DP
        fun width(glyph: Boolean, countdown: Boolean) = mainWidth - ROW_START_DP -
            (if (glyph) ROW_GLYPH_DP + ROW_GLYPH_GAP_DP else 0) - (if (countdown) countdownSlot else end)
        val slots = buildList {
            if (texts.countdownEnd != null && layout != NextUpLayout.Medium) add(Slot(width(true, true), height, glyph = true, countdown = true))
            add(Slot(width(true, false), height, glyph = true))
            add(Slot(width(false, false), height, secondaryMinSp = SECONDARY_LAST_RESORT_SP))
        }
        val until = until(texts)
        // One-row cells keep to three lines; the ribbon's taller row may give "then …" its own line.
        val options = if (layout == NextUpLayout.Ribbon) {
            listOf(listOf(texts.subtitle), texts.subtitleLines, listOf(until))
        } else {
            listOf(listOf(texts.subtitle), listOf(until))
        }
        return fitNow(context, texts, slots, options.distinct())
    }

    private fun nowStack(context: Context, texts: WidgetTexts, cell: CellDp, budgetDp: Float, withThen: Boolean, glyphOptional: Boolean = true): NowFit {
        val width = cell.width - 2 * SURFACE_PAD_DP
        val countdown = texts.countdownEnd != null
        val countdownHeight = if (countdown) TextFit.measure(context, "0", 1000f, COUNTDOWN_SP).heightDp else 0f
        val glyphRow = max(STACK_GLYPH_DP.toFloat(), countdownHeight) + LINE_GAP_DP
        val slots = buildList {
            add(Slot(width, budgetDp, glyph = true, countdown = countdown, fixedDp = glyphRow))
            if (glyphOptional) add(Slot(width, budgetDp, secondaryMinSp = SECONDARY_LAST_RESORT_SP))
        }
        val until = until(texts)
        val options = if (withThen) listOf(texts.subtitleLines, listOf(until)) else listOf(listOf(until))
        return fitNow(context, texts, slots, options.distinct())
    }

    private fun nowCard(context: Context, texts: WidgetTexts, widthDp: Float, budgetDp: Float, withHeader: Boolean): NowFit {
        val header = texts.header?.takeIf { withHeader }?.let { TextFit.measure(context, it, widthDp, HEADER_SP).heightDp + LINE_GAP_DP }
        val slots = listOfNotNull(
            header?.let { Slot(widthDp, budgetDp, header = true, fixedDp = it) },
            Slot(widthDp, budgetDp, secondaryMinSp = SECONDARY_LAST_RESORT_SP),
        )
        val until = until(texts)
        return fitNow(context, texts, slots, listOf(texts.subtitleLines, listOf(until)).distinct())
    }

    /** 2×3 / 4×3 Next up: the now stack first, then as many "Up next" rows as still fit whole. */
    private fun nextUpTall(context: Context, texts: WidgetTexts): WidgetFit {
        val cell = cell(NextUpLayout.Tall)
        val total = cell.height - 2 * SURFACE_PAD_DP - doneDp(texts)
        val width = cell.width - 2 * SURFACE_PAD_DP
        // Up next already says what comes next: the stack drops its "then …" line for the room.
        val withThen = texts.upcoming.isEmpty()
        // The glyph and countdown go only once every Up next row has.
        for (glyphOptional in listOf(false, true)) {
            for (rows in min(UP_NEXT_ROWS, texts.upcoming.size) downTo 0) {
                val upNext = upNextRows(context, texts, width, rows, barWithRows = false, withRoute = true) ?: continue
                val upNextHeight = if (upNext.heightDp > 0f) upNext.heightDp + UP_NEXT_TOP_STACK_DP else 0f
                val now = nowStack(context, texts, cell, total - upNextHeight, withThen, glyphOptional)
                if (now.fits) return WidgetFit(now = now, upNext = upNext)
            }
        }
        return WidgetFit(now = nowStack(context, texts, cell, total, withThen), upNext = UpNextFit.None)
    }

    /** 4×3 Two Clocks: header strip, the dial beside the now card, then as many "Up next" rows as still fit. */
    private fun twoClocksLarge(context: Context, texts: WidgetTexts, cell: CellDp): WidgetFit {
        val inner = cell.width - 2 * SURFACE_PAD_DP
        val strip = if (texts.header != null || texts.route != null) {
            max(texts.header?.let { TextFit.measure(context, it, inner, HEADER_SP).heightDp } ?: 0f, if (texts.route != null) RouteHeightDp.toFloat() else 0f) +
                HEADER_STRIP_BOTTOM_DP
        } else {
            0f
        }
        val total = cell.height - 2 * SURFACE_PAD_DP - strip
        val cardWidth = cardWidthDp(TwoClocksLayout.Large, texts)
        for (rows in min(UP_NEXT_ROWS, texts.upcoming.size) downTo 0) {
            val upNext = upNextRows(context, texts, inner - 2 * UP_NEXT_SIDE_LARGE_DP, rows, barWithRows = true, withRoute = false) ?: continue
            val upNextHeight = if (upNext.heightDp > 0f) upNext.heightDp + UP_NEXT_TOP_LARGE_DP else 0f
            val now = nowCard(context, texts, cardWidth, total - upNextHeight - 2 * CARD_V_PAD_DP, withHeader = false)
            if (now.fits) return WidgetFit(now = now, upNext = upNext)
        }
        return WidgetFit(now = nowCard(context, texts, cardWidth, total - 2 * CARD_V_PAD_DP, withHeader = false))
    }

    private fun caption(context: Context, texts: WidgetTexts, cell: CellDp): CaptionFit {
        val width = cell.width - 2 * SQUARE_DIAL_PAD_DP
        val title = TextFit.fit(context, texts.dialTitle, width, CAPTION_SP, CAPTION_MIN_SP, maxLines = 2, semibold = true, fewerLinesFirst = true)
            ?: TextFit.measure(context, texts.dialTitle, width, CAPTION_MIN_SP, maxLines = 2, semibold = true)
        val detail = texts.dialDetail?.let {
            TextFit.fit(context, it, width, CAPTION_DETAIL_SP, CAPTION_DETAIL_MIN_SP, maxLines = 2, fewerLinesFirst = true)
                ?: TextFit.measure(context, it, width, CAPTION_DETAIL_MIN_SP, maxLines = 2)
        }
        return CaptionFit(title, detail)
    }

    /**
     * [rows] "Up next" rows, each whole (the label may wrap once); null when one of them cannot fit [widthDp]. The
     * adaptation bar shows with them when [barWithRows], and always when there are no rows.
     */
    private fun upNextRows(context: Context, texts: WidgetTexts, widthDp: Float, rows: Int, barWithRows: Boolean, withRoute: Boolean): UpNextFit? {
        val textWidth = widthDp - UP_NEXT_GLYPH_DP - UP_NEXT_GLYPH_GAP_DP
        val fitted = texts.upcoming.take(rows).map { item ->
            val line = TextFit.fit(context, "${item.time}  ${item.label}", textWidth, UP_NEXT_SP, UP_NEXT_MIN_SP, maxLines = 2, fewerLinesFirst = true)
                ?: return null
            val secondary = item.secondary?.let {
                TextFit.fit(context, it, textWidth, UP_NEXT_SECONDARY_SP, UP_NEXT_SECONDARY_MIN_SP, maxLines = 2, fewerLinesFirst = true) ?: return null
            }
            UpNextRowFit(item, line, secondary)
        }
        val bar = (barWithRows || fitted.isEmpty()) && texts.adaptation != null && texts.adaptationLabel != null
        val route = withRoute && fitted.isNotEmpty() && texts.route != null
        val heights = buildList {
            if (fitted.isNotEmpty()) {
                val title = TextFit.measure(context, context.getString(R.string.widget_up_next), widthDp, UP_NEXT_TITLE_SP, semibold = true).heightDp
                add(max(title, if (route) RouteHeightDp.toFloat() else 0f))
            }
            fitted.forEach { add(it.heightDp) }
            if (bar) {
                add(ADAPTATION_TOP_DP + TextFit.measure(context, adaptationText(texts), widthDp, SECONDARY_SP).heightDp)
                add(ADAPTATION_BAR_DP.toFloat())
            }
        }
        val height = heights.sum() + UP_NEXT_SPACING_DP * (heights.size - 1).coerceAtLeast(0)
        return UpNextFit(fitted, bar = bar, route = route, heightDp = height)
    }

    /** The 4×2 ribbon's capsules: as many of the next [CAPSULES] as show their label whole, in order. */
    private fun capsules(context: Context, texts: WidgetTexts, cellWidth: Float): UpNextFit {
        for (count in min(CAPSULES, texts.upcoming.size) downTo 1) {
            val outer = (cellWidth - 2 * CAPSULE_SIDE_DP) / count
            // A middle capsule gives up a gap on both sides.
            val gaps = CAPSULE_GAP_DP * min(count - 1, 2)
            val textWidth = outer - gaps - 2 * CAPSULE_PAD_DP - CAPSULE_GLYPH_DP - CAPSULE_GLYPH_GAP_DP
            val fitted = texts.upcoming.take(count).map { item ->
                val line = TextFit.fit(context, "${item.time} ${item.label}", textWidth, CAPSULE_SP, CAPSULE_MIN_SP) ?: return@map null
                val secondary = item.secondary?.let {
                    TextFit.fit(context, it, textWidth, CAPSULE_SECONDARY_SP, CAPSULE_SECONDARY_MIN_SP) ?: return@map null
                }
                UpNextRowFit(item, line, secondary).takeIf { line.heightDp + (secondary?.heightDp ?: 0f) <= CAPSULE_HEIGHT_DP }
            }
            if (fitted.all { it != null }) return UpNextFit(fitted.filterNotNull(), capsules = true, heightDp = CAPSULE_HEIGHT_DP.toFloat())
        }
        return UpNextFit.None
    }

    // --- The search ---------------------------------------------------------------------------------------------

    /** Where the now block can go: its text width and height, what else it shows, and height already taken. */
    private class Slot(
        val widthDp: Float,
        val heightDp: Float,
        val glyph: Boolean = false,
        val countdown: Boolean = false,
        val header: Boolean = false,
        val fixedDp: Float = 0f,
        val secondaryMinSp: Int = SECONDARY_MIN_SP,
    )

    /**
     * The first arrangement that fits, trying [slots] in order (each gives up more: countdown, then glyph), the label
     * from [TITLE_SP] down to [TITLE_MIN_SP], and [detailOptions] in order (with "then …", then without). The label and
     * the other zone's time are in every arrangement.
     */
    private fun fitNow(context: Context, texts: WidgetTexts, slots: List<Slot>, detailOptions: List<List<String>>): NowFit {
        val until = until(texts)
        for (slot in slots) {
            val secondary = texts.secondary?.let {
                TextFit.fit(context, it, slot.widthDp, SECONDARY_SP, slot.secondaryMinSp, maxLines = 2, fewerLinesFirst = true) ?: continue
            }
            for (sp in TITLE_SP downTo TITLE_MIN_SP) {
                val title = TextFit.measure(context, texts.title, slot.widthDp, sp, maxLines = 2, semibold = true)
                if (!title.fits) continue
                for (option in detailOptions) {
                    val details = option.map { line ->
                        // "until 18:00" (or the adapted line) may wrap; a joined or "then …" line never does.
                        TextFit.fit(context, line, slot.widthDp, DETAIL_SP, DETAIL_MIN_SP, maxLines = if (line == until) 2 else 1, fewerLinesFirst = true)
                    }
                    if (details.any { it == null }) continue
                    val items = listOf(title) + details.filterNotNull() + listOfNotNull(secondary)
                    val height = slot.fixedDp + items.sumOf { it.heightDp.toDouble() }.toFloat() + LINE_GAP_DP * (items.size - 1)
                    if (height <= slot.heightDp) {
                        return NowFit(slot.glyph, slot.countdown, slot.header, title, details.filterNotNull(), secondary, height, fits = true)
                    }
                }
            }
        }
        // Nothing fits (never for the real labels, see WidgetLabelFitTest): the smallest arrangement, ellipsized.
        val slot = slots.last()
        val title = TextFit.measure(context, texts.title, slot.widthDp, TITLE_MIN_SP, maxLines = 2, semibold = true)
        val details = listOf(TextFit.measure(context, until, slot.widthDp, DETAIL_MIN_SP))
        val secondary = texts.secondary?.let { TextFit.measure(context, it, slot.widthDp, slot.secondaryMinSp) }
        val items = listOf(title) + details + listOfNotNull(secondary)
        val height = slot.fixedDp + items.sumOf { it.heightDp.toDouble() }.toFloat() + LINE_GAP_DP * (items.size - 1)
        return NowFit(slot.glyph, slot.countdown, slot.header, title, details, secondary, height, fits = false)
    }

    private fun until(texts: WidgetTexts) = texts.subtitleLines.firstOrNull() ?: texts.subtitle

    private fun doneDp(texts: WidgetTexts) = if (texts.done != null) DONE_HEIGHT_DP else 0

    /** "6% adapted · −7 h" above the adaptation bar. */
    fun adaptationText(texts: WidgetTexts): String = listOfNotNull(texts.adaptationLabel, texts.misalignment).joinToString(" · ")
}
