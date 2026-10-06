package dev.sebastiano.clockblocker.opus.core.designsystem.advice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.ShapeMorph
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.drawHatch
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.drawRoundDots
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.drawStarDots
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.drawStrike
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.AdvicePattern
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.LocalReduceMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.pattern
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import kotlin.math.PI
import kotlin.math.sin

/**
 * "Shapes carry meaning, never decoration": the MaterialShape that stands for each advice type
 * (design.md §2.4 Shape language).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object AdviceShapes {
    fun target(type: AdviceType): RoundedPolygon = when (type) {
        AdviceType.SeeBrightLight -> MaterialShapes.VerySunny
        AdviceType.SeeLight -> MaterialShapes.Sunny
        AdviceType.AvoidLight -> MaterialShapes.SemiCircle
        AdviceType.Sleep -> MaterialShapes.Pill
        AdviceType.Nap, AdviceType.OptionalNap -> MaterialShapes.Bun
        AdviceType.Melatonin -> MaterialShapes.PuffyDiamond
        AdviceType.Caffeine, AdviceType.AvoidCaffeine -> MaterialShapes.Cookie4Sided
        AdviceType.PeakFatigue -> MaterialShapes.SoftBurst
        AdviceType.Flight -> MaterialShapes.Arrow
    }

    /** Where the "now" morph starts: Circle, except Avoid light where the sun sets (Sunny → SemiCircle). */
    fun start(type: AdviceType): RoundedPolygon = when (type) {
        AdviceType.AvoidLight -> MaterialShapes.Sunny
        else -> MaterialShapes.Circle
    }

    /** Fully adapted: Circle → Flower bloom. */
    val Adapted: RoundedPolygon get() = MaterialShapes.Flower

    /**
     * The 8-bit easter egg's pixel twin: `PixelTriangle` for the "go" types (light, caffeine, peak fatigue,
     * flight), `PixelCircle` for the rest-and-dark ones. Labels still carry the meaning.
     */
    fun pixel(type: AdviceType): RoundedPolygon = when (type) {
        AdviceType.SeeBrightLight, AdviceType.SeeLight, AdviceType.Caffeine, AdviceType.PeakFatigue, AdviceType.Flight ->
            MaterialShapes.PixelTriangle
        else -> MaterialShapes.PixelCircle
    }

    /**
     * Rotation that makes the shape read right: MaterialShapes' Pill is diagonal (we want it lying down, like a
     * sleeper) and Bun is stacked (we want it lying flat, like a pillow).
     */
    fun baseRotation(type: AdviceType): Float = when (type) {
        AdviceType.Sleep -> 45f
        AdviceType.Nap, AdviceType.OptionalNap -> 90f
        else -> 0f
    }
}

/**
 * The advice glyph. Labels always accompany it (it is decorative to TalkBack).
 *
 * - **Inactive**: a circular container in the advice container colour holding the advice's MaterialShape as a
 *   mark, so shape identity never depends on colour.
 * - **Active** ("now"): the container itself morphs into the shape (`ClockblockMotion.glyphMorph`, fast spatial) in the
 *   vivid advice colour, then plays one ambient cycle and rests: VerySunny turns one step, melatonin twinkles,
 *   peak fatigue pulses.
 * - **Reduced motion**: the morph snaps to its end state and the ambient cycle never plays.
 *
 * @param headingDegrees for [AdviceType.Flight]: the plane's heading (0 = east, clockwise).
 */
