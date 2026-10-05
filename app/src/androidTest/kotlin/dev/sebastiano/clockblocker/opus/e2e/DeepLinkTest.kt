package dev.sebastiano.clockblocker.opus.e2e

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.feature.plan.PlanTags
import dev.sebastiano.clockblocker.opus.shell.ShellTestTags
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** `opusclockblock://` deep links, both on launch and delivered to the running (singleTop) activity. */
@RunWith(AndroidJUnit4::class)
class DeepLinkTest : OpusE2eTest() {

    @Before
    fun onboard() {
        seedOnboarded()
    }

    @Test
    fun tripsDeepLinkOpensTrips() {
        launch(deepLink = "opusclockblock://trips")

        awaitTag("route_trips").assertIsDisplayed()
        awaitTag(ShellTestTags.NavTrips).assertIsSelected()
    }

    @Test
    fun newTripDeepLinkOpensEditorAboveTrips() {
        launch(deepLink = "opusclockblock://trips/new")

        awaitTag("route_trip_editor").assertIsDisplayed()
        device.pressBack()
        awaitTag("route_trips").assertIsDisplayed()
    }

    @Test
    fun currentPlanDeepLinkOpensNow() {
        launch(deepLink = "opusclockblock://plan/current")

        awaitTag("route_now").assertIsDisplayed()
        awaitTag(ShellTestTags.NavNow).assertIsSelected()
    }

    @Test
    fun planDeepLinkOpensThatTripsPlan() {
        val trip = DemoData.lhrToSydneyViaSingapore(LocalDate.now().plusDays(5), id = "e2e-deeplink")
        seedTrip(trip)

        launch(deepLink = "opusclockblock://plan/${trip.id}")

        awaitTag("route_plan", LongTimeoutMillis).assertIsDisplayed()
        awaitTag(PlanTags.NowCard, LongTimeoutMillis).assertIsDisplayed()
        awaitText("Sydney")
    }

    @Test
    fun deepLinkReachesRunningActivity() {
        launch()
        awaitAnyTag("route_now", "route_trips")
        awaitTag(ShellTestTags.NavSettings)

        // Like a notification tap: same task, delivered through onNewIntent.
        context.startActivity(
            deepLinkIntent("opusclockblock://trips/new")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )

        awaitTag("route_trip_editor").assertIsDisplayed()
    }
}
