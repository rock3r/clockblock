package dev.sebastiano.clockblocker.opus.widget.state

import dev.sebastiano.clockblocker.opus.core.circadian.adaptationProgressAt
import dev.sebastiano.clockblocker.opus.core.circadian.currentDay
import dev.sebastiano.clockblocker.opus.core.circadian.displayZonesAt
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.toDialState
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Trip
import java.time.Instant
import java.time.ZoneId
import kotlinx.collections.immutable.toImmutableList

/** Plan → [WidgetState]. Pure and deterministic for a given `now`. */
object WidgetStateMapper {

    /**
     * @param logs outcomes logged for the plan's trip (drives the Done button's logged state); null when they could
     *   not be read, so the outcome is unknown.
     * @param route the trip's airport codes, if the trip could be read.
     * @param placeNames the trip's city per zone ([placeNames] of the trip), if the trip could be read.
     * @param placeCodes the IATA code of each of those places ([placeCodes] of the trip).
     * @param places the place that names each zone ([places] of the trip): the dial's sky follows its sun.
     */
    fun map(
        plan: JetLagPlan?,
        now: Instant,
        logs: List<AdviceLog>? = emptyList(),
        route: WidgetRoute? = null,
        placeNames: Map<String, String> = emptyMap(),
        placeCodes: Map<String, String> = emptyMap(),
        places: Map<String, Place> = emptyMap(),
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
            dial = plan.toDialState(now, displayZone, places[displayZoneId]),
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
            placeCodes = placeCodes,
        )
    }

    /**
     * The trip's city for each zone it visits: "San Francisco" for an SFO trip, where the zone alone
     * (America/Los_Angeles) would say Los Angeles. The trip's origin and destination win over connections in the same
     * zone; places without a city are left to the zone's name.
     */
    fun placeNames(trip: Trip): Map<String, String> = places(trip).mapValues { it.value.city }

    /** The IATA code of each place in [placeNames], where it has one: the short form of a long city name. */
    fun placeCodes(trip: Trip): Map<String, String> =
        places(trip).mapValues { it.value.code }.filterValues { it.isNotBlank() }

    /** The place that names each zone of [trip], with [placeNames]' precedence (its sun lights the dial's sky). */
    fun places(trip: Trip): Map<String, Place> = buildMap {
        fun add(place: Place) {
            if (place.city.isNotBlank()) put(place.zoneId, place)
        }
        trip.legs.forEach { add(it.origin); add(it.destination) }
        add(trip.origin)
        add(trip.destination)
    }

    /**
     * The lock-screen version of [state] (Settings › Hide details on the lock screen, keyguard hosts only), matching
     * the redacted notifications: no secondary zone (it names a city), no route or place names, and private advice
     * (melatonin) leaves the dial and loses its name and glyph in the texts. Times and the other blocks stay.
     */
    fun redact(state: WidgetState): WidgetState = when (state) {
        WidgetState.NoTrip -> state
        is WidgetState.Active -> state.copy(
            secondaryZoneId = null,
            route = null,
            placeNames = emptyMap(),
            placeCodes = emptyMap(),
            dial = state.dial.copy(
                arcs = state.dial.arcs.filterNot { it.type.isPrivate }.toImmutableList(),
                now = state.dial.now?.takeUnless { it.type.isPrivate },
                next = state.dial.next?.takeUnless { it.type.isPrivate },
                placeName = null,
            ),
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

    private fun Advice.toSlot(zone: ZoneId) = AdviceSlot(
        adviceId = id,
        type = type,
        start = start,
        end = end,
        startMinute = DialMath.minuteOfDay(start, zone),
        endMinute = DialMath.minuteOfDay(end, zone),
    )
}
