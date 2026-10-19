package dev.sebastiano.clockblocker.opus.feature.onboarding

import android.content.ActivityNotFoundException
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ChronotypePrewarm
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Test tags for the e2e suite. */
object OnboardingTags {
    const val Progress = "onboarding_progress"
    const val Next = "onboarding_next"
    const val Back = "onboarding_back"
    const val Skip = "onboarding_skip"
    const val GetStarted = "onboarding_get_started"
    const val Finish = "onboarding_finish"
    const val MaybeLater = "onboarding_maybe_later"
    const val Disclaimer = "onboarding_disclaimer"

    /** `onboarding_step_Welcome` … `onboarding_step_Reminders`, on each step's root. */
    fun step(step: OnboardingStep): String = "onboarding_step_${step.name}"
}

/**
 * Where the first-time chronotype spectacle starts: the sleep dial records its inner face here (in root coordinates,
 * unclipped) whenever it's placed, and Next on the sleep step hands it to the reveal. A plain field, never observed:
 * nothing recomposes when the dial moves.
 */
internal class SpectacleOrigin {
    var dialFace: Rect? = null
}

/** The onboarding flow's [SpectacleOrigin], for the sleep dial; null elsewhere (Settings' sleep editor). */
internal val LocalSpectacleOrigin = staticCompositionLocalOf<SpectacleOrigin?> { null }

/** Everything the onboarding UI can ask for. The route binds it to [OnboardingViewModel]; tests use no-ops. */
interface OnboardingActions {
    fun next()
    fun back()
    fun skip()
    fun queryChange(query: String)
    fun selectPlace(place: Place)
    fun useDeviceZone()
    fun sleepChange(window: SleepWindow)
    fun daysOffSleepChange(window: SleepWindow)
    fun chronotypeChange(chronotype: Chronotype)
    fun profileChange(transform: (UserProfile) -> UserProfile)
    fun melatoninChange(enabled: Boolean)
    fun acknowledgeMelatonin(acknowledged: Boolean)
    fun intensityChange(intensity: Intensity)
    fun requestNotifications()
    fun openNotificationSettings()
    fun openExactAlarmSettings()
    fun loadNotificationPreview(widthDp: Int)
    fun finish()

    /** Does nothing; for previews and screenshot tests. */
    object None : OnboardingActions {
        override fun next() = Unit
        override fun back() = Unit
        override fun skip() = Unit
        override fun queryChange(query: String) = Unit
        override fun selectPlace(place: Place) = Unit
        override fun useDeviceZone() = Unit
        override fun sleepChange(window: SleepWindow) = Unit
        override fun daysOffSleepChange(window: SleepWindow) = Unit
        override fun chronotypeChange(chronotype: Chronotype) = Unit
        override fun profileChange(transform: (UserProfile) -> UserProfile) = Unit
        override fun melatoninChange(enabled: Boolean) = Unit
        override fun acknowledgeMelatonin(acknowledged: Boolean) = Unit
        override fun intensityChange(intensity: Intensity) = Unit
        override fun requestNotifications() = Unit
        override fun openNotificationSettings() = Unit
        override fun openExactAlarmSettings() = Unit
        override fun loadNotificationPreview(widthDp: Int) = Unit
        override fun finish() = Unit
    }
}

