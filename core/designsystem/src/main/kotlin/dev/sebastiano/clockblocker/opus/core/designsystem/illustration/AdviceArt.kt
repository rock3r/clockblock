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
        // Warm horizon glow + distant hill behind the sun.
        val clipBottom = rect(0f, 0f, 120f, 110f)
        fill(intersect(circle(60f, 76f, 48f), clipBottom), colors.tertiary.copy(alpha = 0.16f))
        val backHill = intersect(oval(-10f, 82f, 98f, 52f), clipBottom)
        fill(backHill, colors.primaryContainer)

        // Layered sun: outer VerySunny corona, inner Sunny ring, and warm disc core.
        paper(polygon(MaterialShapes.VerySunny, 60f, 52f, 74f), colors.tertiary)
        fill(polygon(MaterialShapes.Sunny, 60f, 52f, 56f, rotation = 15f), colors.surface.copy(alpha = 0.22f))
        ink(circle(60f, 52f, 22f), width = 1.5f, color = colors.ink.copy(alpha = 0.18f))

        // Foreground hill with an exact elliptical rim highlight.
        val frontHill = intersect(oval(12f, 88f, 106f, 46f), clipBottom)
        paper(frontHill, colors.secondaryContainer)
        val hillHighlight = intersect(minus(oval(12f, 88f, 106f, 46f), oval(12f, 90.5f, 106f, 46f)), clipBottom)
        fill(hillHighlight, colors.highlight)

        // Wayfarer sunglasses dropping into place.
        val dy = lerp(-46f, 0f, drop.value)
        val ly = 50f + dy
        // Sunglasses stay dark in every theme (white "shades" read as goggles on a dark surface).
        val frameColor = DuskPalette.N10
        val lensColor = androidx.compose.ui.graphics.Color(0xFF232536)
        // Temple arms tucking back onto the sun (with subtle rim outline so they read on dark backgrounds).
        val leftTemple = path { moveTo(31f, ly - 5f); lineTo(22f, ly - 10f); lineTo(19f, ly - 6f) }
        val rightTemple = path { moveTo(89f, ly - 5f); lineTo(98f, ly - 10f); lineTo(101f, ly - 6f) }
        ink(leftTemple, width = 3.6f, color = colors.highlight)
        ink(rightTemple, width = 3.6f, color = colors.highlight)
        ink(leftTemple, width = 2.4f, color = frameColor)
        ink(rightTemple, width = 2.4f, color = frameColor)

        val leftFrame = union(roundRect(30f, ly - 8f, 26f, 17.5f, 6.5f), roundRect(28f, ly - 7f, 6f, 5.5f, 2f))
        val rightFrame = union(roundRect(64f, ly - 8f, 26f, 17.5f, 6.5f), roundRect(86f, ly - 7f, 6f, 5.5f, 2f))
        val bridge = path {
            moveTo(54f, ly - 6.5f)
            quadTo(60f, ly - 9f, 66f, ly - 6.5f)
            lineTo(65f, ly - 2.5f)
            quadTo(60f, ly - 5.5f, 55f, ly - 2.5f)
            close()
        }
        paper(union(union(leftFrame, rightFrame), bridge), frameColor)

        // Inset tinted lenses + hinge rivets + double diagonal reflections.
        fill(roundRect(32.5f, ly - 5.5f, 21f, 12.5f, 4.5f), lensColor)
        fill(roundRect(66.5f, ly - 5.5f, 21f, 12.5f, 4.5f), lensColor)
        dot(30.8f, ly - 4.5f, 1.1f, colors.tertiary)
        dot(89.2f, ly - 4.5f, 1.1f, colors.tertiary)
        line(36.5f, ly + 4f, 42.5f, ly - 3f, width = 2.6f, color = colors.highlight)
        line(41.5f, ly + 4f, 45.5f, ly - 1f, width = 1.4f, color = colors.highlight)
        line(70.5f, ly + 4f, 76.5f, ly - 3f, width = 2.6f, color = colors.highlight)
        line(75.5f, ly + 4f, 79.5f, ly - 1f, width = 1.4f, color = colors.highlight)
    }
}

