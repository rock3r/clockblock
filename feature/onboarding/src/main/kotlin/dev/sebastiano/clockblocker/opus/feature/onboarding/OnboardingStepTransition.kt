package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The step-to-step motion of onboarding, on the same navigation tokens as the app shell's hierarchy
 * transitions (`AppTransitions` in `:app`), so moving through setup feels like moving through the app:
 *
 * - **Next / Back buttons**: shared axis X, a 30 dp slide mirrored in RTL, on `stepSpatial()` (300 ms,
 *   no bounce, snaps under reduced motion) with the sequenced `stepFadeOut()` → `stepFadeIn()`.
 *   A step leaves toward the edge the next one comes from, and Back plays exactly the reverse.
 * - **Sleep → chronotype reveal**: a fade-through with the reveal growing in on the container spring (see [reveal]);
 *   the first time, the sleep step only fades away under the reveal's own spectacle (see [spectacle]).
 * - **Predictive back**: the gesture *seeks* the same shared-axis (and fade-through) back transition that a Back tap
 *   plays, so the outgoing step fades out before the previous step fades in and the two never double-expose.
 *   Committing continues from where the finger let go; cancelling returns along the same path on `navigationSpatial()`.
 * - **Start**: a step change first composes and lays out the incoming step at the transition's start (invisible
 *   there), then animates. A slow first composition (the home step on first run) delays the start by its frames
 *   instead of eating the transition's opening.
 */
@Stable
internal class OnboardingStepTransition(
    private val state: SeekableTransitionState<OnboardingStep>,
    val transition: Transition<OnboardingStep>,
    private val motion: ClockblockMotion,
    private val axisOffsetPx: Int,
    private val layoutDirection: LayoutDirection,
) {
    /** The steps whose content is composed and laid out (see [contentLaidOut]). */
    private val laidOut = mutableStateSetOf<OnboardingStep>()

    private var seekJob: Job? = null

    /**
     * Reports [step]'s content as laid out, on the step's root, and forgets it when the content leaves. [settleOn]
     * waits for it before it animates.
     */
    @Composable
    internal fun Modifier.contentLaidOut(step: OnboardingStep): Modifier {
        // Keyed on this transition too: a rebuilt one (new motion, density or direction) must forget the step from its
        // own set, the one its onPlaced fills.
        DisposableEffect(this@OnboardingStepTransition, step) { onDispose { laidOut -= step } }
        return onPlaced { if (step !in laidOut) laidOut += step }
    }

    private val forwardSign: Int get() = if (layoutDirection == LayoutDirection.Ltr) 1 else -1

    /** The [ContentTransform] for a step change from [from] to [to]; [spectacle] while the chronotype spectacle plays. */
    fun transitionFor(from: OnboardingStep, to: OnboardingStep, spectacle: Boolean = false): ContentTransform {
        val forward = to.ordinal > from.ordinal
        return when {
            from == OnboardingStep.Sleep && to == OnboardingStep.Chronotype -> if (spectacle) spectacle() else reveal()
            else -> sharedAxis(if (forward) forwardSign else -forwardSign)
        }
    }

    /**
     * Sleep → the chronotype it gives: a rare moment, so the reveal may use the Expressive container spring. The dial
     * fades away (`navigationFadeOut()`) while the reveal fades in and grows from 92 % (`navigationFadeIn()`,
     * `containerSpatial()`); the reveal's own art and words then play in sequence. Reduce motion: the scale snaps and
     * only the fades remain.
     */
    private fun reveal(): ContentTransform {
        val enter = fadeIn(motion.stepFadeIn()) + scaleIn(motion.containerSpatial(), initialScale = RevealScale)
        val exit = fadeOut(motion.stepFadeOut())
        return ContentTransform(enter, exit, sizeTransform = null)
    }

    /**
     * Sleep → the chronotype reveal's first-time spectacle: the reveal draws its own entrance from the sleep dial, so it
     * comes in as it is, over the sleep step fading away on `colour()` (the shape grows out of the dial meanwhile).
     */
    private fun spectacle(): ContentTransform =
        ContentTransform(EnterTransition.None, fadeOut(motion.colour()), sizeTransform = null)

    private fun sharedAxis(sign: Int): ContentTransform {
        val enter = slideInHorizontally(motion.stepSpatial()) { sign * axisOffsetPx } +
            fadeIn(motion.stepFadeIn())
        val exit = slideOutHorizontally(motion.stepSpatial()) { -sign * axisOffsetPx } +
            fadeOut(motion.stepFadeOut())
        return ContentTransform(enter, exit, sizeTransform = null)
    }

    internal suspend fun settleOn(step: OnboardingStep) {
        seekJob?.cancelAndJoin()
        if (state.targetState != step || step !in laidOut) {
            // Compose and lay out the incoming step at the transition's start (it is still invisible there) before
            // anything moves. Its first composition can take several frames on a phone (the home step on first run),
            // and an animation already running would jump over its opening during them.
            state.seekTo(0f, targetState = step)
            snapshotFlow { step in laidOut }.first { it }
        }
        state.animateTo(step)
    }

    internal suspend fun seekBack(previous: OnboardingStep, fraction: Float) = coroutineScope {
        seekJob = coroutineContext[Job]
        if (state.targetState != previous || previous !in laidOut) {
            // Establish the target and lay out the incoming step at playTime 0 before seeking to fraction: otherwise
            // EnterExitTransition's deferred exit animations are created at playTime > 0 and Compose delays fadeOut by
            // that playTime, overlapping fadeIn.
            state.seekTo(0f, targetState = previous)
            snapshotFlow { previous in laidOut }.first { it }
        }
        state.seekTo(fraction.coerceIn(0f, 1f), targetState = previous)
    }

    /** Runs the seek back to 0 (where the gesture started) and settles on the current step again. */
    internal suspend fun cancelBack() {
        seekJob?.cancelAndJoin()
        val current = state.currentState
        val fraction = Animatable(state.fraction)
        coroutineScope {
            val seeking = launch { snapshotFlow { fraction.value }.collect { state.seekTo(it.coerceIn(0f, 1f)) } }
            fraction.animateTo(0f, motion.navigationSpatial())
            seeking.cancelAndJoin()
        }
        state.snapTo(current)
    }

    private companion object {
        const val RevealScale = 0.92f
    }
}

/**
 * Drives [OnboardingStepTransition] from [step] (the ViewModel's source of truth) and registers the back
 * handler: enabled after the first step (and while [backEnabled]: not while onboarding takes off), it calls [onBack]
 * on commit.
 */
@Composable
internal fun rememberOnboardingStepTransition(
    step: OnboardingStep,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
): OnboardingStepTransition {
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
            .collectLatest { progress ->
                if (progress != null) steps.seekBack(previous, progress)
            }
    }
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    NavigationBackHandler(
        state = backState,
        isBackEnabled = previous != null && backEnabled,
        onBackCancelled = { scope.launch { steps.cancelBack() } },
        onBackCompleted = { currentOnBack() },
    )
    return steps
}

/** Material shared-axis travel distance, as in the app shell. */
private val SharedAxisOffset = 30.dp
