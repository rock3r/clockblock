package dev.sebastiano.clockblocker.opus.widget.rc

import android.content.Context
import dev.sebastiano.clockblocker.opus.widget.R
import dev.sebastiano.clockblocker.opus.widget.draw.GlyphKind
import dev.sebastiano.clockblocker.opus.widget.text.Fitted
import dev.sebastiano.clockblocker.opus.widget.text.TextFit
import dev.sebastiano.clockblocker.opus.widget.text.UpcomingText
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTexts
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Geometry shared by the layouts in WidgetRemoteContent and the fitting below: change it in one place only.

/** Gap between the stacked lines of a text block. */
internal const val LINE_GAP_DP = 1

/** One-row Next up (2×1, 4×1, the 4×2 ribbon's top row): glyph, then the text column, centred vertically. */
internal const val ROW_START_DP = 12
internal const val ROW_V_PAD_DP = 4
internal const val ROW_GLYPH_DP = 40
internal const val ROW_GLYPH_GAP_DP = 12
internal const val ROW_END_DP = 14
internal const val ROW_END_BESIDE_DONE_DP = 4

/** Floor for the widest overlaid countdown ("23h 59m" at 18 sp) at font scale 1; see [LabelFit.countdownWidthDp]. */
internal const val COUNTDOWN_TEXT_DP = 68
internal const val COUNTDOWN_GAP_DP = 12
internal const val COUNTDOWN_END_DP = 10

/** Width share of the Done column next to a weight-1 main region (rows only take weights: see [NextUpRemote]). */
internal const val DONE_WEIGHT = 0.3f

/** Height of the Done row in stacked layouts: a 48 dp touch target plus its 6 dp gap. */
internal const val DONE_HEIGHT_DP = 54
internal const val DONE_TOUCH_DP = 48
internal const val DONE_TOP_DP = 6

/** Padding around the Done column of the one-row layouts (Next up 4×1, 4×2) and inside the button. */
internal const val DONE_COLUMN_PAD_DP = 8
internal const val DONE_TEXT_PAD_DP = 0

/** Gap between the Two Clocks main region and its Done column. */
internal const val CLOCKS_DONE_GAP_DP = 6

/** 1×1 Next up: padding, the glyph (with or without the countdown beside it) and the gap above the label. */
internal const val SMALL_PAD_DP = 4
internal const val SMALL_GAP_DP = 2
internal const val SMALL_LONE_GLYPH_DP = 24

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
internal const val UP_NEXT_ROUTE_GAP_DP = 8
internal const val ADAPTATION_TOP_DP = 4
internal const val ADAPTATION_BAR_DP = 6

/** Two Clocks now card. */
internal const val CARD_H_PAD_DP = 8
internal const val CARD_V_PAD_DP = 8
internal const val CARD_WEIGHT = 1.15f
internal const val CARD_GAP_DP = 10
internal const val CARD_TOP_DP = 6
internal const val CLOCKS_TALL_PAD_DP = 8
internal const val HEADER_STRIP_BOTTOM_DP = 6
internal const val HEADER_STRIP_SIDE_DP = 2
internal const val HEADER_ROUTE_GAP_DP = 8

/** Two Clocks 2×3: the dial above the now card keeps at least this much height; "then …" goes first. */
internal const val MIN_TALL_DIAL_DP = 84

/** Two Clocks 2×2: dial with a caption under it; the dial keeps at least this much height. */
internal const val SQUARE_DIAL_PAD_DP = 8
internal const val MIN_SQUARE_DIAL_DP = 64

/** Two Clocks 1×1 and 2×1 / 4×1 rows: the strips fill the widget inside this padding. */
internal const val STRIP_PAD_DP = 5

/** Two Clocks without a plan: the glyph above "No trip" and "Plan one", inside this padding. */
internal const val EMPTY_PAD_DP = 6
internal const val EMPTY_GLYPH_DP = 24
internal const val EMPTY_GAP_DP = 2

/** Rows of "Up next" in the stacked layouts, at most (two lines each: time + label, then the other zone's time). */
internal const val UP_NEXT_ROWS = 2

/** Capsules in the 4×2 ribbon, at most. */
internal const val CAPSULES = 3

/** The current block (label, until / then, the other zone's time) as it fits its slot. */
internal data class NowFit(
    /** Whether the glyph (and in the stacks, the countdown beside it) is shown. */
    val glyph: Boolean,
    val countdown: Boolean,
    /** The Two Clocks card's "Tokyo · Day 2" header, when it shows. */
    val header: Fitted?,
    val title: Fitted,
    /** "until 18:00 · then Sleep" on one line, one phrase per line, "until 18:00" alone, or nothing (short rows). */
    val details: List<Fitted>,
    val secondary: Fitted?,
    val heightDp: Float,
    /** False only for the fallback when nothing fits (the matrix test proves real labels never get there). */
    val fits: Boolean,
    /** True when the other zone's time didn't fit in any form, even joined to the until line (tightest minimums). */
    val secondaryDropped: Boolean = false,
    /** The countdown's size and whether it is compact ("2h13m"); the stacks may shrink it to fit beside the glyph. */
    val countdownSp: Int = LabelFit.COUNTDOWN_SP,
    val countdownCompact: Boolean = false,
) {
    /** Fits with its until line and the other zone's time, in some form: what Up next entries give way to. */
    val whole: Boolean get() = fits && details.isNotEmpty() && !secondaryDropped
}

/** The 1×1 Next up: the glyph (and the countdown beside it, when it fits) above the label, or the label alone. */
internal data class SmallFit(val glyph: Boolean, val countdownSp: Int?, val label: Fitted, val fits: Boolean)

