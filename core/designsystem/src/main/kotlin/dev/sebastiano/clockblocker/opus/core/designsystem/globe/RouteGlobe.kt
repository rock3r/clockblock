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
 * active leg, see-through arcs on the back of the globe, airport labels on a scrim, and tap-to-pick a leg.
 */
package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import android.content.Context
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A stop on the route: where it is and the label shown beside it at full size (the airport code). */
@Immutable
data class GlobeStop(val point: GeoPoint, val label: String)

/** One flight on the globe. */
@Immutable
data class GlobeLeg(val from: GlobeStop, val to: GlobeStop)

object RouteGlobeDefaults {
    /** The globe inside a card (the trip editor's Your shift). */
    val SmallSize: Dp = 180.dp

    /**
     * Loads the land dots and builds the sea grid now, on the calling thread. The globe otherwise loads the land off
     * the main thread on first use (drawing the sea meanwhile). Screens that may show a globe call this off the main
     * thread ahead of time (so its first frame does no setup); screenshot tests call it so the capture has the land.
     */
    fun preload(context: Context) {
        LandDots.load(context)
        SeaGrid.size
    }
}

/**
 * The route globe: an orthographic Earth drawn as halftone dots, shaded by the real day/night terminator at
 * [instant] (NOAA sun, `Sun.subsolarPoint`), with every leg as a great-circle arc and a dot at each stop. A night-sky
 * object in light and dark themes alike.
 *
 * - **Framing:** it looks at leg [activeLeg] (drawn bright, the others dimmer), or at the whole route. When the view
 *   changes it turns on `dataSpatial()` (a geographic position: no bounce); under reduce motion it snaps. Nothing
 *   moves on its own, so there is no self-timed loop.
 * - **The back of the globe:** route that runs round the back is drawn see-through (faint, dashed), so a long
 *   itinerary still reads as one line.
 * - [showLabels]: airport codes beside the visible stops, on a scrim (the full-size view; too small in a card).
 * - [marker]: a dot for where the traveller is.
 * - Taps: on an arc → [onLegTap] with its index (by default [onLegClick]); anywhere else → [onClick]. TalkBack gets
 *   [contentDescription] (the trip in words), [onClick] as the click action and one custom action per leg
 *   ([legActionLabel], calling [onLegClick]).
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
) {
    val context = LocalContext.current
    val motion = ClockblockTheme.motion
    val reduce = LocalReduceMotion.current
    val dots by produceState<LandDots?>(LandDots.cachedOrNull()) { if (value == null) value = withContext(Dispatchers.IO) { LandDots.load(context) } }

    val samples = remember(legs) { legs.map { GlobeGeo.greatCircle(it.from.point, it.to.point) } }
    val routes = remember(samples) { samples.map(::RouteVectors) }
    val stops = remember(legs) { routeStops(legs) }
    val stopVectors = remember(stops) { UnitVectors(stops.map { it.point }) }
    val active = activeLeg?.takeIf { it in legs.indices }
    val target = remember(samples, active) { GlobeGeo.routeView(samples, active) }

    val latitude = remember { Animatable(target.latitude.toFloat()) }
    val longitude = remember { Animatable(target.longitude.toFloat()) }
    LaunchedEffect(target, reduce) {
        val toLongitude = longitude.value + GlobeGeo.longitudeDelta(longitude.value.toDouble(), target.longitude).toFloat()
        if (reduce) {
            latitude.snapTo(target.latitude.toFloat())
            longitude.snapTo(toLongitude)
        } else {
            launch { latitude.animateTo(target.latitude.toFloat(), motion.dataSpatial()) }
            launch { longitude.animateTo(toLongitude, motion.dataSpatial()) }
        }
    }

    // The terminator moves a degree every 4 minutes: refresh the shading every 5.
    val subsolar = remember(instant.epochSecond / TerminatorBucketSeconds) { Sun.subsolarPoint(instant) }
    val landLight = remember(dots, subsolar) { dots?.let { lightOf(it.vectors, subsolar) } }
    val seaLit = remember(subsolar) { litOf(SeaGrid, subsolar) }
    val renderer = remember { GlobeRenderer() }

    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = GlobePalette.Label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val labels: List<TextLayoutResult>? = if (showLabels) remember(stops, measurer) { stops.map { measurer.measure(it.label, labelStyle) } } else null

    val currentOnLegTap by rememberUpdatedState(onLegTap)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentSamples by rememberUpdatedState(samples)

    Canvas(
        modifier
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                if (onClick != null) {
                    role = Role.Button
                    onClick(label = onClickLabel) { onClick(); true }
                }
                if (onLegClick != null && (legs.size > 1 || (onClick == null && legs.isNotEmpty()))) {
                    customActions = legs.indices.map { i -> CustomAccessibilityAction(legActionLabel(i)) { onLegClick(i); true } }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val radius = globeRadius(size.width.toFloat(), size.height.toFloat())
                    val x = (tap.x - size.width / 2f) / radius
                    val y = -(tap.y - size.height / 2f) / radius
                    val view = GlobeView(latitude.value.toDouble(), longitude.value.toDouble())
                    val hit = currentOnLegTap?.let { GlobeGeo.hitLeg(x.toDouble(), y.toDouble(), currentSamples, view, (TapTolerance.toPx() / radius).toDouble()) }
                    if (hit != null) currentOnLegTap?.invoke(hit) else currentOnClick?.invoke()
                }
            },
    ) {
        // Animated values are read here, in the draw phase only.
        renderer.draw(
            scope = this,
            latitude = latitude.value,
            longitude = longitude.value,
            dots = dots,
            landLight = landLight,
            seaLit = seaLit,
            subsolar = subsolar,
            routes = routes,
            active = active,
            stops = stopVectors,
            labels = labels,
            marker = marker,
        )
    }
}

private const val TerminatorBucketSeconds = 300L
private val TapTolerance = 24.dp

internal fun globeRadius(width: Float, height: Float): Float = min(width, height) / 2f * 0.94f

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
)

internal data class StopLabelPlacement(
    val left: Float,
    val top: Float,
    val scrimLeft: Float,
    val scrimTop: Float,
    val scrimRight: Float,
    val scrimBottom: Float,
)

/**
 * Places each visible stop's airport-code label beside its dot, choosing the candidate side (right, left, above,
 * below or diagonal) that avoids overlapping other stop dots and already-placed labels.
 */
