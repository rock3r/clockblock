package dev.sebastiano.clockblocker.opus.feature.plan

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.circadian.DefaultJetLagPlanner
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/**
 * Regression for issue #11 (found through the e2e `nowCardDoneLogsTheOutcome` failure on the CI Pixel 7): on a
 * compact phone the Now card's Done button used to sit entirely below the visible part of the plan list at rest.
 * The dial now gives up size on short windows and Done shares the "until" row, so Done is on screen when the plan
 * opens.
 *
 * The scenario is the failing CI one, pinned: 18:01 UTC on 5 Oct 2026, the demo SFO → LHR trip that departed the
 * day before (the Now card shows "Avoid caffeine" until 2:00 AM), a 12-hour clock (which makes the card's time line
 * longest), in a window shorter than the Pixel 7's usable height.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h640dp-420dpi")
class NowCardAboveTheFoldTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val logged = mutableListOf<Pair<String, AdviceOutcome>>()

    @Before
    fun use12Hour() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
    }

    @Test
    fun `Done is on screen at rest and logs the active block`() {
        val now = Instant.parse("2026-10-05T18:01:00Z")
        val trip = DemoData.sfoToLhr(LocalDate.parse("2026-10-04"), id = "below-the-fold")
        val plan = DefaultJetLagPlanner().plan(trip, DemoData.profile, now)
        val state = PlanFixtures.ready(now, plan = plan, trip = trip)
        val active = checkNotNull(state.moment.active) { "the pinned scenario must have an active block" }
        active.type shouldBe AdviceType.AvoidCaffeine
        val actions = PlanActions(onLog = { id, outcome -> logged += id to outcome })
        compose.setContent { OpusTheme(dynamicColor = false, reduceMotion = true) { PlanContent(state, actions) } }

        // At rest, without scrolling: the whole button is inside the window.
        val window = compose.onRoot().getBoundsInRoot()
        val done = compose.onNodeWithTag(PlanTags.Done).assertIsDisplayed().getBoundsInRoot()
        (done.bottom <= window.bottom) shouldBe true

        compose.onNodeWithTag(PlanTags.Done).performClick()

        logged shouldBe listOf(active.id to AdviceOutcome.Done)
        compose.onNodeWithText(context.getString(R.string.plan_logged_done)).assertExists()
    }
}
