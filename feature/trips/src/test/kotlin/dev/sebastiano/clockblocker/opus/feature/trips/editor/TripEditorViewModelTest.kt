package dev.sebastiano.clockblocker.opus.feature.trips.editor

import app.cash.turbine.test
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.data.trip.IssueSeverity
import dev.sebastiano.clockblocker.opus.core.data.trip.ReturnTripFactory
import dev.sebastiano.clockblocker.opus.core.data.trip.TripIssue
import dev.sebastiano.clockblocker.opus.core.data.trip.TripTitleSuggester
import dev.sebastiano.clockblocker.opus.core.data.trip.TripValidator
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.data.time.AppDispatchers
import dev.sebastiano.clockblocker.opus.core.data.trip.FlightEstimates
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import dev.sebastiano.clockblocker.opus.core.testing.FakePlaceSearch
import dev.sebastiano.clockblocker.opus.core.testing.FakeProfileRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import dev.sebastiano.clockblocker.opus.core.testing.MainDispatcherRule
import dev.sebastiano.clockblocker.opus.core.testing.MutableClock
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TripEditorViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val clock = MutableClock(DemoData.Now)
    private val trips = FakeTripRepository.withDemoTrips()
    private val search = FakePlaceSearch()

    private val planner = FakeJetLagPlanner()
    private val profiles = FakeProfileRepository.onboarded()

    private fun viewModel(args: TripEditorArgs = TripEditorArgs()) = TripEditorViewModel(
        args = args,
        trips = trips,
        places = search,
        validator = TripValidator(),
        titles = TripTitleSuggester(),
        returnTrips = ReturnTripFactory(clock, TripTitleSuggester()),
        clock = clock,
        planner = planner,
        profiles = profiles,
        dispatchers = AppDispatchers(main.dispatcher, main.dispatcher, main.dispatcher),
    )

    private val from0 = PlaceFieldRef(0, LegEnd.Origin)
    private val to0 = PlaceFieldRef(0, LegEnd.Destination)

    /** LIS 2026-07-01 10:00 → HND [arrivalDate] 07:30 (a 14 h 30 m flight when the date is right). */
    private fun TripEditorViewModel.fillLisbonTokyo(arrivalDate: LocalDate = LocalDate.of(2026, 7, 2)) {
        onPlaceSelected(from0, DemoData.LIS)
        onPlaceSelected(to0, DemoData.HND)
        onDepartureDateChange(0, LocalDate.of(2026, 7, 1))
        onDepartureTimeChange(0, LocalTime.of(10, 0))
        onArrivalDateChange(0, arrivalDate)
        onArrivalTimeChange(0, LocalTime.of(7, 30))
    }

    @Test
    fun `a new trip starts with one empty leg and can't be saved yet`() = runTest(main.dispatcher) {
        val state = viewModel().state.value
        state.loading shouldBe false
        state.mode shouldBe EditorMode.New
        state.legs shouldHaveSize 1
        state.dirty shouldBe false
        state.complete shouldBe false
        state.canSave shouldBe false
    }

    @Test
    fun `typing searches airports offline and Enter picks the top match`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onPlaceQueryChange(from0, "lis")
        vm.state.value.search.shouldNotBeNull().searching shouldBe true
        vm.state.value.legs[0].origin.place.shouldBeNull()
        advanceUntilIdle()
        vm.state.value.search.shouldNotBeNull().results.first().code shouldBe "LIS"
        vm.pickTopResult(from0) shouldBe true
        vm.state.value.legs[0].origin.place shouldBe DemoData.LIS
        vm.state.value.legs[0].origin.query shouldBe "Lisbon"
        vm.state.value.search.shouldBeNull()
    }

    @Test
    fun `Enter picks the match on screen even when the keyboard autocorrects the query as it submits`() = runTest(main.dispatcher) {
        // Gboard autocorrects even with autoCorrectEnabled = false (Compose can't ask for NO_SUGGESTIONS): pressing
        // Enter after "lis" commits "List" first, which starts a new search. The user meant the top row they saw.
        val vm = viewModel()
        vm.onPlaceQueryChange(from0, "lis")
        advanceUntilIdle()
        vm.onPlaceQueryChange(from0, "List")
        vm.pickTopResult(from0) shouldBe true
        vm.state.value.legs[0].origin.place shouldBe DemoData.LIS
    }

    @Test
    fun `editing a picked airport's text clears the pick`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onPlaceSelected(from0, DemoData.LIS)
        vm.onPlaceQueryChange(from0, "Lisbo")
        vm.state.value.legs[0].origin.place.shouldBeNull()
    }

    @Test
    fun `search debounces and only the latest query wins`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onPlaceQueryChange(to0, "t")
        vm.onPlaceQueryChange(to0, "to")
        vm.onPlaceQueryChange(to0, "tok")
        advanceUntilIdle()
        search.queries shouldContainExactly listOf("tok")
        vm.state.value.search.shouldNotBeNull().results.map { it.code }.toSet() shouldBe setOf("HND", "NRT")
    }

    @Test
    fun `arrival before departure is an error with a one-tap next-day fix`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.fillLisbonTokyo(arrivalDate = LocalDate.of(2026, 7, 1))
        val issue = vm.state.value.issuesFor(0).single { it.severity == IssueSeverity.Error }
            .shouldBeInstanceOf<TripIssue.ArrivalNotAfterDeparture>()
        vm.state.value.canSave shouldBe false
        val fix = issue.suggestedArrivalLocal.shouldNotBeNull()
        fix shouldBe LocalDateTime.of(2026, 7, 2, 7, 30)

        vm.applySuggestedArrival(0, fix)
        vm.state.value.issues.filter { it.severity == IssueSeverity.Error }.shouldBeEmpty()
        vm.state.value.canSave shouldBe true
    }

    @Test
    fun `first arrival time assumes the departure date`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onDepartureDateChange(0, LocalDate.of(2026, 7, 1))
        vm.onArrivalTimeChange(0, LocalTime.of(7, 30))
        vm.state.value.legs[0].arrivalDate shouldBe LocalDate.of(2026, 7, 1)
    }

    @Test
    fun `moving the departure date carries the arrival date along`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.fillLisbonTokyo()
        vm.onDepartureDateChange(0, LocalDate.of(2026, 7, 10))
        vm.state.value.legs[0].arrivalDate shouldBe LocalDate.of(2026, 7, 11)
    }

    @Test
    fun `saving a new trip stores it with the suggested title and emits its id`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.fillLisbonTokyo()
        vm.state.value.suggestedTitle shouldBe "Lisbon → Tokyo"
        vm.eventFlow.test {
            vm.save()
            val saved = awaitItem().shouldBeInstanceOf<TripEditorEvent.Saved>()
            val trip = trips.current.single { it.id == saved.tripId }
            trip.title shouldBe "Lisbon → Tokyo"
            trip.legs.single().origin shouldBe DemoData.LIS
            trip.legs.single().arrivalLocal shouldBe LocalDateTime.of(2026, 7, 2, 7, 30)
            trip.createdAt shouldBe DemoData.Now
        }
        vm.state.value.dirty shouldBe false
    }

    @Test
    fun `a typed title wins, a cleared one falls back to the suggestion`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.fillLisbonTokyo()
        vm.onTitleChange("Conference")
        vm.state.value.effectiveTitle shouldBe "Conference"
        vm.onTitleChange("")
        vm.state.value.effectiveTitle shouldBe "Lisbon → Tokyo"
    }

    @Test
    fun `editing loads the trip and tracks dirtiness against it`() = runTest(main.dispatcher) {
        val vm = viewModel(TripEditorArgs(tripId = DemoData.LhrSydId))
        val state = vm.state.value
        state.mode shouldBe EditorMode.Edit
        state.legs.map { it.origin.place?.code } shouldContainExactly listOf("LHR", "SIN")
        state.form.titleEdited shouldBe false
        state.layoverBefore(1) shouldBe Duration.ofMinutes(135)
        state.dirty shouldBe false

        vm.onFlightNumberChange(0, "BA 12")
        vm.state.value.dirty shouldBe true
        vm.onFlightNumberChange(0, "BA 11")
        vm.state.value.dirty shouldBe false
    }

    @Test
    fun `saving an edit keeps id and creation time`() = runTest(main.dispatcher) {
        val original = trips.current.single { it.id == DemoData.SfoLhrId }
        val vm = viewModel(TripEditorArgs(tripId = DemoData.SfoLhrId))
        vm.onStrategyChange(AdaptationStrategy.StayOnHomeTime)
        vm.eventFlow.test {
            vm.save()
            awaitItem() shouldBe TripEditorEvent.Saved(DemoData.SfoLhrId)
        }
        val saved = trips.current.single { it.id == DemoData.SfoLhrId }
        saved.strategyOverride shouldBe AdaptationStrategy.StayOnHomeTime
        saved.createdAt shouldBe original.createdAt
        saved.legs shouldBe original.legs
    }

    @Test
    fun `unknown trip id shows not found`() = runTest(main.dispatcher) {
        viewModel(TripEditorArgs(tripId = "nope")).state.value.notFound shouldBe true
    }

    @Test
    fun `adding a connection starts where the last flight lands, on its arrival date`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.fillLisbonTokyo()
        vm.addLeg()
        val added = vm.state.value.legs[1]
        added.origin.place shouldBe DemoData.HND
        added.departureDate shouldBe LocalDate.of(2026, 7, 2)
        vm.state.value.canSave shouldBe false

        vm.removeLeg(1)
        vm.state.value.legs shouldHaveSize 1
        vm.state.value.canSave shouldBe true
    }

    @Test
    fun `picking a destination fills an empty next origin`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.addLeg()
        vm.onPlaceSelected(to0, DemoData.SIN)
        vm.state.value.legs[1].origin.place shouldBe DemoData.SIN
    }

    @Test
    fun `a disconnected itinerary offers departing from the arrival airport`() = runTest(main.dispatcher) {
        val vm = viewModel(TripEditorArgs(tripId = DemoData.LhrSydId))
        vm.onPlaceSelected(PlaceFieldRef(1, LegEnd.Origin), DemoData.HND)
        vm.state.value.issuesFor(1).any { it is TripIssue.LegsNotConnected } shouldBe true
        vm.useConnectingOrigin(1)
        vm.state.value.legs[1].origin.place shouldBe DemoData.SIN
        vm.state.value.issuesFor(1).none { it is TripIssue.LegsNotConnected } shouldBe true
    }

    @Test
    fun `return trip is prefilled reversed and linked to its outbound on save`() = runTest(main.dispatcher) {
        val vm = viewModel(TripEditorArgs(returnOfTripId = DemoData.LhrSydId))
        val state = vm.state.value
        state.mode shouldBe EditorMode.Return
        state.legs.map { it.origin.place?.code to it.destination.place?.code } shouldContainExactly
            listOf("SYD" to "SIN", "SIN" to "LHR")
        state.dirty shouldBe false
        state.canSave shouldBe true
        vm.eventFlow.test {
            vm.save()
            val saved = awaitItem().shouldBeInstanceOf<TripEditorEvent.Saved>()
            val returnTrip = trips.current.single { it.id == saved.tripId }
            trips.current.single { it.id == DemoData.LhrSydId }.returnDeparture shouldBe returnTrip.departure
        }
    }

    @Test
    fun `return departure is stored in the destination's local time`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.fillLisbonTokyo()
        vm.onReturnDateChange(LocalDate.of(2026, 7, 4))
        vm.onReturnTimeChange(LocalTime.of(9, 15))
        vm.eventFlow.test {
            vm.save()
            val saved = awaitItem().shouldBeInstanceOf<TripEditorEvent.Saved>()
            trips.current.single { it.id == saved.tripId }.returnDeparture shouldBe Instant.parse("2026-07-04T00:15:00Z")
        }
    }

    @Test
    fun `I'm delayed is offered from 48 h before departure and shifts the leg`() = runTest(main.dispatcher) {
        viewModel(TripEditorArgs(tripId = DemoData.SfoLhrId)).state.value.canReportDelay shouldBe false

        clock.set(Instant.parse("2026-06-15T20:00:00Z")) // 13:00 in San Francisco on departure day
        val vm = viewModel(TripEditorArgs(tripId = DemoData.SfoLhrId))
        vm.state.value.canReportDelay shouldBe true
        vm.state.value.delayLegIndex shouldBe 0
        vm.delayLeg(0, Duration.ofMinutes(90))
        val leg = vm.state.value.legs[0]
        leg.departureLocal shouldBe LocalDateTime.of(2026, 6, 15, 21, 0)
        leg.arrivalLocal shouldBe LocalDateTime.of(2026, 6, 16, 15, 20)
        vm.state.value.dirty shouldBe true
    }

    @Test
    fun `new and return trips never offer the delay shortcut`() = runTest(main.dispatcher) {
        clock.set(Instant.parse("2026-06-15T20:00:00Z"))
        viewModel().state.value.canReportDelay shouldBe false
        viewModel(TripEditorArgs(returnOfTripId = DemoData.SfoLhrId)).state.value.canReportDelay shouldBe false
    }

    // region Arrival estimate

    private fun TripEditorViewModel.fillLisbonTokyoDeparture() {
        onPlaceSelected(from0, DemoData.LIS)
        onPlaceSelected(to0, DemoData.HND)
        onDepartureDateChange(0, LocalDate.of(2026, 7, 1))
        onDepartureTimeChange(0, LocalTime.of(10, 0))
    }

    @Test
    fun `an untouched arrival is estimated from the distance once airports and departure are set`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onPlaceSelected(from0, DemoData.LIS)
        vm.onPlaceSelected(to0, DemoData.HND)
        vm.onDepartureDateChange(0, LocalDate.of(2026, 7, 1))
        vm.state.value.legs[0].arrivalLocal.shouldBeNull()

        vm.onDepartureTimeChange(0, LocalTime.of(10, 0))
        val leg = vm.state.value.legs[0]
        leg.arrivalEstimated shouldBe true
        leg.arrivalLocal shouldBe FlightEstimates.arrivalLocal(DemoData.LIS, DemoData.HND, LocalDateTime.of(2026, 7, 1, 10, 0))
        vm.state.value.canSave shouldBe true
    }

    @Test
    fun `the estimate follows the departure until the user sets the arrival`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.fillLisbonTokyoDeparture()
        vm.onDepartureTimeChange(0, LocalTime.of(12, 0))
        vm.state.value.legs[0].arrivalLocal shouldBe
            FlightEstimates.arrivalLocal(DemoData.LIS, DemoData.HND, LocalDateTime.of(2026, 7, 1, 12, 0))

        vm.onArrivalTimeChange(0, LocalTime.of(7, 30))
        vm.state.value.legs[0].arrivalEstimated shouldBe false
        vm.onDepartureTimeChange(0, LocalTime.of(9, 0))
        vm.state.value.legs[0].arrivalTime shouldBe LocalTime.of(7, 30)
    }

    @Test
    fun `an estimate is withdrawn when an airport is cleared`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.fillLisbonTokyoDeparture()
        vm.onPlaceQueryChange(to0, "Toky")
        val leg = vm.state.value.legs[0]
        leg.arrivalLocal.shouldBeNull()
        leg.arrivalEstimated shouldBe false
    }

    @Test
    fun `saved trips keep their arrival times`() = runTest(main.dispatcher) {
        val original = trips.current.single { it.id == DemoData.SfoLhrId }
        val vm = viewModel(TripEditorArgs(tripId = DemoData.SfoLhrId))
        vm.onDepartureTimeChange(0, LocalTime.of(6, 0))
        vm.state.value.legs[0].arrivalLocal shouldBe original.legs[0].arrivalLocal
    }

    // endregion

    @Test
    fun `swap exchanges From and To and re-estimates the arrival`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.fillLisbonTokyoDeparture()
        vm.swapPlaces(0)
        val leg = vm.state.value.legs[0]
        leg.origin.place shouldBe DemoData.HND
        leg.destination.place shouldBe DemoData.LIS
        leg.arrivalLocal shouldBe FlightEstimates.arrivalLocal(DemoData.HND, DemoData.LIS, LocalDateTime.of(2026, 7, 1, 10, 0))
    }

    // region Popular airports

    @Test
    fun `focusing an empty airport field offers popular airports, minus the other end`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onPlaceSelected(from0, DemoData.LHR)
        vm.onPlaceFieldFocused(to0)
        advanceUntilIdle()
        val popular = vm.state.value.search.shouldNotBeNull()
        popular.field shouldBe to0
        popular.popular shouldBe true
        popular.query shouldBe ""
        popular.results.map { it.code }.take(3) shouldContainExactly listOf("JFK", "LAX", "SFO")
        popular.results.none { it.code == "LHR" } shouldBe true
        // Enter on an empty field never picks a suggestion.
        vm.pickTopResult(to0) shouldBe false
    }

    @Test
    fun `focusing a filled field offers nothing`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onPlaceSelected(from0, DemoData.LHR)
        vm.onPlaceFieldFocused(from0)
        advanceUntilIdle()
        vm.state.value.search.shouldBeNull()
    }

    @Test
    fun `clearing the query brings the popular airports back, and typing replaces them`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onPlaceQueryChange(from0, "to")
        advanceUntilIdle()
        vm.onPlaceQueryChange(from0, "")
        advanceUntilIdle()
        vm.state.value.search.shouldNotBeNull().popular shouldBe true

        vm.onPlaceQueryChange(from0, "lis")
        // Popular picks never linger under a typed query while its search runs.
        vm.state.value.search.shouldNotBeNull().results.shouldBeEmpty()
        advanceUntilIdle()
        vm.state.value.search.shouldNotBeNull().results.first().code shouldBe "LIS"
    }

    // endregion

    // region Shift preview

    @Test
    fun `a complete draft gets the planner's shift preview`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.fillLisbonTokyoDeparture()
        advanceUntilIdle()
        val preview = vm.state.value.preview.shouldNotBeNull()
        preview.shiftHours shouldBe 8.0
        preview.direction shouldBe ShiftDirection.Advance
        preview.strategy shouldBe AdaptationStrategy.Adapt
        preview.daysToAdapt shouldBe 8.0 / 1.5
        preview.daysWithoutPlan shouldBe 8.0
    }

    @Test
    fun `the preview follows the strategy and goes away when the draft is incomplete`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.fillLisbonTokyoDeparture()
        vm.onStrategyChange(AdaptationStrategy.StayOnHomeTime)
        advanceUntilIdle()
        vm.state.value.preview.shouldNotBeNull().strategy shouldBe AdaptationStrategy.StayOnHomeTime

        vm.onPlaceQueryChange(to0, "Toky")
        vm.state.value.preview.shouldBeNull()
    }

    @Test
    fun `title edits don't re-run the planner`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.fillLisbonTokyoDeparture()
        advanceUntilIdle()
        val calls = planner.calls
        vm.onTitleChange("Conference")
        advanceUntilIdle()
        planner.calls shouldBe calls
    }

    @Test
    fun `no preview without a profile`() = runTest(main.dispatcher) {
        profiles.set(null)
        val vm = viewModel()
        vm.fillLisbonTokyoDeparture()
        advanceUntilIdle()
        vm.state.value.preview.shouldBeNull()
    }

    // endregion
}
