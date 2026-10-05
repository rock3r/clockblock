package dev.sebastiano.clockblocker.opus.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme

/**
 * The shell's screen transitions, all bound to [OpusMotion] navigation tokens (never NavDisplay's 700 ms default):
 *
 * - **Top-level** switches (navigation suite, back to home): fade-through — the outgoing screen fades out in
 *   90 ms, then the incoming one fades in while scaling 92 % → 100 %.
 * - **Hierarchy** push/pop: shared-axis X — a 30 dp slide (mirrored in RTL) with the same sequenced fades.
 * - **Predictive back**: the outgoing screen shrinks to 90 % and drifts back toward the edge it entered from
 *   while fading, revealing the destination underneath; it is seeked by the gesture.
 *
 * Under reduced motion every spatial part snaps and only the opacity changes remain (the static carrier).
 */
@Immutable
class AppTransitions(
    private val motion: OpusMotion,
    private val axisOffsetPx: Int,
    private val layoutDirection: LayoutDirection,
) {
    /**
     * +1 when "forward" travels toward the right (LTR), −1 in RTL. A pushed screen enters from the forward
     * edge (`forwardSign * offset`) and every reverse motion (pop, predictive back, pane hide) returns toward
     * that same edge, so each surface leaves the way it came.
     */
    internal val forwardSign: Int get() = if (layoutDirection == LayoutDirection.Ltr) 1 else -1

    /** The transition for a navigation in [direction]; [isPop] is NavDisplay's own pop detection. */
    fun forNavigation(direction: NavigationDirection, isPop: Boolean): ContentTransform = when (direction) {
        NavigationDirection.TopLevel -> fadeThrough()
        NavigationDirection.Backward -> backward()
        NavigationDirection.Forward -> if (isPop) backward() else forward()
    }

    fun fadeThrough(): ContentTransform =
        (fadeIn(motion.navigationFadeIn()) + scaleIn(motion.navigationSpatial(), initialScale = FadeThroughScale))
            .togetherWith(fadeOut(motion.navigationFadeOut()))

    fun forward(): ContentTransform = sharedAxis(sign = forwardSign)

    fun backward(): ContentTransform = sharedAxis(sign = -forwardSign)

    /**
     * Seeked by the back gesture. The outgoing screen shrinks and drifts back toward the edge it entered from
     * (the forward edge, mirrored in RTL) whichever edge the swipe started on, so the gesture reads as the
     * push played in reverse and matches the button-driven [backward] pop.
     *
     * [swipeEdge] is a `NavigationEvent.EDGE_*` constant; it does not change the direction (see [predictiveBackDrift]).
     */
    @Suppress("UNUSED_PARAMETER")
    fun predictiveBack(swipeEdge: Int): ContentTransform {
        val drift = predictiveBackDrift
        val enter: EnterTransition = slideInHorizontally(motion.navigationSpatial()) { -drift * axisOffsetPx / 2 }
        val exit: ExitTransition = scaleOut(motion.navigationSpatial(), targetScale = PredictiveBackScale) +
            slideOutHorizontally(motion.navigationSpatial()) { drift * axisOffsetPx } +
            fadeOut(motion.colour())
        return enter togetherWith exit
    }

    /** Horizontal sign the outgoing screen drifts toward during predictive back: always the forward edge. */
    internal val predictiveBackDrift: Int get() = forwardSign

    private fun sharedAxis(sign: Int): ContentTransform {
        val enter = slideInHorizontally(motion.navigationSpatial()) { sign * axisOffsetPx } +
            fadeIn(motion.navigationFadeIn())
        val exit = slideOutHorizontally(motion.navigationSpatial()) { -sign * axisOffsetPx } +
            fadeOut(motion.navigationFadeOut())
        return enter togetherWith exit
    }

    /** Pane show/hide inside the list-detail scene: a fade with a short slide from the trailing side. */
    fun paneEnter(): EnterTransition =
        fadeIn(motion.navigationFadeIn()) + slideInHorizontally(motion.navigationSpatial()) { paneSlideSign * axisOffsetPx }

    /** The reverse of [paneEnter]: the pane fades while sliding back toward the trailing side it came from. */
    fun paneExit(): ExitTransition =
        fadeOut(motion.navigationFadeOut()) + slideOutHorizontally(motion.navigationSpatial()) { paneSlideSign * axisOffsetPx }

    /** Panes enter from, and exit toward, the trailing (forward) edge. */
    internal val paneSlideSign: Int get() = forwardSign

    private companion object {
        const val FadeThroughScale = 0.92f
        const val PredictiveBackScale = 0.9f
    }
}

/** [AppTransitions] for the current theme motion, density and layout direction. */
@Composable
fun rememberAppTransitions(): AppTransitions {
    val motion = OpusTheme.motion
    val layoutDirection = LocalLayoutDirection.current
    val offset = with(LocalDensity.current) { SharedAxisOffset.roundToPx() }
    return remember(motion, offset, layoutDirection) { AppTransitions(motion, offset, layoutDirection) }
}

/** Material shared-axis travel distance. */
private val SharedAxisOffset = 30.dp
