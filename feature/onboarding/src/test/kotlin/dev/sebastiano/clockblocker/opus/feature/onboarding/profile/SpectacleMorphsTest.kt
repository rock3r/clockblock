package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The spectacle's shape never jumps between its morphs, and lands exactly on the card. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class SpectacleMorphsTest {
    private val size = Size(400f, 400f)
    private val morphs = spectacleMorphs(cornerFraction = 0.16f)

    /** The path's own outline bounds, from points along it (a path's bounds count its control points too). */
    private fun Path.outline(): Rect {
        val points = asAndroidPath().approximate(0.05f)
        val xs = points.indices.filter { it % 3 == 1 }.map { points[it] }
        val ys = points.indices.filter { it % 3 == 2 }.map { points[it] }
        return Rect(xs.min(), ys.min(), xs.max(), ys.max())
    }

    private infix fun Rect.shouldMatch(expected: Rect) {
        left shouldBe (expected.left plusOrMinus Tolerance)
        top shouldBe (expected.top plusOrMinus Tolerance)
        right shouldBe (expected.right plusOrMinus Tolerance)
        bottom shouldBe (expected.bottom plusOrMinus Tolerance)
    }

    @Test
    fun `the second spin starts where the first ends`() {
        morphs[1].toPath(0f, size, rotationDegrees = 180f).outline() shouldMatch
            morphs[0].toPath(1f, size, rotationDegrees = 180f).outline()
    }

    @Test
    fun `the pop starts where the second spin ends`() {
        morphs[2].toPath(0f, size).outline() shouldMatch morphs[1].toPath(1f, size, rotationDegrees = 360f).outline()
    }

    @Test
    fun `the pop ends exactly on the card`() {
        morphs[2].toPath(1f, size).outline() shouldMatch Rect(Offset.Zero, size)
    }

    @Test
    fun `spectacleMorphs reuses the spin morphs and caches the card morph per corner fraction`() {
        val first = spectacleMorphs(cornerFraction = 0.16f)
        val second = spectacleMorphs(cornerFraction = 0.16f)
        val otherCorner = spectacleMorphs(cornerFraction = 0.20f)
        (first === second) shouldBe true
        (first[0] === otherCorner[0]) shouldBe true
        (first[1] === otherCorner[1]) shouldBe true
    }

    @Test
    fun `once the pop reaches the card on its first approach, its undershoot never dims artAlpha again`() = runTest {
        val entrance = RevealEntrance(RevealStart.Spectacle)
        entrance.pop.snapTo(0.8f)
        entrance.artAlpha shouldBe (0.5f plusOrMinus 1e-4f)
        entrance.pop.snapTo(1.05f)
        entrance.artAlpha shouldBe 1f
        entrance.pop.snapTo(0.97f)
        entrance.artAlpha shouldBe 1f
    }

    private companion object {
        const val Tolerance = 0.5f
    }
}
