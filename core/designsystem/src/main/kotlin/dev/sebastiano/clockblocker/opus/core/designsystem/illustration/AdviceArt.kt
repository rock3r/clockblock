package dev.sebastiano.clockblocker.opus.core.designsystem.illustration

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.DuskPalette
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyPhase
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Advice illustrations (design.md §2.4 subjects 2–6, 8, 9): shown in the "Why?" sheet and onboarding. Ambient loops
 * are slow and self-timed; under reduce motion (or animated = false) each rests on a complete still.
 */

/** The illustration that explains [type]. */
@Composable
fun AdviceArt(
    type: AdviceType,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    when (type) {
        AdviceType.SeeBrightLight, AdviceType.SeeLight -> WindowLightArt(modifier, animated = animated, contentDescription = contentDescription)
        AdviceType.AvoidLight -> ShadesOnArt(modifier, animated = animated, contentDescription = contentDescription)
        AdviceType.Sleep -> PillowMoonArt(modifier, animated = animated, contentDescription = contentDescription)
        AdviceType.Nap, AdviceType.OptionalNap -> PowerNapArt(modifier, animated = animated, contentDescription = contentDescription)
        AdviceType.Melatonin -> NightCapsuleArt(modifier, animated = animated, contentDescription = contentDescription)
        AdviceType.Caffeine -> LittleAndOftenArt(modifier, animated = animated, contentDescription = contentDescription)
        AdviceType.AvoidCaffeine -> LittleAndOftenArt(modifier, avoid = true, animated = animated, contentDescription = contentDescription)
        AdviceType.PeakFatigue -> RunningLowArt(modifier, animated = animated, contentDescription = contentDescription)
        AdviceType.Flight -> GreatCircleArt(modifier, animated = animated, contentDescription = contentDescription)
    }
}

/** **Shades on** (avoid light): a very sunny sun wearing sunglasses that drop in with a bounce. [progress] = drop. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShadesOnArt(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    val drop = rememberEntrance(progress, animated, bouncy = true)
    ArtCanvas(modifier, contentDescription, animated, ambientPeriodMillis = 0) {
        // Horizon.
        val hill = intersect(oval(2f, 90f, 116f, 52f), rect(0f, 0f, 120f, 110f))
        paper(hill, colors.secondaryContainer)
        // Sun.
        paper(polygon(MaterialShapes.VerySunny, 60f, 54f, 70f), colors.tertiary)

        // Sunglasses.
        val dy = lerp(-46f, 0f, drop.value)
        val ly = 52f + dy
        // Sunglasses stay dark in every theme (white "shades" read as goggles on a dark surface).
        val lens = DuskPalette.N10
        val left = roundRect(36f, ly - 7f, 22f, 15f, 6f)
        val right = roundRect(62f, ly - 7f, 22f, 15f, 6f)
        line(36f, ly - 3f, 27f, ly - 7f, width = 2f, color = lens)
        line(84f, ly - 3f, 93f, ly - 7f, width = 2f, color = lens)
        ink(path { moveTo(57f, ly - 3f); quadTo(60f, ly - 7f, 63f, ly - 3f) }, width = 2f, color = lens)
        paper(left, lens)
        paper(right, lens)
        line(41f, ly + 3f, 45f, ly - 3f, width = 2.5f, color = colors.highlight)
        line(67f, ly + 3f, 71f, ly - 3f, width = 2.5f, color = colors.highlight)
    }
}

/** **Window light** (see bright light): a four-pane window with the sun peeking in; rays turn slowly. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WindowLightArt(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    ArtCanvas(modifier, contentDescription, animated, ambientPeriodMillis = ClockblockMotion.SunRotationMillis, restPhase = 0.5f) { phase ->
        val sunX = 86f
        val sunY = 36f
        // Rays (behind the frame, so they only show around it).
        for (i in 0 until 8) {
            val a = (phase * 45f + i * 45f) * PI.toFloat() / 180f
            line(sunX + 25f * cos(a), sunY + 25f * sin(a), sunX + 33f * cos(a), sunY + 33f * sin(a), width = 4f, color = colors.tertiary, alpha = progress)
        }
        val frame = roundRect(18f, 20f, 76f, 82f, 24f)
        paper(frame, colors.surfaceHighest)
        val glass = roundRect(25f, 27f, 62f, 68f, 18f)
        fill(glass, sky[SkyPhase.Day].verticalBrush(y(27f), y(95f)))
        fill(intersect(polygon(MaterialShapes.VerySunny, sunX, sunY, 40f), glass), colors.tertiary)
        // Mullions.
        line(56f, 27f, 56f, 95f, width = 4f, color = colors.surfaceHighest)
        line(25f, 60f, 87f, 60f, width = 4f, color = colors.surfaceHighest)
        ink(glass, width = 2f)
        // Sill.
        paper(roundRect(10f, 98f, 92f, 9f, 4.5f), colors.primaryContainer)
    }
}

/** **Pillow moon** (sleep): a crescent resting on a cloud pillow; three Fraunces z's drift up and fade. */
@Composable
fun PillowMoonArt(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    ArtCanvas(modifier, contentDescription, animated, ambientPeriodMillis = ClockblockMotion.DriftMillis * 2) { phase ->
        val pillow = union(union(roundRect(12f, 74f, 92f, 30f, 15f), circle(42f, 76f, 14f)), circle(68f, 72f, 17f))
        paper(pillow, colors.primaryContainer)
        val moon = minus(circle(56f, 46f, 22f), circle(56f, 33f, 21f))
        paper(rotated(moon, -12f, 56f, 46f), colors.tertiary)

        val zs = listOf(Triple(84f, 44f, 11f), Triple(94f, 31f, 14f), Triple(104f, 16f, 17f))
        zs.forEachIndexed { i, (zx, zy, size) ->
            if (moving) {
                val t = (phase * 3f + i) % 3f / 3f
                val a = sin(PI.toFloat() * t)
                serifText("z", zx - 6f + 12f * t, zy + 12f - 24f * t, size, colors.ink, alpha = a * progress)
            } else {
                serifText("z", zx, zy, size, colors.ink, alpha = progress)
            }
        }
    }
}

