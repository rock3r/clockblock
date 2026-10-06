package dev.sebastiano.clockblocker.opus.widget.legacy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationIntents
import dev.sebastiano.clockblocker.opus.widget.R
import dev.sebastiano.clockblocker.opus.widget.draw.CanvasOps
import dev.sebastiano.clockblocker.opus.widget.draw.GlyphKind
import dev.sebastiano.clockblocker.opus.widget.draw.Glyphs
import dev.sebastiano.clockblocker.opus.widget.draw.RouteStrip
import dev.sebastiano.clockblocker.opus.widget.draw.TwoClocksDial
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.rc.DeepLinkIntents
import dev.sebastiano.clockblocker.opus.widget.rc.NextUpLayout
import dev.sebastiano.clockblocker.opus.widget.rc.MAX_THREE_LINE_FONT_SCALE
import dev.sebastiano.clockblocker.opus.widget.rc.UP_NEXT_ROWS
import dev.sebastiano.clockblocker.opus.widget.rc.TwoClocksLayout
import dev.sebastiano.clockblocker.opus.widget.rc.WidgetModel
import dev.sebastiano.clockblocker.opus.widget.state.DialMath
import dev.sebastiano.clockblocker.opus.widget.state.WidgetRoute
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.tintType
import dev.sebastiano.clockblocker.opus.widget.text.TextFit
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Classic RemoteViews for API 29–35 and for hosts without a Remote Compose player, with the same size buckets as the
 * Remote Compose documents ([TwoClocksLayout], [NextUpLayout]). The dial is a small Canvas-drawn bitmap (same display
 * list as the Remote Compose version, hand frozen at render time); local and body time keep ticking in the launcher
 * via `TextClock` with a fixed time zone, the countdown via `Chronometer`.
 *
 * Colours that depend on the plan (tinted backgrounds, cards, the Done button) are white shapes tinted with
 * `ImageView.setColorFilter`, which works on every supported API level.
 *
 * Bitmaps are capped ([MAX_DIAL_PX], [MAX_GLYPH_PX]): apps targeting Android 17 get a strictly enforced
 * RemoteViews bitmap memory limit.
 */
object LegacyRemoteViews {
    const val MAX_DIAL_PX = 400
    const val MAX_GLYPH_PX = 144

    /** Must match the 1×1 glyph view in `widget_next_up_small_legacy.xml`. */
    private const val SMALL_GLYPH_DP = 22

    private const val CARD_TINT = 0.6f
    private const val NEXT_UP_TINT = 0.35f

    fun twoClocks(context: Context, model: WidgetModel, layout: TwoClocksLayout, dial: Bitmap): RemoteViews {
        val p = model.palette
        val texts = model.texts
        val active = model.state is WidgetState.Active
        // Without a plan every size shows the dial with "No trip / Plan one" (as on Remote Compose); 1×1 inside the ring.
        val effective = if (active || layout == TwoClocksLayout.Compact) layout else TwoClocksLayout.Square
        val wide = effective == TwoClocksLayout.Wide || effective == TwoClocksLayout.Large
        val res = if (wide) R.layout.widget_two_clocks_wide_legacy else R.layout.widget_two_clocks_legacy
        return RemoteViews(context.packageName, res).apply {
            tintBackground(p.surface)
            setImageViewBitmap(R.id.dial, dial)
            clocks(context, model, effective)
            if (!wide) {
                val captions = effective == TwoClocksLayout.Square
                setTextViewText(R.id.dial_line, texts.dialTitle)
                setTextColor(R.id.dial_line, p.onSurface)
                setViewVisibility(R.id.dial_line, if (captions) View.VISIBLE else View.GONE)
                setTextViewText(R.id.dial_detail, texts.dialDetail.orEmpty())
                setTextColor(R.id.dial_detail, p.onSurfaceVariant)
                setViewVisibility(R.id.dial_detail, if (captions && texts.dialDetail != null) View.VISIBLE else View.GONE)
            }
            val card = effective == TwoClocksLayout.Tall || wide
            val large = effective == TwoClocksLayout.Large
            if (card) nowCard(model, withHeader = !large) else if (!wide) setViewVisibility(R.id.card, View.GONE)
            done(context, model, show = card)
            if (wide) {
                // 4×3: the header moves out of the card into a full-width strip with the route (as on Remote Compose).
                headerStrip(context, model, show = large)
                upNext(context, model, show = large)
            }
            setContentDescription(R.id.main, texts.contentDescription)
            setOnClickPendingIntent(R.id.main, DeepLinkIntents.pendingIntent(context, texts.deepLink))
        }
    }