@Composable
fun AdviceGlyph(
    type: AdviceType,
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    headingDegrees: Float = -45f,
) {
    val role = ClockblockTheme.adviceColors[type]
    val motion = ClockblockTheme.motion
    val reduce = LocalReduceMotion.current
    val morph = remember(type) { ShapeMorph(AdviceShapes.start(type), AdviceShapes.target(type)) }
    val mark = remember(type) { ShapeMorph(AdviceShapes.target(type), AdviceShapes.target(type)) }
    val progress = remember { Animatable(if (active) 1f else 0f) }
    LaunchedEffect(active, reduce) {
        val target = if (active) 1f else 0f
        if (reduce) {
            progress.snapTo(target)
        } else if (active) {
            progress.animateTo(target, motion.glyphMorph())
        } else {
            // A retraction is not a celebration: settle back plainly.
            progress.animateTo(target, motion.dataSpatial())
        }
    }
    val outlineOnly = type.pattern == AdvicePattern.Strike
    val fill by animateColorAsState(
        targetValue = when {
            outlineOnly -> role.container
            active -> role.color
            else -> role.container
        },
        animationSpec = motion.colour(),
        label = "glyphFill",
    )
    val ambient = rememberAmbientPhase(type, enabled = active && !reduce)
    val baseRotation = if (type == AdviceType.Flight) headingDegrees + 90f else AdviceShapes.baseRotation(type)
    val path = remember { Path() }
    val markPath = remember { Path() }

    // 8-bit easter egg: the resting shape morphs into its pixel twin (and straightens up).
    val pixelMode = ClockblockTheme.pixelMode
    val pixel = remember { Animatable(if (pixelMode) 1f else 0f) }
    LaunchedEffect(pixelMode, reduce) {
        val target = if (pixelMode) 1f else 0f
        if (reduce) pixel.snapTo(target) else pixel.animateTo(target, motion.glyphMorph())
    }
    val pixelShape = AdviceShapes.pixel(type)
    val pixelFromActive = remember(type) { ShapeMorph(AdviceShapes.target(type), pixelShape) }
    val pixelFromIdle = remember(type) { ShapeMorph(AdviceShapes.start(type), pixelShape) }
    val pixelMark = pixelFromActive

    Canvas(modifier.size(size)) {
        val p = progress.value
        val q = pixel.value
        val phase = ambient.value
        val rotation = (baseRotation + when (type) {
            AdviceType.SeeBrightLight -> phase * 45f
            else -> 0f
        }) * (1f - q)
        val scale = when (type) {
            AdviceType.Melatonin -> 1f + 0.06f * sin(phase * 2f * PI.toFloat())
            AdviceType.PeakFatigue -> 1f + 0.035f * sin(phase * 2f * PI.toFloat())
            else -> 1f
        }
        val shape = when {
            q <= 0f -> morph.toPath(p, this.size, rotation, scale, path)
            p >= 0.5f -> pixelFromActive.toPath(q, this.size, rotation, scale, path)
            else -> pixelFromIdle.toPath(q, this.size, rotation, scale, path)
        }
        drawPath(shape, fill)
        if (outlineOnly) {
            drawPath(shape, role.color, style = Stroke(width = 2.dp.toPx()))
        }
        // Pattern overlay grows with the morph so the active glyph keeps its colour-blind carrier.
        val patternAlpha = p.coerceIn(0f, 1f)
        if (patternAlpha > 0f) {
            clipPath(shape) {
                when (type.pattern) {
                    AdvicePattern.Hatch -> drawHatch(role.onColor.copy(alpha = 0.45f * patternAlpha), 4.5.dp, 1.4.dp)
                    AdvicePattern.StarDots -> drawStarDots(role.onColor.copy(alpha = 0.7f * patternAlpha), 7.dp, 0.8.dp)
                    AdvicePattern.RoundDots -> if (type == AdviceType.OptionalNap) {
                        drawRoundDots(role.onColor.copy(alpha = 0.35f * patternAlpha), 5.dp, 1.1.dp)
                    }
                    AdvicePattern.Strike -> drawStrike(role.color.copy(alpha = patternAlpha), 2.5.dp, 0.22f)
                    else -> Unit
                }
            }
        }
        // Inactive mark: the shape as an icon inside the circular container.
        val markAlpha = (1f - p).coerceIn(0f, 1f)
        if (markAlpha > 0f) {
            val markColor = role.onContainer.copy(alpha = markAlpha)
            val markRotation = baseRotation * (1f - q)
            val m = if (q <= 0f) {
                mark.toPath(0f, this.size, markRotation, 0.5f, markPath)
            } else {
                pixelMark.toPath(q, this.size, markRotation, 0.5f, markPath)
            }
            if (outlineOnly) {
                drawPath(m, markColor, style = Stroke(width = 1.6.dp.toPx()))
                clipPath(shape) { drawStrike(markColor, 1.8.dp, 0.3f) }
            } else {
                drawPath(m, markColor)
            }
        }
    }
}

/** A morph between any two shapes driven by [active] (e.g. Circle → Flower for "adapted"). */
@Composable
fun MorphGlyph(
    start: RoundedPolygon,
    end: RoundedPolygon,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val motion = ClockblockTheme.motion
    val reduce = LocalReduceMotion.current
    val morph = remember(start, end) { ShapeMorph(start, end) }
    val progress = remember { Animatable(if (active) 1f else 0f) }
    LaunchedEffect(active, reduce) {
        if (reduce) progress.snapTo(if (active) 1f else 0f) else progress.animateTo(if (active) 1f else 0f, motion.glyphMorph())
    }
    val path = remember { Path() }
    Canvas(modifier.size(size)) {
        drawPath(morph.toPath(progress.value, this.size, out = path), color)
    }
}

/**
 * One ambient cycle (VerySunny turns one 45° step, melatonin twinkles once, peak fatigue pulses once) when the
 * glyph becomes active, then rest at phase 0, which is the same frame as phase 1. The glyph sits on the plan
 * screen, which stays open for long stretches, so it must not loop (T-009). The played flag is saveable: coming
 * back to the screen with the same advice still active does not replay it.
 */
@Composable
private fun rememberAmbientPhase(type: AdviceType, enabled: Boolean): State<Float> {
    val period = when (type) {
        AdviceType.SeeBrightLight -> ClockblockMotion.SunRotationMillis
        AdviceType.Melatonin -> ClockblockMotion.TwinkleMillis
        AdviceType.PeakFatigue -> ClockblockMotion.PulseMillis
        else -> 0
    }
    val motion = ClockblockTheme.motion
    val phase = remember { Animatable(0f) }
    var played by rememberSaveable(type) { mutableStateOf(false) }
    LaunchedEffect(enabled, period) {
        if (!enabled) {
            played = false
            phase.snapTo(0f)
            return@LaunchedEffect
        }
        if (period == 0 || played) return@LaunchedEffect
        played = true
        phase.snapTo(0f)
        phase.animateTo(1f, motion.ambientOnce(period))
        phase.snapTo(0f)
    }
    return phase.asState()
}
