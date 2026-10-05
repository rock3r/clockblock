package dev.sebastiano.clockblocker.opus.feature.trips.list

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.data.trip.TripTitleSuggester
import dev.sebastiano.clockblocker.opus.core.testing.FakePlanRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import dev.sebastiano.clockblocker.opus.core.testing.MainDispatcherRule
import dev.sebastiano.clockblocker.opus.core.testing.MutableClock
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TripsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val clock = MutableClock(DemoData.Now) // 2026-06-12T16:00Z, three days before SFO → LHR
    private val trips = FakeTripRepository.withDemoTrips()
    private val plans = FakePlanRepository.withDemoPlans()

    private fun viewModel(repository: FakeTripRepository = trips) =
        TripsViewModel(repository, plans, clock, clock, TripTitleSuggester())

    private suspend fun ReceiveTurbine<TripsUiState>.awaitLoaded(): TripsUiState {
        var state = awaitItem()
        while (state.loading) state = awaitItem()
        return state
    }

    @Test
    fun `groups trips into sections with their plans`() = runTest {
        viewModel().state.test {
            val state = awaitLoaded()
            state.inProgress.shouldBeEmpty()
            state.upcoming.map { it.id } shouldContainExactly listOf(DemoData.SfoLhrId, DemoData.LhrSydId)
            state.upcoming.first().status shouldBe TripStatus.StartsIn(3)
            state.returnCandidate shouldBe null
        }
    }

    @Test
    fun `re-evaluates sections as time passes`() = runTest {
        viewModel().state.test {
            awaitLoaded()
            clock.set(Instant.parse("2026-06-17T12:00:00Z"))
            val state = awaitItem()
            state.inProgress.map { it.id } shouldContainExactly listOf(DemoData.SfoLhrId)
            state.inProgress.single().status shouldBe TripStatus.Day(2, 4)
            state.inProgress.single().adaptation.shouldNotBeNull()
            state.returnCandidate?.id shouldBe DemoData.SfoLhrId
        }
    }

    @Test
    fun `no trips is the empty state`() = runTest {
        viewModel(FakeTripRepository()).state.test {
            awaitLoaded().isEmpty shouldBe true
        }
    }

    @Test
    fun `demo trip opens once created`() = runTest {
        val vm = viewModel(FakeTripRepository())
        vm.eventFlow.test {
            vm.createDemoTrip()
            val event = awaitItem().shouldBeInstanceOf<TripsEvent.OpenTrip>()
            event.tripId shouldBe "demo-try-sfo-lhr"
        }
    }

    @Test
    fun `demo trip is stored with departure three days after today`() = runTest {
        val repository = FakeTripRepository()
        val vm = viewModel(repository)
        vm.eventFlow.test {
            vm.createDemoTrip()
            awaitItem()
        }
        val demo = repository.current.single()
        demo.legs.first().departureLocal.toLocalDate() shouldBe LocalDate.of(2026, 6, 15)
        demo.createdAt shouldBe DemoData.Now
    }

    @Test
    fun `delete removes the trip and undo restores it`() = runTest {
        val vm = viewModel()
        vm.eventFlow.test {
            vm.delete(DemoData.SfoLhrId)
            val deleted = awaitItem().shouldBeInstanceOf<TripsEvent.Deleted>()
            trips.current.map { it.id } shouldContainExactly listOf(DemoData.LhrSydId)
            vm.undoDelete(deleted.trip)
        }
        trips.current.map { it.id } shouldContainExactly listOf(DemoData.SfoLhrId, DemoData.LhrSydId)
    }

    @Test
    fun `duplicate copies flights with fresh ids`() = runTest {
        val vm = viewModel()
        vm.eventFlow.test {
            vm.duplicate(DemoData.LhrSydId)
            val event = awaitItem().shouldBeInstanceOf<TripsEvent.Duplicated>()
            val copy = trips.current.single { it.id == event.tripId }
            val original = trips.current.single { it.id == DemoData.LhrSydId }
            copy.legs.map { it.origin to it.destination } shouldBe original.legs.map { it.origin to it.destination }
            copy.legs.map { it.id }.intersect(original.legs.map { it.id }.toSet()).shouldBeEmpty()
            copy.createdAt shouldBe DemoData.Now
        }
    }
}
