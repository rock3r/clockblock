package dev.sebastiano.clockblocker.opus.core.notifications.text

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Formats instants as wall-clock times in one zone. Times on a different local date than the reference instant
 * get a short weekday prefix ("Tue 18:00") so a window ending tomorrow is never mistaken for today.
 * Pure; DST and zone changes are handled by `java.time` rules.
 */
class ClockFormat(
    val zone: ZoneId,
    private val locale: Locale = Locale.getDefault(),
    private val use24Hour: Boolean = true,
) {
    private val timeFormatter = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale)
    private val compactFormatter = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mma", locale)

    /** "18:00", or "Tue 18:00" when [instant] falls on another local date than [reference]. */
    fun time(instant: Instant, reference: Instant): String {
        val local = instant.atZone(zone)
        val plain = timeFormatter.format(local)
        return if (local.toLocalDate() == reference.atZone(zone).toLocalDate()) plain else "${weekday(instant)} $plain"
    }

    /**
     * "18:00–02:00". The start gets a weekday when it isn't on [reference]'s date; the end only when the range
     * spans a day or more (an overnight window is obvious from the times).
     */
    fun range(from: Instant, to: Instant, reference: Instant): String {
        val start = time(from, reference)
        val end = if (Duration.between(from, to) >= Duration.ofDays(1)) {
            "${weekday(to)} ${timeFormatter.format(to.atZone(zone))}"
        } else {
            timeFormatter.format(to.atZone(zone))
        }
        return "$start–$end"
    }

    /** At most 7 characters ("18:00", "6:30PM") so the Live Update status chip shows it in full. */
    fun compact(instant: Instant): String = compactFormatter.format(instant.atZone(zone)).replace(" ", "")

    /** Same locale and 12/24 h preference, another zone. */
    fun withZone(other: ZoneId): ClockFormat = ClockFormat(other, locale, use24Hour)

    private fun weekday(instant: Instant): String =
        instant.atZone(zone).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    companion object {
        /** "Asia/Tokyo" → "Tokyo", "America/Argentina/Buenos_Aires" → "Buenos Aires". */
        fun cityOf(zoneId: String): String = dev.sebastiano.clockblocker.opus.core.model.ZoneLabels.city(zoneId)
    }
}
