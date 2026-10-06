package dev.sebastiano.clockblocker.opus.widget.state

import dev.sebastiano.clockblocker.opus.core.circadian.adaptationProgressAt
import dev.sebastiano.clockblocker.opus.core.circadian.currentDay
import dev.sebastiano.clockblocker.opus.core.circadian.displayZonesAt
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Trip
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
     * @param logs outcomes logged for the plan's trip (drives the Done button's logged state); null when they could
     *   not be read, so the outcome is unknown.
     * @param route the trip's airport codes, if the trip could be read.
     * @param placeNames the trip's city per zone ([placeNames] of the trip), if the trip could be read.
     */
    fun map(
        plan: JetLagPlan?,
        now: Instant,
        logs: List<AdviceLog>? = emptyList(),
        route: WidgetRoute? = null,
        placeNames: Map<String, String> = emptyMap(),
    ): WidgetState {
        if (plan == null) return WidgetState.NoTrip

        // Same local and secondary zones as the plan screen and the notifications (never the device's zone).
        val zones = plan.displayZonesAt(now)
        val displayZone = zones.local
        val displayZoneId = displayZone.id
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

        val secondary = zones.secondary.id.takeIf { it != displayZoneId }
        val upcoming = all
            .filter { it.start.isAfter(now) && it != current }
            .sortedWith(compareBy<Advice> { it.start }.thenBy { it.type.ordinal })
            .take(UPCOMING_COUNT)
        val day = plan.currentDay(now)

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
            destinationName = placeNames[plan.destinationZoneId] ?: DialMath.cityName(plan.destinationZoneId),
            upcoming = upcoming.map { it.toSlot(displayZone) },
            currentOutcome = current?.let { c -> logs?.lastOrNull { it.adviceId == c.id }?.outcome },
            outcomeKnown = logs != null,
            dayKind = day?.kind,
            dayIndex = day?.index,
            adaptation = plan.adaptationProgressAt(now),
            route = route,
            placeNames = placeNames,
        )
    }

    /**
     * The trip's city for each zone it visits: "San Francisco" for an SFO trip, where the zone alone
     * (America/Los_Angeles) would say Los Angeles. The trip's origin and destination win over connections in the same
     * zone; places without a city are left to the zone's name.
     */
    fun placeNames(trip: Trip): Map<String, String> = buildMap {
        fun add(place: Place) {
            if (place.city.isNotBlank()) put(place.zoneId, place.city)
        }
        trip.legs.forEach { add(it.origin); add(it.destination) }
        add(trip.origin)
        add(trip.destination)
    }

    /**
     * The lock-screen version of [state] (Settings › Hide details on the lock screen, keyguard hosts only), matching
     * the redacted notifications: no secondary zone (it names a city), no route or place names, and private advice
     * (melatonin) loses its dial dot here and its name and glyph in the texts. Times and the other blocks stay.
     */
    fun redact(state: WidgetState): WidgetState = when (state) {
        WidgetState.NoTrip -> state
        is WidgetState.Active -> state.copy(
            secondaryZoneId = null,
            route = null,
            placeNames = emptyMap(),
            arcs = state.arcs.filterNot { it.type?.isPrivate == true },
            redacted = true,
        )
    }

    private const val UPCOMING_COUNT = 3

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