/** **Window light** (see bright light): an arched casement window with sunbeams streaming across the glass; rays turn slowly. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WindowLightArt(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    ArtCanvas(modifier, contentDescription, animated, ambientPeriodMillis = ClockblockMotion.SunRotationMillis, restPhase = 0.5f) { phase ->
        val sunX = 84f
        val sunY = 34f
        // Alternating long/short outer rays behind the window frame.
        for (i in 0 until 12) {
            val a = (phase * 45f + i * 30f) * PI.toFloat() / 180f
            val outer = if (i % 2 == 0) 35f else 30f
            line(
                sunX + 24f * cos(a),
                sunY + 24f * sin(a),
                sunX + outer * cos(a),
                sunY + outer * sin(a),
                width = if (i % 2 == 0) 3.8f else 2.6f,
                color = colors.tertiary,
                alpha = progress,
            )
        }

        // Arched casement frame & glass panes.
        val frame = union(circle(56f, 48f, 38f), roundRect(18f, 48f, 76f, 48f, 10f))
        paper(frame, colors.surfaceHighest)
        ink(frame, width = 2f)

        val glass = union(circle(56f, 48f, 31f), roundRect(25f, 48f, 62f, 42f, 6f))
        fill(glass, sky[SkyPhase.Day].verticalBrush(y(17f), y(90f)))

        // Morning cloud wisp & distant hill visible through the window.
        val cloud = union(roundRect(28f, 42f, 22f, 7f, 3.5f), union(circle(36f, 41f, 5f), circle(43f, 42f, 4f)))
        fill(intersect(cloud, glass), colors.surface.copy(alpha = 0.72f))
        val outdoorHill = union(oval(16f, 76f, 56f, 24f), oval(48f, 73f, 52f, 28f))
        fill(intersect(outdoorHill, glass), colors.secondaryContainer.copy(alpha = 0.82f))

        // Sun in the upper-right pane + diagonal sunbeams streaming across the glass.
        fill(intersect(circle(78f, 36f, 21f), glass), colors.tertiary.copy(alpha = 0.28f * progress))
        fill(intersect(polygon(MaterialShapes.VerySunny, 79f, 35f, 36f, rotation = phase * 45f), glass), colors.tertiary)
        fill(intersect(circle(79f, 35f, 10f), glass), colors.surface.copy(alpha = 0.42f))

        val beam1 = path {
            moveTo(70f, 26f)
            lineTo(86f, 38f)
            lineTo(54f, 90f)
            lineTo(32f, 90f)
            close()
        }
        val beam2 = path {
            moveTo(86f, 44f)
            lineTo(88f, 56f)
            lineTo(70f, 90f)
            lineTo(58f, 90f)
            close()
        }
        fill(intersect(beam1, glass), colors.tertiary.copy(alpha = 0.22f * progress))
        fill(intersect(beam2, glass), colors.tertiary.copy(alpha = 0.16f * progress))

        // Mullions & inner glass frame.
        line(56f, 18f, 56f, 89f, width = 4.5f, color = colors.surfaceHighest)
        line(26f, 56f, 86f, 56f, width = 4.5f, color = colors.surfaceHighest)
        line(56f, 18f, 56f, 89f, width = 1.4f, color = colors.ink.copy(alpha = 0.35f))
        line(26f, 56f, 86f, 56f, width = 1.4f, color = colors.ink.copy(alpha = 0.35f))
        ink(glass, width = 2f)

        // Projecting window sill + small sun-seeking sprout on the left.
        val sill = roundRect(10f, 92f, 92f, 10f, 5f)
        paper(sill, colors.primaryContainer)
        ink(sill, width = 2f)
        line(15f, 95f, 97f, 95f, width = 1.6f, color = colors.highlight)

        val pot = path {
            moveTo(29f, 81f)
            lineTo(41f, 81f)
            lineTo(39f, 92f)
            lineTo(31f, 92f)
            close()
        }
        fill(pot, colors.tertiary)
        ink(pot, width = 1.8f)
        fill(rotated(oval(34f, 72f, 9f, 5f), -35f, 35f, 81f), colors.primary)
        fill(rotated(oval(26f, 74f, 8f, 4.5f), 30f, 35f, 81f), colors.primary)
        ink(path { moveTo(35f, 81f); quadTo(36f, 75f, 40f, 73f) }, width = 1.6f)
    }
}

/** **Pillow moon** (sleep): a crescent tucked into a layered cloud pillow; three Fraunces z's drift up and fade. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PillowMoonArt(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    ArtCanvas(modifier, contentDescription, animated, ambientPeriodMillis = ClockblockMotion.DriftMillis * 2) { phase ->
        // Dreamy night halo + stars behind the moon.
        fill(circle(54f, 54f, 42f), colors.secondaryContainer.copy(alpha = 0.55f))
        fill(polygon(MaterialShapes.PuffyDiamond, 24f, 34f, 9f), colors.tertiary)
        fill(polygon(MaterialShapes.PuffyDiamond, 38f, 20f, 6.5f), colors.primary)
        dot(20f, 52f, 1.5f, colors.primary, alpha = 0.65f)
        dot(72f, 24f, 1.4f, colors.tertiary, alpha = 0.75f)

        // Back cloud puff for depth.
        val backCloud = union(
            roundRect(20f, 68f, 76f, 24f, 12f),
            union(circle(38f, 68f, 13f), circle(72f, 66f, 14f)),
        )
        fill(backCloud, colors.secondaryContainer)

        // Crescent moon resting in the cloud, with a warm specular belly highlight.
        val rawMoon = minus(circle(54f, 48f, 23f), circle(60f, 35f, 20f))
        val moon = rotated(rawMoon, -10f, 54f, 48f)
        paper(moon, colors.tertiary)
        ink(moon, width = 2f)
        ink(
            rotated(path { arcTo(54f, 48f, 18.5f, 30f, 95f, forceMoveTo = true) }, -10f, 54f, 48f),
            width = 2.2f,
            color = colors.highlight,
        )

        // Front plush pillow-cloud overlapping the lower curve of the moon so it sits tucked inside.
        val pillow = union(
            union(
                union(roundRect(12f, 74f, 92f, 28f, 14f), circle(34f, 75f, 14f)),
                circle(58f, 72f, 17f),
            ),
            circle(80f, 76f, 13f),
        )
        paper(pillow, colors.primaryContainer)
        ink(pillow, width = 2f)
        ink(path { arcTo(34f, 76f, 10f, 205f, 110f, forceMoveTo = true) }, width = 1.8f, color = colors.highlight)
        ink(path { arcTo(58f, 73f, 12.5f, 210f, 115f, forceMoveTo = true) }, width = 1.8f, color = colors.highlight)
        ink(path { arcTo(56f, 78f, 14f, 25f, 130f, forceMoveTo = true) }, width = 1.5f, color = colors.ink.copy(alpha = 0.28f))

        val zs = listOf(Triple(84f, 45f, 11f), Triple(94f, 31f, 14f), Triple(104f, 16f, 17f))
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

/** **Night capsule** (melatonin): a two-piece capsule with an overlapping lilac cap, crescent emblem, and twinkling sparkles. */
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
        // Soft night aura behind the capsule.
        fill(rotated(oval(24f, 20f, 68f, 88f), tilt, pcx, pcy), lilac.copy(alpha = 0.15f))

        // Lower capsule body + shadow.
        val fullShadow = rotated(roundRect(pcx - 20f, pcy - 43f, 40f, 86f, 20f), tilt, pcx, pcy)
        with(draw) { drawPath(moved(Path().apply { addPath(fullShadow) }, 3.5f, 3.5f), colors.shadow) }

        val bottomBody = rotated(roundRect(pcx - 18.5f, pcy - 6f, 37f, 49f, 18.5f), tilt, pcx, pcy)
        fill(bottomBody, colors.surfaceHighest)
        // Shadow band cast by the overlapping upper cap.
        fill(intersect(bottomBody, rotated(rect(pcx - 22f, pcy - 1f, 44f, 6.5f), tilt, pcx, pcy)), colors.shadow)
        ink(bottomBody, width = 2f)

        // Upper lilac cap (slightly wider so it sleeves over the lower body).
        val topCap = intersect(
            rotated(roundRect(pcx - 20f, pcy - 43f, 40f, 86f, 20f), tilt, pcx, pcy),
            rotated(roundRect(pcx - 24f, pcy - 46f, 48f, 47f, 5f), tilt, pcx, pcy),
        )
        fill(topCap, lilac)
        ink(topCap, width = 2f)

        // Specular highlights along the upper-left wall of cap and body.
        ink(rotated(path { moveTo(pcx - 12.5f, pcy - 28f); lineTo(pcx - 12.5f, pcy - 8f) }, tilt, pcx, pcy), width = 3.2f, color = colors.highlight)
        ink(rotated(path { moveTo(pcx - 11.5f, pcy + 8f); lineTo(pcx - 11.5f, pcy + 23f) }, tilt, pcx, pcy), width = 2.6f, color = colors.highlight)

        // Embossed crescent moon + tiny star in the upper cap.
        val moon = minus(circle(pcx + 1.5f, pcy - 21f, 7.5f), circle(pcx + 5.5f, pcy - 24.2f, 6.2f))
        fill(rotated(moon, tilt, pcx, pcy), colors.surface)
        fill(rotated(circle(pcx + 7.5f, pcy - 17f, 1.4f), tilt, pcx, pcy), colors.surface)

        val tw = 0.65f + 0.35f * sin(phase * 2f * PI.toFloat())
        fill(polygon(MaterialShapes.PuffyDiamond, 96f, 28f, 18f * tw * progress), colors.tertiary)
        fill(polygon(MaterialShapes.PuffyDiamond, 24f, 96f, 11f * (1.3f - tw * 0.5f) * progress), colors.tertiary)
        fill(polygon(MaterialShapes.PuffyDiamond, 26f, 30f, 8f * tw * progress), lilac)
        dot(88f, 44f, 1.6f, colors.tertiary, alpha = 0.7f * progress)
    }
}

