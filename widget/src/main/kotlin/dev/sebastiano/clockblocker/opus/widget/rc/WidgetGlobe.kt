package dev.sebastiano.clockblocker.opus.widget.rc

import android.content.Context
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GeoPoint
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeGeo
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeLeg
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeStop
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeView
import dev.sebastiano.clockblocker.opus.core.model.SubsolarPoint
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.state.WidgetGlobe
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Display list for the widget's orthographic route globe, laid out in a [width] × [height] dp box at capture time and
 * replayed into a `RemoteCanvas` under one uniform scale + translate (like [WidgetDial]).
 *
 * Dots of the same brightness share one [GlobeOp.Dots] bucket (and therefore one `RemotePaint` in `RecordingCanvas`'s
 * `PaintTracker`), so each dot records a single `DrawCircle` command without repeating the paint bundle.
 */
internal data class WidgetGlobeSpec(
    val width: Float,
    val height: Float,
    val view: GlobeView,
    val ops: List<GlobeOp>,
)

internal sealed interface GlobeOp {
    data class Circle(val cx: Float, val cy: Float, val r: Float, val color: Int, val stroke: Float = 0f) : GlobeOp

    data class Dots(val xy: FloatArray, val count: Int, val r: Float, val color: Int) : GlobeOp

    data class Polyline(
        val xy: FloatArray,
        val count: Int,
        val width: Float,
        val color: Int,
        val dashed: Boolean = false,
    ) : GlobeOp
}

/**
 * Colours of the widget route globe. Like the in-app `RouteGlobe`, the globe is a night-sky object in both Light and
 * Dark themes; in [WidgetTheme.NightSafe] it switches to warm, dimmed amber on near-black so the widget never emits
 * blue or bright white light during an avoid-light or sleep window.
 */
internal data class WidgetGlobePalette(
    val ocean: Int,
    val oceanDay: Int,
    val rim: Int,
    val land: Int,
    val sea: Int,
    val cityLight: Int,
    val route: Int,
    val routeDim: Int,
    val stop: Int,
    val label: Int,
    val scrim: Int,
) {
    companion object {
        val Standard = WidgetGlobePalette(
            ocean = 0xFF0B1026.toInt(),
            oceanDay = 0xFF2A2E6E.toInt(),
            rim = 0xFF3B4290.toInt(),
            land = 0xFFF4EFE6.toInt(),
            sea = 0xFF9FB4FF.toInt(),
            cityLight = 0xFFFFD29D.toInt(),
            route = 0xFFFFB000.toInt(),
            routeDim = 0xFFFFE2A6.toInt(),
            stop = 0xFFFFFFFF.toInt(),
            label = 0xFFFFFFFF.toInt(),
            scrim = 0xB3000000.toInt(),
        )

        val NightSafe = WidgetGlobePalette(
            ocean = 0xFF050300.toInt(),
            oceanDay = 0xFF241504.toInt(),
            rim = 0xFF4A3319.toInt(),
            land = 0xFFB7A48D.toInt(),
            sea = 0xFF6E5536.toInt(),
            cityLight = 0xFFD99B4E.toInt(),
            route = 0xFFD98A1C.toInt(),
            routeDim = 0xFF8E6830.toInt(),
            stop = 0xFFD9B88C.toInt(),
            label = 0xFFD9B88C.toInt(),
            scrim = 0xCC050300.toInt(),
        )

        fun of(theme: WidgetTheme): WidgetGlobePalette =
            if (theme == WidgetTheme.NightSafe) NightSafe else Standard
    }
}

internal object WidgetGlobeRenderer {
    private const val ROUTE_STEP_DEGREES = 2.5
    private const val LAND_LEVELS = 6
    private const val SUN_GLOW_STEPS = 7
    private const val SIN_SIX_DEGREES = 0.104528f

