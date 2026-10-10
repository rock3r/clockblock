package dev.sebastiano.clockblocker.opus.widget.text

import android.content.Context
import android.text.format.DateFormat
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.DeepLinks
import dev.sebastiano.clockblocker.opus.widget.R
import dev.sebastiano.clockblocker.opus.widget.draw.GlyphKind
import dev.sebastiano.clockblocker.opus.widget.state.DialMath
import dev.sebastiano.clockblocker.opus.widget.state.WidgetRoute
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.isPrivate
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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
    /** "Tokyo · Day 2": where the dial's times are shown and which plan day it is. Null without a plan. */
    val header: String? = null,
    /** "Up next" rows for the larger sizes (label + start time, local). */
    val upcoming: List<UpcomingText> = emptyList(),
    /** Share of the planned shift completed, 0..1, and its label ("59% adapted"). */
    val adaptation: Float? = null,
    val adaptationLabel: String? = null,
    /** "Up next: %1$s", the template of [upcomingDescription]. */
    val upcomingTemplate: String = "Up next: %1\$s",
    /** The Done action for the current block; null when there is nothing to mark (free time, flights, no plan). */
    val done: DoneText? = null,
    /** The trip's airport codes for the dot-matrix route strip on the larger sizes; null when unknown or redacted. */
    val route: WidgetRoute? = null,
    /** "L I S to H N D": the route strip spoken, codes spelled out. */
    val routeDescription: String? = null,
    /**
     * The current block spoken, with the end time in the other zone too: "Avoid light. until 18:00 (10:00 in Lisbon)
     * · then Sleep". What Next up's main region says, before its live countdown.
     */
    val spokenNow: String = "$title. $subtitle",
    /** Words of the spoken live countdown ("1 hour 10 minutes left"). */
    val countdownWords: CountdownWords = CountdownWords(),
    /**
     * Shorter forms of [secondary] for small cells, with the place cut or replaced by its airport code ("10:00 in
     * XNA", see `PlaceNames`). Shown only where [secondary] can't fit whole; screen readers keep [secondary].
     */
    val secondaryShort: List<String> = emptyList(),
    /**
     * The "until" line with the other zone's time joined on, as short as it gets: "until 16:30 · 08:30 LIS". For
     * cells where the other zone's time can't have its own line. Null without a secondary zone.
     */
    val untilCompact: String? = null,
    /**
     * A shorter [title] for the 1×1 tile, where one long word would have to shrink well below the other labels
     * ("Adapted" for "Clockblocked"). Null: the tile shows [title]. Screen readers keep [title].
     */
    val smallTitle: String? = null,
) {
    /** [secondary], then [secondaryShort]: the forms a layout tries, in order. Empty without a secondary zone. */
    val secondaryOptions: List<String> get() = listOfNotNull(secondary) + secondaryShort.takeIf { secondary != null }.orEmpty()

    /** The label the 1×1 tile shows: [smallTitle], else [title]. */
    val smallLabel: String get() = smallTitle ?: title

    /**
     * "Up next: Melatonin at 20:30 (12:30 in Lisbon), Sleep at 22:00 (14:00 in Lisbon)": spoken for the queue region.
     * Derived from [upcoming], so a copy with fewer rows speaks only the rows it shows.
     */
    val upcomingDescription: String?
        get() = upcoming.takeIf { it.isNotEmpty() }?.let { rows -> upcomingTemplate.format(rows.joinToString { it.spoken }) }

    companion object {
        fun from(context: Context, state: WidgetState, is24Hour: Boolean = DateFormat.is24HourFormat(context)) =
            WidgetTextFactory(context, is24Hour).texts(state)
    }
}

/** An "Up next" entry: start [time] in the display zone and, like every widget time, the same time in the secondary zone. */
data class UpcomingText(
    val type: AdviceType,
    val label: String,
    val time: String,
    val secondary: String? = null,
    /** Spoken form, both zones: "Melatonin at 20:30 (12:30 in Lisbon)". */
    val spoken: String = listOfNotNull("$label at $time", secondary?.let { "($it)" }).joinToString(" "),
    /** Glyph next to the label: the advice's own, or the neutral plan step when redacted. */
    val glyph: GlyphKind = GlyphKind.Advice(type),
    /** Shorter forms of [secondary] for small cells (see [WidgetTexts.secondaryShort]). */
    val secondaryShort: List<String> = emptyList(),
) {
    /** [secondary], then [secondaryShort]: the forms a layout tries, in order. */
    val secondaryOptions: List<String> get() = listOfNotNull(secondary) + secondaryShort.takeIf { secondary != null }.orEmpty()
}