/**
 * Entry point of the onboarding flow (navigation contract used by `:app`; keep this signature).
 * Saves the [dev.sebastiano.clockblocker.opus.core.model.UserProfile] and calls [onFinished].
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<OnboardingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(state.isFinished) { if (state.isFinished) currentOnFinished() }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onNotificationsResult()
    }
    val actions = remember(viewModel) {
        object : OnboardingActions {
            override fun next() = viewModel.next()
            override fun back() { viewModel.back() }
            override fun skip() = viewModel.skipToEnd()
            override fun queryChange(query: String) = viewModel.onQueryChange(query)
            override fun selectPlace(place: Place) = viewModel.selectPlace(place)
            override fun useDeviceZone() = viewModel.useDeviceZone()
            override fun sleepChange(window: SleepWindow) = viewModel.setSleep(window)
            override fun daysOffSleepChange(window: SleepWindow) = viewModel.setDaysOffSleep(window)
            override fun chronotypeChange(chronotype: Chronotype) = viewModel.setChronotype(chronotype)
            override fun profileChange(transform: (UserProfile) -> UserProfile) = viewModel.updateProfile(transform)
            override fun melatoninChange(enabled: Boolean) = viewModel.setMelatonin(enabled)
            override fun acknowledgeMelatonin(acknowledged: Boolean) = viewModel.acknowledgeMelatoninNote(acknowledged)
            override fun intensityChange(intensity: Intensity) = viewModel.setIntensity(intensity)
            override fun requestNotifications() = permissionLauncher.launch(viewModel.runtimePermission)
            override fun openNotificationSettings() = launchSafely(context, viewModel.notificationSettingsIntent())
            override fun openExactAlarmSettings() = launchSafely(context, viewModel.exactAlarmSettingsIntent())
            override fun loadNotificationPreview(widthDp: Int) = viewModel.loadNotificationPreview(widthDp)
            override fun finish() = viewModel.finish()
        }
    }
    OnboardingContent(state, actions, modifier)
}

private fun launchSafely(context: android.content.Context, intent: android.content.Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Some OEM builds lack the specific settings screen; the status row stays as a reminder.
    }
}

/**
 * Stateless onboarding UI: a wavy progress line that flattens as setup completes, one step at a time with a
 * shared-axis transition, and a bottom action bar. Back (including predictive back) goes to the previous step.
 * Two columns (art left, controls right) when the window is expanded or a landscape medium.
 *
 * The home step's search goes full height when the keyboard leaves too little room ([HomeSearchState.fullHeight]):
 * the field moves to the top, the bars fold away and the results fill the space down to the keyboard. The keyboard is
 * only ever read in layout (it moves the bottom bar and shrinks the steps' scrolling viewport), and its target at the
 * start and end of a move in the search, so the layout never flips while you type.
 *
 * The first Next from the sleep step plays the chronotype spectacle (see `ChronotypeReveal`): the step moves on at
 * once, the sleep step fades away and the bars dim to half (no input until back in full) until the chronotype's name
 * starts to appear. Back during it is an ordinary back to the sleep step. Skipped when motion is reduced or TalkBack
 * explores by touch.
 */
