/*
 * Copyright 2026 Chris Sinco
 * Copyright 2026 Sebastiano Poggi and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from Prime Meridian's HalftoneGlobe
 * (https://github.com/c5inco/m3e-vibes/tree/main/apps/prime-meridian, Apache-2.0): the halftone land and sea, the
 * terminator shading by brightness buckets and the lifted great-circle arcs. Clockblock's changes: its own palette
 * and motion tokens, allocation-free drawing (precomputed unit vectors, reused buffers and paths), framing on the
 * active leg, see-through arcs on the back of the globe, airport labels on a scrim, tap-to-pick a leg or an airport,
 * and drag, fling and pinch to turn and zoom.
 */
package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.clockblocker.opus.core.designsystem.R
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.LocalReduceMotion
import dev.sebastiano.clockblocker.opus.core.model.SubsolarPoint
import dev.sebastiano.clockblocker.opus.core.model.Sun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A stop on the route: where it is, the label shown beside it at full size (the airport code) and its [name] (the
 * city or airport) for when it's tapped.
 */
@Immutable
data class GlobeStop(val point: GeoPoint, val label: String, val name: String = "")

/** One flight on the globe, with its [name] for when it's tapped ("Flight 2 · Doha to Tokyo"). */
@Immutable
data class GlobeLeg(val from: GlobeStop, val to: GlobeStop, val name: String = "")

/** What a [RouteGlobe] does with touch. */
enum class GlobeInteraction {
    /** Taps only, straight away (the small globe in a card): an arc → its leg, anywhere else → the click. */
    Tap,

    /** A large globe: drag to turn it (a flick coasts on), double-tap to re-centre, tap a flight or airport to pick it. */
    Rotate,

    /** [Rotate], plus two fingers to zoom in and out (the full-screen globe). */
    RotateAndZoom,
}

object RouteGlobeDefaults {
    /** The globe inside a card (the trip editor's Your shift). */
    val SmallSize: Dp = 180.dp

    /** How far TalkBack's turn actions (and one press of a key) turn the globe, in degrees. */
    const val TurnStepDegrees: Float = 30f

    /** The globe's radius as a fraction of half its canvas's shorter side (room for the lifted arcs and marker). */
    const val RadiusFraction: Float = 0.94f

    /** The globe's radius on a canvas [width] × [height] (at zoom 1). */
    fun radius(width: Float, height: Float): Float = globeRadius(width, height)

    /** How much TalkBack's zoom actions (and the zoom buttons) zoom in or out per step. */
    const val ZoomStep: Float = 1.5f

    /**
     * Loads the land dots now, on the calling thread. The globe otherwise loads the land off
     * the main thread on first use (drawing the sea meanwhile). Screens that may show a globe call this off the main
     * thread ahead of time (so its first frame does no setup); screenshot tests call it so the capture has the land.
     */
    fun preload(context: Context) {
        LandDots.load(context)
    }
}

/**
 * The route globe: an orthographic Earth drawn as halftone dots, shaded by the real day/night terminator at
 * [instant] (NOAA sun, `Sun.subsolarPoint`), with every leg as a great-circle arc and a dot at each stop. A night-sky
 * object in light and dark themes alike ([GlobeColors]).
 *
 * - **Framing:** it looks at leg [activeLeg] (drawn bright, the others dimmer), or at the whole route. When the view
 *   changes it turns on `dataSpatial()` (a geographic position: no bounce); under reduce motion it snaps. Nothing
 *   moves on its own, so there is no self-timed loop.
 * - **The back of the globe:** route that runs round the back is drawn see-through (faint, dashed), so a long
 *   itinerary still reads as one line.
 * - [showLabels]: airport codes beside the visible stops, on a scrim (the full-size view; too small in a card), at
 *   [labelAlpha] (read in draw, so a transition can fade them in without recomposing).
 * - [marker]: a dot for where the traveller is.
 * - [interaction]: [GlobeInteraction.Tap] (a card): an arc → [onLegTap] with its index (by default [onLegClick]);
 *   anywhere else → [onClick]. [GlobeInteraction.Rotate] / [GlobeInteraction.RotateAndZoom] (a large globe): drag
 *   to turn (a flick coasts on `globeFling()`, not under reduce motion), double-tap to re-centre, two fingers to zoom
 *   (RotateAndZoom only); a tap on an airport or an arc picks it ([RouteGlobeState.selection], shown by
 *   [GlobeCallout]) and an arc also calls [onLegTap]; a tap on neither clears the pick and calls [onClick].
 * - TalkBack gets [contentDescription] (the trip in words), [onClick] as the click action, one custom action per leg
 *   ([legActionLabel], calling [onLegClick] and picking the leg on a large globe), and on a large globe the turn,
 *   zoom and re-centre actions, then [extraActions].
 *
 * Drawing happens entirely in the draw phase from precomputed unit vectors, into reused buffers, paths and paints:
 * a turning globe doesn't recompose or allocate per frame.
 */
