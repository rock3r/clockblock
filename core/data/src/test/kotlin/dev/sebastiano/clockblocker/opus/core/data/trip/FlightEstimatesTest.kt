package dev.sebastiano.clockblocker.opus.core.data.trip

import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Place
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class FlightEstimatesTest {

    @Test
    fun `block time is distance at airliner speed plus taxi time, in whole 5 minutes`() {
        val estimate = FlightEstimates.blockTime(DemoData.LIS, DemoData.HND)
        // ~11,150 km at 800 km/h ≈ 13 h 56 m, + 30 m taxi ≈ 14 h 25 m.
        estimate shouldBeGreaterThan Duration.ofHours(14)
        estimate shouldBeLessThan Duration.ofHours(15)
        (estimate.toMinutes() % 5) shouldBe 0L
    }

    @Test
    fun `every estimate is plausible for the validator`() {
        val places = DemoData.places
        for (a in places) for (b in places) {
            if (a == b || TripValidator.distanceKm(a, b) < 1.0) continue
            val km = TripValidator.distanceKm(a, b)
            val estimate = FlightEstimates.blockTime(a, b)
            (estimate >= TripValidator.minimumDuration(km)) shouldBe true
            (estimate <= TripValidator.maximumDuration(km)) shouldBe true
        }
    }

    @Test
    fun `arrival is the departure plus the block time, in the destination's wall clock`() {
        val departure = LocalDateTime.of(2026, 7, 1, 10, 0)
        val arrival = FlightEstimates.arrivalLocal(DemoData.LIS, DemoData.HND, departure)
        val leg = FlightLeg("x", DemoData.LIS, DemoData.HND, departure, arrival)
        leg.duration shouldBe FlightEstimates.blockTime(DemoData.LIS, DemoData.HND)
        // 10:00 Lisbon (UTC+1) + ~14½ h = ~00:25 UTC = ~09:25 the next day in Tokyo.
        arrival.toLocalDate() shouldBe departure.toLocalDate().plusDays(1)
    }

    @Test
    fun `the same airport twice still gets the minimum taxi time`() {
        val place: Place = DemoData.LHR
        FlightEstimates.blockTime(place, place) shouldBe FlightEstimates.TaxiAllowance
    }
}
