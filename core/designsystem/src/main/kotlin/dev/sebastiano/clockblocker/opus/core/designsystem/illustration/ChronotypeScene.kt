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
import androidx.compose.ui.util.lerp
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
fun ChronotypeSky(
    chronotype: Chronotype,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    drawn: () -> Boolean = { true },
) {
    val pose = chronotype.skyPose()
    ChronotypeScene(
        hour = { pose.hour },
        position = { pose.position },
        moonFraction = { if (pose.evening) 1f else 0f },
        drawn = drawn,
        modifier = modifier,
        cornerRadius = cornerRadius,
    )
}

/**
 * The chronotype landscape with every moving part as a lambda read in the draw phase only (so an animation never
 * recomposes): [hour] picks the sky, [position] places the sun or moon along its path, [moonFraction] cross-fades sun
 * (0) and moon (1), [entrance] (0 → 1) assembles the landscape layer by layer, [sunRotation] turns the sun's rays
 * (0 → 1 = 45°) and [twinkle] (0 → 1) breathes the stars. [bodyScale] grows the sun or moon (1 at rest). The
 * geometry that only depends on the size is built once per size.
 *
 * With a [chronotype], the entrance is that chronotype's own (see [SceneEntrance]); without one, everything simply
 * fades and rises in together. Either way the scene at `entrance = 1` is exactly the scene at rest.
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
    drawn: () -> Boolean = { true },
    cornerRadius: Dp = 14.dp,
    bodyScale: () -> Float = { 1f },
) {
    val sky = ClockblockTheme.sky
    val tertiary = ClockblockTheme.artColors.tertiary
    val shapes = remember { DefaultSceneShapes }
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .drawWithCache {
                var geometry: SceneGeometry? = null
                val detailed = size.width >= 88.dp.toPx()
                val dashUnit = if (detailed) 4.dp.toPx() else 2.6.dp.toPx()
                val strokeW = if (detailed) 1.5.dp.toPx() else 1.1.dp.toPx()
                val ridgeW = if (detailed) 1.3.dp.toPx() else 0.9.dp.toPx()
                val birdW = 1.4.dp.toPx()
                val hills = HillColors.from(sky)
                onDrawBehind {
                    if (!drawn()) return@onDrawBehind
                    val g = geometry ?: SceneGeometry(size, detailed, dashUnit, strokeW, ridgeW, birdW).also { geometry = it }
                    val h = ((hour() % 24f) + 24f) % 24f
                    drawChronotypeScene(
                        chronotype = chronotype,
                        geometry = g,
                        shapes = shapes,
                        sky = sky,
                        hour = h,
                        hills = hills,
                        tertiary = tertiary,
                        position = position(),
                        moonFraction = moonFraction(),
                        entrance = entrance(),
                        sunRotation = sunRotation(),
                        twinkle = twinkle(),
                        bodyScale = bodyScale(),
                    )
                }
            },
    )
}

/** Pre-builds the shared sun and star outlines so the first [ChronotypeScene] composition pays nothing for them. */
fun warmChronotypeSceneShapes() {
    DefaultSceneShapes
}

/**
 * Draws every [ChronotypeScene] primitive (day and night, paths, gradients, circles, round-capped lines) into the
 * current [DrawScope] so the GPU compiles the scene's graphics pipelines ahead of the first reveal and picker sheet.
 */
fun DrawScope.drawChronotypeSceneWarmup(sky: SkyPalette, tertiary: Color) {
    val detailed = size.width >= 88.dp.toPx()
    val dashUnit = if (detailed) 4.dp.toPx() else 2.6.dp.toPx()
    val strokeW = if (detailed) 1.5.dp.toPx() else 1.1.dp.toPx()
    val ridgeW = if (detailed) 1.3.dp.toPx() else 0.9.dp.toPx()
    val birdW = 1.4.dp.toPx()
    val g = SceneGeometry(size, detailed, dashUnit, strokeW, ridgeW, birdW)
    val hills = HillColors.from(sky)
    drawChronotypeScene(
        chronotype = Chronotype.DefiniteMorning,
        geometry = g,
        shapes = DefaultSceneShapes,
        sky = sky,
        hour = 6.3f,
        hills = hills,
        tertiary = tertiary,
        position = 0.20f,
        moonFraction = 0f,
        entrance = 0.5f,
        sunRotation = 0.2f,
        twinkle = 0.5f,
    )
    drawChronotypeScene(
        chronotype = Chronotype.Intermediate,
        geometry = g,
        shapes = DefaultSceneShapes,
        sky = sky,
        hour = 13f,
        hills = hills,
        tertiary = tertiary,
        position = 0.50f,
        moonFraction = 0f,
        entrance = 0.7f,
        sunRotation = 0.2f,
        twinkle = 0.5f,
    )
    drawChronotypeScene(
        chronotype = Chronotype.DefiniteEvening,
        geometry = g,
        shapes = DefaultSceneShapes,
        sky = sky,
        hour = 23.5f,
        hills = hills,
        tertiary = tertiary,
        position = 0.82f,
        moonFraction = 1f,
        entrance = 0.7f,
        sunRotation = 0f,
        twinkle = 0.5f,
    )
}