    private fun RemoteViews.clocks(context: Context, model: WidgetModel, layout: TwoClocksLayout) {
        val p = model.palette
        val texts = model.texts
        val (clockSp, smallSp) = when (layout) {
            TwoClocksLayout.Compact -> 15 to 9
            TwoClocksLayout.Wide, TwoClocksLayout.Large -> 24 to 12
            else -> 20 to 10
        }
        setTextColor(R.id.local_clock, p.onSurface)
        setTextColor(R.id.body_clock, p.onSurfaceVariant)
        setTextColor(R.id.misalignment, p.primary)
        // Inside the ring: capped font scale, or the clock runs into the arcs (see TextFit.dialSp).
        setTextViewTextSize(R.id.local_clock, TypedValue.COMPLEX_UNIT_SP, TextFit.dialSp(context, clockSp).toFloat())
        setTextViewTextSize(R.id.body_clock, TypedValue.COMPLEX_UNIT_SP, TextFit.dialSp(context, smallSp).toFloat())
        setTextViewTextSize(R.id.misalignment, TypedValue.COMPLEX_UNIT_SP, TextFit.dialSp(context, smallSp).toFloat())
        when (val state = model.state) {
            is WidgetState.Active -> {
                setString(R.id.local_clock, "setTimeZone", state.displayZoneId)
                setCharSequence(R.id.local_clock, "setFormat24Hour", "HH:mm")
                setString(R.id.body_clock, "setTimeZone", gmtId(state.bodyOffsetMinutes))
                setCharSequence(R.id.body_clock, "setFormat24Hour", "'${texts.bodyPrefix}' HH:mm")
                setCharSequence(R.id.body_clock, "setFormat12Hour", "'${texts.bodyPrefix}' h:mm a")
                setViewVisibility(R.id.local_clock, View.VISIBLE)
                // The 1×1 dial keeps local time and the jet lag label; the body readout needs more room.
                setViewVisibility(R.id.body_clock, if (layout == TwoClocksLayout.Compact) View.GONE else View.VISIBLE)
                setTextViewText(R.id.misalignment, texts.misalignment.orEmpty())
                setViewVisibility(R.id.misalignment, if (texts.misalignment != null) View.VISIBLE else View.GONE)
            }
            WidgetState.NoTrip -> {
                setViewVisibility(R.id.local_clock, View.GONE)
                setViewVisibility(R.id.body_clock, View.GONE)
                // The 1×1 has no caption below the dial, so the ring says both.
                setTextViewText(R.id.misalignment, if (layout == TwoClocksLayout.Compact) "${texts.title}\n${texts.subtitle}" else texts.subtitle)
                setViewVisibility(R.id.misalignment, View.VISIBLE)
            }
        }
    }

