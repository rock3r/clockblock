package dev.sebastiano.clockblocker.opus.core.circadian.engine

import dev.sebastiano.clockblocker.opus.core.circadian.CircadianMath.mod24
import dev.sebastiano.clockblocker.opus.core.circadian.CircadianMath.norm12
import dev.sebastiano.clockblocker.opus.core.circadian.ode.CircadianModel
import dev.sebastiano.clockblocker.opus.core.circadian.ode.Rk4
import dev.sebastiano.clockblocker.opus.core.circadian.ode.Trajectory
import kotlin.math.abs
import kotlin.math.roundToInt

/** The journey as the validator sees it: home offset, legs (with layovers between them), destination offset. */
internal data class Itinerary(val homeOffset: Double, val legs: List<LegHours>) {
    val dep: Double get() = legs.first().dep
    val arr: Double get() = legs.last().arr
    val destOffset: Double get() = legs.last().arrOffset

    fun inFlight(t: Double): Boolean = legs.any { t >= it.dep && t < it.arr }

    /** UTC offset of the place the traveller is at (home before departure, layover city, destination after). */
    fun groundOffset(t: Double): Double {
        if (t < dep) return homeOffset
        for (i in 0 until legs.size - 1) {
            if (t >= legs[i].arr && t < legs[i + 1].dep) return legs[i].arrOffset
        }
        return destOffset
    }
}

/** A plan reduced to what matters for light: intervals per state (§13.1), and the window it covers. */
internal data class LightPlan(
    val sleep: List<Interval>,
    val avoid: List<Interval>,
    val seek: List<Interval>,
    val start: Double,
    val end: Double,
) {
    companion object {
        /** `validate.plan_lux`: every cycle's sleep parts, avoid and seek; covered from first T − 12 to last T + 12. */
        fun of(cycles: List<Cycle>, restInsteadOfSleep: List<Interval> = emptyList()): LightPlan {
            val sleeps = cycles.flatMap { it.sleepParts }.filter { it.length > 0 }
            return LightPlan(
                sleep = sleeps.minus(restInsteadOfSleep),
                avoid = cycles.flatMap { it.avoid } + sleeps.flatMap { s -> restInsteadOfSleep.mapNotNull { s.intersect(it) } },
                seek = cycles.flatMap { it.seek },
                start = cycles.first().t - 12.0,
                end = cycles.last().t + 12.0,
            )
        }
    }
}

/** One simulated CBTmin: time (UTC hours) and error vs the destination target (hours, norm12). */
internal data class CbtRow(val t: Double, val error: Double)

internal data class Outcome(val rows: List<CbtRow>, val adaptDays: Double?, val netShift: Double)

internal data class ValidationResult(val plan: Outcome, val noPlan: Outcome, val baseClock: Double)

/**
 * Port of `reference/validate.py` (§13): turns a plan into lux(t) with the §4.4 table, simulates it with an ODE,
 * reads out CBTmins and computes days-to-adapt. Uses an integer step counter throughout; the warm-up is shared
 * between the baseline, the plan and the no-plan runs.
 */
