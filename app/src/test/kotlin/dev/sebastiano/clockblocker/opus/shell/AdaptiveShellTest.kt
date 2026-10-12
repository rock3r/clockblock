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

/** Adaptive shell behaviour across window sizes (see [ShellLayout]), and back stack restoration. */
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
    fun `expanded window shows trips next to a placeholder, with the navigation rail`() {
        launch()

        tag("screen_trips").assertIsDisplayed()
        tag(ShellTestTags.PlanPlaceholder).assertIsDisplayed()
        tag(ShellTestTags.NavTrips).assertIsDisplayed().assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `expanded window opens plans in the detail pane, keeps the rail and has no up arrow`() {
        launch()

        tag("trips_open_a").performClick()
        compose.waitForIdle()

        tag("screen_trips").assertIsDisplayed()
        tag("screen_plan_a").assertIsDisplayed()
        tag(ShellTestTags.PlanPlaceholder).assertDoesNotExist()
        tag("plan_up").assertDoesNotExist()
        tag("trips_selected").assertTextEquals("selected=a")
        tag(ShellTestTags.NavTrips).assertIsDisplayed().assertIsSelected()
        tag(ShellTestTags.PaneHandle).assertIsDisplayed()

        // Opening another trip replaces the detail rather than stacking it.
        tag("trips_open_b").performClick()
        compose.waitForIdle()
        tag("screen_plan_b").assertIsDisplayed()
        tag("screen_plan_a").assertDoesNotExist()
        tag("trips_selected").assertTextEquals("selected=b")

        // Back closes the detail, back to the placeholder (not to plan a).
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        tag("screen_plan_b").assertDoesNotExist()
        tag(ShellTestTags.PlanPlaceholder).assertIsDisplayed()
        tag("trips_selected").assertTextEquals("selected=none")
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `re-selecting Trips in the rail closes the open plan`() {
        launch()
        tag("trips_open_a").performClick()
        compose.waitForIdle()

        tag(ShellTestTags.NavTrips).performClick()
        compose.waitForIdle()

        tag("screen_plan_a").assertDoesNotExist()
        tag(ShellTestTags.PlanPlaceholder).assertIsDisplayed()
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
    @Config(qualifiers = "w1000dp-h800dp")
    fun `Now gets the whole window, without a placeholder`() {
        launch(hasTrips = true)

        tag("screen_plan_now").assertIsDisplayed()
        tag(ShellTestTags.PlanPlaceholder).assertDoesNotExist()
        tag(ShellTestTags.NavNow).assertIsDisplayed().assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun `medium window pushes a trip plan as one pane with an up arrow, keeping the rail`() {
        launch()
        tag(ShellTestTags.PlanPlaceholder).assertDoesNotExist()

        tag("trips_open_a").performClick()
        compose.waitForIdle()

        tag("screen_plan_a").assertIsDisplayed()
        tag("plan_up").assertIsDisplayed()
        tag("screen_trips").assertDoesNotExist()
        tag("trips_selected").assertDoesNotExist()
        tag(ShellTestTags.NavTrips).assertIsDisplayed().assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp")
    fun `landscape phone pushes a trip plan full screen without the rail`() {
        launch()
        tag(ShellTestTags.PlanPlaceholder).assertDoesNotExist()

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
