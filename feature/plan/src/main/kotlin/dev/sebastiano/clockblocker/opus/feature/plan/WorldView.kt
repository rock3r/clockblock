package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.circadian.secondaryZoneFor
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeCallout
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeColors
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeControls
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeInteraction
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeLeg
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeOverlay
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeStop
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.Pinch
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.RouteGlobe
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.RouteGlobeDefaults
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.RouteGlobeState
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.WorldCamera
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.WorldPinchRatio
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.pinchOf
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.pinchProgress
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.pinchTransition
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.rememberRouteGlobeState
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.settleTarget
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.TimeFormatter
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Sun
import dev.sebastiano.clockblocker.opus.core.model.Trip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import dev.sebastiano.clockblocker.opus.core.designsystem.R as DesignR
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeOverlayCover

/** Below this sun height (degrees) the place counts as dark: sunset, with refraction. */
private const val DarkBelowDegrees = -0.833

/**
 * The dial and, behind it, the world (the trip on the globe), with a zoom between them that the fingers drive.
 *
 * Pinching out on the dial pulls the camera back from "your day" to "the world" (see [WorldCamera]): the dial shrinks
 * onto the spot on Earth where the traveller is and fades, while the globe, zoomed in on that spot, pulls back to its
 * full view. The fingers drive the progress continuously; a release settles on `zoomSpatial()` (no bounce) towards the
 * side the fingers were heading (or the nearer one), carrying their speed; a cancelled touch goes back. Pinching in on
 * the world, Back (predictive, following the finger), or TalkBack's Back to the dial play it in reverse. TalkBack's
 * Show the world opens it.
 *
 * Reduce motion and Remove animations: nothing scales or moves. The pinch commits once past the old thresholds and the
 * two cross-fade on `fade()` (Remove animations: they swap at once); the globe and its caption are the static carrier.
 * The world isn't an easter egg: it works in sleep windows too.
 *
 * [open] is the screen's state ([onOpenChange] asks to change it); [backEnabled] false leaves Back to something over
 * the world (the full-screen globe); [dial] draws the dial with the given modifier and
 * TalkBack actions (Show the world). The dial's parameters don't change with the zoom, so it never recomposes for it:
 * the progress is read in layers only.
 */
