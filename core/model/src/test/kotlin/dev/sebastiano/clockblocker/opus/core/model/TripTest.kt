package dev.sebastiano.clockblocker.opus.core.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

class TripTest {
    private val sfo = Place("SFO", "San Francisco International", "San Francisco", "US", "America/Los_Angeles", 37.62, -122.38)
    private val lhr = Place("LHR", "Heathrow", "London", "GB", "Europe/London", 51.47, -0.45)

    private val leg = FlightLeg(
        id = "1",
        origin = sfo,
        destination = lhr,
        departureLocal = LocalDateTime.of(2026, 6, 15, 16, 30),
        arrivalLocal = LocalDateTime.of(2026, 6, 16, 10, 45),
    )

    @Test
    fun `leg instants honour each airport's zone rules`() {
        leg.departure shouldBe Instant.parse("2026-06-15T23:30:00Z")
        leg.arrival shouldBe Instant.parse("2026-06-16T09:45:00Z")
        leg.duration shouldBe Duration.ofMinutes(10 * 60 + 15)
    }

    @Test
    fun `sleep window wrapping midnight has the right duration and midpoint`() {
        val w = SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 0))
        w.duration shouldBe Duration.ofHours(8)
        w.midSleep shouldBe LocalTime.of(3, 0)
    }

    @Test
    fun `the place in a zone is the trip's last stop there`() {
        val nrt = Place("NRT", "Narita", "Tokyo", "JP", "Asia/Tokyo", 35.76, 140.39)
        val second = FlightLeg("2", lhr, nrt, LocalDateTime.of(2026, 6, 16, 13, 0), LocalDateTime.of(2026, 6, 17, 9, 0))
        val trip = Trip("t", "Tokyo", listOf(leg, second), Instant.parse("2026-06-01T00:00:00Z"))
        trip.placeIn(java.time.ZoneId.of("America/Los_Angeles")) shouldBe sfo
        trip.placeIn(java.time.ZoneId.of("Europe/London")) shouldBe lhr
        trip.placeIn(java.time.ZoneId.of("Asia/Tokyo")) shouldBe nrt
        trip.placeIn(java.time.ZoneId.of("Europe/Paris")) shouldBe null
    }

    @Test
    fun `trip round-trips through JSON`() {
        val trip = Trip("t", "London", listOf(leg), Instant.parse("2026-06-01T00:00:00Z"))
        val json = Json.encodeToString(Trip.serializer(), trip)
        Json.decodeFromString(Trip.serializer(), json) shouldBe trip
    }
}