@Composable
fun RouteGlobe(
    legs: List<GlobeLeg>,
    instant: Instant,
    contentDescription: String,
    modifier: Modifier = Modifier,
    activeLeg: Int? = null,
    showLabels: Boolean = false,
    marker: GeoPoint? = null,
    onLegClick: ((Int) -> Unit)? = null,
    onLegTap: ((Int) -> Unit)? = onLegClick,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    legActionLabel: (Int) -> String = { "" },
    state: RouteGlobeState = rememberRouteGlobeState(),
    interaction: GlobeInteraction = GlobeInteraction.Tap,
    extraActions: List<CustomAccessibilityAction> = emptyList(),
    labelAlpha: () -> Float = { 1f },
) {
    val context = LocalContext.current
    val motion = ClockblockTheme.motion
    val reduce = LocalReduceMotion.current
    val colors = GlobeColors.current
    val scope = rememberCoroutineScope()
    val dots by produceState<LandDots?>(LandDots.cachedOrNull()) { if (value == null) value = withContext(Dispatchers.IO) { LandDots.load(context) } }

    val samples = remember(legs) { legs.map { GlobeGeo.greatCircle(it.from.point, it.to.point) } }
    val routes = remember(samples) { samples.map(::RouteVectors) }
    val stops = remember(legs) { routeStops(legs) }
    val stopPoints = remember(stops) { stops.map { it.point } }
    val stopVectors = remember(stops) { UnitVectors(stopPoints) }
    val active = activeLeg?.takeIf { it in legs.indices }
    val target = remember(samples, active) { GlobeGeo.routeView(samples, active) }

    // The first framing is at once (the globe opens already looking at its route); later ones turn to it.
    remember(state) { state.frameAtOnce(target) }
    LaunchedEffect(state, target, reduce) {
        if (state.framing == target) return@LaunchedEffect
        state.framing = target
        state.recentre(if (reduce) null else motion.dataSpatial())
    }
    // A pick on legs that have since changed (another trip, an edited route) means nothing any more, even when its
    // index still exists: the same index would name another flight or airport. The first legs a state sees keep any
    // pick made before the globe appeared.
    val seenLegs = remember(state) { arrayOfNulls<List<GlobeLeg>>(1) }
    LaunchedEffect(state, legs) {
        val previous = seenLegs[0]
        seenLegs[0] = legs
        if (previous != null && previous != legs) state.selection = null
    }

    // The terminator moves a degree every 4 minutes: refresh the shading every 5.
    val subsolar = remember(instant.epochSecond / TerminatorBucketSeconds) { Sun.subsolarPoint(instant) }
    val renderer = remember { GlobeRenderer() }

    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val labels: List<TextLayoutResult>? = if (showLabels) remember(stops, measurer, colors) { stops.map { measurer.measure(it.label, labelStyle) } } else null

    val currentOnLegTap by rememberUpdatedState(onLegTap)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentSamples by rememberUpdatedState(samples)
    val currentStops by rememberUpdatedState(stopPoints)
    val currentReduce by rememberUpdatedState(reduce)
    val currentMotion by rememberUpdatedState(motion)
    val large = interaction != GlobeInteraction.Tap
    val zoomable = interaction == GlobeInteraction.RotateAndZoom

    val turnEast = stringResource(R.string.globe_action_turn_east)
    val turnWest = stringResource(R.string.globe_action_turn_west)
    val turnNorth = stringResource(R.string.globe_action_turn_north)
    val turnSouth = stringResource(R.string.globe_action_turn_south)
    val zoomIn = stringResource(R.string.globe_action_zoom_in)
    val zoomOut = stringResource(R.string.globe_action_zoom_out)
    val recentre = stringResource(R.string.globe_action_recentre)
    val legCodes = stringResource(R.string.globe_leg_codes)
    // TalkBack is only offered the zoom and tilt steps that can still change the view, like the buttons' enabled state.
    val canZoomIn by remember(state) { derivedStateOf { state.zoom < RouteGlobeState.MaxZoom } }
    val canZoomOut by remember(state) { derivedStateOf { state.zoom > 1f } }
    val canTiltNorth by remember(state) { derivedStateOf { state.latitude.value < GlobeGeo.MaxDragLatitude } }
    val canTiltSouth by remember(state) { derivedStateOf { state.latitude.value > -GlobeGeo.MaxDragLatitude } }

    Canvas(
        modifier
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                if (onClick != null) {
                    role = Role.Button
                    onClick(label = onClickLabel) { onClick(); true }
                }
                val legActions = if (large || (onLegClick != null && (legs.size > 1 || (onClick == null && legs.isNotEmpty())))) {
                    legs.indices.map { i ->
                        val label = legActionLabel(i).ifEmpty { legs[i].name.ifEmpty { legCodes.format(legs[i].from.label, legs[i].to.label) } }
                        CustomAccessibilityAction(label) {
                            if (large) state.selection = GlobeSelection.Leg(i)
                            onLegClick?.invoke(i)
                            true
                        }
                    }
                } else {
                    emptyList()
                }
                val cameraActions = if (large) {
                    fun spec() = if (reduce) null else motion.zoomSpatial<Float>()
                    buildList {
                        add(CustomAccessibilityAction(turnEast) { scope.launch { state.turnBy(RouteGlobeDefaults.TurnStepDegrees, spec()) }; true })
                        add(CustomAccessibilityAction(turnWest) { scope.launch { state.turnBy(-RouteGlobeDefaults.TurnStepDegrees, spec()) }; true })
                        if (canTiltNorth) add(CustomAccessibilityAction(turnNorth) { scope.launch { state.tiltBy(RouteGlobeDefaults.TurnStepDegrees, spec()) }; true })
                        if (canTiltSouth) add(CustomAccessibilityAction(turnSouth) { scope.launch { state.tiltBy(-RouteGlobeDefaults.TurnStepDegrees, spec()) }; true })
                        if (zoomable && canZoomIn) {
                            add(CustomAccessibilityAction(zoomIn) { scope.launch { state.zoomTo(state.zoom * RouteGlobeDefaults.ZoomStep, spec()) }; true })
                        }
                        if (zoomable && canZoomOut) {
                            add(CustomAccessibilityAction(zoomOut) { scope.launch { state.zoomTo(state.zoom / RouteGlobeDefaults.ZoomStep, spec()) }; true })
                        }
                        if (state.moved) add(CustomAccessibilityAction(recentre) { scope.launch { state.recentre(spec()) }; true })
                    }
                } else {
                    emptyList()
                }
                val all = legActions + cameraActions + extraActions
                if (all.isNotEmpty()) customActions = all
            }
            .pointerInput(interaction) {
                fun radiusPx() = globeRadius(size.width.toFloat(), size.height.toFloat())

                // A tap at [tap], on the unit disc of the (zoomed) globe.
                fun disc(tap: Offset): Offset {
                    val radius = radiusPx() * state.zoom
                    return Offset((tap.x - size.width / 2f) / radius, -(tap.y - size.height / 2f) / radius)
                }

                fun tolerance(): Double = (TapTolerance.toPx() / (radiusPx() * state.zoom)).toDouble()

                if (!large) {
                    detectTapGestures { tap ->
                        val p = disc(tap)
                        val hit = currentOnLegTap?.let { GlobeGeo.hitLeg(p.x.toDouble(), p.y.toDouble(), currentSamples, state.view, tolerance()) }
                        if (hit != null) currentOnLegTap?.invoke(hit) else currentOnClick?.invoke()
                    }
                } else {
                    detectTapGestures(
                        onDoubleTap = {
                            state.selection = null
                            scope.launch { state.recentre(if (currentReduce) null else currentMotion.zoomSpatial()) }
                        },
                        onTap = { tap ->
                            val p = disc(tap)
                            val x = p.x.toDouble()
                            val y = p.y.toDouble()
                            val stop = GlobeGeo.hitStop(x, y, currentStops, state.view, tolerance())
                            val leg = if (stop == null) GlobeGeo.hitLeg(x, y, currentSamples, state.view, tolerance()) else null
                            when {
                                stop != null -> state.selection = GlobeSelection.Stop(stop)
                                leg != null -> {
                                    state.selection = GlobeSelection.Leg(leg)
                                    currentOnLegTap?.invoke(leg)
                                }
                                else -> {
                                    state.selection = null
                                    currentOnClick?.invoke()
                                }
                            }
                        },
                    )
                }
            }
            .then(
                if (!large) {
                    Modifier
                } else {
                    Modifier.pointerInput(interaction) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            // A touch catches a coasting globe at once; a turn to a newly framed leg only stops once
                            // the touch turns out to be a drag (a tap leaves the framing to finish).
                            if (state.flinging) scope.launch { state.stop() }
                            val slop = viewConfiguration.touchSlop
                            var dragging = false
                            var pan = Offset.Zero
                            var zoom = 1f
                            var taken = false
                            val velocity = VelocityTracker()
                            while (true) {
                                val event = awaitPointerEvent()
                                // Someone up the tree took the gesture (the plan's pinch back to the dial).
                                if (event.changes.any { it.isConsumed }) {
                                    taken = true
                                    break
                                }
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) {
                                    // The release itself is a sample: a finger that stopped before lifting doesn't fling.
                                    event.changes.firstOrNull()?.let { velocity.addPosition(it.uptimeMillis, it.position) }
                                    break
                                }
                                val zoomChange = if (zoomable) event.calculateZoom() else 1f
                                val panChange = event.calculatePan()
                                if (!dragging) {
                                    pan += panChange
                                    zoom *= zoomChange
                                    val zoomMotion = abs(1f - zoom) * event.calculateCentroidSize(useCurrent = false)
                                    dragging = pan.getDistance() > slop || zoomMotion > slop
                                    if (dragging) scope.launch { state.stop() }
                                }
                                if (dragging) {
                                    val radius = globeRadius(size.width.toFloat(), size.height.toFloat()) * state.zoom
                                    if (panChange != Offset.Zero) {
                                        val next = state.dragTarget(panChange.x / radius, panChange.y / radius)
                                        scope.launch { state.snapTo(next) }
                                    }
                                    if (zoomChange != 1f) {
                                        val level = state.zoomTarget(zoomChange)
                                        scope.launch { state.zoomLevel.snapTo(level) }
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                                // A fling only carries on from one finger: two lifting never line up.
                                if (pressed.size == 1) velocity.addPosition(pressed[0].uptimeMillis, pressed[0].position) else velocity.resetTracking()
                            }
                            state.endDrag()
                            if (dragging && !taken && !currentReduce) {
                                val v = velocity.calculateVelocity()
                                val radius = globeRadius(size.width.toFloat(), size.height.toFloat()) * state.zoom
                                if (v.x.isFinite() && v.y.isFinite() && (abs(v.x) + abs(v.y)) > 0f) {
                                    scope.launch { state.fling(v.x / radius, v.y / radius, currentMotion.globeFling()) }
                                }
                            }
                        }
                    }
                },
            ),
    ) {
        // Animated values are read here, in the draw phase only.
        renderer.draw(
            scope = this,
            colors = colors,
            latitude = state.latitude.value,
            longitude = state.longitude.value,
            zoom = state.zoomLevel.value,
            dots = dots,
            subsolar = subsolar,
            routes = routes,
            active = active,
            stops = stopVectors,
            labels = labels,
            labelAlpha = if (labels == null) 0f else labelAlpha(),
            marker = marker,
            selection = state.selection,
        )
    }
}

