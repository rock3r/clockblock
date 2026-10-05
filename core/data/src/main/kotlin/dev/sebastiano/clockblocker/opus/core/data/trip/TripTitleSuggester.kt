package dev.sebastiano.clockblocker.opus.core.data.trip

import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.zacsweers.metro.Inject

/**
 * Suggests a trip title from its route, so manual entry never needs typing a name.
 *
 * - One-way, any number of legs: "Lisbon → Tokyo" (connections are details, not the title).
 * - Ends where it started: "Lisbon → Tokyo → Lisbon", via the stop farthest from home.
 * - Same city at both ends with different airports: codes disambiguate, "London (LHR) → London (LCY)".
 */
@Inject
class TripTitleSuggester {

    fun suggest(trip: Trip): String = suggest(trip.legs)

    /** Empty for no legs, so callers can fall back to their own (localised) placeholder. */
    fun suggest(legs: List<FlightLeg>): String {
        if (legs.isEmpty()) return ""
        val origin = legs.first().origin
        val destination = legs.last().destination
        if (label(origin) != label(destination)) return label(origin) + Arrow + label(destination)

        val farthest = legs.dropLast(1).map { it.destination }
            .filter { label(it) != label(origin) }
            .maxByOrNull { TripValidator.distanceKm(origin, it) }
        return when {
            farthest != null -> listOf(origin, farthest, destination).joinToString(Arrow, transform = ::label)
            origin.code != destination.code -> "${label(origin)} (${origin.code})$Arrow${label(destination)} (${destination.code})"
            else -> label(origin)
        }
    }

    private fun label(place: Place): String =
        place.city.ifBlank { place.name }.ifBlank { place.displayCode }

    companion object {
        const val Arrow: String = " → "
    }
}
