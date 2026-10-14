package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
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

    private companion object {
        const val Tolerance = 0.5f
    }
}