/**
 * **Little & often** (caffeine): a ceramic cup with visible espresso crema on a two-tier saucer and three sine-wave
 * steam lines (echoing the wavy indicator). [avoid] lays the steam flat and strikes the cup through.
 */
@Composable
fun LittleAndOftenArt(
    modifier: Modifier = Modifier,
    avoid: Boolean = false,
    progress: Float = 1f,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    val copper = ClockblockTheme.adviceColors[AdviceType.Caffeine]
    val cupShape = remember {
        RoundedPolygon(
            vertices = floatArrayOf(-1f, -0.75f, 1f, -0.75f, 0.68f, 0.75f, -0.68f, 0.75f),
            rounding = CornerRounding(0.28f, smoothing = 0.4f),
        )
    }
    ArtCanvas(modifier, contentDescription, animated && !avoid, ambientPeriodMillis = ClockblockMotion.SteamMillis) { phase ->
        // Two-tier saucer.
        val saucer = oval(14f, 92f, 84f, 14f)
        paper(saucer, colors.surfaceHighest)
        fill(oval(28f, 95f, 56f, 7f), colors.secondaryContainer)
        ink(saucer, width = 2f)

        // Sculpted ear handle.
        val handle = minus(roundRect(72f, 61f, 24f, 24f, 12f), roundRect(77f, 66f, 14f, 14f, 7f))
        paper(handle, colors.primaryContainer)
        ink(handle, width = 2f)

        // Cup body with warm copper band and highlight.
        val cupBody = polygon(cupShape, 56f, 75f, 56f)
        paper(cupBody, colors.primaryContainer)
        fill(intersect(rect(24f, 82f, 64f, 6.5f), cupBody), copper.container)
        line(36f, 64f, 40f, 86f, width = 2.8f, color = colors.highlight)
        ink(cupBody, width = 2f)

        // Ceramic rim + visible dark-roast espresso crema at the top of the cup.
        val rim = oval(29f, 50f, 54f, 13f)
        fill(rim, colors.surfaceHighest)
        ink(rim, width = 2f)
        val coffee = oval(33f, 52.5f, 46f, 8.5f)
        fill(coffee, copper.color)
        ink(path { arcTo(56f, 57f, 14f, 200f, 110f, forceMoveTo = true) }, width = 1.8f, color = colors.highlight)

        // Steam curls.
        for (i in 0 until 3) {
            val x0 = 44f + i * 12f
            val steam = path {
                var yy = 46f
                moveTo(x0, yy)
                while (yy > 16f) {
                    yy -= 1f
                    val amp = if (avoid) 0f else 3.2f * progress
                    lineTo(x0 + amp * sin((yy / 15f + phase + i * 0.3f) * 2f * PI.toFloat()), yy)
                }
            }
            ink(steam, width = 2f, color = colors.ink.copy(alpha = if (avoid) 0.4f else 0.8f))
        }

        if (avoid) {
            val strike = rotated(roundRect(16f, 58f, 88f, 6.5f, 3.25f), -32f, 60f, 61f)
            val gap = rotated(roundRect(13f, 54.5f, 94f, 13.5f, 6.75f), -32f, 60f, 61f)
            fill(gap, colors.surface)
            fill(strike, colors.ink.copy(alpha = 1f))
        }
    }
}

