package dev.sebastiano.clockblocker.opus.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import dev.sebastiano.clockblocker.opus.feature.plan.PlanTags
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** The plan screen: the Why sheet from the rail, the floating toolbar and exporting to a calendar file. */
@RunWith(AndroidJUnit4::class)
class PlanInteractionsTest : ClockblockE2eTest() {

    @Test
    fun railBlockOpensWhySheetAndToolbarJumpsBackToNow() {
        seedOnboarded()
        val active = graph.seedTripWithActiveAdvice()
        launch(deepLink = "clockblock://plan/${active.trip.id}")
        awaitTag(PlanTags.NowCard, LongTimeoutMillis)

        // Scroll the rail to a later block (past the hero cards), and open its explanation.
        val later = active.plan.allAdvice.first { it.start.isAfter(active.advice.end) && !it.type.isMoment }
        compose.onNodeWithTag(PlanTags.Rail).performScrollToNode(hasTestTag(PlanTags.block(later.id)))
        awaitTag(PlanTags.block(later.id)).performClick()
        awaitTag(PlanTags.WhySheet).assertIsDisplayed()
        device.pressBack()
        awaitGone(PlanTags.WhySheet)

        // With the Now card scrolled away the floating toolbar appears; "Now" scrolls the rail back to the row
        // holding now: here the active block (the separate now marker only shows between blocks).
        awaitTag(PlanTags.ToolbarNow).performClick()
        awaitTag(PlanTags.block(active.advice.id)).assertIsDisplayed()
    }

    @Test
    fun exportToCalendarOpensTheDocumentPicker() {
        seedOnboarded()
        val active = graph.seedTripWithActiveAdvice()
        launch(deepLink = "clockblock://plan/${active.trip.id}")
        awaitTag(PlanTags.NowCard, LongTimeoutMillis)

        awaitTag(PlanTags.Overflow).performClick()
        awaitTag(PlanTags.MenuExport).performClick()

        // The system "Save to" picker (DocumentsUI) takes over; cancel it with back.
        val picker = device.wait(Until.findObject(By.pkg(Pattern.compile(".*documentsui.*"))), LongTimeoutMillis)
        assertNotNull("DocumentsUI save picker should open", picker)
        device.pressBack()
        assertNotNull(device.wait(Until.findObject(By.pkg(context.packageName)), LongTimeoutMillis))
        awaitTag(PlanTags.Screen).assertIsDisplayed()
    }
}
