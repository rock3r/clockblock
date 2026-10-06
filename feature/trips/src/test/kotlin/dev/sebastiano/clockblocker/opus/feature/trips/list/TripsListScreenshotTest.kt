package dev.sebastiano.clockblocker.opus.feature.trips.list

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.feature.trips.TripsSamples
import dev.sebastiano.clockblocker.opus.feature.trips.TripsScreenshotTest
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TripsListScreenshotTest : TripsScreenshotTest() {

    private val callbacks = TripsCallbacks(
        onOpenTrip = {},
        onNewTrip = {},
        onOpenSettings = {},
        onEditTrip = {},
        onCreateReturnTrip = {},
        onTryDemo = {},
        onDelete = {},
        onDuplicate = {},
    )

    @Test
    fun populated() = snap("trips_list_light") { TripsContent(TripsSamples.state(), callbacks) }

    @Test
    fun populatedDark() = snap("trips_list_dark", darkTheme = true) { TripsContent(TripsSamples.state(), callbacks) }

    @Test
    fun populatedFontScale() = snap("trips_list_fontscale_1_5", fontScale = 1.5f) { TripsContent(TripsSamples.state(), callbacks) }

    @Test
    fun empty() = snap("trips_list_empty") { TripsContent(TripsSamples.state(trips = emptyList()), callbacks) }

    @Test
    fun emptyDark() = snap("trips_list_empty_dark", darkTheme = true) { TripsContent(TripsSamples.state(trips = emptyList()), callbacks) }

    @Test
    fun emptyFontScale() = snap("trips_list_empty_fontscale_1_5", fontScale = 1.5f) {
        TripsContent(TripsSamples.state(trips = emptyList()), callbacks)
    }

    @Test
    fun twoColumns() = snap("trips_list_expanded", qualifiers = "w1000dp-h720dp") {
        TripsContent(TripsSamples.state(), callbacks, selectedTripId = DemoData.SfoLhrId)
    }

    @Test
    fun twoColumnsDark() = snap("trips_list_expanded_dark", darkTheme = true, qualifiers = "w1000dp-h720dp") {
        TripsContent(TripsSamples.state(), callbacks, selectedTripId = DemoData.LhrSydId)
    }

    /** Landscape phone pane (#15): single-row sky bar, add menu in the app bar, nothing floating over the cards. */
    @Test
    fun shortWindow() = snap("trips_list_short_window", qualifiers = "w540dp-h411dp") { TripsContent(TripsSamples.state(), callbacks) }

    @Test
    fun shortWindowMenu() {
        setContent(darkTheme = true, qualifiers = "w540dp-h411dp") { TripsContent(TripsSamples.state(), callbacks) }
        compose.onNodeWithTag(TripsTestTags.Fab).performClick()
        capture("trips_list_short_window_menu_dark")
    }

    /** At night the sky darkens and the ink turns light; the body clock time is in the subtitle. */
    @Test
    fun nightSky() = snap("trips_list_night_sky") { TripsContent(TripsSamples.state(now = TripsSamples.Now.plusSeconds(11 * 3600)), callbacks) }

    @Test
    fun fabMenuOpen() {
        setContent { TripsContent(TripsSamples.state(), callbacks) }
        compose.onNodeWithTag(TripsTestTags.Fab).performClick()
        capture("trips_list_fab_menu")
    }

    @Test
    fun cardMenuOpen() {
        setContent { TripsContent(TripsSamples.state(), callbacks) }
        compose.onNodeWithTag(TripsTestTags.tripMenu(DemoData.SfoLhrId)).performClick()
        capture("trips_list_card_menu")
    }
}
