package dev.sebastiano.clockblocker.opus.core.data.trip

import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Trip
import java.time.Duration

/**
 * Finds the trip that flies back from an outbound trip, so the editor's Return flight switch moves, reuses or
 * deletes it instead of making a second one (#150 review).
 */
object ReturnTripLinks {

    /** How long after landing a reversed trip still counts as this trip's way back when relinking one. */
    val MaxStay: Duration = Duration.ofDays(120)

    /**
     * The return trip linked to [outbound] among [all]: the one its `returnTripId` names; else, for trips saved before
     * that id existed, the reversed trip departing exactly at its `returnDeparture` that no other trip names by id.
     * Null when there's none.
     */
    fun linked(outbound: Trip, all: List<Trip>): Trip? {
        outbound.returnTripId?.let { id -> all.firstOrNull { it.id == id && it.id != outbound.id }?.let { return it } }
        val back = outbound.returnDeparture ?: return null
        // A trip another one names by id is that trip's return (a duplicate shares its return date, not its return).
        val claimedById = all.filter { it.id != outbound.id }.mapNotNull { it.returnTripId }.toSet()
        return all.firstOrNull { it.id != outbound.id && it.id !in claimedById && it.departure == back && it.reverses(outbound) }
    }

    /**
     * A return trip to relink when the Return flight switch is turned back on: [linked] if there is one, else the
     * soonest reversed trip leaving after [outbound] lands (within [MaxStay]) that no other trip claims as its own.
     */
    fun reusable(outbound: Trip, all: List<Trip>): Trip? {
        linked(outbound, all)?.let { return it }
        val claimed = all.filter { it.id != outbound.id }.mapNotNull { linked(it, all)?.id }.toSet()
        return all
            .filter { it.id != outbound.id && it.id !in claimed && it.reverses(outbound) }
            .filter { it.departure.isAfter(outbound.arrival) && it.departure.isBefore(outbound.arrival.plus(MaxStay)) }
            .minByOrNull { it.departure }
    }

    /** The trip whose return trip is [returnTrip], if any. */
    fun outboundOf(returnTrip: Trip, all: List<Trip>): Trip? =
        all.firstOrNull { it.id != returnTrip.id && linked(it, all)?.id == returnTrip.id }

    /** This trip flies from where [outbound] ends back to where it started. */
    fun Trip.reverses(outbound: Trip): Boolean = origin.samePlace(outbound.destination) && destination.samePlace(outbound.origin)

    /** Same airport (by code), or for plain cities the same city in the same zone. */
    private fun Place.samePlace(other: Place): Boolean =
        if (code.isNotBlank() || other.code.isNotBlank()) code == other.code else city == other.city && zoneId == other.zoneId
}