private const val TerminatorBucketSeconds = 300L
private val TapTolerance = 24.dp

internal fun globeRadius(width: Float, height: Float): Float = min(width, height) / 2f * RouteGlobeDefaults.RadiusFraction

/** The distinct stops in visit order (connections and hubs shared across trips aren't repeated). */
internal fun routeStops(legs: List<GlobeLeg>): List<GlobeStop> = buildList {
    legs.forEach { leg ->
        if (none { it.point == leg.from.point && it.label == leg.from.label }) add(leg.from)
        if (none { it.point == leg.to.point && it.label == leg.to.label }) add(leg.to)
    }
}

internal data class StopLabelInput(
    val ox: Float,
    val oy: Float,
    val width: Float,
    val height: Float,
    /** The candidate side this label used on the previous frame, or -1. Used for hysteresis. */
    val previousSide: Int = -1,
)

internal data class StopLabelPlacement(
    val left: Float,
    val top: Float,
    val scrimLeft: Float,
    val scrimTop: Float,
    val scrimRight: Float,
    val scrimBottom: Float,
    /** Which candidate side was chosen, to feed back as [StopLabelInput.previousSide] next frame. */
    val side: Int = 0,
)

/**
 * Places each visible stop's airport-code label beside its dot, choosing the candidate side (right, left, above,
 * below or diagonal) that avoids overlapping other stop dots and already-placed labels.
 *
 * Hysteresis: a label keeps last frame's side ([StopLabelInput.previousSide]) unless another side is better by more
 * than [switchCost]. Without it, a label whose side just grazes the canvas edge (a sub-pixel clamp) flips to the
 * other side of its dot and back from one frame to the next while the globe turns or zooms. Near an edge the label
 * now slides along the edge (clamped) until switching is clearly worth it.
 */
