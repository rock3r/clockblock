package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.data.trip.ReturnTripFactory
import dev.sebastiano.clockblocker.opus.core.data.trip.TripTitleSuggester
import dev.sebastiano.clockblocker.opus.core.data.trip.TripValidator
import dev.sebastiano.clockblocker.opus.core.data.time.AppDispatchers
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import dev.sebastiano.clockblocker.opus.core.testing.FakePlaceSearch
import dev.sebastiano.clockblocker.opus.core.testing.FakeProfileRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import dev.sebastiano.clockblocker.opus.core.testing.MainDispatcherRule
import dev.sebastiano.clockblocker.opus.core.testing.MutableClock
import dev.sebastiano.clockblocker.opus.feature.trips.TripsSamples
import dev.sebastiano.clockblocker.opus.feature.trips.TripsScreenshotTest
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives the real editor (route + ViewModel + fakes) through the flows a traveller actually uses. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w411dp-h2400dp-xhdpi")
class TripEditorFlowTest : TripsScreenshotTest() {

    @get:Rule
    val main = MainDispatcherRule()

    private val clock = MutableClock(DemoData.Now)
    private val trips = FakeTripRepository(DemoData.trips() + TripsSamples.lisbonTokyoWrongDate())
    private val done = mutableListOf<String?>()
    private var backs = 0

    private fun open(args: TripEditorArgs = TripEditorArgs()): TripEditorViewModel {
        val vm = TripEditorViewModel(
            args = args,
            trips = trips,
            places = FakePlaceSearch(),
            validator = TripValidator(),
            titles = TripTitleSuggester(),
            returnTrips = ReturnTripFactory(clock, TripTitleSuggester()),
            clock = clock,
            planner = FakeJetLagPlanner(),
            profiles = FakeProfileRepository.onboarded(),
            dispatchers = AppDispatchers(main.dispatcher, main.dispatcher, main.dispatcher),
        )
        setContent { TripEditorRoute(viewModel = vm, onDone = { done += it }, onBack = { backs++ }) }
        settle()
        return vm
    }

    private fun settle() {
        main.dispatcher.scheduler.advanceUntilIdle()
        compose.waitForIdle()
    }

    private fun tag(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = false)

    @Test
    fun fixingTheArrivalDateUnblocksSaving() {
        open(TripEditorArgs(tripId = "lis-hnd-typo"))
        tag(TripsTestTags.EditorSave).assertIsNotEnabled()
        tag(TripsTestTags.editorIssue(0)).performScrollTo()

        tag(TripsTestTags.editorFix(0)).performScrollTo().performClick()
        settle()

        tag(TripsTestTags.EditorSave).assertIsEnabled().performClick()
        settle()
        done shouldContainExactly listOf("lis-hnd-typo")
        val saved = trips.current.single { it.id == "lis-hnd-typo" }
        (saved.legs.single().arrivalLocal > saved.legs.single().departureLocal) shouldBe true
    }

    @Test
    fun arrivalBeforeDepartureSaysWhatToFixInPlainWords() {
        open(TripEditorArgs(tripId = "lis-hnd-typo"))
        tag(TripsTestTags.editorIssue(0)).performScrollTo()

        val issue = hasAnyAncestor(hasTestTag(TripsTestTags.editorIssue(0)))
        compose.onNode(issue and hasText("Fix this")).assertExists()
        compose.onNode(issue and hasText("Error")).assertDoesNotExist()
        compose.onNode(
            issue and hasText(
                "Lands before it takes off. Each time is local to its airport, so check the arrival date and time.",
            ),
        ).assertExists()
    }

    @Test
    fun movingAnEstimatedArrivalToTheDepartureDayOffersAFixThatClearsIt() {
        val vm = open()
        val lis = DemoData.LIS
        val hnd = DemoData.HND
        vm.onPlaceSelected(PlaceFieldRef(0, LegEnd.Origin), lis)
        vm.onPlaceSelected(PlaceFieldRef(0, LegEnd.Destination), hnd)
        val day = java.time.LocalDate.of(2026, 10, 7)
        vm.onDepartureDateChange(0, day)
        vm.onDepartureTimeChange(0, java.time.LocalTime.of(9, 0))
        settle()
        tag(TripsTestTags.editorArrivalEstimate(0)).performScrollTo()

        vm.onArrivalDateChange(0, day)
        settle()
        compose.onNodeWithTag(TripsTestTags.editorArrivalEstimate(0)).assertDoesNotExist()
        tag(TripsTestTags.editorFix(0)).performScrollTo().performClick()
        settle()

        compose.onNodeWithTag(TripsTestTags.editorFix(0)).assertDoesNotExist()
        vm.state.value.issuesFor(0) shouldBe emptyList()
    }

