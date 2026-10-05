package dev.sebastiano.clockblocker.opus.shell

import androidx.activity.ComponentActivity
import dev.sebastiano.clockblocker.opus.TestApplication
import androidx.compose.ui.test.assertIsDisplayed
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

/** List-detail vs single pane across window sizes, and back stack restoration. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestApplication::class)
class AdaptiveShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun launch(hasTrips: Boolean = false) {
        compose.setContent {
            OpusAppRoot(
                uiState = ShellUiState.Ready(hasProfile = true, hasTrips = hasTrips, settings = TestSettings),
                destinations = FakeDestinations,
            )
        }
    }

    private fun tag(tag: String) = compose.onNodeWithTag(tag)

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `expanded window shows trips next to a placeholder`() {
        launch()

        tag("screen_trips").assertIsDisplayed()
        tag("screen_placeholder").assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsDisplayed().assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `expanded window opens plans in the detail pane without an up arrow`() {
        launch()

        tag("trips_open_a").performClick()
        compose.waitForIdle()

        tag("screen_trips").assertIsDisplayed()
        tag("screen_plan_a").assertIsDisplayed()
        tag("plan_up").assertDoesNotExist()
        tag("trips_selected").assertTextEquals("selected=a")

        // Opening another trip replaces the detail rather than stacking it.
        tag("trips_open_b").performClick()
        compose.waitForIdle()
        tag("screen_plan_b").assertIsDisplayed()
        tag("screen_plan_a").assertDoesNotExist()
        tag("trips_selected").assertTextEquals("selected=b")
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `expanded window shows the editor full screen`() {
        launch()

        tag("placeholder_new").performClick()
        compose.waitForIdle()

        tag("screen_editor").assertIsDisplayed()
        tag("screen_trips").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun `medium window keeps a single pane with an up arrow`() {
        launch()

        tag("screen_placeholder").assertDoesNotExist()
        tag("trips_open_a").performClick()
        compose.waitForIdle()

        tag("screen_plan_a").assertIsDisplayed()
        tag("plan_up").assertIsDisplayed()
        tag("screen_trips").assertDoesNotExist()
        tag(ShellTestTags.NavTrips).assertIsDisplayed().assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `back stacks and entry state survive recreation`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            OpusAppRoot(
                uiState = ShellUiState.Ready(hasProfile = true, hasTrips = true, settings = TestSettings),
                destinations = FakeDestinations,
            )
        }

        tag(ShellTestTags.NavTrips).performClick()
        tag("trips_open_a").performClick()
        compose.waitForIdle()
        tag("plan_counter_a").performClick().performClick()
        tag(ShellTestTags.NavSettings).performClick()
        tag("settings_about").performClick()
        compose.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()

        tag("screen_about").assertIsDisplayed()
        tag(ShellTestTags.NavSettings).assertIsSelected()
        tag(ShellTestTags.NavTrips).performClick()
        compose.waitForIdle()
        tag("screen_plan_a").assertIsDisplayed()
        tag("plan_counter_a").assertTextEquals("count=2")
    }
}
