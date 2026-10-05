package dev.sebastiano.clockblocker.opus.feature.trips.ui

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.feature.trips.R
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import dev.sebastiano.clockblocker.opus.core.model.ZoneLabels

/** Locale-aware short dates for cards and fields: "Mon 15 Jun" (or the locale's equivalent order). */
@Immutable
class DateFormatter(locale: Locale) {
    private val short = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
    private val withYear = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMMy"), locale)

    fun format(date: LocalDate): String = short.format(date)

    fun formatWithYear(date: LocalDate): String = withYear.format(date)

    override fun equals(other: Any?): Boolean = other is DateFormatter && other.short == short
    override fun hashCode(): Int = short.hashCode()
}

@Composable
internal fun rememberDateFormatter(): DateFormatter {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return remember(locale) { DateFormatter(locale) }
}

/** "10 h 20 m", "45 m", "3 h", "1 d 4 h" (absolute value; callers phrase the sign). */
@Composable
internal fun formatDuration(duration: Duration): String {
    LocalConfiguration.current // Recompose on locale changes.
    return formatDuration(LocalContext.current.resources, duration)
}

/** Non-composable twin of [formatDuration] for callbacks. */
internal fun formatDuration(resources: Resources, duration: Duration): String {
    val totalMinutes = abs(duration.toMinutes())
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes / 60 % 24).toInt()
    val minutes = (totalMinutes % 60).toInt()
    return when {
        days > 0 -> resources.getString(R.string.duration_days_hours, days.toInt(), hours)
        hours > 0 && minutes > 0 -> resources.getString(R.string.duration_hours_minutes, hours, minutes)
        hours > 0 -> resources.getString(R.string.duration_hours, hours)
        else -> resources.getString(R.string.duration_minutes, minutes)
    }
}

/** "GMT+1", "GMT+5:30", "GMT−3", "GMT" for the zone's offset at [at] (same wording as onboarding and settings). */
internal fun Place.utcOffsetLabel(at: Instant): String = ZoneLabels.offset(zone, at)

internal fun ZoneOffset.utcLabel(): String = ZoneLabels.offset(this)

/** Localised country name for an ISO 3166 alpha-2 code ("PT" → "Portugal"); the code if unknown. */
internal fun countryName(countryCode: String, locale: Locale = Locale.getDefault()): String {
    if (countryCode.isBlank()) return ""
    val name = Locale.Builder().setRegion(countryCode).build().getDisplayCountry(locale)
    return name.ifBlank { countryCode }
}

/** City for labels like "local time in Lisbon". */
internal val Place.cityLabel: String get() = city.ifBlank { name }.ifBlank { displayCode }
