package dev.sebastiano.clockblocker.opus.widget.state

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import dev.sebastiano.clockblocker.opus.core.model.ZoneLabels

/** Geometry and formatting shared by the dial renderers and the state mapper. */
object DialMath {
    const val MINUTES_PER_DAY = 1440

    /** Wall-clock minute of the day at a fixed UTC offset. */
    fun minuteOfDay(instant: Instant, offsetMinutes: Int): Int =
        Math.floorMod(Math.floorDiv(instant.epochSecond, 60L) + offsetMinutes, MINUTES_PER_DAY.toLong()).toInt()

    /** Wall-clock minute of the day in [zone] (honours DST at that instant). */
    fun minuteOfDay(instant: Instant, zone: ZoneId): Int =
        minuteOfDay(instant, zone.rules.getOffset(instant).totalSeconds / 60)

    fun wrap(minute: Int): Int = Math.floorMod(minute, MINUTES_PER_DAY)

    /**
     * android.graphics.Canvas angle (degrees, 0 = 3 o'clock, clockwise) of a dial minute on a 24 h dial with
     * **noon at the top** and midnight at the bottom.
     */
    fun canvasDegrees(minute: Float): Float {
        val deg = minute / 4f + 90f
        return ((deg % 360f) + 360f) % 360f
    }

    fun canvasDegrees(minute: Int): Float = canvasDegrees(minute.toFloat())

    /**
     * "+5 h", "−3 h", "+5½ h" for a body-relative offset ("−3 h" = body 3 h behind local time). Rounded to the
     * nearest half hour (body offsets are interpolated, so they are rarely whole). Returns null when the clocks are
     * within 15 minutes of each other.
     */
    fun formatMisalignment(minutes: Int): String? {
        if (abs(minutes) < 15) return null
        val sign = if (minutes > 0) "+" else "\u2212"
        return "$sign${formatHoursMagnitude(minutes)}"
    }

    /** "5 h", "3½ h", "½ h": the unsigned half-hour-rounded magnitude, for "5 h behind" style phrases. */
    fun formatHoursMagnitude(minutes: Int): String {
        val halfHours = abs((minutes / 30.0).roundToInt())
        val whole = halfHours / 2
        val half = halfHours % 2 == 1
        val number = when {
            whole == 0 && half -> "\u00BD"
            half -> "$whole\u00BD"
            else -> "$whole"
        }
        return "$number h"
    }

    /** "Asia/Tokyo" → "Tokyo", "America/Argentina/Buenos_Aires" → "Buenos Aires". */
    fun cityName(zoneId: String): String = ZoneLabels.city(zoneId)
}