/** The Two Clocks 2×2 caption under the dial; null parts don't show. */
internal data class CaptionFit(val title: Fitted?, val detail: Fitted?) {
    /** Height under the dial, with the gaps above each line. */
    val heightDp: Float get() = listOfNotNull(title, detail).sumOf { (it.heightDp + LINE_GAP_DP).toDouble() }.toFloat()
}

/** Two Clocks without a plan: "No trip" (always) and "Plan one", with the glyph above when there is room. */
internal data class EmptyFit(val glyph: Boolean, val title: Fitted, val action: Fitted?)

/** One "Up next" entry: "18:30  Melatonin" (or "18:30 Melatonin" in a capsule) and the other zone's time. */
internal data class UpNextRowFit(val item: UpcomingText, val line: Fitted, val secondary: Fitted?) {
    val heightDp: Float get() = max(UP_NEXT_GLYPH_DP.toFloat(), line.heightDp + (secondary?.heightDp ?: 0f))
}

/** The "Up next" region: the entries that fit whole, in order, and whether the adaptation bar shows. */
internal data class UpNextFit(
    val rows: List<UpNextRowFit>,
    val capsules: Boolean = false,
    /** "6% adapted · −7 h" above the adaptation bar; null hides both. */
    val bar: Fitted? = null,
    val route: Boolean = false,
    val heightDp: Float = 0f,
) {
    companion object {
        val None = UpNextFit(emptyList())
    }
}

/** Everything one layout fitted at capture time. */
internal data class WidgetFit(
    val small: SmallFit? = null,
    val now: NowFit? = null,
    val caption: CaptionFit? = null,
    val upNext: UpNextFit = UpNextFit.None,
    /** The Done button's label ("Done", "Skipped", "✓ Done"). */
    val done: Fitted? = null,
    /** The Two Clocks 4×3 header strip ("Tokyo · Day 2") and whether the route shows beside it. */
    val headerStrip: Fitted? = null,
    val headerRoute: Boolean = false,
    /** Two Clocks without a plan. */
    val empty: EmptyFit? = null,
)

/**
 * Capture-time text fitting for every widget layout: labels never clip.
 *
 * Remote Compose text cannot shrink itself on the host, so the sizes, line counts and which optional parts show are
 * decided here, measured with [TextFit] (the player's own `StaticLayout` setup) at the device's font scale. Each
 * layout is fitted for the **smallest size the host can give it**: its bucket's [Bucket.fitAt] (see [WidgetSizes]).
 * The document is drawn stretched to the real widget, and text that fits the minimum fits every larger size.
 *
 * What matters most: the label (always shown), then the local "until" line, then the other zone's time, then
 * "then …". When space runs out, parts give way in this order:
 * 1. the "then …" tail;
 * 2. the other zone's place shortens: cut at its first "/", " - " or "(", then its airport code
 *    ([WidgetTexts.secondaryOptions]; screen readers keep the full name);
 * 3. the other zone's time joins the until line, "until 16:30 · 08:30 LIS" ([WidgetTexts.untilCompact]);
 * 4. the label shrinks, down to [TITLE_MIN_SP] (it may take two lines where the height allows);
 * 5. "Up next" entries, the last one first (they are never cut off mid-label; their place shortens like the now
 *    block's first);
 * 6. the countdown, then the glyph (the label still names the block), or the card's "Tokyo · Day 2" header;
 * 7. as a last resort the other zone's time goes down to [SECONDARY_LAST_RESORT_SP] and the label to
 *    [TITLE_LAST_RESORT_SP];
 * 8. the other zone's time, at the tightest minimums only: "Avoid light / until 16:30", the until line down to
 *    [DETAIL_LAST_RESORT_SP]. Screen readers still hear it.
 *
 * The building blocks ([fitNow] with its [NowSlot]s, [fitDone], [fitHeader]) are what any new layout should use:
 * describe where the now block can go, smallest-first, and draw what comes back (`FittedLabel`).
 */
internal object LabelFit {
    const val TITLE_SP = 16
    const val TITLE_MIN_SP = 13

    /** Floor of the label in the barest slot, as a last resort (a long single word at 1.3×). */
    const val TITLE_LAST_RESORT_SP = 11
    const val DETAIL_SP = 12
    const val DETAIL_MIN_SP = 11

    /** Floor of the until line once the other zone's time has gone and nothing else is left to give (1.3×). */
    const val DETAIL_LAST_RESORT_SP = 9
    const val SECONDARY_SP = 11
    const val SECONDARY_MIN_SP = 10

    /** Floor of the other zone's time once nothing else is left to give (a 12-hour time with a long place name). */
    const val SECONDARY_LAST_RESORT_SP = 9
    const val HEADER_SP = 11
    const val HEADER_MIN_SP = 10
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

    /**
     * Floor of the countdown beside the 2×2 / 2×3 stack's glyph, the until line's size: it shrinks (and goes compact)
     * before it goes. A 2×3 minimum leaves 44 dp beside the glyph, and "2h59m" is 45 dp at 14 sp.
     */
    const val STACK_COUNTDOWN_MIN_SP = 12
    const val SMALL_LABEL_SP = 11

    /**
     * Floor of the 1×1 label as drawn, in dp: at a large font scale its sp goes below this so the drawn size never
     * does. A 57 dp tile cannot grow with the font scale ("Clockblocked" at 8 sp × 1.3 is 63 dp wide), like the
     * dial readouts (see [TextFit.dialSp]).
     */
    const val SMALL_LABEL_MIN_DP = 7
    const val SMALL_COUNTDOWN_SP = 12
    const val SMALL_COUNTDOWN_MIN_SP = 8
    const val DONE_SP = 14
    const val DONE_MIN_SP = 10
    const val ADAPTATION_SP = 11
    const val ADAPTATION_MIN_SP = 10

