package dev.sebastiano.clockblocker.opus.feature.plan

import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The day strip's number for [day], counted around the flight: "−2", "−1", then "+1", "+2"… after it. The travel day
 * has none (null): its pill shows the plane instead.
 */
internal fun dayStripNumber(day: PlanDay): String? = if (day.kind == DayKind.Travel) null else signedIndex(day.index)

/**
 * The weekday under a day pill: "Sun". A travel day spans from its first take-off to its last landing, each read on
 * the clock where it happens ([zoneAt]: the zone the traveller is in at an instant), so an overnight flight reads
 * "Mon–Tue". A travel day without flights reads its own date's weekday.
 */
internal fun dayWeekdays(day: PlanDay, zoneAt: (Instant) -> ZoneId, locale: Locale = Locale.getDefault()): String {
    val (from, to) = dayDates(day, zoneAt)
    return weekdaySpan(from, to, locale)
}

/** "Sun", or "Mon–Tue" when [to] is a different date from [from]. */
internal fun weekdaySpan(from: LocalDate, to: LocalDate, locale: Locale = Locale.getDefault()): String {
    val format = WeekdayFormat.withLocale(locale)
    return if (from == to) format.format(from) else format.format(from) + "\u2013" + format.format(to)
}

/** The local dates [day] covers: its own date, or a travel day's take-off and landing dates (see [dayWeekdays]). */
internal fun dayDates(day: PlanDay, zoneAt: (Instant) -> ZoneId): Pair<LocalDate, LocalDate> {
    val flights = day.advice.filter { it.type == AdviceType.Flight }
    if (day.kind != DayKind.Travel || flights.isEmpty()) return day.date to day.date
    val takeOff = flights.minOf { it.start }
    val landing = flights.maxOf { it.end }
    // The zone just before take-off and just after landing: the instants themselves sit on the switch.
    val from = takeOff.atZone(zoneAt(takeOff.minusSeconds(1))).toLocalDate()
    val to = landing.atZone(zoneAt(landing.plusSeconds(1))).toLocalDate()
    return from to to
}

private val WeekdayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE")