internal class PlanValidator(
    private val model: CircadianModel,
    private val habitualOnset: Double,
    private val habitualWake: Double,
    private val lightBox: Boolean = false,
    private val dt: Double = Rk4.DEFAULT_DT,
) {
    /** §4.4 default lux levels. */
    object Lux {
        const val SLEEP = 0.0
        const val AVOID = 10.0
        const val SEEK_DAY = 3000.0
        const val SEEK_NIGHT_BOX = 5000.0
        const val SEEK_NIGHT = 500.0
        const val NEUTRAL = 250.0
        const val OUTDOOR = 3000.0
        const val FLIGHT = 100.0
        const val FLIGHT_SEEK = 1000.0
        const val FLIGHT_AVOID = 5.0
    }

    fun typicalDay(localHour: Double): Double {
        val h = mod24(localHour)
        val asleep = if (habitualOnset > habitualWake) h >= habitualOnset || h < habitualWake else h >= habitualOnset && h < habitualWake
        return when {
            asleep -> Lux.SLEEP
            h >= 12.0 && h < 14.0 -> Lux.OUTDOOR
            else -> Lux.NEUTRAL
        }
    }

    fun planLux(it: Itinerary, p: LightPlan): (Double) -> Double = { t ->
        when {
            t < p.start -> typicalDay(t + it.homeOffset)
            t >= p.end -> typicalDay(t + it.destOffset)
            else -> {
                val h = mod24(t + it.groundOffset(t))
                val inFlight = it.inFlight(t)
                when {
                    p.sleep.containsPoint(t) -> Lux.SLEEP
                    p.avoid.containsPoint(t) -> if (inFlight) Lux.FLIGHT_AVOID else Lux.AVOID
                    p.seek.containsPoint(t) -> when {
                        inFlight -> Lux.FLIGHT_SEEK
                        h >= 7.0 && h < 19.0 -> Lux.SEEK_DAY
                        lightBox -> Lux.SEEK_NIGHT_BOX
                        else -> Lux.SEEK_NIGHT
                    }
                    inFlight -> Lux.FLIGHT
                    h >= 12.0 && h < 14.0 -> Lux.OUTDOOR
                    else -> Lux.NEUTRAL
                }
            }
        }
    }

    fun noPlanLux(it: Itinerary): (Double) -> Double = { t ->
        when {
            t < it.dep -> typicalDay(t + it.homeOffset)
            it.inFlight(t) -> Lux.FLIGHT
            else -> typicalDay(t + it.groundOffset(t))
        }
    }

    fun validate(itinerary: Itinerary, plan: LightPlan?, daysAfter: Int): ValidationResult {
        val home = itinerary.homeOffset
        val t0 = minOf(itinerary.dep - 24.0 * 5, plan?.start ?: Double.MAX_VALUE)
        val start = t0 - 24.0 * WARM_UP_DAYS
        val warmSteps = ((t0 - start) / dt).roundToInt()
        val recordFrom = t0 - 72.0
        val typicalHome = { t: Double -> typicalDay(t + home) }

        // warm-up on the habitual "typical day" at home, shared by all three runs
        val prefixTimes = ArrayList<Double>()
        val prefixStates = ArrayList<DoubleArray>()
        val warm = Rk4.integrate(model, model.initialState, start, warmSteps, dt, typicalHome) { t, s ->
            if (t >= recordFrom) {
                prefixTimes += t
                prefixStates += s.copyOf()
            }
        }

        fun run(lux: (Double) -> Double, steps: Int): Trajectory {
            val times = ArrayList<Double>(prefixTimes.size + steps)
            val states = ArrayList<DoubleArray>(prefixStates.size + steps)
            times += prefixTimes
            states += prefixStates
            Rk4.integrate(model, warm, start, steps, dt, lux, firstStep = warmSteps) { t, s ->
                times += t
                states += s.copyOf()
            }
            return Trajectory(times.toDoubleArray(), states.toTypedArray())
        }

        val base = run(typicalHome, (72.0 / dt).roundToInt())
        val baseClock = mod24(model.cbtMinTimes(base).last() + home)

        val t1 = itinerary.arr + 24.0 * daysAfter
        val steps = ((t1 - start) / dt).roundToInt() - warmSteps
        val noPlan = outcome(run(noPlanLux(itinerary), steps), itinerary, t0, baseClock)
        val planOutcome = if (plan == null) noPlan else outcome(run(planLux(itinerary, plan), steps), itinerary, t0, baseClock)
        return ValidationResult(planOutcome, noPlan, baseClock)
    }

    private fun outcome(tr: Trajectory, itinerary: Itinerary, t0: Double, baseClock: Double): Outcome {
        val rows = model.cbtMinTimes(tr).filter { c -> c > t0 }.map { c -> CbtRow(c, norm12(mod24(c + itinerary.destOffset) - baseClock)) }
        var adapt: Double? = null
        for (i in rows.indices) {
            if (rows[i].t < itinerary.arr) continue
            if (i + 3 <= rows.size && (i until i + 3).all { j -> abs(rows[j].error) <= ADAPT_TOLERANCE }) {
                adapt = (rows[i].t - itinerary.arr) / 24.0
                break
            }
        }
        val net = (0 until rows.size - 1).sumOf { j -> rows[j + 1].t - rows[j].t - 24.0 }
        return Outcome(rows, adapt, net)
    }

    companion object {
        const val WARM_UP_DAYS: Int = 40

        /** "Adapted" = 3 consecutive CBTmins within this many hours of the destination target (§13.4). */
        const val ADAPT_TOLERANCE: Double = 1.0
    }
}