    /** End padding of a one-row Next up: a Done column beside it already brings its own gap. */
    fun rowEndDp(layout: NextUpLayout, texts: WidgetTexts): Int =
        if (layout != NextUpLayout.Medium && texts.done != null) ROW_END_BESIDE_DONE_DP else ROW_END_DP

    /** How much the font scale grows [sp] text (non-linearly, like the player): 1 at the default scale. */
    fun spScale(context: Context, sp: Int): Float =
        (TextFit.pxForSp(context, sp) / (sp * context.resources.displayMetrics.density)).coerceAtLeast(1f)

    /**
     * Width of the widest countdown at [COUNTDOWN_SP], at the current font scale: the fixed estimate, or the widest
     * countdown measured at the Medium weight it is drawn in when that is wider (Bold text adds to the weight).
     */
    fun countdownWidthDp(context: Context): Int = countdownTextDp(context, HostText.countdownWidest(WIDEST_COUNTDOWN_MINUTES), COUNTDOWN_SP)

    /**
     * Width of the countdown [text] at [sp]: measured at the Medium weight it is drawn in, but never less than the
     * fixed estimate ([COUNTDOWN_TEXT_DP] for "23h 59m" at 18 sp) scaled to its length, size and the font scale.
     */
    private fun countdownTextDp(context: Context, text: String, sp: Int): Int {
        val widest = HostText.countdownWidest(WIDEST_COUNTDOWN_MINUTES)
        val estimate = COUNTDOWN_TEXT_DP * text.length.toFloat() / widest.length * sp / COUNTDOWN_SP * spScale(context, sp)
        val measured = TextFit.widthDp(context, text, sp, TextFit.MEDIUM) + TextFit.SAFETY_DP
        return max(estimate.roundToInt(), ceil(measured).toInt())
    }

    /** "23h 59m": the longest countdown a block can show. */
    private const val WIDEST_COUNTDOWN_MINUTES = 23 * 60 + 59

    /** Room kept at the end of a one-row Next up for the overlaid countdown; it is sp-sized, so it follows the font scale. */
    fun countdownSlotDp(context: Context, endDp: Int): Int = countdownWidthDp(context) + endDp + COUNTDOWN_GAP_DP

    /**
     * Next up [layout] fitted for [cell], the smallest size the host draws it at (its bucket's minimum).
     * [countdownMinutes] is what the block has left at capture time (null without a countdown): the 1×1 and the
     * stacks size the countdown for the widest text it shows before the next refresh ([HostText.countdownWidest]).
     */
    fun nextUp(context: Context, texts: WidgetTexts, layout: NextUpLayout, cell: CellDp, countdownMinutes: Int? = null): WidgetFit =
        when (layout) {
            NextUpLayout.Small -> WidgetFit(
                small = small(context, texts, cell, countdownMinutes?.let { HostText.countdownWidest(it, compact = true) }),
            )
            NextUpLayout.Medium, NextUpLayout.Wide -> WidgetFit(
                now = nowRow(context, texts, layout, cell, capsules = false),
                done = rowDone(context, texts, layout, cell, cell.height),
            )
            NextUpLayout.Ribbon -> {
                // Capsules only while the now row stays whole beside them; else the row gets the room.
                val capsules = capsules(context, texts, cell.width)
                val withCapsules = nowRow(context, texts, layout, cell, capsules = capsules.rows.isNotEmpty())
                val keep = capsules.rows.isNotEmpty() && withCapsules.whole
                val rowHeight = cell.height - RIBBON_BOTTOM_DP - if (keep) CAPSULE_HEIGHT_DP else 0
                WidgetFit(
                    now = if (keep) withCapsules else nowRow(context, texts, layout, cell, capsules = false),
                    upNext = if (keep) capsules else UpNextFit.None,
                    done = rowDone(context, texts, layout, cell, rowHeight),
                )
            }
            NextUpLayout.Square -> WidgetFit(
                now = nowStack(context, texts, cell, cell.height - 2 * SURFACE_PAD_DP - doneDp(texts), withThen = true, countdownMinutes),
                done = stackDone(context, texts, cell.width - 2 * SURFACE_PAD_DP),
            )
            NextUpLayout.Tall -> nextUpTall(context, texts, cell, countdownMinutes)
        }

    /** Two Clocks [layout] fitted for [cell], the smallest size the host draws it at: its text parts only. */
    fun twoClocks(context: Context, texts: WidgetTexts, layout: TwoClocksLayout, cell: CellDp): WidgetFit {
        // Without a plan every size says "No trip / Plan one": there is no dial to draw.
        if (texts.glyph == GlyphKind.NoTrip) return WidgetFit(empty = empty(context, texts, cell))
        return when (layout) {
            TwoClocksLayout.Compact, TwoClocksLayout.Strip -> WidgetFit()
            TwoClocksLayout.Square -> WidgetFit(caption = caption(context, texts, cell))
            TwoClocksLayout.Tall -> {
                val budget = cell.height - 2 * CLOCKS_TALL_PAD_DP - doneDp(texts) - CARD_TOP_DP - MIN_TALL_DIAL_DP -
                    2 * CARD_V_PAD_DP
                WidgetFit(
                    now = nowCard(context, texts, cardWidthDp(layout, texts, cell), budget, withHeader = true),
                    done = stackDone(context, texts, cell.width - 2 * CLOCKS_TALL_PAD_DP),
                )
            }
            TwoClocksLayout.Wide -> {
                val budget = cell.height - 2 * SURFACE_PAD_DP - 2 * CARD_V_PAD_DP
                WidgetFit(
                    now = nowCard(context, texts, cardWidthDp(layout, texts, cell), budget, withHeader = true),
                    done = clocksDone(context, texts, cell, cell.height - 2 * SURFACE_PAD_DP),
                )
            }
            TwoClocksLayout.Large -> twoClocksLarge(context, texts, cell)
        }
    }

