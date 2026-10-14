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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.testing.captureRoboImageInvalidated
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
    private var direction by mutableStateOf(LayoutDirection.Ltr)
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

    private fun show(
        start: OnboardingStep,
        reduceMotion: Boolean = false,
        fontScale: Float? = null,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        step = start
        direction = layoutDirection
        setContent(reduceMotion = reduceMotion, fontScale = fontScale) {
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                OnboardingContent(onboardingState(step), actions, Modifier.fillMaxSize())
            }
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
    fun `the morph starts only once the incoming step is composed and laid out`() {
        show(OnboardingStep.Welcome)
        val started = boundsOf(OnboardingTags.GetStarted)
        step = OnboardingStep.HomeZone
        Snapshot.sendApplyNotifications()
        // The frame that composes the home step for the first time can take hundreds of ms on a phone. An animation
        // whose clock already runs in that frame jumps over its opening (the first-run cut), so the clock starts on
        // the frame after: one frame later, nothing has moved yet.
        while (compose.onAllNodesWithTag(OnboardingTags.step(OnboardingStep.HomeZone)).fetchSemanticsNodes().isEmpty()) frame(FirstFrame)
        frame(FirstFrame)
        boundsOf(OnboardingTags.Next).width() shouldBe started.width()
        frame(FirstFrame)
        boundsOf(OnboardingTags.Next).width() shouldBeLessThan started.width()
        frame(Settled)
        (boundsOf(OnboardingTags.Next).width() < started.width() * 0.6f) shouldBe true
    }

    @Test
    fun `after the transition is rebuilt (here by a layout direction change), returning to a step still waits for it`() {
        show(OnboardingStep.HomeZone)
        // Each flip rebuilds the step transition while the home step stays on screen.
        direction = LayoutDirection.Rtl
        frame(Settled)
        direction = LayoutDirection.Ltr
        frame(Settled)
        step = OnboardingStep.Sleep
        Snapshot.sendApplyNotifications()
        frame(Settled)
        compose.onNodeWithText(homeHeadline).assertDoesNotExist()
        step = OnboardingStep.HomeZone
        Snapshot.sendApplyNotifications()
        while (compose.onAllNodesWithText(homeHeadline).fetchSemanticsNodes().isEmpty()) frame(FirstFrame)
        val composed = bounds(homeHeadline)
        frame(FirstFrame)
        // Still where it was composed: the slide in hasn't started on the frame after.
        bounds(homeHeadline).left shouldBe composed.left
        frame(FirstFrame)
        bounds(homeHeadline).left shouldNotBe composed.left
    }

    @Test
    fun morphFrames() = morphFrames("onboarding_morph")

    @Test
    fun morphFramesRtl() = morphFrames("onboarding_morph_rtl", LayoutDirection.Rtl)

    private fun morphFrames(prefix: String, layoutDirection: LayoutDirection = LayoutDirection.Ltr) {
        show(OnboardingStep.Welcome, layoutDirection = layoutDirection)
        compose.captureRoboImageInvalidated("src/test/screenshots/${prefix}_1_start.png")
        go(OnboardingStep.HomeZone)
        // Get started fading out as the button shrinks; Next comes in only once it has gone (fade-through).
        frame(EarlyTransition)
        compose.captureRoboImageInvalidated("src/test/screenshots/${prefix}_2_early.png")
        frame(MidTransition - EarlyTransition)
        compose.captureRoboImageInvalidated("src/test/screenshots/${prefix}_2_mid.png")
        frame(Settled)
        compose.captureRoboImageInvalidated("src/test/screenshots/${prefix}_3_end.png")
    }

    @Test
    fun `the back hidden behind get started is off, and comes on with the first question`() {
        show(OnboardingStep.Welcome)
        compose.onNodeWithTag(OnboardingTags.Back, useUnmergedTree = true).assertIsNotEnabled()
        go(OnboardingStep.HomeZone)
        frame(Settled)
        compose.onNodeWithTag(OnboardingTags.Back).assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun `on a narrow window at twice the font back and next never overlap`() {
        show(OnboardingStep.HomeZone, fontScale = 2f)
        val back = boundsOf(OnboardingTags.Back)
        val next = boundsOf(OnboardingTags.Next)
        (back.right <= next.left) shouldBe true
        compose.captureRoboImageInvalidated("src/test/screenshots/onboarding_2_home_narrow_fontscale_2.png")
    }

    private fun backGesture(progress: Float) = compose.runOnUiThread {
        backDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
        backDispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 900f, progress, BackEventCompat.EDGE_LEFT))
    }

    private fun DpRect.verticalSpan(): Pair<Dp, Dp> = top to bottom
    private fun DpRect.width(): Dp = right - left

    private companion object {
        const val FirstFrame = 16L
        const val EarlyTransition = 88L
        const val MidTransition = 120L
        const val Settled = 3_000L
    }
}
