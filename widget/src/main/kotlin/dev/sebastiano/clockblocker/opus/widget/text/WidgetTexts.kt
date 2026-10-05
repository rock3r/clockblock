package dev.sebastiano.clockblocker.opus.widget.text

import android.content.Context
import android.text.format.DateFormat
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DeepLinks
import dev.sebastiano.clockblocker.opus.widget.R
import dev.sebastiano.clockblocker.opus.widget.draw.GlyphKind
import dev.sebastiano.clockblocker.opus.widget.state.DialMath
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Resolved, localised text for one widget render. */
data class WidgetTexts(
    val glyph: GlyphKind,
    /** "Avoid light" */
    val title: String,
    /** "until 18:00 · then Sleep" */
    val subtitle: String,
    /** The same, one phrase per line for taller layouts: ["until 18:00", "then Sleep"]. */
    val subtitleLines: List<String>,
    /** The same end time in the secondary zone: "10:00 in Lisbon". */
    val secondary: String?,
    /** End of the current block when it is less than 24 h away: drives the live countdown. */
    val countdownEnd: Instant?,
    /** "−5 h" jet lag label (body 5 h behind local time), null when in sync or without a plan. */
    val misalignment: String?,
    /**
     * Caption under the square dial, first line: the label ("Avoid light"). Kept separate from [dialDetail] so a
     * 2×2 cell never has to fit "label · until time" on one line.
     */
    val dialTitle: String,
    /** Caption under the square dial, second line: "until 18:00". Null when the dial centre already says it all. */
    val dialDetail: String?,
    val deepLink: String,
    val contentDescription: String,
    val is24Hour: Boolean,
    /** "body", prefix of the body-clock readout. */
    val bodyPrefix: String = "body",
) {
    companion object {
        fun from(context: Context, state: WidgetState, is24Hour: Boolean = DateFormat.is24HourFormat(context)) =
            WidgetTextFactory(context, is24Hour).texts(state)
    }
}

internal class WidgetTextFactory(private val context: Context, private val is24Hour: Boolean) {
    private val locale: Locale = context.resources.configuration.locales[0]
    private val timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)

    fun texts(state: WidgetState): WidgetTexts = when (state) {
        WidgetState.NoTrip -> WidgetTexts(
            glyph = GlyphKind.NoTrip,
            title = str(R.string.widget_no_trip_title),
            subtitle = str(R.string.widget_no_trip_action),
            subtitleLines = listOf(str(R.string.widget_no_trip_action)),
            secondary = null,
            countdownEnd = null,
            misalignment = null,
            // The dial centre already reads "No trip / Plan one"; classic layouts show this single caption.
            dialTitle = str(R.string.widget_no_trip_full),
            dialDetail = null,
            deepLink = DeepLinks.NEW_TRIP,
            contentDescription = str(R.string.widget_no_trip_full),
            is24Hour = is24Hour,
            bodyPrefix = str(R.string.widget_body_prefix),
        )
        is WidgetState.Active -> active(state)
    }

    private fun active(s: WidgetState.Active): WidgetTexts {
        val zone = ZoneId.of(s.displayZoneId)
        val current = s.current
        val next = s.next
        val misalignment = DialMath.formatMisalignment(s.bodyRelativeMinutes)

        val glyph: GlyphKind
        val title: String
        val subtitleLines: List<String>
        val secondaryAt: Instant?
        val dialDetail: String
        when {
            current != null -> {
                glyph = GlyphKind.Advice(current.type)
                title = label(current.type)
                val until = str(R.string.widget_until, time(current.end, zone))
                subtitleLines = listOfNotNull(until, next?.let { str(R.string.widget_then, label(it.type)) })
                secondaryAt = current.end
                dialDetail = until
            }
            s.stage == WidgetState.Stage.Done || next == null -> {
                glyph = GlyphKind.Adapted
                title = str(R.string.widget_adapted_title)
                subtitleLines = listOf(str(R.string.widget_adapted_subtitle, s.destinationName))
                secondaryAt = null
                dialDetail = subtitleLines.single()
            }
            else -> {
                glyph = GlyphKind.Free
                title = str(R.string.widget_free_time)
                val at = time(next.start, zone)
                subtitleLines = listOf(str(R.string.widget_until, at), str(R.string.widget_then, label(next.type)))
                secondaryAt = next.start
                // Time first: "until 18:00" never gets ellipsized away behind a long next label.
                dialDetail = subtitleLines.first()
            }
        }
        val subtitle = subtitleLines.joinToString(" · ")

        val secondary = secondaryAt?.let { at ->
            s.secondaryZoneId?.let { str(R.string.widget_secondary_time, time(at, ZoneId.of(it)), DialMath.cityName(it)) }
        }
        val countdownEnd = current?.end?.takeIf { Duration.between(s.capturedAt, it) < Duration.ofHours(24) }

        val localNow = time(s.capturedAt, zone)
        val bodyNow = time(s.capturedAt, java.time.ZoneOffset.ofTotalSeconds(s.bodyOffsetMinutes * 60))
        val now = if (current != null) str(R.string.widget_a11y_now, title, subtitle) else "$title. $subtitle."
        val bodyPhrase = when {
            misalignment == null -> str(R.string.widget_a11y_body_in_sync)
            s.bodyRelativeMinutes < 0 -> str(R.string.widget_a11y_body_behind, DialMath.formatHoursMagnitude(s.bodyRelativeMinutes))
            else -> str(R.string.widget_a11y_body_ahead, DialMath.formatHoursMagnitude(s.bodyRelativeMinutes))
        }
        return WidgetTexts(
            glyph = glyph,
            title = title,
            subtitle = subtitle,
            subtitleLines = subtitleLines,
            secondary = secondary,
            countdownEnd = countdownEnd,
            misalignment = misalignment,
            dialTitle = title,
            dialDetail = dialDetail,
            deepLink = DeepLinks.plan(s.tripId),
            contentDescription = str(R.string.widget_a11y_two_clocks, localNow, bodyNow, bodyPhrase, now),
            is24Hour = is24Hour,
            bodyPrefix = str(R.string.widget_body_prefix),
        )
    }

    fun label(type: AdviceType): String = str(
        when (type) {
            AdviceType.SeeBrightLight -> R.string.widget_advice_see_bright_light
            AdviceType.SeeLight -> R.string.widget_advice_see_light
            AdviceType.AvoidLight -> R.string.widget_advice_avoid_light
            AdviceType.Sleep -> R.string.widget_advice_sleep
            AdviceType.Nap -> R.string.widget_advice_nap
            AdviceType.OptionalNap -> R.string.widget_advice_optional_nap
            AdviceType.Melatonin -> R.string.widget_advice_melatonin
            AdviceType.Caffeine -> R.string.widget_advice_caffeine
            AdviceType.AvoidCaffeine -> R.string.widget_advice_avoid_caffeine
            AdviceType.PeakFatigue -> R.string.widget_advice_peak_fatigue
            AdviceType.Flight -> R.string.widget_advice_flight
        },
    )

    private fun time(instant: Instant, zone: ZoneId): String = timeFormat.format(instant.atZone(zone))

    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)
}
