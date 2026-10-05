package dev.sebastiano.clockblocker.opus.core.data.plan

import app.cash.turbine.test
import dev.sebastiano.clockblocker.opus.core.circadian.JetLagPlanner
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.data.time.AppDispatchers
import dev.sebastiano.clockblocker.opus.core.data.trip.ReturnTripFactory
import dev.sebastiano.clockblocker.opus.core.data.trip.TripTitleSuggester
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import dev.sebastiano.clockblocker.opus.core.testing.FakeProfileRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import dev.sebastiano.clockblocker.opus.core.testing.MutableClock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

class DefaultPlanRepositoryTest {
    private val sfoLhr = DemoData.sfoToLhr() // Departs 2026-06-15; fake plan spans Jun 13 – Jun 20.
    private val lhrSyd = DemoData.lhrToSydneyViaSingapore() // Departs 2026-06-25; fake plan spans Jun 23 – Jul 1.

    private class Env(
        val trips: FakeTripRepository,
        val profiles: FakeProfileRepository,
        val planner: FakeJetLagPlanner,
        val clock: MutableClock,
        val repo: DefaultPlanRepository,
    )

    private fun TestScope.env(
        trips: List<dev.sebastiano.clockblocker.opus.core.model.Trip> = listOf(sfoLhr, lhrSyd),
        planner: JetLagPlanner? = null,
        onboarded: Boolean = true,
    ): Env {
        val fake = FakeJetLagPlanner()
        val tripRepo = FakeTripRepository(trips)
        val profileRepo = FakeProfileRepository(if (onboarded) DemoData.profile else null)
        val clock = MutableClock(DemoData.Now)
        val repo = DefaultPlanRepository(
            trips = tripRepo,
            profiles = profileRepo,
            planner = planner ?: fake,
            clock = clock,
            ticker = clock,
            dispatchers = AppDispatchers.single(StandardTestDispatcher(testScheduler)),
            scope = backgroundScope,
        )
        return Env(tripRepo, profileRepo, fake, clock, repo)
    }

    @Test
    fun `plan is null until onboarding completes, then derived`() = runTest {
        val e = env(onboarded = false)
        e.repo.plan(sfoLhr.id).test {
            awaitItem().shouldBeNull()
            e.profiles.save(DemoData.profile)
            awaitItem()?.tripId shouldBe sfoLhr.id
        }
    }

    @Test
    fun `unknown trip has no plan`() = runTest {
        env().repo.plan("nope").first().shouldBeNull()
    }

    @Test
    fun `plans are memoised per trip and profile content`() = runTest {
        val e = env()
        val first = e.repo.plan(sfoLhr.id).first()
        val again = e.repo.plan(sfoLhr.id).first()
        again shouldBe first
        e.planner.calls shouldBe 1

        // Editing another trip doesn't recompute this one.
        e.trips.upsert(lhrSyd.copy(title = "Edited"))
        e.repo.plan(sfoLhr.id).first()
        e.planner.calls shouldBe 1

        // Editing this trip or the profile does.
        e.trips.upsert(sfoLhr.copy(title = "Edited"))
        e.repo.plan(sfoLhr.id).first()
        e.planner.calls shouldBe 2
        e.profiles.save(DemoData.profile.copy(intensity = Intensity.Max))
        e.repo.plan(sfoLhr.id).first()
        e.planner.calls shouldBe 3

        // Reverting to earlier content hits the cache again.
        e.trips.upsert(sfoLhr)
        e.profiles.save(DemoData.profile)
        e.repo.plan(sfoLhr.id).first() shouldBe first
        e.planner.calls shouldBe 3
    }

    @Test
    fun `plan flow re-emits when the trip changes`() = runTest {
        val e = env()
        e.repo.plan(sfoLhr.id).test {
            awaitItem().shouldNotBeNull()
            val later = sfoLhr.copy(legs = sfoLhr.legs.map { it.copy(departureLocal = it.departureLocal.plusDays(1), arrivalLocal = it.arrivalLocal.plusDays(1)) })
            e.trips.upsert(later)
            awaitItem()!!.days.first().date shouldBe sfoLhr.legs.first().departureLocal.toLocalDate().minusDays(1)
            e.trips.delete(sfoLhr.id)
            awaitItem().shouldBeNull()
        }
    }

