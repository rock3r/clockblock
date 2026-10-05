package dev.sebastiano.clockblocker.opus.core.data.fakes

import app.cash.turbine.test
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import dev.sebastiano.clockblocker.opus.core.testing.FakeAdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import dev.sebastiano.clockblocker.opus.core.testing.FakePlaceSearch
import dev.sebastiano.clockblocker.opus.core.testing.FakePlanRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeProfileRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeSettingsRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import dev.sebastiano.clockblocker.opus.core.testing.MutableClock
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The fakes other modules test against must honour the same contracts as the real implementations. */
class FakesTest {
    @Test
    fun `fake trips are sorted by departure and observable`() = runTest {
        val repo = FakeTripRepository()
        repo.trips.test {
            awaitItem() shouldBe emptyList()
            repo.upsert(DemoData.lhrToSydneyViaSingapore())
            awaitItem()
            repo.upsert(DemoData.sfoToLhr())
            awaitItem().map { it.id } shouldContainExactly listOf(DemoData.SfoLhrId, DemoData.LhrSydId)
            repo.delete(DemoData.SfoLhrId)
            awaitItem().map { it.id } shouldContainExactly listOf(DemoData.LhrSydId)
        }
        repo.deletedIds shouldBe listOf(DemoData.SfoLhrId)
        FakeTripRepository.withDemoTrips().current shouldBe DemoData.trips()
    }

    @Test
    fun `fake profile and settings`() = runTest {
        val profiles = FakeProfileRepository()
        profiles.profile.first().shouldBeNull()
        profiles.save(DemoData.profile)
        profiles.saveCount shouldBe 1
        FakeProfileRepository.onboarded().current shouldBe DemoData.profile

        val settings = FakeSettingsRepository()
        settings.update { it.copy(themeMode = ThemeMode.Light) }
        settings.settings.first().themeMode shouldBe ThemeMode.Light
    }

    @Test
    fun `fake advice log replaces outcomes per advice`() = runTest {
        val logs = FakeAdviceLogRepository()
        logs.log("t", "a", AdviceOutcome.Done)
        logs.log("t", "a", AdviceOutcome.Skipped)
        logs.current("t").size shouldBe 1
        logs.outcomeOf("t", "a") shouldBe AdviceOutcome.Skipped
        logs.logs("other").first() shouldBe emptyList()
    }

    @Test
    fun `fake advice log clears one outcome`() = runTest {
        val logs = FakeAdviceLogRepository()
        logs.log("t", "a", AdviceOutcome.Done)
        logs.log("t", "b", AdviceOutcome.Skipped)
        logs.clear("t", "a")
        logs.outcomeOf("t", "a").shouldBeNull()
        logs.outcomeOf("t", "b") shouldBe AdviceOutcome.Skipped
        logs.clear("none", "a")
        logs.logs("none").first() shouldBe emptyList()
    }

    @Test
    fun `fake plans`() = runTest {
        val plans = FakePlanRepository.withDemoPlans()
        plans.currentPlan.first()?.tripId shouldBe DemoData.SfoLhrId
        plans.plan(DemoData.LhrSydId).first()?.tripId shouldBe DemoData.LhrSydId

        val other = FakeJetLagPlanner().plan(DemoData.lhrToSydneyViaSingapore(), DemoData.profile, DemoData.Now)
        plans.setCurrentPlan(other)
        plans.currentPlan.first() shouldBe other
        plans.removePlan(DemoData.SfoLhrId)
        plans.plan(DemoData.SfoLhrId).first().shouldBeNull()
    }

    @Test
    fun `fake place search uses production ranking`() = runTest {
        val search = FakePlaceSearch()
        search.search("lon").map { it.code } shouldContainExactly listOf("LHR", "LGW")
        search.search("new york").map { it.code } shouldContainExactly listOf("JFK", "EWR")
        search.search("zürich").single().code shouldBe "ZRH"
        search.byCode("syd") shouldBe DemoData.SYD
        search.queries shouldBe listOf("lon", "new york", "zürich")
    }

    @Test
    fun `fake planner counts calls and is deterministic`() {
        val planner = FakeJetLagPlanner()
        val a = planner.plan(DemoData.sfoToLhr(), DemoData.profile, DemoData.Now)
        a shouldBe planner.plan(DemoData.sfoToLhr(), DemoData.profile, DemoData.Now)
        planner.calls shouldBe 2
        a.shiftHours shouldBe 8.0
    }

    @Test
    fun `mutable clock ticks when moved`() = runTest {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        clock.ticks().test {
            awaitItem() shouldBe Instant.parse("2026-01-01T00:00:00Z")
            clock.advanceBy(Duration.ofHours(1))
            awaitItem() shouldBe Instant.parse("2026-01-01T01:00:00Z")
            val tokyo = clock.withZone(ZoneId.of("Asia/Tokyo"))
            tokyo.set(Instant.parse("2026-02-01T00:00:00Z")) // Views share time.
            awaitItem() shouldBe Instant.parse("2026-02-01T00:00:00Z")
            clock.instant() shouldBe tokyo.instant()
            tokyo.zone shouldBe ZoneId.of("Asia/Tokyo")
        }
    }
}
