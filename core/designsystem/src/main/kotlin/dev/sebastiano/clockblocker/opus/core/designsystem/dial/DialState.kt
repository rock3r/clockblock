package dev.sebastiano.clockblocker.opus.core.designsystem.dial

import androidx.compose.runtime.Immutable
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.atan2

/** Pure geometry of the 24 h dial: **noon at the top, midnight at the bottom**, time runs clockwise. */
object DialGeometry {
    const val MinutesPerDay = 1440f

    /** Canvas angle in degrees (0° = 3 o'clock, clockwise positive) of a minute of the day. */
    fun angleForMinute(minuteOfDay: Float): Float = (minuteOfDay / MinutesPerDay * 360f + 90f).mod(360f)

    /** Inverse of [angleForMinute]: minute of the day [0, 1440) at a canvas angle. */
    fun minuteForAngle(angleDegrees: Float): Float = ((angleDegrees - 90f).mod(360f)) / 360f * MinutesPerDay

    /** Canvas angle of a point relative to the dial centre. */
    fun angleOf(dx: Float, dy: Float): Float = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat().mod(360f)

    /** Signed shortest difference `to - from` in minutes, in [-720, 720). */
    fun minuteDelta(from: Float, to: Float): Float = ((to - from + 720f).mod(MinutesPerDay)) - 720f

    /**
     * Offset of [minuteOfDay] from [nowMinute] inside the dial's window, in
     * [-[DialState.PastWindowMinutes], 1440 - [DialState.PastWindowMinutes]): the dial shows 8 h of past and 16 h of future.
     */
    fun relativeMinute(nowMinute: Float, minuteOfDay: Float): Float =
        ((minuteOfDay - nowMinute + DialState.PastWindowMinutes).mod(MinutesPerDay)) - DialState.PastWindowMinutes

    /** Signed shortest angular difference `to - from` in degrees, in [-180, 180). */
    fun angleDelta(from: Float, to: Float): Float = ((to - from + 180f).mod(360f)) - 180f

    fun minuteOfDay(time: LocalTime): Float = time.toSecondOfDay() / 60f

    fun timeOf(minuteOfDay: Float): LocalTime =
        LocalTime.ofSecondOfDay(((minuteOfDay.mod(MinutesPerDay)) * 60f).toLong().coerceIn(0, 86_399))
}

/**
 * One advice window projected onto the dial, in display-zone minutes of the day. [startInstant] / [endInstant] are
 * the advice's real start and end (never clipped to the window, nor wrapped round the face): what orders blocks of
 * 24 h or more, or across a DST change, where minutes of the day can't. Null when unknown (hand-built states).
 */
@Immutable
data class DialArc(
    val adviceId: String,
    val type: AdviceType,
    val startMinute: Float,
    /** 0 for instantaneous advice (melatonin): drawn as a dot. */
    val sweepMinutes: Float,
    val isNow: Boolean = false,
    /** Where the advice really ends (display-zone minute), even when the arc is clipped to the dial's window. */
    val narratedEndMinute: Float = (startMinute + sweepMinutes).mod(DialGeometry.MinutesPerDay),
    val startInstant: Instant? = null,
    val endInstant: Instant? = null,
) {
    val endMinute: Float get() = (startMinute + sweepMinutes).mod(DialGeometry.MinutesPerDay)
}

/** An advice block for narration ("Now: see bright light until 15:00"). Times are display-zone local. */
@Immutable
data class DialAdvice(
    val type: AdviceType,
    val start: LocalTime,
    val end: LocalTime,
    val startInstant: Instant,
    val endInstant: Instant,
)

/** Whether the sun rises and sets on the dial's day, or stays up (midnight sun) or down (polar night) all day. */
enum class Daylight { RisesAndSets, AlwaysUp, AlwaysDown }

/**
 * UI model of the Two skies dial at one instant. Build it with `JetLagPlan.toDialState(instant, zone, place)`.
 *
 * The outer (local) sky is drawn in display-zone local minutes. The inner body sky is drawn in *body* minutes and
 * turned by [bodyAheadMinutes], so it visibly turns towards alignment day by day.
 */
