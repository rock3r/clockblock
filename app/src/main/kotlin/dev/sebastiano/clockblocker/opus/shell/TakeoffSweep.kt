package dev.sebastiano.clockblocker.opus.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Where the take-off sweep's plane is, seen from above, in a window of [size]: it flies along the window's own
 * diagonal, from beyond the bottom-right corner to beyond the top-left one, sized so its wings span the whole window
 * across that heading (phones, tablets, foldables and desktop windows alike, in any orientation). Everything behind
 * its wings is revealed.
 *
 * The plane is drawn from a 24-unit square with its middle at (12, 12), nose up at y = 2 and tail at y = 22, wings
 * from x = 2 to x = 21; the reveal's edge runs across it at y = [BoundaryY], just behind the wing roots.
 */
internal class SweepGeometry(val size: Size) {
    /** The heading, a unit vector: up and to the left, along the diagonal. */
    val heading: Offset = run {
        val length = hypot(size.width, size.height).coerceAtLeast(1f)
        Offset(-size.width / length, -size.height / length)
    }

    /** The window's extent across the heading: what the wings must cover. */
    val crossExtent: Float = size.width * abs(heading.y) + size.height * abs(heading.x)

    /** The window's extent along the heading. */
    val alongExtent: Float = size.width * abs(heading.x) + size.height * abs(heading.y)

    /** Pixels per plane unit: the wingspan covers [crossExtent] with a margin. */
    val scale: Float = crossExtent * WingMargin / Wingspan

    /** The plane's rotation from nose-up to the heading, in degrees. */
    val rotationDegrees: Float = (atan2(heading.y, heading.x) - atan2(-1f, 0f)) * 180f / PI.toFloat()

    /**
     * How far the plane's middle is along the heading from the window's centre at [progress] (0 → 1): from its nose
     * just short of the window to its tail just past it.
     */
    fun along(progress: Float): Float =
        lerp(-alongExtent / 2f - NoseAhead * scale, alongExtent / 2f + TailBehind * scale, progress.coerceIn(0f, 1f))

    /** Where the reveal's edge is along the heading at [progress]: everything behind it shows the app. */
    fun boundary(progress: Float): Float = along(progress) - (BoundaryY - Middle) * scale

    /** Whether the point [p] (window pixels) is still covered at [progress]. */
    fun covered(p: Offset, progress: Float): Boolean {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val d = p - centre
        return d.x * heading.x + d.y * heading.y > boundary(progress)
    }

    internal companion object {
        const val Middle = 12f
        const val NoseAhead = 10f
        const val TailBehind = 10f
        const val BoundaryY = 14f
        const val Wingspan = 19f
        const val WingMargin = 1.08f
    }
}

/**
 * Onboarding's last beat, over the whole app (bars and rails included): a cover in onboarding's surface colour that
 * a big plane, seen from above, sweeps away from the bottom-right corner to the top-left one, revealing the app behind
 * its wings on `ClockblockMotion.sweep()`. It waits two frames first, so the app has composed and laid out underneath.
 * It takes no input and TalkBack doesn't see it; [onDone] when the app is all revealed.
 */
@Composable
internal fun TakeoffSweep(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val motion = ClockblockTheme.motion
    val cover = MaterialTheme.colorScheme.surface
    val plane = MaterialTheme.colorScheme.primary
    val progress = remember { Animatable(0f) }
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        progress.animateTo(1f, motion.sweep())
        currentOnDone()
    }
    val path = remember { PathParser().parsePathString(PlanePathData).toPath() }
    Canvas(
        modifier
            .fillMaxSize()
            .testTag(ShellTestTags.TakeoffSweep)
            .clearAndSetSemantics { }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
    ) {
        drawSweep(SweepGeometry(size), progress.value, path, cover, plane)
    }
}

private fun DrawScope.drawSweep(g: SweepGeometry, progress: Float, plane: Path, cover: Color, planeColor: Color) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val big = hypot(size.width, size.height) * 2f
    // In a frame turned so the heading points up: the cover is everything above the reveal's edge.
    withTransform({ rotate(g.rotationDegrees, pivot = centre) }) {
        val edge = centre.y - g.boundary(progress)
        drawRect(cover, topLeft = Offset(centre.x - big, edge - big), size = Size(big * 2f, big))
        val middle = Offset(centre.x, centre.y - g.along(progress))
        // A soft shadow below and behind, as from a plane flying low over the app.
        translate(ShadowOffset.toPx() * 0.4f, ShadowOffset.toPx()) { drawPlane(plane, middle, g.scale, Color.Black.copy(alpha = 0.18f)) }
        drawPlane(plane, middle, g.scale, planeColor)
    }
}

private fun DrawScope.drawPlane(path: Path, middle: Offset, scale: Float, color: Color) {
    withTransform({
        translate(middle.x - SweepGeometry.Middle * scale, middle.y - SweepGeometry.Middle * scale)
        scale(scale, scale, pivot = Offset.Zero)
    }) { drawPath(path, color) }
}

private val ShadowOffset = 18.dp

/** Material's "flight" glyph, nose up (the same as the Now notification's flight icon). */
private const val PlanePathData =
    "M21,16v-2l-8,-5V3.5c0,-0.83 -0.67,-1.5 -1.5,-1.5S10,2.67 10,3.5V9l-8,5v2l8,-2.5V19l-2,1.5V22l3.5,-1 3.5,1v-1.5L13,19v-5.5l8,2.5z"
