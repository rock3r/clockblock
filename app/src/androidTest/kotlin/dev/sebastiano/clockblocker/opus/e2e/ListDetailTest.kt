package dev.sebastiano.clockblocker.opus.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.DeepLinks
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import dev.sebastiano.clockblocker.opus.shell.ShellTestTags
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * Trips and a plan side by side on a tablet-sized window. The e2e AVD is a phone, so the test overrides the display
 * to a 1280 × 800 dp landscape tablet (`wm size` / `wm density`) and restores the previous values afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ListDetailTest : ClockblockE2eTest() {
    private var savedSize: String? = null
    private var savedDensity: String? = null

    @Before
    fun becomeATablet() {
        savedSize = override(shell("wm size"))
        savedDensity = override(shell("wm density"))
        shell("wm size 2560x1600")
        shell("wm density 320")
        device.waitForIdle()
    }

    @After
    fun restoreDisplay() {
        shell(savedSize?.let { "wm size $it" } ?: "wm size reset")
        shell(savedDensity?.let { "wm density $it" } ?: "wm density reset")
        device.waitForIdle()
    }

    @Test
    fun aTripOpensBesideTheListAndBackClosesIt() {
        seedOnboarded()
        val today = LocalDate.now(ZoneId.systemDefault())
        // The first upcoming trip lives on Now; the second one is listed on Trips.
        seedTrip(DemoData.sfoToLhr(today.plusDays(10), id = "now-trip"))
        seedTrip(DemoData.sfoToLhr(today.plusDays(40), id = "later-trip"))
        launch(deepLink = DeepLinks.TRIPS)

        awaitTag("route_trips", LongTimeoutMillis)
        awaitTag(ShellTestTags.PlanPlaceholder).assertIsDisplayed()

        awaitTag(TripsTestTags.tripCard("later-trip")).performClick()
        awaitTag("route_plan", LongTimeoutMillis)
        awaitGone(ShellTestTags.PlanPlaceholder)
        // Both panes, the rail still there, and the open trip highlighted in the list.
        awaitTag("route_trips").assertIsDisplayed()
        awaitTag("route_plan").assertIsDisplayed()
        awaitTag(ShellTestTags.NavTrips).assertIsDisplayed().assertIsSelected()
        awaitTag(TripsTestTags.tripCard("later-trip")).assertIsSelected()
        awaitTag(ShellTestTags.PaneHandle).assertIsDisplayed()

        device.pressBack()
        awaitGone("route_plan")
        awaitTag(ShellTestTags.PlanPlaceholder).assertIsDisplayed()
        awaitTag("route_trips").assertIsDisplayed()
    }

    /** The override in `wm size` / `wm density` output ("Override size: 1080x2400"), or null when there is none. */
    private fun override(output: String): String? =
        output.lineSequence().firstOrNull { it.startsWith("Override") }?.substringAfter(':')?.trim()
}
