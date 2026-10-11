package dev.sebastiano.clockblocker.opus.feature.trips.editor

import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the editor's "Your shift" card says about a [ShiftPreview]: "+9 h east · about 4 days with the plan, 7
 * without". Pure, so the numbers are unit-tested; the card only turns them into words. Days round like the plan's
 * "with your plan" stat (nearest whole day, at least 1), and the hours use the trip cards' "+9 h" format.
 */
sealed interface ShiftSummary {

    /** The plan keeps the body clock on home time (a short trip, or Home time picked). */
    data object HomeTime : ShiftSummary

    /** Under an hour (or no direction): no jet lag to speak of. */
    data object NoShift : ShiftSummary

    /**
     * @property shiftHours the time difference the body clock crosses, from where it starts to the destination,
     *   in (−12, 12] (positive = east), for `formatJetLagHours`. Not the planned shift: a +10 h eastward trip
     *   planned the long way is a 14 h delay (`JetLagPlan.shiftHours` −14), and still reads "+10 h east".
     * @property longWay the body clock moves the other way round (later for an eastward trip, earlier for a westward
     *   one), which the card calls out so "+10 h east" with a later shift doesn't read as a mistake.
     */
    data class Shift(
        val shiftHours: Float,
        val daysWithPlan: Int,
        val daysWithoutPlan: Int,
        val longWay: Boolean,
    ) : ShiftSummary {
        val east: Boolean get() = shiftHours > 0f
    }

    companion object {
        /** Below this the planner treats the trip as no shift (the plan screen's "no jet lag" threshold). */
        const val MinShiftHours: Double = 1.0

        fun of(preview: ShiftPreview): ShiftSummary {
            val zone = timeDifference(preview.shiftHours)
            return when {
                // A home-time plan reports no shift (direction None, 0 h) by design, so it's checked first:
                // otherwise a long-haul short stay would read as "no jet lag".
                preview.strategy == AdaptationStrategy.StayOnHomeTime -> HomeTime
                preview.direction == ShiftDirection.None || abs(zone) < MinShiftHours -> NoShift
                else -> Shift(
                    shiftHours = zone.toFloat(),
                    daysWithPlan = roundDays(preview.daysToAdapt),
                    daysWithoutPlan = roundDays(preview.daysWithoutPlan),
                    longWay = (zone > 0 && preview.direction == ShiftDirection.Delay) ||
                        (zone < 0 && preview.direction == ShiftDirection.Advance),
                )
            }
        }

        /** The planned shift folded back to the time difference it crosses, in (−12, 12]: −14 → +10, +13 → −11. */
        internal fun timeDifference(plannedHours: Double): Double {
            val folded = ((plannedHours % 24.0) + 24.0) % 24.0 // [0, 24)
            return if (folded > 12.0) folded - 24.0 else folded
        }

        private fun roundDays(days: Double): Int = days.roundToInt().coerceAtLeast(1)
    }
}
