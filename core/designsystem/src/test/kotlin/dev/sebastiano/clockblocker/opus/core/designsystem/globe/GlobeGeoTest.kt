package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt

class GlobeGeoTest {

    private val lisbon = GeoPoint(38.78, -9.14)
    private val doha = GeoPoint(25.27, 51.61)
    private val tokyo = GeoPoint(35.55, 139.79)
    private val sydney = GeoPoint(-33.95, 151.18)
    private val losAngeles = GeoPoint(33.94, -118.41)
    private val honolulu = GeoPoint(21.32, -157.93)
    private val auckland = GeoPoint(-37.01, 174.79)
    private val london = GeoPoint(51.47, -0.46)

    // region Projection

    @Test
    fun `the view centre projects to the middle of the disc, facing the viewer`() {
        val p = GlobeGeo.orthographic(lisbon, GlobeView(lisbon.latitude, lisbon.longitude))
        p.x shouldBe (0.0 plusOrMinus 1e-9)
        p.y shouldBe (0.0 plusOrMinus 1e-9)
        p.depth shouldBe (1.0 plusOrMinus 1e-9)
    }

    @Test
    fun `east is right, north is up, and the antipode is hidden`() {
        val view = GlobeView(0.0, 0.0)
        GlobeGeo.orthographic(GeoPoint(0.0, 90.0), view).x shouldBe (1.0 plusOrMinus 1e-9)
        GlobeGeo.orthographic(GeoPoint(90.0, 0.0), view).y shouldBe (1.0 plusOrMinus 1e-9)
        GlobeGeo.orthographic(GeoPoint(0.0, 180.0), view).visible.shouldBeFalse()
    }

    @Test
    fun `every projected point lies on the unit disc`() = runTest {
        checkAll(
            Arb.numericDouble(-90.0, 90.0),
            Arb.numericDouble(-180.0, 180.0),
            Arb.numericDouble(-70.0, 70.0),
            Arb.numericDouble(-180.0, 180.0),
        ) { lat, lon, vLat, vLon ->
            val p = GlobeGeo.orthographic(GeoPoint(lat, lon), GlobeView(vLat, vLon))
            (p.x * p.x + p.y * p.y + p.depth * p.depth) shouldBe (1.0 plusOrMinus 1e-9)
        }
    }

    // endregion

    // region Great circles

    @Test
    fun `the great circle halfway point is equidistant and the samples are evenly spaced`() {
        val mid = GlobeGeo.interpolate(lisbon, tokyo, 0.5)
        GlobeGeo.distanceKm(lisbon, mid) shouldBe (GlobeGeo.distanceKm(mid, tokyo) plusOrMinus 1.0)
        val samples = GlobeGeo.greatCircle(lisbon, tokyo, stepDegrees = 5.0)
        samples.first() shouldBe lisbon
        samples.last() shouldBe tokyo
        val steps = samples.zipWithNext { a, b -> GlobeGeo.distanceKm(a, b) }
        (steps.max() - steps.min()) shouldBeLessThan 1.0
    }

    @Test
    fun `a flight over the date line crosses it instead of going round the world`() {
        // Honolulu → Auckland: the short way crosses 180°, so no sample goes anywhere near the prime meridian.
        val samples = GlobeGeo.greatCircle(honolulu, auckland)
        samples.all { abs(it.longitude) > 150.0 }.shouldBeTrue()
        samples.any { it.longitude < 0 }.shouldBeTrue()
        samples.any { it.longitude > 0 }.shouldBeTrue()
    }

    @Test
    fun `a polar route passes near the pole`() {
        // London → Los Angeles flies over Greenland; Los Angeles → Tokyo-ish along high latitudes.
        val samples = GlobeGeo.greatCircle(london, losAngeles)
        samples.maxOf { it.latitude } shouldBeGreaterThan 60.0
        val overThePole = GlobeGeo.greatCircle(GeoPoint(60.0, 0.0), GeoPoint(60.0, 180.0))
        overThePole.maxOf { it.latitude } shouldBe (90.0 plusOrMinus 1.0)
    }

    @Test
    fun `the shortest turn between longitudes never spins the long way`() {
        GlobeGeo.longitudeDelta(170.0, -170.0) shouldBe 20.0
        GlobeGeo.longitudeDelta(-170.0, 170.0) shouldBe -20.0
        GlobeGeo.longitudeDelta(0.0, 180.0) shouldBe 180.0
        GlobeGeo.longitudeDelta(10.0, 10.0) shouldBe 0.0
    }