/**
 * The widget Done button. While [logged] is null it is a button that logs [adviceId] as done; once something is
 * logged it becomes a non-interactive chip in the same footprint ("✓ Done" / "Skipped").
 */
data class DoneText(
    val tripId: String,
    val adviceId: String,
    val logged: AdviceOutcome?,
    val label: String,
    val contentDescription: String,
)

/**
 * Words of the spoken countdown. The countdown is host-evaluated (it ticks on the launcher without waking the app), so
 * it is assembled from parts rather than formatted: [leftPrefix] + "1 hour 10 minutes" + [leftSuffix].
 */
data class CountdownWords(
    val hour: String = "hour",
    val hours: String = "hours",
    val minute: String = "minute",
    val minutes: String = "minutes",
    val leftPrefix: String = "",
    val leftSuffix: String = " left",
)

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
            // Two Clocks shows "No trip" over "Plan one" (EmptyContent); classic layouts show this single caption.
            dialTitle = str(R.string.widget_no_trip_full),
            dialDetail = null,
            deepLink = DeepLinks.NEW_TRIP,
            contentDescription = str(R.string.widget_no_trip_full),
            is24Hour = is24Hour,
        )
        is WidgetState.Active -> active(state)
    }

    private fun active(s: WidgetState.Active): WidgetTexts {
        // Redacted (lock screen): private advice reads as a neutral "Plan step" everywhere, like the notifications.
        fun label(type: AdviceType) =
            if (s.redacted && type.isPrivate) str(R.string.widget_advice_redacted) else this@WidgetTextFactory.label(type)
        fun glyph(type: AdviceType) = if (s.redacted && type.isPrivate) GlyphKind.PlanStep else GlyphKind.Advice(type)
        val zone = ZoneId.of(s.displayZoneId)
        val current = s.current
        val next = s.next
        val misalignment = DialMath.formatMisalignment(s.bodyRelativeMinutes)

        val glyph: GlyphKind
        val title: String
        val subtitleLines: List<String>
        val secondaryAt: Instant?
        val dialDetail: String
        var smallTitle: String? = null
        when {
            current != null -> {
                glyph = glyph(current.type)
                title = label(current.type)
                val until = str(R.string.widget_until, time(current.end, zone))
                subtitleLines = listOfNotNull(until, next?.let { str(R.string.widget_then, label(it.type)) })
                secondaryAt = current.end
                dialDetail = until
            }
            s.stage == WidgetState.Stage.Done || next == null -> {
                glyph = GlyphKind.Adapted
                title = str(R.string.widget_adapted_title)
                smallTitle = str(R.string.widget_adapted_short)
                subtitleLines = listOf(
                    if (s.redacted) str(R.string.widget_adapted_subtitle_redacted) else str(R.string.widget_adapted_subtitle, s.destinationName),
                )
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

        // "10:00 in Lisbon", then its shorter forms for small cells ("10:00 in XNA"): the full form first.
        fun otherZone(at: Instant, zoneId: String): List<String> {
            val atThere = time(at, ZoneId.of(zoneId))
            return s.placeNameOptions(zoneId).map { str(R.string.widget_secondary_time, atThere, it) }
        }
        val secondaryForms = secondaryAt?.let { at -> s.secondaryZoneId?.let { otherZone(at, it) } }.orEmpty()
        val secondary = secondaryForms.firstOrNull()
        // "until 18:00 · 10:00 LIS": the until line (first whenever there is an other-zone time) and the shortest place.
        val untilCompact = secondaryAt?.let { at ->
            s.secondaryZoneId?.let { zoneId ->
                val compact = str(R.string.widget_secondary_compact, time(at, ZoneId.of(zoneId)), s.placeNameOptions(zoneId).last())
                str(R.string.widget_until_compact, subtitleLines.first(), compact)
            }
        }
        val countdownEnd = current?.end?.takeIf { Duration.between(s.capturedAt, it) < Duration.ofHours(24) }
        // Spoken: the other zone's time joins the line whose time it repeats ("until 18:00 (10:00 in Lisbon)").
        val spokenSubtitle = subtitleLines.mapIndexed { i, line ->
            if (i == 0 && secondary != null) str(R.string.widget_a11y_with_secondary, line, secondary) else line
        }.joinToString(" · ")

        val localNow = time(s.capturedAt, zone)
        val bodyNow = time(s.capturedAt, java.time.ZoneOffset.ofTotalSeconds(s.bodyOffsetMinutes * 60))
        val now = if (current != null) str(R.string.widget_a11y_now, title, spokenSubtitle) else "$title. $spokenSubtitle."
        val bodyPhrase = when {
            misalignment == null -> str(R.string.widget_a11y_body_in_sync)
            s.bodyRelativeMinutes < 0 -> str(R.string.widget_a11y_body_behind, DialMath.formatHoursMagnitude(s.bodyRelativeMinutes))
            else -> str(R.string.widget_a11y_body_ahead, DialMath.formatHoursMagnitude(s.bodyRelativeMinutes))
        }
        val secondaryZone = s.secondaryZoneId?.let { ZoneId.of(it) }
        val upcoming = s.upcoming.map {
            val label = label(it.type)
            val time = time(it.start, zone)
            val forms = secondaryZone?.let { z -> otherZone(it.start, z.id) }.orEmpty()
            val secondary = forms.firstOrNull()
            UpcomingText(
                type = it.type,
                label = label,
                time = time,
                secondary = secondary,
                spoken = if (secondary == null) {
                    str(R.string.widget_starts_at, label, time)
                } else {
                    str(R.string.widget_starts_at_with_secondary, label, time, secondary)
                },
                glyph = glyph(it.type),
                secondaryShort = forms.drop(1),
            )
        }
        return WidgetTexts(
            glyph = glyph,
            title = title,
            subtitle = subtitle,
            subtitleLines = subtitleLines,
            secondary = secondary,
            secondaryShort = secondaryForms.drop(1),
            untilCompact = untilCompact,
            smallTitle = smallTitle,
            countdownEnd = countdownEnd,
            misalignment = misalignment,
            dialTitle = title,
            dialDetail = dialDetail,
            deepLink = DeepLinks.plan(s.tripId),
            contentDescription = str(R.string.widget_a11y_two_clocks, localNow, bodyNow, bodyPhrase, now),
            is24Hour = is24Hour,
            header = header(s),
            upcoming = upcoming,
            adaptation = s.adaptation,
            adaptationLabel = s.adaptation?.let { str(R.string.widget_adapted_percent, (it * 100).roundToInt()) },
            upcomingTemplate = context.getString(R.string.widget_a11y_up_next),
            done = current?.takeIf { it.type != AdviceType.Flight && s.outcomeKnown }?.let { c ->
                val label = label(c.type)
                when (s.currentOutcome) {
                    null -> DoneText(s.tripId, c.adviceId, null, str(R.string.widget_done), str(R.string.widget_a11y_done_action, label))
                    AdviceOutcome.Done ->
                        DoneText(s.tripId, c.adviceId, AdviceOutcome.Done, str(R.string.widget_done_logged), str(R.string.widget_a11y_done_logged, label))
                    AdviceOutcome.Skipped, AdviceOutcome.CantDo -> DoneText(
                        s.tripId,
                        c.adviceId,
                        s.currentOutcome,
                        str(R.string.widget_skipped),
                        str(R.string.widget_a11y_skipped_logged, label),
                    )
                }
            },
            route = s.route,
            routeDescription = s.route?.let { str(R.string.widget_a11y_route, spell(it.origin), spell(it.destination)) },
            spokenNow = "$title. $spokenSubtitle",
            countdownWords = countdownWords(),
        )
    }

    private fun countdownWords(): CountdownWords {
        // "%1$s left" split around its placeholder: the duration itself is assembled on the host.
        val (prefix, suffix) = str(R.string.widget_a11y_time_left, PLACEHOLDER).split(PLACEHOLDER, limit = 2)
        return CountdownWords(
            hour = str(R.string.widget_a11y_hour),
            hours = str(R.string.widget_a11y_hours),
            minute = str(R.string.widget_a11y_minute),
            minutes = str(R.string.widget_a11y_minutes),
            leftPrefix = prefix,
            leftSuffix = suffix,
        )
    }

    /** "LIS" → "L I S", so TalkBack spells a code out (same rule as the design system's `spellOut`). */
    private fun spell(code: String) = code.trim().uppercase().toCharArray().joinToString(" ")

    /**
     * "Tokyo · Day 2" (the trip's city in the zone the dial shows, see [WidgetState.Active.placeName]), or just the
     * city outside the plan's days. Redacted: the day alone ("Day 2"), no header outside the plan's days.
     */
    private fun header(s: WidgetState.Active): String? {
        val city = s.placeName(s.displayZoneId)
        val index = s.dayIndex ?: return city.takeUnless { s.redacted }
        val day = when (s.dayKind) {
            DayKind.PreTrip -> str(R.string.widget_day_pre, DialMath.signed(index))
            DayKind.Travel -> str(R.string.widget_day_travel)
            DayKind.Adapted -> str(R.string.widget_day_adapted, index)
            DayKind.Arrival, null -> str(R.string.widget_day_n, index)
        }
        return if (s.redacted) day else str(R.string.widget_header, city, day)
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

    private companion object {
        /** Stands in for the host-assembled duration while splitting a template around it. */
        const val PLACEHOLDER = "\u0000"
    }
}