internal fun placeStopLabels(
    stops: List<StopLabelInput>,
    canvasWidth: Float,
    canvasHeight: Float,
    dotRadius: Float,
    gap: Float,
    pad: Float,
    switchCost: Float = gap * 3f,
): List<StopLabelPlacement> {
    if (stops.isEmpty()) return emptyList()
    val placed = ArrayList<StopLabelPlacement>(stops.size)
    for (s in stops) {
        val w = s.width
        val h = s.height
        val candidates = arrayOf(
            s.ox + gap to s.oy - h / 2f,
            s.ox - gap - w to s.oy - h / 2f,
            s.ox - w / 2f to s.oy - gap - h,
            s.ox - w / 2f to s.oy + gap,
            s.ox + gap to s.oy - gap - h,
            s.ox + gap to s.oy + gap,
            s.ox - gap - w to s.oy - gap - h,
            s.ox - gap - w to s.oy + gap,
        )
        var best: StopLabelPlacement? = null
        var bestScore = Float.POSITIVE_INFINITY
        for ((side, candidate) in candidates.withIndex()) {
            val (rawLeft, rawTop) = candidate
            val maxLeft = (canvasWidth - w - pad).coerceAtLeast(pad)
            val maxTop = (canvasHeight - h - pad).coerceAtLeast(pad)
            val left = rawLeft.coerceIn(pad, maxLeft)
            val top = rawTop.coerceIn(pad, maxTop)
            val sLeft = left - pad
            val sTop = top - pad / 2f
            val sRight = left + w + pad
            val sBottom = top + h + pad / 2f

            var overlap = 0f
            for (prev in placed) {
                overlap += rectOverlap(sLeft, sTop, sRight, sBottom, prev.scrimLeft, prev.scrimTop, prev.scrimRight, prev.scrimBottom)
            }
            for (dot in stops) {
                overlap += rectOverlap(
                    sLeft,
                    sTop,
                    sRight,
                    sBottom,
                    dot.ox - dotRadius,
                    dot.oy - dotRadius,
                    dot.ox + dotRadius,
                    dot.oy + dotRadius,
                )
            }
            val clampPenalty = kotlin.math.abs(left - rawLeft) + kotlin.math.abs(top - rawTop)
            val stickiness = if (s.previousSide >= 0 && side != s.previousSide) switchCost else 0f
            val score = overlap * 100f + clampPenalty + stickiness
            if (score < bestScore) {
                bestScore = score
                best = StopLabelPlacement(left, top, sLeft, sTop, sRight, sBottom, side)
            }
        }
        placed += best!!
    }
    return placed
}

