package dev.sebastiano.clockblocker.opus.widget.rc

import android.content.Context
import dev.sebastiano.clockblocker.opus.widget.R
import dev.sebastiano.clockblocker.opus.widget.text.Fitted
import dev.sebastiano.clockblocker.opus.widget.text.TextFit
import dev.sebastiano.clockblocker.opus.widget.text.UpcomingText
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTexts
import kotlin.math.ceil
import kotlin.math.max

/**
 * The shade's custom-content slots for the Now notification (`DecoratedCustomViewStyle`, #49): 48 dp tall collapsed,
 * at most 252 dp expanded, as wide as the shade leaves beside the app icon (and, collapsed, the expand button).
 */
data class NotificationSlots(val collapsedWidthDp: Int, val expandedWidthDp: Int) {
    companion object {
        const val COLLAPSED_HEIGHT_DP = 48
        const val EXPANDED_MAX_HEIGHT_DP = 252

        /**
         * The slots on a screen [screenWidthDp] wide. Measured on a Pixel 11 Pro (API 37.0, 410 dp): the shade card
         * keeps 16 dp each side, the content starts 69 dp into it and ends 15 dp before its edge (50 dp collapsed,
         * beside the expand button), so 260 dp collapsed and 294 dp expanded.
         */
        fun forScreenWidth(screenWidthDp: Int): NotificationSlots =
            NotificationSlots(screenWidthDp - COLLAPSED_INSET_DP, screenWidthDp - EXPANDED_INSET_DP)

        private const val COLLAPSED_INSET_DP = 150
        private const val EXPANDED_INSET_DP = 116
    }
}

/**
 * The collapsed view: glyph (when it fits), label, and the until line with the other zone's time. No Done: the
 * notification's own actions (Done, Can't do this, Snooze) sit under it.
 */
data class CollapsedNotificationFit(val glyph: Boolean, val title: Fitted, val line: Fitted?)

/** What starts next, glyph beside its label: "Next: Sleep at 20:30 · 12:30 in Lisbon". */
data class NextFit(val item: UpcomingText, val text: Fitted)

/**
 * The expanded view, top to bottom: glyph + label + until line, the block's live progress with its countdown, and what
 * starts next. [heightDp] is what it takes (it wraps its content; the shade caps it at 252 dp).
 */
data class ExpandedNotificationFit(
    val title: Fitted,
    val line: Fitted?,
    val progress: Boolean,
    val countdownSp: Int?,
    val countdownSlotDp: Int,
    val next: NextFit?,
    val heightDp: Float,
)

/**
 * Capture-time fitting for the notification documents ([NowNotificationCollapsedRemote],
 * [NowNotificationExpandedRemote]), with the widgets' [TextFit]: sizes and line counts are picked so nothing is
 * ellipsized on the host. Notification-sized type: 16 sp labels and 14 sp lines, like the shade's own text.
 */
internal object NotificationFit {
    const val TITLE_SP = 16
    const val TITLE_MIN_SP = 13
    const val LINE_SP = 14
    const val COLLAPSED_LINE_SP = 13
    const val LINE_MIN_SP = 11
    const val COUNTDOWN_SP = 14

    /**
     * The until line, longest first: "until 20:30 · 12:30 in Lisbon", then shorter place names, then the code form
     * ("until 20:30 · 12:30 LIS"). Never the local time alone: with a secondary zone every form keeps its time
     * (product rule), and the line is left out when none fits. Without a secondary zone it is "until 20:30"; without
     * a block, the subtitle ("You're adapted to Tokyo", "Plan one").
     *
     * Once the block is logged, each form starts with what was logged ("✓ Done · until 20:30 · 12:30 in Lisbon"), as
     * the classic Now notification's line did; the plain forms follow, for when no logged form fits (the
     * notification's Undo action still says it).
     */
    fun untilOptions(context: Context, texts: WidgetTexts): List<String> {
        val until = texts.subtitleLines.firstOrNull() ?: return emptyList()
        val plain = if (texts.secondaryOptions.isEmpty()) {
            listOf(until)
        } else {
            val withZone = texts.secondaryOptions.map { context.getString(R.string.widget_until_compact, until.glued(), it.glued()) }
            (withZone + listOfNotNull(texts.untilCompact)).distinct()
        }
        val logged = texts.done?.takeIf { it.logged != null }?.label ?: return plain
        return plain.map { context.getString(R.string.widget_until_compact, logged.glued(), it) } + plain
    }

    /**
     * "Next: Sleep at 20:30 · 12:30 in Lisbon", then shorter places. Like [untilOptions], never without the other zone's
     * time when there is one (the row is left out instead). Without [time], just "Next: Sleep": in free time the until
     * line above already gives its start in both zones.
     */
    fun nextOptions(context: Context, item: UpcomingText, time: Boolean = true): List<String> {
        if (!time) return listOf(context.getString(R.string.widget_notification_next, item.label))
        val at = context.getString(R.string.widget_starts_at, item.label, item.time.glued())
        val forms = if (item.secondaryOptions.isEmpty()) {
            listOf(at)
        } else {
            item.secondaryOptions.map { context.getString(R.string.widget_until_compact, at, it.glued()) }
        }
        return forms.map { context.getString(R.string.widget_notification_next, it) }
    }

