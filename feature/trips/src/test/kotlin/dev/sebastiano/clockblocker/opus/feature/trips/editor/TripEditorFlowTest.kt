package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
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
@Config(sdk = [36], qualifiers = "w411dp-h2400dp-xhdpi")
class TripEditorFlowTest : TripsScreenshotTest() {

    @get:Rule
    val main = MainDispatcherRule()

    private val clock = MutableClock(DemoData.Now)
    private val trips = FakeTripRepository(DemoData.trips() + TripsSamples.lisbonTokyoWrongDate())
    private val done = mutableListOf<String?>()
    private var backs = 0

    private fun open(args: TripEditorArgs = TripEditorArgs()) {
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
    fun addingAConnectionChainsFromThePreviousDestinationAndSearchPicksTheNextAirport() {
        open(TripEditorArgs(tripId = "lis-hnd-typo"))

        tag(TripsTestTags.EditorAddLeg).performScrollTo().performClick()
        settle()

        tag(TripsTestTags.editorLeg(1)).performScrollTo()
        tag(TripsTestTags.editorFrom(1)).assertTextContains("Tokyo", substring = true)

        tag(TripsTestTags.editorTo(1)).performScrollTo().performTextInput("syd")
        settle()
        tag(TripsTestTags.placeResult("SYD")).performScrollTo().performClick()
        settle()
        tag(TripsTestTags.editorTo(1)).assertTextContains("Sydney", substring = true)

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
        tag(TripsTestTags.EditorSave).assertIsNotEnabled()
        tag(TripsTestTags.editorLeg(0)).assertExists()
    }
}