    // endregion

    // region Framing

    @Test
    fun `a single flight is framed from its middle, so it all faces the viewer`() {
        val leg = GlobeGeo.greatCircle(lisbon, tokyo)
        val view = GlobeGeo.routeView(listOf(leg))
        GlobeGeo.fitsOneSide(leg, view).shouldBeTrue()
        val mid = GlobeGeo.interpolate(lisbon, tokyo, 0.5)
        // Looking from its middle, tilted a little towards the equator so the arc reads as an arc.
        GlobeGeo.centralAngle(GeoPoint(view.latitude, view.longitude), mid) shouldBeLessThan Math.toRadians(GlobeGeo.ViewTilt + 2.0)
        view.latitude shouldBeLessThan mid.latitude
    }

    @Test
    fun `the active leg is framed, and the rest may run round the back`() {
        // Round the world: London → Johannesburg → Sydney → Honolulu → Santiago → London. No one side shows it all.
        val stops = listOf(london, GeoPoint(-26.1, 28.2), sydney, honolulu, GeoPoint(-33.4, -70.8), london)
        val legs = stops.zipWithNext { a, b -> GlobeGeo.greatCircle(a, b) }
        GlobeGeo.fitsOneSide(legs.flatten(), GlobeGeo.routeView(legs)).shouldBeFalse()
        val view = GlobeGeo.routeView(legs, active = 2)
        GlobeGeo.fitsOneSide(legs[2], view).shouldBeTrue()
        GlobeGeo.fitsOneSide(legs[0], view).shouldBeFalse()
    }

