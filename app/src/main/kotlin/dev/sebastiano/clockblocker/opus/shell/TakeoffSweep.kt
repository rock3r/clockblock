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
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
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
 * The plane is drawn from a 24-unit square with its middle at (12, 12), nose up at y = [NoseY] and tail at y = 22,
 * wings from x = 2 to x = 21. The reveal's edge follows the wings' swept-back trailing edges, tucked just under them
 * ([edgeY]), and runs straight across beyond the tips: no cover shows behind the wings, and none is revealed ahead.
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

    /**
     * The foremost point of the reveal's edge along the heading at [progress] (at the wing roots): everything behind
     * the edge shows the app.
     */
    fun boundary(progress: Float): Float = along(progress) - (edgeY(RootX) - Middle) * scale

    /** The heading turned a quarter clockwise: the plane's own x axis on screen. */
    private val across: Offset = Offset(-heading.y, heading.x)

    /** Whether the point [p] (window pixels) is still covered at [progress]: ahead of the reveal's edge. */
    fun covered(p: Offset, progress: Float): Boolean {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val d = p - centre - heading * along(progress)
        val x = Middle + (d.x * across.x + d.y * across.y) / scale
        val y = Middle - (d.x * heading.x + d.y * heading.y) / scale
        return y < edgeY(x)
    }

    internal companion object {
        const val Middle = 12f
        const val NoseY = 0.6f
        const val NoseAhead = Middle - NoseY
        const val TailBehind = 10f
        const val Wingspan = 19f
        const val WingMargin = 1.08f

        /** The wing roots' and tips' trailing edges (plane units), and how far under the wing the edge tucks. */
        private const val RootX = 11.5f
        private const val LeftTipX = 2f
        private const val RightTipX = 21f
        private const val LeftRootX = 10f
        private const val RightRootX = 13f
        private const val TipTrailingY = 16f
        private const val RootTrailingY = 13.5f
        private const val Tuck = 0.35f

        /** The reveal's edge at plane x [x]: along the trailing edges, flat beyond the tips and across the fuselage. */
        fun edgeY(x: Float): Float = when {
            x <= LeftTipX || x >= RightTipX -> TipTrailingY
            x < LeftRootX -> lerp(TipTrailingY, RootTrailingY, (x - LeftTipX) / (LeftRootX - LeftTipX))
            x <= RightRootX -> RootTrailingY
            else -> lerp(RootTrailingY, TipTrailingY, (x - RightRootX) / (RightTipX - RightRootX))
        } - Tuck

        /** The edge as a polyline of plane points, from far left to far right. */
        val edge: List<Offset> = listOf(LeftTipX, LeftRootX, RightRootX, RightTipX).map { Offset(it, edgeY(it)) }
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
    // Back waits for the reveal too: it would change the screen under the plane, which then uncovers another one.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = {},
    )
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
    // Far enough, in plane units, to run past any corner of the window.
    val far = hypot(size.width, size.height) * 2f / g.scale
    // In a frame turned so the heading points up: the cover is everything ahead of the reveal's edge, which follows
    // the wings' trailing edges.
    withTransform({ rotate(g.rotationDegrees, pivot = centre) }) {
        val middle = Offset(centre.x, centre.y - g.along(progress))
        val coverPath = Path().apply {
            moveTo(-far, SweepGeometry.edgeY(-far))
            SweepGeometry.edge.forEach { lineTo(it.x, it.y) }
            lineTo(far, SweepGeometry.edgeY(far))
            lineTo(far, -far)
            lineTo(-far, -far)
            close()
        }
        drawPlane(coverPath, middle, g.scale, cover)
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

/**
 * Material's "flight" glyph, nose up (the Now notification's flight icon), with an ogive nose coming to a point at
 * (11.5, [SweepGeometry.NoseY]) instead of its round cap.
 */
private const val PlanePathData =
    "M21,16v-2l-8,-5V4.2C13,2.9 12.3,1.5 11.5,0.6C10.7,1.5 10,2.9 10,4.2V9l-8,5v2l8,-2.5V19l-2,1.5V22l3.5,-1 3.5,1v-1.5L13,19v-5.5l8,2.5z"
