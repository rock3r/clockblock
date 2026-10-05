package dev.sebastiano.clockblocker.opus.feature.plan

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.TimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.ready
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.realPlan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import dev.sebastiano.clockblocker.opus.core.designsystem.R as DesignR

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h880dp-xhdpi")
class PlanContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val logged = mutableListOf<Pair<String, AdviceOutcome>>()
    private val snoozed = mutableListOf<String>()
    private var celebrationShown = 0

    private val actions = PlanActions(
        onBack = {},
        onLog = { id, outcome -> logged += id to outcome },
        onSnooze = { snoozed += it },
        onCelebrationShown = { celebrationShown++ },
    )

    @Before
    fun use24Hour() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun show(state: PlanUiState) {
        compose.setContent { OpusTheme(dynamicColor = false, reduceMotion = true) { PlanContent(state, actions) } }
    }

    private val midAdaptation = ready(PlanFixtures.MidAdaptation)
    private val activeId get() = midAdaptation.moment.active!!.id

    @Test
    fun `Done logs the active block`() {
        show(midAdaptation)
        compose.onNodeWithTag(PlanTags.Done).performClick()
        logged shouldBe listOf(activeId to AdviceOutcome.Done)
        compose.onNodeWithText(context.getString(R.string.plan_logged_done)).assertExists()
    }

    @Test
    fun `Skipped lives in the split button menu`() {
        show(midAdaptation)
        compose.onNodeWithTag(PlanTags.MoreOutcomes).performClick()
        compose.onNodeWithTag(PlanTags.Skipped).performClick()
        logged shouldBe listOf(activeId to AdviceOutcome.Skipped)
    }

    @Test
    fun `Can't do this lives in the split button menu`() {
        show(midAdaptation)
        compose.onNodeWithTag(PlanTags.MoreOutcomes).performClick()
        compose.onNodeWithTag(PlanTags.CantDo).performClick()
        logged shouldBe listOf(activeId to AdviceOutcome.CantDo)
        compose.onNodeWithText(context.getString(R.string.plan_logged_cant)).assertExists()
    }

    @Test
    fun `Snooze goes through the actions`() {
        show(midAdaptation)
        compose.onNodeWithTag(PlanTags.MoreOutcomes).performClick()
        compose.onNodeWithTag(PlanTags.Snooze).performClick()
        snoozed shouldBe listOf(activeId)
        compose.onNodeWithText(context.getString(R.string.plan_snoozed)).assertExists()
    }

    @Test
    fun `Why opens the explanation sheet`() {
        show(midAdaptation)
        compose.onNodeWithTag(PlanTags.NowWhy).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.WhySheet).assertExists()
        compose.onNodeWithText(context.getString(R.string.plan_why_how)).assertExists()
    }

    @Test
    fun `tapping a rail block opens its explanation`() {
        show(midAdaptation)
        val next = midAdaptation.moment.upNext.first()
        compose.onNodeWithTag(PlanTags.Rail).performScrollToNode(hasTestTag(PlanTags.block(next.id)))
        compose.onNodeWithTag(PlanTags.block(next.id)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.WhySheet).assertExists()
    }

    @Test
    fun `scrubbing the dial previews another moment and comes back to now`() {
        show(midAdaptation)
        val inNowCard = hasAnyAncestor(hasTestTag(PlanTags.NowCard))
        // Headings render in caps ("NOW", "AT 13:30").
        val nowHeading = hasText(context.getString(R.string.plan_now).uppercase()) and inNowCard
        val previewHeading = hasText(context.getString(R.string.plan_previewing, "").uppercase(), substring = true) and inNowCard
        compose.onNode(nowHeading).assertExists()

        compose.onNodeWithTag(PlanTags.Dial).performCustomAccessibilityActionWithLabel(context.getString(DesignR.string.dial_action_next_block))
        compose.waitForIdle()
        // The Now card follows the dial ("At 13:30") and the "Now" heading is gone.
        compose.onNode(previewHeading).assertExists()
        compose.onNode(nowHeading).assertDoesNotExist()

        compose.onNodeWithTag(PlanTags.Dial).performCustomAccessibilityActionWithLabel(context.getString(DesignR.string.dial_action_back_to_now))
        compose.waitForIdle()
        compose.onNode(nowHeading).assertExists()
    }

    @Test
    fun `the celebration is dismissed once and reported`() {
        show(ready(PlanFixtures.Adapted, celebrate = true))
        compose.onNodeWithTag(PlanTags.Celebration).assertIsDisplayed()
        compose.onNodeWithTag(PlanTags.CelebrationDismiss).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.Celebration).assertDoesNotExist()
        celebrationShown shouldBe 1
    }

    @Test
    fun `night-safe shows its label`() {
        show(ready(PlanFixtures.Evening, nightSafe = true))
        compose.onNodeWithTag(PlanTags.NightSafeChip).assertIsDisplayed()
    }

    @Test
    fun `no plan offers to plan a trip`() {
        var newTrip = 0
        compose.setContent {
            OpusTheme(dynamicColor = false, reduceMotion = true) {
                PlanContent(PlanUiState.NoPlan(missing = false), PlanActions(onNewTrip = { newTrip++ }))
            }
        }
        compose.onNodeWithTag(PlanTags.NewTrip).performClick()
        newTrip shouldBe 1
    }

    @Test
    fun `a fast load never flashes the loader`() {
        compose.mainClock.autoAdvance = false
        show(PlanUiState.Loading)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(PlanTags.Loading).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(LoaderShowDelayMillis + 100)
        compose.onNodeWithTag(PlanTags.Loading).assertExists()
    }

    @Test
    fun `undo restores what was logged before`() {
        val undone = mutableListOf<Pair<String, AdviceOutcome?>>()
        val state = ready(PlanFixtures.MidAdaptation, outcomes = mapOf(activeId to AdviceOutcome.Skipped))
        compose.setContent {
            OpusTheme(dynamicColor = false, reduceMotion = true) {
                PlanContent(state, PlanActions(onUndo = { id, previous -> undone += id to previous }))
            }
        }
        compose.onNodeWithTag(PlanTags.Done).performClick()
        compose.onNodeWithText(context.getString(R.string.plan_undo)).performClick()
        compose.waitForIdle()
        undone shouldBe listOf(activeId to AdviceOutcome.Skipped)
    }

    @Test
    fun `with motion on, the celebration waits for navigation and the rings`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OpusTheme(dynamicColor = false, reduceMotion = false) { PlanContent(ready(PlanFixtures.Adapted, celebrate = true), actions) }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(PlanTags.CelebrationDismiss).assertDoesNotExist()
        // Navigation settle + the ring turn (or the timeout) later, the overlay is up.
        compose.mainClock.advanceTimeBy(CelebrationNavSettleMillis + CelebrationAlignTimeoutMillis + 1_000)
        compose.onNodeWithTag(PlanTags.CelebrationDismiss).assertExists()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun `the day picker jumps to a day`() {
        show(midAdaptation)
        compose.onNodeWithTag(PlanTags.ToolbarDay).performClick()
        compose.onNodeWithTag(PlanTags.dayPick(4)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.day(4)).assertIsDisplayed()
    }

    @Test
    fun `the overflow menu exposes trip actions`() {
        var edits = 0
        var exports = 0
        compose.setContent {
            OpusTheme(dynamicColor = false, reduceMotion = true) {
                PlanContent(midAdaptation, PlanActions(onEditTrip = { edits++ }, onExportCalendar = { exports++ }))
            }
        }
        compose.onNodeWithTag(PlanTags.Overflow).performClick()
        compose.onNodeWithTag(PlanTags.MenuEdit).performClick()
        compose.onNodeWithTag(PlanTags.Overflow).performClick()
        compose.onNodeWithTag(PlanTags.MenuExport).performClick()
        edits shouldBe 1
        exports shouldBe 1
    }

    @Test
    fun `the share summary lists every day and the disclaimer`() {
        val summary = buildPlanSummary(context.resources, TimeFormatter(true, Locale.UK), realPlan, PlanFixtures.trip)
        summary shouldContain context.getString(R.string.plan_share_footer)
        summary shouldContain "05:00 – 08:00"
        summary shouldContain context.getString(R.string.plan_day_header_travel)
    }
}
