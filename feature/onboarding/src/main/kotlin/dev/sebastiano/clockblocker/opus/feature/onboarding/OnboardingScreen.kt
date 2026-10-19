package dev.sebastiano.clockblocker.opus.feature.onboarding

import android.content.ActivityNotFoundException
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
@Stable
internal class SpectacleOrigin {
    var dialFaceProvider: (() -> Rect?)? = null
    var dialFace: Rect? = null
        get() = dialFaceProvider?.invoke() ?: field
}

/** The onboarding flow's [SpectacleOrigin], for the sleep dial; null elsewhere (Settings' sleep editor). */
internal val LocalSpectacleOrigin = staticCompositionLocalOf<SpectacleOrigin?> { null }

/** Everything the onboarding UI can ask for. The route binds it to [OnboardingViewModel]; tests use no-ops. */
@Stable
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

    /** The finish's take-off has started (the app's sweep follows the finish). */
    fun takeoff() = Unit

    /** The finish's take-off has played out: onboarding can hand over once the profile is saved. */
    fun takeoffLanded() = Unit

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
 * Saves the [dev.sebastiano.clockblocker.opus.core.model.UserProfile] and calls [onFinished]. When the finish plays
 * its take-off (motion allowed), [onTakeoff] comes first, as it starts, so the app can follow with its sweep.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier, onTakeoff: () -> Unit = {}) {
    val viewModel = metroViewModel<OnboardingViewModel>()
    val stateHolder = viewModel.state.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnTakeoff by rememberUpdatedState(onTakeoff)
    // A finish that takes off saves while the plane leaves, and hands over once both are done.
    var tookOff by remember { mutableStateOf(false) }
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(finished, tookOff, landed) {
        if (finished && (!tookOff || landed)) currentOnFinished()
    }
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
            override fun takeoff() {
                tookOff = true
                currentOnTakeoff()
            }

            override fun takeoffLanded() {
                landed = true
            }
        }
    }
    OnboardingContent(stateHolder, actions, modifier)
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
    OnboardingContent(rememberUpdatedState(state), actions, modifier)
}

