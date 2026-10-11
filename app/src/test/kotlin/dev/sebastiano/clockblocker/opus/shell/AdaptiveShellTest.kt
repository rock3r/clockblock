package dev.sebastiano.clockblocker.opus.shell

import androidx.activity.ComponentActivity
import dev.sebastiano.clockblocker.opus.TestApplication
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Adaptive shell behaviour across window sizes, and back stack restoration. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], application = TestApplication::class)
class AdaptiveShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun launch(hasTrips: Boolean = false) {
        compose.setContent {
            ClockblockAppRoot(
                uiState = ShellUiState.Ready(hasProfile = true, hasTrips = hasTrips, settings = TestSettings),
                destinations = FakeDestinations,
            )
        }
    }

    private fun tag(tag: String) = compose.onNodeWithTag(tag)

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `expanded window shows trips with the navigation rail`() {
        launch()

        tag("screen_trips").assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsDisplayed().assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `expanded window opens a trip plan full screen without the rail and with an up arrow`() {
        launch()

        tag("trips_open_a").performClick()
        compose.waitForIdle()

        tag("screen_plan_a").assertIsDisplayed()
        tag("screen_trips").assertDoesNotExist()
        tag("plan_up").assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsNotDisplayed()

        tag("plan_up").performClick()
        compose.waitForIdle()
        tag("screen_trips").assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsDisplayed().assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `expanded window shows the editor full screen`() {
        launch()

        tag("trips_new").performClick()
        compose.waitForIdle()

        tag("screen_editor").assertIsDisplayed()
        tag("screen_trips").assertDoesNotExist()
        tag(ShellTestTags.NavTrips).assertIsNotDisplayed()
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun `medium window opens a trip plan full screen without the rail and with an up arrow`() {
        launch()

        tag("trips_open_a").performClick()
        compose.waitForIdle()

        tag("screen_plan_a").assertIsDisplayed()
        tag("plan_up").assertIsDisplayed()
        tag("screen_trips").assertDoesNotExist()
        tag(ShellTestTags.NavTrips).assertIsNotDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `back stacks and entry state survive recreation`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ClockblockAppRoot(
                uiState = ShellUiState.Ready(hasProfile = true, hasTrips = true, settings = TestSettings),
                destinations = FakeDestinations,
            )
        }

        tag("plan_counter_now").performClick().performClick()
        tag(ShellTestTags.NavTrips).performClick()
        tag("trips_open_a").performClick()
        compose.waitForIdle()
        tag("plan_counter_a").performClick().performClick()

        restoration.emulateSavedInstanceStateRestore()

        tag("screen_plan_a").assertIsDisplayed()
        tag("plan_counter_a").assertTextEquals("count=2")
        tag("plan_up").performClick()
        compose.waitForIdle()
        tag("screen_trips").assertIsDisplayed()
        tag(ShellTestTags.NavNow).performClick()
        compose.waitForIdle()
        tag("plan_counter_now").assertTextEquals("count=2")
    }
}
