package dev.sebastiano.clockblocker.opus.core.data.trip

import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.zacsweers.metro.Inject
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** How much an issue should hold the user up. */
enum class IssueSeverity {
    /** The trip can't be planned as entered; saving should be blocked. */
    Error,

    /** Probably a typo; ask the user to confirm. */
    Warning,

    /** Helpful context (e.g. "you land the day before you leave"); never blocks. */
    Info,
}

/**
 * A typed finding about a trip, for the editor to render next to the offending leg. Every issue carries the
 * values the UI needs for a specific message, and [suggestedArrivalLocal] where a one-tap fix exists.
 */
sealed interface TripIssue {
    val severity: IssueSeverity

    /** 0-based leg the issue belongs to, or null for trip-wide issues. */
    val legIndex: Int?

    data object NoFlights : TripIssue {
        override val severity get() = IssueSeverity.Error
        override val legIndex: Int? get() = null
    }

    /** Origin and destination are the same airport. */
    data class SameOriginAndDestination(override val legIndex: Int, val place: Place) : TripIssue {
        override val severity get() = IssueSeverity.Error
    }

    /**
     * Arrival is not after departure once both local times are converted through their zones.
     * [suggestedArrivalLocal] is set when moving the arrival by a day makes the flight plausible
     * (the classic overnight / date-line slip).
     */
    data class ArrivalNotAfterDeparture(
        override val legIndex: Int,
        val duration: Duration,
        val suggestedArrivalLocal: LocalDateTime?,
    ) : TripIssue {
        override val severity get() = IssueSeverity.Error
    }

    /** Faster than any airliner could cover the great-circle distance. */
    data class ImplausiblyShort(
        override val legIndex: Int,
        val duration: Duration,
        val minimum: Duration,
        val distanceKm: Long,
        val suggestedArrivalLocal: LocalDateTime?,
    ) : TripIssue {
        override val severity get() = IssueSeverity.Warning
    }

    /** Much slower than a direct flight; often a wrong date, AM/PM, or a stopover not entered as a leg. */
    data class ImplausiblyLong(
        override val legIndex: Int,
        val duration: Duration,
        val maximum: Duration,
        val distanceKm: Long,
        val suggestedArrivalLocal: LocalDateTime?,
    ) : TripIssue {
        override val severity get() = IssueSeverity.Warning
    }

    /** This leg departs before the previous one has landed. [legIndex] is the later leg. */
    data class LegsOverlap(override val legIndex: Int, val overlap: Duration) : TripIssue {
        override val severity get() = IssueSeverity.Error
    }

    /** This leg doesn't depart from where the previous one landed (e.g. LGW → LHR transfer). */
    data class LegsNotConnected(override val legIndex: Int, val arrivedAt: Place, val departsFrom: Place) : TripIssue {
        override val severity get() = IssueSeverity.Warning
    }

    /** Connection shorter than [minimum]. [legIndex] is the leg after the layover. */
    data class LayoverTooShort(override val legIndex: Int, val layover: Duration, val minimum: Duration) : TripIssue {
        override val severity get() = IssueSeverity.Warning
    }

    /** Stopover longer than [maximum]: probably two separate trips. [legIndex] is the leg after the layover. */
    data class LayoverTooLong(override val legIndex: Int, val layover: Duration, val maximum: Duration) : TripIssue {
        override val severity get() = IssueSeverity.Warning
    }

    /**
     * The leg crosses the International Date Line, so the calendar jumps: [calendarDayShift] is the local
     * arrival date minus the local departure date, e.g. SYD → LAX usually lands the day *before* it departs
     * (`-1`), while an afternoon HNL → NRT lands the next day (`1`) after only 9 h. Lets the editor say
     * "Lands Mon 15 Jun (the day before departure)" and catch date typos.
     */
    data class CrossesDateLine(override val legIndex: Int, val calendarDayShift: Long) : TripIssue {
        override val severity get() = IssueSeverity.Info
    }
}

/** Result of [TripValidator.validate]. */
data class TripValidation(val issues: List<TripIssue>) {
    val errors: List<TripIssue> get() = issues.filter { it.severity == IssueSeverity.Error }
    val warnings: List<TripIssue> get() = issues.filter { it.severity == IssueSeverity.Warning }
    val infos: List<TripIssue> get() = issues.filter { it.severity == IssueSeverity.Info }

    /** True when the trip can be saved and planned (warnings still deserve a confirmation). */
    val isValid: Boolean get() = errors.isEmpty()

    fun forLeg(index: Int): List<TripIssue> = issues.filter { it.legIndex == index }
}

/**
 * Sanity checks for manually entered trips. All comparisons use real instants (local time + IANA zone), so
 * DST changes and the date line are handled correctly; plausibility uses great-circle distances.
 *
 * Deliberately *not* an error: departures in the past (users may log a trip they're already on) and long
 * single legs (people enter a one-stop itinerary as one leg); those are at most warnings.
 */
