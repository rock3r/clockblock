package dev.sebastiano.clockblocker.opus.e2e

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sebastiano.clockblocker.opus.core.model.DeepLinks
import dev.sebastiano.clockblocker.opus.feature.plan.PlanTags
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import dev.sebastiano.clockblocker.opus.feature.plan.R as PlanR

/**
 * The plan in two panes. The e2e AVD is a phone, but in landscape its window is short and wide enough for the plan's
 * two panes (the day strip beside the rail). Rotation settings are saved before and restored after each test.
 */
@RunWith(AndroidJUnit4::class)
class PlanTwoPaneTest : ClockblockE2eTest() {
    private lateinit var saved: Map<String, String>

    @Before
    fun saveDeviceSettings() {
        saved = SystemSettings.associateWith { shell("settings get system $it").trim() }
    }

    @After
    fun restoreDeviceSettings() {
        runCatching { device.setOrientationNatural() }
        saved.forEach { (key, value) ->
            if (value == "null" || value.isEmpty()) shell("settings delete system $key") else shell("settings put system $key $value")
        }
        device.waitForIdle()
    }

    @Test
    fun theDayStripFollowsTheRail() {
        seedOnboarded()
        val active = graph.seedTripWithActiveAdvice()
        val today = active.plan.days.first { active.advice in it.advice }.index
        val last = active.plan.days.last().index
        assumeTrue("the plan needs a day after today to scroll to", last > today)
        // Now, not the trip's own plan: in landscape the shell shows a trip's plan beside Trips, a pane too narrow for
        // the plan's two panes, while Now gives the current trip's plan the whole window.
        launch(deepLink = DeepLinks.CURRENT_PLAN)
        awaitTag(PlanTags.NowCard, LongTimeoutMillis)

        device.setOrientationLandscape()
        compose.waitUntil(DefaultTimeoutMillis) {
            device.waitForIdle()
            device.displayWidth > device.displayHeight
        }
        awaitTag(PlanTags.DayStrip, LongTimeoutMillis)

        // Scrolling the rail to its end marks the last day's pill (one pane never marks one), and only that one.
        // Bringing the last day's header into view isn't enough: it can land below the rail's reading line, and how
        // far depends on how many rows the seeded plan has left at the time of day the test runs. At the end of the
        // rail the last day is always the one in view.
        val inView = hasStateDescription(context.getString(PlanR.string.plan_strip_pill_in_view))
        awaitTag(PlanTags.Rail)
        val rail = compose.onNodeWithTag(PlanTags.Rail)
        rail.performScrollToNode(hasTestTag(PlanTags.day(last)))
        repeat(RailEndFlings) {
            rail.performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        await(hasTestTag(PlanTags.dayPill(last)) and inView, LongTimeoutMillis)
        compose.onAllNodes(inView).assertCountEquals(1)
    }

    private companion object {
        val SystemSettings = listOf("accelerometer_rotation", "user_rotation")
        const val RailEndFlings = 4
    }
}