    @Test
    fun addingAConnectionChainsFromThePreviousDestinationAndSearchPicksTheNextAirport() {
        open(TripEditorArgs(tripId = "lis-hnd-typo"))

        tag(TripsTestTags.EditorAddLeg).performScrollTo().performClick()
        settle()

        tag(TripsTestTags.editorLeg(1)).performScrollTo()
        tag(TripsTestTags.editorFrom(1)).assertTextContains("Tokyo", substring = true)

        tag(TripsTestTags.editorTo(1)).performScrollTo().performClick()
        settle()
        tag(TripsTestTags.EditorPlaceSearch).performTextInput("syd")
        settle()
        tag(TripsTestTags.placeResult("SYD")).performScrollTo().performClick()
        settle()
        tag(TripsTestTags.editorTo(1)).assertTextContains("Sydney", substring = true)
        // Picking the destination closes the sheet, back on the ticket.
        compose.onNodeWithTag(TripsTestTags.EditorPlaceSheet).assertDoesNotExist()

        tag(TripsTestTags.editorRemoveLeg(1)).performScrollTo().performClick()
        settle()
        compose.onNodeWithTag(TripsTestTags.editorLeg(1)).assertDoesNotExist()
    }

    @Test
    fun closingWithUnsavedChangesAsksBeforeDiscarding() {
        open(TripEditorArgs(tripId = DemoData.LhrSydId))
        tag(TripsTestTags.EditorTitle).performScrollTo().performTextInput("Work trip ")
        settle()

        tag(TripsTestTags.EditorClose).performClick()
        tag(TripsTestTags.DiscardKeep).performClick()
        tag(TripsTestTags.DiscardConfirm).assertDoesNotExist()
        done shouldBe emptyList()

        tag(TripsTestTags.EditorClose).performClick()
        tag(TripsTestTags.DiscardConfirm).performClick()
        done shouldContainExactly listOf(null)
        backs shouldBe 0
    }

    @Test
    fun closingAnUntouchedEditorJustGoesBack() {
        open(TripEditorArgs(tripId = DemoData.LhrSydId))
        tag(TripsTestTags.EditorClose).performClick()
        backs shouldBe 1
        tag(TripsTestTags.DiscardConfirm).assertDoesNotExist()
    }

    @Test
    fun newTripCannotBeSavedUntilComplete() {
        open()
        tag(TripsTestTags.EditorSave).assertIsNotEnabled().assertTextEquals("Build my plan")
        // The bar says why, right above the disabled button.
        tag(TripsTestTags.EditorSaveHint).assertTextEquals("Add airports and times for every flight.")
        tag(TripsTestTags.editorLeg(0)).assertExists()
    }

    @Test
    fun editingSaysSaveChanges() {
        open(TripEditorArgs(tripId = DemoData.SfoLhrId))
        tag(TripsTestTags.EditorSave).assertIsEnabled().assertTextEquals("Save changes")
        compose.onNodeWithTag(TripsTestTags.EditorSaveHint).assertDoesNotExist()
    }

    @Test
    fun aNewRouteIsTwoPicksInOneSheet() {
        open()
        tag(TripsTestTags.editorFrom(0)).performClick()
        settle()
        compose.onNodeWithText("Flying from").assertExists()
        tag(TripsTestTags.EditorPlaceSearch).performTextInput("lis")
        settle()
        tag(TripsTestTags.placeResult("LIS")).performScrollTo().performClick()
        settle()

        // From is set, To is empty: the same sheet asks for it next.
        compose.onNodeWithText("Flying to").assertExists()
        tag(TripsTestTags.EditorPlaceSearch).performTextInput("hnd")
        settle()
        tag(TripsTestTags.placeResult("HND")).performScrollTo().performClick()
        settle()

        compose.onNodeWithTag(TripsTestTags.EditorPlaceSheet).assertDoesNotExist()
        tag(TripsTestTags.editorFrom(0)).assertTextContains("Lisbon", substring = true)
        tag(TripsTestTags.editorTo(0)).assertTextContains("Tokyo", substring = true)
    }

    @Test
    fun theFlightHeadingOnlyShowsWithAConnection() {
        open()
        compose.onNodeWithText("Flight 1").assertDoesNotExist()
        tag(TripsTestTags.EditorAddLeg).performScrollTo().performClick()
        settle()
        compose.onNodeWithText("Flight 1").performScrollTo().assertExists()
        compose.onNodeWithText("Flight 2").performScrollTo().assertExists()
    }

    @Test
    fun anOvernightFlightSaysPlusOneDay() {
        open(TripEditorArgs(tripId = DemoData.SfoLhrId)) // SFO 19:30 → LHR 13:50 the next day.
        tag(TripsTestTags.editorDayOffset(0)).performScrollTo().assertContentDescriptionEquals("Lands the next day")
    }