@Inject
class TripValidator {

    fun validate(trip: Trip): TripValidation = validate(trip.legs)

    fun validate(legs: List<FlightLeg>): TripValidation {
        if (legs.isEmpty()) return TripValidation(listOf(TripIssue.NoFlights))
        val issues = mutableListOf<TripIssue>()
        legs.forEachIndexed { i, leg -> issues += validateLeg(i, leg) }
        legs.zipWithNext().forEachIndexed { i, (prev, next) -> issues += validateConnection(i + 1, prev, next) }
        return TripValidation(issues.sortedWith(compareBy({ it.legIndex ?: -1 }, { it.severity.ordinal })))
    }

    private fun validateLeg(i: Int, leg: FlightLeg): List<TripIssue> {
        if (leg.origin.code.isNotBlank() && leg.origin.code == leg.destination.code) {
            return listOf(TripIssue.SameOriginAndDestination(i, leg.origin))
        }
        val distance = distanceKm(leg.origin, leg.destination)
        val duration = leg.duration
        val min = minimumDuration(distance)
        val max = maximumDuration(distance)
        val issues = mutableListOf<TripIssue>()
        when {
            duration.isNegative || duration.isZero ->
                issues += TripIssue.ArrivalNotAfterDeparture(i, duration, suggestArrival(leg, distance))
            duration < min ->
                issues += TripIssue.ImplausiblyShort(i, duration, min, distance.roundToLong(), suggestArrival(leg, distance))
            duration > max ->
                issues += TripIssue.ImplausiblyLong(i, duration, max, distance.roundToLong(), suggestArrival(leg, distance))
        }
        if (crossesDateLine(leg.origin, leg.destination) && duration > Duration.ZERO) {
            val shift = Duration.between(
                leg.departureLocal.toLocalDate().atStartOfDay(),
                leg.arrivalLocal.toLocalDate().atStartOfDay(),
            ).toDays()
            issues += TripIssue.CrossesDateLine(i, shift)
        }
        return issues
    }

    private fun validateConnection(nextIndex: Int, prev: FlightLeg, next: FlightLeg): List<TripIssue> {
        val issues = mutableListOf<TripIssue>()
        if (!samePlace(prev.destination, next.origin)) {
            issues += TripIssue.LegsNotConnected(nextIndex, prev.destination, next.origin)
        }
        val layover = Duration.between(prev.arrival, next.departure)
        when {
            layover.isNegative -> issues += TripIssue.LegsOverlap(nextIndex, layover.negated())
            layover < MinLayover -> issues += TripIssue.LayoverTooShort(nextIndex, layover, MinLayover)
            layover > MaxLayover -> issues += TripIssue.LayoverTooLong(nextIndex, layover, MaxLayover)
        }
        return issues
    }

    /** Tries arrival ±1 day; returns the first that makes the flight plausible. */
    private fun suggestArrival(leg: FlightLeg, distanceKm: Double): LocalDateTime? =
        listOf(1L, -1L).map { leg.arrivalLocal.plusDays(it) }.firstOrNull { candidate ->
            val d = Duration.between(leg.departure, candidate.atZone(leg.destination.zone).toInstant())
            d >= minimumDuration(distanceKm) && d <= maximumDuration(distanceKm)
        }

    private fun samePlace(a: Place, b: Place): Boolean =
        if (a.code.isNotBlank() && b.code.isNotBlank()) a.code == b.code else a == b

    companion object {
        /** Faster than any airliner's ground speed even with a jet stream tailwind. */
        const val MaxGroundSpeedKmh: Double = 1_100.0

        /** Slow turboprop, plus generous taxi/holding/diversion allowance. */
        const val MinGroundSpeedKmh: Double = 450.0
        val Overhead: Duration = Duration.ofHours(4)
        val MinFlight: Duration = Duration.ofMinutes(15)
        val MinLayover: Duration = Duration.ofMinutes(30)
        val MaxLayover: Duration = Duration.ofHours(24)

        fun minimumDuration(distanceKm: Double): Duration =
            maxOf(MinFlight, Duration.ofSeconds((distanceKm / MaxGroundSpeedKmh * 3600).roundToLong()))

        fun maximumDuration(distanceKm: Double): Duration =
            Duration.ofSeconds((distanceKm / MinGroundSpeedKmh * 3600).roundToLong()).plus(Overhead)

        /** Great-circle distance in km. */
        fun distanceKm(a: Place, b: Place): Double {
            val p1 = Math.toRadians(a.latitude)
            val p2 = Math.toRadians(b.latitude)
            val dp = p2 - p1
            val dl = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dp / 2).let { it * it } + cos(p1) * cos(p2) * sin(dl / 2).let { it * it }
            return 2 * 6_371.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }

        /** Whether the shorter way between the two longitudes crosses ±180°. */
        fun crossesDateLine(a: Place, b: Place): Boolean {
            val raw = b.longitude - a.longitude
            return raw > 180 || raw < -180
        }
    }
}
