package dev.sebastiano.clockblocker.opus.core.designsystem.time

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.sebastiano.clockblocker.opus.core.model.ZoneLabels

/** Formats clock times honouring the user's 12/24-hour preference. */
@Immutable
class TimeFormatter(val is24Hour: Boolean, locale: Locale) {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm", locale)
    private val withMarker: DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)

    /** "14:20" or "2:20" (no AM/PM; pair with [marker] when space allows). */
    fun format(time: LocalTime): String = formatter.format(time)

    /** "14:20" or "2:20 PM". */
    fun formatFull(time: LocalTime): String = withMarker.format(time)

    /** "PM" in 12-hour mode, null in 24-hour mode. */
    fun marker(time: LocalTime): String? = if (is24Hour) null else if (time.hour < 12) "AM" else "PM"

    override fun equals(other: Any?): Boolean = other is TimeFormatter && other.is24Hour == is24Hour
    override fun hashCode(): Int = is24Hour.hashCode()
}

@Composable
fun rememberTimeFormatter(): TimeFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val is24 = DateFormat.is24HourFormat(context)
    return remember(is24, locale) { TimeFormatter(is24, locale) }
}

/** "Europe/Lisbon" → "Lisbon", "America/Argentina/Buenos_Aires" → "Buenos Aires"; offset-only zones → "GMT+2". */
fun ZoneId.cityName(): String = ZoneLabels.city(this)