    fun spec(
        context: Context,
        globe: WidgetGlobe,
        palette: WidgetPalette,
        widthDp: Float,
        heightDp: Float,
    ): WidgetGlobeSpec {
        val colors = WidgetGlobePalette.of(palette.theme)
        val radius = min(widthDp, heightDp) / 2f * 0.94f
        val cx = widthDp / 2f
        val cy = heightDp / 2f

        val samples = globe.legs.map { GlobeGeo.greatCircle(it.from.point, it.to.point, ROUTE_STEP_DEGREES) }
        val active = globe.activeLeg?.takeIf { it in globe.legs.indices }
        val view = if (samples.isEmpty()) GlobeView(20.0, 0.0) else GlobeGeo.routeView(samples, active)
        val rot = GlobeRotation(view.latitude.toFloat(), view.longitude.toFloat())

        val ops = ArrayList<GlobeOp>(32)

        // 1. Ocean disc + internal-tangent sunlit glow (every circle stays strictly inside the disc without clipPath).
        ops += GlobeOp.Circle(cx, cy, radius, colors.ocean)
        addSunGlow(ops, globe.subsolar, rot, cx, cy, radius, colors.oceanDay)

        // 2. Sea halftone, spatially deduplicated for the widget's radius.
        addSeaDots(ops, globe.subsolar, rot, cx, cy, radius, colors.sea)

        // 3. Rim.
        ops += GlobeOp.Circle(cx, cy, radius, colors.rim, stroke = 1.2f)

        // 4. Back-of-the-globe route (see-through dashed under the land).
        val routes = samples.map(::GlobeRouteVectors)
        routes.forEachIndexed { index, route ->
            val alpha = if (active == null || active == index) 0.55f else 0.3f
            addRoutePolylines(
                ops = ops,
                route = route,
                rot = rot,
                cx = cx,
                cy = cy,
                radius = radius,
                front = false,
                width = 1.5f,
                color = withAlpha(colors.routeDim, alpha),
                dashed = true,
            )
        }

        // 5. Land halftone (6 brightness buckets + night-side city lights), spatially deduplicated near the limb.
        val land = WidgetLandDots.load(context)
        addLandDots(ops, land, globe.subsolar, rot, cx, cy, radius, colors)

        // 6. Front-of-the-globe route: inactive legs dimmer, active leg(s) bright.
        val scale = (radius / 70f).coerceIn(0.65f, 1.1f)
        routes.forEachIndexed { index, route ->
            if (active != null && index != active) {
                addRoutePolylines(
                    ops = ops,
                    route = route,
                    rot = rot,
                    cx = cx,
                    cy = cy,
                    radius = radius,
                    front = true,
                    width = 2f * scale,
                    color = withAlpha(colors.routeDim, 0.6f),
                    dashed = false,
                )
            }
        }
        routes.forEachIndexed { index, route ->
            if (active == null || index == active) {
                addRoutePolylines(
                    ops = ops,
                    route = route,
                    rot = rot,
                    cx = cx,
                    cy = cy,
                    radius = radius,
                    front = true,
                    width = 2.8f * scale,
                    color = colors.route,
                    dashed = false,
                )
            }
        }

        // 7. Stops.
        val stops = distinctStops(globe.legs)
        val stopVectors = GlobeUnitVectors(stops.map { it.point })
        val dotRadius = (3.2f * scale).coerceAtLeast(2.2f)
        val ringStroke = (1.3f * scale).coerceAtLeast(1f)
        for (i in 0 until stopVectors.size) {
            val px = stopVectors.x[i]
            val py = stopVectors.y[i]
            val pz = stopVectors.z[i]
            val ox = cx + rot.x(px, py) * radius
            val oy = cy - rot.y(px, py, pz) * radius
            if (rot.depth(px, py, pz) >= 0f) {
                ops += GlobeOp.Circle(ox, oy, dotRadius, colors.stop)
                ops += GlobeOp.Circle(ox, oy, dotRadius + 1.4f * scale, colors.route, stroke = ringStroke)
            } else {
                ops += GlobeOp.Circle(ox, oy, dotRadius, withAlpha(colors.routeDim, 0.45f), stroke = ringStroke)
            }
        }

        // 8. Traveller marker.
        globe.marker?.let { m ->
            val phi = Math.toRadians(m.latitude)
            val lambda = Math.toRadians(m.longitude)
            val px = (cos(phi) * cos(lambda)).toFloat()
            val py = (cos(phi) * sin(lambda)).toFloat()
            val pz = sin(phi).toFloat()
            if (rot.depth(px, py, pz) >= 0f) {
                val ox = cx + rot.x(px, py) * radius * 1.03f
                val oy = cy - rot.y(px, py, pz) * radius * 1.03f
                val outer = (5.2f * scale).coerceAtLeast(3.4f)
                val inner = (3.4f * scale).coerceAtLeast(2.2f)
                ops += GlobeOp.Circle(ox, oy, outer, colors.stop)
                ops += GlobeOp.Circle(ox, oy, inner, colors.route)
            }
        }

        return WidgetGlobeSpec(widthDp, heightDp, view, ops)
    }

