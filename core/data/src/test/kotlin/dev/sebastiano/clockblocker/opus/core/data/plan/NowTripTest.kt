package dev.sebastiano.clockblocker.opus.core.data.plan

import dev.sebastiano.clockblocker.opus.core.circadian.daySpans
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.data.time.AppDispatchers
import dev.sebastiano.clockblocker.opus.core.data.trip.ReturnTripFactory
import dev.sebastiano.clockblocker.opus.core.data.trip.TripTitleSuggester
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import dev.sebastiano.clockblocker.opus.core.testing.FakeProfileRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import dev.sebastiano.clockblocker.opus.core.testing.MutableClock
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class NowTripTest {
    private val planner = FakeJetLagPlanner()
    private val outbound = DemoData.sfoToLhr() // Departs 2026-06-15.
    private val next = DemoData.lhrToSydneyViaSingapore(LocalDate.of(2026, 6, 28), id = "next")

    private fun planned(vararg trips: Trip) = trips.map { it to planner.plan(it, DemoData.profile, DemoData.Now) }

    @Test
    fun `a plan's window runs until its last night's sleep ends, past the last day's midnight`() {
        val plan = planner.plan(outbound, DemoData.profile, DemoData.Now)
        val lastSleepEnd = plan.allAdvice.filter { it.type == AdviceType.Sleep }.maxOf { it.end }
        val window = plan.window(outbound)
        window.end shouldBe lastSleepEnd
        window.end shouldBeGreaterThan plan.daySpans().last().end
    }

    @Test
    fun `without a plan a trip is under way from take-off until two days after landing`() {
        val window = NowTrip.window(outbound, plan = null)
        window shouldBe TripWindow(outbound.departure, outbound.arrival.plus(Duration.ofDays(2)))
        NowTrip.isUnderWay(outbound, null, window.end) shouldBe false // Half-open.
        NowTrip.isUnderWay(outbound, null, window.start) shouldBe true
    }

    @Test
    fun `around the last night the finishing trip stays now until its sleep ends, then the next trip takes over`() {
        val entries = planned(outbound, next)
        val plan = entries.first().second
        val midnight = plan.daySpans().last().end
        val end = plan.window(outbound).end

        NowTrip.pick(entries, midnight.minusSeconds(1))?.first?.id shouldBe outbound.id
        NowTrip.pick(entries, midnight.plusSeconds(1))?.first?.id shouldBe outbound.id
        NowTrip.pick(entries, end.minusSeconds(1))?.first?.id shouldBe outbound.id
        NowTrip.pick(entries, end)?.first?.id shouldBe next.id
    }

    @Test
    fun `when two trips are under way the later departure wins`() {
        val back = ReturnTripFactory(MutableClock(), TripTitleSuggester())
            .create(outbound, departureLocal = LocalDateTime.of(2026, 6, 20, 11, 0)) { "back" }
        val entries = planned(outbound, back)
        NowTrip.pick(entries, Instant.parse("2026-06-19T09:00:00Z"))?.first?.id shouldBe "back"
    }

    @Test
    fun `with nothing under way the soonest departure is now, and nothing once all are over`() {
        val entries = planned(next, outbound)
        NowTrip.pick(entries, Instant.parse("2026-06-01T00:00:00Z"))?.first?.id shouldBe outbound.id
        NowTrip.pick(entries, Instant.parse("2026-09-01T00:00:00Z")).shouldBeNull()
    }

    @Test
    fun `the plan repository picks the same trip as the rule at any time`() = runTest {
        val trips = listOf(
            outbound,
            ReturnTripFactory(MutableClock(), TripTitleSuggester()).create(outbound, LocalDateTime.of(2026, 6, 20, 11, 0)) { "back" },
            next,
            DemoData.sfoToLhr(LocalDate.of(2026, 8, 30), id = "far"),
        )
        val clock = MutableClock(DemoData.Now)
        val repo = DefaultPlanRepository(
            trips = FakeTripRepository(trips),
            profiles = FakeProfileRepository(DemoData.profile),
            planner = planner,
            clock = clock,
            ticker = clock,
            dispatchers = AppDispatchers.single(StandardTestDispatcher(testScheduler)),
            scope = backgroundScope,
        )
        val entries = planned(*trips.toTypedArray())
        val from = Instant.parse("2026-06-01T00:00:00Z").epochSecond
        val to = Instant.parse("2026-10-01T00:00:00Z").epochSecond
        checkAll(Arb.long(from, to)) { seconds ->
            val now = Instant.ofEpochSecond(seconds)
            repo.currentPlan(trips, DemoData.profile, now)?.tripId shouldBe NowTrip.pick(entries, now)?.first?.id
        }
    }
}
