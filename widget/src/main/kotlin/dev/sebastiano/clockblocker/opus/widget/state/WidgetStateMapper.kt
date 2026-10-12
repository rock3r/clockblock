package dev.sebastiano.clockblocker.opus.widget.state

import dev.sebastiano.clockblocker.opus.core.circadian.adaptationProgressAt
import dev.sebastiano.clockblocker.opus.core.circadian.currentDay
import dev.sebastiano.clockblocker.opus.core.circadian.displayZonesAt
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.toDialState
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GeoPoint
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeGeo
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeLeg
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeStop
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Sun
import dev.sebastiano.clockblocker.opus.core.model.Trip
import java.time.Duration
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
     * @param trip the trip itself, so the route globe can frame the whole route before departure or the active leg
     *   during the trip.
     */
    fun map(
        plan: JetLagPlan?,
        now: Instant,
        logs: List<AdviceLog>? = emptyList(),
        route: WidgetRoute? = null,
        placeNames: Map<String, String> = emptyMap(),
        placeCodes: Map<String, String> = emptyMap(),
        places: Map<String, Place> = emptyMap(),
        trip: Trip? = null,
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
            globe = globe(trip, now, stage, places, plan.originZoneId, plan.destinationZoneId),
        )
    }

    /**
     * The route globe for [trip] at [now]: before the first flight departs (or while [stage] is [WidgetState.Stage.Upcoming])
     * it frames the whole route (`activeLeg = null`, no traveller marker); once the trip has started it highlights the
     * active leg (in flight, the next flight during a layover, or the final leg after landing) and places the marker
     * along the route. When [trip] isn't given, a single leg is built from the endpoints in [places] if both are known.
     */
    fun globe(
        trip: Trip?,
        now: Instant,
        stage: WidgetState.Stage,
        places: Map<String, Place> = emptyMap(),
        originZoneId: String? = null,
        destinationZoneId: String? = null,
    ): WidgetGlobe {
        val subsolar = Sun.subsolarPoint(now)
        if (trip != null && trip.legs.isNotEmpty()) {
            val legs = trip.legs.map { leg ->
                GlobeLeg(
                    from = GlobeStop(GeoPoint(leg.origin.latitude, leg.origin.longitude), leg.origin.displayCode),
                    to = GlobeStop(GeoPoint(leg.destination.latitude, leg.destination.longitude), leg.destination.displayCode),
                )
            }
            val first = trip.legs.first()
            if (now.isBefore(first.departure) || stage == WidgetState.Stage.Upcoming) {
                return WidgetGlobe(legs = legs, activeLeg = null, marker = null, subsolar = subsolar)
            }
            for (i in trip.legs.indices) {
                val leg = trip.legs[i]
                if (now.isBefore(leg.departure)) {
                    return WidgetGlobe(legs = legs, activeLeg = i, marker = legs[i].from.point, subsolar = subsolar)
                }
                if (now.isBefore(leg.arrival)) {
                    val span = Duration.between(leg.departure, leg.arrival).toMillis().toDouble()
                    val elapsed = Duration.between(leg.departure, now).toMillis().toDouble()
                    val fraction = if (span > 0.0) (elapsed / span).coerceIn(0.0, 1.0) else 0.0
                    val marker = GlobeGeo.interpolate(legs[i].from.point, legs[i].to.point, fraction)
                    return WidgetGlobe(legs = legs, activeLeg = i, marker = marker, subsolar = subsolar)
                }
            }
            return WidgetGlobe(legs = legs, activeLeg = legs.lastIndex, marker = legs.last().to.point, subsolar = subsolar)
        }
        val origin = originZoneId?.let(places::get)
        val destination = destinationZoneId?.let(places::get)
        if (origin != null && destination != null) {
            val from = GlobeStop(GeoPoint(origin.latitude, origin.longitude), origin.displayCode)
            val to = GlobeStop(GeoPoint(destination.latitude, destination.longitude), destination.displayCode)
            val upcoming = stage == WidgetState.Stage.Upcoming
            return WidgetGlobe(
                legs = listOf(GlobeLeg(from, to)),
                activeLeg = if (upcoming) null else 0,
                marker = if (upcoming) null else to.point,
                subsolar = subsolar,
            )
        }
        return WidgetGlobe(legs = emptyList(), activeLeg = null, marker = null, subsolar = subsolar)
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
     * [state] with the Now notification's own pick (#49): its [headline] as the current block, its [next] window as
     * what starts next (the only upcoming row), and what was logged for the headline. The notification's actions act
     * on that headline, and its documents must name the same advice; the widgets pick differently (plan order, with
     * moments), the notification puts sleep first and leaves moments to their reminders.
     */
    fun forNowNotification(state: WidgetState.Active, headline: Advice?, next: Advice?, outcome: AdviceOutcome?): WidgetState.Active {
        val zone = ZoneId.of(state.displayZoneId)
        return state.copy(
            current = headline?.toSlot(zone),
            next = next?.toSlot(zone),
            upcoming = listOfNotNull(next?.toSlot(zone)),
            currentOutcome = outcome?.takeIf { headline != null },
            outcomeKnown = true,
        )
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
            globe = state.globe.copy(legs = emptyList(), activeLeg = null, marker = null),
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
