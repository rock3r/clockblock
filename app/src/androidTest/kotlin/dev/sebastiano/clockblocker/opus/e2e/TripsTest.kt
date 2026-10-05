package dev.sebastiano.clockblocker.opus.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.feature.plan.PlanTags
import dev.sebastiano.clockblocker.opus.feature.plan.R as PlanR
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Creating trips (by hand and the demo) and acting on the plan's Now card. */
@RunWith(AndroidJUnit4::class)
class TripsTest : OpusE2eTest() {

    @Test
    fun createTripWithPickersAndValidationFixOpensItsPlan() {
        seedOnboarded()
        launch()
        awaitTag("route_trips", LongTimeoutMillis)
        awaitTag(TripsTestTags.EmptyState).assertIsDisplayed()

        openNewTrip()
        pickPlace(TripsTestTags.editorFrom(0), "lis", "LIS")
        pickPlace(TripsTestTags.editorTo(0), "hnd", "HND")

        awaitTag(TripsTestTags.editorDepartureDate(0)).scrollToIfScrollable().performClick()
        pickDateInOpenPicker(departureDay())
        confirmTimePicker(TripsTestTags.editorDepartureTime(0))
        // 09:00 in Tokyo on the departure day is before 09:00 in Lisbon: the validator flags it and offers a fix.
        confirmTimePicker(TripsTestTags.editorArrivalTime(0))
        awaitTag(TripsTestTags.editorFix(0)).scrollToIfScrollable().performClick()
        awaitGone(TripsTestTags.editorFix(0))

        awaitTag(TripsTestTags.EditorSave).assertIsEnabled().performClick()

        // A new trip saved from Trips opens its plan.
        awaitTag("route_plan", LongTimeoutMillis).assertIsDisplayed()
        awaitTag(PlanTags.Dial, LongTimeoutMillis).assertIsDisplayed()
        awaitTag(PlanTags.NowCard).assertIsDisplayed()
        awaitText("Tokyo")

        device.pressBack()
        awaitTag("route_trips").assertIsDisplayed()
        await(hasTestTagPrefix("trip_card_") and hasAnyDescendant(hasText("Tokyo", substring = true))).assertIsDisplayed()
        val trips = runBlocking { graph.tripRepository.trips.first() }
        assertEquals(listOf("LIS" to "HND"), trips.map { it.origin.code to it.destination.code })
    }

    @Test
    fun demoTripFromEmptyStateOpensItsPlan() {
        seedOnboarded()
        launch()
        awaitTag(TripsTestTags.EmptyDemoTrip, LongTimeoutMillis).scrollToIfScrollable().performClick()

        awaitTag("route_plan", LongTimeoutMillis).assertIsDisplayed()
        awaitTag(PlanTags.Dial, LongTimeoutMillis).assertIsDisplayed()
        awaitTag(PlanTags.NowCard).assertIsDisplayed()
        awaitText("London")

        device.pressBack()
        awaitTag(TripsTestTags.tripCard("demo-try-sfo-lhr")).assertIsDisplayed()
    }

    @Test
    fun nowCardDoneLogsTheOutcome() {
        seedOnboarded()
        val active = graph.seedTripWithActiveAdvice()
        launch(deepLink = "opusclockblock://plan/${active.trip.id}")

        awaitTag(PlanTags.NowCard, LongTimeoutMillis).assertIsDisplayed()
        // At rest Done can sit below the fold (under the navigation bar), depending on the card's text.
        awaitTag(PlanTags.Done).scrollIntoViewAndClick()

        // The UI confirms it (snackbar) and the outcome is stored for this trip's advice.
        awaitText(context.getString(PlanR.string.plan_logged_done)).assertIsDisplayed()
        compose.waitUntil(DefaultTimeoutMillis) {
            runBlocking { graph.adviceLogRepository.logs(active.trip.id).first() }.any { it.outcome == AdviceOutcome.Done }
        }
    }
}
