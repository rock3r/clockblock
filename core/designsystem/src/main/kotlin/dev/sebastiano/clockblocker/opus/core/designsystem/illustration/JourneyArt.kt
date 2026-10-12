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

        // Soft ambient backdrop behind the two dials.
        fill(circle(60f, cy, 44f), colors.secondaryContainer.copy(alpha = 0.32f))

        val left = circle(lx, cy, r)
        val right = circle(rx, cy, r)
        paper(left, colors.primaryContainer)
        paper(right, colors.inverse)

        // Subtle inner dial rings and 12-hour perimeter ticks on both clocks.
        fill(circle(lx, cy, r - 4.5f), colors.surface.copy(alpha = 0.28f))
        fill(circle(rx, cy, r - 4.5f), colors.onInverse.copy(alpha = 0.08f))
        for (i in 0 until 12) {
            val a = i * PI.toFloat() / 6f
            val inner = if (i % 3 == 0) r - 6.5f else r - 4.8f
            val outer = r - 2.6f
            val w = if (i % 3 == 0) 1.8f else 1.1f
            line(
                lx + inner * sin(a),
                cy - inner * cos(a),
                lx + outer * sin(a),
                cy - outer * cos(a),
                width = w,
                color = colors.ink.copy(alpha = 0.35f),
            )
            line(
                rx + inner * sin(a),
                cy - inner * cos(a),
                rx + outer * sin(a),
                cy - outer * cos(a),
                width = w,
                color = colors.onInverse.copy(alpha = 0.35f),
            )
        }

        // Subtle clock hands in the lower-left of the body clock and stars in the lower-right of the night clock.
        line(lx - 10f, cy + 10f, lx - 16f, cy + 6f, width = 2.0f, color = colors.ink.copy(alpha = 0.55f))
        line(lx - 10f, cy + 10f, lx - 5f, cy + 15f, width = 1.6f, color = colors.ink.copy(alpha = 0.55f))
        dot(lx - 10f, cy + 10f, 1.6f, colors.ink, alpha = 0.6f)
        dot(rx + 15f, cy + 6f, 1.3f, colors.onInverse, alpha = 0.75f)
        dot(rx + 9f, cy + 12f, 1.0f, colors.onInverse, alpha = 0.55f)

        // Overlap lens (the synchronized zone between home and destination time).
        val overlap = intersect(left, right)
        fill(overlap, colors.tertiary)
        val innerOverlap = intersect(circle(lx, cy, r - 5f), circle(rx, cy, r - 5f))
        fill(innerOverlap, colors.surface.copy(alpha = 0.22f))
        ink(overlap, width = 1.6f, color = colors.ink.copy(alpha = 0.45f))

        // Sun in the left disc, top-left; crescent in the right disc, top-right.
        val sunCenterX = lx - 11f
        val sunCenterY = cy - 9f
        fill(intersect(polygon(MaterialShapes.Sunny, sunCenterX, sunCenterY, 20f), left), colors.tertiary)
        fill(intersect(circle(sunCenterX, sunCenterY, 5.5f), left), colors.surface.copy(alpha = 0.65f))

        val moon = minus(circle(rx + 10f, cy - 8f, 9f), circle(rx + 14.5f, cy - 12f, 8f))
        fill(intersect(moon, right), colors.onInverse)

        // Synchronization sparkle inside the lower center of the overlap lens.
        fill(
            intersect(polygon(MaterialShapes.PuffyDiamond, 60f, cy + 9f, 8.5f + 3.5f * t), overlap),
            colors.surface.copy(alpha = 0.88f),
        )

        // Arc arrow sweeping over the top, right → left (the clock you are moving towards).
        val ax = 60f
        val ay = 66f
        val ar = 46f
        val start = -34f
        ink(
            path { arcTo(ax, ay, ar, start, -112f, forceMoveTo = true) },
            width = 2f,
            color = colors.primary.copy(alpha = 0.25f),
            dash = floatArrayOf(3f, 4f),
        )
        val sweep = -112f * trim.value
        if (sweep < -1f) {
            val startRad = start * PI.toFloat() / 180f
            dot(ax + ar * cos(startRad), ay + ar * sin(startRad), 2.8f, colors.primary)
            ink(path { arcTo(ax, ay, ar, start, sweep, forceMoveTo = true) }, width = 4.5f, color = colors.primary)
            val end = (start + sweep) * PI.toFloat() / 180f
            val tipX = ax + ar * cos(end)
            val tipY = ay + ar * sin(end)
            val dirX = sin(end)
            val dirY = -cos(end)
            for (s in floatArrayOf(-1f, 1f)) {
                val a = atan2(-dirY, -dirX) + s * 0.65f
                line(tipX, tipY, tipX + 8.5f * cos(a), tipY + 8.5f * sin(a), width = 4.5f, color = colors.primary)
            }
        }

        // Three puffy-diamond stars with companion sparkles.
        fill(polygon(MaterialShapes.PuffyDiamond, 104f, 26f, 10f), colors.tertiary)
        dot(96f, 20f, 1.5f, colors.tertiary, alpha = 0.7f)
        fill(polygon(MaterialShapes.PuffyDiamond, 14f, 30f, 7.5f), colors.tertiary)
        fill(polygon(MaterialShapes.PuffyDiamond, 104f, 104f, 6.5f), colors.primary)
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
        // Hemisphere with an atmospheric rim, graticule lines and dot matrix, clipped flat at the bottom.
        val cx = 60f
        val cy = 112f
        val r = 58f
        val clipBox = rect(0f, 0f, 120f, 108f)
        val atmosphere = intersect(circle(cx, cy, r + 5f), clipBox)
        fill(atmosphere, colors.primary.copy(alpha = 0.12f))

        val globe = intersect(circle(cx, cy, r), clipBox)
        paper(globe, colors.primaryContainer)

        // Upper sunlit rim highlight along the curvature of the globe.
        val rimHighlight = intersect(minus(circle(cx, cy, r), circle(cx, cy + 4.5f, r - 2f)), clipBox)
        fill(rimHighlight, colors.surface.copy(alpha = 0.42f))

        // Subtle latitude and meridian graticule curves.
        val graticuleColor = colors.primary.copy(alpha = 0.16f)
        ink(
            intersect(path { moveTo(14f, 68f); quadTo(60f, 58f, 106f, 68f) }, globe),
            width = 1.2f,
            color = graticuleColor,
        )
        ink(
            intersect(path { moveTo(6f, 84f); quadTo(60f, 72f, 114f, 84f) }, globe),
            width = 1.2f,
            color = graticuleColor,
        )
        ink(
            intersect(path { moveTo(2f, 100f); quadTo(60f, 89f, 118f, 100f) }, globe),
            width = 1.2f,
            color = graticuleColor,
        )
        for (offset in floatArrayOf(-24f, 0f, 24f)) {
            ink(
                intersect(
                    path {
                        moveTo(60f + offset * 0.55f, 54f)
                        quadTo(60f + offset, 80f, 60f + offset * 1.35f, 108f)
                    },
                    globe,
                ),
                width = 1.2f,
                color = graticuleColor,
            )
        }

        var row = 0
        var gy = 58f
        while (gy <= 106f) {
            var gx = if (row % 2 == 0) 4f else 7.5f
            while (gx <= 116f) {
                val dx = gx - cx
                val dy = gy - cy
                if (dx * dx + dy * dy < (r - 3f) * (r - 3f)) {
                    val edge = 1f - (dx * dx + dy * dy) / (r * r)
                    dot(gx, gy, 1.15f, colors.primary, alpha = 0.25f + 0.48f * edge)
                }
                gx += 7f
            }
            gy += 6f
            row++
        }

        // Crisp horizon baseline.
        line(4f, 108f, 116f, 108f, width = 2f, color = colors.ink.copy(alpha = 0.28f))

        // Subtle ground shadow of the flight path projected onto the globe surface.
        ink(
            path {
                moveTo(18f, 76f)
                quadTo(58f, 52f, 102f, 64f)
            },
            width = 1.8f,
            color = colors.ink.copy(alpha = 0.15f),
        )

        // Route: a great-circle arc from left to right.
        val route = path {
            moveTo(18f, 76f)
            quadTo(56f, 2f, 102f, 64f)
        }
        val m = measure
        m.setPath(route, false)
        val length = m.length
        val at = plane.value * length
        ink(route, width = 2f, color = colors.ink.copy(alpha = 0.55f), dash = floatArrayOf(3f, 5f))
        if (at > 0.5f) {
            val done = Path()
            m.getSegment(0f, at, done, true)
            ink(done, width = 5.5f, color = colors.primary.copy(alpha = 0.20f))
            ink(done, width = 2.8f, color = colors.primary)
        }

        // Departure and arrival pins.
        dot(18f, 76f, 6f, colors.primary, alpha = 0.22f)
        dot(18f, 76f, 3.8f, colors.primary)
        dot(18f, 76f, 1.5f, colors.surface)

        dot(102f, 64f, 7f, colors.tertiary, alpha = 0.25f)
        dot(102f, 64f, 4.4f, colors.tertiary)
        dot(102f, 64f, 1.8f, colors.surface)

        // Swept-wing aircraft aligned with the route tangent.
        val pos = m.getPosition(at)
        val tan = m.getTangent(at)
        val heading = atan2(tan.y, tan.x) * 180f / PI.toFloat() + 90f
        val px = (pos.x - x(0f)) / u
        val py = (pos.y - y(0f)) / u
        val jet = rotated(
            path {
                moveTo(px, py - 9.5f)
                cubicTo(px + 1.5f, py - 7.5f, px + 1.8f, py - 4.5f, px + 1.8f, py - 2.5f)
                lineTo(px + 9.5f, py + 2.2f)
                lineTo(px + 9.5f, py + 4.0f)
                lineTo(px + 1.7f, py + 1.8f)
                lineTo(px + 1.2f, py + 6.2f)
                lineTo(px + 4.8f, py + 8.4f)
                lineTo(px + 4.8f, py + 9.6f)
                lineTo(px, py + 8.2f)
                lineTo(px - 4.8f, py + 9.6f)
                lineTo(px - 4.8f, py + 8.4f)
                lineTo(px - 1.2f, py + 6.2f)
                lineTo(px - 1.7f, py + 1.8f)
                lineTo(px - 9.5f, py + 4.0f)
                lineTo(px - 9.5f, py + 2.2f)
                lineTo(px - 1.8f, py - 2.5f)
                cubicTo(px - 1.8f, py - 4.5f, px - 1.5f, py - 7.5f, px, py - 9.5f)
                close()
            },
            degrees = heading,
            cx = px,
            cy = py,
        )
        paper(jet, colors.tertiary)
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
        if (s > 0.01f) {
            // Soft outer celebration glow.
            fill(circle(60f, 60f, 45f * s), colors.tertiary.copy(alpha = 0.15f))
        }

        // Confetti shapes and companion sparkles around (behind) the flower, at rest.
        val confetti = listOf(
            Triple(MaterialShapes.Clover4Leaf, colors.tertiary, 13f),
            Triple(MaterialShapes.Sunny, colors.primaryContainer, 12f),
            Triple(MaterialShapes.PuffyDiamond, colors.primary, 9.5f),
            Triple(MaterialShapes.Clover4Leaf, colors.primary, 9.5f),
            Triple(MaterialShapes.Sunny, colors.tertiary, 8.5f),
        )
        confetti.forEachIndexed { i, (shape, color, size) ->
            val a = (phase + i / confetti.size.toFloat()) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val orbit = 47f + if (i % 2 == 0) 0f else 5f
            val ox = 60f + orbit * cos(a)
            val oy = 60f + orbit * sin(a)
            fill(polygon(shape, ox, oy, size * s, rotation = phase * 360f * if (i % 2 == 0) 1f else -1f), color)

            val dotAngle = a + PI.toFloat() / confetti.size
            val dotOrbit = 49f * s
            dot(60f + dotOrbit * cos(dotAngle), 60f + dotOrbit * sin(dotAngle), 1.8f * s, colors.tertiary, alpha = 0.65f)
        }

        if (s > 0.01f) {
            // Multi-layered petals with an offset secondary corolla and scalloped inner ring.
            paper(polygon(MaterialShapes.Flower, 60f, 60f, 76f * s), colors.primaryContainer)
            fill(
                polygon(MaterialShapes.Flower, 60f, 60f, 62f * s, rotation = 22.5f),
                colors.tertiary.copy(alpha = 0.35f),
            )
            fill(polygon(MaterialShapes.Cookie9Sided, 60f, 60f, 47f * s), colors.secondaryContainer)
            fill(circle(60f, 60f, 20f * s), colors.surface)
            ink(circle(60f, 60f, 20f * s), width = 2f, color = colors.primary.copy(alpha = 0.35f))
        }

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
        // Ground shadow and caster wheels beneath the suitcase.
        fill(oval(22f, 103f, 76f, 6f), colors.shadow)
        fill(roundRect(26f, 100f, 8f, 6f, 2.5f), colors.inverse)
        fill(roundRect(86f, 100f, 8f, 6f, 2.5f), colors.inverse)

        // Handle (arch) and mounting posts behind the case.
        val handle = minus(roundRect(44f, 25f, 32f, 24f, 10f), roundRect(50f, 32f, 20f, 19f, 5f))
        paper(handle, colors.inverse)
        line(52f, 28f, 68f, 28f, width = 1.8f, color = colors.surface.copy(alpha = 0.40f))
        fill(roundRect(42.5f, 38f, 8.5f, 6f, 2f), colors.ink)
        fill(roundRect(69f, 38f, 8.5f, 6f, 2f), colors.ink)

        // Case body.
        val case = roundRect(16f, 42f, 88f, 62f, 14f)
        paper(case, colors.primaryContainer)
        line(28f, 46f, 92f, 46f, width = 2f, color = colors.surface.copy(alpha = 0.45f))

        // Corner reinforcement patches clipped to the case.
        val corners = union(
            union(circle(18f, 44f, 13f), circle(102f, 44f, 13f)),
            union(circle(18f, 102f, 13f), circle(102f, 102f, 13f)),
        )
        fill(intersect(corners, case), colors.secondaryContainer.copy(alpha = 0.75f))

        // Vertical straps and buckles.
        for (sx in floatArrayOf(29f, 84f)) {
            val strap = intersect(rect(sx, 42f, 7f, 62f), case)
            fill(strap, colors.secondaryContainer)
            line(sx, 43f, sx, 103f, width = 1.4f, color = colors.ink.copy(alpha = 0.35f))
            line(sx + 7f, 43f, sx + 7f, 103f, width = 1.4f, color = colors.ink.copy(alpha = 0.35f))
            val buckle = roundRect(sx - 1.5f, 79f, 10f, 7.5f, 2f)
            fill(buckle, colors.tertiary)
            ink(buckle, width = 1.4f)
            line(sx + 3.5f, 80.5f, sx + 3.5f, 85f, width = 1.4f, color = colors.surface)
        }
        ink(case, width = 2f)

        // Recessed clock bezel and dial face on the side of the suitcase.
        val bezel = circle(60f, 73f, 21f)
        fill(bezel, colors.secondaryContainer)
        ink(bezel, width = 1.8f)
        val face = circle(60f, 73f, 18f)
        fill(face, colors.surface)
        ink(face, width = 2f)
        ink(circle(60f, 73f, 15.2f), width = 0.8f, color = colors.ink.copy(alpha = 0.14f))
        for (i in 0 until 12) {
            val a = i * PI.toFloat() / 6f
            val inner = if (i % 3 == 0) 12.8f else 14.8f
            line(
                60f + inner * sin(a),
                73f - inner * cos(a),
                60f + 16.2f * sin(a),
                73f - 16.2f * cos(a),
                width = if (i % 3 == 0) 2f else 1.2f,
            )
        }
        // Hour hand ~10, minute hand ~2, second hand ticks with counterweight tail.
        val h = -60f * PI.toFloat() / 180f
        line(60f, 73f, 60f + 8.5f * sin(h), 73f - 8.5f * cos(h), width = 3f)
        val mn = 60f * PI.toFloat() / 180f
        line(60f, 73f, 60f + 12.2f * sin(mn), 73f - 12.2f * cos(mn), width = 2.4f)
        val sec = (ticks.intValue * 6f) * PI.toFloat() / 180f
        line(
            60f - 3.8f * sin(sec),
            73f + 3.8f * cos(sec),
            60f + 14.2f * sin(sec),
            73f - 14.2f * cos(sec),
            width = 1.6f,
            color = colors.tertiary,
        )
        dot(60f, 73f, 2.6f, colors.tertiary)
        dot(60f, 73f, 1.0f, colors.surface)

        // Luggage tag hanging off the handle, with a reinforced grommet and "?".
        ink(path { moveTo(72f, 38f); quadTo(86f, 34f, 95f, 51f) }, width = 1.6f)
        val tag = rotated(roundRect(84f, 48f, 22f, 29f, 5f), 14f, 95f, 50f)
        paper(tag, colors.tertiary)
        val grommet = rotated(circle(95f, 53f, 2.3f), 14f, 95f, 50f)
        fill(grommet, colors.surface)
        ink(grommet, width = 1.2f)
        serifText("?", 93f, 65f, 16f, colors.inverse)
    }
}


/** Where Bloom's still confetti sit around the flower (the old orbit's rest frame). */
private const val BloomConfettiPhase = 0.06f
