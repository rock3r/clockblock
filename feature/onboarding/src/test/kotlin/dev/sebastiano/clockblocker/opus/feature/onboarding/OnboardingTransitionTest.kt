package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.testing.captureRoboImageInvalidated
import dev.sebastiano.clockblocker.opus.core.testing.invalidateAllViews
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Welcome ↔ the first question, frame by frame with motion on: nothing on either step moves vertically because of
 * the top bar or the disclaimer appearing (the old jump), and Get started shrinks into Next while Back comes in
 * from the start edge.
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
        calls.clear()
        setContent(reduceMotion = reduceMotion, fontScale = fontScale) {
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                OnboardingContent(onboardingState(step), actions, Modifier.fillMaxSize())
            }
        }
        compose.mainClock.advanceTimeBy(Settled)
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
        startBackGesture()
        progressBackGesture(0.5f)
        val seeking = bounds(welcomeHeadline)
        commitBackGesture()
        calls shouldContainExactly listOf("back")
        compose.onNodeWithText(homeHeadline).assertDoesNotExist()
        bounds(welcomeHeadline).verticalSpan() shouldBe seeking.verticalSpan()
    }

    @Test
    fun `get started shrinks into next and back enters from the start edge`() {
        show(OnboardingStep.Welcome)
        val started = boundsOf(OnboardingTags.GetStarted)
        go(OnboardingStep.HomeZone)
        frame(EarlyTransition)
        val early = boundsOf(OnboardingTags.Next)
        val backEarly = boundsOf(OnboardingTags.Back)
        frame(MidTransition - EarlyTransition)
        val mid = boundsOf(OnboardingTags.Next)
        val backMid = boundsOf(OnboardingTags.Back)
        frame(Settled)
        val next = boundsOf(OnboardingTags.Next)
        val back = boundsOf(OnboardingTags.Back)

        // One button, one row: the same height and baseline throughout.
        listOf(started, early, mid, next).map { it.top }.distinct().size shouldBe 1
        listOf(started, early, mid, next).map { it.bottom }.distinct().size shouldBe 1
        // The width shrinks through the frames from the whole row to Next's slot at the end.
        started.width() shouldBeGreaterThan early.width()
        early.width() shouldBeGreaterThan mid.width()
        mid.width() shouldBeGreaterThan next.width()
        next.right.value shouldBe started.right.value.plusOrMinus(0.5f)
        // Back enters from off the start edge as the primary shrinks.
        backEarly.left shouldBeLessThan backMid.left
        backMid.left shouldBeLessThan back.left
        back.left.value shouldBe started.left.value.plusOrMinus(0.5f)
    }

    @Test
    fun `going forward and back repeatedly, next grows back into get started and back leaves off the start edge every time`() {
        show(OnboardingStep.Welcome)
        repeat(2) {
            val startedBefore = boundsOf(OnboardingTags.GetStarted)
            go(OnboardingStep.HomeZone)
            frame(EarlyTransition)
            val forwardEarly = boundsOf(OnboardingTags.Next)
            val backForwardEarly = boundsOf(OnboardingTags.Back)
            frame(MidTransition - EarlyTransition)
            val forwardMid = boundsOf(OnboardingTags.Next)
            val backForwardMid = boundsOf(OnboardingTags.Back)
            frame(Settled)
            val next = boundsOf(OnboardingTags.Next)
            val back = boundsOf(OnboardingTags.Back)

            startedBefore.width() shouldBeGreaterThan forwardEarly.width()
            forwardEarly.width() shouldBeGreaterThan forwardMid.width()
            forwardMid.width() shouldBeGreaterThan next.width()
            backForwardEarly.left shouldBeLessThan backForwardMid.left
            backForwardMid.left shouldBeLessThan back.left

            go(OnboardingStep.Welcome)
            frame(BackMidTransition)
            val backEarly = boundsOf(OnboardingTags.GetStarted)
            val backBtnEarly = boundsOf(OnboardingTags.Back)
            frame(MidTransition - BackMidTransition)
            val backMid = boundsOf(OnboardingTags.GetStarted)
            val backBtnMid = boundsOf(OnboardingTags.Back)
            frame(Settled)
            val startedAfter = boundsOf(OnboardingTags.GetStarted)

            backEarly.width() shouldBeGreaterThan next.width()
            backMid.width() shouldBeGreaterThan backEarly.width()
            startedAfter.width() shouldBeGreaterThan backMid.width()
            // Back leaves towards the start edge, the way it came, as the button takes its room back.
            backBtnEarly.left shouldBeLessThan back.left
            backBtnMid.left shouldBeLessThan backBtnEarly.left
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun `on a wide window get started is centred and back still enters and leaves from the start edge`() {
        show(OnboardingStep.Welcome)
        val started = boundsOf(OnboardingTags.GetStarted)
        go(OnboardingStep.HomeZone)
        frame(MidTransition)
        val forwardMid = boundsOf(OnboardingTags.Next)
        val backForwardMid = boundsOf(OnboardingTags.Back)
        frame(Settled)
        val next = boundsOf(OnboardingTags.Next)
        val back = boundsOf(OnboardingTags.Back)

        // Centred on Welcome, at the trailing edge on step 2; Back enters from the start edge, not from the centre.
        started.left shouldBeGreaterThan back.left
        started.right shouldBeLessThan next.right
        started.width() shouldBeGreaterThan forwardMid.width()
        forwardMid.width() shouldBeGreaterThan next.width()
        backForwardMid.left shouldBeLessThan back.left

        go(OnboardingStep.Welcome)
        frame(MidTransition)
        val backMid = boundsOf(OnboardingTags.GetStarted)
        val backBtnMid = boundsOf(OnboardingTags.Back)
        frame(Settled)
        backMid.width() shouldBeGreaterThan next.width()
        backBtnMid.left shouldBeLessThan back.left
    }

    @Test
    fun `in rtl back enters and leaves from the right edge as the button morphs`() {
        show(OnboardingStep.Welcome, layoutDirection = LayoutDirection.Rtl)
        val started = boundsOf(OnboardingTags.GetStarted)
        go(OnboardingStep.HomeZone)
        frame(MidTransition)
        val forwardMid = boundsOf(OnboardingTags.Next)
        val backForwardMid = boundsOf(OnboardingTags.Back)
        frame(Settled)
        val next = boundsOf(OnboardingTags.Next)
        val back = boundsOf(OnboardingTags.Back)

        started.width() shouldBeGreaterThan forwardMid.width()
        forwardMid.width() shouldBeGreaterThan next.width()
        backForwardMid.right shouldBeGreaterThan back.right

        go(OnboardingStep.Welcome)
        frame(MidTransition)
        val backMid = boundsOf(OnboardingTags.GetStarted)
        val backBtnMid = boundsOf(OnboardingTags.Back)
        frame(Settled)
        backMid.width() shouldBeGreaterThan next.width()
        backBtnMid.right shouldBeGreaterThan back.right
    }

    @Test
    fun `the top bar never shows welcome's step and keeps step 2 of 5 while returning to welcome`() {
        for (step in OnboardingStep.entries) {
            for (current in OnboardingStep.entries) {
                topBarStep(step, current) shouldNotBe OnboardingStep.Welcome
            }
        }
        topBarStep(OnboardingStep.Welcome, OnboardingStep.Welcome) shouldBe OnboardingStep.HomeZone
        topBarStep(OnboardingStep.HomeZone, OnboardingStep.Welcome) shouldBe OnboardingStep.HomeZone
        topBarStep(OnboardingStep.Welcome, OnboardingStep.HomeZone) shouldBe OnboardingStep.HomeZone
        topBarStep(OnboardingStep.Sleep, OnboardingStep.HomeZone) shouldBe OnboardingStep.Sleep
        topBarStep(OnboardingStep.HomeZone, OnboardingStep.Sleep) shouldBe OnboardingStep.HomeZone
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
        // An immediate cancel before the previous step has laid out must not wedge later back gestures.
        compose.runOnUiThread {
            backDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
            backDispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 900f, 0.2f, BackEventCompat.EDGE_LEFT))
            backDispatcher.dispatchOnBackCancelled()
        }
        Snapshot.sendApplyNotifications()
        frame(Settled)
        compose.waitForIdle()
        boundsOf(OnboardingTags.Next) shouldBe next

        startBackGesture()
        progressBackGesture(0.5f)
        val seeking = boundsOf(OnboardingTags.Next)
        seeking.width() shouldBeGreaterThan next.width()
        cancelBackGesture()
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
        frame(StaggerMidTransition - EarlyTransition)
        compose.captureRoboImageInvalidated("src/test/screenshots/${prefix}_2_mid.png")
        frame(Settled)
        compose.captureRoboImageInvalidated("src/test/screenshots/${prefix}_3_end.png")
        // Going back: Next grows back into Get started, Back slides off the start edge, and the top bar keeps "Step 2
        // of 5" while fading out in place.
        go(OnboardingStep.Welcome)
        frame(BackMidTransition)
        compose.captureRoboImageInvalidated("src/test/screenshots/${prefix}_4_back_mid.png")
        frame(Settled)
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

    @Test
    fun `predictive back from home to welcome scrubs without double exposure`() =
        assertPredictiveBackPair(
            from = OnboardingStep.HomeZone,
            to = OnboardingStep.Welcome,
            goldenPrefix = "onboarding_predictive_back_home_to_welcome",
        )

    @Test
    fun `predictive back from sleep to home scrubs without double exposure`() =
        assertPredictiveBackPair(
            from = OnboardingStep.Sleep,
            to = OnboardingStep.HomeZone,
            goldenPrefix = "onboarding_predictive_back_sleep_to_home",
        )

    @Test
    fun `predictive back from chronotype to sleep scrubs without double exposure`() =
        assertPredictiveBackPair(
            from = OnboardingStep.Chronotype,
            to = OnboardingStep.Sleep,
            goldenPrefix = "onboarding_predictive_back_chronotype_to_sleep",
        )

    private fun assertPredictiveBackPair(
        from: OnboardingStep,
        to: OnboardingStep,
        goldenPrefix: String,
    ) {
        show(from)
        val fromAtRest = captureRootPixels()
        val initialPrimaryWidth = primaryButtonBounds().width()

        startBackGesture()
        val fractions = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
        val widths = mutableListOf<Dp>()
        val captures = fractions.map { fraction ->
            progressBackGesture(fraction)
            compose.captureRoboImageInvalidated("src/test/screenshots/${goldenPrefix}_${(fraction * 10).roundToInt()}.png")
            widths += primaryButtonBounds().width()
            captureRootPixels()
        }

        // Cancel returns to the current step.
        cancelBackGesture()
        calls shouldContainExactly emptyList()
        compose.onNodeWithTag(OnboardingTags.step(from)).assertIsDisplayed()
        compose.onNodeWithTag(OnboardingTags.step(to)).assertDoesNotExist()

        // Commit continues to the previous step.
        startBackGesture()
        progressBackGesture(0.5f)
        commitBackGesture()
        calls shouldContainExactly listOf("back")
        compose.onNodeWithTag(OnboardingTags.step(to)).assertIsDisplayed()
        compose.onNodeWithTag(OnboardingTags.step(from)).assertDoesNotExist()
        val toAtRest = captureRootPixels()
        val settledPrimaryWidth = primaryButtonBounds().width()

        // Captures at different fractions must differ.
        for (i in captures.indices) {
            for (j in i + 1 until captures.size) {
                diffPixels(captures[i], captures[j]) shouldBeGreaterThan 0
            }
        }

        if (to == OnboardingStep.Welcome) {
            val totalWidthRange = settledPrimaryWidth - initialPrimaryWidth
            val minWidthStep = totalWidthRange * 0.10f
            widths.first() shouldBeGreaterThan initialPrimaryWidth
            widths.last() shouldBeLessThan settledPrimaryWidth
            for (i in 0 until widths.lastIndex) {
                (widths[i + 1] - widths[i]) shouldBeGreaterThan minWidthStep
            }
        }

        // Pixel-sample regions unique to each step's content (outside the top and bottom chrome, guarded against the
        // 30 dp shared-axis slide) and assert at most one step has ink at every fraction, and that content alpha
        // progresses monotonically by at least 10 % of the full range across every fraction step.
        val density = compose.density.density
        val topPx = (72 * density).roundToInt()
        val bottomPx = (750 * density).roundToInt().coerceAtMost(fromAtRest.height)
        val slideMarginPx = (34 * density).roundToInt()
        val sideArtRightPx = (100 * density).roundToInt()
        val sideArtBottomPx = (155 * density).roundToInt()
        val bg = fromAtRest[0, 0]
        val fromUnique = uniqueContentPixels(fromAtRest, toAtRest, bg, topPx, bottomPx, slideMarginPx, sideArtRightPx, sideArtBottomPx)
        val toUnique = uniqueContentPixels(toAtRest, fromAtRest, bg, topPx, bottomPx, slideMarginPx, sideArtRightPx, sideArtBottomPx)
        fromUnique.size shouldBeGreaterThan 200
        toUnique.size shouldBeGreaterThan 200
        val staticTopPx = (300 * density).roundToInt()
        val toRestInkSum = sumContentInk(toAtRest, bg, staticTopPx, bottomPx)

        val incomingAlphas = captures.mapIndexed { index, frame ->
            val fromInk = countInk(frame, fromUnique, bg)
            val toInk = countInk(frame, toUnique, bg)
            val fromVisible = fromInk > InkThreshold
            val toVisible = toInk > InkThreshold
            (fromVisible && toVisible) shouldBe false
            if (index == 0) {
                fromVisible shouldBe true
                0f
            } else {
                toVisible shouldBe true
                sumContentInk(frame, bg, staticTopPx, bottomPx) / toRestInkSum
            }
        }
        incomingAlphas.last() shouldBeLessThan 0.99f
        for (i in 0 until incomingAlphas.lastIndex) {
            (incomingAlphas[i + 1] - incomingAlphas[i]) shouldBeGreaterThan 0.10f
        }
    }

    private fun primaryButtonBounds(): DpRect =
        if (compose.onAllNodesWithTag(OnboardingTags.Next, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            boundsOf(OnboardingTags.Next)
        } else {
            boundsOf(OnboardingTags.GetStarted)
        }

    private fun sumContentInk(
        map: PixelMap,
        bg: Color,
        topPx: Int,
        bottomPx: Int,
    ): Float {
        var sum = 0f
        for (y in topPx until bottomPx step 3) {
            for (x in 0 until map.width step 3) {
                sum += colorDistance(map[x, y], bg)
            }
        }
        return sum
    }

    private fun captureRootPixels(): PixelMap {
        compose.invalidateAllViews()
        return compose.onRoot().captureToImage().toPixelMap()
    }

    private fun diffPixels(a: PixelMap, b: PixelMap): Int {
        var count = 0
        for (y in 0 until a.height step 2) {
            for (x in 0 until a.width step 2) {
                if (a[x, y] != b[x, y]) count++
            }
        }
        return count
    }

    private fun uniqueContentPixels(
        primary: PixelMap,
        other: PixelMap,
        bg: Color,
        topPx: Int,
        bottomPx: Int,
        slideMarginPx: Int,
        sideArtRightPx: Int,
        sideArtBottomPx: Int,
    ): List<Pair<Int, Int>> {
        val points = mutableListOf<Pair<Int, Int>>()
        for (y in topPx until bottomPx step 3) {
            for (x in slideMarginPx until (primary.width - slideMarginPx) step 3) {
                if (x < sideArtRightPx && y < sideArtBottomPx) continue
                if (colorDistance(primary[x, y], bg) > 0.08f) {
                    var clearInOther = true
                    for (dx in -slideMarginPx..slideMarginPx) {
                        if (colorDistance(other[x + dx, y], bg) > 0.01f) {
                            clearInOther = false
                            break
                        }
                    }
                    if (clearInOther) points += x to y
                }
            }
        }
        return points
    }

    private fun countInk(map: PixelMap, points: List<Pair<Int, Int>>, bg: Color): Int =
        points.count { (x, y) -> colorDistance(map[x, y], bg) > 0.02f }

    private fun colorDistance(a: Color, b: Color): Float =
        abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    private fun startBackGesture() {
        compose.runOnUiThread {
            backDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
        }
        Snapshot.sendApplyNotifications()
        frame(FirstFrame)
        frame(FirstFrame)
        compose.waitForIdle()
    }

    private fun progressBackGesture(progress: Float) {
        compose.runOnUiThread {
            backDispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 900f, progress, BackEventCompat.EDGE_LEFT))
        }
        Snapshot.sendApplyNotifications()
        frame(FirstFrame)
        frame(FirstFrame)
        compose.waitForIdle()
    }

    private fun cancelBackGesture() {
        compose.runOnUiThread { backDispatcher.dispatchOnBackCancelled() }
        Snapshot.sendApplyNotifications()
        frame(FirstFrame)
        frame(FirstFrame)
        frame(Settled)
        frame(FirstFrame)
        frame(FirstFrame)
        compose.waitForIdle()
    }

    private fun commitBackGesture() {
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        Snapshot.sendApplyNotifications()
        frame(FirstFrame)
        frame(FirstFrame)
        frame(Settled)
        frame(FirstFrame)
        frame(FirstFrame)
        compose.waitForIdle()
    }

    private fun DpRect.verticalSpan(): Pair<Dp, Dp> = top to bottom
    private fun DpRect.width(): Dp = right - left

    private companion object {
        const val FirstFrame = 16L
        const val BackMidTransition = 64L
        const val EarlyTransition = 64L
        const val MidTransition = 120L
        const val StaggerMidTransition = 160L
        const val Settled = 3_000L
        const val InkThreshold = 5
    }
}
