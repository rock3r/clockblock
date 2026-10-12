package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import androidx.compose.ui.geometry.Offset
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.plusOrMinus as floatPlusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.numericFloat
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import org.junit.jupiter.api.Test

/** The interactive globe's pure geometry: dragging the view, picking a stop, and the dial ↔ world camera. */
class GlobeInteractionGeoTest {

    private val lisbon = GeoPoint(38.78, -9.14)
    private val doha = GeoPoint(25.27, 51.61)
    private val tokyo = GeoPoint(35.55, 139.79)

    // region Dragging

    @Test
    fun `dragging right turns the globe so the surface follows the finger east to west`() {
        // A finger moving right by a quarter of the radius drags the surface with it: the centre moves west, by more
        // away from the equator (a parallel there is shorter), so the middle of the view keeps up with the finger.
        GlobeGeo.dragged(GlobeView(0.0, 20.0), dx = 0.25, dy = 0.0).longitude shouldBe (20.0 - Math.toDegrees(0.25) plusOrMinus 1e-9)
        val view = GlobeGeo.dragged(GlobeView(60.0, 20.0), dx = 0.25, dy = 0.0)
        view.latitude shouldBe (60.0 plusOrMinus 1e-9)
        view.longitude shouldBe (20.0 - Math.toDegrees(0.5) plusOrMinus 1e-6)
    }

    @Test
    fun `near a pole a sideways drag turns the globe a bounded amount`() {
        val view = GlobeGeo.dragged(GlobeView(84.0, 0.0), dx = 0.1, dy = 0.0)
        (abs(view.longitude) < Math.toDegrees(0.1) * 3.1) shouldBe true
    }

    @Test
    fun `dragging down brings the north to the middle`() {
        val view = GlobeGeo.dragged(GlobeView(10.0, 20.0), dx = 0.0, dy = 0.1)
        view.latitude shouldBe (10.0 + Math.toDegrees(0.1) plusOrMinus 1e-9)
        view.longitude shouldBe (20.0 plusOrMinus 1e-9)
    }

    @Test
    fun `a point under the finger stays under it for a small drag`() {
        // Lisbon at the centre; dragging right by a small amount puts Lisbon right of the centre by about as much.
        val start = GlobeView(lisbon.latitude, lisbon.longitude)
        val moved = GlobeGeo.dragged(start, dx = 0.05, dy = 0.0)
        val p = GlobeGeo.orthographic(lisbon, moved)
        p.x shouldBe (0.05 plusOrMinus 0.01)
    }

    @Test
    fun `the view never tips over a pole`() = runTest {
        checkAll(Arb.numericDouble(-80.0, 80.0), Arb.numericDouble(-5.0, 5.0)) { lat, dy ->
            val view = GlobeGeo.dragged(GlobeView(lat, 0.0), dx = 0.0, dy = dy)
            (view.latitude in -GlobeGeo.MaxDragLatitude..GlobeGeo.MaxDragLatitude) shouldBe true
        }
    }

    // endregion

    // region Picking a stop

    @Test
    fun `a tap near a visible stop picks it, the nearest one first`() {
        val view = GlobeView(30.0, 20.0)
        val stops = listOf(lisbon, doha, tokyo)
        val d = GlobeGeo.orthographic(doha, view)
        GlobeGeo.hitStop(d.x + 0.01, d.y - 0.01, stops, view, tolerance = 0.05) shouldBe 1
    }

    @Test
    fun `a tap far from every stop, or on one behind the globe, picks none`() {
        val view = GlobeView(30.0, 20.0)
        GlobeGeo.hitStop(0.9, -0.9, listOf(lisbon, doha), view, tolerance = 0.05).shouldBeNull()
        // Tokyo is behind the globe seen from the Atlantic: its projection can't be tapped.
        val atlantic = GlobeView(30.0, -40.0)
        val t = GlobeGeo.orthographic(tokyo, atlantic)
        GlobeGeo.hitStop(t.x, t.y, listOf(tokyo), atlantic, tolerance = 0.05).shouldBeNull()
    }

    // endregion

    // region Dial ↔ world camera

