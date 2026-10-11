package dev.sebastiano.clockblocker.opus.e2e

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.feature.plan.PlanTags
import dev.sebastiano.clockblocker.opus.feature.plan.R as PlanR
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import dev.sebastiano.clockblocker.opus.feature.trips.list.TripPhase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** Creating trips (by hand and the demo) and acting on the plan's Now card. */
@RunWith(AndroidJUnit4::class)
class TripsTest : ClockblockE2eTest() {

    @Test
    fun createTripWithPickersAndValidationFixOpensItsPlan() {
        seedOnboarded()
        launch()
        awaitTag("route_trips", LongTimeoutMillis)
        awaitTag(TripsTestTags.EmptyState).assertIsDisplayed()

        openNewTrip()
        pickPlace(TripsTestTags.editorFrom(0), "lis", "LIS")
        pickPlace(TripsTestTags.editorTo(0), "hnd", "HND")

        openPicker(TripsTestTags.editorDepartureDate(0))
        pickDateInOpenPicker(departureDay())
        confirmTimePicker(TripsTestTags.editorDepartureTime(0))
        // With both airports and the departure set, the editor estimates the arrival from the distance (next morning
        // in Tokyo) and labels it as an estimate.
        awaitTag(TripsTestTags.editorArrivalEstimate(0)).scrollToIfScrollable().assertIsDisplayed()
        // Landing on the departure day itself would be before take-off: the validator flags it and offers a fix.
        openPicker(TripsTestTags.editorArrivalDate(0))
        // The picker opens on the estimate's month, which is next month when the departure is the month's last day.
        if (departureDay().plusDays(1).month != departureDay().month) {
            await(hasContentDescription("previous month", substring = true, ignoreCase = true) and hasClickAction())
                .performClick()
        }
        pickDateInOpenPicker(departureDay())
        awaitGone(TripsTestTags.editorArrivalEstimate(0))
        awaitTag(TripsTestTags.editorFix(0)).scrollIntoViewAndClick()
        awaitGone(TripsTestTags.editorFix(0))

        awaitTag(TripsTestTags.EditorSave).assertIsEnabled().performClick()

        // A new trip saved from Trips opens its plan.
        awaitTag("route_plan", LongTimeoutMillis).assertIsDisplayed()
        awaitTag(PlanTags.Dial, LongTimeoutMillis).assertIsDisplayed()
        awaitTag(PlanTags.NowCard).assertIsDisplayed()
        awaitText("Tokyo")

        // Back on Trips: the single upcoming trip lives on Now, and Trips points there.
        device.pressBack()
        awaitTag("route_trips").assertIsDisplayed()
        awaitTag(TripsTestTags.UpcomingEmptyState)
            .assert(hasAnyDescendant(hasText("Tokyo", substring = true)))
            .assertIsDisplayed()
        val trips = runBlocking { graph.tripRepository.trips.first() }
        assertEquals(listOf("LIS" to "HND"), trips.map { it.origin.code to it.destination.code })
    }

