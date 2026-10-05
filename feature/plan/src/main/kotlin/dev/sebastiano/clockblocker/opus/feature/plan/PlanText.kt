package dev.sebastiano.clockblocker.opus.feature.plan

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.BodyOffsetDirection
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.bodyOffsetDirection
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.formatHoursMagnitude
import dev.sebastiano.clockblocker.opus.core.designsystem.time.TimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Plain-language mechanism for each [AdviceReason] (the "Why?" sheet). */
@get:StringRes
internal val AdviceReason.explanationRes: Int
    get() = when (this) {
        AdviceReason.LightAdvancesClock -> R.string.plan_reason_light_advances
        AdviceReason.LightDelaysClock -> R.string.plan_reason_light_delays
        AdviceReason.AvoidCounterShift -> R.string.plan_reason_avoid_counter
        AdviceReason.MelatoninAdvances -> R.string.plan_reason_melatonin_advances
        AdviceReason.MelatoninDelays -> R.string.plan_reason_melatonin_delays
        AdviceReason.ShiftedSleep -> R.string.plan_reason_shifted_sleep
        AdviceReason.DestinationSleep -> R.string.plan_reason_destination_sleep
        AdviceReason.StayOnHomeTime -> R.string.plan_reason_stay_home
        AdviceReason.AlertnessSupport -> R.string.plan_reason_alertness
        AdviceReason.ProtectSleep -> R.string.plan_reason_protect_sleep
        AdviceReason.CircadianLow -> R.string.plan_reason_circadian_low
        AdviceReason.SleepPressure -> R.string.plan_reason_sleep_pressure
        AdviceReason.TravelMarker -> R.string.plan_reason_travel
        AdviceReason.RestInFlight -> R.string.plan_reason_rest_in_flight
    }

/** The evidence note behind each [AdviceReason]. */
@get:StringRes
internal val AdviceReason.evidenceRes: Int
    get() = when (this) {
        AdviceReason.LightAdvancesClock, AdviceReason.LightDelaysClock -> R.string.plan_evidence_light
        AdviceReason.AvoidCounterShift -> R.string.plan_evidence_avoid
        AdviceReason.MelatoninAdvances, AdviceReason.MelatoninDelays -> R.string.plan_evidence_melatonin
        AdviceReason.ShiftedSleep, AdviceReason.DestinationSleep -> R.string.plan_evidence_sleep
        AdviceReason.StayOnHomeTime -> R.string.plan_evidence_stay_home
        AdviceReason.AlertnessSupport, AdviceReason.ProtectSleep -> R.string.plan_evidence_caffeine
        AdviceReason.CircadianLow -> R.string.plan_evidence_low
        AdviceReason.SleepPressure -> R.string.plan_evidence_nap
        AdviceReason.TravelMarker -> R.string.plan_evidence_travel
        AdviceReason.RestInFlight -> R.string.plan_evidence_rest
    }

/** Practical how-to for each [AdviceType], with alternatives ("Stuck indoors? …"). */
@get:StringRes
internal val AdviceType.howRes: Int
    get() = when (this) {
        AdviceType.SeeBrightLight -> R.string.plan_how_bright_light
        AdviceType.SeeLight -> R.string.plan_how_light
        AdviceType.AvoidLight -> R.string.plan_how_avoid_light
        AdviceType.Sleep -> R.string.plan_how_sleep
        AdviceType.Nap -> R.string.plan_how_nap
        AdviceType.OptionalNap -> R.string.plan_how_optional_nap
        AdviceType.Melatonin -> R.string.plan_how_melatonin
        AdviceType.Caffeine -> R.string.plan_how_caffeine
        AdviceType.AvoidCaffeine -> R.string.plan_how_avoid_caffeine
        AdviceType.PeakFatigue -> R.string.plan_how_peak_fatigue
        AdviceType.Flight -> R.string.plan_how_flight
    }

/** "1 h 12 min", "2 h", "45 min" (rounded to the minute, at least 1 min). */
internal fun Resources.formatDuration(duration: Duration): String {
    val minutes = duration.toMinutes().coerceAtLeast(1)
    val h = (minutes / 60).toInt()
    val m = (minutes % 60).toInt()
    return when {
        h == 0 -> getString(R.string.plan_duration_m, m)
        m == 0 -> getString(R.string.plan_duration_h, h)
        else -> getString(R.string.plan_duration_h_m, h, m)
    }
}