/** **Power nap**: a quilted hammock slung between two finial posts, a warm sun half-set behind; the hammock sways ±3°. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PowerNapArt(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    ArtCanvas(modifier, contentDescription, animated, ambientPeriodMillis = ClockblockMotion.SwayMillis, restPhase = 0f) { phase ->
        // Soft sunset dome & layered setting sun behind the ground.
        val aboveGround = rect(0f, 0f, 120f, 86f)
        fill(intersect(circle(60f, 84f, 44f), aboveGround), colors.tertiaryContainer.copy(alpha = 0.40f))
        fill(intersect(circle(60f, 86f, 33f), aboveGround), colors.tertiary.copy(alpha = 0.32f))
        fill(intersect(circle(60f, 86f, 25f), aboveGround), colors.tertiary)

        // Ground plinth.
        val ground = roundRect(6f, 86f, 108f, 14f, 7f)
        paper(ground, colors.secondaryContainer)
        ink(ground, width = 2f)
        line(16f, 89f, 104f, 89f, width = 1.6f, color = colors.highlight)

        // Sturdy posts with finial caps.
        for (px in floatArrayOf(18f, 102f)) {
            val post = roundRect(px - 3f, 36f, 6f, 54f, 3f)
            paper(post, colors.inverse, shadow = false)
            ink(post, width = 1.8f)
            dot(px, 34f, 3.8f, colors.tertiary)
            ink(circle(px, 34f, 3.8f), width = 1.6f)
        }

        val sway = 3f * sin(phase * 2f * PI.toFloat()) * progress
        val ropes = path {
            moveTo(18f, 42f); lineTo(31f, 56f)
            moveTo(29f, 53f); lineTo(34f, 59f)
            moveTo(102f, 42f); lineTo(89f, 56f)
            moveTo(91f, 53f); lineTo(86f, 59f)
        }
        ink(rotated(ropes, sway, 60f, 40f), width = 1.8f)

        // Bolster pillow at the head end.
        val pillow = rotated(roundRect(33f, 51f, 20f, 11f, 5.5f), 20f + sway, 43f, 57f)
        fill(pillow, colors.surfaceHighest)
        ink(pillow, width = 1.8f)

        // Two-tone hammock body (inner lining + outer sling + stitched fold highlight).
        val innerLining = path {
            moveTo(31f, 56f)
            quadTo(60f, 73f, 89f, 56f)
            quadTo(60f, 86f, 31f, 56f)
            close()
        }
        fill(rotated(innerLining, sway, 60f, 40f), colors.secondaryContainer)

        val sling = path {
            moveTo(31f, 56f)
            quadTo(60f, 99f, 89f, 56f)
            quadTo(60f, 71f, 31f, 56f)
            close()
        }
        fill(rotated(sling, sway, 60f, 40f), colors.primaryContainer)
        ink(rotated(sling, sway, 60f, 40f), width = 2f)
        ink(rotated(path { moveTo(42f, 67f); quadTo(60f, 85f, 78f, 67f) }, sway, 60f, 40f), width = 1.8f, color = colors.highlight)

        // Calm resting z's above the hammock.
        serifText("z", 54f, 33f, 11f, colors.ink, alpha = 0.72f * progress)
        serifText("z", 65f, 22f, 14f, colors.ink, alpha = 0.90f * progress)
    }
}

/** **Running low** (peak fatigue): a gauge-marked battery with a dual-wave sloshing fill in front of a coral burst. [progress] = charge. */
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
        // Layered burst backdrop.
        fill(polygon(MaterialShapes.SoftBurst, 60f, 62f, 106f), coral.container)
        fill(polygon(MaterialShapes.Cookie12Sided, 60f, 62f, 86f, rotation = 15f), coral.color.copy(alpha = 0.14f))

        // Metallic terminal cap.
        val cap = roundRect(49f, 20f, 22f, 11f, 4f)
        paper(cap, colors.ink.copy(alpha = 1f), shadow = false)
        line(53f, 23.5f, 67f, 23.5f, width = 2f, color = colors.highlight)

        // Battery casing & recessed inner chamber.
        val body = roundRect(34f, 28f, 52f, 76f, 12f)
        paper(body, colors.surface)
        val inner = roundRect(40f, 34f, 40f, 64f, 7f)
        fill(inner, colors.surfaceHighest.copy(alpha = 0.55f))

        // Instrument graduation ticks along the right inner wall.
        for (gy in intArrayOf(48, 64, 80)) {
            line(73f, gy.toFloat(), 77.5f, gy.toFloat(), width = 1.6f, color = colors.ink.copy(alpha = 0.28f))
        }

        // Dual-wave sloshing charge fill.
        val level = 98f - 62f * progress.coerceIn(0f, 1f)
        val tilt = 2.5f * sin(phase * 2f * PI.toFloat())
        val backWave = path {
            moveTo(40f, 100f)
            var xx = 40f
            lineTo(xx, level)
            while (xx < 80f) {
                xx += 1f
                val wave = 2.0f * sin(((xx - 40f) / 18f - phase + 0.35f) * 2f * PI.toFloat())
                lineTo(xx, level - 2f + wave - tilt * (xx - 60f) / 24f)
            }
            lineTo(80f, 100f)
            close()
        }
        fill(intersect(backWave, inner), coral.color.copy(alpha = 0.38f))

        val frontWave = path {
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
        fill(intersect(frontWave, inner), coral.color)

        // Sculpted lightning bolt in the upper chamber + glass highlight.
        val bolt = path {
            moveTo(63f, 42f)
            lineTo(52f, 57f)
            lineTo(60f, 57f)
            lineTo(56f, 71f)
            lineTo(68f, 55f)
            lineTo(60f, 55f)
            close()
        }
        fill(bolt, coral.color.copy(alpha = 0.22f))
        ink(bolt, width = 2f, color = colors.ink.copy(alpha = 0.62f))
        line(38f, 38f, 38f, 72f, width = 2.2f, color = colors.highlight)
        ink(body, width = 2.5f)
    }
}