@Composable
internal fun DialAndWorld(
    trip: Trip?,
    plan: JetLagPlan,
    at: Instant,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onExpand: () -> Unit,
    worldSize: Dp,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    dial: @Composable (Modifier, List<CustomAccessibilityAction>) -> Unit,
) {
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(if (open) 1f else 0f) }
    val globe = rememberRouteGlobeState()
    val currentOpen by rememberUpdatedState(open)
    val currentOnOpenChange by rememberUpdatedState(onOpenChange)
    // Plain holders: the gesture's bookkeeping never needs to recompose anything.
    val gesture = remember { WorldGesture() }
    val marker = remember(trip, at) { trip?.positionAt(at)?.point }

    fun spec() = if (reduce) motion.fade<Float>() else motion.zoomSpatial()

    fun settle(target: Float, velocity: Float = 0f) {
        gesture.settlingTo = target
        scope.launch { progress.animateTo(target, spec(), velocity) }
        val nowOpen = target == 1f
        if (nowOpen != currentOpen) currentOnOpenChange(nowOpen)
    }

    // Opened or closed from elsewhere (TalkBack, Back to the dial, a restored state): play the zoom there.
    LaunchedEffect(open) {
        val target = if (open) 1f else 0f
        if (gesture.pinching || gesture.settlingTo == target) return@LaunchedEffect
        gesture.settlingTo = target
        if (open && progress.value == 0f) startWorld(globe)
        progress.animateTo(target, spec())
    }

    // Back follows the finger back to the dial, and returns to the world if the gesture is cancelled.
    PredictiveBackHandler(enabled = open && backEnabled) { events ->
        try {
            events.collect { event -> if (!reduce) progress.snapTo(1f - event.progress) }
            settle(0f)
        } catch (e: CancellationException) {
            gesture.settlingTo = 1f
            scope.launch { progress.animateTo(1f, spec()) }
            throw e
        }
    }

    fun camera(): WorldCamera {
        val m = marker?.let { globe.project(it) } ?: Offset.Zero
        return WorldCamera.at(progress.value, m, still = reduce)
    }

    val worldAction = stringResource(R.string.plan_world_action)
    val showTheWorld = remember(trip != null, worldAction) {
        if (trip != null) listOf(CustomAccessibilityAction(worldAction) { currentOnOpenChange(true); true }) else emptyList()
    }

    Box(
        modifier.pinchTransition(
            enabled = trip != null,
            onStart = {
                gesture.from = if (reduce) progress.targetValue.roundToInt().toFloat() else progress.value
                gesture.committed = false
                gesture.pinching = !reduce
                if (gesture.from == 0f) scope.launch { startWorld(globe) }
                if (!reduce) scope.launch { progress.stop() }
            },
            onPinch = { ratio ->
                if (reduce) {
                    if (!gesture.committed) {
                        when (pinchOf(1f, ratio)) {
                            Pinch.Out -> if (gesture.from == 0f) { gesture.committed = true; settle(1f) }
                            Pinch.In -> if (gesture.from == 1f) { gesture.committed = true; settle(0f) }
                            null -> Unit
                        }
                    }
                } else {
                    val p = pinchProgress(gesture.from, ratio)
                    scope.launch { progress.snapTo(p) }
                }
            },
            onEnd = { logVelocity, cancelled ->
                if (gesture.pinching) {
                    gesture.pinching = false
                    val velocity = logVelocity / ln(WorldPinchRatio)
                    if (cancelled) {
                        settle(if (gesture.from >= 0.5f) 1f else 0f)
                    } else {
                        settle(settleTarget(progress.value, velocity), velocity)
                    }
                }
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                // Until the world is open, the dial is on top: it shrinks onto the globe (and grows back off it on
                // the way in) rather than behind it. Once open, the world is on top and takes the touches.
                .zIndex(if (open) 0f else 1f)
                .graphicsLayer {
                    val c = camera()
                    val r = RouteGlobeDefaults.radius(size.width, size.height)
                    scaleX = c.dialScale
                    scaleY = c.dialScale
                    translationX = c.dialCentre.x * r
                    translationY = c.dialCentre.y * r
                    alpha = c.dialAlpha
                    // No offscreen buffer for the fade (the first frames back were GPU-bound with one).
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .then(if (open) Modifier.clearAndSetSemantics { } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            dial(Modifier, showTheWorld)
        }
        // The world is always composed under the dial (at rest it's fully transparent, so it draws nothing): the zoom
        // only moves layers, and never waits on composing the globe in the middle of a gesture. Closed, it's out of
        // TalkBack's reach.
        if (trip != null) {
            WorldView(
                trip = trip,
                plan = plan,
                at = at,
                state = globe,
                camera = ::camera,
                open = open,
                onBack = { settle(0f) },
                onExpand = onExpand,
                modifier = Modifier
                    .widthIn(max = worldSize)
                    .then(if (open) Modifier else Modifier.clearAndSetSemantics { }),
            )
        }
    }
}

/** The pinch's bookkeeping between its callbacks. */
private class WorldGesture {
    var from = 0f
    var pinching = false
    var committed = false
    var settlingTo = -1f
}

/** The world opens framed on the trip, at zoom 1, with nothing picked: the zoom lands on the traveller's spot. */
private suspend fun startWorld(globe: RouteGlobeState) {
    globe.selection = null
    globe.recentre(null)
}

/** The disc the globe is drawn in: the zoomed-in globe of the zoom shows through the dial's own round footprint. */
private val GlobeDisc = GenericShape { size, _ ->
    val r = RouteGlobeDefaults.radius(size.width, size.height) * 1.04f
    val cx = size.width / 2f
    val cy = size.height / 2f
    addOval(Rect(cx - r, cy - r, cx + r, cy + r))
}

/**
 * The world behind the dial: the trip's route on the globe, the terminator at [at] (the dial's time), a dot where the
 * traveller is then, and the leg they're on bright. Drag to turn it (a flick coasts on), double-tap or the re-centre
 * button to frame the trip again, tap a flight or an airport to name it. The caption says where the traveller is, in
 * local time plus the trip's other zone ("It's 03:10 and dark in Tokyo · 19:10 in Lisbon"). The full-screen button
 * opens the [PlanWorldOverlay].
 *
 * TalkBack: the globe is one focusable node with the trip in words and every action (each flight, turn, re-centre,
 * full screen, Back to the dial). Opening the world announces it once, as a pane titled with where the traveller is
 * at that moment; the caption itself isn't a live region, so the ticking minute never interrupts.
 */
@Composable
internal fun WorldView(
    trip: Trip,
    plan: JetLagPlan,
    at: Instant,
    state: RouteGlobeState,
    camera: () -> WorldCamera,
    open: Boolean,
    onBack: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val legs = rememberWorldLegs(trip)
    val position = remember(trip, at) { trip.positionAt(at) }
    val caption = whereYouAre(trip, plan, at)
    val description = tripDescription(trip, plan.shiftHours)
    val back = stringResource(R.string.plan_world_back)
    val fullScreen = stringResource(DesignR.string.globe_full_screen)
    val actions = remember(back, fullScreen) {
        listOf(
            CustomAccessibilityAction(back) { onBack(); true },
            CustomAccessibilityAction(fullScreen) { onExpand(); true },
        )
    }
    // Announced once when the world opens: what it said then, not every minute after.
    val announced = remember(open) { caption }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .testTag(PlanTags.World)
            .then(if (open) Modifier.semantics { paneTitle = announced } else Modifier),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(GlobeDisc)
                .graphicsLayer {
                    val c = camera()
                    val r = RouteGlobeDefaults.radius(size.width, size.height)
                    scaleX = c.globeScale
                    scaleY = c.globeScale
                    translationX = c.globeTranslation.x * r
                    translationY = c.globeTranslation.y * r
                    alpha = c.globeAlpha
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        ) {
            RouteGlobe(
                legs = legs,
                instant = at,
                contentDescription = description,
                activeLeg = position.legIndex.takeIf { legs.size > 1 },
                showLabels = true,
                labelAlpha = { camera().chromeAlpha },
                marker = position.point,
                state = state,
                interaction = GlobeInteraction.Rotate,
                extraActions = actions,
                modifier = Modifier.fillMaxSize().testTag(PlanTags.WorldGlobe),
            )
        }
        WorldChrome(state, legs, caption, onExpand, Modifier.matchParentSize().graphicsLayer { alpha = camera().chromeAlpha })
    }
}

@Composable
private fun WorldChrome(state: RouteGlobeState, legs: List<GlobeLeg>, caption: String, onExpand: () -> Unit, modifier: Modifier) {
    val colors = GlobeColors.current
    Box(modifier) {
        IconButton(onClick = onExpand, modifier = Modifier.align(Alignment.TopEnd).testTag(PlanTags.WorldFullScreen)) {
            Surface(color = colors.scrim, contentColor = colors.label, shape = MaterialTheme.shapes.extraLarge) {
                Icon(
                    painterResource(DesignR.drawable.ic_globe_full_screen),
                    contentDescription = stringResource(DesignR.string.globe_full_screen),
                    modifier = Modifier.padding(6.dp),
                )
            }
        }
        GlobeCallout(state, legs, Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 8.dp, end = 8.dp))
        GlobeControls(state, Modifier.align(Alignment.BottomEnd).padding(bottom = 44.dp), zoomable = false)
        WorldCaption(caption, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
    }
}

@Composable
private fun WorldCaption(caption: String, modifier: Modifier) {
    val colors = GlobeColors.current
    Surface(color = colors.scrim, contentColor = colors.label, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(
            caption,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).testTag(PlanTags.WorldCaption),
        )
    }
}