/** The sun and star outlines and their bounds (shared, never mutated). */
private class SceneShapes(val sun: Path, val sunBounds: Rect, val star: Path, val starBounds: Rect)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val DefaultSceneShapes: SceneShapes by lazy(LazyThreadSafetyMode.PUBLICATION) {
    val sun = MaterialShapes.VerySunny.toPath().asComposePath()
    val star = MaterialShapes.PuffyDiamond.toPath().asComposePath()
    SceneShapes(sun, sun.getBounds(), star, star.getBounds())
}

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
private class SceneGeometry(
    size: Size,
    val detailed: Boolean,
    dashUnit: Float,
    strokeW: Float,
    ridgeW: Float,
    birdW: Float,
) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.76f
    val dashStroke = Stroke(strokeW, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashUnit, dashUnit)))
    val activeArcStroke = Stroke(strokeW * 1.15f, cap = StrokeCap.Round)
    val ridgeStroke = Stroke(ridgeW, cap = StrokeCap.Round)
    val birdStroke = Stroke(birdW, cap = StrokeCap.Round)

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

    private val activeArc = Path()
    private var lastArcSweep = Float.NaN
    val birdPath = Path()
    private val moonDisc = Path()
    private val moonBite = Path()
    private val moonCrescent = Path()
    private var lastMoonCenter = Offset.Unspecified
    private var lastMoonRadius = Float.NaN

    private var cachedHour = Float.NaN
    private var cachedGradient: SkyGradient? = null
    private var cachedBrushDawn = Float.NaN
    private var cachedBrushDusk = Float.NaN
    private var cachedBrushGradient: SkyGradient? = null
    private var cachedSkyBrush: Brush? = null
    private var cachedHorizonSky: Color = Color.Unspecified

    fun gradientAt(sky: SkyPalette, hour: Float): SkyGradient {
        if (hour == cachedHour) cachedGradient?.let { return it }
        return sky.gradientAt(hour).also {
            cachedHour = hour
            cachedGradient = it
        }
    }

    fun skyBrush(gradient: SkyGradient, light: SceneLight): Pair<Brush, Color> {
        val cached = cachedSkyBrush
        if (cached != null && gradient == cachedBrushGradient && light.dawn == cachedBrushDawn && light.dusk == cachedBrushDusk) {
            return cached to cachedHorizonSky
        }
        val midSky = lerp(gradient.top, gradient.bottom, 0.55f)
        val horizonSky = when {
            light.dawn > 0f -> lerp(gradient.bottom, DawnHorizonGlow, light.dawn * 0.45f)
            light.dusk > 0f -> lerp(gradient.bottom, DuskHorizonGlow, light.dusk * 0.50f)
            else -> gradient.bottom
        }
        val brush = Brush.verticalGradient(0.0f to gradient.top, 0.52f to midSky, 0.82f to horizonSky, 1.0f to gradient.bottom)
        cachedBrushGradient = gradient
        cachedBrushDawn = light.dawn
        cachedBrushDusk = light.dusk
        cachedSkyBrush = brush
        cachedHorizonSky = horizonSky
        return brush to horizonSky
    }

    fun activeArc(sweep: Float): Path {
        if (sweep != lastArcSweep) {
            activeArc.rewind()
            activeArc.arcTo(arcRect, 180f, sweep, true)
            lastArcSweep = sweep
        }
        return activeArc
    }

    fun moonCrescent(p: Offset, r: Float): Path {
        if (p != lastMoonCenter || r != lastMoonRadius) {
            moonDisc.rewind()
            moonDisc.addOval(Rect(p, r))
            moonBite.rewind()
            moonBite.addOval(Rect(Offset(p.x + r * 0.52f, p.y - r * 0.42f), r * 0.84f))
            moonCrescent.op(moonDisc, moonBite, PathOperation.Difference)
            lastMoonCenter = p
            lastMoonRadius = r
        }
        return moonCrescent
    }
}