/** **Night capsule** (melatonin): a diagonally split capsule with a tiny crescent; a sparkle twinkles. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NightCapsuleArt(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    val lilac = ClockblockTheme.adviceColors[AdviceType.Melatonin].color
    ArtCanvas(modifier, contentDescription, animated, ambientPeriodMillis = ClockblockMotion.TwinkleMillis, restPhase = 0.25f, reverse = true) { phase ->
        val tilt = -40f
        val pcx = 58f
        val pcy = 64f
        val capsule = rotated(roundRect(pcx - 19f, pcy - 42f, 38f, 84f, 19f), tilt, pcx, pcy)
        val topHalf = intersect(capsule, rotated(rect(pcx - 40f, pcy - 60f, 80f, 60f), tilt, pcx, pcy))
        val bottomHalf = minus(capsule, topHalf)
        with(draw) { drawPath(moved(Path().apply { addPath(capsule) }, 3f, 3f), colors.shadow) }
        fill(topHalf, lilac)
        fill(bottomHalf, colors.surfaceHighest)
        ink(rotated(path { moveTo(pcx - 19f, pcy); lineTo(pcx + 19f, pcy) }, tilt, pcx, pcy), width = 2f)
        ink(capsule, width = 2f)
        // Highlight along the upper-left edge.
        ink(rotated(path { moveTo(pcx - 11f, pcy - 30f); lineTo(pcx - 11f, pcy - 12f) }, tilt, pcx, pcy), width = 3f, color = colors.highlight)
        // Tiny crescent in the upper half.
        val moon = minus(circle(pcx + 2f, pcy - 22f, 7f), circle(pcx + 5.5f, pcy - 25f, 6f))
        fill(rotated(moon, tilt, pcx, pcy), colors.surface)

        val tw = 0.65f + 0.35f * sin(phase * 2f * PI.toFloat())
        fill(polygon(MaterialShapes.PuffyDiamond, 96f, 28f, 18f * tw * progress), colors.tertiary)
        fill(polygon(MaterialShapes.PuffyDiamond, 24f, 98f, 10f * (1.3f - tw * 0.5f) * progress), colors.tertiary)
    }
}

/**
 * **Little & often** (caffeine): a cup with three sine-wave steam lines (echoing the wavy indicator). [avoid]
 * lays the steam flat and strikes the cup through.
 */
