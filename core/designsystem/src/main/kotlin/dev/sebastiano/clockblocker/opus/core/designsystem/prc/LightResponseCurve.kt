package dev.sebastiano.clockblocker.opus.core.designsystem.prc

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * A schematic human phase response curve to bright light, for the "light response curve" card (issue #20).
 *
 * The x axis is hours from the body's coldest point (CBTmin), −12…+12 h. The y axis is the shift that a long,
 * bright light exposure would cause there, in hours: negative moves the clock later (a delay), positive moves it
 * earlier (an advance). The shape follows Khalsa et al. 2003 (6.7 h of ~10,000 lux): crossover at CBTmin, the
 * largest delay (≈ 3.4 h) about 3½ h before it, the largest advance (≈ 2 h) about 2½ h after it, no long dead
 * zone, and only a weak region about 12 h away. St Hilaire et al. 2012 found the same shape and timing for a 1 h
 * pulse, at ~40 % of the amplitude.
 *
 * It is a drawing aid, not a prediction: each lobe is a skewed bump `u·(1−u)^b` scaled to its peak, so the card
 * reads the right way round. The planner does not use it (see docs/science.md §2.6), and the readout stays
 * qualitative ([effectAt]).
 */
object LightResponseCurve {
    /** The curve spans this many hours either side of the coldest point. */
    const val SpanHours = 12.0

    /** Largest delay (later), hours, Khalsa 2003. */
    const val PeakDelayHours = 3.4

    /** Largest advance (earlier), hours, Khalsa 2003. */
    const val PeakAdvanceHours = 2.0

    /** Where the largest delay falls, hours from the coldest point. */
    const val PeakDelayAtHours = -3.5

    /** Where the largest advance falls, hours from the coldest point. */
    const val PeakAdvanceAtHours = 2.5

    /** Shifts at least this large read "strongly". */
    const val StronglyHours = 1.5

    /** Shifts smaller than this read "barely". */
    const val BarelyHours = 0.3

    /** The handle moves in quarter hours. */
    const val StepHours = 0.25

    private val delayLobe = Lobe(peakAt = -PeakDelayAtHours / SpanHours)
    private val advanceLobe = Lobe(peakAt = PeakAdvanceAtHours / SpanHours)

    /** What light at a point of the curve does, in words. */
    enum class Effect { LaterStrongly, LaterALittle, Barely, EarlierALittle, EarlierStrongly }

    /** A haptic step while dragging along the curve. */
    enum class Tick { Hour, ColdestPoint }

    /** The schematic shift in hours (+ earlier, − later) for light at [hoursFromCbtMin]; repeats every 24 h. */
    fun shiftAt(hoursFromCbtMin: Double): Double {
        val x = (hoursFromCbtMin + SpanHours).mod(2 * SpanHours) - SpanHours
        return if (x < 0) -PeakDelayHours * delayLobe(-x / SpanHours) else PeakAdvanceHours * advanceLobe(x / SpanHours)
    }

    /** The qualitative readout for light at [hoursFromCbtMin]. */
    fun effectAt(hoursFromCbtMin: Double): Effect {
        val shift = shiftAt(hoursFromCbtMin)
        return when {
            abs(shift) < BarelyHours -> Effect.Barely
            shift < 0 -> if (-shift >= StronglyHours) Effect.LaterStrongly else Effect.LaterALittle
            else -> if (shift >= StronglyHours) Effect.EarlierStrongly else Effect.EarlierALittle
        }
    }

    /** The haptic for moving the handle [from] → [to]: crossing the coldest point beats crossing an hour. */
    fun tickBetween(from: Double, to: Double): Tick? = when {
        from == to -> null
        (from < 0 && to >= 0) || (from > 0 && to <= 0) -> Tick.ColdestPoint
        floor(from) != floor(to) -> Tick.Hour
        else -> null
    }

    /** [hours] on the handle's quarter-hour grid, within the curve. */
    fun snap(hours: Double): Double = ((hours / StepHours).roundToLong() * StepHours).coerceIn(-SpanHours, SpanHours) + 0.0

    /**
     * An advice window [start]…[end] in hours from [cbtMin]. A block that runs past either end of the curve wraps
     * round to the other end, because the curve repeats every 24 h.
     */
    fun window(cbtMin: Instant, start: Instant, end: Instant): Window =
        Window.between(hoursFrom(cbtMin, start), hoursFrom(cbtMin, end))

    private fun hoursFrom(cbtMin: Instant, instant: Instant): Double = Duration.between(cbtMin, instant).toMillis() / 3_600_000.0

    /** [hours] folded onto the curve, −12 (inclusive) … +12 (exclusive). */
    private fun wrap(hours: Double): Double {
        val x = (hours + SpanHours).mod(2 * SpanHours) - SpanHours
        return if (x >= SpanHours) x - 2 * SpanHours else x
    }

    /**
     * An advice block on the curve, as one segment, or two when it wraps past +12 h round to −12 h. Segments are in
     * the block's own order (the first starts where the block starts) and cover at most the whole curve.
     */
    data class Window(val segments: List<ClosedFloatingPointRange<Double>>) {
        /** The block's midpoint on the curve, wrapped like the block. */
        val middle: Double =
            wrap(segments.first().start + segments.sumOf { it.endInclusive - it.start } / 2)

        /** The longest segment, where the block's label goes. */
        val largest: ClosedFloatingPointRange<Double> = segments.maxBy { it.endInclusive - it.start }

        /** Whether a segment end at [hours] is only the seam where the block wraps (±12 h), not a real edge of it. */
        fun isSeam(hours: Double): Boolean = segments.size > 1 && abs(abs(hours) - SpanHours) < 1e-9

        operator fun contains(hours: Double): Boolean = segments.any { hours in it }

        companion object {
            /** The block from [startHours] to [endHours] (hours from the coldest point, any range), on the curve. */
            fun between(startHours: Double, endHours: Double): Window {
                val length = (endHours - startHours).coerceIn(0.0, 2 * SpanHours)
                val start = wrap(startHours)
                val end = start + length
                return Window(
                    if (end <= SpanHours) listOf(start..end) else listOf(start..SpanHours, -SpanHours..end - 2 * SpanHours),
                )
            }
        }
    }

    /** `u·(1−u)^b` on 0…1, scaled so its peak (at u = [peakAt]) is 1. Zero at both ends. */
    private class Lobe(peakAt: Double) {
        private val b = 1 / peakAt - 1
        private val scale = 1 / (peakAt * (1 - peakAt).pow(b))

        operator fun invoke(u: Double): Double = scale * u * (1 - u).pow(b)
    }
}