private fun rectOverlap(
    ax0: Float,
    ay0: Float,
    ax1: Float,
    ay1: Float,
    bx0: Float,
    by0: Float,
    bx1: Float,
    by1: Float,
): Float {
    val dx = (min(ax1, bx1) - maxOf(ax0, bx0)).coerceAtLeast(0f)
    val dy = (min(ay1, by1) - maxOf(ay0, by0)).coerceAtLeast(0f)
    return dx * dy
}

/** Unit vectors (x, y, z) of points on the sphere, for cheap per-frame rotation. */
internal class UnitVectors(points: List<GeoPoint>) {
    val size = points.size
    val x = FloatArray(size)
    val y = FloatArray(size)
    val z = FloatArray(size)

    init {
        points.forEachIndexed { i, p ->
            val phi = Math.toRadians(p.latitude)
            val lambda = Math.toRadians(p.longitude)
            x[i] = (cos(phi) * cos(lambda)).toFloat()
            y[i] = (cos(phi) * sin(lambda)).toFloat()
            z[i] = sin(phi).toFloat()
        }
    }
}

/** A leg's great circle as unit vectors, with the lift (1 at the ends, up to 1.05 mid-flight) for each point. */
private class RouteVectors(points: List<GeoPoint>) {
    val vectors = UnitVectors(points)
    val lift = FloatArray(points.size) { i ->
        val f = if (points.size > 1) i / (points.size - 1f) else 0f
        1f + 0.05f * sin(PI * f).toFloat()
    }
}

