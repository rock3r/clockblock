package dev.sebastiano.clockblocker.opus.core.circadian.ode

/**
 * A sampled model trajectory. `times[i]` is the time of `states[i]`; `times[0]` is the start state.
 */
class Trajectory(val times: DoubleArray, val states: Array<DoubleArray>) {
    init {
        require(times.size == states.size) { "times and states must have the same length" }
    }

    val size: Int get() = times.size
}

/** Classical fourth-order Runge–Kutta integration with piecewise-constant light (§4.5). */
object Rk4 {
    /** Default step: 0.1 h (6 min). Stable for all lux up to 100 000 for both models. */
    const val DEFAULT_DT: Double = 0.1

    /**
     * Integrates [model] for [steps] steps of [dt] hours from [s0] at [t0]. Light is sampled once at the start
     * of each step. Step times are computed as `t0 + i * dt` from an integer counter (never `t += dt`, which
     * shifts light transitions by a step through accumulated rounding).
     *
     * @param firstStep index of the first step on the grid `t0 + i * dt`, so that a run can be continued
     * bit-identically from a cached state.
     * @param onStep called after every step with the step end time and the (reused!) state array.
     * @return the final state.
     */
    inline fun integrate(
        model: CircadianModel,
        s0: DoubleArray,
        t0: Double,
        steps: Int,
        dt: Double,
        lux: (Double) -> Double,
        firstStep: Int = 0,
        onStep: (t: Double, state: DoubleArray) -> Unit = { _, _ -> },
    ): DoubleArray {
        val dim = model.dim
        val s = s0.copyOf()
        val k1 = DoubleArray(dim)
        val k2 = DoubleArray(dim)
        val k3 = DoubleArray(dim)
        val k4 = DoubleArray(dim)
        val tmp = DoubleArray(dim)
        for (i in firstStep until firstStep + steps) {
            val l = lux(t0 + i * dt)
            model.deriv(s, l, k1)
            for (j in 0 until dim) tmp[j] = s[j] + dt / 2 * k1[j]
            model.deriv(tmp, l, k2)
            for (j in 0 until dim) tmp[j] = s[j] + dt / 2 * k2[j]
            model.deriv(tmp, l, k3)
            for (j in 0 until dim) tmp[j] = s[j] + dt * k3[j]
            model.deriv(tmp, l, k4)
            for (j in 0 until dim) s[j] += dt / 6 * (k1[j] + 2 * k2[j] + 2 * k3[j] + k4[j])
            onStep(t0 + (i + 1) * dt, s)
        }
        return s
    }

    /** Like [integrate] but records every state (including the start) into a [Trajectory]. */
    fun simulate(
        model: CircadianModel,
        s0: DoubleArray,
        t0: Double,
        steps: Int,
        dt: Double = DEFAULT_DT,
        lux: (Double) -> Double,
    ): Trajectory {
        val times = DoubleArray(steps + 1)
        val states = arrayOfNulls<DoubleArray>(steps + 1)
        times[0] = t0
        states[0] = s0.copyOf()
        var i = 0
        integrate(model, s0, t0, steps, dt, lux) { t, s ->
            i++
            times[i] = t
            states[i] = s.copyOf()
        }
        @Suppress("UNCHECKED_CAST")
        return Trajectory(times, states as Array<DoubleArray>)
    }
}
