package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.testing.captureRoboImageInvalidated
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Welcome ↔ the first question, frame by frame with motion on: nothing on either step moves vertically because of
 * the top bar or the disclaimer appearing (the old jump), and Get started shrinks into Next while Back comes out
 * from behind it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w411dp-h891dp-xxhdpi")
class OnboardingTransitionTest : ClockblockScreenshotTest() {
    private var step by mutableStateOf(OnboardingStep.Welcome)
    private val calls = mutableListOf<String>()
    private lateinit var backDispatcher: OnBackPressedDispatcher

    private val actions = object : OnboardingActions by OnboardingActions.None {
        override fun next() {
            calls += "next"
            step = OnboardingStep.entries[step.ordinal + 1]
        }
        override fun back() {
            calls += "back"
            step = OnboardingStep.entries[step.ordinal - 1]
        }
    }

    private fun show(start: OnboardingStep, reduceMotion: Boolean = false) {
        step = start
        setContent(reduceMotion = reduceMotion) {
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            OnboardingContent(onboardingState(step), actions, Modifier.fillMaxSize())
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun frame(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
    }

    /** Moves to [target] and runs the first frames: the new step is composed and the transition has started. */
    private fun go(target: OnboardingStep) {
        step = target
        Snapshot.sendApplyNotifications()
        frame(FirstFrame)
        frame(FirstFrame)
    }

    private fun bounds(text: String): DpRect = compose.onNodeWithText(text).getUnclippedBoundsInRoot()
    private fun boundsOf(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private val welcomeHeadline = "Jet lag is two clocks disagreeing."
    private val homeHeadline = "Where's home?"

    @Test
    fun `going forward, the outgoing welcome and the incoming step never move vertically`() {
        show(OnboardingStep.Welcome)
        val welcomeAtRest = bounds(welcomeHeadline)
        go(OnboardingStep.HomeZone)
        bounds(welcomeHeadline).verticalSpan() shouldBe welcomeAtRest.verticalSpan()
        val homeEarly = bounds(homeHeadline)
        frame(MidTransition)
        bounds(welcomeHeadline).verticalSpan() shouldBe welcomeAtRest.verticalSpan()
        bounds(homeHeadline).verticalSpan() shouldBe homeEarly.verticalSpan()
        frame(Settled)
        compose.onNodeWithText(welcomeHeadline).assertDoesNotExist()
        bounds(homeHeadline).verticalSpan() shouldBe homeEarly.verticalSpan()
    }

    @Test
    fun `going back, the incoming welcome lands where it started and never jumps at the end`() {
        show(OnboardingStep.HomeZone)
        val homeAtRest = bounds(homeHeadline)
        go(OnboardingStep.Welcome)
        val welcomeEarly = bounds(welcomeHeadline)
        bounds(homeHeadline).verticalSpan() shouldBe homeAtRest.verticalSpan()
        frame(MidTransition)
        bounds(welcomeHeadline).verticalSpan() shouldBe welcomeEarly.verticalSpan()
        frame(Settled)
        compose.onNodeWithText(homeHeadline).assertDoesNotExist()
        bounds(welcomeHeadline).verticalSpan() shouldBe welcomeEarly.verticalSpan()
    }

    @Test
    fun `under the predictive back gesture welcome already sits where it lands`() {
        show(OnboardingStep.HomeZone)
        compose.mainClock.autoAdvance = true
        backGesture(0.5f)
        compose.waitForIdle()
        val seeking = bounds(welcomeHeadline)
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(Settled)
        compose.waitForIdle()
        calls shouldContainExactly listOf("back")
        compose.onNodeWithText(homeHeadline).assertDoesNotExist()
        bounds(welcomeHeadline).verticalSpan() shouldBe seeking.verticalSpan()
    }

    @Test
    fun `get started shrinks into next and back comes out from behind it`() {
        show(OnboardingStep.Welcome)
        val started = boundsOf(OnboardingTags.GetStarted)
        go(OnboardingStep.HomeZone)
        frame(MidTransition)
        val mid = boundsOf(OnboardingTags.Next)
        val backMid = boundsOf(OnboardingTags.Back)
        frame(Settled)
        val next = boundsOf(OnboardingTags.Next)
        val back = boundsOf(OnboardingTags.Back)

        // One button, one row: the same height and baseline throughout.
        listOf(started, mid, next).map { it.top }.distinct().size shouldBe 1
        listOf(started, mid, next).map { it.bottom }.distinct().size shouldBe 1
        // The width shrinks through the frames from the whole row to Next's slot at the end.
        started.width() shouldBeGreaterThan mid.width()
        mid.width() shouldBeGreaterThan next.width()
        next.right.value shouldBe started.right.value.plusOrMinus(0.5f)
        // Back slides from under the button's leading edge to the start of the row.
        backMid.left shouldBeGreaterThan back.left
        back.left.value shouldBe started.left.value.plusOrMinus(0.5f)
    }

    @Test
    fun `under reduce motion the button snaps to next straight away`() {
        show(OnboardingStep.Welcome, reduceMotion = true)
        go(OnboardingStep.HomeZone)
        val early = boundsOf(OnboardingTags.Next)
        frame(Settled)
        early shouldBe boundsOf(OnboardingTags.Next)
    }

    @Test
    fun `predictive back seeks next back towards get started`() {
        show(OnboardingStep.HomeZone)
        val next = boundsOf(OnboardingTags.Next)
        compose.mainClock.autoAdvance = true
        backGesture(0.5f)
        compose.waitForIdle()
        val seeking = boundsOf(OnboardingTags.Next)
        seeking.width() shouldBeGreaterThan next.width()
        compose.runOnUiThread { backDispatcher.dispatchOnBackCancelled() }
        compose.mainClock.advanceTimeBy(Settled)
        compose.waitForIdle()
        boundsOf(OnboardingTags.Next) shouldBe next
    }

    @Test
    fun morphFrames() {
        show(OnboardingStep.Welcome)
        compose.captureRoboImageInvalidated("src/test/screenshots/onboarding_morph_1_start.png")
        go(OnboardingStep.HomeZone)
        frame(MidTransition)
        compose.captureRoboImageInvalidated("src/test/screenshots/onboarding_morph_2_mid.png")
        frame(Settled)
        compose.captureRoboImageInvalidated("src/test/screenshots/onboarding_morph_3_end.png")
    }

    private fun backGesture(progress: Float) = compose.runOnUiThread {
        backDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
        backDispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 900f, progress, BackEventCompat.EDGE_LEFT))
    }

    private fun DpRect.verticalSpan(): Pair<Dp, Dp> = top to bottom
    private fun DpRect.width(): Dp = right - left

    private companion object {
        const val FirstFrame = 16L
        const val MidTransition = 120L
        const val Settled = 3_000L
    }
}