private fun DrawScope.drawChronotypeScene(
    chronotype: Chronotype?,
    geometry: SceneGeometry,
    shapes: SceneShapes,
    sky: SkyPalette,
    hour: Float,
    hills: HillColors,
    tertiary: Color,
    position: Float,
    moonFraction: Float,
    entrance: Float,
    sunRotation: Float,
    twinkle: Float,
    bodyScale: Float = 1f,
) {
    val m = moonFraction.coerceIn(0f, 1f)
    val act = SceneEntrance.of(chronotype, entrance.coerceIn(0f, 1f))
    val light = SceneLight(position, m)
    val gradient = geometry.gradientAt(sky, hour)
    val horizonSky = drawSky(geometry, gradient, light)
    val celestial = celestialPoint(geometry, position * act.travel)
    drawHorizonGlow(geometry, light, celestial, act)
    if (m > 0f) drawStars(geometry, shapes, light, twinkle, act)
    if (light.day > 0.01f) drawClouds(geometry, light, position, act)
    drawSunPath(geometry, position * act.path, act.path)
    drawSunAndMoon(geometry, shapes, light, celestial, gradient, tertiary, sunRotation, act, bodyScale)
    drawHills(geometry, horizonSky, hills, m, act.hills)
    if (geometry.detailed) drawDetails(geometry, light, hills, position, act)
}

/**
 * Each layer's entrance progress for an overall entrance [e]; every value is 1 (offsets 0) at rest, which draws the
 * scene exactly as it rests. The hills rise in parallax and the dashed path draws for everyone; then each chronotype
 * has its own signature:
 * - **Early bird**: the sun clears the ridge, its dawn rays fan out, and two birds burst across the sky, wings beating.
 * - **Morning person**: the sun climbs a long way along its path while the clouds part to either side.
 * - **Somewhere in between**: the sun is already high, hidden behind two clouds that drift apart to reveal it; a
 *   shimmer runs across the water.
 * - **Evening person**: the dusk glow sinks to the horizon, the moon fades up, the first stars come out and a cottage
 *   window flickers alight.
 * - **Night owl**: the stars ignite one by one, the constellation draws itself in, and the moon blooms with light.
 */