    @Test
    fun returnFlightSwitchSavesTheReturnTripToo() {
        seedOnboarded()
        launch(deepLink = "clockblock://trips/new")
        awaitTag("route_trip_editor", LongTimeoutMillis)

        pickPlace(TripsTestTags.editorFrom(0), "lis", "LIS")
        pickPlace(TripsTestTags.editorTo(0), "hnd", "HND")
        awaitGone(TripsTestTags.EditorPlaceSheet)
        openPicker(TripsTestTags.editorDepartureDate(0))
        pickDateInOpenPicker(departureDay())
        confirmTimePicker(TripsTestTags.editorDepartureTime(0))

        // Return flight on: a week after landing is suggested, and the main action builds both trips.
        awaitTag(TripsTestTags.EditorReturnSwitch).scrollIntoViewAndClick()
        awaitTag(TripsTestTags.EditorReturnDate).scrollToIfScrollable().assertIsDisplayed()
        awaitTag(TripsTestTags.EditorSave).assertIsEnabled().assert(hasText("Build my plan")).performClick()

        awaitTag("route_plan", LongTimeoutMillis).assertIsDisplayed()
        compose.waitUntil(DefaultTimeoutMillis) { runBlocking { graph.tripRepository.trips.first() }.size == 2 }
        val trips = runBlocking { graph.tripRepository.trips.first() }
        assertEquals(listOf("LIS" to "HND", "HND" to "LIS"), trips.map { it.origin.code to it.destination.code })

        // Back on Trips: the outbound trip is on Now, and the return trip sits in Upcoming on Trips.
        device.pressBack()
        awaitTag("route_trips").assertIsDisplayed()
        await(hasTestTagPrefix("trip_card_") and hasAnyDescendant(hasText("Lisbon", substring = true))).assertIsDisplayed()
    }

    @Test
    fun demoTripFromEmptyStateOpensItsPlan() {
        seedOnboarded()
        launch()
        awaitTag(TripsTestTags.EmptyDemoTrip, LongTimeoutMillis).scrollIntoViewAndClick()

        awaitTag("route_plan", LongTimeoutMillis).assertIsDisplayed()
        awaitTag(PlanTags.Dial, LongTimeoutMillis).assertIsDisplayed()
        awaitTag(PlanTags.NowCard).assertIsDisplayed()
        awaitText("London")

        device.pressBack()
        awaitTag(TripsTestTags.UpcomingEmptyState).assertIsDisplayed()
        awaitTag(TripsTestTags.UpcomingEmptyNow).performClick()
        awaitTag("route_now").assertIsDisplayed()
    }

    @Test
    fun nowCardDoneLogsTheOutcome() {
        seedOnboarded()
        val active = graph.seedTripWithActiveAdvice()
        launch(deepLink = "clockblock://plan/${active.trip.id}")

        awaitTag(PlanTags.NowCard, LongTimeoutMillis).assertIsDisplayed()
        // At rest Done can sit below the fold (under the navigation bar), depending on the card's text.
        awaitTag(PlanTags.Done).scrollIntoViewAndClick()

        // The UI confirms it (snackbar) and the outcome is stored for this trip's advice.
        awaitText(context.getString(PlanR.string.plan_logged_done)).assertIsDisplayed()
        compose.waitUntil(DefaultTimeoutMillis) {
            runBlocking { graph.adviceLogRepository.logs(active.trip.id).first() }.any { it.outcome == AdviceOutcome.Done }
        }
    }

    @Test
    fun pastTripsOpenFromNowAndShowPastGlobeSheet() {
        seedOnboarded()
        val pastDay = LocalDate.now(ZoneId.systemDefault()).minusDays(20)
        seedTrip(DemoData.sfoToLhr(pastDay, id = "past-sfo-lhr"))
        launch()

        // With only a past trip, Now shows its empty state and offers to open Trips.
        awaitTag("route_now", LongTimeoutMillis).assertIsDisplayed()
        awaitTag(PlanTags.OpenTrips, LongTimeoutMillis).scrollIntoViewAndClick()

        // Trips shows the Upcoming empty state above the Past trips section.
        awaitTag("route_trips").assertIsDisplayed()
        awaitTag(TripsTestTags.UpcomingEmptyState).assertIsDisplayed()
        awaitTag(TripsTestTags.tripCard("past-sfo-lhr")).scrollToIfScrollable().assertIsDisplayed()

        // Tapping the Past trips header opens the globe sheet with all past routes and stats.
        awaitTag(TripsTestTags.section(TripPhase.Past.name)).scrollIntoViewAndClick()
        awaitTag(TripsTestTags.PastGlobeSheet).assertIsDisplayed()
        awaitTag(TripsTestTags.PastGlobe).assertIsDisplayed()
    }
}
