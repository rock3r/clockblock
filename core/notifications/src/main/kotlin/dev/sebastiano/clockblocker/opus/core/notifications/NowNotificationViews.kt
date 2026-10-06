package dev.sebastiano.clockblocker.opus.core.notifications

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowState
import dev.sebastiano.clockblocker.opus.core.notifications.text.NotificationText
import dev.sebastiano.clockblocker.opus.core.notifications.text.ZoneTime
import java.time.Duration
import java.time.Instant

/**
 * The custom content of the Now notification (inside `DecoratedCustomViewStyle`, so the system still draws the app
 * icon, the header with the body clock, the expand button and the actions). Instead of a wall of text it gives the
 * current advice a glyph on its colour chip, a clear label, "until" with the other zone as a tail, and a bar for how
 * far through the block you are; the expanded view adds what runs alongside, what starts next and a tip.
 *
 * Text uses the shade's own notification text appearances (so it follows dark mode and the font scale); the chips
 * and the bar get light and dark colours, and the shade picks the pair for its theme (API 31+ night-aware
 * `RemoteViews` colours). Glyphs are decorative for TalkBack: every one sits next to its text label.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal object NowNotificationViews {

    /**
     * One line plus the progress bar (or, in a gap, the "Next" line): the platform caps this view at 48dp. No chip:
     * at large font sizes the label needs the room ("See some light" truncated beside one at 1.3×).
     */
    fun collapsed(context: Context, state: NowState, text: NotificationText, now: Instant): RemoteViews =
        RemoteViews(context.packageName, R.layout.notif_now_collapsed).apply {
            setTextViewText(R.id.now_title, text.title)
            val progress = progressOf(state, now)
            if (progress == null) {
                setViewVisibility(R.id.now_until, View.GONE)
                setViewVisibility(R.id.now_progress, View.GONE)
                setViewVisibility(R.id.now_text, View.VISIBLE)
                // Local time only, like "until" above: the zone tail waits for the expanded view.
                setTextViewText(R.id.now_text, text.text)
            } else {
                setTextViewText(R.id.now_until, text.text)
                progress(R.id.now_progress, state.headline?.type, progress)
            }
        }

    /** Label, until (with the zone tail), progress, alongside, next and the tip, in reading order. */
    fun expanded(context: Context, state: NowState, text: NotificationText, now: Instant): RemoteViews =
        RemoteViews(context.packageName, R.layout.notif_now_expanded).apply {
            chip(R.id.now_chip, R.id.now_glyph, state.headline?.type)
            setTextViewText(R.id.now_title, text.title)
            // Lines wrap after a time's "·", never inside "11:00 Los Angeles" or before the dot.
            setTextViewText(R.id.now_line, text.line.wrappingAfterSeparators(text.zoneTimes))
            val progress = progressOf(state, now)
            if (progress == null) {
                setViewVisibility(R.id.now_progress, View.GONE)
            } else {
                progress(R.id.now_progress, state.headline?.type, progress)
            }
            row(R.id.now_also_row, R.id.now_also_chip, R.id.now_also_glyph, R.id.now_also, text.also?.wrappingAfterSeparators(text.zoneTimes), state.alongside.firstOrNull())
            // In a gap the main line already says what's next.
            row(R.id.now_next_row, R.id.now_next_chip, R.id.now_next_glyph, R.id.now_next, text.next?.wrappingAfterSeparators(text.zoneTimes), state.next)
            text.tip?.let {
                setViewVisibility(R.id.now_tip, View.VISIBLE)
                setTextViewText(R.id.now_tip, it)
            }
        }

    /** How far through the headline block [now] is, in thousandths; `null` in a gap. */
    internal fun progressOf(state: NowState, now: Instant): Int? {
        val headline = state.headline ?: return null
        val until = state.until ?: return null
        val total = Duration.between(headline.start, until).toMillis().coerceAtLeast(1)
        val elapsed = Duration.between(headline.start, now).toMillis().coerceIn(0, total)
        return (elapsed * PROGRESS_MAX / total).toInt()
    }

    private fun RemoteViews.row(@IdRes row: Int, @IdRes chip: Int, @IdRes glyph: Int, @IdRes textId: Int, line: String?, advice: Advice?) {
        if (line == null || advice == null) return
        setViewVisibility(row, View.VISIBLE)
        chip(chip, glyph, advice.type)
        setTextViewText(textId, line)
    }

    private fun RemoteViews.chip(@IdRes chip: Int, @IdRes glyph: Int, type: AdviceType?) {
        val light = NotificationPalette.of(type, dark = false)
        val dark = NotificationPalette.of(type, dark = true)
        setImageViewResource(glyph, type?.style?.icon ?: R.drawable.ic_notif_clock)
        tint(chip, "setImageTintList", light.container, dark.container)
        tint(glyph, "setImageTintList", light.onContainer, dark.onContainer)
    }

    private fun RemoteViews.progress(@IdRes id: Int, type: AdviceType?, progress: Int) {
        val light = NotificationPalette.of(type, dark = false)
        val dark = NotificationPalette.of(type, dark = true)
        setProgressBar(id, PROGRESS_MAX, progress, false)
        tint(id, "setProgressTintList", light.mark, dark.mark)
        tint(id, "setProgressBackgroundTintList", light.container, dark.container)
    }

    private fun RemoteViews.tint(@IdRes id: Int, method: String, light: Int, dark: Int) =
        setColorStateList(id, method, ColorStateList.valueOf(light), ColorStateList.valueOf(dark))

    /**
     * "Wed 02:00 · 18:00 Los Angeles" may only wrap after the dot: the local time stays whole and keeps its dot,
     * and the other zone's time and city stay together on the next line.
     */
    private fun String.wrappingAfterSeparators(times: List<ZoneTime>) = times.fold(this) { line, time ->
        val separator = time.joined.removePrefix(time.local).removeSuffix(time.other)
        val glued = time.local.unbreakable() + separator.trimEnd().unbreakable() + separator.takeLastWhile { it == ' ' } +
            time.other.unbreakable()
        line.replace(time.joined, glued)
    }

    private fun String.unbreakable() = replace(' ', '\u00A0')

    const val PROGRESS_MAX = 1000
}
