package dev.sebastiano.clockblocker.opus.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.sebastiano.clockblocker.opus.TestApplication
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import dev.sebastiano.clockblocker.opus.core.testing.captureRoboImageInvalidated
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shell chrome at each window size class, with fake destinations (tinted panes) and the real detail
 * placeholder: short navigation bar and a full-screen plan (compact, landscape phone), collapsed rail beside a
 * pushed plan (medium), and rail + Trips | plan list-detail with its drag handle (expanded, extra large).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], application = TestApplication::class)
class ShellScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun launch(hasProfile: Boolean = true, hasTrips: Boolean = false, dark: Boolean = false, motion: Boolean = false) {
        val settings = TestSettings.copy(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light, reduceMotion = !motion)
        compose.setContent {
            ClockblockAppRoot(
                uiState = ShellUiState.Ready(hasProfile = hasProfile, hasTrips = hasTrips, settings = settings),
                destinations = FakeDestinations,
            )
        }
    }

    private fun openTripA() {
        compose.onNodeWithTag("trips_open_a").performClick()
        compose.waitForIdle()
    }

    private fun snap(name: String) {
        compose.captureRoboImageInvalidated("src/test/screenshots/shell_$name.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun compactNow() {
        launch(hasTrips = true)
        snap("compact_now")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun compactPlan() {
        launch()
        openTripA()
        snap("compact_plan")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun compactOnboarding() {
        launch(hasProfile = false)
        snap("compact_onboarding")
    }

    /** Onboarding's take-off sweep revealing the app behind the plane's wings: a third and two thirds of the way. */
    private fun sweepFrames(name: String) {
        launch(hasProfile = false, motion = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("onboarding_takeoff").performClick()
        compose.mainClock.advanceTimeBy(50)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag(ShellTestTags.TakeoffSweep).assertExists()
        snap("${name}_sweep_1")
        compose.mainClock.advanceTimeBy(300)
        snap("${name}_sweep_2")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun compactSweep() = sweepFrames("compact")

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun expandedSweep() = sweepFrames("expanded")

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun compactTripsDark() {
        launch(dark = true)
        snap("compact_trips_dark")
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun mediumPlan() {
        launch()
        openTripA()
        snap("medium_plan")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp")
    fun landscapePhonePlan() {
        launch()
        openTripA()
        snap("landscape_plan")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun expandedTrips() {
        launch(hasTrips = true)
        compose.onNodeWithTag(ShellTestTags.NavTrips).performClick()
        compose.waitForIdle()
        snap("expanded_trips")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-night")
    fun expandedTripsNoTripsDark() {
        launch(dark = true)
        snap("expanded_trips_no_trips_dark")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun expandedPlan() {
        launch()
        openTripA()
        snap("expanded_plan")
    }

    @Test
    @Config(qualifiers = "w1600dp-h1000dp")
    fun extraLargePlan() {
        launch()
        openTripA()
        snap("extra_large_plan")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun expandedEditor() {
        launch()
        compose.onNodeWithTag("trips_new").performClick()
        compose.waitForIdle()
        snap("expanded_editor")
    }
}
