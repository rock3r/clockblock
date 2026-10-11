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

import androidx.compose.material3.Surface
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.RouteGlobeDefaults
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w411dp-h891dp-xhdpi")
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
        onOpenNow = {},
    )

    @Test
    fun populated() = snap("trips_list_light") { TripsContent(TripsSamples.state(), callbacks, now = TripsSamples.Now) }

    @Test
    fun populatedDark() = snap("trips_list_dark", darkTheme = true) { TripsContent(TripsSamples.state(), callbacks, now = TripsSamples.Now) }

    @Test
    fun populatedFontScale() = snap("trips_list_fontscale_1_5", fontScale = 1.5f) {
        TripsContent(TripsSamples.state(), callbacks, now = TripsSamples.Now)
    }

    @Test
    fun empty() = snap("trips_list_empty") { TripsContent(TripsSamples.state(trips = emptyList()), callbacks, now = TripsSamples.Now) }

    @Test
    fun emptyDark() = snap("trips_list_empty_dark", darkTheme = true) {
        TripsContent(TripsSamples.state(trips = emptyList()), callbacks, now = TripsSamples.Now)
    }

    @Test
    fun emptyFontScale() = snap("trips_list_empty_fontscale_1_5", fontScale = 1.5f) {
        TripsContent(TripsSamples.state(trips = emptyList()), callbacks, now = TripsSamples.Now)
    }

    @Test
    fun upcomingEmptyWithNowTrip() = snap("trips_list_upcoming_empty_now") {
        TripsContent(
            TripsSamples.state(trips = listOf(DemoData.sfoToLhr(), TripsSamples.lisbonTokyo)),
            callbacks,
            now = TripsSamples.Now,
        )
    }

    @Test
    fun upcomingEmptyPastOnly() {
        val afterAll = Instant.parse("2026-08-01T12:00:00Z")
        snap("trips_list_past_only") {
            TripsContent(TripsSamples.state(now = afterAll), callbacks, now = afterAll)
        }
    }

    @Test
    fun pastGlobeSheet() {
        RouteGlobeDefaults.preload(ApplicationProvider.getApplicationContext())
        val afterAll = Instant.parse("2026-08-01T12:00:00Z")
        val stats = PastTripsStats.of(TripsSamples.state(now = afterAll).past)
        snap("trips_past_globe_sheet") {
            Surface {
                PastTripsGlobeSheetContent(
                    stats = stats,
                    now = afterAll,
                    onOpenTrip = {},
                )
            }
        }
    }

    @Test
    fun twoColumns() = snap("trips_list_expanded", qualifiers = "w1000dp-h720dp") {
        TripsContent(TripsSamples.state(), callbacks, selectedTripId = DemoData.LhrSydId, now = TripsSamples.Now)
    }

    @Test
    fun twoColumnsDark() = snap("trips_list_expanded_dark", darkTheme = true, qualifiers = "w1000dp-h720dp") {
        TripsContent(TripsSamples.state(), callbacks, selectedTripId = DemoData.LhrSydId, now = TripsSamples.Now)
    }

    /** Landscape phone pane (#15): single-row sky bar, add menu in the app bar, nothing floating over the cards. */
    @Test
    fun shortWindow() = snap("trips_list_short_window", qualifiers = "w540dp-h411dp") {
        TripsContent(TripsSamples.state(), callbacks, now = TripsSamples.Now)
    }

    @Test
    fun shortWindowMenu() {
        setContent(darkTheme = true, qualifiers = "w540dp-h411dp") { TripsContent(TripsSamples.state(), callbacks, now = TripsSamples.Now) }
        compose.onNodeWithTag(TripsTestTags.Fab).performClick()
        capture("trips_list_short_window_menu_dark")
    }

    /** At night the sky darkens and the ink turns light; the body clock time is in the subtitle. */
    @Test
    fun nightSky() = snap("trips_list_night_sky") {
        val nightNow = TripsSamples.Now.plusSeconds(11 * 3600)
        TripsContent(TripsSamples.state(now = nightNow), callbacks, now = nightNow)
    }

    @Test
    fun fabMenuOpen() {
        setContent { TripsContent(TripsSamples.state(), callbacks, now = TripsSamples.Now) }
        compose.onNodeWithTag(TripsTestTags.Fab).performClick()
        capture("trips_list_fab_menu")
    }

    @Test
    fun cardMenuOpen() {
        setContent { TripsContent(TripsSamples.state(), callbacks, now = TripsSamples.Now) }
        compose.onNodeWithTag(TripsTestTags.tripMenu(DemoData.LhrSydId)).performClick()
        capture("trips_list_card_menu")
    }
}
