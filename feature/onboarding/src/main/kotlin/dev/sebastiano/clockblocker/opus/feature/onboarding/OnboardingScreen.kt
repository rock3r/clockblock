package dev.sebastiano.clockblocker.opus.feature.onboarding

import android.content.ActivityNotFoundException
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
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
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlin.math.roundToInt

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
 * once, the sleep step fades away and the bars fade out and take no input until the reveal rests. Back during it is an
 * ordinary back to the sleep step. Skipped when motion is reduced or TalkBack explores by touch.
 */
@Composable
fun OnboardingContent(state: OnboardingUiState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    val step = state.step
    // At most once per onboarding session, on the first Next from Sleep. Where it starts from is plain state, so a
    // spectacle interrupted by a rotation or process death never resumes: the reveal just sits at rest.
    var spectaclePlayed by rememberSaveable { mutableStateOf(false) }
    var spectacleFrom by remember { mutableStateOf<Rect?>(null) }
    val spectacleOrigin = remember { SpectacleOrigin() }
    val allowed = !ClockblockTheme.reduceMotion && !rememberTouchExplorationEnabled()
    // Reduce motion or TalkBack turning on ends it too: the reveal then sits at rest.
    val spectacleRunning = spectacleFrom != null && step == OnboardingStep.Chronotype && allowed
    // Back during the spectacle ends it, as does a host that didn't move on, and for good once motion is disallowed.
    LaunchedEffect(step) { if (step != OnboardingStep.Chronotype) spectacleFrom = null }
    LaunchedEffect(allowed) { if (!allowed) spectacleFrom = null }
    val currentStep by rememberUpdatedState(step)
    val spectacleAllowed by rememberUpdatedState(allowed)
    val stepActions = remember(actions) {
        object : OnboardingActions by actions {
            override fun next() {
                // The first visit counts even when it can't play (reduce motion, TalkBack): it never plays later.
                if (currentStep == OnboardingStep.Sleep && !spectaclePlayed) {
                    spectaclePlayed = true
                    if (spectacleAllowed) spectacleFrom = spectacleOrigin.dialFace ?: Rect.Zero
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
    val welcome = steps.transition.settledOr(
        steps.transition.animateFloat({ aroundWelcome { motion.navigationSpatial() } }, label = "welcomeBounds") { welcomeOnly(it) },
        ::welcomeOnly,
    )
    val welcomeChrome = steps.transition.settledOr(
        steps.transition.animateFloat(
            { aroundWelcome { if (targetState == OnboardingStep.Welcome) motion.navigationFadeIn() else motion.navigationFadeOut() } },
            label = "welcomeChromeFade",
        ) { welcomeOnly(it) },
        ::welcomeOnly,
    )
    val chrome = steps.transition.settledOr(
        steps.transition.animateFloat(
            { aroundWelcome { if (targetState == OnboardingStep.Welcome) motion.navigationFadeOut() else motion.navigationFadeIn() } },
            label = "chromeFade",
        ) { 1f - welcomeOnly(it) },
        { 1f - welcomeOnly(it) },
    )
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
        val bars = animateFloatAsState(if (spectacleRunning) 0f else 1f, motion.fade(), label = "spectacleBars")
        // Centred and capped on large windows: the two columns, progress line and bottom actions stay together
        // instead of spreading to the window edges.
        OnboardingFrame(
            searchProgress = { search.value },
            keyboard = keyboard,
            topBar = {
                OnboardingTopBar(
                    step = step,
                    visible = step != OnboardingStep.Welcome && !searchMode && !spectacleRunning,
                    alpha = { chrome() * (1f - search.value) * bars.value },
                    onSkip = actions::skip,
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
                        .then(if (spectacleRunning) Modifier.inert() else Modifier),
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
                                .testTag(OnboardingTags.step(target)),
                        ) {
                            CompositionLocalProvider(LocalSpectacleOrigin provides spectacleOrigin) {
                                OnboardingStepBody(
                                    target, state, stepActions, wide,
                                    homeSearch = homeSearch,
                                    spectacleFrom = spectacleFrom.takeIf { target == OnboardingStep.Chronotype },
                                    onSpectacleFinished = { spectacleFrom = null },
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
 * [spec] when the segment enters or leaves Welcome; otherwise a snap, as the Welcome-only values don't change there
 * and must not add a duration of their own to (so stretch, and shift the predictive back seek of) the other steps'
 * transitions.
 */
private fun <T> Transition.Segment<OnboardingStep>.aroundWelcome(spec: () -> FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (initialState == OnboardingStep.Welcome || targetState == OnboardingStep.Welcome) spec() else snap()

/**
 * [animated] while the step transition runs or a back gesture seeks it, [settled] for the step it rests on. A child
 * animation of a seekable transition can report its old start value again once the transition has ended (the
 * steps' enter/exit animations leaving change its total duration and replay it from 0), so at rest the value comes
 * from the step itself. Read it in layout or draw.
 */
private fun Transition<OnboardingStep>.settledOr(animated: State<Float>, settled: (OnboardingStep) -> Float): () -> Float = {
    if (currentState == targetState) settled(currentState) else animated.value
}

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
