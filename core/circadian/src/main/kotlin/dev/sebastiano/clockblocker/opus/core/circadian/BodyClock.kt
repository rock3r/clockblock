package dev.sebastiano.clockblocker.opus.core.circadian

import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * A [PlanDay] with the instants it covers, `[start, end)`. Consecutive spans of a plan touch: one ends where the
 * next starts, so every instant between the first day's start and the last day's end belongs to exactly one day.
 */
data class PlanDaySpan(val day: PlanDay, val start: Instant, val end: Instant) {
    operator fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)
}

/**
 * What the body clock reads at [instant]: the wall-clock time in a fictional zone at the estimated body offset
 * ("your body thinks it's 03:12").
 */
fun JetLagPlan.bodyClockTimeAt(instant: Instant): LocalTime = instant.atOffset(bodyOffsetAt(instant)).toLocalTime()

/**
 * True when the body clock at [instant] reads inside the habitual [sleep] window (the window is in home time,
 * which is what an entrained body clock reads). This is "body night": the app moves calmly then, whatever the
 * local clock says and independently of Night-safe colours.
 */
fun JetLagPlan.isBodyNightAt(instant: Instant, sleep: SleepWindow): Boolean = bodyClockTimeAt(instant).isWithin(sleep)

/** True when [this] clock time falls in `[bedtime, wake)`, handling windows that wrap around midnight. */
fun LocalTime.isWithin(sleep: SleepWindow): Boolean {
    val bed = sleep.bedtime
    val wake = sleep.wake
    return if (bed < wake) this >= bed && this < wake else this >= bed || this < wake
}

/**
 * Share of the planned shift (`shiftHours`) the body clock has completed at [instant], in `0f..1f`.
 *
 * Stored offsets are clock values that may wrap around the 24 h dial, so the trajectory is unwrapped along the
 * shortest arc between consecutive points before measuring how far it moved from the first point. Movement
 * against the planned direction counts as zero, overshoot as one. A plan with nothing to shift is adapted (1f).
 */
fun JetLagPlan.adaptationProgressAt(instant: Instant): Float {
    if (abs(shiftHours) < 1e-9) return 1f
    if (phase.isEmpty()) return 0f
    val unwrapped = DoubleArray(phase.size)
    unwrapped[0] = phase[0].bodyUtcOffsetMinutes.toDouble()
    for (i in 1 until phase.size) {
        val diff = Math.floorMod(phase[i].bodyUtcOffsetMinutes - phase[i - 1].bodyUtcOffsetMinutes + 720, 1440) - 720
        unwrapped[i] = unwrapped[i - 1] + diff
    }
    val after = phase.indexOfFirst { !it.instant.isBefore(instant) }
    val minutes = when (after) {
        -1 -> unwrapped.last()
        0 -> unwrapped.first()
        else -> {
            val a = phase[after - 1].instant
            val span = Duration.between(a, phase[after].instant).toMillis().toDouble()
            val t = if (span == 0.0) 1.0 else Duration.between(a, instant).toMillis() / span
            unwrapped[after - 1] + (unwrapped[after] - unwrapped[after - 1]) * t
        }
    }
    val progress = (minutes - unwrapped[0]) / 60.0 / shiftHours
    return progress.coerceIn(0.0, 1.0).toFloat()
}

/**
 * The instants each [PlanDay] covers, reconstructed from the plan alone (same rules the planner used to assign
 * cards to days):
 * - a day starts at local midnight of its date in its zone;
 * - except the day after a Travel day, which starts at the arrival (the latest flight end of that Travel day)
 *   when that is later than midnight: a morning arrival does not leave a stub before landing;
 * - each day ends where the next starts; the last day ends at the following local midnight.
 */
fun JetLagPlan.daySpans(): List<PlanDaySpan> {
    val starts = days.mapIndexed { i, day ->
        val midnight = day.date.atStartOfDay(ZoneId.of(day.zoneId)).toInstant()
        val previous = days.getOrNull(i - 1)
        val landed = previous?.takeIf { it.kind == DayKind.Travel }
            ?.advice?.filter { it.type == AdviceType.Flight }?.maxOfOrNull { it.end }
        if (landed != null && landed.isAfter(midnight)) landed else midnight
    }
    return days.mapIndexed { i, day ->
        val end = starts.getOrNull(i + 1) ?: day.date.plusDays(1).atStartOfDay(ZoneId.of(day.zoneId)).toInstant()
        PlanDaySpan(day, starts[i], end)
    }
}

/** The plan day whose span contains [instant] (see [daySpans]), or null before the first or after the last day. */
fun JetLagPlan.currentDay(instant: Instant): PlanDay? = daySpans().firstOrNull { instant in it }?.day