@Composable
fun OnboardingContent(state: OnboardingUiState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    val step = state.step
    // At most once per onboarding session, on the first Next from Sleep. Where it starts from is plain state, so a
    // spectacle interrupted by a rotation or process death never resumes: the reveal just sits at rest.
    var spectaclePlayed by rememberSaveable { mutableStateOf(false) }
    var spectacleFrom by remember { mutableStateOf<Rect?>(null) }
    var spectacleActive by remember { mutableStateOf(false) }
    // The bars dim for the spectacle's first part and come back as the chronotype's name starts to appear.
    var spectacleDimsBars by remember { mutableStateOf(false) }
    val spectacleOrigin = remember { SpectacleOrigin() }
    val allowed = !ClockblockTheme.reduceMotion && !rememberTouchExplorationEnabled()
    // Reduce motion or TalkBack turning on ends it too: the reveal then sits at rest.
    val spectacleRunning = spectacleActive && step == OnboardingStep.Chronotype && allowed
    // Back during the spectacle ends it, as does a host that didn't move on, and for good once motion is disallowed.
    LaunchedEffect(step) {
        if (step != OnboardingStep.Chronotype) {
            spectacleFrom = null
            spectacleActive = false
        }
    }
    LaunchedEffect(allowed) {
        if (!allowed) {
            spectacleFrom = null
            spectacleActive = false
        }
    }
    val currentStep by rememberUpdatedState(step)
    val spectacleAllowed by rememberUpdatedState(allowed)
    val stepActions = remember(actions) {
        object : OnboardingActions by actions {
            override fun next() {
                // The first visit counts even when it can't play (reduce motion, TalkBack): it never plays later.
                if (currentStep == OnboardingStep.Sleep && !spectaclePlayed) {
                    spectaclePlayed = true
                    if (spectacleAllowed) {
                        spectacleFrom = spectacleOrigin.dialFace ?: Rect.Zero
                        spectacleActive = true
                        spectacleDimsBars = true
                    }
                }
                actions.next()
            }
        }
    }
    val steps = rememberOnboardingStepTransition(step, onBack = actions::back)
    // Each step keeps its saved state while you move away and back (T-017: the welcome clocks don't re-slide,
    // art entrances don't replay, scroll positions and open helpers stay put).
    val stepStates = rememberSaveableStateHolder()
    val motion = ClockblockTheme.motion
    val homeSearch = rememberHomeSearchState()
    // The step transition's own values, so predictive back seeks them with the steps: the Get started ↔ Next
    // bounds (spatial), and a fade-through between Welcome's chrome (the disclaimer, the Get started label: out
    // first) and the other steps' (the top bar, Back, the Next label: in once Welcome's is gone).
    val welcome = steps.transition.animateAroundWelcome("welcomeBounds", { motion.stepSpatial() }, ::welcomeOnly)
    val welcomeChrome = steps.transition.animateAroundWelcome(
        "welcomeChromeFade",
        { if (targetState == OnboardingStep.Welcome) motion.stepFadeIn() else motion.stepFadeOut() },
        ::welcomeOnly,
    )
    val chrome = steps.transition.animateChrome(motion, index = 0)
    // The top bar drops in a beat after Back / Next (StepTopBarLagMillis), so the two read as distinct moves. That
    // lagged entrance runs on its own clock: on the step transition it would outlast the step's 300 ms, so a Back
    // tapped once the step looks settled would find the transition still on Welcome and cut instead of animating.
    // Leaving for Welcome (and predictive back's seek) stays on the step transition.
    val topBarExit = steps.transition.animateTopBarExit(motion)
    val topBarEntrance = rememberTopBarEntrance(step, steps.transition, motion)
    // The last step: Skip slides out and fades as its room goes to the progress line, the runway for a little plane.
    // Seeked with the steps (predictive back scrubs it back), so it lives on the step transition, within its 300 ms.
    val lastStep = steps.transition.animateAround(OnboardingStep.Reminders, "lastStep", { motion.stepSpatial() }, ::remindersOnly)
    val lastStepFade = steps.transition.animateAround(OnboardingStep.Reminders, "lastStepFade", { motion.stepCrossFade() }, ::remindersOnly)
    val taxi = rememberRunwayTaxi(step, steps.transition, motion)
    // The progress line's fill moves with the steps, not on its own clock: an incoming step that takes a while to
    // compose delays the transition's start until it is laid out, and a fill started on the step change would run out
    // its time during those frames and then jump. On the transition it also scrubs with predictive back.
    val progress = steps.transition.animateStepValue("progress", { motion.stepSpatial() }) { progressFor(topBarStep(it)) }
    val nearWelcome = steps.transition.currentState == OnboardingStep.Welcome || steps.transition.targetState == OnboardingStep.Welcome
    val keyboard = rememberKeyboardAboveBars()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // Not the keyboard: it never changes these constraints (the frame takes it off in layout), so the window's
            // height without it decides the layout.
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
    ) {
        val wide = maxWidth >= 840.dp || (maxWidth >= 600.dp && maxWidth > maxHeight)
        val short = maxHeight < 480.dp
        val searchMode = step == OnboardingStep.HomeZone && homeSearch.fullHeight
        val search = animateFloatAsState(if (searchMode) 1f else 0f, motion.navigationSpatial(), label = "searchMode")
        // During the spectacle the bars stay on screen, dimmed, instead of vanishing and popping back: they fade to half
        // as it starts and back in full as the chronotype's name starts to appear, and take input only when in full.
        // A new window width is a new runway: the holding point follows it.
        val runwayPlane = remember(taxi, maxWidth) { RunwayPlanePose(taxi = { taxi.value }, alpha = lastStepFade) }
        val bars = animateFloatAsState(
            if (spectacleRunning && spectacleDimsBars) SpectacleBarsAlpha else 1f,
            motion.chromeDim(),
            label = "spectacleBars",
        )
        val barsInert by remember { derivedStateOf { bars.value < 1f } }
        // Centred and capped on large windows: the two columns, progress line and bottom actions stay together
        // instead of spreading to the window edges.
        if (step == OnboardingStep.Sleep) {
            ChronotypePrewarm()
        }
        OnboardingFrame(
            searchProgress = { search.value },
            keyboard = keyboard,
            topBar = {
                OnboardingTopBar(
                    step = topBarStep(step, steps.transition.currentState),
                    progress = progress,
                    visible = step != OnboardingStep.Welcome && !searchMode,
                    // The drop follows the entrance alone: at rest it is 1, so fading out toward Welcome doesn't
                    // slide, and an entrance frozen by an early Back keeps its offset while the items fade out.
                    slide = !ClockblockTheme.reduceMotion,
                    barAlpha = { (1f - search.value) * bars.value },
                    labelAlpha = { topBarExit() * topBarEntrance[0].value },
                    progressAlpha = { topBarExit() * topBarEntrance[1].value },
                    skipAlpha = { topBarExit() * topBarEntrance[2].value },
                    labelDrop = { topBarEntrance[0].value },
                    progressDrop = { topBarEntrance[1].value },
                    skipDrop = { topBarEntrance[2].value },
                    lastStep = lastStep,
                    skipLeaves = lastStepFade,
                    plane = runwayPlane,
                    onSkip = actions::skip,
                    // Moving until the transition rests on Welcome, so the fade out doesn't restart the wave.
                    waveMoving = !(steps.transition.currentState == OnboardingStep.Welcome &&
                        steps.transition.targetState == OnboardingStep.Welcome) && !searchMode,
                    // Dimmed under the spectacle: Skip keeps its look (not disabled) but takes no input.
                    modifier = if (barsInert) Modifier.inert() else Modifier,
                )
            },
            disclaimer = {
                if (nearWelcome) {
                    WelcomeDisclaimer(
                        Modifier
                            .graphicsLayer { alpha = welcomeChrome() }
                            .then(if (step == OnboardingStep.Welcome) Modifier else Modifier.clearAndSetSemantics { }),
                    )
                }
            },
            bottomBar = {
                OnboardingBottomBar(
                    state, stepActions, wide, short = short, welcome = welcome, welcomeChrome = welcomeChrome, chrome = chrome,
                    modifier = Modifier
                        .graphicsLayer { alpha = (1f - search.value) * bars.value }
                        // Folded away under the full-height search: out of keyboard and D-pad focus too.
                        .focusProperties { canFocus = !searchMode }
                        .then(if (searchMode) Modifier.clearAndSetSemantics { } else Modifier)
                        .then(if (barsInert) Modifier.inert() else Modifier),
                )
            },
            steps = { barHeight, noteHeight ->
                steps.transition.AnimatedContent(
                    transitionSpec = { steps.transitionFor(initialState, targetState, spectacle = spectacleFrom != null) },
                    contentKey = { it },
                    modifier = Modifier.fillMaxSize(),
                ) { target ->
                    stepStates.SaveableStateProvider(target.name) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .stepRoom(
                                    top = {
                                        when (target) {
                                            OnboardingStep.Welcome -> 0
                                            OnboardingStep.HomeZone -> (barHeight * (1f - search.value)).roundToInt()
                                            else -> barHeight
                                        }
                                    },
                                    bottom = if (target == OnboardingStep.Welcome) noteHeight else 0,
                                )
                                .testTag(OnboardingTags.step(target))
                                .then(with(steps) { Modifier.contentLaidOut(target) }),
                        ) {
                            CompositionLocalProvider(LocalSpectacleOrigin provides spectacleOrigin) {
                                OnboardingStepBody(
                                    target, state, stepActions, wide,
                                    homeSearch = homeSearch,
                                    spectacleFrom = spectacleFrom.takeIf { target == OnboardingStep.Chronotype },
                                    onSpectacleFinished = { spectacleActive = false },
                                    onSpectacleName = { spectacleDimsBars = false },
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxHeight().widthIn(max = OnboardingMaxWidth).fillMaxWidth().align(Alignment.TopCenter),
            disclaimerOverlap = bottomBarVerticalPadding(short),
        )
    }
}

/** Hidden chrome: takes no taps and no focus, and TalkBack doesn't see it. */
private fun Modifier.inert(): Modifier = this
    .clearAndSetSemantics { }
    .focusProperties { canFocus = false }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

/**
 * Whether a touch-exploration service (TalkBack) is on, following the setting while shown. The chronotype reveal then
 * skips its spectacle and entrance, so everything TalkBack reads is already on screen.
 */
@Composable
internal fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}


/** 1 on Welcome, 0 on every other step. */
private fun welcomeOnly(step: OnboardingStep): Float = if (step == OnboardingStep.Welcome) 1f else 0f

/**
 * Animates a Welcome-only float value on [this] step transition: [spec] when entering or leaving Welcome and a [snap]
 * elsewhere, so other steps' transitions (and their predictive back seek) never inherit a Welcome duration. See
 * [animateAround].
 *
 * Keyed on [Transition.currentState] so that when a transition settles (`currentState` moves to `targetState`) the
 * animation state is re-seeded at its resting value; otherwise `SeekableTransitionState.onTotalDurationChanged`
 * (fired when `AnimatedContent` disposes the outgoing step) replays the finished animation at `fraction = 0`, resetting
 * its value to the old start state and making the reverse transition a no-op.
 */
@Composable
private fun Transition<OnboardingStep>.animateAroundWelcome(
    label: String,
    spec: Transition.Segment<OnboardingStep>.() -> FiniteAnimationSpec<Float>,
    targetValueByState: (OnboardingStep) -> Float,
): () -> Float = animateAround(OnboardingStep.Welcome, label, spec, targetValueByState)

/**
 * Animates a float value tied to [around] on [this] step transition: [spec] when entering or leaving [around] and a
 * [snap] elsewhere. Keyed on [Transition.currentState]: see the note on re-seeding above.
 */
@Composable
private fun Transition<OnboardingStep>.animateAround(
    around: OnboardingStep,
    label: String,
    spec: Transition.Segment<OnboardingStep>.() -> FiniteAnimationSpec<Float>,
    targetValueByState: (OnboardingStep) -> Float,
): () -> Float {
    val animated = key(currentState) {
        animateFloat(
            transitionSpec = { if (initialState == around || targetState == around) spec() else snap() },
            label = label,
        ) { targetValueByState(it) }
    }
    return { if (currentState == targetState) targetValueByState(currentState) else animated.value }
}

/** How full the progress line is on [step]: its share of the questions. */
private fun progressFor(step: OnboardingStep): Float = (step.index + 1f) / OnboardingStep.Count

/**
 * Animates a float value on [this] step transition on every step change (seekable, so predictive back scrubs it),
 * re-seeded on [Transition.currentState] like [animateAround].
 */
@Composable
private fun Transition<OnboardingStep>.animateStepValue(
    label: String,
    spec: Transition.Segment<OnboardingStep>.() -> FiniteAnimationSpec<Float>,
    targetValueByState: (OnboardingStep) -> Float,
): () -> Float {
    val animated = key(currentState) { animateFloat(transitionSpec = { spec() }, label = label) { targetValueByState(it) } }
    return { if (currentState == targetState) targetValueByState(currentState) else animated.value }
}

/** 1 on the last step (Reminders), 0 on every other step. */
private fun remindersOnly(step: OnboardingStep): Float = if (step == OnboardingStep.Reminders) 1f else 0f

/**
 * The runway plane's taxi to its holding point (0 → 1) when the last step comes in, on `taxi()`: like the top bar's
 * entrance it runs on its own clock (it outlasts the 300 ms step transition, which must still settle with the step).
 * Heading away from the last step freezes it where it is while the plane fades out (on the step transition); at rest
 * on any other step it is back at 0, so coming back taxis in again.
 */
@Composable
private fun rememberRunwayTaxi(
    step: OnboardingStep,
    transition: Transition<OnboardingStep>,
    motion: ClockblockMotion,
): Animatable<Float, AnimationVector1D> {
    val last = OnboardingStep.Reminders
    val taxi = remember { Animatable(if (transition.currentState == last) 1f else 0f) }
    val scope = rememberCoroutineScope()
    val currentMotion by rememberUpdatedState(motion)
    val arriving = transition.currentState != last && transition.targetState == last
    val leaving = transition.currentState == last && (step != last || transition.targetState != last)
    val awayAtRest = transition.currentState != last && transition.targetState != last
    LaunchedEffect(awayAtRest) { if (awayAtRest) taxi.snapTo(0f) }
    LaunchedEffect(leaving) { if (leaving) taxi.stop() }
    // A cancelled predictive back leaves the taxi frozen: it rolls on to its holding point (a running one carries on).
    val atLast = transition.currentState == last && transition.targetState == last && step == last
    LaunchedEffect(atLast) {
        if (atLast && !taxi.isRunning && taxi.value < 1f) taxi.animateTo(1f, currentMotion.taxi())
    }
    LaunchedEffect(arriving) {
        if (!arriving) return@LaunchedEffect
        // Not tied to this effect: the taxi outlasts the transition, whose end would otherwise cancel it.
        scope.launch {
            snapshotFlow { transition.playTimeNanos > 0 || transition.currentState == last }.first { it }
            if (transition.targetState != last) return@launch
            taxi.snapTo(0f)
            taxi.animateTo(1f, currentMotion.taxi())
        }
    }
    return taxi
}

/**
 * Non-Welcome chrome opacity (0 on Welcome, 1 elsewhere): fades in at stagger slot [index] when leaving Welcome, and
 * fades out together (un-staggered) when returning to Welcome.
 */
@Composable
private fun Transition<OnboardingStep>.animateChrome(motion: ClockblockMotion, index: Int): () -> Float =
    animateAroundWelcome(
        label = "chromeFade$index",
        spec = { if (targetState == OnboardingStep.Welcome) motion.stepFadeOut() else motion.stepFadeIn(index) },
    ) { 1f - welcomeOnly(it) }

/**
 * The top bar's share of the step transition: fades out on `stepFadeOut()` returning to Welcome (so predictive back
 * seeks it), and snaps to 1 leaving Welcome, where [rememberTopBarEntrance] does the fading in. A snap adds no
 * duration, so the step transition still settles with the step.
 */
@Composable
private fun Transition<OnboardingStep>.animateTopBarExit(motion: ClockblockMotion): () -> Float =
    animateAroundWelcome(
        label = "topBarExit",
        spec = { if (targetState == OnboardingStep.Welcome) motion.stepFadeOut() else snap() },
    ) { 1f - welcomeOnly(it) }

/**
 * The top bar's label, progress line and Skip dropping in when leaving Welcome: staggered on
 * `stepFadeIn(index, StepTopBarLagMillis)`, from the moment the step transition starts moving, and run outside it (in
 * a scope that outlives the transition) so they never hold it on Welcome. Heading back to Welcome (a tap or predictive
 * back) freezes them where they are, so the exit fades them out from there; if the gesture is cancelled, they finish
 * on `fade()`. Back at rest on Welcome they reset to 0; on any other step they start (and stay) at 1.
 */
@Composable
private fun rememberTopBarEntrance(
    step: OnboardingStep,
    transition: Transition<OnboardingStep>,
    motion: ClockblockMotion,
): List<Animatable<Float, AnimationVector1D>> {
    val initial = if (transition.currentState == OnboardingStep.Welcome) 0f else 1f
    val items = remember { List(TopBarItems) { Animatable(initial) } }
    val scope = rememberCoroutineScope()
    val onWelcome = transition.currentState == OnboardingStep.Welcome
    val atWelcome = onWelcome && transition.targetState == OnboardingStep.Welcome
    val leavingWelcome = onWelcome && transition.targetState != OnboardingStep.Welcome
    // On the step too, not only the transition's target: that one changes a frame or two later, while the entrance
    // carries on dropping. Predictive back only moves the target.
    val headingToWelcome = !onWelcome &&
        (step == OnboardingStep.Welcome || transition.targetState == OnboardingStep.Welcome)
    val awayFromWelcome = !onWelcome && !headingToWelcome
    val currentMotion by rememberUpdatedState(motion)
    LaunchedEffect(atWelcome) {
        if (atWelcome) items.forEach { it.snapTo(0f) }
    }
    LaunchedEffect(headingToWelcome) {
        if (headingToWelcome) items.forEach { it.stop() }
    }
    LaunchedEffect(awayFromWelcome) {
        // A cancelled predictive back leaves a frozen entrance behind: finish it (a running one carries on as it is).
        if (awayFromWelcome) {
            items.forEach { item -> if (!item.isRunning && item.value < 1f) launch { item.animateTo(1f, currentMotion.fade()) } }
        }
    }
    LaunchedEffect(leavingWelcome) {
        if (!leavingWelcome) return@LaunchedEffect
        // Not tied to this effect: the entrance outlasts the transition, whose end would otherwise cancel it.
        scope.launch {
            // Wait for the step transition to start moving (it first lays the incoming step out at its start).
            snapshotFlow { transition.playTimeNanos > 0 || transition.currentState != OnboardingStep.Welcome }.first { it }
            if (transition.targetState == OnboardingStep.Welcome) return@launch
            items.forEachIndexed { index, item ->
                launch { item.animateTo(1f, currentMotion.stepFadeIn(index, ClockblockMotion.StepTopBarLagMillis)) }
            }
        }
    }
    return items
}

/** The top bar's label, progress line and Skip. */
private const val TopBarItems = 3

/**
 * Reserves a step's room for the chrome around it: [top] (read in layout, so the home search can fold it away frame
 * by frame) and [bottom] pixels.
 */
private fun Modifier.stepRoom(top: () -> Int, bottom: Int): Modifier = layout { measurable, constraints ->
    val topPx = top().coerceAtLeast(0)
    val inner = (constraints.maxHeight - topPx - bottom).coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, inner))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, topPx) }
}

/** Onboarding never grows wider than this: past it, the window only gains margins. */
private val OnboardingMaxWidth = 1080.dp

/** The top and bottom bars' opacity while the chronotype spectacle plays: still there, plainly not for now. */
private const val SpectacleBarsAlpha = 0.5f
