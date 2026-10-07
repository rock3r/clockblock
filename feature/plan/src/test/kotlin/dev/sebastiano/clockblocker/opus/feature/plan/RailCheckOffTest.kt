package dev.sebastiano.clockblocker.opus.feature.plan

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.ready
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Quick check-off circles on the rail's rows (issue #23). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h880dp-xhdpi")
class RailCheckOffTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val logged = mutableListOf<Pair<String, AdviceOutcome>>()
    private val undone = mutableListOf<Pair<String, AdviceOutcome?>>()
    private val actions = PlanActions(
        onLog = { id, outcome -> logged += id to outcome },
        onUndo = { id, previous -> undone += id to previous },
    )

    @Before
    fun use24Hour() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun show(state: PlanUiState.Ready) {
        compose.setContent { ClockblockTheme(dynamicColor = false, reduceMotion = true) { PlanContent(state, actions) } }
    }

    /** The rail's own rows (not the chips nested inside them) on [state]'s rail. */
    private fun rowItems(state: PlanUiState.Ready): List<RailItem> =
        buildRailRows(state.plan.railDays(state.now, state.outcomes), state.now, showEarlier = false)
            .filterIsInstance<RailRow.Block>()
            .map { it.item }

    private val midAdaptation = ready(PlanFixtures.MidAdaptation)
    private val past get() = rowItems(midAdaptation).last { it.status == RailStatus.Past && it.advice.type != AdviceType.Flight }.advice.id
    private val future get() = rowItems(midAdaptation).first { it.status == RailStatus.Future && it.advice.type != AdviceType.Flight }.advice.id

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag(PlanTags.Rail).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun `a past row checks off as done, and Undo puts back what was there`() {
        show(midAdaptation)
        scrollTo(PlanTags.checkOff(past))
        compose.onNodeWithTag(PlanTags.checkOff(past)).assertIsOff().performClick()

        logged shouldBe listOf(past to AdviceOutcome.Done)
        compose.onNodeWithText(context.getString(R.string.plan_undo)).performClick()
        compose.waitForIdle()
        undone shouldBe listOf(past to null)
    }

    @Test
    fun `undo after checking off a skipped row puts the skip back`() {
        val state = ready(PlanFixtures.MidAdaptation, outcomes = mapOf(past to AdviceOutcome.Skipped))
        show(state)
        scrollTo(PlanTags.checkOff(past))
        compose.onNodeWithTag(PlanTags.checkOff(past)).assertIsOff().performClick()
        compose.onNodeWithText(context.getString(R.string.plan_undo)).performClick()
        compose.waitForIdle()
        undone shouldBe listOf(past to AdviceOutcome.Skipped)
    }

    @Test
    fun `a done row's circle is checked, and tapping it clears the log`() {
        show(ready(PlanFixtures.MidAdaptation, outcomes = mapOf(past to AdviceOutcome.Done)))
        scrollTo(PlanTags.checkOff(past))
        compose.onNodeWithTag(PlanTags.checkOff(past)).assertIsOn().performClick()
        logged shouldBe emptyList()
        undone shouldBe listOf(past to null)
    }

    @Test
    fun `the row happening now can be checked off too`() {
        show(midAdaptation)
        val now = midAdaptation.moment.active!!.id
        scrollTo(PlanTags.checkOff(now))
        compose.onNodeWithTag(PlanTags.checkOff(now)).performClick()
        logged shouldBe listOf(now to AdviceOutcome.Done)
    }

    @Test
    fun `future rows have no circle - you can't log the future`() {
        show(midAdaptation)
        scrollTo(PlanTags.block(future))
        compose.onNodeWithTag(PlanTags.checkOff(future)).assertDoesNotExist()
    }

    @Test
    fun `flights have no circle - a flight just happens`() {
        val inFlight = ready(PlanFixtures.InFlight)
        val flight = rowItems(inFlight).first { it.advice.type == AdviceType.Flight }.advice.id
        show(inFlight)
        scrollTo(PlanTags.block(flight))
        compose.onNodeWithTag(PlanTags.checkOff(flight)).assertDoesNotExist()
    }
}
