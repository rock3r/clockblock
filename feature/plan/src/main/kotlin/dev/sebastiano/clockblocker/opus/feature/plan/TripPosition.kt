package dev.sebastiano.clockblocker.opus.feature.plan

import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GeoPoint
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeGeo
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Trip
import java.time.Duration
import java.time.Instant

/**
 * Where the traveller is on [Trip] at an instant, for the dial's globe.
 *
 * @property point on the ground at a stop, or along the flight's great circle in proportion to the time flown.
 * @property place the stop they're at, or the one they're flying to.
 * @property legIndex the flight to highlight: the one in the air, or the next one (the last after landing).
 */
internal data class TripPosition(val point: GeoPoint, val place: Place, val legIndex: Int, val inAir: Boolean)

internal fun Trip.positionAt(at: Instant): TripPosition {
    val first = legs.first()
    if (at.isBefore(first.departure)) return TripPosition(first.origin.point, first.origin, 0, inAir = false)
    legs.forEachIndexed { i, leg ->
        if (at.isBefore(leg.departure)) return TripPosition(leg.origin.point, leg.origin, i, inAir = false)
        if (at.isBefore(leg.arrival)) {
            val total = Duration.between(leg.departure, leg.arrival).toMillis().coerceAtLeast(1)
            val fraction = Duration.between(leg.departure, at).toMillis().toDouble() / total
            return TripPosition(GlobeGeo.interpolate(leg.origin.point, leg.destination.point, fraction), leg.destination, i, inAir = true)
        }
    }
    val last = legs.last()
    return TripPosition(last.destination.point, last.destination, legs.lastIndex, inAir = false)
}

internal val Place.point: GeoPoint get() = GeoPoint(latitude, longitude)

/** The time difference a planned shift crosses, folded into (−12, 12]: a long-way −14 h is +10 h east. */
internal fun timeDifferenceHours(plannedHours: Double): Double {
    val folded = ((plannedHours % 24.0) + 24.0) % 24.0
    return if (folded > 12.0) folded - 24.0 else folded
}