/**
 * Land as dots on [HalftoneGrid.Land], precomputed from Natural Earth 110 m land (public domain), baked into the
 * [mask] the halftone shader samples.
 */
internal class LandDots(val mask: Bitmap) {
    companion object {
        @Volatile private var cached: LandDots? = null

        fun cachedOrNull(): LandDots? = cached

        fun load(context: Context): LandDots = cached ?: synchronized(this) {
            cached ?: context.assets.open("land_dots.bin").use { stream ->
                val input = DataInputStream(stream.buffered())
                val count = input.readInt()
                val latitudes = ShortArray(count)
                val longitudes = ShortArray(count)
                for (i in 0 until count) {
                    latitudes[i] = input.readShort()
                    longitudes[i] = input.readShort()
                }
                LandDots(GlobeHalftoneShader.landMask(latitudes, longitudes))
            }.also { cached = it }
        }
    }
}

/** Reused buffers, paths and paints: one per globe on screen. */
private class GlobeRenderer {
    // Sea, land and city lights: two shader draws (see GlobeHalftoneShader), nothing per dot on the CPU.
    private val halftone = GlobeHalftoneShader()

    /** Each stop's label side from the last frame (-1 = not shown), for label hysteresis. */
    private var labelSides = IntArray(0)
    private val frontPaths = ArrayList<Path>()
    private val backPaths = ArrayList<Path>()
    private val rotation = Rotation()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowMatrix = android.graphics.Matrix()
    private var glowColor: Color? = null
    private var strokes: Strokes? = null
    private var strokesDensity = -1f

    private class Strokes(density: Float) {
        val dashes = PathEffect.dashPathEffect(floatArrayOf(4f * density, 4f * density))
        val back = Stroke(1.6f * density, cap = StrokeCap.Round, pathEffect = dashes)
        val dim = Stroke(2f * density, cap = StrokeCap.Round)
        val active = Stroke(3f * density, cap = StrokeCap.Round)
        val rim = Stroke(1.2f * density)
        val ring = Stroke(1.4f * density)
        val halo = Stroke(8f * density, cap = StrokeCap.Round)
        val picked = Stroke(2f * density)
    }

    @Suppress("LongParameterList")
    fun draw(
        scope: DrawScope,
        colors: GlobeColors,
        latitude: Float,
        longitude: Float,
        zoom: Float,
        dots: LandDots?,
        subsolar: SubsolarPoint,
        routes: List<RouteVectors>,
        active: Int?,
        stops: UnitVectors,
        labels: List<TextLayoutResult>?,
        labelAlpha: Float,
        marker: GeoPoint?,
        selection: GlobeSelection?,
    ) = with(scope) {
        // Zoomed in, the globe is bigger than its canvas: keep it inside, so it never draws over its neighbours.
        if (zoom > 1.001f) {
            clipRect { drawGlobe(colors, latitude, longitude, zoom, dots, subsolar, routes, active, stops, labels.takeIf { labelAlpha > 0f }, labelAlpha, marker, selection) }
        } else {
            drawGlobe(colors, latitude, longitude, zoom, dots, subsolar, routes, active, stops, labels.takeIf { labelAlpha > 0f }, labelAlpha, marker, selection)
        }
    }

