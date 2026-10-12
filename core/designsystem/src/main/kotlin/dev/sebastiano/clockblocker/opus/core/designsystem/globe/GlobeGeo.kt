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
 * The projection, great-circle interpolation and central angle are adapted from Prime Meridian's Geo.kt
 * (https://github.com/c5inco/m3e-vibes/tree/main/apps/prime-meridian, Apache-2.0). Framing, hit-testing and the
 * back-of-the-globe handling are Clockblock's.
 */
package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import androidx.compose.runtime.Immutable
import dev.sebastiano.clockblocker.opus.core.model.Sun
import dev.sebastiano.clockblocker.opus.core.model.SubsolarPoint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on the globe in degrees, north and east positive. */
@Immutable
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * A point projected onto a unit orthographic globe: `x` east and `y` north on the disc (−1..1), and [depth] towards
 * the viewer (1 at the centre of the visible face, 0 on the limb, negative behind the globe).
 */
data class Projected(val x: Double, val y: Double, val depth: Double) {
    val visible: Boolean get() = depth >= 0.0
}

/** Where the globe looks from: the point at the centre of the visible face. */
@Immutable
data class GlobeView(val latitude: Double, val longitude: Double)

/** Pure spherical geometry for the route globe: projection, great circles, framing, the terminator and hit-testing. */
object GlobeGeo {
    private const val EarthRadiusKm = 6371.0

    /** The furthest a view centre goes from the equator: higher would show a polar route edge-on. */
    const val MaxViewLatitude: Double = 70.0

    fun distanceKm(a: GeoPoint, b: GeoPoint): Double = centralAngle(a, b) * EarthRadiusKm

    /** Angle between [a] and [b] seen from the Earth's centre, in radians (haversine). */
    fun centralAngle(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).let { it * it }
        return 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /**
     * The point [fraction] of the way from [a] to [b] along the great circle (spherical linear interpolation in 3D,
     * so routes over the date line or a pole come out right).
     */
    fun interpolate(a: GeoPoint, b: GeoPoint, fraction: Double): GeoPoint {
        if (fraction <= 0.0) return a
        if (fraction >= 1.0) return b
        val angle = centralAngle(a, b)
        if (angle < 1e-12) return a
        val va = a.toVector()
        val vb = b.toVector()
        val wa = sin((1 - fraction) * angle) / sin(angle)
        val wb = sin(fraction * angle) / sin(angle)
        return fromVector(wa * va[0] + wb * vb[0], wa * va[1] + wb * vb[1], wa * va[2] + wb * vb[2])
    }

    /** The great circle from [a] to [b] as points about every [stepDegrees] (at least 2: both ends). */
    fun greatCircle(a: GeoPoint, b: GeoPoint, stepDegrees: Double = 2.0): List<GeoPoint> {
        val steps = ceil(Math.toDegrees(centralAngle(a, b)) / stepDegrees).toInt().coerceAtLeast(1)
        return List(steps + 1) { interpolate(a, b, it / steps.toDouble()) }
    }

    /** Orthographic projection of [point] onto a globe seen from [view]. */
    fun orthographic(point: GeoPoint, view: GlobeView): Projected {
        val phi = Math.toRadians(point.latitude)
        val dLambda = Math.toRadians(point.longitude - view.longitude)
        val phi0 = Math.toRadians(view.latitude)
        val x = cos(phi) * sin(dLambda)
        val y = cos(phi0) * sin(phi) - sin(phi0) * cos(phi) * cos(dLambda)
        val depth = sin(phi0) * sin(phi) + cos(phi0) * cos(phi) * cos(dLambda)
        return Projected(x, y, depth)
    }

    /**
     * The view that shows [points] best: looking at their mean direction, so as much of them as possible faces the
     * viewer. When they cancel out (stops spread right round the world) there is no best side, and [fallback] is used.
     * The latitude stays within ±[MaxViewLatitude].
     */
    fun bestView(points: List<GeoPoint>, fallback: GlobeView = GlobeView(20.0, 0.0)): GlobeView {
        if (points.isEmpty()) return fallback
        var x = 0.0
        var y = 0.0
        var z = 0.0
        points.forEach { p ->
            val v = p.toVector()
            x += v[0]
            y += v[1]
            z += v[2]
        }
        val length = sqrt(x * x + y * y + z * z)
        if (length < points.size * 1e-3) return fallback
        val mean = fromVector(x, y, z)
        return GlobeView(mean.latitude.coerceIn(-MaxViewLatitude, MaxViewLatitude), mean.longitude)
    }

    /**
     * The view for a route of [legs] (each a list of points along its great circle): framed on leg [active] when
     * there is one (the leg the user is on), otherwise on the whole route. The rest of the route may then run round
     * the back of the globe, where it is drawn see-through.
     */
    fun routeView(legs: List<List<GeoPoint>>, active: Int? = null): GlobeView {
        val all = legs.flatten()
        val whole = bestView(all, fallback = all.firstOrNull()?.let { GlobeView(it.latitude, it.longitude) } ?: GlobeView(20.0, 0.0))
        val focus = active?.let(legs::getOrNull)
        return tilted(if (focus == null) whole else bestView(focus, fallback = whole))
    }

    /** How far the view looks from the equator side: a great circle seen from straight above its middle is a line. */
    const val ViewTilt: Double = 12.0

    /**
     * Moves [view] [ViewTilt] degrees towards the equator (or south, near it), so a route that bulges towards the
     * pole, as great circles do, reads as an arc rather than a straight line.
     */
    fun tilted(view: GlobeView): GlobeView {
        val lat = view.latitude
        val towardsEquator = if (lat >= 0) lat - ViewTilt else lat + ViewTilt
        return view.copy(latitude = towardsEquator.coerceIn(-MaxViewLatitude, MaxViewLatitude))
    }

    /** Whether every one of [points] is on the visible face of a globe seen from [view]. */
    fun fitsOneSide(points: List<GeoPoint>, view: GlobeView): Boolean = points.all { orthographic(it, view).depth >= -1e-9 }

    /** The sun's height in degrees at [point] when it is overhead at [subsolar]: positive in daylight. */
    fun sunElevation(subsolar: SubsolarPoint, point: GeoPoint): Double = Sun.elevation(subsolar, point.latitude, point.longitude)

    /**
     * Which leg a tap at ([x], [y]) on the unit disc (east and north positive) hits: the leg whose visible part of
     * its arc passes nearest, within [tolerance] (in disc units). Parts behind the globe can't be tapped. Null: no leg.
     */
    fun hitLeg(x: Double, y: Double, legs: List<List<GeoPoint>>, view: GlobeView, tolerance: Double): Int? {
        var best: Int? = null
        var bestDistance = tolerance
        legs.forEachIndexed { index, points ->
            var previous: Projected? = null
            points.forEach { point ->
                val p = orthographic(point, view)
                val prev = previous
                if (p.visible && prev != null && prev.visible) {
                    val d = distanceToSegment(x, y, prev.x, prev.y, p.x, p.y)
                    if (d <= bestDistance) {
                        bestDistance = d
                        best = index
                    }
                } else if (p.visible && prev == null) {
                    val d = hypot(x - p.x, y - p.y)
                    if (d <= bestDistance) {
                        bestDistance = d
                        best = index
                    }
                }
                previous = p
            }
        }
        return best
    }

    /** How far a drag can tilt the view from the equator: further would flip the globe over a pole. */
    const val MaxDragLatitude: Double = 85.0

    /**
     * The view after a drag of ([dx], [dy]) in units of the globe's radius on screen (right and down positive): the
     * surface follows the finger, so dragging right turns the view west and dragging down brings the north to the
     * middle. The latitude stays within ±[MaxDragLatitude]; the longitude is left unwrapped, so a drag across the date
     * line never jumps.
     */
    fun dragged(view: GlobeView, dx: Double, dy: Double): GlobeView = GlobeView(
        latitude = (view.latitude + Math.toDegrees(dy)).coerceIn(-MaxDragLatitude, MaxDragLatitude),
        longitude = view.longitude - Math.toDegrees(dx),
    )

    /**
     * Which of [stops] a tap at ([x], [y]) on the unit disc (east and north positive) picks: the nearest one on the
     * visible face within [tolerance] (disc units). Null: none.
     */
    fun hitStop(x: Double, y: Double, stops: List<GeoPoint>, view: GlobeView, tolerance: Double): Int? {
        var best: Int? = null
        var bestDistance = tolerance
        stops.forEachIndexed { index, stop ->
            val p = orthographic(stop, view)
            if (!p.visible) return@forEachIndexed
            val d = hypot(x - p.x, y - p.y)
            if (d <= bestDistance) {
                bestDistance = d
                best = index
            }
        }
        return best
    }

    /** Shortest turn from longitude [from] to [to], in degrees (−180, 180]: the globe never spins the long way. */
    fun longitudeDelta(from: Double, to: Double): Double {
        val d = ((to - from) % 360.0 + 540.0) % 360.0 - 180.0
        return if (d == -180.0) 180.0 else d
    }

    internal fun distanceToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    internal fun GeoPoint.toVector(): DoubleArray {
        val phi = Math.toRadians(latitude)
        val lambda = Math.toRadians(longitude)
        return doubleArrayOf(cos(phi) * cos(lambda), cos(phi) * sin(lambda), sin(phi))
    }

    internal fun fromVector(x: Double, y: Double, z: Double): GeoPoint =
        GeoPoint(Math.toDegrees(atan2(z, sqrt(x * x + y * y))), Math.toDegrees(atan2(y, x)))
}
