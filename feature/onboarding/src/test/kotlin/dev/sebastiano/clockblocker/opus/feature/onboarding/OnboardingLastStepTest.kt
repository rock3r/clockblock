package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan as intShouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The last step's top bar: nothing left to skip, so Skip slides out and fades while the progress line takes its room,
 * and a little plane taxis along it to a third of the way, fading out on Back and taxiing in again on return.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w411dp-h891dp-xxhdpi")
class OnboardingLastStepTest : ClockblockScreenshotTest() {
    private var step by mutableStateOf(OnboardingStep.Tools)
    private val calls = mutableListOf<String>()
    private lateinit var backDispatcher: OnBackPressedDispatcher

    private val actions = object : OnboardingActions by OnboardingActions.None {
        override fun skip() { calls += "skip" }
        override fun takeoff() { calls += "takeoff" }
        override fun finish() { calls += "finish" }
        override fun takeoffLanded() { calls += "landed" }
        override fun back() { calls += "back"; step = OnboardingStep.entries[step.ordinal - 1] }
    }

    private fun show(start: OnboardingStep, reduceMotion: Boolean = false) {
        step = start
        setContent(reduceMotion = reduceMotion) {
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            // The host's own Back underneath (NavDisplay popping the flow), registered before the steps' handler.
            remember {
                backDispatcher.addCallback(object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() { calls += "hostBack" }
                })
            }
            OnboardingContent(onboardingState(step), actions, Modifier.fillMaxSize())
        }
        compose.mainClock.advanceTimeBy(Settled)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun go(target: OnboardingStep) {
        step = target
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun progressBounds(): DpRect = compose.onNodeWithTag(OnboardingTags.Progress, useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    fun `on the last step the progress line takes skip's room and skip takes no taps`() {
        show(OnboardingStep.Tools)
        val before = progressBounds()
        val skip = compose.onNodeWithTag(OnboardingTags.Skip).getUnclippedBoundsInRoot()
        go(OnboardingStep.Reminders)
        compose.mainClock.advanceTimeBy(Settled)
        val after = progressBounds()
        after.right shouldBeGreaterThan before.right
        after.left shouldBe before.left
        // Skip is out of the tree, and a tap where it was does nothing.
        compose.onNodeWithTag(OnboardingTags.Skip).assertHasNoClickAction()
        compose.onRoot().performTouchInput {
            click(Offset(((skip.left + skip.right) / 2).toPx(), ((skip.top + skip.bottom) / 2).toPx()))
        }
        compose.mainClock.advanceTimeBy(100)
        calls shouldContainExactly emptyList()
    }

    @Test
    fun `skip slides out and fades instead of vanishing`() {
        show(OnboardingStep.Tools)
        val bounds = compose.onNodeWithTag(OnboardingTags.Skip).getUnclippedBoundsInRoot()
        // Its label's band only: the growing line runs through the lower part of where Skip was.
        val line = progressBounds()
        val skip = DpRect(bounds.left, bounds.top, bounds.right, line.top + 9.dp)
        val atRest = inkIn(skip)
        go(OnboardingStep.Reminders)
        compose.mainClock.advanceTimeBy(100)
        // Partway: still showing, fading.
        val partway = inkIn(skip)
        partway shouldBeGreaterThan 0.05f
        (partway < atRest) shouldBe true
        compose.mainClock.advanceTimeBy(Settled)
        (inkIn(skip) < 0.02f) shouldBe true
    }

    @Test
    fun `the plane taxis to a third of the way, fades out on back, and taxis in again`() {
        show(OnboardingStep.Tools)
        go(OnboardingStep.Reminders)
        compose.mainClock.advanceTimeBy(150)
        val early = planeCentre()
        compose.mainClock.advanceTimeBy(Settled)
        val parked = planeCentre()
        val line = progressBounds()
        val third = with(compose.density) { (line.left + (line.right - line.left) / 3).toPx() }
        requireNotNull(early)
        requireNotNull(parked)
        // It rolled towards its holding point and stopped around a third of the way along.
        parked intShouldBeGreaterThan early
        (abs(parked - third) < with(compose.density) { 16f * density }) shouldBe true

        go(OnboardingStep.Tools)
        compose.mainClock.advanceTimeBy(150)
        // Fading out where it was, not taxiing back.
        val fading = planeCentre()
        if (fading != null) (abs(fading - parked) < 6) shouldBe true
        compose.mainClock.advanceTimeBy(Settled)
        planeCentre() shouldBe null

        go(OnboardingStep.Reminders)
        compose.mainClock.advanceTimeBy(150)
        val again = requireNotNull(planeCentre())
        (again < parked) shouldBe true
    }

    @Test
    fun `finishing takes off and saves at once, nothing takes a tap meanwhile, and it lands after the take-off`() {
        show(OnboardingStep.Reminders)
        compose.onNodeWithTag(OnboardingTags.MaybeLater).performClick()
        compose.mainClock.advanceTimeByFrame()
        calls shouldContainExactly listOf("takeoff", "finish")
        // Locked while it leaves: a second tap or Back does nothing, and Back doesn't reach the host either (it would
        // pop the flow mid take-off).
        compose.onNodeWithTag(OnboardingTags.MaybeLater).assertDoesNotExist()
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(150)
        calls shouldContainExactly listOf("takeoff", "finish")
        compose.mainClock.advanceTimeBy(400)
        calls shouldContainExactly listOf("takeoff", "finish", "landed")
    }

    @Test
    fun `with motion reduced, finishing saves straight away, without a take-off`() {
        show(OnboardingStep.Reminders, reduceMotion = true)
        compose.onNodeWithTag(OnboardingTags.MaybeLater).performClick()
        compose.mainClock.advanceTimeByFrame()
        calls shouldContainExactly listOf("finish")
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        calls shouldContainExactly listOf("finish")
    }

    /** The plane's horizontal centre: the middle of the ink in the band just above the progress line, or null. */
    private fun planeCentre(): Int? {
        val map = compose.onRoot().captureToImage().toPixelMap()
        val line = progressBounds()
        val (top, bottom, left, right) = with(compose.density) {
            // The plane sits on the line in the indicator's middle; the label is above the indicator's top.
            listOf((line.top.toPx() + 4f * density).toInt(), (line.top.toPx() + 10f * density).toInt(), line.left.toPx().toInt(), line.right.toPx().toInt())
        }
        val bg = map[2, map.height / 2]
        val xs = mutableListOf<Int>()
        for (x in left until right) for (y in top.coerceAtLeast(0) until bottom) {
            if (distance(map[x, y], bg) > 0.3f) { xs += x; break }
        }
        return if (xs.size < 8) null else (xs.min() + xs.max()) / 2
    }

    private fun inkIn(bounds: DpRect): Float {
        val map = compose.onRoot().captureToImage().toPixelMap()
        val bg = map[2, map.height / 2]
        val (l, t, r, b) = with(compose.density) { listOf(bounds.left.toPx(), bounds.top.toPx(), bounds.right.toPx(), bounds.bottom.toPx()).map { it.toInt() } }
        var ink = 0f
        for (x in l.coerceAtLeast(0) until r.coerceAtMost(map.width)) for (y in t.coerceAtLeast(0) until b.coerceAtMost(map.height)) {
            ink = maxOf(ink, distance(map[x, y], bg))
        }
        return ink
    }

    private fun distance(a: Color, b: Color): Float = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    private companion object {
        const val Settled = 3_000L
    }
}
