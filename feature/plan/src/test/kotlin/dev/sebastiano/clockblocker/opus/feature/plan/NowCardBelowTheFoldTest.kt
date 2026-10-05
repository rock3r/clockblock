package dev.sebastiano.clockblocker.opus.feature.plan

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * Regression for the e2e `nowCardDoneLogsTheOutcome` failure on the CI Pixel 7: on a compact phone the Now
 * card's Done button can sit entirely below the visible part of the plan list at rest. It's still composed (the
 * card itself is partly on screen), but its clipped bounds are empty, so a tap "on" it lands nowhere. It must
 * stay reachable by scrolling, which is what users (and the e2e test) do.
 *
 * The scenario is the failing CI one, pinned: 18:01 UTC on 5 Oct 2026, the demo SFO → LHR trip that departed the
 * day before (the Now card shows "Avoid caffeine" until 2:00 AM), a 12-hour clock (which wraps the card's
 * secondary-zone line, making the card taller), in a window shorter than the Pixel 7's usable height.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h640dp-420dpi")
class NowCardBelowTheFoldTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val logged = mutableListOf<Pair<String, AdviceOutcome>>()

    @Before
    fun use12Hour() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
    }

    @Test
    fun `Done below the fold is reachable by scrolling and logs the active block`() {
        val now = Instant.parse("2026-10-05T18:01:00Z")
        val trip = DemoData.sfoToLhr(LocalDate.parse("2026-10-04"), id = "below-the-fold")
        val plan = DefaultJetLagPlanner().plan(trip, DemoData.profile, now)
        val state = PlanFixtures.ready(now, plan = plan, trip = trip)
        val active = checkNotNull(state.moment.active) { "the pinned scenario must have an active block" }
        active.type shouldBe AdviceType.AvoidCaffeine
        val actions = PlanActions(onLog = { id, outcome -> logged += id to outcome })
        compose.setContent { OpusTheme(dynamicColor = false, reduceMotion = true) { PlanContent(state, actions) } }

        // At rest: the card is on screen, its Done button isn't (the precondition the e2e test tripped over).
        compose.onNodeWithTag(PlanTags.NowCard).assertIsDisplayed()
        compose.onNodeWithTag(PlanTags.Done).assertIsNotDisplayed()

        compose.onNodeWithTag(PlanTags.Done).performScrollTo().assertIsDisplayed().performClick()

        logged shouldBe listOf(active.id to AdviceOutcome.Done)
        compose.onNodeWithText(context.getString(R.string.plan_logged_done)).assertExists()
    }
}
