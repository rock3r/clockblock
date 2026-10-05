package dev.sebastiano.clockblocker.opus.core.data.demo

import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Realistic, deterministic sample data for previews, screenshot tests and the "Try a demo trip" button.
 *
 * Arrival times are derived from departure + block time through the IANA zones, so they're always
 * consistent (DST included) whatever date the trips are generated for. Ids are stable, so screenshots and
 * plan ids don't change between runs.
 */
object DemoData {

    /** Departure date of the first demo trip by default. A June date: DST in both hemispheres differs. */
    val DefaultDepartureDate: LocalDate = LocalDate.of(2026, 6, 15)

    /** A moment a few days before the default trips, handy as a test clock's start. */
    val Now: Instant = Instant.parse("2026-06-12T16:00:00Z")

    val SFO = Place("SFO", "San Francisco International Airport", "San Francisco", "US", "America/Los_Angeles", 37.620, -122.375)
    val LHR = Place("LHR", "London Heathrow Airport", "London", "GB", "Europe/London", 51.471, -0.460)
    val LGW = Place("LGW", "London Gatwick Airport", "London", "GB", "Europe/London", 51.149, -0.186)
    val SIN = Place("SIN", "Singapore Changi Airport", "Singapore", "SG", "Asia/Singapore", 1.350, 103.994)
    val SYD = Place("SYD", "Sydney Kingsford Smith International Airport", "Sydney", "AU", "Australia/Sydney", -33.946, 151.177)
    val JFK = Place("JFK", "John F. Kennedy International Airport", "New York", "US", "America/New_York", 40.639, -73.779)
    val EWR = Place("EWR", "Newark Liberty International Airport", "New York", "US", "America/New_York", 40.689, -74.171)
    val LAX = Place("LAX", "Los Angeles International Airport", "Los Angeles", "US", "America/Los_Angeles", 33.943, -118.408)
    val HND = Place("HND", "Tokyo Haneda International Airport", "Tokyo", "JP", "Asia/Tokyo", 35.550, 139.787)
    val NRT = Place("NRT", "Narita International Airport", "Tokyo", "JP", "Asia/Tokyo", 35.769, 140.389)
    val LIS = Place("LIS", "Lisbon Humberto Delgado Airport", "Lisbon", "PT", "Europe/Lisbon", 38.781, -9.136)
    val CDG = Place("CDG", "Charles de Gaulle International Airport", "Paris", "FR", "Europe/Paris", 49.009, 2.554)
    val FRA = Place("FRA", "Frankfurt Main Airport", "Frankfurt am Main", "DE", "Europe/Berlin", 50.027, 8.558)
    val ZRH = Place("ZRH", "Zürich Airport", "Zurich", "CH", "Europe/Zurich", 47.458, 8.548)
    val DXB = Place("DXB", "Dubai International Airport", "Dubai", "AE", "Asia/Dubai", 25.250, 55.371)
    val HNL = Place("HNL", "Daniel K. Inouye International Airport", "Honolulu", "US", "Pacific/Honolulu", 21.318, -157.926)
    val AKL = Place("AKL", "Auckland International Airport", "Auckland", "NZ", "Pacific/Auckland", -37.012, 174.786)

    /** A small, ranked (busiest first) world of airports for fakes and previews. */
    val places: List<Place> = listOf(LHR, LAX, CDG, JFK, HND, SIN, FRA, DXB, SFO, EWR, LGW, SYD, NRT, ZRH, LIS, HNL, AKL)

    /** A San Francisco-based intermediate chronotype with default tools (melatonin off). */
    val profile = UserProfile(
        homeZoneId = "America/Los_Angeles",
        sleep = SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 0)),
        chronotype = Chronotype.Intermediate,
        useMelatonin = false,
        useCaffeine = true,
        canSleepOnPlanes = true,
        adjustBeforeDeparture = true,
        intensity = Intensity.Balanced,
    )

    val settings = AppSettings()

    const val SfoLhrId = "demo-sfo-lhr"
    const val LhrSydId = "demo-lhr-sin-syd"

    /** SFO 19:30 → LHR next day 13:50 (BA 286, 10 h 20 min), eastbound 8 h shift. */
    fun sfoToLhr(departureDate: LocalDate = DefaultDepartureDate, id: String = SfoLhrId): Trip = Trip(
        id = id,
        title = "San Francisco → London",
        legs = listOf(leg("$id-1", SFO, LHR, departureDate.atTime(19, 30), Duration.ofMinutes(10 * 60 + 20), "BA 286")),
        createdAt = createdAt(departureDate),
    )

    /**
     * LHR 21:35 → SIN 18:05 (+1) (BA 11, 13 h 30 min), 2 h 15 min connection, SIN → SYD (BA 15, 7 h 45 min):
     * a two-leg trip with a layover, crossing 9 time zones eastward.
     */
    fun lhrToSydneyViaSingapore(
        departureDate: LocalDate = DefaultDepartureDate.plusDays(10),
        id: String = LhrSydId,
    ): Trip {
        val first = leg("$id-1", LHR, SIN, departureDate.atTime(21, 35), Duration.ofMinutes(13 * 60 + 30), "BA 11")
        val secondDeparture = first.arrival.plus(Duration.ofMinutes(2 * 60 + 15)).atZone(SIN.zone).toLocalDateTime()
        val second = leg("$id-2", SIN, SYD, secondDeparture, Duration.ofMinutes(7 * 60 + 45), "BA 15")
        return Trip(
            id = id,
            title = "London → Sydney",
            legs = listOf(first, second),
            createdAt = createdAt(departureDate),
        )
    }

    /** Both demo trips, the second departing ten days after the first. */
    fun trips(firstDepartureDate: LocalDate = DefaultDepartureDate): List<Trip> =
        listOf(sfoToLhr(firstDepartureDate), lhrToSydneyViaSingapore(firstDepartureDate.plusDays(10)))

    /** For "Try a demo trip": SFO → LHR departing three days after [today], so its pre-trip days are ahead. */
    fun tryItTrip(today: LocalDate): Trip = sfoToLhr(today.plusDays(3), id = "demo-try-sfo-lhr")

    private fun leg(id: String, from: Place, to: Place, departureLocal: LocalDateTime, block: Duration, flight: String): FlightLeg {
        val arrival = departureLocal.atZone(from.zone).toInstant().plus(block)
        return FlightLeg(
            id = id,
            origin = from,
            destination = to,
            departureLocal = departureLocal,
            arrivalLocal = arrival.atZone(to.zone).toLocalDateTime(),
            flightNumber = flight,
        )
    }

    private fun createdAt(departureDate: LocalDate): Instant =
        departureDate.minusDays(30).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
}
