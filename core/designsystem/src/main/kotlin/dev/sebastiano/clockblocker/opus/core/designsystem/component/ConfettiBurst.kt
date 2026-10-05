package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.sharedUnitPath
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** One confetti particle's launch parameters (deterministic per seed so goldens are stable). */
@Immutable
internal data class ConfettiParticle(
    val angle: Float,
    val speed: Float,
    val sizeDp: Float,
    val spin: Float,
    val shape: Int,
    val color: Int,
)

internal fun confettiParticles(count: Int, seed: Long): List<ConfettiParticle> {
    val rnd = Random(seed)
    return List(count) {
        ConfettiParticle(
            angle = (-155f + rnd.nextFloat() * 130f) * PI.toFloat() / 180f,
            speed = 0.7f + rnd.nextFloat() * 0.6f,
            sizeDp = 9f + rnd.nextFloat() * 9f,
            spin = (rnd.nextFloat() - 0.5f) * 900f,
            shape = rnd.nextInt(4),
            color = rnd.nextInt(5),
        )
    }
}

/**
 * Position of a particle at [seconds] after launch, in units of the canvas height, relative to the origin.
 * Linear drag (k) plus gravity (g): x = vx·(1 − e^−kt)/k, y = vy·(1 − e^−kt)/k + g·t²/2.
 */
internal fun confettiOffset(p: ConfettiParticle, seconds: Float, drag: Float = 1.6f, gravity: Float = 1.5f): Offset {
    val d = (1f - exp(-drag * seconds)) / drag
    val vx = cos(p.angle) * p.speed * 0.75f
    val vy = sin(p.angle) * p.speed * 1.1f
    return Offset(vx * d, vy * d + 0.5f * gravity * seconds * seconds)
}

/**
 * A stateless frame of the adaptation confetti (design.md §2.4: MaterialShapes Sunny, Clover4Leaf,
 * PuffyDiamond, Cookie4Sided in advice colours, physics drop). [progress] 0–1 is read in the draw phase only.
 * [origin] is where the burst launches from, in this canvas's pixels (read in draw; null = the centre), so a
 * host can fire it from the element that earned it (the Bloom).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConfettiCanvas(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    particleCount: Int = 36,
    seed: Long = 7L,
    origin: (() -> Offset?)? = null,
) {
    val particles = remember(particleCount, seed) { confettiParticles(particleCount, seed) }
    val advice = OpusTheme.adviceColors
    val colors: List<Color> = listOf(
        advice[AdviceType.SeeBrightLight].color,
        advice[AdviceType.Sleep].color,
        advice[AdviceType.Melatonin].color,
        advice[AdviceType.Nap].color,
        advice[AdviceType.Caffeine].color,
    )
    val shapes: List<RoundedPolygon> = remember {
        listOf(MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.PuffyDiamond, MaterialShapes.Cookie4Sided)
    }
    Canvas(modifier) {
        val t = progress().coerceIn(0f, 1f)
        if (t <= 0f || t >= 1f) return@Canvas
        val seconds = t * TotalSeconds
        val alpha = 1f - ((t - 0.72f) / 0.28f).coerceIn(0f, 1f)
        val launch = origin?.invoke() ?: Offset(size.width / 2f, size.height * 0.5f)
        particles.forEach { p ->
            val o = confettiOffset(p, seconds)
            val c = launch + Offset(o.x * size.height, o.y * size.height)
            val s = p.sizeDp.dp.toPx()
            translate(c.x, c.y) {
                rotate(p.spin * seconds, pivot = Offset.Zero) {
                    scale(s, s, pivot = Offset.Zero) {
                        drawPath(shapes[p.shape].sharedUnitPath(), colors[p.color], alpha = alpha)
                    }
                }
            }
        }
    }
}

private const val TotalSeconds = OpusMotion.CelebrationMillis / 1000f

/**
 * Plays the confetti once each time [playing] turns true, over the celebration clock token, then calls
 * [onFinished]. A rare, earned moment (frequency gate: once per trip), so it may be exuberant; under reduce
 * motion it is skipped entirely and [onFinished] fires straight away (the "Clockblocked." headline and the
 * flattened wave carry the meaning).
 */
@Composable
fun ConfettiBurst(
    playing: Boolean,
    modifier: Modifier = Modifier,
    origin: (() -> Offset?)? = null,
    onFinished: () -> Unit = {},
) {
    val reduce = OpusTheme.reduceMotion
    val motion = OpusTheme.motion
    val finished = rememberUpdatedState(onFinished)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(playing, reduce) {
        if (!playing) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        if (!reduce) {
            progress.snapTo(0f)
            progress.animateTo(1f, motion.celebrationClock())
        }
        progress.snapTo(1f)
        finished.value()
    }
    if (playing && !reduce) ConfettiCanvas(progress = { progress.value }, modifier = modifier, origin = origin)
}
