package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.R
import dev.sebastiano.clockblocker.opus.core.designsystem.component.SkyStatusBarIcons
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Test tags of the globe's shared parts. */
object GlobeTags {
    const val Callout = "globe_callout"
    const val CalloutClear = "globe_callout_clear"
    const val Recentre = "globe_recentre"
    const val ZoomIn = "globe_zoom_in"
    const val ZoomOut = "globe_zoom_out"
    const val OverlayClose = "globe_overlay_close"
}

/**
 * What's picked on the globe ([RouteGlobeState.selection]) in words, on the globe's scrim: "LIS → HND" over the
 * flight's name, or the airport's code and name. [action] adds a button for the pick (Go to ticket, Open trip). A
 * clear button (a 48 dp target) drops the pick; so does a tap on the globe away from the route. Announced politely to
 * TalkBack when it changes (it only changes when the user picks something). Fades in and out on `fade()`.
 */
@Composable
fun GlobeCallout(
    state: RouteGlobeState,
    legs: List<GlobeLeg>,
    modifier: Modifier = Modifier,
    action: (@Composable (GlobeSelection) -> Unit)? = null,
) {
    val motion = ClockblockTheme.motion
    val colors = GlobeColors.current
    val stops = remember(legs) { routeStops(legs) }
    val picked = state.selection
    // The last pick stays on screen while the callout fades out.
    var shown by remember { mutableStateOf(picked) }
    if (picked != null) shown = picked
    AnimatedVisibility(
        visible = picked != null,
        enter = fadeIn(motion.fade()),
        exit = fadeOut(motion.fade()),
        modifier = modifier,
    ) {
        val selection = shown ?: return@AnimatedVisibility
        val codes = stringResource(R.string.globe_leg_codes)
        val stopName = stringResource(R.string.globe_stop_name)
        val (title, subtitle) = when (selection) {
            is GlobeSelection.Leg -> legs.getOrNull(selection.index)?.let { codes.format(it.from.label, it.to.label) to it.name }
            is GlobeSelection.Stop -> stops.getOrNull(selection.index)?.let { stop ->
                (if (stop.name.isBlank()) stop.label else stopName.format(stop.label, stop.name)) to ""
            }
        } ?: return@AnimatedVisibility
        Surface(
            color = colors.scrim,
            contentColor = colors.label,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.testTag(GlobeTags.Callout),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = 8.dp)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.label.copy(alpha = 0.86f))
                    }
                }
                action?.let {
                    Spacer(Modifier.width(8.dp))
                    it(selection)
                }
                IconButton(onClick = { state.selection = null }, modifier = Modifier.testTag(GlobeTags.CalloutClear)) {
                    Icon(painterResource(R.drawable.ic_globe_close), contentDescription = stringResource(R.string.globe_clear_selection))
                }
            }
        }
    }
}

/**
 * Buttons for what a globe gesture does, for people who can't (or don't know to) drag, pinch or double-tap: zoom in
 * and out ([zoomable]) and re-centre (only once the globe has moved). The same actions are on the globe for TalkBack.
 */
@Composable
fun GlobeControls(state: RouteGlobeState, modifier: Modifier = Modifier, zoomable: Boolean = true) {
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    val scope = rememberCoroutineScope()
    val globeColors = GlobeColors.current
    val colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = globeColors.scrim,
        contentColor = globeColors.label,
        disabledContainerColor = globeColors.scrim.copy(alpha = 0.4f),
        disabledContentColor = globeColors.label.copy(alpha = 0.38f),
    )
    fun spec() = if (reduce) null else motion.zoomSpatial<Float>()
    // Derived, so a pinch or a zoom animation recomposes the buttons only when one turns on or off, not every frame.
    val canZoomIn by remember(state) { derivedStateOf { state.zoom < RouteGlobeState.MaxZoom } }
    val canZoomOut by remember(state) { derivedStateOf { state.zoom > 1f } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
        if (zoomable) {
            FilledTonalIconButton(
                onClick = { scope.launch { state.zoomTo(state.zoom * RouteGlobeDefaults.ZoomStep, spec()) } },
                enabled = canZoomIn,
                colors = colors,
                modifier = Modifier.testTag(GlobeTags.ZoomIn),
            ) {
                Icon(painterResource(R.drawable.ic_globe_zoom_in), contentDescription = stringResource(R.string.globe_action_zoom_in))
            }
            FilledTonalIconButton(
                onClick = { scope.launch { state.zoomTo(state.zoom / RouteGlobeDefaults.ZoomStep, spec()) } },
                enabled = canZoomOut,
                colors = colors,
                modifier = Modifier.testTag(GlobeTags.ZoomOut),
            ) {
                Icon(painterResource(R.drawable.ic_globe_zoom_out), contentDescription = stringResource(R.string.globe_action_zoom_out))
            }
        }
        if (state.moved) {
            FilledTonalIconButton(
                onClick = { scope.launch { state.recentre(spec()) } },
                colors = colors,
                modifier = Modifier.testTag(GlobeTags.Recentre),
            ) {
                Icon(painterResource(R.drawable.ic_globe_recentre), contentDescription = stringResource(R.string.globe_action_recentre))
            }
        }
    }
}

