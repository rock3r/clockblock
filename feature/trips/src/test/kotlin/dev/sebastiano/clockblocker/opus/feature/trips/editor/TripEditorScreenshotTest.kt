package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
@Config(sdk = [37], qualifiers = "w411dp-h1500dp-xhdpi")
class TripEditorScreenshotTest : TripsScreenshotTest() {

    @get:Rule
    val main = MainDispatcherRule()

    private val clock = MutableClock(DemoData.Now)
    private val trips = FakeTripRepository(DemoData.trips() + TripsSamples.lisbonTokyoWrongDate() + TripsSamples.newYorkParisFromHome())

    private fun viewModel(args: TripEditorArgs = TripEditorArgs()) = TripEditorViewModel(
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

    private fun editor(vm: TripEditorViewModel, name: String, darkTheme: Boolean = false, fontScale: Float? = null, qualifiers: String? = null) {
        setContent(darkTheme, fontScale, qualifiers) { Editor(vm) }
        // The plan preview is debounced: let it settle so complete trips show it.
        main.dispatcher.scheduler.advanceUntilIdle()
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

    /** "Body clock when you leave" set to Home: the trip leaves New York, home is Los Angeles (issue #9). */
    @Test
    fun bodyClockStartHome() =
        editor(viewModel(TripEditorArgs(tripId = "jfk-cdg-from-home")), "editor_body_clock_home", qualifiers = "w411dp-h1700dp")

    @Test
    fun bodyClockStartHomeDark() =
        editor(viewModel(TripEditorArgs(tripId = "jfk-cdg-from-home")), "editor_body_clock_home_dark", darkTheme = true, qualifiers = "w411dp-h1700dp")

    @Test
    fun bodyClockStartHomeFontScale() = editor(
        viewModel(TripEditorArgs(tripId = "jfk-cdg-from-home")),
        "editor_body_clock_home_fontscale_1_5",
        fontScale = 1.5f,
        qualifiers = "w411dp-h2600dp",
    )

    @Test
    fun returnTrip() = editor(viewModel(TripEditorArgs(returnOfTripId = DemoData.SfoLhrId)), "editor_return")

    @Test
    fun expanded() = editor(viewModel(TripEditorArgs(tripId = DemoData.LhrSydId)), "editor_expanded", qualifiers = "w1000dp-h1400dp")

    /** A new LIS → HND trip, filled in: the ticket with "+1 day", the flight time, and the live Your shift card. */
    @Test
    fun filled() = editor(viewModel().apply { fillLisbonTokyo() }, "editor_filled", qualifiers = "w411dp-h1600dp")

    @Test
    fun filledDark() = editor(viewModel().apply { fillLisbonTokyo() }, "editor_filled_dark", darkTheme = true, qualifiers = "w411dp-h1600dp")

    @Test
    fun filledFontScale() =
        editor(viewModel().apply { fillLisbonTokyo() }, "editor_filled_fontscale_1_3", fontScale = 1.3f, qualifiers = "w411dp-h2000dp")

    /** The inline Return flight switch on: the return date row, and Save will add Tokyo → Lisbon. */
    @Test
    fun returnFlight() = editor(
        viewModel().apply {
            fillLisbonTokyo()
            onReturnFlightChange(true)
        },
        "editor_return_flight",
        qualifiers = "w411dp-h1700dp",
    )

    /** A return flight that leaves before the trip lands: the chips turn red and the main action says why. */
    @Test
    fun returnFlightBeforeLanding() = editor(
        viewModel().apply {
            fillLisbonTokyo()
            onReturnFlightChange(true)
            onReturnDateChange(LocalDate.of(2026, 10, 7))
        },
        "editor_return_flight_error",
        qualifiers = "w411dp-h1700dp",
    )

    @Test
    fun searching() {
        val vm = viewModel()
        vm.openPlaceSearch(PlaceFieldRef(0, LegEnd.Origin))
        vm.onPlaceQueryChange(PlaceFieldRef(0, LegEnd.Origin), "lon")
        main.dispatcher.scheduler.advanceUntilIdle()
        searchPanel(vm, "editor_search")
    }

    @Test
    fun popularAirports() {
        val vm = viewModel()
        vm.onPlaceSelected(PlaceFieldRef(0, LegEnd.Origin), DemoData.LIS)
        vm.openPlaceSearch(PlaceFieldRef(0, LegEnd.Destination))
        main.dispatcher.scheduler.advanceUntilIdle()
        searchPanel(vm, "editor_popular")
    }

    /** The airport sheet's content (the sheet itself is a separate window, which a root capture doesn't include). */
    private fun searchPanel(vm: TripEditorViewModel, name: String) {
        setContent(qualifiers = "w411dp-h891dp") {
            val state by vm.state.collectAsStateWithLifecycle()
            state.search?.let { search ->
                PlaceSearchPanel(
                    search = search,
                    legCount = state.legs.size,
                    now = clock.instant(),
                    onQueryChange = {},
                    onPick = {},
                    onPickTop = {},
                    autoFocus = false,
                )
            }
        }
        capture(name)
    }

    /** LIS Wed 7 Oct 09:00 → HND, the arrival estimated from the distance (lands the next day). */
    private fun TripEditorViewModel.fillLisbonTokyo() {
        onPlaceSelected(PlaceFieldRef(0, LegEnd.Origin), DemoData.LIS)
        onPlaceSelected(PlaceFieldRef(0, LegEnd.Destination), DemoData.HND)
        onDepartureDateChange(0, LocalDate.of(2026, 10, 7))
        onDepartureTimeChange(0, LocalTime.of(9, 0))
    }
}