@Composable
@ReadOnlyComposable
internal fun formatDuration(duration: Duration): String = LocalContext.current.resources.formatDuration(duration)

/** "−1" with a true minus sign for pre-trip day indices. */
internal fun signedIndex(index: Int): String = if (index < 0) "\u2212${-index}" else "+$index"

/** Header subtitle part 1: "Pre-trip day −1", "Travel day", "Day 2 · Adapting", "Starts Sat 13 Jun"… */
@Composable
@ReadOnlyComposable
internal fun stageLabel(moment: PlanMoment, firstDayDate: LocalDate?): String = when (moment.stage) {
    PlanStage.Upcoming -> stringResource(R.string.plan_day_upcoming, firstDayDate?.let(::formatDayDate).orEmpty())
    PlanStage.PreTrip -> stringResource(R.string.plan_day_pre_trip, signedIndex(moment.day?.index ?: -1))
    PlanStage.Travel -> stringResource(R.string.plan_day_travel)
    PlanStage.Adapting -> stringResource(R.string.plan_day_adapting, moment.day?.index ?: 1)
    PlanStage.Adapted -> stringResource(R.string.plan_day_adapted, moment.day?.index ?: 1)
    PlanStage.Complete -> stringResource(R.string.plan_day_complete)
}

/**
 * Header subtitle part 2: "body 8 h behind", "body 2 h ahead" or "body in sync": the body clock relative to
 * local time, in words. Same quantity, rounding and threshold as the dial's wedge chip ("−8 h").
 */
internal fun Resources.bodyShiftLabel(bodyAheadHours: Float): String = when (bodyOffsetDirection(bodyAheadHours)) {
    BodyOffsetDirection.InSync -> getString(R.string.plan_body_in_sync)
    BodyOffsetDirection.Behind -> getString(R.string.plan_body_behind, formatHoursMagnitude(bodyAheadHours))
    BodyOffsetDirection.Ahead -> getString(R.string.plan_body_ahead, formatHoursMagnitude(bodyAheadHours))
}

@Composable
@ReadOnlyComposable
internal fun bodyShiftLabel(bodyAheadHours: Float): String = LocalContext.current.resources.bodyShiftLabel(bodyAheadHours)

/** Day header title: "Pre-trip −2", "Travel day", "Day 2", "Day 4 · Adapted". */
internal fun Resources.dayTitle(day: PlanDay): String = when (day.kind) {
    DayKind.PreTrip -> getString(R.string.plan_day_header_pre, signedIndex(day.index))
    DayKind.Travel -> getString(R.string.plan_day_header_travel)
    DayKind.Arrival -> getString(R.string.plan_day_header_day, day.index)
    DayKind.Adapted -> getString(R.string.plan_day_header_adapted, day.index)
}

@Composable
@ReadOnlyComposable
internal fun dayTitle(day: PlanDay): String = LocalContext.current.resources.dayTitle(day)

private val DayDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

/** "Tue 16 Jun". */
internal fun formatDayDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DayDateFormat.withLocale(locale).format(date)

/** Long date for share text. */
internal fun formatLongDate(date: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date)

/** "07:00 – 09:00" in [zone]; a single time for moments. */
internal fun TimeFormatter.range(start: Instant, end: Instant, zone: ZoneId, resources: Resources): String {
    val s = formatFull(start.atZone(zone).toLocalTime())
    if (start == end) return s
    return resources.getString(R.string.plan_time_range, s, formatFull(end.atZone(zone).toLocalTime()))
}

/** Whole days for "about N days" copy (at least 1). */
internal fun roundDays(days: Double): Int = days.roundToInt().coerceAtLeast(1)

@Composable
@ReadOnlyComposable
internal fun daysToGoLabel(days: Double): String =
    if (days < 0.75) stringResource(R.string.plan_less_than_a_day) else roundDays(days).let { pluralStringResource(R.plurals.plan_days_to_go, it, it) }
