package dev.sebastiano.clockblocker.opus.core.data.trip

import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.testing.MutableClock
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class ReturnTripLinksTest {
    private val factory = ReturnTripFactory(MutableClock(), TripTitleSuggester())
    private val outbound = DemoData.sfoToLhr() // Lands at LHR 2026-06-16.
    private val back = factory.create(outbound, LocalDateTime.of(2026, 6, 22, 11, 0)) { "back" }

    @Test
    fun `a linked return trip is found by its id, wherever it has moved`() {
        val linked = factory.linkOutbound(outbound, back)
        linked.returnTripId shouldBe "back"
        linked.returnDeparture shouldBe back.departure
        val moved = factory.moveTo(back, LocalDateTime.of(2026, 6, 25, 9, 0))
        ReturnTripLinks.linked(linked, listOf(linked, moved)) shouldBe moved
        ReturnTripLinks.outboundOf(moved, listOf(linked, moved)) shouldBe linked
    }

    @Test
    fun `trips saved before the id existed link by their return departure`() {
        val legacy = outbound.copy(returnDeparture = back.departure)
        ReturnTripLinks.linked(legacy, listOf(legacy, back)) shouldBe back
        ReturnTripLinks.linked(legacy.copy(returnDeparture = back.departure.plusSeconds(60)), listOf(legacy, back)).shouldBeNull()
    }

    @Test
    fun `a deleted return trip is no link`() {
        ReturnTripLinks.linked(factory.linkOutbound(outbound, back), listOf(outbound)).shouldBeNull()
    }

    @Test
    fun `turning the switch back on reuses an orphaned reversed trip, but not one another trip claims`() {
        // Switched off and saved: no return departure any more, and (as before this fix) no id.
        val oneWay = outbound.copy(returnDeparture = null, returnTripId = null)
        ReturnTripLinks.reusable(oneWay, listOf(oneWay, back)) shouldBe back

        val other = DemoData.sfoToLhr(LocalDate.of(2026, 6, 10), id = "other").let { factory.linkOutbound(it, back) }
        ReturnTripLinks.reusable(oneWay, listOf(oneWay, other, back)).shouldBeNull()
    }

    @Test
    fun `only a trip that reverses the route, after landing, can be reused`() {
        val oneWay = outbound.copy(returnDeparture = null)
        val sameWay = DemoData.sfoToLhr(LocalDate.of(2026, 6, 20), id = "again")
        val beforeLanding = factory.create(outbound, LocalDateTime.of(2026, 6, 14, 11, 0)) { "early" }
        val muchLater = factory.create(outbound, LocalDateTime.of(2027, 1, 10, 11, 0)) { "later" }
        ReturnTripLinks.reusable(oneWay, listOf(oneWay, sameWay, beforeLanding, muchLater)).shouldBeNull()
    }

    @Test
    fun `moving a return trip keeps its flight lengths, connections and flight numbers`() {
        val sydney = DemoData.lhrToSydneyViaSingapore()
        var n = 0
        val home = factory.create(sydney) { "home-${n++}" }.let { t -> t.copy(legs = t.legs.map { it.copy(flightNumber = "QF 1") }) }
        val moved = factory.moveTo(home, home.legs.first().departureLocal.plusDays(3).withHour(8))
        moved.legs.first().departureLocal shouldBe home.legs.first().departureLocal.plusDays(3).withHour(8)
        moved.legs.map { it.duration } shouldBe home.legs.map { it.duration }
        Duration.between(moved.legs[0].arrival, moved.legs[1].departure) shouldBe Duration.between(home.legs[0].arrival, home.legs[1].departure)
        moved.legs.map { it.flightNumber } shouldBe listOf("QF 1", "QF 1")
        moved.id shouldBe home.id
    }
}