internal class SceneEntrance(
    val hills: Float = 1f,
    val path: Float = 1f,
    /** The sun or moon's share of its way along the path. */
    val travel: Float = 1f,
    /** The sun or moon's corona, and the body's own opacity and size. */
    val corona: Float = 1f,
    val bodyAlpha: Float = 1f,
    val bodyScale: Float = 1f,
    /** The horizon glow, and how far above the horizon it still hangs (a share of the height). */
    val glow: Float = 1f,
    val glowLift: Float = 0f,
    /** The dawn rays' opacity and fan (0 = folded into one, 1 = spread). */
    val rays: Float = 1f,
    val raysFan: Float = 1f,
    val clouds: Float = 1f,
    /** Where each cloud still is, away from its resting place (shares of the width and height). */
    val morningCloud: Offset = Offset.Zero,
    val middayCloud: Offset = Offset.Zero,
    /** The birds' opacity, their way along the flight still to go (shares of the width and height), and a wingbeat. */
    val birds: Float = 1f,
    val birdsFlight: Offset = Offset.Zero,
    val wingbeat: Float = 0f,
    /** Every star's opacity, or one per star (lighting in sequence) when [starIgnite] is set. */
    val stars: Float = 1f,
    val starIgnite: ((Int) -> Float)? = null,
    /** How much of each constellation line is drawn, in order. */
    val constellation: (Int) -> Float = { 1f },
    /** The moon's bloom: an extra glow that swells and settles (0 at rest). */
    val moonBloom: Float = 0f,
    val window: Float = 1f,
    val shimmer: Float = 1f,
    /** A sparkle running across the shimmer (0 at rest). */
    val sparkle: Float = 0f,
) {
    companion object {
        fun of(chronotype: Chronotype?, e: Float): SceneEntrance {
            val hills = stage(e, 0f, 0.7f)
            val path = stage(e, 0.1f, 1f)
            return when (chronotype) {
                null -> SceneEntrance(
                    hills = hills, path = path, travel = e, corona = e, glow = e, rays = e, clouds = e,
                    morningCloud = Offset(-(1f - e) * CloudDrift, 0f), middayCloud = Offset((1f - e) * CloudDrift, 0f),
                    birds = e, birdsFlight = Offset(-(1f - e) * 0.06f, (1f - e) * 0.03f), stars = e, window = e, shimmer = e,
                )
                Chronotype.DefiniteMorning -> {
                    val sun = stage(e, 0.1f, 0.7f)
                    val fan = stage(e, 0.4f, 0.95f)
                    val flight = stage(e, 0.45f, 1f)
                    SceneEntrance(
                        hills = hills, path = path, travel = sun, corona = sun, glow = stage(e, 0f, 0.6f),
                        rays = fan, raysFan = fan, clouds = stage(e, 0.3f, 1f),
                        morningCloud = Offset(-(1f - e) * CloudDrift, 0f),
                        birds = stage(e, 0.45f, 0.6f), birdsFlight = Offset(-(1f - flight) * 0.8f, (1f - flight) * 0.14f),
                        wingbeat = (1f - flight) * sin(flight * PI.toFloat() * Wingbeats),
                        window = e, shimmer = stage(e, 0.5f, 1f),
                    )
                }
                Chronotype.ModerateMorning -> {
                    val parted = 1f - stage(e, 0.25f, 1f)
                    val sun = stage(e, 0.05f, 0.9f)
                    SceneEntrance(
                        hills = hills, path = path, travel = sun, corona = sun, glow = stage(e, 0f, 0.6f), rays = sun,
                        clouds = stage(e, 0.05f, 0.35f),
                        morningCloud = Offset(parted * 0.16f, 0f), middayCloud = Offset(-parted * 0.16f, 0f),
                        birds = stage(e, 0.6f, 1f), birdsFlight = Offset(-(1f - stage(e, 0.6f, 1f)) * 0.06f, 0f),
                        window = e, shimmer = stage(e, 0.5f, 1f),
                    )
                }
                Chronotype.Intermediate -> {
                    val apart = 1f - stage(e, 0.2f, 0.85f)
                    val revealed = stage(e, 0.25f, 0.85f)
                    val shimmer = stage(e, 0.55f, 1f)
                    SceneEntrance(
                        hills = hills, path = path, travel = 1f, corona = revealed,
                        bodyAlpha = revealed, bodyScale = lerp(0.8f, 1f, revealed),
                        // Gathered over the sun, the clouds are thicker; they thin to their usual veil as they part.
                        glow = stage(e, 0f, 0.6f), rays = revealed, clouds = stage(e, 0f, 0.15f) * (1f + apart * CloudGathered),
                        morningCloud = Offset(apart * 0.2f, -apart * 0.07f), middayCloud = Offset(-apart * 0.2f, -apart * 0.02f),
                        birds = e, window = e, shimmer = shimmer, sparkle = 4f * shimmer * (1f - shimmer),
                    )
                }
                Chronotype.ModerateEvening -> {
                    val sunk = stage(e, 0f, 0.7f)
                    val moon = stage(e, 0.2f, 0.8f)
                    SceneEntrance(
                        hills = hills, path = path, travel = lerp(0.8f, 1f, moon), corona = moon, bodyAlpha = moon,
                        glow = 1f, glowLift = (1f - sunk) * 0.3f, rays = e, clouds = e,
                        stars = stage(e, 0.45f, 0.85f), window = flicker(stage(e, 0.55f, 1f)), shimmer = stage(e, 0.6f, 1f),
                    )
                }
                Chronotype.DefiniteEvening -> {
                    val moon = stage(e, 0.1f, 0.7f)
                    SceneEntrance(
                        hills = hills, path = path, travel = 1f, corona = moon, bodyAlpha = moon, glow = stage(e, 0f, 0.6f),
                        rays = e, clouds = e,
                        starIgnite = { i -> stage(e, 0.15f + i * 0.055f, 0.35f + i * 0.055f) },
                        constellation = { k -> stage(e, 0.6f + k * 0.1f, 0.78f + k * 0.1f) },
                        moonBloom = 4f * moon * (1f - moon), window = stage(e, 0.75f, 1f), shimmer = stage(e, 0.6f, 1f),
                    )
                }
            }
        }

        /**
         * A layer's own 0 → 1 over its share [from]..[to] of the entrance. Exactly 1 at the end (a share ending at 1
         * divides a number by itself), so every layer lands on its resting place.
         */
        private fun stage(e: Float, from: Float, to: Float): Float = ((e - from) / (to - from)).coerceIn(0f, 1f)

        /** A window lighting with a catch: up to 70 %, a dip, then fully lit. */
        private fun flicker(p: Float): Float = when {
            p < 0.35f -> p / 0.35f * 0.7f
            p < 0.5f -> lerp(0.7f, 0.25f, (p - 0.35f) / 0.15f)
            else -> lerp(0.25f, 1f, (p - 0.5f) / 0.5f)
        }

        private const val CloudDrift = 0.08f
        private const val CloudGathered = 1.2f
        private const val Wingbeats = 6f
    }
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
private fun DrawScope.drawSky(g: SceneGeometry, gradient: SkyGradient, light: SceneLight): Color {
    val (brush, horizonSky) = g.skyBrush(gradient, light)
    drawRect(brush)
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

private fun DrawScope.drawHorizonGlow(g: SceneGeometry, light: SceneLight, c: Celestial, act: SceneEntrance) {
    if (light.dawn + light.dusk <= 0.01f) return
    val glowColor = if (light.dusk > light.dawn) DuskHorizonGlow else DawnHorizonGlow
    val glowAlpha = maxOf(light.dawn, light.dusk) * 0.42f * act.glow
    val centre = Offset(c.p.x, g.horizon - act.glowLift * g.h)
    val radius = g.w * 0.68f * (1f + act.glowLift)
    drawCircle(
        Brush.radialGradient(listOf(glowColor.copy(alpha = glowAlpha), glowColor.copy(alpha = 0f)), center = centre, radius = radius),
        radius = radius,
        center = centre,
    )
}

/** Stars, the Evening Star and (on the big reveal) a faint constellation, for evening and night types. */
private fun DrawScope.drawStars(g: SceneGeometry, shapes: SceneShapes, light: SceneLight, twinkle: Float, act: SceneEntrance) {
    val (w, h) = g.w to g.h
    val m = light.m
    if (g.detailed && light.deepNight > 0.1f) {
        val cAlpha = 0.26f * light.deepNight * act.stars
        ConstellationEdges.forEachIndexed { k, (a, b) ->
            val drawn = act.constellation(k)
            if (drawn > 0f) {
                val (x0, y0) = Stars[a]
                val (x1, y1) = Stars[b]
                val end = if (drawn >= 1f) Offset(x1 * w, y1 * h) else Offset(lerp(x0, x1, drawn) * w, lerp(y0, y1, drawn) * h)
                drawLine(StarColor.copy(alpha = cAlpha), Offset(x0 * w, y0 * h), end, strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
    Stars.forEachIndexed { i, (sx, sy, sr) ->
        // Dusk shows the brightest early stars; deep night brings out the full starfield.
        val visibility = if (i < 4) 1f else light.deepNight
        val lit = act.starIgnite?.invoke(i) ?: act.stars
        if (visibility > 0.01f && lit > 0f) {
            // A continuous breath (the twinkle loop runs 0 → 1 → 0): no jump where a phase would wrap.
            val alpha = (0.75f + 0.25f * sin((twinkle + i * 0.27f) * 2f * PI.toFloat())) * m * visibility * lit
            val flare = 1f + StarFlare * 4f * lit * (1f - lit)
            drawCircle(StarColor.copy(alpha = alpha), sr * w * (if (g.detailed) 1f else 1.25f) * flare, Offset(sx * w, sy * h))
        }
    }
    val bounds = shapes.starBounds
    DiamondStars.forEachIndexed { i, (dx, dy, ds) ->
        val visibility = if (i == 0) 1f else light.deepNight
        val lit = act.starIgnite?.invoke(Stars.size + i) ?: act.stars
        if (visibility > 0.01f && lit > 0f) {
            val pulse = 0.82f + 0.18f * sin((twinkle + i * 0.4f) * 2f * PI.toFloat())
            val starSize = ds * w * pulse * (if (g.detailed) 1f else 1.25f) * lit
            val s = starSize / maxOf(bounds.width, bounds.height)
            drawCircle(StarColor.copy(alpha = 0.22f * m * visibility * lit), radius = starSize * 0.95f, center = Offset(dx * w, dy * h))
            withTransform({
                translate(dx * w - bounds.center.x * s, dy * h - bounds.center.y * s)
                scale(s, s, pivot = Offset.Zero)
            }) { drawPath(shapes.star, StarColor.copy(alpha = 0.92f * m * visibility * lit)) }
        }
    }
}

/** Soft daytime clouds: a morning wisp, and a second cumulus from late morning. */
private fun DrawScope.drawClouds(g: SceneGeometry, light: SceneLight, position: Float, act: SceneEntrance) {
    val cloudAlpha = (0.28f + 0.24f * light.midday) * light.day * act.clouds
    withTransform({ translate(act.morningCloud.x * g.w, act.morningCloud.y * g.h) }) {
        drawPath(g.morningCloud, Color.White.copy(alpha = cloudAlpha))
    }
    if (light.midday > 0.05f || position > 0.28f) {
        withTransform({ translate(act.middayCloud.x * g.w, act.middayCloud.y * g.h) }) {
            drawPath(g.middayCloud, Color.White.copy(alpha = cloudAlpha * 0.9f))
        }
    }
}

/** The dashed full path and the brighter travelled part up to the sun or moon. */
private fun DrawScope.drawSunPath(g: SceneGeometry, pos: Float, e: Float) {
    drawPath(g.fullArc, Color.White.copy(alpha = 0.42f), style = g.dashStroke)
    if (pos > 0.02f) {
        val activeArc = g.activeArc((180f * pos).coerceIn(1f, 180f))
        drawPath(activeArc, Color.White.copy(alpha = 0.75f * e), style = g.activeArcStroke)
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
    act: SceneEntrance,
    bodyScale: Float = 1f,
) {
    val p = c.p
    val r = c.r * act.bodyScale * bodyScale
    val m = light.m
    withTransform({ clipRect(bottom = g.horizon + g.h * 0.04f) }) {
        if (m < 1f) {
            val sunAlpha = (1f - m) * act.bodyAlpha
            drawCircle(
                Brush.radialGradient(listOf(SunCoreColor.copy(alpha = 0.42f * sunAlpha * act.corona), tertiary.copy(alpha = 0f)), center = p, radius = r * 2.5f),
                radius = r * 2.5f,
                center = p,
            )
            // Dawn rays for the Early bird.
            if (light.dawn > 0.05f && g.detailed && act.rays > 0f) {
                for (ray in -2..2) {
                    val rayAngle = (-55f + ray * 22f * act.raysFan) * PI.toFloat() / 180f
                    val r0 = r * 1.35f
                    val r1 = r * (2.2f + (2 - abs(ray)) * 0.25f) * lerp(FoldedRayLength, 1f, act.raysFan)
                    drawLine(
                        SunCoreColor.copy(alpha = 0.34f * light.dawn * sunAlpha * act.rays),
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
            val moon = m * act.bodyAlpha
            val glowRadius = r * 2.3f * (1f + MoonBloomGrowth * act.moonBloom)
            drawCircle(
                Brush.radialGradient(
                    listOf(MoonColor.copy(alpha = 0.32f * m * act.corona * (1f + act.moonBloom)), MoonColor.copy(alpha = 0f)),
                    center = p,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = p,
            )
            // Earthshine on the dark limb (it also hides the path behind the moon).
            drawCircle(lerp(gradient.top, gradient.bottom, 0.55f).copy(alpha = moon), radius = r * 0.96f, center = p)
            drawCircle(MoonColor.copy(alpha = 0.12f * moon), radius = r * 0.96f, center = p)
            drawPath(g.moonCrescent(p, r), MoonColor.copy(alpha = moon))
            if (g.detailed) {
                drawCircle(tertiary.copy(alpha = 0.16f * moon), radius = r * 0.18f, center = Offset(p.x - r * 0.36f, p.y + r * 0.14f))
                drawCircle(tertiary.copy(alpha = 0.13f * moon), radius = r * 0.11f, center = Offset(p.x - r * 0.18f, p.y + r * 0.42f))
            }
        }
    }
}

/** Three layers of hills, each blended from the horizon toward the hill tone, and a rim light on the far ridge. */
private fun DrawScope.drawHills(g: SceneGeometry, horizonSky: Color, hills: HillColors, m: Float, e: Float) {
    val hillBase = lerp(hills.day, hills.night, m)
    val rimColor = if (m > 0.5f) MoonColor else SunCoreColor
    val eBack = (e / 0.7f).coerceIn(0f, 1f)
    val eMid = ((e - 0.15f) / 0.7f).coerceIn(0f, 1f)
    val eFront = ((e - 0.30f) / 0.7f).coerceIn(0f, 1f)
    withTransform({ translate(top = (1f - eBack) * g.h * 0.18f) }) {
        drawPath(g.backHill, lerp(horizonSky, hillBase, 0.42f))
        drawPath(g.ridgeCrest, rimColor.copy(alpha = 0.28f * e), style = g.ridgeStroke)
    }
    withTransform({ translate(top = (1f - eMid) * g.h * 0.14f) }) {
        drawPath(g.midHill, lerp(horizonSky, hillBase, 0.66f))
    }
    withTransform({ translate(top = (1f - eFront) * g.h * 0.10f) }) {
        drawPath(g.frontHill, lerp(horizonSky, hillBase, 0.86f))
    }
}

/** The reveal's fine details: morning birds, or a lit window on the hill at night, and a shimmer near the foot. */
private fun DrawScope.drawDetails(g: SceneGeometry, light: SceneLight, hills: HillColors, position: Float, act: SceneEntrance) {
    val (w, h) = g.w to g.h
    if (light.day > 0.2f && position < 0.42f && act.birds > 0f) {
        val birdAlpha = 0.55f * light.day * act.birds
        val flightX = act.birdsFlight.x * w
        val flightY = act.birdsFlight.y * h
        drawBird(g, w * 0.66f + flightX, h * 0.60f + flightY, w * 0.032f, act.wingbeat, hills.day.copy(alpha = birdAlpha))
        drawBird(g, w * 0.73f + flightX * 1.3f, h * 0.56f + flightY * 1.3f, w * 0.024f, -act.wingbeat, hills.day.copy(alpha = birdAlpha * 0.8f))
    }
    if (light.m > 0.2f) {
        val window = Offset(w * 0.27f, h * 0.83f)
        drawCircle(WindowGlowColor.copy(alpha = 0.32f * light.m * act.window), radius = w * 0.028f * (0.5f + 0.5f * act.window), center = window)
        drawCircle(WindowGlowColor.copy(alpha = 0.92f * light.m * act.window), radius = w * 0.011f * act.window, center = window)
    }
    val shimmerColor = (if (light.m > 0.5f) MoonColor else Color.White).copy(alpha = 0.22f * act.shimmer * (1f + SparkleBoost * act.sparkle))
    val span1 = 0.16f * act.shimmer
    val span2 = 0.08f * act.shimmer
    if (act.shimmer > 0.01f) {
        drawLine(shimmerColor, Offset(w * (0.50f - span1), h * 0.935f), Offset(w * (0.50f + span1), h * 0.935f), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(shimmerColor, Offset(w * (0.50f - span2), h * 0.962f), Offset(w * (0.50f + span2), h * 0.962f), strokeWidth = 1.0.dp.toPx(), cap = StrokeCap.Round)
    }
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

/** A bird in flight; [wingbeat] (−1 → 1, 0 at rest) lowers or raises its wings. */
private fun DrawScope.drawBird(g: SceneGeometry, cx: Float, cy: Float, span: Float, wingbeat: Float, color: Color) {
    val lift = span * 0.45f * (1f + wingbeat)
    val bird = g.birdPath.apply {
        rewind()
        moveTo(cx - span, cy + span * 0.15f)
        quadraticTo(cx - span * 0.45f, cy - lift, cx, cy + span * 0.08f)
        quadraticTo(cx + span * 0.45f, cy - lift, cx + span, cy + span * 0.15f)
    }
    drawPath(bird, color, style = g.birdStroke)
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

/** How much a star swells as it ignites, the moon's glow grows as it blooms, and the shimmer brightens as it sparkles. */
private const val StarFlare = 0.8f
private const val MoonBloomGrowth = 0.5f
private const val SparkleBoost = 1.2f

/** The dawn rays' length while still folded together, as a share of their spread length. */
private const val FoldedRayLength = 0.55f

// The sky's lights: fixed like the sky gradients they sit on (design.md §2.4: gradients only for skies).
private val StarColor = Color(0xFFFFF4D6)
private val MoonColor = Color(0xFFF5EDD6)
private val SunCoreColor = Color(0xFFFFF3C4)
private val DawnHorizonGlow = Color(0xFFFFB88C)
private val DuskHorizonGlow = Color(0xFFFF8E6E)
private val WindowGlowColor = Color(0xFFFFD175)
