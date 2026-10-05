package dev.sebastiano.clockblocker.opus.feature.onboarding

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** Test tags for the e2e suite. */
object OnboardingTags {
    const val Progress = "onboarding_progress"
    const val Next = "onboarding_next"
    const val Back = "onboarding_back"
    const val Skip = "onboarding_skip"
    const val GetStarted = "onboarding_get_started"
    const val Finish = "onboarding_finish"
    const val MaybeLater = "onboarding_maybe_later"

    /** `onboarding_step_Welcome` … `onboarding_step_Reminders`, on each step's root. */
    fun step(step: OnboardingStep): String = "onboarding_step_${step.name}"
}

/** Everything the onboarding UI can ask for. The route binds it to [OnboardingViewModel]; tests use no-ops. */
interface OnboardingActions {
    fun next()
    fun back()
    fun skip()
    fun queryChange(query: String)
    fun selectPlace(place: Place)
    fun useDeviceZone()
    fun sleepChange(window: SleepWindow)
    fun chronotypeChange(chronotype: Chronotype)
    fun profileChange(profile: UserProfile)
    fun melatoninChange(enabled: Boolean)
    fun acknowledgeMelatonin(acknowledged: Boolean)
    fun intensityChange(intensity: Intensity)
    fun requestNotifications(thenFinish: Boolean)
    fun openExactAlarmSettings()
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
        override fun chronotypeChange(chronotype: Chronotype) = Unit
        override fun profileChange(profile: UserProfile) = Unit
        override fun melatoninChange(enabled: Boolean) = Unit
        override fun acknowledgeMelatonin(acknowledged: Boolean) = Unit
        override fun intensityChange(intensity: Intensity) = Unit
        override fun requestNotifications(thenFinish: Boolean) = Unit
        override fun openExactAlarmSettings() = Unit
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
    var finishAfterPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermissions()
        if (finishAfterPermission) viewModel.finish()
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
            override fun chronotypeChange(chronotype: Chronotype) = viewModel.setChronotype(chronotype)
            override fun profileChange(profile: UserProfile) = viewModel.updateProfile { profile }
            override fun melatoninChange(enabled: Boolean) = viewModel.setMelatonin(enabled)
            override fun acknowledgeMelatonin(acknowledged: Boolean) = viewModel.acknowledgeMelatoninNote(acknowledged)
            override fun intensityChange(intensity: Intensity) = viewModel.setIntensity(intensity)
            override fun requestNotifications(thenFinish: Boolean) {
                val permission = viewModel.runtimePermission
                if (permission != null) {
                    finishAfterPermission = thenFinish
                    permissionLauncher.launch(permission)
                } else {
                    // Below API 33 there is no runtime prompt: notifications were switched off in system settings.
                    launchSafely(context, viewModel.notificationSettingsIntent())
                    if (thenFinish) viewModel.finish()
                }
            }
            override fun openExactAlarmSettings() = launchSafely(context, viewModel.exactAlarmSettingsIntent())
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
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingContent(state: OnboardingUiState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    val step = state.step
    val steps = rememberOnboardingStepTransition(step, onBack = actions::back)
    // Each step keeps its saved state while you move away and back (T-017: the welcome clocks don't re-slide,
    // art entrances don't replay, scroll positions and open helpers stay put).
    val stepStates = rememberSaveableStateHolder()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val wide = maxWidth >= 840.dp || (maxWidth >= 600.dp && maxWidth > maxHeight)
        val short = maxHeight < 480.dp
        Column(Modifier.fillMaxSize()) {
            OnboardingTopBar(step, onSkip = actions::skip)
            steps.transition.AnimatedContent(
                transitionSpec = { steps.transitionFor(initialState, targetState) },
                contentKey = { it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { target ->
                stepStates.SaveableStateProvider(target.name) {
                    Box(Modifier.fillMaxSize().testTag(OnboardingTags.step(target))) {
                        OnboardingStepBody(target, state, actions, wide)
                    }
                }
            }
            OnboardingBottomBar(state, actions, wide, short = short)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnboardingTopBar(step: OnboardingStep, onSkip: () -> Unit) {
    val motion = OpusTheme.motion
    val reduce = OpusTheme.reduceMotion
    val visible = step != OnboardingStep.Welcome
    AnimatedVisibility(visible = visible, enter = fadeIn(motion.fade()), exit = fadeOut(motion.fade())) {
        val target = (step.index + 1f) / OnboardingStep.Count
        val progress by animateFloatAsState(target, motion.dataSpatial(), label = "onboardingProgress")
        val label = stringResource(R.string.onboarding_progress, step.index + 1, OnboardingStep.Count)
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(6.dp))
                // "Wavy = jet-lagged, flat = adapted": the line calms down as setup completes.
                LinearWavyProgressIndicator(
                    progress = { progress },
                    amplitude = { p -> (1f - p).coerceIn(0f, 1f) },
                    wavelength = 28.dp,
                    waveSpeed = if (reduce) 0.dp else 14.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = label }
                        .testTag(OnboardingTags.Progress),
                )
            }
            Spacer(Modifier.width(8.dp))
            val skippable = step != OnboardingStep.Reminders
            TextButton(
                onClick = onSkip,
                enabled = skippable,
                modifier = Modifier.heightIn(min = 48.dp).testTag(OnboardingTags.Skip).graphicsLayer { alpha = if (skippable) 1f else 0f },
            ) { Text(stringResource(R.string.onboarding_skip)) }
        }
    }
}

@Composable
private fun OnboardingBottomBar(state: OnboardingUiState, actions: OnboardingActions, wide: Boolean, short: Boolean) {
    val step = state.step
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = if (short) 8.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (step == OnboardingStep.Welcome) Arrangement.Center else Arrangement.SpaceBetween,
    ) {
        when (step) {
            OnboardingStep.Welcome -> Button(
                onClick = actions::next,
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                modifier = Modifier
                    .heightIn(min = ButtonDefaults.MediumContainerHeight)
                    .then(if (wide) Modifier.widthIn(min = 280.dp) else Modifier.fillMaxWidth())
                    .testTag(OnboardingTags.GetStarted),
            ) { Text(stringResource(R.string.welcome_get_started), style = MaterialTheme.typography.titleMedium) }
            OnboardingStep.Reminders -> {
                TextButton(
                    onClick = actions::finish,
                    enabled = !state.isSaving,
                    modifier = Modifier.heightIn(min = 56.dp).testTag(OnboardingTags.MaybeLater),
                ) { Text(stringResource(R.string.reminders_maybe_later)) }
                val granted = state.permissions.notificationsGranted
                Button(
                    onClick = { if (granted) actions.finish() else actions.requestNotifications(thenFinish = true) },
                    enabled = !state.isSaving,
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                    modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight).testTag(OnboardingTags.Finish),
                ) { Text(stringResource(if (granted) R.string.onboarding_finish else R.string.reminders_allow_finish)) }
            }
            else -> {
                OutlinedButton(
                    onClick = actions::back,
                    modifier = Modifier.heightIn(min = 56.dp).testTag(OnboardingTags.Back),
                ) {
                    Icon(
                        painterResource(R.drawable.onboarding_ic_arrow_back),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).graphicsLayer { scaleX = if (rtl) -1f else 1f },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.onboarding_back))
                }
                Button(
                    onClick = actions::next,
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                    modifier = Modifier
                        .heightIn(min = ButtonDefaults.MediumContainerHeight)
                        .widthIn(min = 140.dp)
                        .testTag(OnboardingTags.Next),
                ) { Text(stringResource(R.string.onboarding_next), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
