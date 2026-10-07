package dev.sebastiano.clockblocker.opus.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.clockblocker.opus.TestApplication
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shell chrome at each window size class, with fake destinations: short navigation bar (compact), collapsed
 * navigation rail (medium, single pane) and rail + list-detail (expanded).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], application = TestApplication::class)
class ShellScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun launch(hasProfile: Boolean = true, hasTrips: Boolean = false, dark: Boolean = false) {
        val settings = TestSettings.copy(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light)
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
        compose.onRoot().captureRoboImage("src/test/screenshots/shell_$name.png")
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
    @Config(qualifiers = "w1280dp-h800dp")
    fun expandedTrips() {
        launch()
        snap("expanded_trips")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun expandedPlan() {
        launch()
        openTripA()
        snap("expanded_plan")
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
