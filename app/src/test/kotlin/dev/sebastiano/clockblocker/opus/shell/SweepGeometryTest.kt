package dev.sebastiano.clockblocker.opus.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.float
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The take-off sweep's plane covers any window, and reveals it from the bottom-right corner to the top-left one. */
class SweepGeometryTest {
    private val windows = listOf(
        Size(1080f, 2340f), // phone, portrait
        Size(2340f, 1080f), // phone, landscape
        Size(1840f, 2208f), // unfolded foldable
        Size(2560f, 1600f), // tablet, landscape
        Size(3840f, 1080f), // ultra-wide desktop window
        Size(600f, 2400f), // tall, narrow window
    )

    private fun Size.corners() = listOf(Offset.Zero, Offset(width, 0f), Offset(0f, height), Offset(width, height))

    @Test
    fun `the whole window is covered before the sweep, and nothing after`() {
        windows.forEach { size ->
            val g = SweepGeometry(size)
            size.corners().forEach { g.covered(it, 0f).shouldBeTrue() }
            size.corners().forEach { g.covered(it, 1f).shouldBeFalse() }
        }
    }

    @Test
    fun `the wings always span the window across the heading`() {
        windows.forEach { size ->
            val g = SweepGeometry(size)
            (g.scale * SweepGeometry.Wingspan) shouldBeGreaterThanOrEqual g.crossExtent
        }
    }

    @Test
    fun `the reveal starts at the bottom-right corner and ends at the top-left one`() = runTest {
        windows.forEach { size ->
            val g = SweepGeometry(size)
            val bottomRight = Offset(size.width, size.height)
            checkAll(Arb.float(0f, 1f).filter { it.isFinite() }) { p ->
                // Once the top-left corner shows, so does the bottom-right one.
                if (!g.covered(Offset.Zero, p)) g.covered(bottomRight, p).shouldBeFalse()
            }
        }
    }

    @Test
    fun `the reveal only ever grows`() = runTest {
        windows.forEach { size ->
            val g = SweepGeometry(size)
            checkAll(Arb.float(0f, 0.99f).filter { it.isFinite() }) { p -> g.boundary(p) shouldBeLessThan g.boundary(p + 0.01f) }
        }
    }

    @Test
    fun `the plane enters and leaves out of sight`() {
        windows.forEach { size ->
            val g = SweepGeometry(size)
            // Its nose just short of the window at the start, its tail just past it at the end.
            (g.along(0f) + SweepGeometry.NoseAhead * g.scale) shouldBeLessThan (-g.alongExtent / 2f + 1f)
            (g.along(1f) - SweepGeometry.TailBehind * g.scale) shouldBeGreaterThanOrEqual (g.alongExtent / 2f - 1f)
        }
    }
}