@Immutable
data class DialState(
    val instant: Instant,
    val displayZoneId: String,
    /** Display-zone local minute of the day at [instant]. */
    val localMinute: Float,
    /** Body clock minus local clock, in minutes, in [-720, 720). Negative = body is behind (flew east). */
    val bodyAheadMinutes: Float,
    /** Core-body-temperature minimum, as a body-clock minute of the day. */
    val cbtMinBodyMinute: Float = 270f,
    /** Biological night (≈ DLMO → habitual wake) in body-clock minutes. */
    val biologicalNightStartBodyMinute: Float = (cbtMinBodyMinute - 360f).mod(DialGeometry.MinutesPerDay),
    val biologicalNightEndBodyMinute: Float = (cbtMinBodyMinute + 150f).mod(DialGeometry.MinutesPerDay),
    val arcs: ImmutableList<DialArc> = persistentListOf(),
    val now: DialAdvice? = null,
    val next: DialAdvice? = null,
    val dayKind: DayKind? = null,
    val dayIndex: Int? = null,
    /**
     * Local sunrise/sunset (display-zone minutes) for the sky rings, from the sun at the place
     * ([dev.sebastiano.clockblocker.opus.core.model.Sun]); 06:30/19:00 when the location is unknown. On a polar
     * day or night ([daylight]) both hold solar noon, which the sky centres on.
     */
    val sunriseMinute: Float = 390f,
    val sunsetMinute: Float = 1140f,
    val daylight: Daylight = Daylight.RisesAndSets,
    /**
     * The trip's stop in [displayZoneId] (e.g. "Tromsø", which keeps Oslo's zone id), named on the dial; null, or too
     * long to fit, falls back to the zone's city.
     */
    val placeName: String? = null,
) {
    val bodyMinute: Float get() = (localMinute + bodyAheadMinutes).mod(DialGeometry.MinutesPerDay)
    val localTime: LocalTime get() = DialGeometry.timeOf(localMinute)
    val bodyTime: LocalTime get() = DialGeometry.timeOf(bodyMinute)

    /** Hours the local clock is ahead of the body (internal geometry; labels show [bodyOffsetHours]). */
    val jetLagHours: Float get() = -bodyAheadMinutes / 60f

    /** Body clock relative to local time, in hours: −3.5 = body 3½ h behind. The convention every label uses. */
    val bodyOffsetHours: Float get() = bodyAheadMinutes / 60f

    /** The two skies line up: the dial says "in sync" and the rings "click". */
    val isAligned: Boolean get() = abs(bodyAheadMinutes) < AlignedThresholdMinutes

    /** Display-zone minute at which the body clock reads [bodyMinute]. */
    fun localMinuteForBody(bodyMinute: Float): Float = (bodyMinute - bodyAheadMinutes).mod(DialGeometry.MinutesPerDay)

    /** Shift the readouts to another instant inside the dial's window (scrubbing). */
    fun scrubbedTo(minutesFromNow: Float): DialState = copy(
        instant = instant.plusSeconds((minutesFromNow * 60f).toLong()),
        localMinute = (localMinute + minutesFromNow).mod(DialGeometry.MinutesPerDay),
    )

    companion object {
        const val AlignedThresholdMinutes = 30f

        /** The dial covers 24 h: 8 h of past (dimmed) and 16 h ahead, so tonight is always visible. */
        const val PastWindowMinutes = 480f
    }
}

/**
 * "+5 h", "+4½ h", "−2 h", "0 h": rounded to the nearest half hour.
 *
 * App-wide convention when this labels the body clock (plan header, trip cards): pass the body clock relative
 * to local time ([DialState.bodyOffsetHours]), so "−3½ h" means the body is 3½ h *behind* local time. Words
 * ("3½ h behind") use [formatHoursMagnitude] with a behind/ahead string. Trip shifts ("+8 h east") are a
 * different quantity and keep their own sign.
 */
fun formatJetLagHours(hours: Float): String {
    val halves = Math.round(hours * 2f)
    if (halves == 0) return "0 h"
    val sign = if (halves > 0) "+" else "\u2212"
    return sign + formatHoursMagnitude(hours)
}

/** "3½ h", "½ h", "0 h": the unsigned magnitude of [hours], rounded to the nearest half hour. */
fun formatHoursMagnitude(hours: Float): String {
    val halves = abs(Math.round(hours * 2f))
    val whole = halves / 2
    val half = halves % 2 == 1
    val number = when {
        whole == 0 && half -> "\u00BD"
        half -> "$whole\u00BD"
        else -> "$whole"
    }
    return "$number h"
}

/** Where the body clock sits relative to local time. */
enum class BodyOffsetDirection { InSync, Behind, Ahead }

/**
 * [BodyOffsetDirection] of a body offset in hours (body − local). In sync below the dial's alignment threshold
 * ([DialState.AlignedThresholdMinutes]), exactly when the dial says "in sync", so header and dial agree.
 */
fun bodyOffsetDirection(bodyOffsetHours: Float): BodyOffsetDirection = when {
    abs(bodyOffsetHours) * 60f < DialState.AlignedThresholdMinutes -> BodyOffsetDirection.InSync
    bodyOffsetHours < 0f -> BodyOffsetDirection.Behind
    else -> BodyOffsetDirection.Ahead
}