    @Test
    fun `stops spread right round the world fall back to a sensible view`() {
        val view = GlobeGeo.bestView(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 180.0)), fallback = GlobeView(10.0, 20.0))
        view shouldBe GlobeView(10.0, 20.0)
    }

    @Test
    fun `a polar route's view stays off the pole`() {
        val view = GlobeGeo.routeView(listOf(GlobeGeo.greatCircle(GeoPoint(80.0, 0.0), GeoPoint(80.0, 180.0))))
        view.latitude shouldBe GlobeGeo.MaxViewLatitude - GlobeGeo.ViewTilt
    }

    // endregion

    // region Hit-testing

    @Test
    fun `a tap on an arc picks its leg, a tap elsewhere picks none`() {
        val legs = listOf(GlobeGeo.greatCircle(lisbon, doha), GlobeGeo.greatCircle(doha, tokyo))
        val view = GlobeGeo.routeView(legs)
        val onSecond = GlobeGeo.orthographic(GlobeGeo.interpolate(doha, tokyo, 0.5), view)
        GlobeGeo.hitLeg(onSecond.x, onSecond.y, legs, view, tolerance = 0.05) shouldBe 1
        val onFirst = GlobeGeo.orthographic(GlobeGeo.interpolate(lisbon, doha, 0.4), view)
        GlobeGeo.hitLeg(onFirst.x + 0.01, onFirst.y, legs, view, tolerance = 0.05) shouldBe 0
        GlobeGeo.hitLeg(0.0, -0.9, legs, view, tolerance = 0.05).shouldBeNull()
    }

    @Test
    fun `the back of the globe can't be tapped`() {
        val back = listOf(GlobeGeo.greatCircle(GeoPoint(0.0, 170.0), GeoPoint(10.0, -170.0)))
        val view = GlobeView(0.0, 0.0)
        val p = GlobeGeo.orthographic(GeoPoint(5.0, 180.0), view)
        GlobeGeo.hitLeg(p.x, p.y, back, view, tolerance = 0.2).shouldBeNull()
    }

    @Test
    fun `distance to a segment clamps to its ends`() {
        GlobeGeo.distanceToSegment(0.5, 1.0, 0.0, 0.0, 1.0, 0.0) shouldBe (1.0 plusOrMinus 1e-9)
        GlobeGeo.distanceToSegment(2.0, 0.0, 0.0, 0.0, 1.0, 0.0) shouldBe (1.0 plusOrMinus 1e-9)
        GlobeGeo.distanceToSegment(1.0, 1.0, 0.0, 0.0, 0.0, 0.0) shouldBe (sqrt(2.0) plusOrMinus 1e-9)
    }

    // endregion

    // region Terminator

    @Test
    fun `the terminator puts the subsolar side in day and the far side in night`() {
        val sub = dev.sebastiano.clockblocker.opus.core.model.Sun.subsolarPoint(java.time.Instant.parse("2026-06-21T12:00:00Z"))
        GlobeGeo.sunElevation(sub, london) shouldBeGreaterThan 0.0
        GlobeGeo.sunElevation(sub, tokyo) shouldBeLessThan 0.0
    }

    @Test
    fun `routeStops keeps each airport once, even when disconnected trips share a hub`() {
        val lhr = GlobeStop(london, "LHR")
        val lis = GlobeStop(lisbon, "LIS")
        val hnd = GlobeStop(tokyo, "HND")
        val syd = GlobeStop(sydney, "SYD")
        val stops = routeStops(listOf(GlobeLeg(lis, hnd), GlobeLeg(lhr, syd), GlobeLeg(hnd, lhr)))
        stops shouldBe listOf(lis, hnd, lhr, syd)
    }

    @Test
    fun `a label near the canvas edge keeps its side instead of flipping frame to frame`() {
        // A stop whose right-side label pokes 1 px past the canvas edge: without history it moves to the left side,
        // but with last frame's side (right = 0) it slides 1 px along the edge instead of jumping across the dot.
        val width = 28f
        val ox = 280f - 4f - width - 9.5f + 1f
        fun place(x: Float, previous: Int) = placeStopLabels(
            stops = listOf(StopLabelInput(ox = x, oy = 140f, width = width, height = 16f, previousSide = previous)),
            canvasWidth = 280f,
            canvasHeight = 280f,
            dotRadius = 3.5f,
            gap = 9.5f,
            pad = 4f,
        ).single()
        place(ox, previous = -1).side shouldBe 1
        place(ox, previous = 0).side shouldBe 0
        // Frame after frame it stays put while the dot wiggles by sub-pixel amounts.
        var side = 0
        repeat(20) { frame ->
            side = place(ox + if (frame % 2 == 0) 0.4f else -0.4f, previous = side).side
            side shouldBe 0
        }
    }

    @Test
    fun `a label moves to the other side once its own side is mostly off the canvas`() {
        val placement = placeStopLabels(
            stops = listOf(StopLabelInput(ox = 270f, oy = 140f, width = 28f, height = 16f, previousSide = 0)),
            canvasWidth = 280f,
            canvasHeight = 280f,
            dotRadius = 3.5f,
            gap = 9.5f,
            pad = 4f,
        ).single()
        placement.side shouldBe 1
    }

    @Test
    fun `placeStopLabels spreads out labels for nearby stops so their scrims do not overlap`() {
        // Three nearby European stops (like LHR, CDG, LIS on a 280x280 canvas).
        val placements = placeStopLabels(
            stops = listOf(
                StopLabelInput(ox = 92f, oy = 132f, width = 28f, height = 16f),
                StopLabelInput(ox = 76f, oy = 144f, width = 24f, height = 16f),
                StopLabelInput(ox = 96f, oy = 137f, width = 30f, height = 16f),
            ),
            canvasWidth = 280f,
            canvasHeight = 280f,
            dotRadius = 3.5f,
            gap = 9.5f,
            pad = 4f,
        )
        placements.size shouldBe 3
        for (i in placements.indices) {
            for (j in i + 1 until placements.size) {
                val a = placements[i]
                val b = placements[j]
                val overlapX = (minOf(a.scrimRight, b.scrimRight) - maxOf(a.scrimLeft, b.scrimLeft)).coerceAtLeast(0f)
                val overlapY = (minOf(a.scrimBottom, b.scrimBottom) - maxOf(a.scrimTop, b.scrimTop)).coerceAtLeast(0f)
                (overlapX * overlapY) shouldBe 0f
            }
        }
    }

    // endregion
}

class PinchTest {
    @Test
    fun `fingers spreading by a third are a pinch out, closing by a quarter a pinch in, anything less nothing`() {
        pinchOf(100f, 131f) shouldBe Pinch.Out
        pinchOf(100f, 76f) shouldBe Pinch.In
        pinchOf(100f, 110f).shouldBeNull()
        pinchOf(100f, 90f).shouldBeNull()
        pinchOf(0f, 50f).shouldBeNull()
    }
}
