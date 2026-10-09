package dev.sebastiano.clockblocker.opus.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import dev.sebastiano.clockblocker.opus.core.designsystem.prc.LightResponseCurveTags
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.feature.plan.PlanTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern
import dev.sebastiano.clockblocker.opus.core.designsystem.R as DesignR
import dev.sebastiano.clockblocker.opus.feature.plan.R as PlanR

/**
 * The plan screen: the Why sheet from the rail (with the light response curve on a light block), the rail's
 * check-off circles, the floating toolbar and exporting to a calendar file.
 */
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
    fun railTickLogsDoneAndCanBeUndone() {
        seedOnboarded()
        val active = graph.seedTripWithActiveAdvice()
        launch(deepLink = "clockblock://plan/${active.trip.id}")
        awaitTag(PlanTags.NowCard, LongTimeoutMillis)

        // The block happening now has a check-off circle on its rail row; ticking it is the Now card's Done. The
        // circle follows the stored log, not the tap, so wait for the log and then for the circle.
        val tick = PlanTags.checkOff(active.advice.id)
        scrollToMiddle(PlanTags.Rail, tick).assertIsOff().performClick()
        awaitOutcome(active, AdviceOutcome.Done)
        await(hasTestTag(tick) and isOn())

        // The snackbar's Undo puts back what the log held before: nothing.
        await(hasText(context.getString(PlanR.string.plan_undo)) and hasClickAction()).performClick()
        awaitOutcome(active, null)
        await(hasTestTag(tick) and isOff())

        // Unticking a ticked row forgets the log too.
        awaitTag(tick).performClick()
        awaitOutcome(active, AdviceOutcome.Done)
        await(hasTestTag(tick) and isOn()).performClick()
        awaitOutcome(active, null)
        await(hasTestTag(tick) and isOff())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun whyOnALightBlockShowsTheLightResponseCurve() {
        seedOnboarded()
        val active = graph.seedTripWithActiveAdvice()
        launch(deepLink = "clockblock://plan/${active.trip.id}")
        awaitTag(PlanTags.NowCard, LongTimeoutMillis)

        val light = active.plan.allAdvice.first { it.type in LightTypes && it.start.isAfter(active.advice.end) }
        scrollToMiddle(PlanTags.Rail, PlanTags.block(light.id)).performClick()
        awaitTag(PlanTags.WhySheet)
        awaitTag(PlanTags.WhyLightCurve)

        // The readout is the curve's state (the visible labels are cleared for TalkBack); "Later hour" moves it.
        val before = awaitTag(LightResponseCurveTags.Curve).stateDescription
        assertNotNull("the curve reads out where it is", before)
        awaitTag(LightResponseCurveTags.Curve)
            .performCustomAccessibilityActionWithLabel(context.getString(DesignR.string.light_curve_action_later))
        compose.waitUntil(DefaultTimeoutMillis) { awaitTag(LightResponseCurveTags.Curve).stateDescription != before }
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

    /** Waits until the trip's log holds [outcome] for the active block (`null`: nothing logged). */
    private fun awaitOutcome(active: ActiveTrip, outcome: AdviceOutcome?) {
        compose.waitUntil("the block's log to be $outcome", DefaultTimeoutMillis) {
            runBlocking { graph.adviceLogRepository.logs(active.trip.id).first() }
                .firstOrNull { it.adviceId == active.advice.id }?.outcome == outcome
        }
    }

    private companion object {
        /** The advice whose Why sheet shows the light response curve. */
        val LightTypes = setOf(AdviceType.SeeBrightLight, AdviceType.SeeLight, AdviceType.AvoidLight)
    }
}
