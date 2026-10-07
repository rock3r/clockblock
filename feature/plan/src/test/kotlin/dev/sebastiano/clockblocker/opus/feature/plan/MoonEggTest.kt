package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The header's moon-phase easter egg and its gate (reduce motion off, the plan not saying sleep). */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h880dp-xhdpi")
class MoonEggTest {
    @get:Rule
    val compose = createComposeRule()

    private var enabled by mutableStateOf(true)
    private var tips = 0

    private fun showHeader() {
        // In flight the body is at about 22:00, so the header shows the moon.
        val moment = PlanFixtures.ready(PlanFixtures.InFlight).moment
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = false) {
                PlanHeader(
                    title = "London",
                    moment = moment,
                    firstDay = null,
                    nightSafe = false,
                    canEdit = false,
                    easterEggs = enabled,
                    actions = PlanActions(onBack = null, onLog = { _, _ -> }, onSnooze = {}, onCelebrationShown = {}),
                    onMoonTip = { tips++ },
                    scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
                )
            }
        }
    }

    private fun tapMoon(times: Int) = repeat(times) {
        compose.onNodeWithTag(PlanTags.Moon).performClick()
        // With the clock paused, the click's state write only reaches composition once it's applied.
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun the_moon_egg_fires_its_tip_after_seven_taps() {
        showHeader()
        tapMoon(MoonTaps)
        compose.waitForIdle()
        tips shouldBe 1
    }

    @Test
    fun the_gate_closing_cancels_a_hatched_moon_and_reopening_starts_over() {
        showHeader()
        compose.mainClock.autoAdvance = false
        tapMoon(MoonTaps)
        // The moon has hatched and is mid-morph when a sleep block starts (or Reduce motion is turned on).
        enabled = false
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(30_000)
        tips shouldBe 0

        // When the gate reopens the egg starts over: the moon is plain again and needs all seven taps.
        enabled = true
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(1_000)
        tapMoon(MoonTaps - 1)
        compose.mainClock.advanceTimeBy(30_000)
        tips shouldBe 0
        tapMoon(1)
        compose.mainClock.advanceTimeBy(30_000)
        tips shouldBe 1
    }
}