    /**
     * The Two Clocks dial region of [layout] at [cell], given what [fit] shows around it: the size its spec is laid
     * out for (the host scales it to the real region). Its level (Glance, Simple, Full) comes from this size.
     */
    fun dialBox(texts: WidgetTexts, layout: TwoClocksLayout, cell: CellDp, fit: WidgetFit): CellDp {
        fun box(w: Float, h: Float) = CellDp(w.coerceAtLeast(MIN_DIAL_BOX_DP), h.coerceAtLeast(MIN_DIAL_BOX_DP))
        val now = fit.now
        return when {
            layout == TwoClocksLayout.Compact || layout == TwoClocksLayout.Strip ->
                box(cell.width - 2 * STRIP_PAD_DP, cell.height - 2 * STRIP_PAD_DP)
            layout == TwoClocksLayout.Square || now == null -> box(
                cell.width - 2 * SQUARE_DIAL_PAD_DP,
                cell.height - 2 * SQUARE_DIAL_PAD_DP - (fit.caption?.heightDp ?: 0f),
            )
            layout == TwoClocksLayout.Tall -> box(
                cell.width - 2 * CLOCKS_TALL_PAD_DP,
                cell.height - 2 * CLOCKS_TALL_PAD_DP - doneDp(texts) - CARD_TOP_DP - now.heightDp - 2 * CARD_V_PAD_DP,
            )
            else -> {
                val strip = if (layout == TwoClocksLayout.Large && (fit.headerStrip != null || fit.headerRoute)) {
                    max(fit.headerStrip?.heightDp ?: 0f, if (fit.headerRoute) RouteHeightDp.toFloat() else 0f) + HEADER_STRIP_BOTTOM_DP
                } else {
                    0f
                }
                val upNext = if (fit.upNext.heightDp > 0f) fit.upNext.heightDp + UP_NEXT_TOP_LARGE_DP else 0f
                box(clocksMainWidth(texts, cell) / (1f + CARD_WEIGHT), cell.height - 2 * SURFACE_PAD_DP - strip - upNext)
            }
        }
    }

    /**
     * "No trip" and "Plan one" in the widget, the glyph above them when there is room. "No trip" shrinks before
     * "Plan one" has to go; only a 1×1 at a large font scale shows "No trip" alone (the tap still plans one).
     */
    private fun empty(context: Context, texts: WidgetTexts, cell: CellDp): EmptyFit {
        val width = cell.width - 2 * EMPTY_PAD_DP
        val height = cell.height - 2 * EMPTY_PAD_DP
        for (sp in TITLE_SP downTo EMPTY_MIN_SP) {
            val title = TextFit.fit(context, texts.title, width, sp, sp, maxLines = 2, semibold = true, fewerLinesFirst = true) ?: continue
            val action = TextFit.fit(context, texts.subtitle, width, min(DETAIL_SP, sp), EMPTY_MIN_SP, semibold = true)
                ?.takeIf { title.heightDp + EMPTY_GAP_DP + it.heightDp <= height }
                ?: continue
            val text = title.heightDp + EMPTY_GAP_DP + action.heightDp
            return EmptyFit(glyph = text + EMPTY_GLYPH_DP + EMPTY_GAP_DP <= height, title = title, action = action)
        }
        val title = TextFit.fit(context, texts.title, width, TITLE_SP, EMPTY_MIN_SP, maxLines = 2, semibold = true, fewerLinesFirst = true)
            ?: TextFit.measure(context, texts.title, width, EMPTY_MIN_SP, maxLines = 2, semibold = true)
        return EmptyFit(glyph = title.heightDp + EMPTY_GLYPH_DP + EMPTY_GAP_DP <= height, title = title, action = null)
    }

    /** Floor of the "No trip" texts in a 1×1 at a large font scale. */
    private const val EMPTY_MIN_SP = 8

    /** The smallest dial region a spec is laid out for. */
    private const val MIN_DIAL_BOX_DP = 24f

    /** Text width inside the Two Clocks now card of [layout] at [cell]. */
    fun cardWidthDp(layout: TwoClocksLayout, texts: WidgetTexts, cell: CellDp): Float {
        if (layout == TwoClocksLayout.Tall) return cell.width - 2 * CLOCKS_TALL_PAD_DP - 2 * CARD_H_PAD_DP
        val main = clocksMainWidth(texts, cell)
        return main * CARD_WEIGHT / (1f + CARD_WEIGHT) - CARD_GAP_DP - 2 * CARD_H_PAD_DP
    }

    // --- Building blocks ---------------------------------------------------------------------------------------

    /**
     * Where the now block can go: its text width and height, what else it shows, and height already taken by
     * parts drawn above it ([fixedDp]: a glyph row, a header). [fitNow] tries slots in order, so list them from the
     * richest (glyph, countdown, header) to the barest.
     */
    class NowSlot(
        val widthDp: Float,
        val heightDp: Float,
        val glyph: Boolean = false,
        val countdown: Boolean = false,
        val header: Fitted? = null,
        val fixedDp: Float = 0f,
        val secondaryMinSp: Int = SECONDARY_MIN_SP,
        val countdownSp: Int = COUNTDOWN_SP,
        val countdownCompact: Boolean = false,
    )

