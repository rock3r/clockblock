package dev.sebastiano.clockblocker.opus.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.navigation.AppTransitions

/**
 * Predictive back for the detail pane beside the list. Closing a plan there only swaps the pane's content for
 * [placeholder] (both panes stay put), so the scene has nothing of its own to preview while the user swipes. This
 * handler (the newest, so it runs ahead of the scene's) previews it: [content] shrinks and drifts back toward the
 * edge panes enter from ([AppTransitions.paneBack]), revealing the placeholder underneath; a cancelled gesture
 * settles back on the navigation spatial token, and a completed one calls [onBack].
 *
 * @param enabled false when the plan is a whole screen: then NavDisplay's own predictive back runs.
 */
@Composable
internal fun PaneBack(
    enabled: Boolean,
    transitions: AppTransitions,
    onBack: () -> Unit,
    placeholder: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    val progress = remember { Animatable(0f) }
    var completed by remember { mutableStateOf(false) }
    val currentOnBack by rememberUpdatedState(onBack)
    val settle = ClockblockTheme.motion.navigationSpatial<Float>()
    LaunchedEffect(state) {
        snapshotFlow { state.transitionState }.collect { transition ->
            when (transition) {
                is NavigationEventTransitionState.InProgress -> progress.snapTo(transition.latestEvent.progress)
                // A completed gesture keeps its last frame until the pane goes; a cancelled one springs back.
                NavigationEventTransitionState.Idle -> if (!completed) progress.animateTo(0f, settle)
            }
        }
    }
    NavigationBackHandler(
        state = state,
        isBackEnabled = enabled,
        onBackCompleted = {
            completed = true
            currentOnBack()
        },
    )
    // The placeholder only exists while there is something to reveal. A derived flag, so the gesture's frames
    // recompose nothing: only its start and end do.
    val revealing by remember {
        derivedStateOf { state.transitionState is NavigationEventTransitionState.InProgress || progress.value > 0f }
    }
    Box(Modifier.fillMaxSize()) {
        if (enabled && revealing) placeholder()
        Box(Modifier.fillMaxSize().graphicsLayer { transitions.paneBack(this, progress.value) }) {
            content()
        }
    }
}