@Composable
fun LittleAndOftenArt(
    modifier: Modifier = Modifier,
    avoid: Boolean = false,
    progress: Float = 1f,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    val cupShape = remember {
        RoundedPolygon(
            vertices = floatArrayOf(-1f, -0.75f, 1f, -0.75f, 0.68f, 0.75f, -0.68f, 0.75f),
            rounding = CornerRounding(0.28f, smoothing = 0.4f),
        )
    }
    ArtCanvas(modifier, contentDescription, animated && !avoid, ambientPeriodMillis = ClockblockMotion.SteamMillis) { phase ->
        // Saucer, handle, cup.
        paper(oval(18f, 94f, 84f, 12f), colors.surfaceHighest)
        paper(minus(circle(82f, 74f, 12f), circle(82f, 74f, 6.5f)), colors.primaryContainer)
        paper(polygon(cupShape, 56f, 76f, 54f), colors.primaryContainer)
        line(36f, 62f, 76f, 62f, width = 2f, color = colors.highlight)

        // Steam.
        for (i in 0 until 3) {
            val x0 = 44f + i * 12f
            val steam = path {
                var yy = 52f
                moveTo(x0, yy)
                while (yy > 18f) {
                    yy -= 1f
                    val amp = if (avoid) 0f else 3.2f * progress
                    lineTo(x0 + amp * sin((yy / 15f + phase + i * 0.3f) * 2f * PI.toFloat()), yy)
                }
            }
            ink(steam, width = 2f, color = colors.ink.copy(alpha = if (avoid) 0.4f else 0.8f))
        }

        if (avoid) {
            val strike = rotated(roundRect(16f, 58f, 88f, 6f, 3f), -32f, 60f, 61f)
            val gap = rotated(roundRect(13f, 55f, 94f, 12f, 6f), -32f, 60f, 61f)
            fill(gap, colors.surface)
            fill(strike, colors.ink.copy(alpha = 1f))
        }
    }
}

/** **Power nap**: a hammock slung between two posts, a sun half-set behind; the hammock sways ±3°. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PowerNapArt(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    ArtCanvas(modifier, contentDescription, animated, ambientPeriodMillis = ClockblockMotion.SwayMillis, restPhase = 0f) { phase ->
        val sun = intersect(circle(60f, 86f, 30f), rect(0f, 0f, 120f, 86f))
        fill(sun, colors.tertiary)
        paper(roundRect(6f, 86f, 108f, 14f, 7f), colors.secondaryContainer)
        line(20f, 90f, 20f, 38f, width = 4f)
        line(100f, 90f, 100f, 38f, width = 4f)

        val sway = 3f * sin(phase * 2f * PI.toFloat()) * progress
        val ropes = path {
            moveTo(20f, 42f); lineTo(34f, 58f)
            moveTo(100f, 42f); lineTo(86f, 58f)
        }
        ink(rotated(ropes, sway, 60f, 40f), width = 1.5f)
        val sling = path {
            moveTo(32f, 57f)
            quadTo(60f, 98f, 88f, 57f)
            quadTo(60f, 70f, 32f, 57f)
            close()
        }
        // The sling hangs in the air, so it gets no cut-paper shadow; a pillow at the head end, then the fabric.
        fill(rotated(roundRect(36f, 55f, 18f, 9f, 4.5f), 20f + sway, 45f, 59f), colors.surfaceHighest)
        fill(rotated(sling, sway, 60f, 40f), colors.primaryContainer)
        ink(rotated(path { moveTo(32f, 57f); quadTo(60f, 98f, 88f, 57f) }, sway, 60f, 40f), width = 2f)
        ink(rotated(path { moveTo(42f, 66f); quadTo(60f, 84f, 78f, 66f) }, sway, 60f, 40f), width = 1.5f, color = colors.highlight)
    }
}

/** **Running low** (peak fatigue): a battery with a wavy, sloshing fill in front of a coral burst. [progress] = charge. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RunningLowArt(
    modifier: Modifier = Modifier,
    progress: Float = 0.24f,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    val coral = ClockblockTheme.adviceColors[AdviceType.PeakFatigue]
    ArtCanvas(modifier, contentDescription, animated, ambientPeriodMillis = ClockblockMotion.PulseMillis) { phase ->
        fill(polygon(MaterialShapes.SoftBurst, 60f, 62f, 104f), coral.container)
        paper(roundRect(51f, 22f, 18f, 10f, 3f), colors.ink.copy(alpha = 1f), shadow = false)
        val body = roundRect(36f, 30f, 48f, 74f, 11f)
        paper(body, colors.surface)
        val inner = roundRect(42f, 36f, 36f, 62f, 6f)
        val level = 98f - 62f * progress.coerceIn(0f, 1f)
        val tilt = 2.5f * sin(phase * 2f * PI.toFloat())
        val fillPath = path {
            moveTo(40f, 100f)
            var xx = 40f
            lineTo(xx, level)
            while (xx < 80f) {
                xx += 1f
                val wave = 2.2f * sin(((xx - 40f) / 18f + phase) * 2f * PI.toFloat())
                lineTo(xx, level + wave + tilt * (xx - 60f) / 20f)
            }
            lineTo(80f, 100f)
            close()
        }
        fill(intersect(fillPath, inner), coral.color)
        ink(body, width = 2.5f)
        // A small bolt, struck out faintly: it is a low point, not an emergency.
        ink(path { moveTo(63f, 44f); lineTo(55f, 58f); lineTo(65f, 58f); lineTo(57f, 72f) }, width = 2.5f, color = colors.ink.copy(alpha = 0.5f))
    }
}
