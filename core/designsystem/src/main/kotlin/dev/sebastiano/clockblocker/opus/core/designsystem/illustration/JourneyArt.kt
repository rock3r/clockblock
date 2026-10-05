package dev.sebastiano.clockblocker.opus.core.designsystem.illustration

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.MotionDurationScale
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * Journey illustrations: onboarding hero, trip, celebration and empty state (design.md §2.4 subjects 1, 7, 10, 11).
 * Each takes `progress` (its one meaningful parameter), `animated` (ambient/entrance motion; always off under
 * reduce motion) and an optional content description (null = decorative, the usual case next to a label).
 */

/**
 * **Two clocks** — the body clock (sun disc) and the local clock (moon disc). [progress] 0 = far apart
 * (jet-lagged), 1 = slid together (adapted). Changes slide with the container-spatial token; the arc arrow
 * trims in once.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TwoClocksArt(
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    val together = rememberArtValue(progress, animated)
    val trim = rememberEntrance(1f, animated)
    ArtCanvas(modifier, contentDescription, animated, ambientPeriodMillis = 0) {
        val t = together.value
        val cy = 66f
        val r = 30f
        val lx = lerp(43f, 52f, t)
        val rx = lerp(77f, 68f, t)
        val left = circle(lx, cy, r)
        val right = circle(rx, cy, r)
        paper(left, colors.primaryContainer)
        paper(right, colors.inverse)
        fill(intersect(left, right), colors.tertiary)

        // Sun in the left disc, top-left; crescent in the right disc, top-right.
        fill(intersect(polygon(MaterialShapes.Sunny, lx - 11f, cy - 9f, 20f), left), colors.tertiary)
        val moon = minus(circle(rx + 10f, cy - 8f, 9f), circle(rx + 14.5f, cy - 12f, 8f))
        fill(intersect(moon, right), colors.onInverse)

        // Arc arrow sweeping over the top, right → left (the clock you are moving towards).
        val sweep = -112f * trim.value
        if (sweep < -1f) {
            val ax = 60f
            val ay = 66f
            val ar = 46f
            val start = -34f
            ink(path { arcTo(ax, ay, ar, start, sweep, forceMoveTo = true) }, width = 5f, color = colors.primary)
            val end = (start + sweep) * PI.toFloat() / 180f
            val tipX = ax + ar * cos(end)
            val tipY = ay + ar * sin(end)
            val dirX = sin(end)
            val dirY = -cos(end)
            for (s in floatArrayOf(-1f, 1f)) {
                val a = atan2(-dirY, -dirX) + s * 0.65f
                line(tipX, tipY, tipX + 8f * cos(a), tipY + 8f * sin(a), width = 5f, color = colors.primary)
            }
        }

        // Three puffy-diamond stars.
        fill(polygon(MaterialShapes.PuffyDiamond, 104f, 26f, 10f), colors.tertiary)
        fill(polygon(MaterialShapes.PuffyDiamond, 14f, 30f, 7f), colors.tertiary)
        fill(polygon(MaterialShapes.PuffyDiamond, 104f, 104f, 6f), colors.primary)
    }
}

/**
 * **Great circle** — a flight over a dot-matrix hemisphere. [progress] is the plane's position along the route
 * (0 = departure, 1 = arrival); the travelled part is solid, the rest dashed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GreatCircleArt(
    modifier: Modifier = Modifier,
    progress: Float = 0.6f,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    val plane = rememberEntrance(progress.coerceIn(0f, 1f), animated)
    val measure = remember { PathMeasure() }
    ArtCanvas(modifier, contentDescription, animated, ambientPeriodMillis = 0) {
        // Hemisphere with a dot matrix, clipped flat at the bottom.
        val cx = 60f
        val cy = 112f
        val r = 58f
        val globe = intersect(circle(cx, cy, r), rect(0f, 0f, 120f, 108f))
        paper(globe, colors.primaryContainer)
        var row = 0
        var gy = 58f
        while (gy <= 106f) {
            var gx = if (row % 2 == 0) 4f else 7.5f
            while (gx <= 116f) {
                val dx = gx - cx
                val dy = gy - cy
                if (dx * dx + dy * dy < (r - 3f) * (r - 3f)) {
                    val edge = 1f - (dx * dx + dy * dy) / (r * r)
                    dot(gx, gy, 1.1f, colors.primary, alpha = 0.25f + 0.45f * edge)
                }
                gx += 7f
            }
            gy += 6f
            row++
        }

        // Route: a great-circle arc from left to right.
        val route = path {
            moveTo(18f, 76f)
            quadTo(56f, 2f, 102f, 64f)
        }
        val m = measure
        m.setPath(route, false)
        val length = m.length
        val at = plane.value * length
        ink(route, width = 2f, color = colors.ink, dash = floatArrayOf(3f, 5f))
        if (at > 0.5f) {
            val done = Path()
            m.getSegment(0f, at, done, true)
            ink(done, width = 2.5f, color = colors.primary)
        }
        dot(18f, 76f, 3.5f, colors.primary)
        dot(102f, 64f, 4f, colors.tertiary)
        dot(102f, 64f, 1.6f, colors.surface)

        // The plane: MaterialShapes.Arrow points up; rotate it onto the tangent.
        val pos = m.getPosition(at)
        val tan = m.getTangent(at)
        val heading = atan2(tan.y, tan.x) * 180f / PI.toFloat() + 90f
        val px = (pos.x - x(0f)) / u
        val py = (pos.y - y(0f)) / u
        paper(polygon(MaterialShapes.Arrow, px, py, 16f, rotation = heading), colors.tertiary)
    }
}

/**
 * **Bloom** — the adaptation celebration. A flower scales in with a bounce, then a check trims on; still
 * confetti shapes sit around it (no orbit loop: the celebration's burst is the motion). [progress] is the
 * flower reveal (1 = complete picture); [checkProgress] the check trim, so a host can sequence them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BloomArt(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    animated: Boolean = true,
    contentDescription: String? = null,
    checkProgress: Float = progress,
) {
    val scale = rememberEntrance(progress, animated, bouncy = true)
    val check = rememberEntrance(checkProgress, animated)
    val measure = remember { PathMeasure() }
    ArtCanvas(modifier, contentDescription, animated, ambientPeriodMillis = 0, restPhase = BloomConfettiPhase) { phase ->
        val s = scale.value
        // Confetti shapes around (behind) the flower, at rest.
        val confetti = listOf(
            Triple(MaterialShapes.Clover4Leaf, colors.tertiary, 13f),
            Triple(MaterialShapes.Sunny, colors.secondaryContainer, 12f),
            Triple(MaterialShapes.PuffyDiamond, colors.primary, 9f),
            Triple(MaterialShapes.Clover4Leaf, colors.primary, 9f),
            Triple(MaterialShapes.Sunny, colors.tertiary, 8f),
        )
        confetti.forEachIndexed { i, (shape, color, size) ->
            val a = (phase + i / confetti.size.toFloat()) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val orbit = 47f + if (i % 2 == 0) 0f else 5f
            val ox = 60f + orbit * cos(a)
            val oy = 60f + orbit * sin(a)
            fill(polygon(shape, ox, oy, size * s, rotation = phase * 360f * if (i % 2 == 0) 1f else -1f), color)
        }

        if (s > 0.01f) paper(polygon(MaterialShapes.Flower, 60f, 60f, 74f * s), colors.primaryContainer)
        fill(polygon(MaterialShapes.Circle, 60f, 60f, 40f * s), colors.surface)

        val tick = path {
            moveTo(48f, 61f)
            lineTo(57f, 70f)
            lineTo(73f, 52f)
        }
        val c = check.value.coerceIn(0f, 1f)
        if (c > 0.01f) {
            measure.setPath(tick, false)
            val seg = Path()
            measure.getSegment(0f, measure.length * c, seg, true)
            ink(seg, width = 6f, color = colors.primary)
        }
    }
}

/**
 * **Suitcase o'clock** — the "no trips yet" empty state. The clock's second hand ticks once a second, three
 * times, then rests ([progress] is unused beyond 1 = rested; kept for signature symmetry).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SuitcaseOClockArt(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    val reduce = LocalReduceMotion.current
    val inspection = LocalInspectionMode.current
    val play = animated && !reduce && !inspection && progress >= 1f
    val ticks = remember { mutableIntStateOf(if (play) 0 else 3) }
    LaunchedEffect(play) {
        if (!play) {
            ticks.intValue = 3
            return@LaunchedEffect
        }
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (scale == 0f) {
            ticks.intValue = 3
            return@LaunchedEffect
        }
        ticks.intValue = 0
        repeat(3) {
            delay((1_000 * scale).toLong())
            ticks.intValue = it + 1
        }
    }
    ArtCanvas(modifier, contentDescription, animated = false, ambientPeriodMillis = 0) {
        // Handle (arch) behind the case.
        val handle = minus(roundRect(44f, 26f, 32f, 24f, 10f), roundRect(51f, 33f, 18f, 20f, 5f))
        paper(handle, colors.inverse)

        // Case.
        val case = roundRect(16f, 42f, 88f, 62f, 14f)
        paper(case, colors.primaryContainer)
        line(32f, 44f, 32f, 102f, width = 4f, color = colors.shadow)
        line(88f, 44f, 88f, 102f, width = 4f, color = colors.shadow)

        // Clock face on its side.
        val face = circle(60f, 73f, 18f)
        fill(face, colors.surface)
        ink(face, width = 2f)
        for (i in 0 until 12) {
            val a = i * PI.toFloat() / 6f
            val inner = if (i % 3 == 0) 13f else 15f
            line(60f + inner * sin(a), 73f - inner * cos(a), 60f + 16f * sin(a), 73f - 16f * cos(a), width = if (i % 3 == 0) 2f else 1.2f)
        }
        // Hour hand ~10, minute hand ~2, second hand ticks.
        val h = -60f * PI.toFloat() / 180f
        line(60f, 73f, 60f + 8f * sin(h), 73f - 8f * cos(h), width = 3f)
        val mn = 60f * PI.toFloat() / 180f
        line(60f, 73f, 60f + 12f * sin(mn), 73f - 12f * cos(mn), width = 2.5f)
        val sec = (ticks.intValue * 6f) * PI.toFloat() / 180f
        line(60f, 73f, 60f + 14f * sin(sec), 73f - 14f * cos(sec), width = 1.5f, color = colors.tertiary)
        dot(60f, 73f, 2.2f, colors.tertiary)

        // Luggage tag hanging off the handle, with a "?".
        ink(path { moveTo(72f, 38f); quadTo(86f, 34f, 94f, 50f) }, width = 1.5f)
        val tag = rotated(roundRect(84f, 48f, 22f, 28f, 5f), 14f, 95f, 50f)
        paper(tag, colors.tertiary)
        fill(rotated(circle(95f, 53f, 2f), 14f, 95f, 50f), colors.surface)
        serifText("?", 93f, 65f, 16f, colors.inverse)
    }
}


/** Where Bloom's still confetti sit around the flower (the old orbit's rest frame). */
private const val BloomConfettiPhase = 0.06f
