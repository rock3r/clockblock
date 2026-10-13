package dev.sebastiano.clockblocker.opus.core.data.trip

import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.zacsweers.metro.Inject
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * Turns an outbound trip into a prefilled return trip: legs reversed (B→A, connections kept), flight
 * numbers cleared, times estimated so the user only has to correct them.
 *
 * Timing: the first return flight departs at [departureLocal] if given, else at the outbound's
 * `returnDeparture`, else [DefaultStay] after landing at the outbound's departure time of day. Each
 * return leg reuses the outbound leg's block time and each connection the outbound layover, and every local
 * time is derived through the IANA zones, so DST and the date line come out right.
 */
@Inject
class ReturnTripFactory(
    private val clock: Clock,
    private val titles: TripTitleSuggester,
) {

    fun create(
        outbound: Trip,
        departureLocal: LocalDateTime? = null,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): Trip {
        val out = outbound.legs
        val firstDeparture = departureLocal
            ?: outbound.returnDeparture?.atZone(outbound.destination.zone)?.toLocalDateTime()
            ?: out.last().arrivalLocal.toLocalDate().plusDays(DefaultStay.toDays())
                .atTime(out.first().departureLocal.toLocalTime())

        val legs = ArrayList<FlightLeg>(out.size)
        var departure = firstDeparture.atZone(out.last().destination.zone).toInstant()
        for (k in out.indices.reversed()) {
            val source = out[k]
            if (legs.isNotEmpty()) {
                // Reuse the outbound connection at this airport.
                val layover = Duration.between(source.arrival, out[k + 1].departure)
                departure = legs.last().arrival.plus(if (layover < MinLayover) DefaultLayover else layover)
            }
            val arrival = departure.plus(blockTime(source))
            legs += FlightLeg(
                id = newId(),
                origin = source.destination,
                destination = source.origin,
                departureLocal = departure.atZone(source.destination.zone).toLocalDateTime(),
                arrivalLocal = arrival.atZone(source.origin.zone).toLocalDateTime(),
                flightNumber = null,
            )
        }
        return Trip(
            id = newId(),
            title = titles.suggest(legs),
            legs = legs,
            createdAt = clock.instant(),
        )
    }

    /**
     * The outbound trip linked to [returnTrip]: its `returnDeparture` set (which lets the planner detect short stays)
     * and its `returnTripId` pointing at it (so the editor can move or reuse it later).
     */
    fun linkOutbound(outbound: Trip, returnTrip: Trip): Trip =
        outbound.copy(returnDeparture = returnTrip.departure, returnTripId = returnTrip.id)

    /**
     * [returnTrip] moved so it departs at [departureLocal] (local time where it leaves): every leg shifts by the same
     * amount of real time, so flight lengths and connections stay as they were, and each wall-clock time is derived
     * again through its airport's zone (DST-safe). Everything else the user set on it (flight numbers, title, …) stays.
     */
    fun moveTo(returnTrip: Trip, departureLocal: LocalDateTime): Trip {
        val shift = Duration.between(returnTrip.departure, departureLocal.atZone(returnTrip.origin.zone).toInstant())
        if (shift.isZero) return returnTrip
        return returnTrip.copy(
            legs = returnTrip.legs.map { leg ->
                leg.copy(
                    departureLocal = leg.departure.plus(shift).atZone(leg.origin.zone).toLocalDateTime(),
                    arrivalLocal = leg.arrival.plus(shift).atZone(leg.destination.zone).toLocalDateTime(),
                )
            },
        )
    }

    private fun blockTime(leg: FlightLeg): Duration {
        val d = leg.duration
        if (d > Duration.ZERO) return d
        // The outbound leg itself is invalid: estimate from distance at airliner speed plus taxi time.
        return FlightEstimates.blockTime(leg.origin, leg.destination)
    }

    companion object {
        val DefaultStay: Duration = Duration.ofDays(7)
        val MinLayover: Duration = Duration.ofMinutes(45)
        val DefaultLayover: Duration = Duration.ofHours(2)
    }
}
