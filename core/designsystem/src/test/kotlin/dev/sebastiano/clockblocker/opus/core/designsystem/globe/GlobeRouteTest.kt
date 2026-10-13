package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/** The route's front (lifted) and back (see-through) parts meet at the horizon; the day-side glow never pops. */
class GlobeRouteTest {

    private class Op(val front: Boolean, val move: Boolean, val x: Float, val y: Float)

    private class Recorder : RouteSink {
        val ops = ArrayList<Op>()
        override fun moveTo(front: Boolean, x: Float, y: Float) { ops += Op(front, true, x, y) }
        override fun lineTo(front: Boolean, x: Float, y: Float) { ops += Op(front, false, x, y) }
    }

    private val radius = 500f
    private val cx = 540f
    private val cy = 600f

    private fun trace(from: GeoPoint, to: GeoPoint, view: GlobeView, lifted: Boolean = true): List<Op> {
        val points = GlobeGeo.greatCircle(from, to)
        val lift = FloatArray(points.size) { i -> if (lifted) 1f + 0.05f * sin(PI * i / (points.size - 1)).toFloat() else 1f }
        val rotation = GlobeRotation().apply { update(view.latitude.toFloat(), view.longitude.toFloat()) }
        return Recorder().also { traceRoute(UnitVectors(points), lift, rotation, cx, cy, radius, it) }.ops
    }

    @Test
    fun `where a route crosses the horizon, the front part ends where the back part starts, on the limb`() {
        val lisbon = GeoPoint(38.78, -9.14)
        val tokyo = GeoPoint(35.55, 139.79)
        val sydney = GeoPoint(-33.95, 151.18)
        val newYork = GeoPoint(40.64, -73.78)
        var crossings = 0
        for ((from, to) in listOf(lisbon to tokyo, newYork to sydney, sydney to lisbon)) {
            for (longitude in -180..180 step 15) {
                for (latitude in listOf(-40.0, 0.0, 35.0, 70.0)) {
                    val ops = trace(from, to, GlobeView(latitude, longitude.toDouble()))
                    for (k in 1 until ops.size) {
                        val prev = ops[k - 1]
                        val next = ops[k]
                        if (prev.front == next.front) continue
                        crossings++
                        // The side being left ends with a line to the crossing, the other starts with a move there.
                        prev.move shouldBe false
                        next.move shouldBe true
                        hypot(prev.x - next.x, prev.y - next.y) shouldBeLessThan 1f
                        // On the limb: the lift has eased to nothing there.
                        hypot(next.x - cx, next.y - cy) shouldBe (radius plusOrMinus 1f)
                    }
                }
            }
        }
        crossings shouldBeGreaterThan 50
    }

    @Test
    fun `facing the viewer, the arc keeps its lift mid-flight`() {
        val from = GeoPoint(40.0, -20.0)
        val to = GeoPoint(40.0, 20.0)
        val view = GlobeView(0.0, 0.0)
        val lifted = trace(from, to, view)
        val flat = trace(from, to, view, lifted = false)
        val mid = lifted.size / 2
        lifted[mid].front shouldBe true
        // The middle of the leg faces the viewer well away from the horizon: 5 % above the surface.
        (hypot(lifted[mid].x - cx, lifted[mid].y - cy) / hypot(flat[mid].x - cx, flat[mid].y - cy)) shouldBe (1.05f plusOrMinus 1e-3f)
    }

    @Test
    fun `the glow fades smoothly as the sun crosses the horizon`() {
        var depth = -1f
        var previous = glowAlpha(depth)
        while (depth < 1f) {
            depth += 0.01f
            val alpha = glowAlpha(depth)
            (alpha - previous) shouldBeLessThan 0.03f
            previous = alpha
        }
        glowAlpha(1f) shouldBe (0.95f plusOrMinus 1e-6f)
        glowAlpha(-1f) shouldBe (0f plusOrMinus 1e-6f)
        glowAlpha(0.001f) - glowAlpha(-0.001f) shouldBeLessThan 0.01f
    }

    @Test
    fun `behind the globe the glow rests on the limb, and joins up with the sun at the horizon`() {
        // At the horizon the sun is on the limb (|x, y| = 1): both sides agree.
        glowReach(0.6f, 0.8f, 0.0001f) shouldBe 1f
        glowReach(0.6f, 0.8f, -0.0001f) shouldBe (1f plusOrMinus 1e-3f)
        // Behind, the projected sun is inside the disc: the glow is pushed back out to the limb.
        val x = 0.3f
        val y = 0.4f
        (hypot(x, y) * glowReach(x, y, -0.866f)) shouldBe (1f plusOrMinus 1e-5f)
    }
}
