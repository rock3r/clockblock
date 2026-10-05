package dev.sebastiano.clockblocker.opus.widget.legacy

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import dev.sebastiano.clockblocker.opus.widget.R
import dev.sebastiano.clockblocker.opus.widget.draw.CanvasOps
import dev.sebastiano.clockblocker.opus.widget.draw.Glyphs
import dev.sebastiano.clockblocker.opus.widget.draw.TwoClocksDial
import dev.sebastiano.clockblocker.opus.widget.rc.DeepLinkIntents
import dev.sebastiano.clockblocker.opus.widget.rc.NextUpLayout
import dev.sebastiano.clockblocker.opus.widget.rc.WidgetModel
import dev.sebastiano.clockblocker.opus.widget.state.DialMath
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.text.TextFit
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

/**
 * Classic RemoteViews for API 29–35 and for hosts without a Remote Compose player. The dial is a small
 * Canvas-drawn bitmap (same display list as the Remote Compose version, hand frozen at render time); local and
 * body time keep ticking in the launcher via `TextClock` with a fixed time zone, the countdown via `Chronometer`.
 *
 * Bitmaps are capped ([MAX_DIAL_PX], [MAX_GLYPH_PX]): apps targeting Android 17 get a strictly enforced
 * RemoteViews bitmap memory limit.
 */
object LegacyRemoteViews {
    const val MAX_DIAL_PX = 400
    const val MAX_GLYPH_PX = 144

    /** Must match the 1×1 glyph view in `widget_next_up_small_legacy.xml`. */
    private const val SMALL_GLYPH_DP = 22

    fun twoClocks(context: Context, model: WidgetModel, dialSizePx: Int, now: Instant = Instant.now()): RemoteViews {
        val p = model.palette
        val texts = model.texts
        return RemoteViews(context.packageName, R.layout.widget_two_clocks_legacy).apply {
            setInt(android.R.id.background, "setBackgroundResource", background(model))
            setImageViewBitmap(R.id.dial, dialBitmap(model, dialSizePx, now))
            setTextViewText(R.id.dial_line, texts.dialTitle)
            setTextColor(R.id.dial_line, p.onSurface)
            setTextViewText(R.id.dial_detail, texts.dialDetail.orEmpty())
            setTextColor(R.id.dial_detail, p.onSurfaceVariant)
            setViewVisibility(R.id.dial_detail, if (texts.dialDetail != null) View.VISIBLE else View.GONE)
            setTextColor(R.id.local_clock, p.onSurface)
            setTextColor(R.id.body_clock, p.onSurfaceVariant)
            setTextColor(R.id.misalignment, p.primary)
            // Inside the ring: capped font scale, or the clock runs into the arcs (see TextFit.dialSp).
            setTextViewTextSize(R.id.local_clock, TypedValue.COMPLEX_UNIT_SP, TextFit.dialSp(context, 20).toFloat())
            setTextViewTextSize(R.id.body_clock, TypedValue.COMPLEX_UNIT_SP, TextFit.dialSp(context, 10).toFloat())
            setTextViewTextSize(R.id.misalignment, TypedValue.COMPLEX_UNIT_SP, TextFit.dialSp(context, 10).toFloat())
            when (val state = model.state) {
                is WidgetState.Active -> {
                    setString(R.id.local_clock, "setTimeZone", state.displayZoneId)
                    setCharSequence(R.id.local_clock, "setFormat24Hour", "HH:mm")
                    setString(R.id.body_clock, "setTimeZone", gmtId(state.bodyOffsetMinutes))
                    setCharSequence(R.id.body_clock, "setFormat24Hour", "'${texts.bodyPrefix}' HH:mm")
                    setCharSequence(R.id.body_clock, "setFormat12Hour", "'${texts.bodyPrefix}' h:mm a")
                    setViewVisibility(R.id.local_clock, View.VISIBLE)
                    setViewVisibility(R.id.body_clock, View.VISIBLE)
                    setTextViewText(R.id.misalignment, texts.misalignment.orEmpty())
                    setViewVisibility(R.id.misalignment, if (texts.misalignment != null) View.VISIBLE else View.GONE)
                }
                WidgetState.NoTrip -> {
                    setViewVisibility(R.id.local_clock, View.GONE)
                    setViewVisibility(R.id.body_clock, View.GONE)
                    setTextViewText(R.id.misalignment, texts.subtitle)
                    setViewVisibility(R.id.misalignment, View.VISIBLE)
                }
            }
            setContentDescription(android.R.id.background, texts.contentDescription)
            setOnClickPendingIntent(android.R.id.background, DeepLinkIntents.pendingIntent(context, texts.deepLink))
        }
    }

