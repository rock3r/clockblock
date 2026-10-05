package dev.sebastiano.clockblocker.opus.core.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Human labels for time zones. The one place that turns a [ZoneId] into text, so raw ids ("Z", "Etc/UTC",
 * "Europe/Rome") never reach the UI.
 */
object ZoneLabels {
    /** True for zones that are just an offset (`Z`, `UTC`, `GMT+2`, `Etc/GMT-5`), not a place. */
    fun isOffsetOnly(zone: ZoneId): Boolean {
        val id = zone.id
        return zone is ZoneOffset || '/' !in id || id.startsWith("Etc/")
    }

    /** "Lisbon", "Buenos Aires"; an offset label ("GMT", "GMT+5:30") for zones that aren't a place. */
    fun city(zone: ZoneId): String =
        if (isOffsetOnly(zone)) offset(zone.rules.getOffset(Instant.EPOCH)) else zone.id.substringAfterLast('/').replace('_', ' ')

    /** [city] for a stored IANA id; unparseable ids fall back to their last path segment. */
    fun city(zoneId: String): String =
        runCatching { city(ZoneId.of(zoneId)) }.getOrElse { zoneId.substringAfterLast('/').replace('_', ' ') }

    /** "GMT", "GMT+1", "GMT+5:30", "GMT−3:30" for [zone]'s offset at [at] (DST-aware). */
    fun offset(zone: ZoneId, at: Instant): String = offset(zone.rules.getOffset(at))

    /** "GMT", "GMT+1", "GMT+5:30", "GMT−3:30" (typographic minus). */
    fun offset(offset: ZoneOffset): String {
        val seconds = offset.totalSeconds
        if (seconds == 0) return "GMT"
        val sign = if (seconds > 0) "+" else "\u2212"
        val hours = abs(seconds) / 3600
        val minutes = abs(seconds) % 3600 / 60
        return if (minutes == 0) "GMT$sign$hours" else "GMT$sign$hours:%02d".format(minutes)
    }

    /**
     * Localised, DST-aware full name at [at]: "British Summer Time", "Central European Standard Time". Null for
     * offset-only zones (their [offset] label already says everything) or when the platform has no name.
     */
    fun longName(zone: ZoneId, at: Instant, locale: Locale = Locale.getDefault()): String? {
        if (isOffsetOnly(zone)) return null
        val name = DateTimeFormatter.ofPattern("zzzz", locale).format(at.atZone(zone))
        // Without CLDR names the formatter falls back to an offset ("GMT+01:00") or the raw id: not worth showing.
        return name.takeUnless { it.startsWith("GMT") || it.startsWith("UTC") || it == zone.id }
    }
}
