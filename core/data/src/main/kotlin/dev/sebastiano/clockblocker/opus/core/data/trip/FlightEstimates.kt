package dev.sebastiano.clockblocker.opus.core.data.trip

import dev.sebastiano.clockblocker.opus.core.model.Place
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToLong

/**
 * Rough flight times from great-circle distance, for prefilling times the user then corrects to their ticket
 * (the trip editor's arrival, a return trip's legs). Never used for planning on its own: the plan always uses the
 * times the user saved.
 */
object FlightEstimates {
    /** Typical airliner block speed over the ground, averaged over a whole flight. */
    const val EstimatedSpeedKmh: Double = 800.0

    /** Taxi, climb and approach on top of the cruise. */
    val TaxiAllowance: Duration = Duration.ofMinutes(30)

    private const val RoundToMinutes = 5L

    /** Estimated gate-to-gate time between [origin] and [destination], rounded to 5 minutes. */
    fun blockTime(origin: Place, destination: Place): Duration {
        val km = TripValidator.distanceKm(origin, destination)
        val minutes = km / EstimatedSpeedKmh * 60 + TaxiAllowance.toMinutes()
        val rounded = (minutes / RoundToMinutes).roundToLong() * RoundToMinutes
        return Duration.ofMinutes(rounded.coerceAtLeast(TaxiAllowance.toMinutes()))
    }

    /**
     * Estimated arrival, in [destination]'s wall-clock time, of a flight leaving [origin] at [departureLocal]
     * (wall-clock time there). Converted through both IANA zones, so DST and the date line come out right.
     */
    fun arrivalLocal(origin: Place, destination: Place, departureLocal: LocalDateTime): LocalDateTime =
        departureLocal.atZone(origin.zone).toInstant()
            .plus(blockTime(origin, destination))
            .atZone(destination.zone)
            .toLocalDateTime()
}
