package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Onboarding's finish, on one 0 → 1 clock (`ClockblockMotion.takeoff()`): the runway plane takes off while the
 * content leaves, roughly the chronotype reveal's entrance played backwards: where its words dropped in top first,
 * here everything rises out and fades, bottom first (the buttons, then the step's controls, body, headline and art,
 * the step label and runway last, under the climbing plane).
 */
internal object Takeoff {
    /** Exit slots, bottom first. */
    const val BottomBar = 0
    const val Controls = 1
    const val Body = 2
    const val Headline = 3
    const val Art = 4
    const val TopBar = 5

    /** Each slot's share of the clock, and how far after the previous one it starts. */
    private const val ItemShare = 0.5f
    private const val Stagger = 0.1f

    /** How far an item rises as it leaves. */
    val Rise = 16.dp

    /** How far through leaving (0 → 1, eased) the item in [slot] is at [progress] of the clock. */
    fun exit(slot: Int, progress: Float): Float {
        val start = slot * Stagger
        val p = ((progress - start) / ItemShare).coerceIn(0f, 1f)
        return FastOutLinearInEasing.transform(p)
    }

    /**
     * The plane's way from its holding point (0) to the end of the runway (1) and beyond, at [progress]: rolling,
     * accelerating all the way.
     */
    fun roll(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return p * p * RollBeyond
    }

    /** Its nose-up pitch, in degrees: it rotates once it has speed. */
    fun pitch(progress: Float): Float = stage(progress, 0.35f, 0.6f) * MaxPitch

    /** How far it has climbed, as a share of [ClimbHeight]: it leaves the ground as it finishes rotating. */
    fun climb(progress: Float): Float {
        val p = stage(progress, 0.5f, 1f)
        return p * p
    }

    /** Its opacity: gone by the end, into the sky. */
    fun alpha(progress: Float): Float = 1f - stage(progress, 0.75f, 1f)

    val ClimbHeight = 28.dp
    private const val MaxPitch = 12f
    private const val RollBeyond = 1.15f

    private fun stage(progress: Float, from: Float, to: Float): Float = ((progress - from) / (to - from)).coerceIn(0f, 1f)
}

/**
 * The finish clock: 0 until onboarding leaves. Provided once and only ever read in the layer phase, so the take-off
 * starting recomposes nothing (a value provided as it starts would recompose every step under it, a long first frame).
 */
internal val LocalTakeoff = staticCompositionLocalOf<() -> Float> { { 0f } }

/** The item in exit [slot] rises out and fades as onboarding leaves (read in the layer phase). */
@Composable
internal fun Modifier.takeoffExit(slot: Int): Modifier {
    val takeoff = LocalTakeoff.current
    return remember(this, slot, takeoff) {
        this.graphicsLayer {
            val e = Takeoff.exit(slot, takeoff())
            alpha = 1f - e
            translationY = -lerp(0f, Takeoff.Rise.toPx(), e)
        }
    }
}