    @Test
    fun turningOnTheReturnFlightAddsTheReturnTripOnSave() {
        open(TripEditorArgs(tripId = DemoData.SfoLhrId))
        val before = trips.current.size
        tag(TripsTestTags.EditorReturnSwitch).performScrollTo().assertIsOff().performClick()
        settle()
        tag(TripsTestTags.EditorReturnSwitch).assertIsOn()
        // A week after landing (Tue 16 Jun), at the outbound's time of day.
        tag(TripsTestTags.EditorReturnDate).performScrollTo().assertContentDescriptionContains("Jun 23", substring = true)
        compose.onNodeWithText("Added to your trips as a trip of its own when you build your plan.").assertExists()

        tag(TripsTestTags.EditorSave).performClick()
        settle()
        trips.current.size shouldBe before + 1
        val back = trips.current.single { it.legs.first().origin == DemoData.LHR && it.id != DemoData.LhrSydId }
        back.legs.last().destination shouldBe DemoData.SFO
    }

    @Test
    fun choosingWhereTheBodyClockStartsIsSaved() {
        // Home is Los Angeles; the trip leaves London.
        open(TripEditorArgs(tripId = DemoData.LhrSydId))
        compose.onNodeWithText("Body clock when you leave").performScrollTo().assertExists()
        tag(TripsTestTags.EditorBodyClockStartDeparture).performScrollTo().assertTextEquals("Departure city").assertIsOn()
        tag(TripsTestTags.EditorBodyClockStartHome).assertTextEquals("Home (Los Angeles)").assertIsOff().performClick()
        settle()
        tag(TripsTestTags.EditorBodyClockStartHome).assertIsOn()
        tag(TripsTestTags.EditorBodyClockStartDeparture).assertIsOff()
        compose.onNodeWithText("The plan starts on Los Angeles time", substring = true).assertExists()

        tag(TripsTestTags.EditorSave).performClick()
        settle()
        trips.current.single { it.id == DemoData.LhrSydId }.bodyClockStartZoneId shouldBe "America/Los_Angeles"
    }

    @Test
    fun aTripBackHomeStartsOnHomeTimeUnlessYouSayOtherwise() {
        // Home is Los Angeles: the return of San Francisco → London flies back home.
        open(TripEditorArgs(returnOfTripId = DemoData.SfoLhrId))
        tag(TripsTestTags.EditorBodyClockStartHome).performScrollTo().assertTextEquals("Home (Los Angeles)").assertIsOn()
        tag(TripsTestTags.EditorBodyClockStartDeparture).assertIsOff().performClick()
        settle()
        tag(TripsTestTags.EditorBodyClockStartDeparture).assertIsOn()
        tag(TripsTestTags.EditorBodyClockStartHome).assertIsOff()
        compose.onNodeWithText("The plan starts on London time", substring = true).assertExists()

        tag(TripsTestTags.EditorSave).performClick()
        settle()
        val saved = trips.current.single { it.id == done.single() }
        saved.bodyClockStartZoneId shouldBe null
    }

    @Test
    fun noBodyClockStartChoiceWhenTheTripLeavesFromHome() {
        open(TripEditorArgs(tripId = DemoData.SfoLhrId)) // San Francisco is on Los Angeles time.
        tag(TripsTestTags.EditorBodyClockStartHome).assertDoesNotExist()
        compose.onNodeWithText("Body clock when you leave").assertDoesNotExist()
    }

    @Test
    fun theGlobeOpensFullSize() {
        open(TripEditorArgs(tripId = DemoData.SfoLhrId))
        tag(TripsTestTags.EditorGlobe).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithTag(TripsTestTags.EditorGlobeSheet).assertExists()
        compose.onNodeWithTag(TripsTestTags.EditorGlobeFull).assertExists()
    }

    @Test
    fun aFlightOnTheGlobeGoesToItsTicket() {
        open(TripEditorArgs(tripId = DemoData.LhrSydId)) // LHR → SIN → SYD.
        tag(TripsTestTags.editorLeg(0)).assertIsSelected()
        val globe = tag(TripsTestTags.EditorGlobe).performScrollTo()
        globe.assertContentDescriptionContains("London to Sydney via Singapore", substring = true)
        val action = globe.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Go to flight 2: Singapore to Sydney" }
        compose.runOnIdle { action.action() }
        settle()
        tag(TripsTestTags.editorLeg(1)).assertIsSelected()
        tag(TripsTestTags.editorLeg(0)).assertIsNotSelected()
    }

    @Test
    fun touchingATicketMakesItTheOneTheGlobeShows() {
        open(TripEditorArgs(tripId = DemoData.LhrSydId))
        tag(TripsTestTags.editorDepartureDate(1)).performScrollTo()
        tag(TripsTestTags.editorLeg(1)).performTouchInput { down(center); up() }
        settle()
        tag(TripsTestTags.editorLeg(1)).assertIsSelected()
    }
}
