package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The sun ⇄ moon swap in the reveal's illustration when a pick moves between morning and evening types, as functions
 * of one 0 → 1 clock (`ClockblockMotion.bodySwap()`): the two cross-fade (a symmetric sine ease, so both are half
 * there at the midpoint), the body pops up by [PopScale] at the midpoint and back, and wobbles side to side ([Wobbles] shakes, up to
 * [WobbleDegrees], dying away). Every value is exactly at rest at 0 and 1.
 */
internal object BodySwap {
    /** How much bigger the body is at the top of the pop. */
    const val PopScale = 0.16f

    /** The wobble's widest swing, and how many times it shakes. */
    const val WobbleDegrees = 9f
    const val Wobbles = 3f

    /** The moon's share (0 = sun, 1 = moon) at [progress] of a swap [from] one share [to] the other. */
    fun moonFraction(from: Float, to: Float, progress: Float): Float =
        lerp(from, to, (1f - cos(PI.toFloat() * progress.coerceIn(0f, 1f))) / 2f)

    /** The pop, 0 at rest and 1 at the midpoint. */
    fun pop(progress: Float): Float = sin(PI.toFloat() * progress.coerceIn(0f, 1f))

    /** The wobble's angle in degrees: shakes both ways, dying away to 0 at the end. */
    fun wobble(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val decay = (1f - p) * (1f - p)
        return WobbleDegrees * sin(2f * PI.toFloat() * Wobbles * p) * decay
    }
}
