package dev.sebastiano.clockblocker.opus.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** The sun on one local date at one place. */
sealed interface SunDay {
    /** When the sun is highest: the middle of the day, even when it never rises. */
    val solarNoon: Instant

    /** An ordinary day: the sun rises at [sunrise] and sets at [sunset]. */
    data class RisesAndSets(val sunrise: Instant, val sunset: Instant, override val solarNoon: Instant) : SunDay

    /** Midnight sun: above the horizon all day. */
    data class AlwaysUp(override val solarNoon: Instant) : SunDay

    /** Polar night: below the horizon all day. */
    data class AlwaysDown(override val solarNoon: Instant) : SunDay
}

/**
 * Offline sunrise and sunset from NOAA's solar calculator (the "General Solar Position" spreadsheet equations,
 * after Meeus): good to about a minute between ±72° latitude, which is all the sky ring needs. Sunrise and sunset
 * are when the sun's upper limb touches the horizon, with standard refraction (zenith 90.833°).
 */
object Sun {
    private const val Zenith = 90.833

    /**
     * The sun on the local [date] in [zone] at [latitude], [longitude] (degrees, north and east positive). The
     * times are the ones around that date's solar noon, so they fall on [date] even across the date line.
     */
    fun on(date: LocalDate, zone: ZoneId, latitude: Double, longitude: Double): SunDay {
        val clockNoon = date.atTime(LocalTime.NOON).atZone(zone).toInstant()
        // Two passes: the sun's position at clock noon finds solar noon, then again at solar noon.
        var noon = solarNoonNear(clockNoon, longitude)
        noon = solarNoonNear(noon, longitude)
        val atNoon = position(noon)
        val cosH = cosHourAngle(latitude, atNoon)
        if (cosH > 1.0) return SunDay.AlwaysDown(noon)
        if (cosH < -1.0) return SunDay.AlwaysUp(noon)
        return SunDay.RisesAndSets(event(noon, atNoon, latitude, -1), event(noon, atNoon, latitude, +1), noon)
    }

    /**
     * Sunrise ([sign] −1) or sunset (+1), refined with the sun's position at the event itself: near the polar
     * circles the declination moves enough between noon and sunrise to shift it by minutes.
     */
    private fun event(noon: Instant, atNoon: Position, latitude: Double, sign: Int): Instant {
        var at = noon
        var position = atNoon
        repeat(3) {
            val cosH = cosHourAngle(latitude, position).coerceIn(-1.0, 1.0)
            val shift = position.equationOfTimeMinutes - atNoon.equationOfTimeMinutes
            val minutes = sign * 4.0 * deg(acos(cosH)) - shift
            at = noon.plusMillis((minutes * 60_000.0).toLong())
            position = position(at)
        }
        return at
    }

    private fun cosHourAngle(latitude: Double, position: Position): Double =
        cos(rad(Zenith)) / (cos(rad(latitude)) * cos(position.declination)) - tan(rad(latitude)) * tan(position.declination)

    /** The solar noon closest to [near] at [longitude]. */
    private fun solarNoonNear(near: Instant, longitude: Double): Instant {
        val minutesUtc = 720.0 - 4.0 * longitude - position(near).equationOfTimeMinutes
        val utcMidnight = near.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC)
        val candidate = utcMidnight.plusMillis((minutesUtc * 60_000.0).toLong())
        val offset = Duration.between(near, candidate).toMinutes()
        // Pick the day's solar noon nearest [near]: at most 12 h away.
        return when {
            offset > 720 -> candidate.minus(Duration.ofDays(1))
            offset < -720 -> candidate.plus(Duration.ofDays(1))
            else -> candidate
        }
    }

    private class Position(val declination: Double, val equationOfTimeMinutes: Double)

    private fun position(at: Instant): Position {
        val julianDay = at.toEpochMilli() / 86_400_000.0 + 2_440_587.5
        val t = (julianDay - 2_451_545.0) / 36_525.0
        val meanLongitude = (280.46646 + t * (36_000.76983 + t * 0.0003032)).mod(360.0)
        val meanAnomaly = 357.52911 + t * (35_999.05029 - 0.0001537 * t)
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val m = rad(meanAnomaly)
        val centre = sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * m) * (0.019993 - 0.000101 * t) +
            sin(3 * m) * 0.000289
        val omega = rad(125.04 - 1934.136 * t)
        val apparentLongitude = meanLongitude + centre - 0.00569 - 0.00478 * sin(omega)
        val meanObliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val obliquity = rad(meanObliquity + 0.00256 * cos(omega))
        val declination = asin(sin(obliquity) * sin(rad(apparentLongitude)))
        val y = tan(obliquity / 2).let { it * it }
        val l0 = rad(meanLongitude)
        val equationOfTime = 4.0 * deg(
            y * sin(2 * l0) - 2 * eccentricity * sin(m) + 4 * eccentricity * y * sin(m) * cos(2 * l0) -
                0.5 * y * y * sin(4 * l0) - 1.25 * eccentricity * eccentricity * sin(2 * m),
        )
        return Position(declination, equationOfTime)
    }

    private fun rad(degrees: Double) = Math.toRadians(degrees)
    private fun deg(radians: Double) = Math.toDegrees(radians)
}
