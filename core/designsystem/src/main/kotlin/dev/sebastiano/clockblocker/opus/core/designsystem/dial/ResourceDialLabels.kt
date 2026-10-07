package dev.sebastiano.clockblocker.opus.core.designsystem.dial

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.sebastiano.clockblocker.opus.core.designsystem.R
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.labelRes
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DefaultDialLabels
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialLabels
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.roundToMinute
import dev.sebastiano.clockblocker.opus.core.designsystem.time.TimeFormatter
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.AdviceType

/** [DialLabels] from string resources and the user's 12/24-hour clock, so the dial speaks the app's language. */
class ResourceDialLabels(private val resources: Resources, private val formatter: TimeFormatter) : DialLabels {
    override fun time(minuteOfDay: Float): String = formatter.format(DialGeometry.timeOf(minuteOfDay.roundToMinute()))
    override fun marker(minuteOfDay: Float): String? = formatter.marker(DialGeometry.timeOf(minuteOfDay.roundToMinute()))
    override fun fullTime(minuteOfDay: Float): String = formatter.formatFull(DialGeometry.timeOf(minuteOfDay.roundToMinute()))
    override fun numeral(hour: Int): String = DefaultDialLabels.numeralFor(hour, formatter.is24Hour)
    override fun placeDay(place: String): String = resources.getString(R.string.dial_ring_place_day, place)
    override fun placeNight(place: String): String = resources.getString(R.string.dial_ring_place_night, place)
    override fun bodyDay(): String = resources.getString(R.string.dial_ring_body_day)
    override fun bodyNight(): String = resources.getString(R.string.dial_ring_body_night)
    override fun body(): String = resources.getString(R.string.dial_ring_body)
    override fun bodyTime(time: String): String = resources.getString(R.string.dial_body_time, time)
    override fun offset(bodyAheadMinutes: Float): String {
        val hours = formatHoursMagnitude(bodyAheadMinutes / 60f)
        return when {
            DefaultDialLabels.isInSync(bodyAheadMinutes) -> inSync()
            bodyAheadMinutes < 0f -> resources.getString(R.string.dial_offset_behind, hours)
            else -> resources.getString(R.string.dial_offset_ahead, hours)
        }
    }
    override fun inSync(): String = resources.getString(R.string.dial_in_sync)
    override fun advice(type: AdviceType): String = resources.getString(type.labelRes)
    override fun until(advice: String, time: String): String = resources.getString(R.string.dial_advice_until, advice, time)
    override fun at(advice: String, time: String): String = resources.getString(R.string.dial_advice_at, advice, time)
    override fun then(advice: String): String =
        resources.getString(R.string.dial_advice_then, advice.replaceFirstChar { it.lowercase() })
}

/** The dial's labels for the current configuration (locale, 12/24-hour clock). */
@Composable
fun rememberDialLabels(): DialLabels {
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    val formatter = rememberTimeFormatter()
    return remember(resources, configuration, formatter) { ResourceDialLabels(resources, formatter) }
}