    private fun addSunGlow(
        ops: MutableList<GlobeOp>,
        subsolar: SubsolarPoint,
        rot: GlobeRotation,
        cx: Float,
        cy: Float,
        radius: Float,
        oceanDay: Int,
    ) {
        val phi = Math.toRadians(subsolar.latitude)
        val lambda = Math.toRadians(subsolar.longitude)
        val px = (cos(phi) * cos(lambda)).toFloat()
        val py = (cos(phi) * sin(lambda)).toFloat()
        val pz = sin(phi).toFloat()
        val sx = rot.x(px, py)
        val sy = rot.y(px, py, pz)
        val sd = rot.depth(px, py, pz)
        val dirLen = hypot(sx, sy)
        val stepAlpha = if (sd >= 0f) 0.16f else 0.06f
        val color = withAlpha(oceanDay, stepAlpha)
        for (k in 1..SUN_GLOW_STEPS) {
            val t = k / SUN_GLOW_STEPS.toFloat()
            if (dirLen > 1e-3f) {
                val d = radius * t * 0.56f * dirLen.coerceAtMost(1f)
                val ux = sx / dirLen
                val uy = -sy / dirLen
                ops += GlobeOp.Circle(cx + ux * d, cy + uy * d, radius - d, color)
            } else {
                ops += GlobeOp.Circle(cx, cy, radius * (1f - t * 0.45f), color)
            }
        }
    }

    private fun addSeaDots(
        ops: MutableList<GlobeOp>,
        subsolar: SubsolarPoint,
        rot: GlobeRotation,
        cx: Float,
        cy: Float,
        radius: Float,
        seaColor: Int,
    ) {
        val sea = SeaGrid
        val light = lightOf(sea, subsolar)
        val winners = cullVisibleDots(sea, rot, gridSize = (2f * radius / maxOf(radius / 14f, 2.8f)).toInt().coerceIn(14, 28))
        val litXy = FloatArray(winners.size * 2)
        val dimXy = FloatArray(winners.size * 2)
        var litCount = 0
        var dimCount = 0
        for (i in winners) {
            val px = sea.x[i]
            val py = sea.y[i]
            val pz = sea.z[i]
            val ox = cx + rot.x(px, py) * radius
            val oy = cy - rot.y(px, py, pz) * radius
            if (light[i] > 0f) {
                litXy[litCount++] = ox
                litXy[litCount++] = oy
            } else {
                dimXy[dimCount++] = ox
                dimXy[dimCount++] = oy
            }
        }
        val seaRadius = (radius / 140f).coerceAtLeast(0.55f)
        if (litCount > 0) ops += GlobeOp.Dots(litXy, litCount, seaRadius, withAlpha(seaColor, 0.32f))
        if (dimCount > 0) ops += GlobeOp.Dots(dimXy, dimCount, seaRadius, withAlpha(seaColor, 0.08f))
    }

    private fun addLandDots(
        ops: MutableList<GlobeOp>,
        land: GlobeUnitVectors,
        subsolar: SubsolarPoint,
        rot: GlobeRotation,
        cx: Float,
        cy: Float,
        radius: Float,
        colors: WidgetGlobePalette,
    ) {
        val light = lightOf(land, subsolar)
        // Grid cell size adapts to the widget's globe radius so small 1×1 globes stay crisp and limb overdraw is culled.
        val stepDp = maxOf(radius / 22f, 2.0f)
        val winners = cullVisibleDots(land, rot, gridSize = (2f * radius / stepDp).toInt().coerceIn(22, 46))
        val buckets = Array(LAND_LEVELS) { FloatArray(winners.size * 2) }
        val counts = IntArray(LAND_LEVELS)
        val cities = FloatArray(winners.size * 2)
        var cityCount = 0

        for (i in winners) {
            val px = land.x[i]
            val py = land.y[i]
            val pz = land.z[i]
            val depth = rot.depth(px, py, pz)
            val ox = cx + rot.x(px, py) * radius
            val oy = cy - rot.y(px, py, pz) * radius
            val l = light[i]
            val level = (l * 4f + depth.coerceIn(0f, 1f) * 1.2f).toInt().coerceIn(0, LAND_LEVELS - 1)
            val buf = buckets[level]
            buf[counts[level]++] = ox
            buf[counts[level]++] = oy
            if (l < 0.02f && (i * 2654435761L ushr 7) % 6 == 0L) {
                cities[cityCount++] = ox
                cities[cityCount++] = oy
            }
        }

        val landRadius = (radius / 72f).coerceAtLeast(0.8f)
        for (level in 0 until LAND_LEVELS) {
            if (counts[level] > 0) {
                ops += GlobeOp.Dots(buckets[level], counts[level], landRadius, withAlpha(colors.land, 0.14f + level * 0.17f))
            }
        }
        if (cityCount > 0) {
            ops += GlobeOp.Dots(cities, cityCount, landRadius * 0.75f, withAlpha(colors.cityLight, 0.9f))
        }
    }