    /** One way to show the now block's lines under the label: each of [options] in turn, plus [secondary] below. */
    private class Arrangement(
        val options: List<List<String>>,
        val secondary: Fitted?,
        val secondaryDropped: Boolean = false,
        val detailMinSp: Int = DETAIL_MIN_SP,
    ) {
        /**
         * The largest sizes to try the lines at. Normally only [DETAIL_SP]: each line takes the largest size that fits
         * its width. Below [DETAIL_MIN_SP] (the last resort), lines also shrink to fit the height.
         */
        val detailCaps: IntProgression get() = DETAIL_SP downTo if (detailMinSp < DETAIL_MIN_SP) detailMinSp else DETAIL_SP
    }

    /** One pass of [fitNow]: the slots it tries, how far the label may shrink, and the arrangements for a slot. */
    private class Pass(val slots: List<NowSlot>, val titleMinSp: Int, val arrangements: (NowSlot) -> List<Arrangement>)

    /**
     * The first arrangement of [texts]' now block that fits. The label always shows, and the local "until" line comes
     * next: it stays as long as anything does. Passes, in order:
     * 1. every slot, the label down to [TITLE_MIN_SP], with the other zone's time: on its own line in each of its forms
     *    ([WidgetTexts.secondaryOptions]: full, cut, airport code), each with every one of [detailOptions] (with
     *    "then …", then without); then joined to the until line ([WidgetTexts.untilCompact]);
     * 2. the same in the barest slot, the label down to [TITLE_LAST_RESORT_SP];
     * 3. every slot, the label and the until line only (the tightest minimums at a large font scale);
     * 4. the same in the barest slot, the label down to [TITLE_LAST_RESORT_SP] and the until line down to
     *    [DETAIL_LAST_RESORT_SP];
     * 5. the label alone, in case even that doesn't fit (never with a time to show, see WidgetLabelFitTest).
     */
    fun fitNow(context: Context, texts: WidgetTexts, slots: List<NowSlot>, detailOptions: List<List<String>>): NowFit {
        val until = until(texts)
        val barest = listOf(slots.last())
        val dropped = texts.secondary != null
        fun withSecondary(slot: NowSlot): List<Arrangement> = buildList {
            if (texts.secondaryOptions.isEmpty()) {
                add(Arrangement(detailOptions, null))
                return@buildList
            }
            texts.secondaryOptions.forEach { form ->
                TextFit.fit(context, form, slot.widthDp, SECONDARY_SP, slot.secondaryMinSp, maxLines = 2, fewerLinesFirst = true)
                    ?.let { add(Arrangement(detailOptions, it)) }
            }
            texts.untilCompact?.let { add(Arrangement(listOf(listOf(it)), null)) }
        }
        val untilOnly = listOf(Arrangement(listOf(listOf(until)), null, secondaryDropped = dropped))
        val untilLastResort = listOf(Arrangement(listOf(listOf(until)), null, secondaryDropped = dropped, detailMinSp = DETAIL_LAST_RESORT_SP))
        val labelOnly = listOf(Arrangement(listOf(emptyList()), null, secondaryDropped = dropped))
        val passes = listOf(
            Pass(slots, TITLE_MIN_SP, ::withSecondary),
            Pass(barest, TITLE_LAST_RESORT_SP, ::withSecondary),
            Pass(slots, TITLE_MIN_SP) { untilOnly },
            Pass(barest, TITLE_LAST_RESORT_SP) { untilLastResort },
            Pass(barest, TITLE_LAST_RESORT_SP) { labelOnly },
        )
        for (pass in passes) {
            for (slot in pass.slots) {
                val arrangements = pass.arrangements(slot)
                for (sp in TITLE_SP downTo pass.titleMinSp) {
                    val title = TextFit.measure(context, texts.title, slot.widthDp, sp, maxLines = 2, semibold = true)
                    if (!title.fits) continue
                    // "then …" goes first, then the place shortens, then the other zone's time joins the until line;
                    // all of that before the label shrinks.
                    for (arrangement in arrangements) {
                        for (option in arrangement.options) for (cap in arrangement.detailCaps) {
                            val details = option.map { line ->
                                // "until 18:00" (or the adapted line) may wrap; a joined or "then …" line never does.
                                val maxLines = if (line == until) 2 else 1
                                TextFit.fit(context, line, slot.widthDp, cap, arrangement.detailMinSp, maxLines = maxLines, fewerLinesFirst = true)
                            }
                            if (details.any { it == null }) continue
                            val items = listOf(title) + details.filterNotNull() + listOfNotNull(arrangement.secondary)
                            val height = slot.fixedDp + items.sumOf { it.heightDp.toDouble() }.toFloat() + LINE_GAP_DP * (items.size - 1)
                            if (height <= slot.heightDp) {
                                return NowFit(
                                    slot.glyph, slot.countdown, slot.header, title, details.filterNotNull(), arrangement.secondary, height,
                                    fits = true, secondaryDropped = arrangement.secondaryDropped,
                                    countdownSp = slot.countdownSp, countdownCompact = slot.countdownCompact,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Nothing fits (never for the real labels at a bucket's minimum, see WidgetLabelFitTest): the label, ellipsized.
        val slot = slots.last()
        val title = TextFit.measure(context, texts.title, slot.widthDp, TITLE_LAST_RESORT_SP, maxLines = 2, semibold = true)
        return NowFit(
            slot.glyph, slot.countdown, slot.header, title, emptyList(), null, slot.fixedDp + title.heightDp,
            fits = false, secondaryDropped = texts.secondary != null,
            countdownSp = slot.countdownSp, countdownCompact = slot.countdownCompact,
        )
    }

    /** The Done button's label in a [widthDp] × [heightDp] button: shrinks, then wraps; null without a Done button. */
    fun fitDone(context: Context, texts: WidgetTexts, widthDp: Float, heightDp: Float): Fitted? {
        val label = texts.done?.label ?: return null
        val width = widthDp - 2 * DONE_TEXT_PAD_DP
        for (lines in 1..2) {
            val fitted = TextFit.fit(context, label, width, DONE_SP, DONE_MIN_SP, maxLines = lines, semibold = true) ?: continue
            if (fitted.heightDp <= heightDp) return fitted
        }
        return TextFit.measure(context, label, width, DONE_MIN_SP, semibold = true)
    }

    /** A one-line header ("Tokyo · Day 2") in [widthDp], or null when it cannot show whole. */
    fun fitHeader(context: Context, text: String?, widthDp: Float): Fitted? =
        text?.let { TextFit.fit(context, it, widthDp, HEADER_SP, HEADER_MIN_SP) }

    // --- Layout families ---------------------------------------------------------------------------------------

    private fun small(context: Context, texts: WidgetTexts, cell: CellDp, countdown: String?): SmallFit {
        val width = cell.width - 2 * SMALL_PAD_DP
        val height = cell.height - 2 * SMALL_PAD_DP
        val countdownSp = countdown?.let {
            TextFit.fit(context, it, width - TextFit.SMALL_GLYPH_DP - TextFit.SMALL_GLYPH_GAP_DP, SMALL_COUNTDOWN_SP, SMALL_COUNTDOWN_MIN_SP, semibold = true)
        }
        // Glyph and countdown above the label; the glyph alone; else the label alone (never a glyph alone).
        val rows = listOfNotNull(
            countdownSp?.let { Triple(true, it.sp, max(TextFit.SMALL_GLYPH_DP.toFloat(), it.heightDp)) },
            Triple(true, null, SMALL_LONE_GLYPH_DP.toFloat()),
            Triple(false, null, 0f),
        )
        val minSp = smallLabelMinSp(context)
        for ((glyph, sp, rowHeight) in rows) {
            val room = height - if (glyph) rowHeight + SMALL_GAP_DP else 0f
            for (lines in 1..3) {
                val label = TextFit.fit(context, texts.smallLabel, width, SMALL_LABEL_SP, minSp, maxLines = lines, semibold = true)
                    ?: continue
                if (label.heightDp <= room) return SmallFit(glyph, sp, label, fits = true)
            }
        }
        val label = TextFit.measure(context, texts.smallLabel, width, minSp, maxLines = 2, semibold = true)
        return SmallFit(glyph = false, countdownSp = null, label = label, fits = false)
    }

    /**
     * The first of [options] (a full text, then its shorter forms) that fits [widthDp] in up to [maxLines] lines,
     * fewest lines first, as [TextFit.fit] fits it.
     */
    private fun fitFirst(context: Context, options: List<String>, widthDp: Float, maxSp: Int, minSp: Int, maxLines: Int = 1): Fitted? =
        options.firstNotNullOfOrNull { TextFit.fit(context, it, widthDp, maxSp, minSp, maxLines = maxLines, fewerLinesFirst = true) }

    /** The smallest sp the 1×1 label may use: drawn at least [SMALL_LABEL_MIN_DP] tall at the current font scale. */
    fun smallLabelMinSp(context: Context): Int {
        val floorPx = SMALL_LABEL_MIN_DP * context.resources.displayMetrics.density
        return (1..SMALL_LABEL_SP).firstOrNull { TextFit.pxForSp(context, it) >= floorPx - 0.01f } ?: SMALL_LABEL_SP
    }

    private fun nowRow(context: Context, texts: WidgetTexts, layout: NextUpLayout, cell: CellDp, capsules: Boolean): NowFit {
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
            if (texts.countdownEnd != null && layout != NextUpLayout.Medium) add(NowSlot(width(true, true), height, glyph = true, countdown = true))
            add(NowSlot(width(true, false), height, glyph = true))
            add(NowSlot(width(false, false), height, secondaryMinSp = SECONDARY_LAST_RESORT_SP))
        }.filter { it.widthDp > 0f }
        val until = until(texts)
        // One-row cells keep to three lines; the ribbon's taller row may give "then …" its own line.
        val options = if (layout == NextUpLayout.Ribbon) {
            listOf(listOf(texts.subtitle), texts.subtitleLines, listOf(until))
        } else {
            listOf(listOf(texts.subtitle), listOf(until))
        }
        return fitNow(context, texts, slots, options.distinct())
    }

    private fun nowStack(
        context: Context,
        texts: WidgetTexts,
        cell: CellDp,
        budgetDp: Float,
        withThen: Boolean,
        countdownMinutes: Int?,
        glyphOptional: Boolean = true,
    ): NowFit {
        val width = cell.width - 2 * SURFACE_PAD_DP
        val countdown = texts.countdownEnd?.let { stackCountdown(context, width, countdownMinutes ?: WIDEST_COUNTDOWN_MINUTES) }
        val countdownHeight = countdown?.let { TextFit.measure(context, "0", 1000f, it.first).heightDp } ?: 0f
        val glyphRow = max(STACK_GLYPH_DP.toFloat(), countdownHeight) + LINE_GAP_DP
        val slots = buildList {
            add(
                NowSlot(
                    width, budgetDp, glyph = true, countdown = countdown != null, fixedDp = glyphRow,
                    countdownSp = countdown?.first ?: COUNTDOWN_SP, countdownCompact = countdown?.second ?: false,
                ),
            )
            if (glyphOptional) add(NowSlot(width, budgetDp, secondaryMinSp = SECONDARY_LAST_RESORT_SP))
        }
        val until = until(texts)
        val options = if (withThen) listOf(texts.subtitleLines, listOf(until)) else listOf(listOf(until))
        return fitNow(context, texts, slots, options.distinct())
    }

    /**
     * The countdown beside the stack's glyph, as its size and whether it is compact, or null when it cannot fit: the
     * widest text it shows before the next refresh ([HostText.countdownWidest]) at the largest size from
     * [COUNTDOWN_SP] down to [STACK_COUNTDOWN_MIN_SP], "2h 13m" before "2h13m" at each size. A 2-cell-wide stack has
     * room for neither "23h 59m" at 18 sp nor, at the default font scale, a 10-hour block's "10h59m".
     */
    private fun stackCountdown(context: Context, width: Float, minutes: Int): Pair<Int, Boolean>? {
        val room = width - STACK_GLYPH_DP - STACK_COUNTDOWN_GAP_DP
        for (sp in COUNTDOWN_SP downTo STACK_COUNTDOWN_MIN_SP) {
            for (compact in listOf(false, true)) {
                if (countdownTextDp(context, HostText.countdownWidest(minutes, compact), sp) <= room) return sp to compact
            }
        }
        return null
    }

    private fun nowCard(context: Context, texts: WidgetTexts, widthDp: Float, budgetDp: Float, withHeader: Boolean): NowFit {
        val header = fitHeader(context, texts.header?.takeIf { withHeader }, widthDp)
        val slots = listOfNotNull(
            header?.let { NowSlot(widthDp, budgetDp, header = it, fixedDp = it.heightDp + LINE_GAP_DP) },
            NowSlot(widthDp, budgetDp, secondaryMinSp = SECONDARY_LAST_RESORT_SP),
        )
        val until = until(texts)
        return fitNow(context, texts, slots, listOf(texts.subtitleLines, listOf(until)).distinct())
    }

    /** 2×3 / 4×3 Next up: the now stack first, then as many "Up next" rows as still fit whole. */
    private fun nextUpTall(context: Context, texts: WidgetTexts, cell: CellDp, countdownMinutes: Int?): WidgetFit {
        val total = cell.height - 2 * SURFACE_PAD_DP - doneDp(texts)
        val width = cell.width - 2 * SURFACE_PAD_DP
        val done = stackDone(context, texts, width)
        // Up next already says what comes next: the stack drops its "then …" line for the room.
        val withThen = texts.upcoming.isEmpty()
        // Rows go first, then the glyph and countdown; the now block keeps its until line and the other zone longest.
        for (glyphOptional in listOf(false, true)) {
            for (rows in min(UP_NEXT_ROWS, texts.upcoming.size) downTo 0) {
                val upNext = upNextRows(context, texts, width, rows, barWithRows = false, withRoute = true) ?: continue
                val upNextHeight = if (upNext.heightDp > 0f) upNext.heightDp + UP_NEXT_TOP_STACK_DP else 0f
                val now = nowStack(context, texts, cell, total - upNextHeight, withThen, countdownMinutes, glyphOptional)
                if (now.whole) return WidgetFit(now = now, upNext = upNext, done = done)
            }
        }
        return WidgetFit(now = nowStack(context, texts, cell, total, withThen, countdownMinutes), done = done)
    }

    /** 4×3 Two Clocks: header strip, the dial beside the now card, then as many "Up next" rows as still fit. */
    private fun twoClocksLarge(context: Context, texts: WidgetTexts, cell: CellDp): WidgetFit {
        val inner = cell.width - 2 * SURFACE_PAD_DP
        val stripWidth = inner - 2 * HEADER_STRIP_SIDE_DP
        val route = texts.route != null
        // The route goes first when the strip is too narrow for both, but comes back alone when no header fits.
        val besideRoute = if (route) fitHeader(context, texts.header, stripWidth - RouteWidthDp - HEADER_ROUTE_GAP_DP) else null
        val header = besideRoute ?: fitHeader(context, texts.header, stripWidth)
        val headerRoute = route && (besideRoute != null || header == null)
        val strip = if (header != null || headerRoute) {
            max(header?.heightDp ?: 0f, if (headerRoute) RouteHeightDp.toFloat() else 0f) + HEADER_STRIP_BOTTOM_DP
        } else {
            0f
        }
        val total = cell.height - 2 * SURFACE_PAD_DP - strip
        val cardWidth = cardWidthDp(TwoClocksLayout.Large, texts, cell)
        val done = clocksDone(context, texts, cell, total)
        // Rows go before the now card loses its until line or the other zone's time.
        for (rows in min(UP_NEXT_ROWS, texts.upcoming.size) downTo 0) {
            val upNext = upNextRows(context, texts, inner - 2 * UP_NEXT_SIDE_LARGE_DP, rows, barWithRows = true, withRoute = false) ?: continue
            val upNextHeight = if (upNext.heightDp > 0f) upNext.heightDp + UP_NEXT_TOP_LARGE_DP else 0f
            val now = nowCard(context, texts, cardWidth, total - upNextHeight - 2 * CARD_V_PAD_DP, withHeader = false)
            if (now.whole) {
                return WidgetFit(now = now, upNext = upNext, done = done, headerStrip = header, headerRoute = headerRoute)
            }
        }
        val now = nowCard(context, texts, cardWidth, total - 2 * CARD_V_PAD_DP, withHeader = false)
        return WidgetFit(now = now, done = done, headerStrip = header, headerRoute = headerRoute)
    }

    private fun caption(context: Context, texts: WidgetTexts, cell: CellDp): CaptionFit {
        val width = cell.width - 2 * SQUARE_DIAL_PAD_DP
        // The dial keeps its minimum; the caption gets the rest, the detail line going first.
        val room = cell.height - 2 * SQUARE_DIAL_PAD_DP - MIN_SQUARE_DIAL_DP
        val title = TextFit.fit(context, texts.dialTitle, width, CAPTION_SP, CAPTION_MIN_SP, maxLines = 2, semibold = true, fewerLinesFirst = true)
            ?.takeIf { it.heightDp + LINE_GAP_DP <= room }
            ?: return CaptionFit(null, null)
        val detail = texts.dialDetail?.let {
            TextFit.fit(context, it, width, CAPTION_DETAIL_SP, CAPTION_DETAIL_MIN_SP, maxLines = 2, fewerLinesFirst = true)
        }?.takeIf { title.heightDp + it.heightDp + 2 * LINE_GAP_DP <= room }
        return CaptionFit(title, detail)
    }

    /**
     * [rows] "Up next" rows, each whole (the label may wrap once); null when one of them cannot fit [widthDp]. The
     * adaptation bar shows with them when [barWithRows], and always when there are no rows (when its label fits).
     */
    private fun upNextRows(context: Context, texts: WidgetTexts, widthDp: Float, rows: Int, barWithRows: Boolean, withRoute: Boolean): UpNextFit? {
        val textWidth = widthDp - UP_NEXT_GLYPH_DP - UP_NEXT_GLYPH_GAP_DP
        val fitted = texts.upcoming.take(rows).map { item ->
            val line = TextFit.fit(context, "${item.time}  ${item.label}", textWidth, UP_NEXT_SP, UP_NEXT_MIN_SP, maxLines = 2, fewerLinesFirst = true)
                ?: return null
            val secondary = item.secondary?.let {
                fitFirst(context, item.secondaryOptions, textWidth, UP_NEXT_SECONDARY_SP, UP_NEXT_SECONDARY_MIN_SP, maxLines = 2) ?: return null
            }
            UpNextRowFit(item, line, secondary)
        }
        val bar = if ((barWithRows || fitted.isEmpty()) && texts.adaptation != null && texts.adaptationLabel != null) {
            TextFit.fit(context, adaptationText(texts), widthDp, ADAPTATION_SP, ADAPTATION_MIN_SP, maxLines = 2, fewerLinesFirst = true)
        } else {
            null
        }
        val title = if (fitted.isNotEmpty()) {
            TextFit.measure(context, context.getString(R.string.widget_up_next), widthDp, UP_NEXT_TITLE_SP, semibold = true)
        } else {
            null
        }
        // The route sits at the end of the title row: only where both fit side by side.
        val route = withRoute && title != null && texts.route != null &&
            title.widthDp + UP_NEXT_ROUTE_GAP_DP + RouteWidthDp <= widthDp - TextFit.SAFETY_DP
        val heights = buildList {
            title?.let { add(max(it.heightDp, if (route) RouteHeightDp.toFloat() else 0f)) }
            fitted.forEach { add(it.heightDp) }
            bar?.let {
                add(ADAPTATION_TOP_DP + it.heightDp)
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
                    fitFirst(context, item.secondaryOptions, textWidth, CAPSULE_SECONDARY_SP, CAPSULE_SECONDARY_MIN_SP) ?: return@map null
                }
                UpNextRowFit(item, line, secondary).takeIf { line.heightDp + (secondary?.heightDp ?: 0f) <= CAPSULE_HEIGHT_DP }
            }
            if (fitted.all { it != null }) return UpNextFit(fitted.filterNotNull(), capsules = true, heightDp = CAPSULE_HEIGHT_DP.toFloat())
        }
        return UpNextFit.None
    }

    /** Done beside a one-row Next up: a weighted column, padded, as tall as the row. */
    private fun rowDone(context: Context, texts: WidgetTexts, layout: NextUpLayout, cell: CellDp, rowHeight: Float): Fitted? {
        if (layout == NextUpLayout.Medium) return null
        val width = cell.width * DONE_WEIGHT / (1f + DONE_WEIGHT) - DONE_COLUMN_PAD_DP
        val bottom = if (layout == NextUpLayout.Ribbon) DONE_COLUMN_PAD_DP / 2 else DONE_COLUMN_PAD_DP
        return fitDone(context, texts, width, rowHeight - DONE_COLUMN_PAD_DP - bottom)
    }

    /** Done under a stack: full width, a 48 dp button. */
    private fun stackDone(context: Context, texts: WidgetTexts, widthDp: Float): Fitted? =
        fitDone(context, texts, widthDp, DONE_TOUCH_DP.toFloat())

    /** Done beside the Two Clocks dial and card: a weighted column with a start gap. */
    private fun clocksDone(context: Context, texts: WidgetTexts, cell: CellDp, heightDp: Float): Fitted? {
        val inner = cell.width - 2 * SURFACE_PAD_DP
        return fitDone(context, texts, inner * DONE_WEIGHT / (1f + DONE_WEIGHT) - CLOCKS_DONE_GAP_DP, heightDp)
    }

    private fun clocksMainWidth(texts: WidgetTexts, cell: CellDp): Float {
        val inner = cell.width - 2 * SURFACE_PAD_DP
        return if (texts.done != null) inner / (1f + DONE_WEIGHT) else inner
    }

    private fun until(texts: WidgetTexts) = texts.subtitleLines.firstOrNull() ?: texts.subtitle

    private fun doneDp(texts: WidgetTexts) = if (texts.done != null) DONE_HEIGHT_DP else 0

    /** "6% adapted · −7 h" above the adaptation bar; "−7 h" never splits across lines. */
    fun adaptationText(texts: WidgetTexts): String =
        listOfNotNull(texts.adaptationLabel, texts.misalignment?.replace(' ', '\u00A0')).joinToString(" · ")
}
