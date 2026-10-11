package dev.sebastiano.clockblocker.opus.feature.plan

import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration

/** Where the traveller is along a trip, for the dial's globe. */
class TripPositionTest {

    private val trip = DemoData.lhrToSydneyViaSingapore() // LHR → SIN → SYD.

    @Test
    fun `before take-off you're at the first airport, with the first flight next`() {
        val p = trip.positionAt(trip.departure.minus(Duration.ofHours(5)))
        p.place shouldBe DemoData.LHR
        p.legIndex shouldBe 0
        p.inAir.shouldBeFalse()
    }

    @Test
    fun `halfway through a flight you're halfway along its great circle`() {
        val leg = trip.legs[0]
        val half = leg.departure.plus(Duration.between(leg.departure, leg.arrival).dividedBy(2))
        val p = trip.positionAt(half)
        p.inAir.shouldBeTrue()
        p.place shouldBe DemoData.SIN
        p.legIndex shouldBe 0
        val mid = dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeGeo.interpolate(DemoData.LHR.point, DemoData.SIN.point, 0.5)
        p.point.latitude shouldBe (mid.latitude plusOrMinus 0.5)
        p.point.longitude shouldBe (mid.longitude plusOrMinus 0.5)
    }

    @Test
    fun `on a layover you're at the connection, with the next flight highlighted`() {
        val p = trip.positionAt(trip.legs[0].arrival.plus(Duration.ofMinutes(30)))
        p.place shouldBe DemoData.SIN
        p.legIndex shouldBe 1
        p.inAir.shouldBeFalse()
    }

    @Test
    fun `after landing you're at the destination`() {
        val p = trip.positionAt(trip.arrival.plus(Duration.ofDays(2)))
        p.place shouldBe DemoData.SYD
        p.legIndex shouldBe 1
    }

    @Test
    fun `a long-way shift folds back to the time difference`() {
        timeDifferenceHours(-14.0) shouldBe 10.0
        timeDifferenceHours(9.0) shouldBe 9.0
        timeDifferenceHours(13.0) shouldBe -11.0
    }
}
