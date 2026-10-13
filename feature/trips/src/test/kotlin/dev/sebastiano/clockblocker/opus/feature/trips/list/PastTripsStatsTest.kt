package dev.sebastiano.clockblocker.opus.feature.trips.list

import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GeoPoint
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeGeo
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import dev.sebastiano.clockblocker.opus.feature.trips.TripsSamples
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Place
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

class PastTripsStatsTest {

    private val planner = FakeJetLagPlanner()
    private val afterAll = Instant.parse("2026-08-01T12:00:00Z")

    private fun summary(trip: dev.sebastiano.clockblocker.opus.core.model.Trip) = TripSummaries.summarize(
        trip = trip,
        plan = planner.plan(trip, DemoData.profile, afterAll),
        now = afterAll,
        today = afterAll.atZone(ZoneOffset.UTC).toLocalDate(),
        fallbackTitle = trip.title,
    )

    @Test
    fun `aggregates trips, flights, unique airports, distance, shift hours and days saved`() {
        val past = listOf(
            summary(DemoData.sfoToLhr()), // SFO → LHR (+8 h)
            summary(DemoData.lhrToSydneyViaSingapore()), // LHR → SIN → SYD (+9 h)
            summary(TripsSamples.lisbonTokyo), // LIS → HND (+8 h)
        )
        val stats = PastTripsStats.of(past)

        stats.tripsCount shouldBe 3
        stats.flightsCount shouldBe 4
        // SFO, LHR, SIN, SYD, LIS, HND (LHR is shared between two trips and counted once).
        stats.airportsCount shouldBe 6
        // Hours of time difference, flight by flight: SFO → LHR 8, LHR → SIN 7, SIN → SYD 2, LIS → HND 8.
        stats.hoursOfTimeDifference shouldBe 25f
        stats.daysSaved shouldBeGreaterThan 0

        val expectedKm = past
            .flatMap { it.trip.legs }
            .sumOf {
                GlobeGeo.distanceKm(
                    GeoPoint(it.origin.latitude, it.origin.longitude),
                    GeoPoint(it.destination.latitude, it.destination.longitude),
                )
            }
            .roundToInt()
        stats.totalDistanceKm shouldBe expectedKm

        // Legs are ordered chronologically by trip departure so the globe traces the traveller's history.
        stats.legs.map { it.from.label to it.to.label } shouldContainExactly listOf(
            "LIS" to "HND",
            "SFO" to "LHR",
            "LHR" to "SIN",
            "SIN" to "SYD",
        )
        stats.legTripIds shouldContainExactly listOf(
            TripsSamples.lisbonTokyo.id,
            DemoData.SfoLhrId,
            DemoData.LhrSydId,
            DemoData.LhrSydId,
        )
    }

    @Test
    fun `a round trip counts the time difference of both flights, not the net change of zero`() {
        val out = TripsSamples.lisbonTokyo.legs.single()
        val back = FlightLeg(
            id = "back",
            origin = out.destination,
            destination = out.origin,
            departureLocal = LocalDateTime.of(2026, 4, 12, 11, 0),
            arrivalLocal = LocalDateTime.of(2026, 4, 12, 18, 0),
        )
        val roundTrip = TripsSamples.lisbonTokyo.copy(id = "round", legs = listOf(out, back))
        // Lisbon (UTC+1 in April) ↔ Tokyo (UTC+9): 8 hours out and 8 back.
        hoursOfTimeDifference(roundTrip) shouldBe 16f
        PastTripsStats.of(listOf(summary(roundTrip))).hoursOfTimeDifference shouldBe 16f
    }

    @Test
    fun `half-hour zones count their half hours`() {
        val out = TripsSamples.lisbonTokyo.legs.single()
        val delhi = out.destination.copy(code = "DEL", zoneId = "Asia/Kolkata")
        val trip = TripsSamples.lisbonTokyo.copy(legs = listOf(out.copy(destination = delhi)))
        // UTC+1 → UTC+5:30.
        hoursOfTimeDifference(trip) shouldBe 4.5f
    }

    @Test
    fun `a clock change during a flight within one zone isn't a time difference`() {
        val out = TripsSamples.lisbonTokyo.legs.single()
        val porto = out.origin.copy(code = "OPO", city = "Porto", latitude = 41.24, longitude = -8.68)
        // Lisbon → Porto overnight across the end of summer time (25 Oct 2026, 02:00 → 01:00): same zone throughout.
        val leg = out.copy(
            destination = porto,
            departureLocal = LocalDateTime.of(2026, 10, 25, 0, 30),
            arrivalLocal = LocalDateTime.of(2026, 10, 25, 3, 0),
        )
        hoursOfTimeDifference(TripsSamples.lisbonTokyo.copy(legs = listOf(leg))) shouldBe 0f
    }

    @Test
    fun `across the date line the clock-face change counts, not the 24 hours on paper`() {
        val out = TripsSamples.lisbonTokyo.legs.single()
        val kiritimati = out.origin.copy(code = "CXI", zoneId = "Pacific/Kiritimati", latitude = 1.99, longitude = -157.35)
        val honolulu = out.destination.copy(code = "HNL", zoneId = "Pacific/Honolulu", latitude = 21.32, longitude = -157.93)
        val auckland = out.origin.copy(code = "AKL", zoneId = "Pacific/Auckland", latitude = -37.01, longitude = 174.79)
        fun trip(from: Place, to: Place) = TripsSamples.lisbonTokyo.copy(legs = listOf(out.copy(origin = from, destination = to)))
        // +14 → −10: the same time of day.
        hoursOfTimeDifference(trip(kiritimati, honolulu)) shouldBe 0f
        // Auckland (+13 in April's early days, +12 after) → Honolulu (−10): 1 or 2 hours, never 22 or 23.
        (hoursOfTimeDifference(trip(auckland, honolulu)) <= 2f) shouldBe true
    }

    @Test
    fun `empty past list produces zero stats`() {
        val stats = PastTripsStats.of(emptyList())
        stats.tripsCount shouldBe 0
        stats.flightsCount shouldBe 0
        stats.airportsCount shouldBe 0
        stats.totalDistanceKm shouldBe 0
        stats.hoursOfTimeDifference shouldBe 0f
        stats.daysSaved shouldBe 0
        stats.legs shouldContainExactly emptyList()
    }
}
