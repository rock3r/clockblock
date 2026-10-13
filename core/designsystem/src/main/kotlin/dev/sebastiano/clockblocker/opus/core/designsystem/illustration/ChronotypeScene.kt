package dev.sebastiano.clockblocker.opus.core.designsystem.illustration

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toPath
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyGradient
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyPalette
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyPhase
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Where a chronotype's day peaks: the sky's hour, the sun/moon's place along its path (0 = rising, 1 = set), and moon or sun. */
@Immutable
data class SkyPose(val hour: Float, val position: Float, val evening: Boolean)

/** The [SkyPose] each chronotype's sky shows. */
fun Chronotype.skyPose(): SkyPose = when (this) {
    Chronotype.DefiniteMorning -> SkyPose(6.3f, 0.20f, evening = false)
    Chronotype.ModerateMorning -> SkyPose(8.5f, 0.34f, evening = false)
    Chronotype.Intermediate -> SkyPose(13f, 0.50f, evening = false)
    Chronotype.ModerateEvening -> SkyPose(19.3f, 0.70f, evening = true)
    Chronotype.DefiniteEvening -> SkyPose(23.5f, 0.82f, evening = true)
}

/**
 * A miniature sky landscape for a chronotype (the picker cards, the Settings You card): the sky of the hour that
 * person's day peaks, glow, clouds or stars, a dashed path with the sun or crescent moon on it, and layered hills.
 */
@Composable
fun ChronotypeSky(chronotype: Chronotype, modifier: Modifier = Modifier, cornerRadius: Dp = 14.dp) {
    val pose = chronotype.skyPose()
    ChronotypeScene(
        hour = { pose.hour },
        position = { pose.position },
        moonFraction = { if (pose.evening) 1f else 0f },
        modifier = modifier,
        cornerRadius = cornerRadius,
    )
}

/**
 * The chronotype landscape with every moving part as a lambda read in the draw phase only (so an animation never
 * recomposes): [hour] picks the sky, [position] places the sun or moon along its path, [moonFraction] cross-fades sun
 * (0) and moon (1), [entrance] (0 → 1) rises it from the horizon and fades in the details, [sunRotation] turns the
 * sun's rays (0 → 1 = 45°) and [twinkle] (0 → 1) breathes the stars. The geometry that only depends on the size is
 * built once per size.
 *
 * Colours: the sky and its lights (sun core, moon, stars, dawn and dusk glows, a lit window) are the fixed sky family
 * of design.md §2.4 (gradients only for skies; [SkyPalette] already blends them toward primary under dynamic colour
 * and dims them in Night-safe). The hills are taken from the same palette (its dusk and night skies, deepened), so they
 * follow both too; the sun's corona uses the art tertiary role.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChronotypeScene(
    chronotype: Chronotype? = null,
    hour: () -> Float,
    position: () -> Float,
    moonFraction: () -> Float,
    modifier: Modifier = Modifier,
    entrance: () -> Float = { 1f },
    sunRotation: () -> Float = { 0f },
    twinkle: () -> Float = { 0.5f },
    cornerRadius: Dp = 14.dp,
) {
    val sky = ClockblockTheme.sky
    val tertiary = ClockblockTheme.artColors.tertiary
    val shapes = remember {
        val sun = MaterialShapes.VerySunny.toPath().asComposePath()
        val star = MaterialShapes.PuffyDiamond.toPath().asComposePath()
        SceneShapes(sun, sun.getBounds(), star, star.getBounds())
    }
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .drawWithCache {
                val geometry = SceneGeometry(size, detailed = size.width >= 88.dp.toPx())
                val hills = HillColors.from(sky)
                onDrawBehind {
                    val h = hour()
                    drawChronotypeScene(
                        chronotype = chronotype,
                        geometry = geometry,
                        shapes = shapes,
                        gradient = sky.gradientAt(((h % 24f) + 24f) % 24f),
                        hills = hills,
                        tertiary = tertiary,
                        position = position(),
                        moonFraction = moonFraction(),
                        entrance = entrance(),
                        sunRotation = sunRotation(),
                        twinkle = twinkle(),
                    )
                }
            },
    )
}

/** The sun and star outlines and their bounds (shared, never mutated). */
private class SceneShapes(val sun: Path, val sunBounds: Rect, val star: Path, val starBounds: Rect)