/**
 * A globe over everything in its container (the whole window, or just its pane in a list-detail layout), edge to
 * edge on the night sky: [title] and [subtitle] at the top beside a close button, the [globe] filling the rest, the
 * [callout] and the [GlobeControls] over its bottom, and [details] (stats) under it, or beside it in a wide window.
 *
 * It appears in place: in on `colour()` while growing from 92 % on `navigationSpatial()`, out the same way reversed.
 * Predictive back seeks the exit with the finger (and comes back if the gesture is cancelled); the close button and
 * Back close it. Reduce motion: the scale snaps and it only cross-fades. It blocks touches to what's under it; the
 * host hides what's under it from TalkBack while [visible] (the overlay announces itself with [title] as a pane).
 */
@Composable
fun GlobeOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    state: RouteGlobeState,
    globe: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    zoomable: Boolean = true,
    callout: @Composable (Modifier) -> Unit = {},
    details: (@Composable (Modifier) -> Unit)? = null,
) {
    val motion = ClockblockTheme.motion
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // Starts hidden so the entrance actually plays (an AnimatedVisibility created visible never animates in).
    val overlay = remember { SeekableTransitionState(false) }
    LaunchedEffect(visible) { overlay.animateTo(visible) }
    val transition = rememberTransition(overlay, label = "globe overlay")

    PredictiveBackHandler(enabled = visible) { progress ->
        try {
            progress.collect { event -> overlay.seekTo(event.progress, targetState = false) }
            overlay.animateTo(false)
            currentOnDismiss()
        } catch (e: CancellationException) {
            scope.launch { overlay.animateTo(true) }
            throw e
        }
    }
    SkyStatusBarIcons(ink = Color.White, ownsStatusBar = visible)

    transition.AnimatedVisibility(
        visible = { it },
        enter = fadeIn(motion.colour()) + scaleIn(motion.navigationSpatial(), initialScale = 0.92f),
        exit = fadeOut(motion.colour()) + scaleOut(motion.navigationSpatial(), targetScale = 0.92f),
        modifier = modifier,
    ) {
        val sky = ClockblockTheme.sky[SkyPhase.Night]
        val ink = GlobeColors.current.label
        // A Surface swallows touches, so nothing under the overlay reacts to them.
        Surface(color = sky.top, modifier = Modifier.fillMaxSize().semantics { paneTitle = title }) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(sky.top, sky.bottom)))) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag(GlobeTags.OverlayClose)) {
                            Icon(painterResource(R.drawable.ic_globe_close), contentDescription = stringResource(R.string.globe_close), tint = ink)
                        }
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(title, style = MaterialTheme.typography.titleLarge, color = ink, modifier = Modifier.semantics { heading() })
                            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.86f)) }
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                        val wide = details != null && maxWidth > maxHeight && maxWidth >= 600.dp
                        val globeArea: @Composable (Modifier) -> Unit = { areaModifier ->
                            Box(areaModifier) {
                                globe(Modifier.fillMaxSize())
                                Row(
                                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.BottomStart) { callout(Modifier) }
                                    Spacer(Modifier.width(12.dp))
                                    GlobeControls(state, zoomable = zoomable)
                                }
                            }
                        }
                        if (wide) {
                            Row(Modifier.fillMaxSize()) {
                                globeArea(Modifier.weight(1f).fillMaxHeight())
                                details?.invoke(
                                    Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 16.dp, bottom = 16.dp),
                                )
                            }
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                globeArea(Modifier.fillMaxWidth().weight(1f))
                                details?.invoke(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