    fun nextUp(
        context: Context,
        model: WidgetModel,
        layout: NextUpLayout,
        density: Float,
        now: Instant = Instant.now(),
    ): RemoteViews {
        val p = model.palette
        val texts = model.texts
        val small = layout == NextUpLayout.Small
        val res = if (small) R.layout.widget_next_up_small_legacy else R.layout.widget_next_up_legacy
        val glyphPx = ((if (small) SMALL_GLYPH_DP else 40) * density).toInt().coerceIn(24, MAX_GLYPH_PX)
        return RemoteViews(context.packageName, res).apply {
            setInt(android.R.id.background, "setBackgroundResource", background(model))
            setImageViewBitmap(R.id.glyph, glyphBitmap(model, glyphPx))
            setTextViewText(R.id.title, texts.title)
            setTextColor(R.id.title, p.onSurface)
            if (small) {
                // Same rule as the Remote Compose 1×1: no single word may break ("Clockblocked").
                setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, TextFit.smallLabelSp(context, texts.title).toFloat())
            }
            val millisLeft = texts.countdownEnd?.let { Duration.between(now, it).toMillis() }?.takeIf { it > 0 }
            val showCountdown = millisLeft != null && layout != NextUpLayout.Medium
            if (showCountdown) {
                setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + millisLeft!!, null, true)
                setChronometerCountDown(R.id.countdown, true)
                setTextColor(R.id.countdown, p.primary)
                if (small) {
                    // Chronometer shows H:MM:SS (MM:SS under an hour); size for the widest it gets beside the glyph.
                    val hours = millisLeft / 3_600_000
                    val widest = if (hours == 0L) "59:59" else "$hours:59:59"
                    setTextViewTextSize(
                        R.id.countdown,
                        TypedValue.COMPLEX_UNIT_SP,
                        TextFit.smallCountdownSp(context, widest, glyphDp = SMALL_GLYPH_DP, maxSp = 11).toFloat(),
                    )
                }
            }
            setViewVisibility(R.id.countdown, if (showCountdown) View.VISIBLE else View.GONE)
            // The label always stays: never an icon (or a bare countdown) alone.
            if (!small) {
                setTextViewText(R.id.subtitle, texts.subtitle)
                setTextColor(R.id.subtitle, p.onSurfaceVariant)
                val secondary = texts.secondary.takeIf { layout == NextUpLayout.Wide }
                setTextViewText(R.id.secondary, secondary.orEmpty())
                setTextColor(R.id.secondary, p.onSurfaceVariant)
                setViewVisibility(R.id.secondary, if (secondary != null) View.VISIBLE else View.GONE)
            }
            setContentDescription(android.R.id.background, "${texts.title}. ${texts.subtitle}")
            setOnClickPendingIntent(android.R.id.background, DeepLinkIntents.pendingIntent(context, texts.deepLink))
        }
    }

    /** The dial bitmap: shared display list plus a hand frozen at [now]. */
    fun dialBitmap(model: WidgetModel, sizePx: Int, now: Instant = Instant.now()): Bitmap {
        val size = sizePx.coerceIn(64, MAX_DIAL_PX)
        val ops = when (val state = model.state) {
            is WidgetState.Active ->
                TwoClocksDial.build(state, model.palette) +
                    TwoClocksDial.hand(DialMath.minuteOfDay(now, state.displayOffsetMinutes).toFloat(), model.palette)
            WidgetState.NoTrip -> TwoClocksDial.empty(model.palette)
        }
        return CanvasOps.bitmap(ops, size, paddingPx = size * 0.01f)
    }

    fun glyphBitmap(model: WidgetModel, sizePx: Int): Bitmap =
        CanvasOps.bitmap(Glyphs.build(model.texts.glyph, model.palette), sizePx.coerceAtMost(MAX_GLYPH_PX))

    private fun background(model: WidgetModel) =
        if (model.palette.isDark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light

    /** Fixed-offset TimeZone id understood by TextClock, e.g. "GMT+05:12", "GMT-03:00". */
    fun gmtId(offsetMinutes: Int): String {
        val sign = if (offsetMinutes < 0) '-' else '+'
        val m = abs(offsetMinutes)
        return String.format(Locale.ROOT, "GMT%c%02d:%02d", sign, m / 60, m % 60)
    }
}
