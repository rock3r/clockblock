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
import java.time.Instant
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
        stats.totalShiftHours shouldBe 25f
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
    fun `empty past list produces zero stats`() {
        val stats = PastTripsStats.of(emptyList())
        stats.tripsCount shouldBe 0
        stats.flightsCount shouldBe 0
        stats.airportsCount shouldBe 0
        stats.totalDistanceKm shouldBe 0
        stats.totalShiftHours shouldBe 0f
        stats.daysSaved shouldBe 0
        stats.legs shouldContainExactly emptyList()
    }
}