    /**
     * Keeps at most one visible dot per 2D cell of a [gridSize] × [gridSize] grid over the unit disc, preferring the
     * frontmost dot (`depth` closest to 1). This eliminates foreshortened overdraw near the limb and scales the dot
     * count naturally to the widget's size.
     */
    private fun cullVisibleDots(vectors: GlobeUnitVectors, rot: GlobeRotation, gridSize: Int): IntArray {
        val chosen = IntArray(gridSize * gridSize) { -1 }
        val bestDepth = FloatArray(gridSize * gridSize) { -1f }
        for (i in 0 until vectors.size) {
            val px = vectors.x[i]
            val py = vectors.y[i]
            val pz = vectors.z[i]
            val depth = rot.depth(px, py, pz)
            if (depth < 0f) continue
            val nx = ((rot.x(px, py) + 1f) * 0.5f).coerceIn(0f, 0.9999f)
            val ny = ((1f - rot.y(px, py, pz)) * 0.5f).coerceIn(0f, 0.9999f)
            val cell = (ny * gridSize).toInt() * gridSize + (nx * gridSize).toInt()
            if (depth > bestDepth[cell]) {
                bestDepth[cell] = depth
                chosen[cell] = i
            }
        }
        var count = 0
        for (idx in chosen) if (idx >= 0) count++
        val result = IntArray(count)
        var out = 0
        for (idx in chosen) if (idx >= 0) result[out++] = idx
        result.sort()
        return result
    }

    private fun addRoutePolylines(
        ops: MutableList<GlobeOp>,
        route: GlobeRouteVectors,
        rot: GlobeRotation,
        cx: Float,
        cy: Float,
        radius: Float,
        front: Boolean,
        width: Float,
        color: Int,
        dashed: Boolean,
    ) {
        val v = route.vectors
        val buf = FloatArray(v.size * 2)
        var count = 0
        fun flush() {
            if (count >= 4) ops += GlobeOp.Polyline(buf.copyOf(count), count, width, color, dashed)
            count = 0
        }
        for (i in 0 until v.size) {
            val px = v.x[i]
            val py = v.y[i]
            val pz = v.z[i]
            val visible = rot.depth(px, py, pz) >= 0f
            if (visible == front) {
                val lift = if (front) route.lift[i] else 1f
                buf[count++] = cx + rot.x(px, py) * lift * radius
                buf[count++] = cy - rot.y(px, py, pz) * lift * radius
            } else {
                flush()
            }
        }
        flush()
    }

    private fun distinctStops(legs: List<GlobeLeg>): List<GlobeStop> = buildList {
        legs.forEach { leg ->
            if (none { it.point == leg.from.point && it.label == leg.from.label }) add(leg.from)
            if (none { it.point == leg.to.point && it.label == leg.to.label }) add(leg.to)
        }
    }

    private fun lightOf(vectors: GlobeUnitVectors, subsolar: SubsolarPoint): FloatArray {
        val phi = Math.toRadians(subsolar.latitude)
        val lambda = Math.toRadians(subsolar.longitude)
        val sx = (cos(phi) * cos(lambda)).toFloat()
        val sy = (cos(phi) * sin(lambda)).toFloat()
        val sz = sin(phi).toFloat()
        return FloatArray(vectors.size) { i ->
            val sinEl = vectors.x[i] * sx + vectors.y[i] * sy + vectors.z[i] * sz
            ((sinEl + SIN_SIX_DEGREES) / (2f * SIN_SIX_DEGREES)).coerceIn(0f, 1f)
        }
    }

    private fun withAlpha(argb: Int, alpha: Float): Int {
        val baseAlpha = (argb ushr 24) and 0xFF
        val a = (baseAlpha * alpha.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return (argb and 0x00FFFFFF) or (a shl 24)
    }

    private val SeaGrid: GlobeUnitVectors by lazy {
        val points = ArrayList<GeoPoint>()
        var lat = -88.5
        while (lat < 90) {
            val n = maxOf(1, (360 * cos(Math.toRadians(lat)) / 4.0).toInt())
            for (k in 0 until n) points += GeoPoint(lat, -180.0 + (k + 0.5) * 360.0 / n)
            lat += 4.0
        }
        GlobeUnitVectors(points)
    }
}

internal class GlobeUnitVectors(points: List<GeoPoint>) {
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

private class GlobeRouteVectors(points: List<GeoPoint>) {
    val vectors = GlobeUnitVectors(points)
    val lift = FloatArray(points.size) { i ->
        val f = if (points.size > 1) i / (points.size - 1f) else 0f
        1f + 0.05f * sin(PI * f).toFloat()
    }
}

private class GlobeRotation(latitude: Float, longitude: Float) {
    private val sinPhi0 = sin(Math.toRadians(latitude.toDouble())).toFloat()
    private val cosPhi0 = cos(Math.toRadians(latitude.toDouble())).toFloat()
    private val sinLambda0 = sin(Math.toRadians(longitude.toDouble())).toFloat()
    private val cosLambda0 = cos(Math.toRadians(longitude.toDouble())).toFloat()