    @Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
    private fun DrawScope.drawGlobe(
        colors: GlobeColors,
        latitude: Float,
        longitude: Float,
        zoom: Float,
        dots: LandDots?,
        subsolar: SubsolarPoint,
        routes: List<RouteVectors>,
        active: Int?,
        stops: UnitVectors,
        labels: List<TextLayoutResult>?,
        labelAlpha: Float,
        marker: GeoPoint?,
        selection: GlobeSelection?,
    ) {
        val radius = globeRadius(size.width, size.height) * zoom
        val cx = size.width / 2f
        val cy = size.height / 2f
        rotation.update(latitude, longitude)
        val s = strokes?.takeIf { strokesDensity == density } ?: Strokes(density).also { strokes = it; strokesDensity = density }

        // Ocean, with the day side glowing from under the sun.
        drawCircle(colors.ocean, radius, Offset(cx, cy))
        // The subsolar point as a unit vector, and where it is in the view.
        val sunPhi = Math.toRadians(subsolar.latitude)
        val sunLambda = Math.toRadians(subsolar.longitude)
        val px = (cos(sunPhi) * cos(sunLambda)).toFloat()
        val py = (cos(sunPhi) * sin(sunLambda)).toFloat()
        val pz = sin(sunPhi).toFloat()
        val sx = rotation.x(px, py)
        val sy = rotation.y(px, py, pz)
        val sd = rotation.depth(px, py, pz)
        // The day side's glow: one radial shader (built once per colour, moved with a matrix) filling the globe's own
        // circle, so no clip is needed (a path clip costs the GPU a mask every frame while the globe turns).
        if (glowColor != colors.oceanDay) {
            glowPaint.shader = android.graphics.RadialGradient(
                0f, 0f, 1f,
                intArrayOf(colors.oceanDay.toArgb(), Color.Transparent.toArgb()),
                null,
                android.graphics.Shader.TileMode.CLAMP,
            )
            glowColor = colors.oceanDay
        }
        glowMatrix.setScale(radius * 1.25f, radius * 1.25f)
        glowMatrix.postTranslate(cx + sx * radius * 0.85f, cy - sy * radius * 0.85f)
        glowPaint.shader.setLocalMatrix(glowMatrix)
        glowPaint.alpha = ((if (sd >= 0f) 0.95f else 0.35f) * 255).toInt()
        drawContext.canvas.nativeCanvas.drawCircle(cx, cy, radius, glowPaint)

        // Sea halftone.
        val canvas = drawContext.canvas.nativeCanvas
        halftone.update(colors, cx, cy, radius, rotation.sinPhi0, rotation.cosPhi0, rotation.sinLambda0, rotation.cosLambda0, px, py, pz)
        halftone.drawSea(canvas, cx, cy, radius)
        drawCircle(colors.rim, radius, Offset(cx, cy), style = s.rim)

        // Route round the back of the globe: see-through, under the land.
        buildRoutes(routes, rotation, cx, cy, radius)
        routes.indices.forEach { i ->
            val alpha = if (active == null || active == i) 0.55f else 0.3f
            drawPath(backPaths[i], colors.routeDim.copy(alpha = alpha), style = s.back)
        }

        // Land and city lights.
        dots?.let { halftone.drawLand(canvas, cx, cy, radius, it.mask) }

        // Route on the visible face: the active leg bright, the others dimmer; a picked leg on a light halo.
        val pickedLeg = (selection as? GlobeSelection.Leg)?.index?.takeIf { it in routes.indices }
        pickedLeg?.let { drawPath(frontPaths[it], colors.stop.copy(alpha = 0.35f), style = s.halo) }
        routes.indices.forEach { i ->
            if (active != null && i != active) drawPath(frontPaths[i], colors.routeDim.copy(alpha = 0.6f), style = s.dim)
        }
        routes.indices.forEach { i ->
            if (active == null || i == active) drawPath(frontPaths[i], colors.route, style = s.active)
        }
        pickedLeg?.let { if (active != null && it != active) drawPath(frontPaths[it], colors.route, style = s.active) }

        // Stops (a picked one in a wider ring), then their labels on a scrim.
        val dotRadius = 3.5f * density
        val pickedStop = (selection as? GlobeSelection.Stop)?.index
        for (i in 0 until stops.size) {
            val px = stops.x[i]
            val py = stops.y[i]
            val pz = stops.z[i]
            val o = Offset(cx + rotation.x(px, py) * radius, cy - rotation.y(px, py, pz) * radius)
            if (rotation.depth(px, py, pz) >= 0f) {
                drawCircle(colors.stop, dotRadius, o)
                drawCircle(colors.route, dotRadius + 1.6f * density, o, style = s.ring)
                if (i == pickedStop) drawCircle(colors.stop, dotRadius + 6f * density, o, style = s.picked)
            } else {
                drawCircle(colors.routeDim.copy(alpha = 0.45f), dotRadius, o, style = s.ring)
            }
        }
        labels?.let { layouts ->
            val pad = 4f * density
            val gap = dotRadius + 6f * density
            val visibleIndices = ArrayList<Int>(stops.size)
            val inputs = ArrayList<StopLabelInput>(stops.size)
            for (i in 0 until stops.size) {
                val px = stops.x[i]
                val py = stops.y[i]
                val pz = stops.z[i]
                if (rotation.depth(px, py, pz) < 0f) continue
                val layout = layouts.getOrNull(i) ?: continue
                val ox = cx + rotation.x(px, py) * radius
                val oy = cy - rotation.y(px, py, pz) * radius
                // Zoomed in, a stop on the near side can still be off the canvas: no label for a dot you can't see.
                if (ox < 0f || ox > size.width || oy < 0f || oy > size.height) continue
                visibleIndices += i
                inputs += StopLabelInput(
                    ox = ox,
                    oy = oy,
                    width = layout.size.width.toFloat(),
                    height = layout.size.height.toFloat(),
                    previousSide = labelSides.getOrElse(i) { -1 },
                )
            }
            val placements = placeStopLabels(
                stops = inputs,
                canvasWidth = size.width,
                canvasHeight = size.height,
                dotRadius = dotRadius + 2f * density,
                gap = gap,
                pad = pad,
            )
            if (labelSides.size != stops.size) labelSides = IntArray(stops.size) { -1 }
            labelSides.fill(-1)
            for (k in visibleIndices.indices) {
                val layout = layouts[visibleIndices[k]]
                val p = placements[k]
                labelSides[visibleIndices[k]] = p.side
                drawRoundRect(
                    colors.scrim,
                    topLeft = Offset(p.scrimLeft, p.scrimTop),
                    size = Size(p.scrimRight - p.scrimLeft, p.scrimBottom - p.scrimTop),
                    cornerRadius = CornerRadius(pad, pad),
                    alpha = labelAlpha,
                )
                drawText(layout, topLeft = Offset(p.left, p.top), alpha = labelAlpha)
            }
        }

        // Where the traveller is.
        marker?.let { m ->
            val phi = Math.toRadians(m.latitude)
            val lambda = Math.toRadians(m.longitude)
            val px = (cos(phi) * cos(lambda)).toFloat()
            val py = (cos(phi) * sin(lambda)).toFloat()
            val pz = sin(phi).toFloat()
            if (rotation.depth(px, py, pz) >= 0f) {
                val o = Offset(cx + rotation.x(px, py) * radius * 1.03f, cy - rotation.y(px, py, pz) * radius * 1.03f)
                drawCircle(colors.stop, 6f * density, o)
                drawCircle(colors.route, 4f * density, o)
            }
        }
    }