    @Test
    fun `concurrent requests for the same inputs share one computation`() = runTest {
        val e = env()
        (1..8).map { async { e.repo.planFor(sfoLhr, DemoData.profile) } }.awaitAll().toSet().size shouldBe 1
        e.planner.calls shouldBe 1
    }

    @Test
    fun `planner failures propagate and are not cached`() = runTest {
        val attempts = AtomicInteger()
        val fake = FakeJetLagPlanner()
        val flaky = JetLagPlanner { trip, profile, now ->
            if (attempts.incrementAndGet() == 1) error("engine hiccup") else fake.plan(trip, profile, now)
        }
        val e = env(planner = flaky)
        shouldThrow<IllegalStateException> { e.repo.planFor(sfoLhr, DemoData.profile) }
        e.repo.planFor(sfoLhr, DemoData.profile).tripId shouldBe sfoLhr.id
        attempts.get() shouldBe 2
    }

    @Test
    fun `current plan follows time across trips`() = runTest {
        val e = env()
        e.repo.currentPlan.test {
            // Jun 12: nothing in progress -> next upcoming.
            awaitItem()?.tripId shouldBe sfoLhr.id
            // Jun 14: pre-trip days of SFO-LHR: same plan, no re-emission.
            e.clock.set(Instant.parse("2026-06-14T12:00:00Z"))
            testScheduler.advanceUntilIdle()
            expectNoEvents()
            // Jun 21: SFO-LHR plan over, LHR-SYD not started -> next upcoming.
            e.clock.set(Instant.parse("2026-06-21T12:00:00Z"))
            awaitItem()?.tripId shouldBe lhrSyd.id
            // Jun 26: in progress.
            e.clock.set(Instant.parse("2026-06-26T12:00:00Z"))
            testScheduler.advanceUntilIdle()
            expectNoEvents()
            // August: everything is over.
            e.clock.set(Instant.parse("2026-08-01T00:00:00Z"))
            awaitItem().shouldBeNull()
        }
    }

    @Test
    fun `an in-progress trip wins over an upcoming one`() = runTest {
        val e = env()
        e.clock.set(Instant.parse("2026-06-17T12:00:00Z")) // Adapting in London after SFO-LHR.
        e.repo.currentPlan.first()?.tripId shouldBe sfoLhr.id
    }

    @Test
    fun `when plans overlap the later departure wins`() = runTest {
        // Return flight 4 days after landing: its pre-trip days overlap the outbound adaptation tail.
        var n = 0
        val back = ReturnTripFactory(MutableClock(), TripTitleSuggester())
            .create(sfoLhr, departureLocal = LocalDateTime.of(2026, 6, 20, 11, 0)) { "back-${n++}" }
        val e = env(trips = listOf(sfoLhr, back))
        e.clock.set(Instant.parse("2026-06-19T09:00:00Z"))
        e.repo.currentPlan.first()?.tripId shouldBe back.id
    }

    @Test
    fun `current plan is null without a profile or trips`() = runTest {
        env(onboarded = false).repo.currentPlan.first().shouldBeNull()
        env(trips = emptyList()).repo.currentPlan.first().shouldBeNull()
    }

    @Test
    fun `plan window covers plan days and flights`() {
        val plan: JetLagPlan = FakeJetLagPlanner().plan(sfoLhr, DemoData.profile, DemoData.Now)
        val window = plan.window(sfoLhr)
        (sfoLhr.departure in window && sfoLhr.arrival in window) shouldBe true
        window.start shouldBe Instant.parse("2026-06-13T07:00:00Z") // Jun 13 00:00 in Los Angeles (PDT).
    }
}
