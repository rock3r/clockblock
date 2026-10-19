package dev.sebastiano.clockblocker.opus.shell

import androidx.activity.ComponentActivity
import dev.sebastiano.clockblocker.opus.TestApplication
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.navigation.DeepLinkParser
import dev.sebastiano.clockblocker.opus.navigation.DeepLinkTarget
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Shell behaviour on a compact phone window: gating, navigation suite, deep links and back. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], application = TestApplication::class, qualifiers = "w411dp-h891dp")
class ShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val deepLinks = Channel<DeepLinkTarget>(Channel.BUFFERED)
    private val deepLinkFlow = deepLinks.receiveAsFlow()
    private var uiState by mutableStateOf<ShellUiState>(ShellUiState.Loading)

    private fun launch(hasProfile: Boolean, hasTrips: Boolean) {
        uiState = ShellUiState.Ready(hasProfile, hasTrips, TestSettings)
        compose.setContent {
            ClockblockAppRoot(uiState = uiState, deepLinks = deepLinkFlow, destinations = FakeDestinations)
        }
    }

    private fun tag(tag: String) = compose.onNodeWithTag(tag)

    private fun deepLink(uri: String) {
        deepLinks.trySend(requireNotNull(DeepLinkParser.parse(uri)))
        compose.waitForIdle()
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `nothing is drawn while loading`() {
        compose.setContent { ClockblockAppRoot(uiState = ShellUiState.Loading, destinations = FakeDestinations) }

        tag("screen_onboarding").assertDoesNotExist()
        tag(ShellTestTags.NavNow).assertDoesNotExist()
    }

    @Test
    fun `first run shows onboarding without the navigation suite`() {
        launch(hasProfile = false, hasTrips = false)

        tag("screen_onboarding").assertIsDisplayed()
        tag(ShellTestTags.NavNow).assertIsNotDisplayed()
        tag(ShellTestTags.NavTrips).assertIsNotDisplayed()
        tag(ShellTestTags.NavSettings).assertIsNotDisplayed()
    }

    @Test
    fun `finishing onboarding without trips lands on Trips with the suite visible`() {
        launch(hasProfile = false, hasTrips = false)

        tag("onboarding_finish").performClick()
        compose.waitForIdle()

        tag(ShellTestTags.TakeoffSweep).assertDoesNotExist()
        tag("screen_trips").assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsDisplayed().assertIsSelected()
        tag(ShellTestTags.NavNow).assertIsDisplayed().assertIsNotSelected()
    }

    @Test
    fun `a finish that took off hands over under the sweep, which reveals the app and goes`() {
        launch(hasProfile = false, hasTrips = false)
        compose.mainClock.autoAdvance = false

        tag("onboarding_takeoff").performClick()
        compose.mainClock.advanceTimeBy(50)

        // The app is already there underneath (the suite in place), under the sweep's cover.
        tag(ShellTestTags.TakeoffSweep).assertExists()
        tag("screen_trips").assertExists()
        tag(ShellTestTags.NavTrips).assertExists()
        compose.mainClock.advanceTimeBy(3_000)
        tag(ShellTestTags.TakeoffSweep).assertDoesNotExist()
        tag("screen_trips").assertIsDisplayed()
    }

    @Test
    fun `finishing onboarding with trips lands on Now`() {
        launch(hasProfile = false, hasTrips = true)

        tag("onboarding_finish").performClick()
        compose.waitForIdle()

        tag("screen_plan_now").assertIsDisplayed()
        tag(ShellTestTags.NavNow).assertIsSelected()
    }

    @Test
    fun `onboarded users with trips start on Now`() {
        launch(hasProfile = true, hasTrips = true)

        tag("screen_plan_now").assertIsDisplayed()
        tag(ShellTestTags.NavNow).assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `onboarded users without trips start on Trips`() {
        launch(hasProfile = true, hasTrips = false)

        tag("screen_trips").assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsSelected()
    }

    @Test
    fun `losing the profile returns to onboarding`() {
        launch(hasProfile = true, hasTrips = true)
        tag("screen_plan_now").assertIsDisplayed()

        uiState = ShellUiState.Ready(hasProfile = false, hasTrips = false, settings = TestSettings)
        compose.waitForIdle()

        tag("screen_onboarding").assertIsDisplayed()
        tag(ShellTestTags.NavNow).assertIsNotDisplayed()
    }

    @Test
    fun `tapping suite items switches destinations and keeps each back stack`() {
        launch(hasProfile = true, hasTrips = true)

        tag(ShellTestTags.NavSettings).performClick()
        tag("settings_about").performClick()
        compose.waitForIdle()
        tag("screen_about").assertIsDisplayed()

        tag(ShellTestTags.NavTrips).performClick()
        compose.waitForIdle()
        tag("screen_trips").assertIsDisplayed()

        tag(ShellTestTags.NavSettings).performClick()
        compose.waitForIdle()
        tag("screen_about").assertIsDisplayed()

        // Re-selecting the active destination pops it to its root.
        tag(ShellTestTags.NavSettings).performClick()
        compose.waitForIdle()
        tag("screen_settings").assertIsDisplayed()
    }

    @Test
    fun `opening a trip plan from Trips hides the suite and has an up arrow that returns to the list`() {
        launch(hasProfile = true, hasTrips = false)

        tag("trips_open_a").performClick()
        compose.waitForIdle()
        tag("screen_trips").assertDoesNotExist()
        tag("screen_plan_a").assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsNotDisplayed()

        tag("plan_up").performClick()
        compose.waitForIdle()
        tag("screen_trips").assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `Now and Trips empty-state actions switch between the two tabs`() {
        launch(hasProfile = true, hasTrips = true)

        tag("plan_open_trips").performClick()
        compose.waitForIdle()
        tag("screen_trips").assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsSelected()

        tag("trips_open_now").performClick()
        compose.waitForIdle()
        tag("screen_plan_now").assertIsDisplayed()
        tag(ShellTestTags.NavNow).assertIsSelected()
    }

    @Test
    fun `trip editor hides the suite and a new trip opens its plan`() {
        launch(hasProfile = true, hasTrips = false)

        tag("trips_new").performClick()
        compose.waitForIdle()
        tag("screen_editor").assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsNotDisplayed()

        tag("editor_save").performClick()
        compose.waitForIdle()
        tag("screen_plan_${FakeDestinations.NewTripId}").assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsNotDisplayed()

        tag("plan_up").performClick()
        compose.waitForIdle()
        tag("screen_trips").assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsDisplayed()
    }

    @Test
    fun `back exits through home`() {
        launch(hasProfile = true, hasTrips = true)

        tag(ShellTestTags.NavSettings).performClick()
        tag("settings_about").performClick()
        compose.waitForIdle()
        tag("screen_about").assertIsDisplayed()

        pressBack()
        tag("screen_settings").assertIsDisplayed()

        pressBack()
        tag("screen_plan_now").assertIsDisplayed()
        tag(ShellTestTags.NavNow).assertIsSelected()
    }

    @Test
    fun `replaying onboarding from settings returns to settings`() {
        launch(hasProfile = true, hasTrips = true)

        tag(ShellTestTags.NavSettings).performClick()
        tag("settings_replay").performClick()
        compose.waitForIdle()
        tag("screen_onboarding").assertIsDisplayed()
        tag(ShellTestTags.NavSettings).assertIsNotDisplayed()

        tag("onboarding_finish").performClick()
        compose.waitForIdle()
        tag("screen_settings").assertIsDisplayed()
    }

    @Test
    fun `plan deep link opens the plan inside Trips`() {
        launch(hasProfile = true, hasTrips = true)

        deepLink("clockblock://plan/abc")

        tag("screen_plan_abc").assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsNotDisplayed()

        // The synthetic back stack goes to the trips list, then home.
        pressBack()
        tag("screen_trips").assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsSelected()
        pressBack()
        tag("screen_plan_now").assertIsDisplayed()
    }

    @Test
    fun `new trip deep link opens the editor over Trips`() {
        launch(hasProfile = true, hasTrips = true)

        deepLink("clockblock://trips/new")

        tag("screen_editor").assertIsDisplayed()
        pressBack()
        tag("screen_trips").assertIsDisplayed()
    }

    @Test
    fun `current plan deep link shows Now`() {
        launch(hasProfile = true, hasTrips = true)
        tag(ShellTestTags.NavSettings).performClick()
        compose.waitForIdle()

        deepLink("clockblock://plan/current")

        tag("screen_plan_now").assertIsDisplayed()
        tag(ShellTestTags.NavNow).assertIsSelected()
    }

    @Test
    fun `deep links during first-run onboarding wait until it completes`() {
        launch(hasProfile = false, hasTrips = false)

        deepLink("clockblock://plan/abc")
        tag("screen_onboarding").assertIsDisplayed()

        tag("onboarding_finish").performClick()
        compose.waitForIdle()
        tag("screen_plan_abc").assertIsDisplayed()
    }
}

/** Deterministic theme for shell tests: no dynamic colour, reduced motion. */
internal val TestSettings = AppSettings(dynamicColor = false, reduceMotion = true)
