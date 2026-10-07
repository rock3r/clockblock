package dev.sebastiano.clockblocker.opus.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sebastiano.clockblocker.opus.shell.ShellTestTags
import org.junit.Test
import org.junit.runner.RunWith

/** The app starts past the splash screen into onboarding or a home destination, and the suite navigates. */
@RunWith(AndroidJUnit4::class)
class SmokeTest : ClockblockE2eTest() {

    @Test
    fun launchShowsOnboardingOrHome() {
        launch()

        awaitAnyTag("route_onboarding", "route_now", "route_trips")
    }

    @Test
    fun onboardedLaunchShowsNavigationSuite() {
        seedOnboarded()
        launch()

        awaitAnyTag("route_now", "route_trips")
        awaitTag(ShellTestTags.NavNow).assertIsDisplayed()
        awaitTag(ShellTestTags.NavTrips).assertIsDisplayed()

        awaitTag(ShellTestTags.NavSettings).performClick()
        awaitTag("route_settings").assertIsDisplayed()

        // Back from another destination's root exits through home rather than leaving the app.
        device.pressBack()
        awaitAnyTag("route_now", "route_trips")
    }
}
