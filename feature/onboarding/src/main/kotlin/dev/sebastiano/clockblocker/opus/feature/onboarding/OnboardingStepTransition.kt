package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
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
    private val stepState: State<OnboardingStep>,
    private val motion: ClockblockMotion,
    private val axisOffsetPx: Int,
    private val layoutDirection: LayoutDirection,
) {
    /** The steps whose content is composed and laid out (see [contentLaidOut]). */
    private val laidOut = mutableStateSetOf<OnboardingStep>()

    /** The latest target step requested by the caller. */
    val currentStep: OnboardingStep
        get() = stepState.value

    private var seekJob: Job? = null
    private var settleJob: Job? = null

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

    /** Records [step] as measured and placed in the step deck. */
    internal fun recordPlaced(step: OnboardingStep) {
        if (step !in laidOut) laidOut += step
    }

    /**
     * Layer poses (alpha, translationX, scale) for the placed steps on [transition], read only inside `placeWithLayer`
     * so step transitions never recompose or relayout their children during animation frames.
     */
    @Composable
    internal fun rememberStepPoses(spectacleFrom: () -> Rect?): StepDeckPoses {
        val poses = remember(this) {
            StepDeckPoses(transition, forwardSign, axisOffsetPx)
        }
        poses.Bind(transition, motion, spectacleFrom)
        return poses
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
        settleJob?.cancelAndJoin()
        seekJob?.cancelAndJoin()
        coroutineScope {
            settleJob = coroutineContext[Job]
            if (state.targetState != step || step !in laidOut) {
                // Compose and lay out the incoming step at the transition's start (it is still invisible there) before
                // anything moves. Its first composition can take several frames on a phone (the home step on first run),
                // and an animation already running would jump over its opening during them.
                state.seekTo(0f, targetState = step)
                snapshotFlow { step in laidOut }.first { it }
            }
            state.animateTo(step)
        }
    }

    internal suspend fun seekBack(previous: OnboardingStep, fraction: Float) = coroutineScope {
        settleJob?.cancelAndJoin()
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
        settleJob?.cancelAndJoin()
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

@Stable
internal class StepDeckPoses(
    private val transition: Transition<OnboardingStep>,
    private val forwardSign: Int,
    private val axisOffsetPx: Int,
) {
    private val activeStates: Array<State<Boolean>> = Array(OnboardingStep.entries.size) { i ->
        val step = OnboardingStep.entries[i]
        derivedStateOf {
            transition.currentState == step || transition.targetState == step
        }
    }
    private var lastFrom: OnboardingStep = Snapshot.withoutReadObservation { transition.currentState }
    private var currentStepTarget: Float = 0f
    private var spectacleFrom: () -> Rect? = { null }
    private var spatial: State<Float>? = null
    private var enterAlpha: State<Float>? = null
    private var exitAlpha: State<Float>? = null
    private var enterScale: State<Float>? = null
    private var crossFade: State<Float>? = null

    fun activeState(step: OnboardingStep): State<Boolean> = activeStates[step.ordinal]

    fun isActive(step: OnboardingStep): Boolean = activeStates[step.ordinal].value

    @Composable
    fun Bind(transition: Transition<OnboardingStep>, motion: ClockblockMotion, spectacleFrom: () -> Rect?) {
        val from = transition.currentState
        if (from != lastFrom) {
            lastFrom = from
            currentStepTarget = 1f - currentStepTarget
        }
        val base = currentStepTarget
        this.spectacleFrom = spectacleFrom
        spatial = transition.animateFloat(
            transitionSpec = { motion.stepSpatial() },
            label = "stepSpatial",
        ) { if (it == from) base else 1f - base }
        enterAlpha = transition.animateFloat(
            transitionSpec = {
                if (initialState == OnboardingStep.Sleep && targetState == OnboardingStep.Chronotype && spectacleFrom() != null) snap() else motion.stepFadeIn()
            },
            label = "stepEnterAlpha",
        ) { if (it == from) base else 1f - base }
        exitAlpha = transition.animateFloat(
            transitionSpec = {
                if (initialState == OnboardingStep.Sleep && targetState == OnboardingStep.Chronotype && spectacleFrom() != null) motion.colour() else motion.stepFadeOut()
            },
            label = "stepExitAlpha",
        ) { if (it == from) base else 1f - base }
        enterScale = transition.animateFloat(
            transitionSpec = {
                if (initialState == OnboardingStep.Sleep && targetState == OnboardingStep.Chronotype && spectacleFrom() == null) motion.containerSpatial() else snap()
            },
            label = "stepEnterScale",
        ) { if (it == from) base else 1f - base }
        crossFade = transition.animateFloat(
            transitionSpec = {
                if (initialState == OnboardingStep.Reminders || targetState == OnboardingStep.Reminders) motion.stepCrossFade() else snap()
            },
            label = "stepCrossFade",
        ) { if (it == from) base else 1f - base }
    }

    private fun progress(state: State<Float>?): Float {
        val v = state?.value ?: currentStepTarget
        return (if (currentStepTarget == 0f) v else 1f - v).coerceIn(0f, 1f)
    }

    fun welcome(): Float {
        if (!isActive(OnboardingStep.Welcome)) return 0f
        val from = transition.currentState
        val to = transition.targetState
        return when {
            from == to -> 1f
            from == OnboardingStep.Welcome -> 1f - progress(spatial)
            else -> progress(spatial)
        }
    }

    fun welcomeChrome(): Float {
        if (!isActive(OnboardingStep.Welcome)) return 0f
        val from = transition.currentState
        val to = transition.targetState
        return when {
            from == to -> 1f
            from == OnboardingStep.Welcome -> 1f - progress(exitAlpha)
            else -> progress(enterAlpha)
        }
    }

    fun chrome(): Float {
        if (!isActive(OnboardingStep.Welcome)) return 1f
        val from = transition.currentState
        val to = transition.targetState
        return when {
            from == to -> 0f
            from == OnboardingStep.Welcome -> progress(enterAlpha)
            else -> 1f - progress(exitAlpha)
        }
    }

    fun topBarExit(): Float {
        if (!isActive(OnboardingStep.Welcome)) return 1f
        val from = transition.currentState
        val to = transition.targetState
        return when {
            from == to -> 0f
            to == OnboardingStep.Welcome -> 1f - progress(exitAlpha)
            else -> 1f
        }
    }

    fun lastStep(): Float {
        if (!isActive(OnboardingStep.Reminders)) return 0f
        val from = transition.currentState
        val to = transition.targetState
        return when {
            from == to -> 1f
            from == OnboardingStep.Reminders -> 1f - progress(spatial)
            else -> progress(spatial)
        }
    }

    fun lastStepFade(): Float {
        if (!isActive(OnboardingStep.Reminders)) return 0f
        val from = transition.currentState
        val to = transition.targetState
        return when {
            from == to -> 1f
            from == OnboardingStep.Reminders -> 1f - progress(crossFade)
            else -> progress(crossFade)
        }
    }

    fun progress(): Float {
        val cur = transition.currentState
        val tgt = transition.targetState
        val curVal = progressFor(topBarStep(cur))
        return if (cur == tgt) {
            curVal
        } else {
            val tgtVal = progressFor(topBarStep(tgt))
            curVal + (tgtVal - curVal) * progress(spatial)
        }
    }

    fun GraphicsLayerScope.applyTo(step: OnboardingStep, warming: OnboardingStep? = null) {
        if (!isActive(step)) {
            if (step == warming) {
                alpha = PrewarmStepAlpha
                translationX = 0f
            } else {
                alpha = 0f
                translationX = size.width + 1f
            }
            scaleX = 1f
            scaleY = 1f
            return
        }
        val from = transition.currentState
        val to = transition.targetState
        if (from == to) {
            alpha = 1f
            translationX = 0f
            scaleX = 1f
            scaleY = 1f
            return
        }
        val sleepToChronotype = from == OnboardingStep.Sleep && to == OnboardingStep.Chronotype
        val spectacle = sleepToChronotype && spectacleFrom() != null
        val sign = if (to.ordinal > from.ordinal) forwardSign else -forwardSign
        val spatialProgress = progress(spatial)
        if (step == from) {
            alpha = 1f - progress(exitAlpha)
            translationX = if (sleepToChronotype) 0f else ((-sign * axisOffsetPx) * spatialProgress).roundToInt().toFloat()
            scaleX = 1f
            scaleY = 1f
        } else {
            alpha = if (spectacle) 1f else progress(enterAlpha)
            translationX = if (sleepToChronotype) 0f else ((sign * axisOffsetPx) * (1f - spatialProgress)).roundToInt().toFloat()
            val s = if (sleepToChronotype && !spectacle) RevealScale + (1f - RevealScale) * progress(enterScale) else 1f
            scaleX = s
            scaleY = s
        }
    }

    private companion object {
        const val RevealScale = 0.92f
        const val PrewarmStepAlpha = 0.5f

        fun progressFor(step: OnboardingStep): Float = (step.index + 1f) / OnboardingStep.Count
    }
}

/**
 * Drives [OnboardingStepTransition] from [step] (the ViewModel's source of truth) and registers the back
 * handler: enabled after the first step, it calls [onBack] on commit. While not [backEnabled] (onboarding is leaving),
 * Back is taken and does nothing, on any step: let through, the host would pop the flow mid take-off.
 */
@Composable
internal fun rememberOnboardingStepTransition(
    step: OnboardingStep,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
): OnboardingStepTransition {
    val stepState = rememberUpdatedState(step)
    return rememberOnboardingStepTransition(stepState, onBack, backEnabled)
}

@Composable
internal fun rememberOnboardingStepTransition(
    stepState: State<OnboardingStep>,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
): OnboardingStepTransition {
    val motion = ClockblockTheme.motion
    val layoutDirection = LocalLayoutDirection.current
    val axisOffsetPx = with(LocalDensity.current) { SharedAxisOffset.roundToPx() }
    val seekable = remember { SeekableTransitionState(Snapshot.withoutReadObservation { stepState.value }) }
    val holder = remember { TransitionHolder() }
    BindSeekableTransition(seekable, holder)
    val transition = holder.transition!!
    val steps = remember(seekable, transition, stepState, motion, axisOffsetPx, layoutDirection) {
        OnboardingStepTransition(seekable, transition, stepState, motion, axisOffsetPx, layoutDirection)
    }
    BindOnboardingStepTransition(steps, stepState, onBack, backEnabled)
    return steps
}

@Stable
private class TransitionHolder {
    var transition: Transition<OnboardingStep>? = null
}

@Composable
private fun BindSeekableTransition(
    seekable: SeekableTransitionState<OnboardingStep>,
    holder: TransitionHolder,
) {
    holder.transition = rememberTransition(seekable, label = "onboardingStep")
}

@Composable
private fun BindOnboardingStepTransition(
    steps: OnboardingStepTransition,
    stepState: State<OnboardingStep>,
    onBack: () -> Unit,
    backEnabled: Boolean,
) {
    val step = stepState.value
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
    // Not back enabled (onboarding is leaving): Back is taken and does nothing. Let through, the host would pop the
    // flow mid take-off.
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = !backEnabled,
        onBackCompleted = {},
    )
}

/** Material shared-axis travel distance, as in the app shell. */
private val SharedAxisOffset = 30.dp
