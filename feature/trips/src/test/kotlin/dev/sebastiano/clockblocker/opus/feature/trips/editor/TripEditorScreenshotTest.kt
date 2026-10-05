package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.data.trip.ReturnTripFactory
import dev.sebastiano.clockblocker.opus.core.data.trip.TripTitleSuggester
import dev.sebastiano.clockblocker.opus.core.data.trip.TripValidator
import dev.sebastiano.clockblocker.opus.core.testing.FakePlaceSearch
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import dev.sebastiano.clockblocker.opus.core.testing.MainDispatcherRule
import dev.sebastiano.clockblocker.opus.core.testing.MutableClock
import dev.sebastiano.clockblocker.opus.feature.trips.TripsSamples
import dev.sebastiano.clockblocker.opus.feature.trips.TripsScreenshotTest
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1500dp-xhdpi")
class TripEditorScreenshotTest : TripsScreenshotTest() {

    @get:Rule
    val main = MainDispatcherRule()

    private val clock = MutableClock(DemoData.Now)
    private val trips = FakeTripRepository(DemoData.trips() + TripsSamples.lisbonTokyoWrongDate())

    private fun viewModel(args: TripEditorArgs = TripEditorArgs()) = TripEditorViewModel(
        args = args,
        trips = trips,
        places = FakePlaceSearch(),
        validator = TripValidator(),
        titles = TripTitleSuggester(),
        returnTrips = ReturnTripFactory(clock, TripTitleSuggester()),
        clock = clock,
    )

    private fun editor(vm: TripEditorViewModel, name: String, darkTheme: Boolean = false, fontScale: Float? = null, qualifiers: String? = null) {
        setContent(darkTheme, fontScale, qualifiers) { Editor(vm) }
        capture(name)
    }

    @androidx.compose.runtime.Composable
    private fun Editor(vm: TripEditorViewModel) {
        val state by vm.state.collectAsStateWithLifecycle()
        TripEditorContent(state = state, actions = vm, onClose = {}, onDiscard = {}, now = clock.instant())
    }

    @Test
    fun newTrip() = editor(viewModel(), "editor_new")

    @Test
    fun newTripPhone() = editor(viewModel(), "editor_new_phone", qualifiers = "w411dp-h891dp")

    @Test
    fun newTripDark() = editor(viewModel(), "editor_new_dark", darkTheme = true)

    @Test
    fun withErrors() = editor(viewModel(TripEditorArgs(tripId = "lis-hnd-typo")), "editor_errors")

    @Test
    fun withErrorsFontScale() =
        editor(viewModel(TripEditorArgs(tripId = "lis-hnd-typo")), "editor_errors_fontscale_1_5", fontScale = 1.5f, qualifiers = "w411dp-h2400dp")

    @Test
    fun multiLegWithDelay() {
        clock.set(Instant.parse("2026-06-25T12:00:00Z")) // The day before LHR → SIN → SYD departs.
        val vm = viewModel(TripEditorArgs(tripId = DemoData.LhrSydId))
        vm.onReturnDateChange(LocalDate.of(2026, 7, 10))
        vm.onReturnTimeChange(LocalTime.of(16, 45))
        editor(vm, "editor_multi_leg", qualifiers = "w411dp-h2200dp")
    }

    @Test
    fun multiLegDark() {
        val vm = viewModel(TripEditorArgs(tripId = DemoData.LhrSydId))
        editor(vm, "editor_multi_leg_dark", darkTheme = true, qualifiers = "w411dp-h2000dp")
    }

    @Test
    fun returnTrip() = editor(viewModel(TripEditorArgs(returnOfTripId = DemoData.SfoLhrId)), "editor_return")

    @Test
    fun expanded() = editor(viewModel(TripEditorArgs(tripId = DemoData.LhrSydId)), "editor_expanded", qualifiers = "w1000dp-h1400dp")

    @Test
    fun searching() {
        val vm = viewModel()
        setContent(qualifiers = "w411dp-h891dp") { Editor(vm) }
        compose.onNodeWithTag(TripsTestTags.editorFrom(0)).performTextInput("lon")
        main.dispatcher.scheduler.advanceUntilIdle()
        capture("editor_search")
    }
}
