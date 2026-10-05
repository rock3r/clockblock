package dev.sebastiano.clockblocker.opus.feature.plan

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.data.export.ExportLabels
import dev.sebastiano.clockblocker.opus.core.data.export.IcsExporter
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.testing.FakeAdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakePlanRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeProfileRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeSettingsRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import dev.sebastiano.clockblocker.opus.core.testing.MainDispatcherRule
import dev.sebastiano.clockblocker.opus.core.testing.MutableClock
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PlanViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val tripId = DemoData.SfoLhrId
    private val clock = MutableClock(PlanFixtures.FakePreTrip)
    private val plans = FakePlanRepository.withDemoPlans()
    private val trips = FakeTripRepository.withDemoTrips()
    private val logs = FakeAdviceLogRepository()
    private val settings = FakeSettingsRepository()
    private val celebrations = FakeCelebrationStore()
    private val snoozed = mutableListOf<String>()

    private fun viewModel(id: String? = tripId) = PlanViewModel(
        tripId = id,
        planRepository = plans,
        tripRepository = trips,
        adviceLogRepository = logs,
        settingsRepository = settings,
        profileRepository = FakeProfileRepository.onboarded(),
        celebrationStore = celebrations,
        snoozer = { snoozed += it },
        icsExporter = IcsExporter(),
        clock = clock,
        ticker = clock,
    )

    @Test
    fun `starts loading, then shows the trip's plan at now`() = runTest {
        val vm = viewModel()
        vm.state.test {
            val ready = awaitReady()
            ready.tripId shouldBe tripId
            ready.trip?.id shouldBe tripId
            ready.now shouldBe PlanFixtures.FakePreTrip
            ready.moment.stage shouldBe PlanStage.PreTrip
            ready.moment.active?.type shouldBe AdviceType.SeeBrightLight
            ready.kind shouldBe PlanKind.Adapt
            ready.celebrate.shouldBeFalse()
        }
    }

    @Test
    fun `null trip id follows the current plan`() = runTest {
        viewModel(null).state.test {
            awaitReady().tripId shouldBe tripId
        }
    }

    @Test
    fun `a deleted trip is reported as missing`() = runTest {
        viewModel("nope").state.test {
            awaitNoPlan().missing.shouldBeTrue()
        }
    }

    @Test
    fun `no current plan is an empty state`() = runTest {
        plans.setCurrentPlan(null)
        viewModel(null).state.test {
            awaitNoPlan().missing.shouldBeFalse()
        }
    }

    @Test
    fun `now advances with the clock`() = runTest {
        viewModel().state.test {
            awaitReady().moment.stage shouldBe PlanStage.PreTrip
            clock.set(PlanFixtures.FakeInFlight)
            awaitReady { it.moment.stage == PlanStage.Travel }.moment.active?.type shouldBe AdviceType.Flight
            clock.set(PlanFixtures.FakeArrival)
            awaitReady { it.moment.stage == PlanStage.Adapting }.moment.zone.id shouldBe "Europe/London"
        }
    }

    @Test
    fun `logging an outcome stores it and shows it`() = runTest {
        val vm = viewModel()
        vm.state.test {
            val active = awaitReady().moment.active!!
            vm.log(active.id, AdviceOutcome.Done)
            awaitReady { it.outcomes[active.id] == AdviceOutcome.Done }
            logs.outcomeOf(tripId, active.id) shouldBe AdviceOutcome.Done
            vm.log(active.id, AdviceOutcome.CantDo)
            awaitReady { it.outcomes[active.id] == AdviceOutcome.CantDo }
        }
    }

    @Test
    fun `undo forgets a first outcome`() = runTest {
        val vm = viewModel()
        vm.state.test {
            val active = awaitReady().moment.active!!
            vm.log(active.id, AdviceOutcome.Done)
            awaitReady { it.outcomes[active.id] == AdviceOutcome.Done }
            vm.undo(active.id, previous = null)
            awaitReady { active.id !in it.outcomes }
            logs.outcomeOf(tripId, active.id) shouldBe null
        }
    }

    @Test
    fun `undo puts back the outcome it replaced`() = runTest {
        val vm = viewModel()
        vm.state.test {
            val active = awaitReady().moment.active!!
            vm.log(active.id, AdviceOutcome.Skipped)
            awaitReady { it.outcomes[active.id] == AdviceOutcome.Skipped }
            vm.log(active.id, AdviceOutcome.Done)
            awaitReady { it.outcomes[active.id] == AdviceOutcome.Done }
            vm.undo(active.id, previous = AdviceOutcome.Skipped)
            awaitReady { it.outcomes[active.id] == AdviceOutcome.Skipped }
        }
    }

    @Test
    fun `snooze goes to the scheduler`() = runTest {
        val vm = viewModel()
        vm.state.test {
            val active = awaitReady().moment.active!!
            vm.snooze(active.id)
            snoozed shouldBe listOf(active.id)
        }
    }

    @Test
    fun `the celebration shows once adapted, until it has been seen`() = runTest {
        clock.set(PlanFixtures.FakeAdapted)
        val vm = viewModel()
        vm.state.test {
            awaitReady().celebrate.shouldBeTrue()
            vm.celebrationShown()
            awaitReady { !it.celebrate }
            celebrations.marked shouldBe setOf(tripId)
        }
    }

    @Test
    fun `night-safe follows the plan and the setting`() = runTest {
        clock.set(PlanFixtures.FakeAvoidLight)
        val vm = viewModel()
        vm.state.test {
            awaitReady().nightSafe.shouldBeTrue()
            settings.set(AppSettings(nightSafeAuto = false))
            awaitReady { !it.nightSafe }
        }
    }

    @Test
    fun `easter eggs are off with reduce motion`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitReady().easterEggs.shouldBeTrue()
            settings.set(AppSettings(reduceMotion = true))
            awaitReady { !it.easterEggs }
        }
    }

    @Test
    fun `calendar export uses the labels`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitReady()
            val ics = vm.calendar(ExportLabels(adviceTitle = { "T-${it.name}" }, disclaimer = "Not medical advice"))!!
            ics shouldContain "BEGIN:VCALENDAR"
            ics shouldContain "T-SeeBrightLight"
        }
    }

    private suspend fun ReceiveTurbine<PlanUiState>.awaitReady(
        until: (PlanUiState.Ready) -> Boolean = { true },
    ): PlanUiState.Ready {
        while (true) {
            val item = awaitItem()
            if (item is PlanUiState.Ready && until(item)) return item
        }
    }

    private suspend fun ReceiveTurbine<PlanUiState>.awaitNoPlan(): PlanUiState.NoPlan {
        while (true) {
            val item = awaitItem()
            if (item is PlanUiState.NoPlan) return item
            item.shouldBeInstanceOf<PlanUiState.Loading>()
        }
    }
}

/** In-memory [CelebrationStore]. */
internal class FakeCelebrationStore : CelebrationStore {
    private val state = MutableStateFlow(emptySet<String>())
    val marked: Set<String> get() = state.value

    override fun celebrated(tripId: String): Flow<Boolean> = state.map { tripId in it }

    override suspend fun markCelebrated(tripId: String) {
        state.update { it + tripId }
    }
}
