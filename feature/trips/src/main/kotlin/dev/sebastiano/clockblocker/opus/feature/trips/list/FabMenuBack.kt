package dev.sebastiano.clockblocker.opus.feature.trips.list

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Predictive back for the trips FAB menu. While the back gesture is in progress the open menu collapses with
 * the finger: [collapseProgress] goes 0 → 1 and the menu items and scrim read it in their draw/layer phase.
 *
 * - **Commit**: the menu closes and whatever the gesture left visible fades out from where it is (no replay).
 * - **Cancel**: the menu springs back open on the navigation spatial token.
 * - **Button back** (no gesture): progress stays 0 and the M3 menu plays its own close.
 *
 * The M3 `FloatingActionButtonMenu` owns its open/close transition and exposes only `expanded`, so it can't be
 * seeked directly; the gesture drives an overlay transform on top of it instead.
 */
@Stable
internal class FabMenuBack(private val scope: CoroutineScope) {
    private val progress = Animatable(0f)

    /** True from commit until the menu is opened again, so late gesture events can't revive the menu. */
    internal var settling: Boolean = false
        private set

    /** 0 = fully open, 1 = collapsed by the back gesture. Read it in layout/draw only. */
    fun collapseProgress(): Float = progress.value

    /** Call when the menu opens: it always opens from its resting, uncollapsed state. */
    fun reset() {
        settling = false
        scope.launch { progress.snapTo(0f) }
    }

    internal suspend fun follow(fraction: Float) {
        if (!settling) progress.snapTo(fraction.coerceIn(0f, 1f))
    }

    internal fun commit(fadeOut: suspend Animatable<Float, *>.() -> Unit) {
        settling = true
        if (progress.value > 0f) scope.launch { progress.fadeOut() }
    }

    internal fun cancel(springBack: suspend Animatable<Float, *>.() -> Unit) {
        scope.launch { progress.springBack() }
    }
}

/** Registers the menu's back handler (enabled while [expanded]) and returns the gesture state. */
@Composable
internal fun rememberFabMenuBack(expanded: Boolean, onCollapse: () -> Unit): FabMenuBack {
    val motion = OpusTheme.motion
    val scope = rememberCoroutineScope()
    val back = remember(scope) { FabMenuBack(scope) }
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    LaunchedEffect(backState, back) {
        snapshotFlow { (backState.transitionState as? NavigationEventTransitionState.InProgress)?.latestEvent?.progress }
            .filterNotNull()
            .collect { back.follow(it) }
    }
    val currentOnCollapse by rememberUpdatedState(onCollapse)
    NavigationBackHandler(
        state = backState,
        isBackEnabled = expanded,
        onBackCancelled = { back.cancel { animateTo(0f, motion.navigationSpatial()) } },
        onBackCompleted = {
            back.commit { animateTo(1f, motion.colour()) }
            currentOnCollapse()
        },
    )
    return back
}
