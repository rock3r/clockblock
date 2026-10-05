package dev.sebastiano.clockblocker.opus.core.data.trip

import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Trip
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class TripTitleSuggesterTest {
    private val titles = TripTitleSuggester()

    private fun leg(a: Place, b: Place) = FlightLeg("x", a, b, LocalDateTime.of(2026, 6, 1, 8, 0), LocalDateTime.of(2026, 6, 1, 20, 0))

    @Test
    fun `one way uses origin and final destination cities`() {
        titles.suggest(listOf(leg(DemoData.LIS, DemoData.HND))) shouldBe "Lisbon → Tokyo"
        titles.suggest(DemoData.lhrToSydneyViaSingapore()) shouldBe "London → Sydney"
    }

    @Test
    fun `round trip in one trip goes via the farthest stop`() {
        val legs = listOf(leg(DemoData.LIS, DemoData.FRA), leg(DemoData.FRA, DemoData.HND), leg(DemoData.HND, DemoData.LIS))
        titles.suggest(legs) shouldBe "Lisbon → Tokyo → Lisbon"
    }

    @Test
    fun `same city at both ends is disambiguated with codes`() {
        titles.suggest(listOf(leg(DemoData.LHR, DemoData.LGW))) shouldBe "London (LHR) → London (LGW)"
    }

    @Test
    fun `falls back to the name or code when the city is blank`() {
        val noCity = DemoData.HNL.copy(city = "")
        titles.suggest(listOf(leg(DemoData.SFO, noCity))) shouldBe "San Francisco → Daniel K. Inouye International Airport"
        titles.suggest(listOf(leg(DemoData.SFO, noCity.copy(name = "")))) shouldBe "San Francisco → HNL"
    }

    @Test
    fun `no legs gives an empty title`() {
        titles.suggest(emptyList()) shouldBe ""
    }
}

class ReturnTripFactoryTest {
    private val now = Instant.parse("2026-06-01T09:00:00Z")
    private val factory = ReturnTripFactory(Clock.fixed(now, ZoneOffset.UTC), TripTitleSuggester())
    private val validator = TripValidator()

    private fun ids(): () -> String {
        var n = 0
        return { "id${n++}" }
    }

    @Test
    fun `reverses a single leg a week later at the same time of day`() {
        val out = DemoData.sfoToLhr() // SFO 19:30 Jun 15 -> LHR 13:50 Jun 16.
        val back = factory.create(out, newId = ids())

        back.title shouldBe "London → San Francisco"
        back.createdAt shouldBe now
        back.returnDeparture.shouldBeNull()
        back.strategyOverride.shouldBeNull()
        val leg = back.legs.single()
        leg.origin shouldBe DemoData.LHR
        leg.destination shouldBe DemoData.SFO
        leg.departureLocal shouldBe LocalDateTime.of(2026, 6, 23, 19, 30)
        leg.duration shouldBe out.legs.single().duration
        leg.arrivalLocal shouldBe LocalDateTime.of(2026, 6, 23, 21, 50) // 10 h 20 min, 8 h behind.
        leg.flightNumber.shouldBeNull()
        listOf(back.id, leg.id).toSet().size shouldBe 2
        validator.validate(back).issues.shouldBeEmpty()
    }

    @Test
    fun `reverses multi-leg trips keeping block times and the connection`() {
        val out = DemoData.lhrToSydneyViaSingapore()
        val back = factory.create(out, departureLocal = LocalDateTime.of(2026, 7, 10, 16, 0), newId = ids())

        back.legs.map { it.origin.code to it.destination.code } shouldContainExactly listOf("SYD" to "SIN", "SIN" to "LHR")
        back.legs[0].departureLocal shouldBe LocalDateTime.of(2026, 7, 10, 16, 0)
        back.legs[0].duration shouldBe out.legs[1].duration
        back.legs[1].duration shouldBe out.legs[0].duration
        back.layovers.single().duration shouldBe out.layovers.single().duration
        back.title shouldBe "Sydney → London"
        validator.validate(back).issues.shouldBeEmpty()
    }

    @Test
    fun `uses the outbound return departure when known`() {
        val returnAt = Instant.parse("2026-06-20T10:00:00Z") // 11:00 in London (BST).
        val back = factory.create(DemoData.sfoToLhr().copy(returnDeparture = returnAt), newId = ids())
        back.departure shouldBe returnAt
        back.legs.single().departureLocal shouldBe LocalDateTime.of(2026, 6, 20, 11, 0)
    }

    @Test
    fun `too-short outbound connections fall back to a sensible layover`() {
        val out = DemoData.lhrToSydneyViaSingapore()
        val tight = out.copy(
            legs = listOf(
                out.legs[0],
                out.legs[1].let { l ->
                    val dep = out.legs[0].arrivalLocal.plusMinutes(10)
                    l.copy(departureLocal = dep, arrivalLocal = dep.atZone(l.origin.zone).toInstant().plus(l.duration).atZone(l.destination.zone).toLocalDateTime())
                },
            ),
        )
        val back = factory.create(tight, newId = ids())
        back.layovers.single().duration shouldBe ReturnTripFactory.DefaultLayover
    }

    @Test
    fun `invalid outbound legs get an estimated block time`() {
        val bad = Trip(
            "bad", "x",
            listOf(FlightLeg("l", DemoData.LHR, DemoData.JFK, LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 4, 0))),
            Instant.EPOCH,
        )
        val back = factory.create(bad, newId = ids())
        (back.legs.single().duration > Duration.ofHours(6)) shouldBe true
        validator.validate(back).isValid shouldBe true
    }

    @Test
    fun `linkOutbound records the return departure`() {
        val out = DemoData.sfoToLhr()
        val back = factory.create(out, newId = ids())
        factory.linkOutbound(out, back).returnDeparture shouldBe back.departure
    }
}
