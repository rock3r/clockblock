package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The step-to-step motion of onboarding, on the same navigation tokens as the app shell's hierarchy
 * transitions (`AppTransitions` in `:app`), so moving through setup feels like moving through the app:
 *
 * - **Next / Back buttons**: shared axis X, a 30 dp slide mirrored in RTL, on `navigationSpatial()` (Standard,
 *   no bounce, snaps under reduced motion) with the sequenced `navigationFadeOut()` → `navigationFadeIn()`.
 *   A step leaves toward the edge the next one comes from, and Back plays exactly the reverse.
 * - **Predictive back**: the gesture *seeks* the transition to the previous step: the current step shrinks to
 *   90 % and drifts back toward the forward edge it entered from (whichever edge the swipe started on) while
 *   fading on `colour()`, revealing the previous step underneath. Committing continues from where the finger
 *   let go; cancelling returns along the same path on `navigationSpatial()`.
 */
@Stable
internal class OnboardingStepTransition(
    private val state: SeekableTransitionState<OnboardingStep>,
    val transition: Transition<OnboardingStep>,
    private val motion: ClockblockMotion,
    private val axisOffsetPx: Int,
    private val layoutDirection: LayoutDirection,
) {
    /** True while a back gesture drives (or has just committed) the transition. */
    internal var gestureBack: Boolean by mutableStateOf(false)

    private val forwardSign: Int get() = if (layoutDirection == LayoutDirection.Ltr) 1 else -1

    /** The [ContentTransform] for a step change from [from] to [to]. */
    fun transitionFor(from: OnboardingStep, to: OnboardingStep): ContentTransform {
        val forward = to.ordinal > from.ordinal
        return if (!forward && gestureBack) predictiveBack() else sharedAxis(if (forward) forwardSign else -forwardSign)
    }

    private fun sharedAxis(sign: Int): ContentTransform {
        val enter = slideInHorizontally(motion.navigationSpatial()) { sign * axisOffsetPx } +
            fadeIn(motion.navigationFadeIn())
        val exit = slideOutHorizontally(motion.navigationSpatial()) { -sign * axisOffsetPx } +
            fadeOut(motion.navigationFadeOut())
        return ContentTransform(enter, exit, sizeTransform = SizeTransform(clip = false))
    }

    private fun predictiveBack(): ContentTransform {
        val enter = slideInHorizontally(motion.navigationSpatial()) { -forwardSign * axisOffsetPx / 2 }
        val exit = scaleOut(motion.navigationSpatial(), targetScale = PredictiveBackScale) +
            slideOutHorizontally(motion.navigationSpatial()) { forwardSign * axisOffsetPx } +
            fadeOut(motion.colour())
        // The outgoing step stays on top so the previous one is revealed underneath it.
        return ContentTransform(enter, exit, targetContentZIndex = -1f, sizeTransform = SizeTransform(clip = false))
    }

    internal suspend fun settleOn(step: OnboardingStep) {
        state.animateTo(step)
        gestureBack = false
    }

    internal suspend fun seekBack(previous: OnboardingStep, fraction: Float) {
        gestureBack = true
        state.seekTo(fraction.coerceIn(0f, 1f), targetState = previous)
    }

    /** Runs the seek back to 0 (where the gesture started) and settles on the current step again. */
    internal suspend fun cancelBack() {
        val current = state.currentState
        val fraction = Animatable(state.fraction)
        coroutineScope {
            val seeking = launch { snapshotFlow { fraction.value }.collect { state.seekTo(it.coerceIn(0f, 1f)) } }
            fraction.animateTo(0f, motion.navigationSpatial())
            seeking.cancelAndJoin()
        }
        state.snapTo(current)
        gestureBack = false
    }

    private companion object {
        const val PredictiveBackScale = 0.9f
    }
}

/**
 * Drives [OnboardingStepTransition] from [step] (the ViewModel's source of truth) and registers the back
 * handler: enabled after the first step, it calls [onBack] on commit.
 */
@Composable
internal fun rememberOnboardingStepTransition(step: OnboardingStep, onBack: () -> Unit): OnboardingStepTransition {
    val motion = ClockblockTheme.motion
    val layoutDirection = LocalLayoutDirection.current
    val axisOffsetPx = with(LocalDensity.current) { SharedAxisOffset.roundToPx() }
    val seekable = remember { SeekableTransitionState(step) }
    val transition = rememberTransition(seekable, label = "onboardingStep")
    val steps = remember(seekable, transition, motion, axisOffsetPx, layoutDirection) {
        OnboardingStepTransition(seekable, transition, motion, axisOffsetPx, layoutDirection)
    }
    // Every step change animates (a committed gesture continues from its current fraction).
    LaunchedEffect(steps, step) { steps.settleOn(step) }

    val previous = OnboardingStep.entries.getOrNull(step.ordinal - 1)
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    LaunchedEffect(steps, backState, previous) {
        if (previous == null) return@LaunchedEffect
        snapshotFlow { (backState.transitionState as? NavigationEventTransitionState.InProgress)?.latestEvent?.progress }
            .filterNotNull()
            .collect { steps.seekBack(previous, it) }
    }
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    NavigationBackHandler(
        state = backState,
        isBackEnabled = previous != null,
        onBackCancelled = { scope.launch { steps.cancelBack() } },
        onBackCompleted = { currentOnBack() },
    )
    return steps
}

/** Material shared-axis travel distance, as in the app shell. */
private val SharedAxisOffset = 30.dp