/**
 * The world full screen (over the plan, or over its pane in two panes): the same trip, now zoomable, with the airport
 * codes beside their dots and the caption under the title. Close, Back (predictive) or the close button return.
 */
@Composable
internal fun PlanWorldOverlay(
    trip: Trip,
    plan: JetLagPlan,
    at: Instant,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cover: GlobeOverlayCover? = null,
) {
    val legs = rememberWorldLegs(trip)
    val position = remember(trip, at) { trip.positionAt(at) }
    val state = rememberRouteGlobeState()
    val description = tripDescription(trip, plan.shiftHours)
    // Opens framed on the trip, whatever it was left at.
    LaunchedEffect(visible) {
        if (visible) {
            state.selection = null
            state.recentre(null)
        }
    }
    GlobeOverlay(
        visible = visible,
        onDismiss = onDismiss,
        title = routeTitle(trip),
        subtitle = whereYouAre(trip, plan, at),
        state = state,
        cover = cover,
        modifier = modifier.testTag(PlanTags.WorldOverlay),
        globe = { globeModifier ->
            RouteGlobe(
                legs = legs,
                instant = at,
                contentDescription = description,
                activeLeg = position.legIndex.takeIf { legs.size > 1 },
                showLabels = true,
                marker = position.point,
                state = state,
                interaction = GlobeInteraction.RotateAndZoom,
                modifier = globeModifier.testTag(PlanTags.WorldOverlayGlobe),
            )
        },
        callout = { calloutModifier -> GlobeCallout(state, legs, calloutModifier) },
    )
}