    fun x(px: Float, py: Float): Float = -px * sinLambda0 + py * cosLambda0
    fun y(px: Float, py: Float, pz: Float): Float = cosPhi0 * pz - sinPhi0 * (px * cosLambda0 + py * sinLambda0)
    fun depth(px: Float, py: Float, pz: Float): Float = sinPhi0 * pz + cosPhi0 * (px * cosLambda0 + py * sinLambda0)
}

internal object WidgetLandDots {
    @Volatile private var cached: GlobeUnitVectors? = null

    fun load(context: Context): GlobeUnitVectors = cached ?: synchronized(this) {
        cached ?: openLandDots(context).use { stream ->
            val input = DataInputStream(stream.buffered())
            val count = input.readInt()
            val points = List(count) {
                val lat = input.readShort() / 100.0
                val lon = input.readShort() / 100.0
                GeoPoint(lat, lon)
            }
            GlobeUnitVectors(points)
        }.also { cached = it }
    }

    /** The shared dot map from `:core:designsystem`'s assets (merged into unit tests by `isIncludeAndroidResources`). */
    private fun openLandDots(context: Context): InputStream = context.assets.open("land_dots.bin")
}

/**
 * Replays [spec] into a Remote Compose canvas, scaled uniformly and centred in the canvas.
 */
internal fun RemoteDrawScope.drawGlobeSpec(spec: WidgetGlobeSpec) {
    val unit = (width / spec.width.rf).min(height / spec.height.rf)
    val ox = (width - unit * spec.width.rf) / 2f.rf
    val oy = (height - unit * spec.height.rf) / 2f.rf
    translate(ox, oy) {
        scale(unit) {
            spec.ops.forEach { op -> drawGlobeOp(op) }
        }
    }
}

private fun globePaint(color: Int, stroke: Float = 0f, cap: StrokeCap = StrokeCap.Butt) = RemotePaint {
    this.color = Color(color).rc
    if (stroke > 0f) {
        style = PaintingStyle.Stroke
        strokeWidth = stroke.rf
        strokeCap = cap
    } else {
        style = PaintingStyle.Fill
    }
}

private fun RemoteDrawScope.drawGlobeOp(op: GlobeOp) {
    when (op) {
        is GlobeOp.Circle -> drawCircle(
            paint = globePaint(op.color, op.stroke),
            center = RemoteOffset(op.cx.rf, op.cy.rf),
            radius = op.r.rf,
        )
        is GlobeOp.Dots -> {
            val p = globePaint(op.color)
            val r = op.r.rf
            var i = 0
            while (i < op.count) {
                drawCircle(paint = p, center = RemoteOffset(op.xy[i].rf, op.xy[i + 1].rf), radius = r)
                i += 2
            }
        }
        is GlobeOp.Polyline -> {
            val p = globePaint(op.color, stroke = op.width, cap = StrokeCap.Round)
            var i = 0
            while (i + 3 < op.count) {
                if (!op.dashed || ((i / 2) % 2 == 0)) {
                    drawLine(
                        paint = p,
                        start = RemoteOffset(op.xy[i].rf, op.xy[i + 1].rf),
                        end = RemoteOffset(op.xy[i + 2].rf, op.xy[i + 3].rf),
                    )
                }
                i += 2
            }
        }
    }
}

/**
 * The route globe of [model] in a canvas filling its parent, laid out for [widthDp] × [heightDp] (the region at the
 * bucket's minimum) and scaled to the real size on the host.
 */
@RemoteComposable
@Composable
internal fun WidgetGlobeCanvas(model: WidgetModel, widthDp: Float, heightDp: Float) {
    val state = model.state as? WidgetState.Active ?: return
    val context = LocalContext.current
    val spec = WidgetGlobeRenderer.spec(context, state.globe, model.palette, widthDp, heightDp)
    RemoteCanvas(modifier = RemoteModifier.fillMaxSize()) { drawGlobeSpec(spec) }
}