    @Test
    fun `the camera starts on the marker, zoomed in, and ends on the whole globe`() {
        val m = Offset(0.3f, -0.2f)
        val start = WorldCamera.at(progress = 0f, marker = m, zoomIn = 4f)
        start.globeScale shouldBe (4f floatPlusOrMinus 1e-4f)
        // The marker sits in the middle of the view at the start…
        (start.globeTranslation.x + start.globeScale * m.x) shouldBe (0f floatPlusOrMinus 1e-4f)
        (start.globeTranslation.y + start.globeScale * m.y) shouldBe (0f floatPlusOrMinus 1e-4f)
        start.dialScale shouldBe (1f floatPlusOrMinus 1e-4f)

        val end = WorldCamera.at(progress = 1f, marker = m, zoomIn = 4f)
        end.globeScale shouldBe (1f floatPlusOrMinus 1e-4f)
        end.globeTranslation.x shouldBe (0f floatPlusOrMinus 1e-4f)
        end.globeTranslation.y shouldBe (0f floatPlusOrMinus 1e-4f)
        // …and the dial has shrunk onto the marker.
        end.dialCentre.x shouldBe (m.x floatPlusOrMinus 1e-4f)
        end.dialCentre.y shouldBe (m.y floatPlusOrMinus 1e-4f)
        end.dialScale shouldBe (0.25f floatPlusOrMinus 1e-4f)
    }

    @Test
    fun `the dial stays pinned to the marker on the globe all the way`() = runTest {
        checkAll(Arb.numericFloat(0f, 1f), Arb.numericFloat(-0.9f, 0.9f), Arb.numericFloat(-0.9f, 0.9f)) { p, x, y ->
            val m = Offset(x, y)
            val c = WorldCamera.at(p, m, zoomIn = 4f)
            // Where the globe draws the marker is where the dial's centre is.
            (c.globeTranslation.x + c.globeScale * m.x) shouldBe (c.dialCentre.x floatPlusOrMinus 1e-3f)
            (c.globeTranslation.y + c.globeScale * m.y) shouldBe (c.dialCentre.y floatPlusOrMinus 1e-3f)
        }
    }

    @Test
    fun `the zoom is even on a log scale, so it reads as one steady pull back`() {
        val half = WorldCamera.at(0.5f, Offset(0f, 0f), zoomIn = 4f)
        half.globeScale shouldBe (2f floatPlusOrMinus 1e-4f)
    }

    @Test
    fun `the dial fades out before the world has finished fading in, and labels come last`() {
        WorldCamera.at(0f, Offset.Zero, 4f).dialAlpha shouldBe 1f
        WorldCamera.at(0f, Offset.Zero, 4f).globeAlpha shouldBe 0f
        WorldCamera.at(1f, Offset.Zero, 4f).dialAlpha shouldBe 0f
        WorldCamera.at(1f, Offset.Zero, 4f).globeAlpha shouldBe 1f
        WorldCamera.at(1f, Offset.Zero, 4f).chromeAlpha shouldBe 1f
        WorldCamera.at(0.5f, Offset.Zero, 4f).chromeAlpha shouldBe 0f
    }

    @Test
    fun `under reduce motion nothing scales or moves, the two only cross-fade`() {
        val c = WorldCamera.at(0.5f, Offset(0.4f, 0.4f), zoomIn = 4f, still = true)
        c.globeScale shouldBe 1f
        c.dialScale shouldBe 1f
        c.globeTranslation shouldBe Offset.Zero
        c.dialCentre shouldBe Offset.Zero
    }

    // endregion

    // region Pinch progress

    @Test
    fun `spreading the fingers by the full ratio goes all the way to the world, and back by its inverse`() {
        pinchProgress(from = 0f, ratio = WorldPinchRatio) shouldBe (1f floatPlusOrMinus 1e-4f)
        pinchProgress(from = 1f, ratio = 1f / WorldPinchRatio) shouldBe (0f floatPlusOrMinus 1e-4f)
        pinchProgress(from = 0f, ratio = 0.5f) shouldBe 0f
        pinchProgress(from = 1f, ratio = 3f) shouldBe 1f
    }

    @Test
    fun `a release settles towards the side the fingers were heading, or the nearer side when still`() {
        settleTarget(progress = 0.3f, velocity = 2f) shouldBe 1f
        settleTarget(progress = 0.7f, velocity = -2f) shouldBe 0f
        settleTarget(progress = 0.6f, velocity = 0f) shouldBe 1f
        settleTarget(progress = 0.4f, velocity = 0.1f) shouldBe 0f
    }

    // endregion
}
