package dev.sebastiano.clockblocker.opus.widget.state

import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Plan → [WidgetState]. Pure and deterministic for a given `now`. */
object WidgetStateMapper {

    private val WINDOW: Duration = Duration.ofHours(24)

    /** Biological night relative to CBTmin: roughly DLMO+2 h … habitual wake (CBTmin + 2 h). */
    private val NIGHT_BEFORE_CBT_MIN: Duration = Duration.ofHours(6)
    private val NIGHT_LENGTH: Duration = Duration.ofHours(8)

    /** Body-clock night used when the plan has no phase trajectory: body 23:00 – 07:00. */
    private const val DEFAULT_BODY_NIGHT_START = 23 * 60

    /**
     * @param fallbackZone zone used when the plan has no days (the device's zone).
     */
    fun map(plan: JetLagPlan?, now: Instant, fallbackZone: ZoneId): WidgetState {
        if (plan == null) return WidgetState.NoTrip

        val displayZoneId = displayZone(plan, now) ?: fallbackZone.id
        val displayZone = ZoneId.of(displayZoneId)
        val displayOffset = displayZone.rules.getOffset(now).totalSeconds / 60
        val bodyOffset = plan.bodyOffsetAt(now).totalSeconds / 60

        val all = plan.allAdvice
        val current = plan.activeAt(now).firstOrNull()
        val next = nextAfter(all, current, now)

        val stage = when {
            all.isEmpty() -> WidgetState.Stage.Done
            all.all { it.start.isAfter(now) } -> WidgetState.Stage.Upcoming
            current == null && next == null -> WidgetState.Stage.Done
            else -> WidgetState.Stage.InProgress
        }

        val nearestPhase = plan.phase.minByOrNull { Duration.between(it.instant, now).abs() }
        val cbtMinMinute = nearestPhase?.let { DialMath.minuteOfDay(it.cbtMin, displayZone) }
        val bodyNight = if (nearestPhase != null) {
            val start = nearestPhase.cbtMin.minus(NIGHT_BEFORE_CBT_MIN)
            DialArc(null, DialMath.minuteOfDay(start, displayZone), NIGHT_LENGTH.toMinutes().toInt())
        } else {
            val misalignment = displayOffset - bodyOffset
            DialArc(null, DialMath.wrap(DEFAULT_BODY_NIGHT_START + misalignment), NIGHT_LENGTH.toMinutes().toInt())
        }

        val secondary = listOf(plan.destinationZoneId, plan.originZoneId).firstOrNull { it != displayZoneId }

        return WidgetState.Active(
            tripId = plan.tripId,
            capturedAt = now,
            displayZoneId = displayZoneId,
            secondaryZoneId = secondary,
            displayOffsetMinutes = displayOffset,
            bodyOffsetMinutes = bodyOffset,
            current = current?.toSlot(displayZone),
            next = next?.toSlot(displayZone),
            arcs = arcs(all, now, displayZone),
            bodyNight = bodyNight,
            cbtMinMinute = cbtMinMinute,
            stage = stage,
            destinationName = DialMath.cityName(plan.destinationZoneId),
        )
    }

    /** The zone of the latest plan day that has started by [now] (first day before the plan starts). */
    private fun displayZone(plan: JetLagPlan, now: Instant): String? {
        val days = plan.days.sortedBy { it.date.atStartOfDay(ZoneId.of(it.zoneId)).toInstant() }
        val started = days.lastOrNull { !it.date.atStartOfDay(ZoneId.of(it.zoneId)).toInstant().isAfter(now) }
        return (started ?: days.firstOrNull())?.zoneId
    }

    /** "Then …": the first block starting once the current one is over (ties broken by priority). */
    private fun nextAfter(all: List<Advice>, current: Advice?, now: Instant): Advice? {
        val from = current?.end ?: now
        return all
            .filter { if (current == null) it.start.isAfter(now) else !it.start.isBefore(from) && it != current }
            .minWithOrNull(compareBy<Advice> { it.start }.thenBy { it.type.ordinal })
    }

    private fun arcs(all: List<Advice>, now: Instant, zone: ZoneId): List<DialArc> {
        val windowEnd = now.plus(WINDOW)
        return all
            .sortedByDescending { it.type.ordinal } // low priority first so high priority paints on top
            .mapNotNull { advice ->
                if (advice.type.isMoment) {
                    if (advice.start.isBefore(now) || !advice.start.isBefore(windowEnd)) return@mapNotNull null
                    return@mapNotNull DialArc(advice.type, DialMath.minuteOfDay(advice.start, zone), 0)
                }
                val start = maxOf(advice.start, now)
                val end = minOf(advice.end, windowEnd)
                if (!end.isAfter(start)) return@mapNotNull null
                val startMinute = DialMath.minuteOfDay(start, zone)
                val sweep = if (Duration.between(start, end) >= WINDOW) {
                    DialMath.MINUTES_PER_DAY
                } else {
                    DialMath.wrap(DialMath.minuteOfDay(end, zone) - startMinute)
                        .takeIf { it > 0 } ?: return@mapNotNull null
                }
                DialArc(advice.type, startMinute, sweep)
            }
    }

    private fun Advice.toSlot(zone: ZoneId) = AdviceSlot(
        adviceId = id,
        type = type,
        start = start,
        end = end,
        startMinute = DialMath.minuteOfDay(start, zone),
        endMinute = DialMath.minuteOfDay(end, zone),
    )
}
