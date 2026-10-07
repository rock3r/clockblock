package dev.sebastiano.clockblocker.opus.core.model

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** An airport or city the user can fly from/to. Always carries an IANA zone id, never a fixed offset. */
@Serializable
data class Place(
    /** IATA code for airports (e.g. "LHR"), empty for plain cities. */
    val code: String,
    val name: String,
    val city: String,
    /** ISO 3166-1 alpha-2. */
    val countryCode: String,
    val zoneId: String,
    val latitude: Double,
    val longitude: Double,
) {
    val zone: ZoneId get() = ZoneId.of(zoneId)
    val displayCode: String get() = code.ifBlank { city.take(3).uppercase() }
}

/**
 * One flight. Times are wall-clock times *at the respective airport*, exactly as printed on a boarding pass;
 * instants are derived through the IANA zone rules so DST and the date line are handled correctly.
 */
@Serializable
data class FlightLeg(
    val id: String,
    val origin: Place,
    val destination: Place,
    @Serializable(with = LocalDateTimeIsoSerializer::class) val departureLocal: LocalDateTime,
    @Serializable(with = LocalDateTimeIsoSerializer::class) val arrivalLocal: LocalDateTime,
    val flightNumber: String? = null,
) {
    val departure: Instant get() = departureLocal.atZone(origin.zone).toInstant()
    val arrival: Instant get() = arrivalLocal.atZone(destination.zone).toInstant()
    val duration: Duration get() = Duration.between(departure, arrival)
}

/** Whether the plan should move the body clock to the destination or keep it on home time (short trips). */
@Serializable
enum class AdaptationStrategy { Adapt, StayOnHomeTime }

@Serializable
data class Trip(
    val id: String,
    val title: String,
    val legs: List<FlightLeg>,
    @Serializable(with = InstantIsoSerializer::class) val createdAt: Instant,
    /** Null = let the planner decide (short stays default to staying on home time). */
    val strategyOverride: AdaptationStrategy? = null,
    /** Optional: when the user flies back. Used to detect short trips. */
    @Serializable(with = InstantIsoSerializer::class) val returnDeparture: Instant? = null,
) {
    init {
        require(legs.isNotEmpty()) { "A trip needs at least one flight" }
    }

    val origin: Place get() = legs.first().origin
    val destination: Place get() = legs.last().destination
    val departure: Instant get() = legs.first().departure
    val arrival: Instant get() = legs.last().arrival

    /**
     * The trip's place in [zone] (the last one, if it stops there twice), e.g. to find where the plan's local time
     * is; null if no stop is in that zone.
     */
    fun placeIn(zone: ZoneId): Place? =
        legs.flatMap { listOf(it.origin, it.destination) }.lastOrNull { it.zoneId == zone.id }

    /** Layovers between consecutive legs. */
    val layovers: List<Layover>
        get() = legs.zipWithNext { a, b -> Layover(a.destination, a.arrival, b.departure) }
}

data class Layover(val place: Place, val start: Instant, val end: Instant) {
    val duration: Duration get() = Duration.between(start, end)
}