/** Hill tones from the sky palette: deepened dusk for day hills, deepened night for night hills. */
private class HillColors(val day: Color, val night: Color) {
    companion object {
        fun from(sky: SkyPalette) = HillColors(
            day = lerp(sky[SkyPhase.Dusk].bottom, Color.Black, 0.12f),
            night = lerp(sky[SkyPhase.Night].top, Color.Black, 0.10f),
        )
    }
}

/** Everything that depends only on the scene's size, built once per size. */
private class SceneGeometry(size: Size, val detailed: Boolean) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.76f

    /** The far ridge's crest; filled down to the bottom for the hill, stroked alone for its rim light. */
    val ridgeCrest = Path().apply {
        moveTo(0f, h * 0.77f)
        cubicTo(w * 0.20f, h * 0.69f, w * 0.42f, h * 0.80f, w * 0.65f, h * 0.73f)
        cubicTo(w * 0.82f, h * 0.68f, w * 0.93f, h * 0.74f, w, h * 0.76f)
    }
    val backHill = Path().apply {
        addPath(ridgeCrest)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    val midHill = Path().apply {
        moveTo(0f, h * 0.84f)
        cubicTo(w * 0.28f, h * 0.74f, w * 0.58f, h * 0.85f, w, h * 0.79f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    val frontHill = Path().apply {
        moveTo(0f, h * 0.89f)
        cubicTo(w * 0.36f, h * 0.94f, w * 0.68f, h * 0.83f, w, h * 0.90f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    val arcRect = Rect(w * 0.13f, h * 0.21f, w * 0.87f, horizon * 2f - h * 0.21f)
    val fullArc = Path().apply { arcTo(arcRect, 180f, 180f, true) }
    val morningCloud = cloudPuff(w * 0.23f, h * 0.29f, w * 0.18f, h * 0.055f)
    val middayCloud = cloudPuff(w * 0.77f, h * 0.23f, w * 0.20f, h * 0.060f)
}

private fun DrawScope.drawChronotypeScene(
    chronotype: Chronotype?,
    geometry: SceneGeometry,
    shapes: SceneShapes,
    gradient: SkyGradient,
    hills: HillColors,
    tertiary: Color,
    position: Float,
    moonFraction: Float,
    entrance: Float,
    sunRotation: Float,
    twinkle: Float,
) {
    val m = moonFraction.coerceIn(0f, 1f)
    val e = entrance.coerceIn(0f, 1f)

    var eSky = e
    var eHills = e
    var eSunMoon = e
    var eStars = e
    var eClouds = e
    var eDetails = e
    var eSunPath = e

    if (chronotype != null) {
        when (chronotype) {
            Chronotype.DefiniteMorning -> {
                eSky = (e * 1.5f).coerceIn(0f, 1f)
                eHills = ((e - 0.2f) * 1.5f).coerceIn(0f, 1f)
                eSunMoon = ((e - 0.4f) * 2f).coerceIn(0f, 1f)
                eClouds = ((e - 0.3f) * 1.5f).coerceIn(0f, 1f)
                eSunPath = ((e - 0.1f) * 1.5f).coerceIn(0f, 1f)
                eDetails = ((e - 0.6f) * 2f).coerceIn(0f, 1f)
                eStars = e
            }
            Chronotype.ModerateMorning -> {
                eSky = (e * 1.4f).coerceIn(0f, 1f)
                eHills = ((e - 0.1f) * 1.5f).coerceIn(0f, 1f)
                eSunPath = ((e - 0.2f) * 1.5f).coerceIn(0f, 1f)
                eSunMoon = ((e - 0.3f) * 2f).coerceIn(0f, 1f)
                eClouds = ((e - 0.4f) * 1.5f).coerceIn(0f, 1f)
                eDetails = ((e - 0.5f) * 2f).coerceIn(0f, 1f)
                eStars = e
            }
            Chronotype.Intermediate -> {
                eSky = (e * 1.3f).coerceIn(0f, 1f)
                eHills = ((e - 0.1f) * 1.5f).coerceIn(0f, 1f)
                eSunPath = ((e - 0.2f) * 1.5f).coerceIn(0f, 1f)
                eSunMoon = ((e - 0.3f) * 2f).coerceIn(0f, 1f)
                eClouds = ((e - 0.3f) * 1.5f).coerceIn(0f, 1f)
                eDetails = ((e - 0.4f) * 2f).coerceIn(0f, 1f)
                eStars = e
            }
            Chronotype.ModerateEvening -> {
                eSky = (e * 1.5f).coerceIn(0f, 1f)
                eHills = ((e - 0.2f) * 1.5f).coerceIn(0f, 1f)
                eSunMoon = ((e - 0.1f) * 1.5f).coerceIn(0f, 1f)
                eSunPath = ((e - 0.3f) * 1.5f).coerceIn(0f, 1f)
                eClouds = ((e - 0.4f) * 1.5f).coerceIn(0f, 1f)
                eStars = ((e - 0.5f) * 2f).coerceIn(0f, 1f)
                eDetails = ((e - 0.6f) * 2f).coerceIn(0f, 1f)
            }
            Chronotype.DefiniteEvening -> {
                eSky = (e * 1.5f).coerceIn(0f, 1f)
                eHills = ((e - 0.2f) * 1.5f).coerceIn(0f, 1f)
                eSunMoon = ((e - 0.1f) * 1.5f).coerceIn(0f, 1f)
                eSunPath = ((e - 0.3f) * 1.5f).coerceIn(0f, 1f)
                eStars = ((e - 0.4f) * 2f).coerceIn(0f, 1f)
                eClouds = ((e - 0.5f) * 2f).coerceIn(0f, 1f)
                eDetails = ((e - 0.7f) * 3f).coerceIn(0f, 1f)
            }
        }
    }
        val light = SceneLight(position, m)
    val horizonSky = drawSky(gradient, light)
    // We use eSky for sky gradient logic if any, but drawSky doesnt take e. We can just let it fade via alpha later if we want.
    val celestial = celestialPoint(geometry, position * eSunMoon)
    drawHorizonGlow(geometry, light, celestial, eSky)
    if (m > 0f) drawStars(geometry, shapes, light, twinkle, eStars)
    if (light.day > 0.01f) drawClouds(geometry, light, position, eClouds)
    drawSunPath(geometry, position * eSunPath, eSunPath)
    drawSunAndMoon(geometry, shapes, light, celestial, gradient, tertiary, sunRotation, eSunMoon)
    drawHills(geometry, horizonSky, hills, m, eHills)
    if (geometry.detailed) drawDetails(geometry, light, hills, position, eDetails)
}

/** How much of each time of day the scene shows: sun (day) or moon ([m]), and the dawn, midday, dusk and deep-night weights. */
private class SceneLight(position: Float, val m: Float) {
    val day = 1f - m
    val dawn = ((0.30f - position) / 0.12f).coerceIn(0f, 1f) * day
    val midday = (1f - abs(position - 0.50f) / 0.18f).coerceIn(0f, 1f) * day
    val dusk = (1f - abs(position - 0.70f) / 0.10f).coerceIn(0f, 1f) * m
    val deepNight = ((position - 0.74f) / 0.08f).coerceIn(0f, 1f) * m
}

/** The multi-stop sky with a warm dawn or dusk horizon; returns the horizon colour (the hills blend from it). */
private fun DrawScope.drawSky(gradient: SkyGradient, light: SceneLight): Color {
    val midSky = lerp(gradient.top, gradient.bottom, 0.55f)
    val horizonSky = when {
        light.dawn > 0f -> lerp(gradient.bottom, DawnHorizonGlow, light.dawn * 0.45f)
        light.dusk > 0f -> lerp(gradient.bottom, DuskHorizonGlow, light.dusk * 0.50f)
        else -> gradient.bottom
    }
    drawRect(Brush.verticalGradient(0.0f to gradient.top, 0.52f to midSky, 0.82f to horizonSky, 1.0f to gradient.bottom))
    return horizonSky
}

/** The sun's or moon's centre on its path for a travelled share [pos], and its radius. */
private class Celestial(val p: Offset, val r: Float)

private fun celestialPoint(g: SceneGeometry, pos: Float): Celestial {
    val angle = PI * (1.0 - pos)
    val rx = g.w * 0.37f
    val ry = g.horizon - g.h * 0.21f
    val p = Offset(g.w / 2f + (rx * cos(angle)).toFloat(), g.horizon - (ry * sin(angle)).toFloat())
    return Celestial(p, g.w * if (g.detailed) 0.115f else 0.135f)
}

private fun DrawScope.drawHorizonGlow(g: SceneGeometry, light: SceneLight, c: Celestial, e: Float) {
    if (light.dawn + light.dusk <= 0.01f) return
    val glowColor = if (light.dusk > light.dawn) DuskHorizonGlow else DawnHorizonGlow
    val glowAlpha = maxOf(light.dawn, light.dusk) * 0.42f * e
    val centre = Offset(c.p.x, g.horizon)
    drawCircle(
        Brush.radialGradient(listOf(glowColor.copy(alpha = glowAlpha), glowColor.copy(alpha = 0f)), center = centre, radius = g.w * 0.68f),
        radius = g.w * 0.68f,
        center = centre,
    )
}

/** Stars, the Evening Star and (on the big reveal) a faint constellation, for evening and night types. */
private fun DrawScope.drawStars(g: SceneGeometry, shapes: SceneShapes, light: SceneLight, twinkle: Float, e: Float) {
    val (w, h) = g.w to g.h
    val m = light.m
    if (g.detailed && light.deepNight > 0.1f) {
        val cAlpha = 0.26f * light.deepNight * e
        for ((a, b) in ConstellationEdges) {
            val (x0, y0) = Stars[a]
            val (x1, y1) = Stars[b]
            drawLine(StarColor.copy(alpha = cAlpha), Offset(x0 * w, y0 * h), Offset(x1 * w, y1 * h), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
        }
    }
    Stars.forEachIndexed { i, (sx, sy, sr) ->
        // Dusk shows the brightest early stars; deep night brings out the full starfield.
        val visibility = if (i < 4) 1f else light.deepNight
        if (visibility > 0.01f) {
            // A continuous breath (the twinkle loop runs 0 → 1 → 0): no jump where a phase would wrap.
            val alpha = (0.75f + 0.25f * sin((twinkle + i * 0.27f) * 2f * PI.toFloat())) * m * visibility * e
            drawCircle(StarColor.copy(alpha = alpha), sr * w * if (g.detailed) 1f else 1.25f, Offset(sx * w, sy * h))
        }
    }
    val bounds = shapes.starBounds
    DiamondStars.forEachIndexed { i, (dx, dy, ds) ->
        val visibility = if (i == 0) 1f else light.deepNight
        if (visibility > 0.01f) {
            val pulse = 0.82f + 0.18f * sin((twinkle + i * 0.4f) * 2f * PI.toFloat())
            val starSize = ds * w * pulse * (if (g.detailed) 1f else 1.25f) * e
            val s = starSize / maxOf(bounds.width, bounds.height)
            drawCircle(StarColor.copy(alpha = 0.22f * m * visibility * e), radius = starSize * 0.95f, center = Offset(dx * w, dy * h))
            withTransform({
                translate(dx * w - bounds.center.x * s, dy * h - bounds.center.y * s)
                scale(s, s, pivot = Offset.Zero)
            }) { drawPath(shapes.star, StarColor.copy(alpha = 0.92f * m * visibility * e)) }
        }
    }
}

/** Soft daytime clouds: a morning wisp, and a second cumulus from late morning. */
private fun DrawScope.drawClouds(g: SceneGeometry, light: SceneLight, position: Float, e: Float) {
    val cloudAlpha = (0.28f + 0.24f * light.midday) * light.day * e
    drawPath(g.morningCloud, Color.White.copy(alpha = cloudAlpha))
    if (light.midday > 0.05f || position > 0.28f) drawPath(g.middayCloud, Color.White.copy(alpha = cloudAlpha * 0.9f))
}

/** The dashed full path and the brighter travelled part up to the sun or moon. */
private fun DrawScope.drawSunPath(g: SceneGeometry, pos: Float, e: Float) {
    val dashUnit = if (g.detailed) 4.dp.toPx() else 2.6.dp.toPx()
    val strokeW = if (g.detailed) 1.5.dp.toPx() else 1.1.dp.toPx()
    drawPath(
        g.fullArc,
        Color.White.copy(alpha = 0.42f),
        style = Stroke(strokeW, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashUnit, dashUnit))),
    )
    if (pos > 0.02f) {
        val activeArc = Path().apply { arcTo(g.arcRect, 180f, (180f * pos).coerceIn(1f, 180f), true) }
        drawPath(activeArc, Color.White.copy(alpha = 0.75f * e), style = Stroke(strokeW * 1.15f, cap = StrokeCap.Round))
    }
}

/** The sun and the moon, clipped just below the far ridge so they rise out of the landscape. */
private fun DrawScope.drawSunAndMoon(
    g: SceneGeometry,
    shapes: SceneShapes,
    light: SceneLight,
    c: Celestial,
    gradient: SkyGradient,
    tertiary: Color,
    sunRotation: Float,
    e: Float,
) {
    val (p, r) = c.p to c.r
    val m = light.m
    withTransform({ clipRect(bottom = g.horizon + g.h * 0.04f) }) {
        if (m < 1f) {
            val sunAlpha = 1f - m
            drawCircle(
                Brush.radialGradient(listOf(SunCoreColor.copy(alpha = 0.42f * sunAlpha * e), tertiary.copy(alpha = 0f)), center = p, radius = r * 2.5f),
                radius = r * 2.5f,
                center = p,
            )
            // Dawn rays for the Early bird.
            if (light.dawn > 0.05f && g.detailed) {
                for (ray in -2..2) {
                    val rayAngle = (-55f + ray * 22f) * PI.toFloat() / 180f
                    val r0 = r * 1.35f
                    val r1 = r * (2.2f + (2 - abs(ray)) * 0.25f)
                    drawLine(
                        SunCoreColor.copy(alpha = 0.34f * light.dawn * sunAlpha * e),
                        Offset(p.x + r0 * cos(rayAngle), p.y + r0 * sin(rayAngle)),
                        Offset(p.x + r1 * cos(rayAngle), p.y + r1 * sin(rayAngle)),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            val bounds = shapes.sunBounds
            val s = 2.45f * r / maxOf(bounds.width, bounds.height)
            withTransform({
                translate(p.x - bounds.center.x * s, p.y - bounds.center.y * s)
                scale(s, s, pivot = Offset.Zero)
                rotate(sunRotation * 45f, pivot = bounds.center)
            }) { drawPath(shapes.sun, tertiary.copy(alpha = sunAlpha)) }
            drawCircle(SunCoreColor.copy(alpha = 0.88f * sunAlpha), radius = r * 0.74f, center = p)
            drawCircle(Color.White.copy(alpha = 0.45f * sunAlpha), radius = r * 0.26f, center = Offset(p.x - r * 0.22f, p.y - r * 0.22f))
        }
        if (m > 0f) {
            drawCircle(
                Brush.radialGradient(listOf(MoonColor.copy(alpha = 0.32f * m * e), MoonColor.copy(alpha = 0f)), center = p, radius = r * 2.3f),
                radius = r * 2.3f,
                center = p,
            )
            // Earthshine on the dark limb (it also hides the path behind the moon).
            drawCircle(lerp(gradient.top, gradient.bottom, 0.55f).copy(alpha = m), radius = r * 0.96f, center = p)
            drawCircle(MoonColor.copy(alpha = 0.12f * m), radius = r * 0.96f, center = p)
            val disc = Path().apply { addOval(Rect(p, r)) }
            val bite = Path().apply { addOval(Rect(Offset(p.x + r * 0.52f, p.y - r * 0.42f), r * 0.84f)) }
            drawPath(Path.combine(PathOperation.Difference, disc, bite), MoonColor.copy(alpha = m))
            if (g.detailed) {
                drawCircle(tertiary.copy(alpha = 0.16f * m), radius = r * 0.18f, center = Offset(p.x - r * 0.36f, p.y + r * 0.14f))
                drawCircle(tertiary.copy(alpha = 0.13f * m), radius = r * 0.11f, center = Offset(p.x - r * 0.18f, p.y + r * 0.42f))
            }
        }
    }
}

/** Three layers of hills, each blended from the horizon toward the hill tone, and a rim light on the far ridge. */
private fun DrawScope.drawHills(g: SceneGeometry, horizonSky: Color, hills: HillColors, m: Float, e: Float) {
    val hillBase = lerp(hills.day, hills.night, m)
    drawPath(g.backHill, lerp(horizonSky, hillBase, 0.42f))
    val rimColor = if (m > 0.5f) MoonColor else SunCoreColor
    drawPath(g.ridgeCrest, rimColor.copy(alpha = 0.28f * e), style = Stroke(if (g.detailed) 1.3.dp.toPx() else 0.9.dp.toPx(), cap = StrokeCap.Round))
    drawPath(g.midHill, lerp(horizonSky, hillBase, 0.66f))
    drawPath(g.frontHill, lerp(horizonSky, hillBase, 0.86f))
}

/** The reveal's fine details: morning birds, or a lit window on the hill at night, and a shimmer near the foot. */
private fun DrawScope.drawDetails(g: SceneGeometry, light: SceneLight, hills: HillColors, position: Float, e: Float) {
    val (w, h) = g.w to g.h
    if (light.day > 0.2f && position < 0.42f) {
        val birdAlpha = 0.55f * light.day * e
        drawBird(w * 0.66f, h * 0.60f, w * 0.032f, hills.day.copy(alpha = birdAlpha))
        drawBird(w * 0.73f, h * 0.56f, w * 0.024f, hills.day.copy(alpha = birdAlpha * 0.8f))
    }
    if (light.m > 0.2f) {
        val window = Offset(w * 0.27f, h * 0.83f)
        drawCircle(WindowGlowColor.copy(alpha = 0.32f * light.m * e), radius = w * 0.028f, center = window)
        drawCircle(WindowGlowColor.copy(alpha = 0.92f * light.m * e), radius = w * 0.011f, center = window)
    }
    val shimmerColor = (if (light.m > 0.5f) MoonColor else Color.White).copy(alpha = 0.22f * e)
    drawLine(shimmerColor, Offset(w * 0.34f, h * 0.935f), Offset(w * 0.66f, h * 0.935f), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
    drawLine(shimmerColor, Offset(w * 0.42f, h * 0.962f), Offset(w * 0.58f, h * 0.962f), strokeWidth = 1.0.dp.toPx(), cap = StrokeCap.Round)
}

private fun cloudPuff(cx: Float, cy: Float, width: Float, height: Float) = Path().apply {
    val l = cx - width / 2f
    val r = cx + width / 2f
    val b = cy + height / 2f
    moveTo(l + height * 0.5f, b)
    lineTo(r - height * 0.5f, b)
    arcTo(Rect(r - height, b - height, r, b), 90f, -180f, false)
    arcTo(Rect(cx - width * 0.18f, cy - height * 0.95f, cx + width * 0.28f, b - height * 0.1f), 0f, -180f, false)
    arcTo(Rect(cx - width * 0.36f, cy - height * 0.65f, cx - width * 0.02f, b - height * 0.1f), 0f, -180f, false)
    arcTo(Rect(l, b - height, l + height, b), 270f, -180f, false)
    close()
}

private fun DrawScope.drawBird(cx: Float, cy: Float, span: Float, color: Color) {
    val bird = Path().apply {
        moveTo(cx - span, cy + span * 0.15f)
        quadraticTo(cx - span * 0.45f, cy - span * 0.45f, cx, cy + span * 0.08f)
        quadraticTo(cx + span * 0.45f, cy - span * 0.45f, cx + span, cy + span * 0.15f)
    }
    drawPath(bird, color, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
}

/** Star positions (fractions of the art) and radii; indices 0..3 form a delicate constellation in the upper-left sky. */
private val Stars = listOf(
    Triple(0.16f, 0.18f, 0.011f),
    Triple(0.27f, 0.12f, 0.010f),
    Triple(0.37f, 0.17f, 0.012f),
    Triple(0.47f, 0.10f, 0.009f),
    Triple(0.62f, 0.13f, 0.011f),
    Triple(0.75f, 0.09f, 0.009f),
    Triple(0.86f, 0.19f, 0.010f),
    Triple(0.22f, 0.31f, 0.008f),
    Triple(0.56f, 0.26f, 0.008f),
)

private val ConstellationEdges = listOf(0 to 1, 1 to 2, 2 to 3)

/** Four-point diamond stars: index 0 is the Evening Star (visible at dusk and night); 1..2 join in deep night. */
private val DiamondStars = listOf(
    Triple(0.24f, 0.22f, 0.052f),
    Triple(0.68f, 0.16f, 0.042f),
    Triple(0.42f, 0.30f, 0.036f),
)

// The sky's lights: fixed like the sky gradients they sit on (design.md §2.4: gradients only for skies).
private val StarColor = Color(0xFFFFF4D6)
private val MoonColor = Color(0xFFF5EDD6)
private val SunCoreColor = Color(0xFFFFF3C4)
private val DawnHorizonGlow = Color(0xFFFFB88C)
private val DuskHorizonGlow = Color(0xFFFF8E6E)
private val WindowGlowColor = Color(0xFFFFD175)