internal fun placeStopLabels(
    stops: List<StopLabelInput>,
    canvasWidth: Float,
    canvasHeight: Float,
    dotRadius: Float,
    gap: Float,
    pad: Float,
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
        for ((rawLeft, rawTop) in candidates) {
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
            val score = overlap * 100f + clampPenalty
            if (score < bestScore) {
                bestScore = score
                best = StopLabelPlacement(left, top, sLeft, sTop, sRight, sBottom)
                if (score == 0f) break
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

/** The globe is a night-sky object in both themes: Clockblock's night sky ramp, warm land and Marigold routes. */
private object GlobePalette {
    val Ocean = Color(0xFF0B1026)
    val OceanDay = Color(0xFF2A2E6E)
    val Rim = Color(0xFF3B4290)
    val Land = Color(0xFFF4EFE6)
    val Sea = Color(0xFF9FB4FF)
    val CityLight = Color(0xFFFFD29D)
    val Route = Color(0xFFFFB000)
    val RouteDim = Color(0xFFFFE2A6)
    val Stop = Color(0xFFFFFFFF)
    val Label = Color(0xFFFFFFFF)
    val Scrim = Color(0xB3000000)
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

/** Land as a cloud of dots, precomputed from Natural Earth 110 m land (public domain). */
internal class LandDots(val vectors: UnitVectors) {
    companion object {
        @Volatile private var cached: LandDots? = null

        fun cachedOrNull(): LandDots? = cached

        fun load(context: Context): LandDots = cached ?: synchronized(this) {
            cached ?: context.assets.open("land_dots.bin").use { stream ->
                val input = DataInputStream(stream.buffered())
                val count = input.readInt()
                val points = List(count) {
                    val lat = input.readShort() / 100.0
                    val lon = input.readShort() / 100.0
                    GeoPoint(lat, lon)
                }
                LandDots(UnitVectors(points))
            }.also { cached = it }
        }
    }
}

/** A sparse lat/lon grid over the whole sphere, for the sea's halftone. */
private val SeaGrid: UnitVectors by lazy {
    val points = ArrayList<GeoPoint>()
    var lat = -88.5
    while (lat < 90) {
        val n = maxOf(1, (360 * cos(Math.toRadians(lat)) / 3.0).toInt())
        for (k in 0 until n) points += GeoPoint(lat, -180.0 + (k + 0.5) * 360.0 / n)
        lat += 3.0
    }
    UnitVectors(points)
}

/** Daylight per dot, 0 in full night to 1 once the sun is 6° up (civil twilight in between). */
private fun lightOf(vectors: UnitVectors, subsolar: SubsolarPoint): FloatArray {
    val s = UnitVectors(listOf(GeoPoint(subsolar.latitude, subsolar.longitude)))
    return FloatArray(vectors.size) { i ->
        // sin(elevation) = dot(point, subsolar); light ramps over ±6°.
        val sinElevation = vectors.x[i] * s.x[0] + vectors.y[i] * s.y[0] + vectors.z[i] * s.z[0]
        ((sinElevation + SinSixDegrees) / (2 * SinSixDegrees)).coerceIn(0f, 1f)
    }
}

private fun litOf(vectors: UnitVectors, subsolar: SubsolarPoint): BooleanArray {
    val light = lightOf(vectors, subsolar)
    return BooleanArray(light.size) { light[it] > 0f }
}

private const val SinSixDegrees = 0.104528f

/** Reused buffers, paths and paints: one per globe on screen. */
private class GlobeRenderer {
    private val landBuckets = Array(LandLevels) { FloatArray(0) }
    private val landCounts = IntArray(LandLevels)
    private var cityBuffer = FloatArray(0)
    private var seaLitBuffer = FloatArray(0)
    private var seaDimBuffer = FloatArray(0)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val frontPaths = ArrayList<Path>()
    private val backPaths = ArrayList<Path>()
    private val clip = Path()
    private var clipRadius = -1f
    private var clipCx = -1f
    private var clipCy = -1f
    private val rotation = Rotation()
    private var glow: Brush? = null
    private var glowRadius = -1f
    private var strokes: Strokes? = null
    private var strokesDensity = -1f

    private class Strokes(density: Float) {
        val dashes = PathEffect.dashPathEffect(floatArrayOf(4f * density, 4f * density))
        val back = Stroke(1.6f * density, cap = StrokeCap.Round, pathEffect = dashes)
        val dim = Stroke(2f * density, cap = StrokeCap.Round)
        val active = Stroke(3f * density, cap = StrokeCap.Round)
        val rim = Stroke(1.2f * density)
        val ring = Stroke(1.4f * density)
    }

    @Suppress("LongParameterList")
    fun draw(
        scope: DrawScope,
        latitude: Float,
        longitude: Float,
        dots: LandDots?,
        landLight: FloatArray?,
        seaLit: BooleanArray,
        subsolar: SubsolarPoint,
        routes: List<RouteVectors>,
        active: Int?,
        stops: UnitVectors,
        labels: List<TextLayoutResult>?,
        marker: GeoPoint?,
    ) = with(scope) {
        val radius = globeRadius(size.width, size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f
        rotation.update(latitude, longitude)
        val s = strokes?.takeIf { strokesDensity == density } ?: Strokes(density).also { strokes = it; strokesDensity = density }

        // Ocean, with the day side glowing from under the sun.
        drawCircle(GlobePalette.Ocean, radius, Offset(cx, cy))
        val sx: Float
        val sy: Float
        val sd: Float
        run {
            val phi = Math.toRadians(subsolar.latitude)
            val lambda = Math.toRadians(subsolar.longitude)
            val px = (cos(phi) * cos(lambda)).toFloat()
            val py = (cos(phi) * sin(lambda)).toFloat()
            val pz = sin(phi).toFloat()
            sx = rotation.x(px, py)
            sy = rotation.y(px, py, pz)
            sd = rotation.depth(px, py, pz)
        }
        if (glowRadius != radius) {
            glow = Brush.radialGradient(listOf(GlobePalette.OceanDay, Color.Transparent))
            glowRadius = radius
        }
        if (clipRadius != radius || clipCx != cx || clipCy != cy) {
            clip.reset()
            clip.addOval(androidx.compose.ui.geometry.Rect(cx - radius, cy - radius, cx + radius, cy + radius))
            clipRadius = radius
            clipCx = cx
            clipCy = cy
        }
        clipPath(clip) {
            translate(sx * radius * 0.85f, -sy * radius * 0.85f) {
                drawCircle(glow!!, radius * 1.25f, Offset(cx, cy), alpha = if (sd >= 0f) 0.95f else 0.35f)
            }
        }

        // Sea halftone.
        val sea = SeaGrid
        if (seaLitBuffer.size < sea.size * 2) {
            seaLitBuffer = FloatArray(sea.size * 2)
            seaDimBuffer = FloatArray(sea.size * 2)
        }
        var lit = 0
        var dim = 0
        for (i in 0 until sea.size) {
            val px = sea.x[i]
            val py = sea.y[i]
            val pz = sea.z[i]
            if (rotation.depth(px, py, pz) < 0f) continue
            val ox = cx + rotation.x(px, py) * radius
            val oy = cy - rotation.y(px, py, pz) * radius
            if (seaLit[i]) {
                seaLitBuffer[lit++] = ox; seaLitBuffer[lit++] = oy
            } else {
                seaDimBuffer[dim++] = ox; seaDimBuffer[dim++] = oy
            }
        }
        val seaDot = radius / 105f
        points(seaLitBuffer, lit, GlobePalette.Sea.copy(alpha = 0.32f), seaDot)
        points(seaDimBuffer, dim, GlobePalette.Sea.copy(alpha = 0.08f), seaDot)
        drawCircle(GlobePalette.Rim, radius, Offset(cx, cy), style = s.rim)

        // Route round the back of the globe: see-through, under the land.
        buildRoutes(routes, rotation, cx, cy, radius)
        routes.indices.forEach { i ->
            val alpha = if (active == null || active == i) 0.55f else 0.3f
            drawPath(backPaths[i], GlobePalette.RouteDim.copy(alpha = alpha), style = s.back)
        }

        // Land, bucketed by brightness so the cloud draws in a handful of calls.
        if (dots != null && landLight != null) {
            val v = dots.vectors
            for (level in 0 until LandLevels) {
                if (landBuckets[level].size < v.size * 2) landBuckets[level] = FloatArray(v.size * 2)
                landCounts[level] = 0
            }
            if (cityBuffer.size < v.size * 2) cityBuffer = FloatArray(v.size * 2)
            var cities = 0
            for (i in 0 until v.size) {
                val px = v.x[i]
                val py = v.y[i]
                val pz = v.z[i]
                val depth = rotation.depth(px, py, pz)
                if (depth < 0f) continue
                val ox = cx + rotation.x(px, py) * radius
                val oy = cy - rotation.y(px, py, pz) * radius
                val light = landLight[i]
                val level = (light * 4f + depth.coerceIn(0f, 1f) * 1.2f).toInt().coerceIn(0, LandLevels - 1)
                val buffer = landBuckets[level]
                buffer[landCounts[level]++] = ox
                buffer[landCounts[level]++] = oy
                if (light < 0.02f && (i * 2654435761L ushr 7) % 6 == 0L) {
                    cityBuffer[cities++] = ox; cityBuffer[cities++] = oy
                }
            }
            val landDot = radius / 60f
            for (level in 0 until LandLevels) {
                points(landBuckets[level], landCounts[level], GlobePalette.Land.copy(alpha = 0.14f + level * 0.17f), landDot)
            }
            points(cityBuffer, cities, GlobePalette.CityLight.copy(alpha = 0.9f), landDot * 0.75f)
        }

        // Route on the visible face: the active leg bright, the others dimmer.
        routes.indices.forEach { i ->
            if (active != null && i != active) drawPath(frontPaths[i], GlobePalette.RouteDim.copy(alpha = 0.6f), style = s.dim)
        }
        routes.indices.forEach { i ->
            if (active == null || i == active) drawPath(frontPaths[i], GlobePalette.Route, style = s.active)
        }

        // Stops, then their labels on a scrim.
        val dotRadius = 3.5f * density
        for (i in 0 until stops.size) {
            val px = stops.x[i]
            val py = stops.y[i]
            val pz = stops.z[i]
            val o = Offset(cx + rotation.x(px, py) * radius, cy - rotation.y(px, py, pz) * radius)
            if (rotation.depth(px, py, pz) >= 0f) {
                drawCircle(GlobePalette.Stop, dotRadius, o)
                drawCircle(GlobePalette.Route, dotRadius + 1.6f * density, o, style = s.ring)
            } else {
                drawCircle(GlobePalette.RouteDim.copy(alpha = 0.45f), dotRadius, o, style = s.ring)
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
                visibleIndices += i
                inputs += StopLabelInput(
                    ox = cx + rotation.x(px, py) * radius,
                    oy = cy - rotation.y(px, py, pz) * radius,
                    width = layout.size.width.toFloat(),
                    height = layout.size.height.toFloat(),
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
            for (k in visibleIndices.indices) {
                val layout = layouts[visibleIndices[k]]
                val p = placements[k]
                drawRoundRect(
                    GlobePalette.Scrim,
                    topLeft = Offset(p.scrimLeft, p.scrimTop),
                    size = Size(p.scrimRight - p.scrimLeft, p.scrimBottom - p.scrimTop),
                    cornerRadius = CornerRadius(pad, pad),
                )
                drawText(layout, topLeft = Offset(p.left, p.top))
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
                drawCircle(GlobePalette.Stop, 6f * density, o)
                drawCircle(GlobePalette.Route, 4f * density, o)
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

    private fun DrawScope.points(buffer: FloatArray, count: Int, color: Color, width: Float) {
        if (count == 0) return
        paint.color = color.toArgb()
        paint.strokeWidth = width
        drawContext.canvas.nativeCanvas.drawPoints(buffer, 0, count, paint)
    }

    /** The view's rotation, applied to unit vectors: three multiply-adds per point. */
    private class Rotation {
        private var sinPhi0 = 0f
        private var cosPhi0 = 1f
        private var sinLambda0 = 0f
        private var cosLambda0 = 1f

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

    private companion object {
        const val LandLevels = 6
    }
}