    fun collapsed(context: Context, texts: WidgetTexts, widthDp: Int): CollapsedNotificationFit {
        val lines = untilOptions(context, texts)
        val available = (NotificationSlots.COLLAPSED_HEIGHT_DP - 2 * COLLAPSED_V_PAD_DP).toFloat()
        for (glyph in listOf(true, false)) {
            val textWidth = (widthDp - if (glyph) COLLAPSED_GLYPH_DP + COLLAPSED_GLYPH_GAP_DP else 0).toFloat()
            val title = TextFit.fit(context, texts.title, textWidth, TITLE_SP, TITLE_MIN_SP, semibold = true) ?: continue
            // The until line shrinks before the label does (at large font scales the label gives up some size so both
            // fit the row's height), and goes before the glyph does.
            val line = firstFit(context, lines, textWidth, COLLAPSED_LINE_SP, LINE_MIN_SP, maxLines = 1) ?: return CollapsedNotificationFit(glyph, title, null)
            val titles = (title.sp downTo TITLE_MIN_SP).asSequence().mapNotNull { sp ->
                TextFit.fit(context, texts.title, textWidth, sp, TITLE_MIN_SP, semibold = true)
            }
            val both = titles.firstOrNull { it.heightDp + LINE_GAP_DP + line.heightDp <= available }
            return if (both != null) CollapsedNotificationFit(glyph, both, line) else CollapsedNotificationFit(glyph, title, null)
        }
        // A single word wider than the slot at the smallest size: ellipsized rather than broken.
        return CollapsedNotificationFit(false, TextFit.measure(context, texts.title, widthDp.toFloat(), TITLE_MIN_SP, semibold = true), null)
    }

    fun expanded(context: Context, texts: WidgetTexts, widthDp: Int, progress: Boolean): ExpandedNotificationFit {
        val textWidth = (widthDp - EXPANDED_GLYPH_DP - EXPANDED_GLYPH_GAP_DP).toFloat()
        val title = TextFit.fit(context, texts.title, textWidth, TITLE_SP, TITLE_MIN_SP, maxLines = 2, semibold = true)
            ?: TextFit.measure(context, texts.title, textWidth, TITLE_MIN_SP, maxLines = 2, semibold = true)
        val line = firstFit(context, untilOptions(context, texts), textWidth, LINE_SP, LINE_MIN_SP, maxLines = 2)
        val countdown = texts.countdownEnd?.let {
            val widest = HostText.countdownWidest(WIDEST_COUNTDOWN_MINUTES)
            TextFit.widthDp(context, widest, COUNTDOWN_SP, TextFit.MEDIUM) + TextFit.SAFETY_DP
        }
        val countdownSlot = countdown?.let { ceil(it).toInt() + COUNTDOWN_GAP_DP } ?: 0
        val countdownHeight = if (countdown != null) TextFit.measure(context, "0", widthDp.toFloat(), COUNTDOWN_SP).heightDp else 0f
        val nextItem = texts.upcoming.firstOrNull()
        val next = nextItem?.let { item ->
            firstFit(context, nextOptions(context, item, time = progress), (widthDp - NEXT_GLYPH_DP - NEXT_GLYPH_GAP_DP).toFloat(), LINE_SP, LINE_MIN_SP, maxLines = 2)
                ?.let { NextFit(item, it) }
        }
        val header = max(EXPANDED_GLYPH_DP.toFloat(), title.heightDp + (line?.let { LINE_GAP_DP + it.heightDp } ?: 0f))
        val progressHeight = if (progress) max(PROGRESS_BAR_DP.toFloat(), countdownHeight) + PROGRESS_TOP_DP else 0f
        fun nextHeight(next: NextFit?) = next?.let { max(NEXT_GLYPH_DP.toFloat(), it.text.heightDp) + NEXT_TOP_DP } ?: 0f
        val withNext = header + progressHeight + nextHeight(next) + EXPANDED_BOTTOM_DP
        // What starts next gives way first when the shade's cap would clip the view (large font scales).
        val keepNext = withNext <= NotificationSlots.EXPANDED_MAX_HEIGHT_DP
        val height = if (keepNext) withNext else withNext - nextHeight(next)
        return ExpandedNotificationFit(
            title = title,
            line = line,
            progress = progress,
            countdownSp = COUNTDOWN_SP.takeIf { countdown != null && progress },
            countdownSlotDp = if (progress) countdownSlot else 0,
            next = next?.takeIf { keepNext },
            heightDp = height,
        )
    }

    private fun firstFit(context: Context, options: List<String>, widthDp: Float, maxSp: Int, minSp: Int, maxLines: Int): Fitted? {
        // Every form at the largest size first: a shorter place beats a smaller size.
        for (sp in maxSp downTo minSp) {
            for (text in options) {
                val fitted = TextFit.measure(context, text, widthDp, sp, maxLines)
                if (fitted.fits) return fitted
            }
        }
        return null
    }

    /**
     * [this] with no-break spaces, so a two-line text wraps after a "·", never inside "08:30 in Lisbon" (or "until
     * 16:30"), like the classic Now notification's lines.
     */
    private fun String.glued(): String = replace(' ', '\u00A0')

    /** "23h 59m": the widest countdown a block can show. */
    private const val WIDEST_COUNTDOWN_MINUTES = 23 * 60 + 59
}

internal const val COLLAPSED_V_PAD_DP = 2
internal const val COLLAPSED_GLYPH_DP = 28
internal const val COLLAPSED_GLYPH_GAP_DP = 10
internal const val EXPANDED_GLYPH_DP = 40
internal const val EXPANDED_GLYPH_GAP_DP = 14
internal const val EXPANDED_BOTTOM_DP = 4
internal const val PROGRESS_TOP_DP = 14
internal const val PROGRESS_BAR_DP = 6
internal const val NEXT_TOP_DP = 12
internal const val NEXT_GLYPH_DP = 24
internal const val NEXT_GLYPH_GAP_DP = 10