    private fun buildRoutes(routes: List<RouteVectors>, rotation: Rotation, cx: Float, cy: Float, radius: Float) {
        while (frontPaths.size < routes.size) {
            frontPaths += Path()
            backPaths += Path()
        }
        routes.forEachIndexed { index, route ->
            val front = frontPaths[index]
            val back = backPaths[index]
            front.reset()
            back.reset()
            var frontPen = false
            var backPen = false
            val v = route.vectors
            for (i in 0 until v.size) {
                val px = v.x[i]
                val py = v.y[i]
                val pz = v.z[i]
                val depth = rotation.depth(px, py, pz)
                val x = rotation.x(px, py)
                val y = rotation.y(px, py, pz)
                if (depth >= 0f) {
                    val lift = route.lift[i]
                    val ox = cx + x * lift * radius
                    val oy = cy - y * lift * radius
                    if (frontPen) front.lineTo(ox, oy) else front.moveTo(ox, oy)
                    frontPen = true
                    backPen = false
                } else {
                    val ox = cx + x * radius
                    val oy = cy - y * radius
                    if (backPen) back.lineTo(ox, oy) else back.moveTo(ox, oy)
                    backPen = true
                    frontPen = false
                }
            }
        }
    }

    /** The view's rotation, applied to unit vectors: three multiply-adds per point. */
    private class Rotation {
        var sinPhi0 = 0f
            private set
        var cosPhi0 = 1f
            private set
        var sinLambda0 = 0f
            private set
        var cosLambda0 = 1f
            private set

        fun update(latitude: Float, longitude: Float) {
            sinPhi0 = sin(Math.toRadians(latitude.toDouble())).toFloat()
            cosPhi0 = cos(Math.toRadians(latitude.toDouble())).toFloat()
            sinLambda0 = sin(Math.toRadians(longitude.toDouble())).toFloat()
            cosLambda0 = cos(Math.toRadians(longitude.toDouble())).toFloat()
        }

        fun x(px: Float, py: Float): Float = -px * sinLambda0 + py * cosLambda0
        fun y(px: Float, py: Float, pz: Float): Float = cosPhi0 * pz - sinPhi0 * (px * cosLambda0 + py * sinLambda0)
        fun depth(px: Float, py: Float, pz: Float): Float = sinPhi0 * pz + cosPhi0 * (px * cosLambda0 + py * sinLambda0)
    }
}