@Composable
internal fun OnboardingContent(stateHolder: State<OnboardingUiState>, actions: OnboardingActions, modifier: Modifier = Modifier) {
    val latestActions = rememberUpdatedState(actions)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // Not the keyboard: it never changes these constraints (the frame takes it off in layout), so the window's
            // height without it decides the layout.
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
    ) {
        OnboardingBoxContent(
            stateHolder = stateHolder,
            actions = latestActions.value,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            maxWidthPx = constraints.maxWidth,
            modifier = Modifier.fillMaxHeight().widthIn(max = OnboardingMaxWidth).fillMaxWidth().align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun OnboardingBoxContent(
    stateHolder: State<OnboardingUiState>,
    actions: OnboardingActions,
    maxWidth: Dp,
    maxHeight: Dp,
    maxWidthPx: Int,
    modifier: Modifier = Modifier,
) {
    val stepState = remember(stateHolder) { derivedStateOf { stateHolder.value.step } }
    // At most once per onboarding session, on the first Next from Sleep. Where it starts from is plain state, so a
    // spectacle interrupted by a rotation or process death never resumes: the reveal just sits at rest.
    var spectaclePlayed by rememberSaveable { mutableStateOf(false) }
    var spectacleFrom by remember { mutableStateOf<Rect?>(null) }
    var spectacleActive by remember { mutableStateOf(false) }
    // The bars dim for the spectacle's first part and come back as the chronotype's name starts to appear.
    var spectacleDimsBars by remember { mutableStateOf(false) }
    val spectacleOrigin = remember { SpectacleOrigin() }
    val allowed = !ClockblockTheme.reduceMotion && !rememberTouchExplorationEnabled()
    val resetSpectacle = remember {
        {
            spectacleFrom = null
            spectacleActive = false
        }
    }
    BindSpectacleReset(stepState, allowed, resetSpectacle)
    val spectacleAllowed by rememberUpdatedState(allowed)
    // Finishing: the runway plane takes off and the content leaves (Takeoff) before the profile is saved and the app
    // takes over (its sweep follows). Nothing takes input meanwhile, with or without it: the buttons keep their look
    // (not disabled) as they leave. The take-off is skipped with the spectacle's conditions.
    val takeoff = remember { Animatable(0f) }
    val takeoffClock = remember(takeoff) { { takeoff.value } }
    var leaving by remember { mutableStateOf(false) }
    val leaveScope = rememberCoroutineScope()
    val currentMotion by rememberUpdatedState(ClockblockTheme.motion)
    val stepActions = remember(actions, stepState) {
        object : OnboardingActions by actions {
            private var lastPreviewWidthDp = -1

            override fun next() {
                // The first visit counts even when it can't play (reduce motion, TalkBack): it never plays later.
                if (stepState.value == OnboardingStep.Sleep && !spectaclePlayed) {
                    spectaclePlayed = true
                    if (spectacleAllowed) {
                        spectacleFrom = spectacleOrigin.dialFace ?: Rect.Zero
                        spectacleActive = true
                        spectacleDimsBars = true
                    }
                }
                actions.next()
            }

            override fun finish() {
                if (leaving) return
                leaving = true
                if (!spectacleAllowed) return actions.finish()
                actions.takeoff()
                // Saved meanwhile, so the hand-over doesn't wait for it after the take-off.
                actions.finish()
                leaveScope.launch {
                    takeoff.animateTo(1f, currentMotion.takeoff())
                    actions.takeoffLanded()
                }
            }

            override fun loadNotificationPreview(widthDp: Int) {
                if (widthDp <= 0 || widthDp == lastPreviewWidthDp) return
                lastPreviewWidthDp = widthDp
                actions.loadNotificationPreview(widthDp)
            }
        }
    }
    val steps = rememberOnboardingStepTransition(stepState, onBack = actions::back, backEnabled = !leaving)
    // Each step keeps its saved state while you move away and back (T-017: the welcome clocks don't re-slide,
    // art entrances don't replay, scroll positions and open helpers stay put).
    val stepStates = rememberSaveableStateHolder()
    val motion = ClockblockTheme.motion
    val homeSearch = rememberHomeSearchState()
    val onSpectacleFinished = remember { { spectacleActive = false } }
    val onSpectacleName = remember { { spectacleDimsBars = false } }
    val spectacleFromProvider = remember { { spectacleFrom } }
    val poses = steps.rememberStepPoses(spectacleFrom = spectacleFromProvider)
    val welcome = remember(poses) { poses::welcome }
    val welcomeChrome = remember(poses) { poses::welcomeChrome }
    val chrome = remember(poses) { poses::chrome }
    val topBarExit = remember(poses) { poses::topBarExit }
    val lastStep = remember(poses) { poses::lastStep }
    val lastStepFade = remember(poses) { poses::lastStepFade }
    val progress = remember(poses) { poses::progress }
    val topBarEntrance = rememberTopBarEntrance(stepState, steps.transition, motion)
    val taxi = rememberRunwayTaxi(stepState, steps.transition, motion)
    val keyboard = rememberKeyboardAboveBars()
    val contentStateHolder = remember(stateHolder) {
        var prev: OnboardingUiState? = null
        derivedStateOf {
            val s = stateHolder.value
            val normalized = if (s.step == OnboardingStep.Welcome) s else s.copy(step = OnboardingStep.Welcome)
            val cached = prev
            if (cached == normalized) cached else normalized.also { prev = it }
        }
    }
    val wide = maxWidth >= 840.dp || (maxWidth >= 600.dp && maxWidth > maxHeight)
    val short = maxHeight < 480.dp
    val searchModeState = remember(stepState, homeSearch) {
        derivedStateOf { stepState.value == OnboardingStep.HomeZone && homeSearch.fullHeight }
    }
    val spectacleDimmedState = remember(stepState, allowed) {
        derivedStateOf {
            spectacleActive && stepState.value == OnboardingStep.Chronotype && allowed && spectacleDimsBars
        }
    }
    // During the spectacle the bars stay on screen, dimmed, instead of vanishing and popping back: they fade to half
    // as it starts and back in full as the chronotype's name starts to appear, and take input only when in full.
    // A new window width is a new runway: the holding point follows it.
    val runwayPlane = remember(taxi, maxWidth) {
        RunwayPlanePose(taxi = { taxi.value }, alpha = lastStepFade, takeoff = { takeoff.value })
    }
    val chromeBarState = remember { ChromeBarState() }
    chromeBarState.Bind(searchModeState, spectacleDimmedState, motion)
    val searchProgress = remember(chromeBarState) { { chromeBarState.searchProgress() } }
    val barAlpha = remember(chromeBarState) { { chromeBarState.barAlpha() } }
    val labelAlpha = remember(topBarExit, topBarEntrance) { { topBarExit() * topBarEntrance[0].value } }
    val progressAlpha = remember(topBarExit, topBarEntrance) { { topBarExit() * topBarEntrance[1].value } }
    val skipAlpha = remember(topBarExit, topBarEntrance) { { topBarExit() * topBarEntrance[2].value } }
    val labelDrop = remember(topBarEntrance) { { topBarEntrance[0].value } }
    val progressDrop = remember(topBarEntrance) { { topBarEntrance[1].value } }
    val skipDrop = remember(topBarEntrance) { { topBarEntrance[2].value } }
    val density = LocalDensity.current
    val previewWidthDp = with(density) { remindersPreviewWidthDp(maxWidthPx, wide) }
    LaunchedEffect(previewWidthDp) { stepActions.loadNotificationPreview(previewWidthDp) }
    val precomposed = remember { PrecomposedSteps() }
    BindStepPrewarm(stepState, steps, stateHolder, precomposed)
    val frameRoom = remember { OnboardingFrameRoom() }
    CompositionLocalProvider(LocalTakeoff provides takeoffClock) {
        OnboardingFrame(
            room = frameRoom,
            searchProgress = searchProgress,
            keyboard = keyboard,
            topBar = {
                val displayedTopBarStepState = remember(steps, stepState) {
                    derivedStateOf { topBarStep(stepState.value, steps.transition.currentState) }
                }
                val atWelcomeRest by remember(steps) {
                    derivedStateOf {
                        steps.transition.currentState == OnboardingStep.Welcome &&
                            steps.transition.targetState == OnboardingStep.Welcome
                    }
                }
                val visible by remember(stepState, searchModeState) {
                    derivedStateOf { stepState.value != OnboardingStep.Welcome && !searchModeState.value }
                }
                val waveMoving by remember(atWelcomeRest, searchModeState) {
                    derivedStateOf { !atWelcomeRest && !searchModeState.value }
                }
                val topBarActiveState = remember(chromeBarState.barsInertState) {
                    derivedStateOf { !chromeBarState.barsInertState.value }
                }
                val topBarInertSemantics = remember {
                    StepSemanticsHolder(initialActive = Snapshot.withoutReadObservation { topBarActiveState.value })
                }
                SyncStepSemantics(topBarActiveState, topBarInertSemantics)
                val topBarInert = remember(topBarActiveState) { { !topBarActiveState.value } }
                val topBarModifier = remember(topBarInertSemantics, topBarInert) {
                    Modifier.inertWhen(topBarInertSemantics, topBarInert)
                }
                OnboardingTopBar(
                    stepState = displayedTopBarStepState,
                    progress = progress,
                    visible = visible,
                    // The drop follows the entrance alone: at rest it is 1, so fading out toward Welcome doesn't
                    // slide, and an entrance frozen by an early Back keeps its offset while the items fade out.
                    slide = !ClockblockTheme.reduceMotion,
                    barAlpha = barAlpha,
                    labelAlpha = labelAlpha,
                    progressAlpha = progressAlpha,
                    skipAlpha = skipAlpha,
                    labelDrop = labelDrop,
                    progressDrop = progressDrop,
                    skipDrop = skipDrop,
                    lastStep = lastStep,
                    skipLeaves = lastStepFade,
                    plane = runwayPlane,
                    onSkip = actions::skip,
                    // Moving until the transition rests on Welcome, so the fade out doesn't restart the wave.
                    waveMoving = waveMoving,
                    // Dimmed under the spectacle: Skip keeps its look (not disabled) but takes no input.
                    modifier = topBarModifier,
                )
            },
            disclaimer = {
                if (poses.isActive(OnboardingStep.Welcome)) {
                    val onWelcome by remember(stepState) {
                        derivedStateOf { stepState.value == OnboardingStep.Welcome }
                    }
                    WelcomeDisclaimer(
                        Modifier
                            .graphicsLayer { alpha = welcomeChrome() }
                            .then(if (onWelcome) Modifier else Modifier.clearAndSetSemantics { }),
                    )
                }
            },
            bottomBar = {
                val onWelcome by remember(stepState) {
                    derivedStateOf { stepState.value == OnboardingStep.Welcome }
                }
                val onRemindersState = remember(stepState) {
                    derivedStateOf { stepState.value == OnboardingStep.Reminders }
                }
                val remindersHandledState = remember(stateHolder) {
                    derivedStateOf { stateHolder.value.remindersHandled }
                }
                val bottomBarActiveState = remember(searchModeState, chromeBarState.barsInertState) {
                    derivedStateOf { !searchModeState.value && !chromeBarState.barsInertState.value }
                }
                val bottomBarSemantics = remember {
                    StepSemanticsHolder(initialActive = Snapshot.withoutReadObservation { bottomBarActiveState.value })
                }
                SyncStepSemantics(bottomBarActiveState, bottomBarSemantics)
                val bottomBarInert = remember(bottomBarActiveState) { { !bottomBarActiveState.value } }
                val bottomBarModifier = remember(barAlpha, takeoffClock, bottomBarSemantics, bottomBarInert) {
                    Modifier
                        .graphicsLayer {
                            val e = Takeoff.exit(Takeoff.BottomBar, takeoffClock())
                            alpha = barAlpha() * (1f - e)
                            translationY = if (e > 0f) -(Takeoff.Rise.toPx() * e) else 0f
                        }
                        // Folded away under the full-height search or dimmed under the spectacle: out of focus, TalkBack, and touch.
                        .inertWhen(bottomBarSemantics, bottomBarInert)
                }
                OnboardingBottomBar(
                    onWelcome = onWelcome,
                    onRemindersState = onRemindersState,
                    remindersHandledState = remindersHandledState,
                    actions = stepActions,
                    wide = wide,
                    short = short,
                    welcome = welcome,
                    welcomeChrome = welcomeChrome,
                    chrome = chrome,
                    modifier = bottomBarModifier,
                )
            },
            steps = {
                OnboardingStepDeck(
                    steps = steps,
                    poses = poses,
                    precomposed = precomposed,
                    contentStateHolder = contentStateHolder,
                    stepActions = stepActions,
                    wide = wide,
                    frameRoom = frameRoom,
                    searchProgress = searchProgress,
                    stepStates = stepStates,
                    spectacleOrigin = spectacleOrigin,
                    homeSearch = homeSearch,
                    spectacleFrom = spectacleFromProvider,
                    onSpectacleFinished = onSpectacleFinished,
                    onSpectacleName = onSpectacleName,
                )
            },
            // Leaving: nothing takes a tap, focus or TalkBack while the profile saves and the plane takes off.
            modifier = modifier.then(if (leaving) Modifier.inert() else Modifier),
            disclaimerOverlap = bottomBarVerticalPadding(short),
        )
    }
}

@Composable
private fun BindSpectacleReset(
    stepState: State<OnboardingStep>,
    allowed: Boolean,
    onReset: () -> Unit,
) {
    val step = stepState.value
    // Back during the spectacle ends it, as does a host that didn't move on, and for good once motion is disallowed.
    LaunchedEffect(step) {
        if (step != OnboardingStep.Chronotype) onReset()
    }
    LaunchedEffect(allowed) {
        if (!allowed) onReset()
    }
}

@Stable
private class ChromeBarState {
    var searchProgress: () -> Float = { 0f }
    var barAlpha: () -> Float = { 1f }
    var barsInertState: State<Boolean> = mutableStateOf(false)

    @Composable
    fun Bind(
        searchModeState: State<Boolean>,
        spectacleDimmedState: State<Boolean>,
        motion: ClockblockMotion,
    ) {
        val search = animateFloatAsState(
            if (searchModeState.value) 1f else 0f,
            motion.navigationSpatial(),
            label = "searchMode",
        )
        val bars = animateFloatAsState(
            if (spectacleDimmedState.value) SpectacleBarsAlpha else 1f,
            motion.chromeDim(),
            label = "spectacleBars",
        )
        searchProgress = remember(search) { { search.value } }
        barAlpha = remember(search, bars) { { (1f - search.value) * bars.value } }
        barsInertState = remember(bars) { derivedStateOf { bars.value < 1f } }
    }
}

@Composable
private fun BindStepPrewarm(
    stepState: State<OnboardingStep>,
    steps: OnboardingStepTransition,
    stateHolder: State<OnboardingUiState>,
    precomposed: PrecomposedSteps,
) {
    val step = stepState.value
    // Prewarm runs once on Welcome (before any step transition) or on Sleep if started directly there.
    var prewarmed by remember { mutableStateOf(false) }
    if (!prewarmed && (step == OnboardingStep.Welcome || step == OnboardingStep.Sleep)) {
        ChronotypePrewarm(onPrewarmed = { prewarmed = true })
    }
    if (precomposed.steps.size < OnboardingStep.entries.size) {
        val from = steps.transition.currentState
        val to = steps.transition.targetState
        if (step !in precomposed.steps) precomposed.steps += step
        if (from !in precomposed.steps) precomposed.steps += from
        if (to !in precomposed.steps) precomposed.steps += to
    }
    val hasNotificationPreview by remember(stateHolder) {
        derivedStateOf { stateHolder.value.notificationPreview != null }
    }
    LaunchedEffect(prewarmed, step) {
        if (!prewarmed || step != OnboardingStep.Welcome) {
            precomposed.warmingStep = null
            return@LaunchedEffect
        }
        for (candidate in PrecomposedOnboardingSteps) {
            if (candidate !in precomposed.steps) {
                withFrameNanos { }
                precomposed.warmingStep = candidate
                precomposed.steps += candidate
            }
        }
        withFrameNanos { }
        precomposed.warmingStep = null
    }
    LaunchedEffect(hasNotificationPreview) {
        if (hasNotificationPreview && stepState.value == OnboardingStep.Welcome && OnboardingStep.Reminders in precomposed.steps) {
            withFrameNanos { }
            if (stepState.value == OnboardingStep.Welcome && precomposed.warmingStep == null) {
                precomposed.warmingStep = OnboardingStep.Reminders
                withFrameNanos { }
                if (precomposed.warmingStep == OnboardingStep.Reminders) {
                    precomposed.warmingStep = null
                }
            }
        }
    }
}

private val PrecomposedOnboardingSteps = listOf(
    OnboardingStep.HomeZone,
    OnboardingStep.Sleep,
    OnboardingStep.Chronotype,
    OnboardingStep.Tools,
    OnboardingStep.Reminders,
)

@Stable
private class PrecomposedSteps {
    val steps = mutableStateSetOf<OnboardingStep>()
    var warmingStep by mutableStateOf<OnboardingStep?>(null)
}

@Composable
private fun OnboardingStepDeck(
    steps: OnboardingStepTransition,
    poses: StepDeckPoses,
    precomposed: PrecomposedSteps,
    contentStateHolder: State<OnboardingUiState>,
    stepActions: OnboardingActions,
    wide: Boolean,
    frameRoom: OnboardingFrameRoom,
    searchProgress: () -> Float,
    stepStates: SaveableStateHolder,
    spectacleOrigin: SpectacleOrigin,
    homeSearch: HomeSearchState,
    spectacleFrom: () -> Rect?,
    onSpectacleFinished: () -> Unit,
    onSpectacleName: () -> Unit,
) {
    val layerBlocks = remember(poses, precomposed) {
        Array<GraphicsLayerScope.() -> Unit>(OnboardingStep.entries.size) { i ->
            val step = OnboardingStep.entries[i]
            { with(poses) { applyTo(step, precomposed.warmingStep) } }
        }
    }
    val measurePolicy = remember(steps, precomposed, layerBlocks) {
        MeasurePolicy { measurables, constraints ->
            val fixed = Constraints.fixed(constraints.maxWidth, constraints.maxHeight)
            val placeables = arrayOfNulls<Placeable>(OnboardingStep.entries.size)
            for (i in measurables.indices) {
                val measurable = measurables[i]
                val stepKey = measurable.layoutId as OnboardingStep
                placeables[stepKey.ordinal] = measurable.measure(fixed)
                steps.recordPlaced(stepKey)
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                val warming = precomposed.warmingStep
                for (target in OnboardingStep.entries) {
                    val placeable = placeables[target.ordinal] ?: continue
                    placeable.placeWithLayer(
                        x = 0,
                        y = 0,
                        zIndex = if (target == warming) -1f else 0f,
                        layerBlock = layerBlocks[target.ordinal],
                    )
                }
            }
        }
    }
    Layout(
        content = {
            for (target in OnboardingStep.entries) {
                if (target !in precomposed.steps) continue
                key(target) {
                    StepDeckItem(
                        target = target,
                        poses = poses,
                        precomposed = precomposed,
                        contentStateHolder = contentStateHolder,
                        stepActions = stepActions,
                        wide = wide,
                        frameRoom = frameRoom,
                        searchProgress = searchProgress,
                        stepStates = stepStates,
                        spectacleOrigin = spectacleOrigin,
                        homeSearch = homeSearch,
                        spectacleFrom = spectacleFrom,
                        onSpectacleFinished = onSpectacleFinished,
                        onSpectacleName = onSpectacleName,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        measurePolicy = measurePolicy,
    )
}

@Composable
private fun StepDeckItem(
    target: OnboardingStep,
    poses: StepDeckPoses,
    precomposed: PrecomposedSteps,
    contentStateHolder: State<OnboardingUiState>,
    stepActions: OnboardingActions,
    wide: Boolean,
    frameRoom: OnboardingFrameRoom,
    searchProgress: () -> Float,
    stepStates: SaveableStateHolder,
    spectacleOrigin: SpectacleOrigin,
    homeSearch: HomeSearchState,
    spectacleFrom: () -> Rect?,
    onSpectacleFinished: () -> Unit,
    onSpectacleName: () -> Unit,
) {
    val activeState = poses.activeState(target)
    val semanticsHolder = remember(target) {
        StepSemanticsHolder(
            initialTag = OnboardingTags.step(target),
            initialActive = Snapshot.withoutReadObservation { activeState.value },
        )
    }
    SyncStepSemantics(activeState, semanticsHolder)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val boxModifier = remember(target, frameRoom, searchProgress, activeState, semanticsHolder, precomposed, surfaceColor) {
        Modifier
            .layoutId(target)
            .fillMaxSize()
            .then(
                if (target == OnboardingStep.Welcome) {
                    Modifier.drawBehind { if (precomposed.warmingStep != null) drawRect(surfaceColor) }
                } else {
                    Modifier
                },
            )
            .stepRoom(
                top = {
                    when (target) {
                        OnboardingStep.Welcome -> 0
                        OnboardingStep.HomeZone -> (frameRoom.topBarHeight * (1f - searchProgress())).roundToInt()
                        else -> frameRoom.topBarHeight
                    }
                },
                bottom = {
                    when (target) {
                        OnboardingStep.Welcome -> frameRoom.bottomBarHeight + frameRoom.disclaimerHeight
                        OnboardingStep.HomeZone -> (frameRoom.bottomBarHeight * (1f - searchProgress())).roundToInt()
                        else -> frameRoom.bottomBarHeight
                    }
                },
            )
            .focusProperties { canFocus = activeState.value }
            .then(StepSemanticsElement(semanticsHolder))
    }
    stepStates.SaveableStateProvider(target.name) {
        Box(boxModifier) {
            CompositionLocalProvider(LocalSpectacleOrigin provides spectacleOrigin) {
                OnboardingStepBody(
                    step = target,
                    state = contentStateHolder.value,
                    actions = stepActions,
                    wide = wide,
                    activeState = activeState,
                    homeSearch = homeSearch,
                    spectacleFrom = spectacleFrom,
                    onSpectacleFinished = onSpectacleFinished,
                    onSpectacleName = onSpectacleName,
                )
            }
        }
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

/**
 * The runway plane's taxi to its holding point (0 → 1) when the last step comes in, on `taxi()`: like the top bar's
 * entrance it runs on its own clock (it outlasts the 300 ms step transition, which must still settle with the step).
 * Heading away from the last step freezes it where it is while the plane fades out (on the step transition); at rest
 * on any other step it is back at 0, so coming back taxis in again.
 */
@Composable
private fun rememberRunwayTaxi(
    stepState: State<OnboardingStep>,
    transition: Transition<OnboardingStep>,
    motion: ClockblockMotion,
): Animatable<Float, AnimationVector1D> {
    val last = OnboardingStep.Reminders
    val taxi = remember { Animatable(Snapshot.withoutReadObservation { if (transition.currentState == last) 1f else 0f }) }
    BindRunwayTaxi(taxi, stepState, transition, motion)
    return taxi
}

@Composable
private fun BindRunwayTaxi(
    taxi: Animatable<Float, AnimationVector1D>,
    stepState: State<OnboardingStep>,
    transition: Transition<OnboardingStep>,
    motion: ClockblockMotion,
) {
    val last = OnboardingStep.Reminders
    val scope = rememberCoroutineScope()
    val currentMotion by rememberUpdatedState(motion)
    val arriving by remember(transition) { derivedStateOf { transition.currentState != last && transition.targetState == last } }
    val leaving by remember(transition, stepState) {
        derivedStateOf { transition.currentState == last && (stepState.value != last || transition.targetState != last) }
    }
    val awayAtRest by remember(transition) { derivedStateOf { transition.currentState != last && transition.targetState != last } }
    LaunchedEffect(awayAtRest) { if (awayAtRest) taxi.snapTo(0f) }
    LaunchedEffect(leaving) { if (leaving) taxi.stop() }
    // A cancelled predictive back leaves the taxi frozen: it rolls on to its holding point (a running one carries on).
    val atLast by remember(transition, stepState) {
        derivedStateOf { transition.currentState == last && transition.targetState == last && stepState.value == last }
    }
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
}

/**
 * The top bar's label, progress line and Skip dropping in when leaving Welcome: staggered on
 * `stepFadeIn(index, StepTopBarLagMillis)`, from the moment the step transition starts moving, and run outside it (in
 * a scope that outlives the transition) so they never hold it on Welcome. Heading back to Welcome (a tap or predictive
 * back) freezes them where they are, so the exit fades them out from there; if the gesture is cancelled, they finish
 * on `fade()`. Back at rest on Welcome they reset to 0; on any other step they start (and stay) at 1.
 */
@Composable
private fun rememberTopBarEntrance(
    stepState: State<OnboardingStep>,
    transition: Transition<OnboardingStep>,
    motion: ClockblockMotion,
): List<Animatable<Float, AnimationVector1D>> {
    val items = remember {
        val initial = Snapshot.withoutReadObservation { if (transition.currentState == OnboardingStep.Welcome) 0f else 1f }
        List(TopBarItems) { Animatable(initial) }
    }
    BindTopBarEntrance(items, stepState, transition, motion)
    return items
}

@Composable
private fun BindTopBarEntrance(
    items: List<Animatable<Float, AnimationVector1D>>,
    stepState: State<OnboardingStep>,
    transition: Transition<OnboardingStep>,
    motion: ClockblockMotion,
) {
    val scope = rememberCoroutineScope()
    val atWelcome by remember(transition) {
        derivedStateOf {
            transition.currentState == OnboardingStep.Welcome && transition.targetState == OnboardingStep.Welcome
        }
    }
    val leavingWelcome by remember(transition) {
        derivedStateOf {
            transition.currentState == OnboardingStep.Welcome && transition.targetState != OnboardingStep.Welcome
        }
    }
    // On the step too, not only the transition's target: that one changes a frame or two later, while the entrance
    // carries on dropping. Predictive back only moves the target.
    val headingToWelcome by remember(transition, stepState) {
        derivedStateOf {
            transition.currentState != OnboardingStep.Welcome &&
                (stepState.value == OnboardingStep.Welcome || transition.targetState == OnboardingStep.Welcome)
        }
    }
    val awayFromWelcome by remember(transition, headingToWelcome) {
        derivedStateOf {
            transition.currentState != OnboardingStep.Welcome && !headingToWelcome
        }
    }
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
}

/** The top bar's label, progress line and Skip. */
private const val TopBarItems = 3

/**
 * Reserves a step's room for the chrome around it: [top] (read in layout, so the home search can fold it away frame
 * by frame) and [bottom] pixels.
 */
private fun Modifier.stepRoom(top: () -> Int, bottom: () -> Int): Modifier = layout { measurable, constraints ->
    val topPx = top().coerceAtLeast(0)
    val bottomPx = bottom().coerceAtLeast(0)
    val inner = (constraints.maxHeight - topPx - bottomPx).coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, inner))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, topPx) }
}

/** Onboarding never grows wider than this: past it, the window only gains margins. */
private val OnboardingMaxWidth = 1080.dp

/** The top and bottom bars' opacity while the chronotype spectacle plays: still there, plainly not for now. */
private const val SpectacleBarsAlpha = 0.5f