    /** "Tokyo · Day 2", label, until / then and the secondary zone, on a card tinted towards the current advice. */
    private fun RemoteViews.nowCard(model: WidgetModel, withHeader: Boolean = true) {
        val p = model.palette
        val texts = model.texts
        setViewVisibility(R.id.card, View.VISIBLE)
        setInt(R.id.card_bg, "setColorFilter", p.card(model.state.tintType, CARD_TINT))
        text(R.id.card_header, texts.header?.takeIf { withHeader }, p.onSurfaceVariant)
        text(R.id.card_title, texts.title, p.onSurface)
        text(R.id.card_line1, texts.subtitleLines.getOrNull(0), p.onSurfaceVariant)
        text(R.id.card_line2, texts.subtitleLines.getOrNull(1), p.onSurfaceVariant)
        text(R.id.card_secondary, texts.secondary, p.onSurfaceVariant)
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
        val res = when (layout) {
            NextUpLayout.Small -> R.layout.widget_next_up_small_legacy
            NextUpLayout.Medium, NextUpLayout.Wide -> R.layout.widget_next_up_legacy
            NextUpLayout.Square, NextUpLayout.Tall -> R.layout.widget_next_up_stack_legacy
            NextUpLayout.Ribbon -> R.layout.widget_next_up_ribbon_legacy
        }
        val glyphDp = when (layout) {
            NextUpLayout.Small -> SMALL_GLYPH_DP
            NextUpLayout.Square, NextUpLayout.Tall -> 36
            else -> 40
        }
        val small = layout == NextUpLayout.Small
        val stack = layout == NextUpLayout.Square || layout == NextUpLayout.Tall
        return RemoteViews(context.packageName, res).apply {
            tintBackground(p.tinted(model.state.tintType, NEXT_UP_TINT))
            setImageViewBitmap(R.id.glyph, glyphBitmap(texts.glyph, p, (glyphDp * density).toInt()))
            setTextViewText(R.id.title, texts.title)
            setTextColor(R.id.title, p.onSurface)
            if (small) {
                // Same rule as the Remote Compose 1×1: no single word may break ("Clockblocked").
                setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, TextFit.smallLabelSp(context, texts.title).toFloat())
            }
            val millisLeft = texts.countdownEnd?.let { Duration.between(now, it).toMillis() }?.takeIf { it > 0 }
            // Same rule as Remote Compose: at large font sizes the one-row sizes (2×1, 4×1) have two lines, the second
            // one folds in the other zone's time, and the 4×1 countdown gives that line its room.
            val oneRow = layout == NextUpLayout.Medium || layout == NextUpLayout.Wide
            val twoLines = oneRow && context.resources.configuration.fontScale > MAX_THREE_LINE_FONT_SCALE
            val showCountdown = millisLeft != null && layout != NextUpLayout.Medium && !twoLines
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
            when {
                small -> Unit
                stack -> {
                    // Up next already says what comes next: the tall stack drops its "then …" line for the room.
                    val withThen = layout != NextUpLayout.Tall || texts.upcoming.isEmpty()
                    text(R.id.line1, texts.subtitleLines.getOrNull(0), p.onSurfaceVariant)
                    text(R.id.line2, texts.subtitleLines.getOrNull(1)?.takeIf { withThen }, p.onSurfaceVariant)
                    text(R.id.secondary, texts.secondary, p.onSurfaceVariant)
                }
                else -> {
                    setTextViewText(R.id.subtitle, if (twoLines) texts.subtitleWithSecondary else texts.subtitle)
                    setTextColor(R.id.subtitle, p.onSurfaceVariant)
                    text(R.id.secondary, texts.secondary.takeIf { !twoLines }, p.onSurfaceVariant)
                }
            }
            if (!small) done(context, model, show = layout != NextUpLayout.Medium)
            if (stack) {
                val tall = layout == NextUpLayout.Tall
                setViewVisibility(R.id.spacer_middle, if (tall) View.GONE else View.VISIBLE)
                setViewVisibility(R.id.spacer_end, if (tall) View.VISIBLE else View.GONE)
                upNext(context, model, show = tall, barWithRows = false, withRoute = true)
            }
            if (layout == NextUpLayout.Ribbon) capsules(context, model)
            setContentDescription(R.id.main, "${texts.title}. ${texts.subtitle}")
            setOnClickPendingIntent(R.id.main, DeepLinkIntents.pendingIntent(context, texts.deepLink))
        }
    }

    /** "Tokyo · Day 2" with the route at the end (`header_strip`, 4×3 Two Clocks). Same rules as `HeaderStrip`. */
    private fun RemoteViews.headerStrip(context: Context, model: WidgetModel, show: Boolean) {
        val texts = model.texts
        if (!show || (texts.header == null && texts.route == null)) {
            setViewVisibility(R.id.header_strip, View.GONE)
            return
        }
        setViewVisibility(R.id.header_strip, View.VISIBLE)
        text(R.id.strip_header, texts.header, model.palette.onSurfaceVariant)
        val routeWidthPx = routeImage(context, model, R.id.strip_route, texts.route)
        // The route overlays the end of the line: keep the text clear of it.
        setViewPadding(R.id.strip_header, 0, 0, if (routeWidthPx > 0) routeWidthPx + dp(context, 8) else 0, 0)
        setContentDescription(R.id.header_strip, listOfNotNull(texts.header, texts.routeDescription).joinToString(". "))
    }

    /** The [route] in dot matrix into the image view [id] (hidden when null); returns its width in px, 0 when hidden. */
    private fun RemoteViews.routeImage(context: Context, model: WidgetModel, id: Int, route: WidgetRoute?): Int {
        if (route == null) {
            setViewVisibility(id, View.GONE)
            return 0
        }
        val pitch = RouteStrip.PITCH_DP * context.resources.displayMetrics.density
        val w = ceil(RouteStrip.WIDTH * pitch).toInt()
        val h = ceil(RouteStrip.HEIGHT * pitch).toInt()
        val p = model.palette
        setImageViewBitmap(id, CanvasOps.bitmap(RouteStrip.build(route, p.onSurface, p.onSurfaceVariant), w, h, pitch))
        setViewVisibility(id, View.VISIBLE)
        return w
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    /**
     * The Done button: a filled button that fires the notification module's Done broadcast, or, once something is
     * logged, a quiet chip in the same footprint. Hidden for free time, flights and the empty state.
     */
    private fun RemoteViews.done(context: Context, model: WidgetModel, show: Boolean) {
        val done = model.texts.done.takeIf { show }
        if (done == null) {
            setViewVisibility(R.id.done, View.GONE)
            return
        }
        val p = model.palette
        val logged = done.logged != null
        setViewVisibility(R.id.done, View.VISIBLE)
        setInt(R.id.done_bg, "setColorFilter", if (logged) p.surfaceContainer else p.primary)
        setTextViewText(R.id.done_label, done.label)
        setTextColor(R.id.done_label, if (logged) p.onSurfaceVariant else p.onPrimary)
        setContentDescription(R.id.done, done.contentDescription)
        // Explicitly cleared once logged: hosts reapply same-layout updates, so an old listener would survive.
        setOnClickPendingIntent(
            R.id.done,
            if (logged) null else NotificationIntents.widgetDone(context, done.tripId, done.adviceId),
        )
    }

    /**
     * "Up next" rows and the adaptation bar (`widget_up_next_legacy.xml`). Same rules as the Remote Compose `UpNext`:
     * at most [UP_NEXT_ROWS] two-line rows, and the bar shows when [barWithRows] or when nothing is up next.
     */
    private fun RemoteViews.upNext(
        context: Context,
        model: WidgetModel,
        show: Boolean,
        barWithRows: Boolean = true,
        withRoute: Boolean = false,
    ) {
        val p = model.palette
        val texts = model.texts.let { t ->
            val upcoming = t.upcoming.take(UP_NEXT_ROWS)
            val bar = barWithRows || upcoming.isEmpty()
            t.copy(upcoming = upcoming, adaptation = t.adaptation.takeIf { bar }, adaptationLabel = t.adaptationLabel.takeIf { bar })
        }
        if (!show || (texts.upcoming.isEmpty() && texts.adaptation == null)) {
            setViewVisibility(R.id.up_next, View.GONE)
            return
        }
        setViewVisibility(R.id.up_next, View.VISIBLE)
        setTextColor(R.id.up_next_title, p.onSurfaceVariant)
        setViewVisibility(R.id.up_next_header, if (texts.upcoming.isEmpty()) View.GONE else View.VISIBLE)
        val route = texts.route?.takeIf { withRoute && texts.upcoming.isNotEmpty() }
        routeImage(context, model, R.id.up_next_route, route)
        val density = context.resources.displayMetrics.density
        listOf(
            listOf(R.id.row_0, R.id.row_0_glyph, R.id.row_0_text, R.id.row_0_secondary),
            listOf(R.id.row_1, R.id.row_1_glyph, R.id.row_1_text, R.id.row_1_secondary),
        ).forEachIndexed { i, (row, glyph, text, secondary) ->
            val item = texts.upcoming.getOrNull(i)
            setViewVisibility(row, if (item != null) View.VISIBLE else View.GONE)
            if (item != null) {
                setImageViewBitmap(glyph, glyphBitmap(item.glyph, p, (20 * density).toInt()))
                setTextViewText(text, "${item.time}  ${item.label}")
                setTextColor(text, p.onSurface)
                text(secondary, item.secondary, p.onSurfaceVariant)
            }
        }
        val fraction = texts.adaptation
        val label = texts.adaptationLabel
        if (fraction != null && label != null) {
            text(R.id.adaptation_label, listOfNotNull(label, texts.misalignment).joinToString(" · "), p.onSurfaceVariant)
            setViewVisibility(R.id.adaptation_bar, View.VISIBLE)
            setImageViewBitmap(R.id.adaptation_bar, adaptationBar(fraction, p, (6 * density).roundToInt()))
        } else {
            setViewVisibility(R.id.adaptation_label, View.GONE)
            setViewVisibility(R.id.adaptation_bar, View.GONE)
        }
        setContentDescription(
            R.id.up_next,
            listOfNotNull(texts.routeDescription?.takeIf { route != null }, texts.upcomingDescription, texts.adaptationLabel)
                .joinToString(". "),
        )
        setOnClickPendingIntent(R.id.up_next, DeepLinkIntents.pendingIntent(context, texts.deepLink))
    }

    /** The 4×2 ribbon's Up next capsules. */
    private fun RemoteViews.capsules(context: Context, model: WidgetModel) {
        val p = model.palette
        val texts = model.texts
        setViewVisibility(R.id.capsules, if (texts.upcoming.isEmpty()) View.INVISIBLE else View.VISIBLE)
        val density = context.resources.displayMetrics.density
        listOf(
            listOf(R.id.cap_0, R.id.cap_0_bg, R.id.cap_0_glyph, R.id.cap_0_text, R.id.cap_0_secondary),
            listOf(R.id.cap_1, R.id.cap_1_bg, R.id.cap_1_glyph, R.id.cap_1_text, R.id.cap_1_secondary),
            listOf(R.id.cap_2, R.id.cap_2_bg, R.id.cap_2_glyph, R.id.cap_2_text, R.id.cap_2_secondary),
        ).forEachIndexed { i, (cap, bg, glyph, text, secondary) ->
            val item = texts.upcoming.getOrNull(i)
            setViewVisibility(cap, if (item != null) View.VISIBLE else View.INVISIBLE)
            if (item != null) {
                setInt(bg, "setColorFilter", p.surfaceContainer)
                setImageViewBitmap(glyph, glyphBitmap(item.glyph, p, (18 * density).toInt()))
                setTextViewText(text, "${item.time} ${item.label}")
                setTextColor(text, p.onSurface)
                text(secondary, item.secondary, p.onSurfaceVariant)
            }
        }
        texts.upcomingDescription?.let { setContentDescription(R.id.capsules, it) }
        setOnClickPendingIntent(R.id.capsules, DeepLinkIntents.pendingIntent(context, texts.deepLink))
    }

    /** Shows [value] in [id] (in [color]), or hides the view when there is nothing to say. */
    private fun RemoteViews.text(id: Int, value: String?, color: Int) {
        setViewVisibility(id, if (value != null) View.VISIBLE else View.GONE)
        setTextViewText(id, value.orEmpty())
        setTextColor(id, color)
    }

    private fun RemoteViews.tintBackground(color: Int) {
        setViewVisibility(R.id.bg, View.VISIBLE)
        setInt(R.id.bg, "setColorFilter", color)
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

    fun glyphBitmap(model: WidgetModel, sizePx: Int): Bitmap = glyphBitmap(model.texts.glyph, model.palette, sizePx)

    fun glyphBitmap(kind: GlyphKind, palette: WidgetPalette, sizePx: Int): Bitmap =
        CanvasOps.bitmap(Glyphs.build(kind, palette), sizePx.coerceIn(24, MAX_GLYPH_PX))

    /** Static adaptation bar (stretched to the row width by the ImageView). Data, not delight: no motion. */
    private fun adaptationBar(fraction: Float, p: WidgetPalette, heightPx: Int): Bitmap {
        val h = heightPx.coerceAtLeast(4)
        val w = h * 40
        return createBitmap(w, h).also { bitmap ->
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val r = h / 2f
            paint.color = p.track
            canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, paint)
            if (fraction > 0f) {
                paint.color = p.primary
                canvas.drawRoundRect(RectF(0f, 0f, w * fraction.coerceIn(0.04f, 1f), h.toFloat()), r, r, paint)
            }
        }
    }

    /** Fixed-offset TimeZone id understood by TextClock, e.g. "GMT+05:12", "GMT-03:00". */
    fun gmtId(offsetMinutes: Int): String {
        val sign = if (offsetMinutes < 0) '-' else '+'
        val m = abs(offsetMinutes)
        return String.format(Locale.ROOT, "GMT%c%02d:%02d", sign, m / 60, m % 60)
    }
}