/** The trip's flights for the globe, each named for when it's tapped ("Flight 2 · Doha to Tokyo"). */
@Composable
private fun rememberWorldLegs(trip: Trip): List<GlobeLeg> {
    val flight = stringResource(R.string.plan_world_leg_name)
    val single = stringResource(R.string.plan_world_route)
    return remember(trip, flight, single) {
        trip.legs.mapIndexed { i, leg ->
            val from = leg.origin.cityLabel()
            val to = leg.destination.cityLabel()
            GlobeLeg(
                from = leg.origin.stop(),
                to = leg.destination.stop(),
                name = if (trip.legs.size > 1) flight.format(i + 1, from, to) else single.format(from, to),
            )
        }
    }
}

private fun Place.cityLabel() = city.ifBlank { name }

private fun Place.stop() = GlobeStop(point, displayCode, cityLabel())

/**
 * Where the traveller is at [at], in words: local time and light where they are (the destination while in the air),
 * plus the time in the trip's other zone ("It's 03:10 and dark in Tokyo · 19:10 in Lisbon").
 */
@Composable
internal fun whereYouAre(trip: Trip, plan: JetLagPlan, at: Instant): String {
    val position = remember(trip, at) { trip.positionAt(at) }
    val formatter = rememberTimeFormatter()
    val zone = position.place.zone
    val time = formatter.formatFull(at.atZone(zone).toLocalTime())
    val dark = remember(position, at) { Sun.elevation(Sun.subsolarPoint(at), position.place.latitude, position.place.longitude) < DarkBelowDegrees }
    val local = stringResource(
        when {
            position.inAir && dark -> R.string.plan_world_in_air_dark
            position.inAir -> R.string.plan_world_in_air_light
            dark -> R.string.plan_world_dark
            else -> R.string.plan_world_light
        },
        time,
        position.place.cityLabel(),
    )
    val secondary = remember(plan, zone) { plan.secondaryZoneFor(zone) }
    // Zones that happen to share an offset (Lisbon and London) are still two places: only the same zone drops it.
    if (secondary == zone) return local
    return stringResource(R.string.plan_world_with_secondary, local, secondaryTime(formatter, at, secondary), secondary.cityName())
}

private fun secondaryTime(formatter: TimeFormatter, at: Instant, zone: java.time.ZoneId) = formatter.formatFull(at.atZone(zone).toLocalTime())

/** "Lisbon to Tokyo" (via the connections): the trip's route, for the full-screen world's title. */
@Composable
private fun routeTitle(trip: Trip): String {
    val cities = tripCities(trip)
    return when {
        cities.size <= 2 -> stringResource(R.string.plan_world_route, cities.first(), cities.last())
        else -> stringResource(R.string.plan_world_route_via, cities.first(), cities.last(), cities.subList(1, cities.size - 1).joinToString(", "))
    }
}

private fun tripCities(trip: Trip): List<String> = buildList {
    trip.legs.forEach { leg ->
        if (lastOrNull() != leg.origin) add(leg.origin)
        add(leg.destination)
    }
}.map { it.cityLabel() }

/** "Lisbon to Tokyo via Doha, 9 hours east": the trip in words for TalkBack. */
@Composable
internal fun tripDescription(trip: Trip, shiftHours: Double): String {
    val route = routeTitle(trip)
    val hours = timeDifferenceHours(shiftHours)
    if (abs(hours) < 1.0) return route
    val whole = abs(hours).roundToInt()
    val shift = if (abs(abs(hours) - whole) < 0.01) {
        pluralStringResource(if (hours > 0) R.plurals.plan_world_hours_east else R.plurals.plan_world_hours_west, whole, whole)
    } else {
        stringResource(if (hours > 0) R.string.plan_world_hours_east_fraction else R.string.plan_world_hours_west_fraction, abs(hours))
    }
    return "$route, $shift"
}
