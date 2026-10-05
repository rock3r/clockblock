package dev.sebastiano.clockblocker.opus.core.circadian.ode

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A light-driven model of the human circadian pacemaker (`docs/science.md` §4).
 *
 * Time is in hours, light in photopic lux at the eye. Implementations are stateless; the state vector is
 * passed in and out so a single instance can be shared freely.
 */
sealed interface CircadianModel {
    /** Size of the state vector. */
    val dim: Int

    /** Published initial condition (Arcascope `circadian`), roughly entrained at midnight. */
    val initialState: DoubleArray

    /** Photoreceptor activation rate α(I) of Kronauer's Process L. Lux is clamped to [0, 100 000]. */
    fun alpha(lux: Double): Double

    /** Writes d(state)/dt into [out] for constant light [lux] (lux is clamped by [alpha]). */
    fun deriv(state: DoubleArray, lux: Double, out: DoubleArray)

    /** Times (same clock as the trajectory) of every core body temperature minimum in [trajectory]. */
    fun cbtMinTimes(trajectory: Trajectory): List<Double>

    companion object {
        /** Clamp applied to light before conversion to drive (§4.4). */
        const val MAX_LUX: Double = 100_000.0

        internal fun clampLux(lux: Double): Double = if (lux.isNaN() || lux <= 0.0) 0.0 else minOf(lux, MAX_LUX)
    }
}

/**
 * Forger, Jewett & Kronauer 1999 "simpler" van der Pol model (doi 10.1177/074873099129000867), state
 * `(x, xc, n)`. CBTmin = local minimum of `x` (Arcascope convention, §4.1).
 */
data object Forger99 : CircadianModel {
    const val TAUX: Double = 24.2
    const val MU: Double = 0.23
    const val K: Double = 0.55
    const val G: Double = 33.75
    const val ALPHA0: Double = 0.05
    const val BETA: Double = 0.0075
    const val P: Double = 0.5
    const val I0: Double = 9500.0

    /** Minimum separation between two CBTmins when de-duplicating shallow minima (§4.5). */
    const val MIN_PEAK_SEPARATION_HOURS: Double = 13.0

    override val dim: Int = 3
    override val initialState: DoubleArray get() = doubleArrayOf(-0.0843259, -1.09607546, 0.45584306)

    private val w2 = (24.0 / (0.99669 * TAUX)).let { it * it }

    override fun alpha(lux: Double): Double {
        val l = CircadianModel.clampLux(lux)
        return if (l > 0.0) ALPHA0 * (if (P == 0.5) sqrt(l / I0) else (l / I0).pow(P)) else 0.0
    }

    override fun deriv(state: DoubleArray, lux: Double, out: DoubleArray) {
        val x = state[0]
        val xc = state[1]
        val n = state[2]
        val a = alpha(lux)
        val bHat = G * (1 - n) * a
        val b = bHat * (1 - 0.4 * x) * (1 - 0.4 * xc)
        out[0] = PI / 12 * (xc + b)
        out[1] = PI / 12 * (MU * (xc - 4.0 / 3.0 * xc * xc * xc) - x * (w2 + K * b))
        out[2] = 60.0 * (a * (1 - n) - BETA * n)
    }

    /** Discrete minima of x, parabolic refinement, then greedy de-duplication (deepest first, ≥13 h apart). */
    override fun cbtMinTimes(trajectory: Trajectory): List<Double> {
        val times = trajectory.times
        val states = trajectory.states
        val candidates = ArrayList<Pair<Double, Double>>()
        for (i in 1 until states.size - 1) {
            val a = states[i - 1][0]
            val b = states[i][0]
            val c = states[i + 1][0]
            if (b < a && b <= c) {
                val step = times[i + 1] - times[i]
                val denom = a - 2 * b + c
                val off = if (denom != 0.0) 0.5 * (a - c) / denom else 0.0
                candidates += (times[i] + off * step) to b
            }
        }
        val kept = ArrayList<Double>()
        for ((t, _) in candidates.sortedBy { it.second }) {
            if (kept.all { kotlin.math.abs(t - it) >= MIN_PEAK_SEPARATION_HOURS }) kept += t
        }
        return kept.sorted()
    }
}

/**
 * Hannay, Booth & Forger 2019 single-population macroscopic model (doi 10.1177/0748730419878298), state
 * `(R, ψ, n)`. CBTmin = ψ crossing π (mod 2π), linearly interpolated (§4.2).
 */
data object Hannay19 : CircadianModel {
    const val TAU: Double = 23.84
    const val KC: Double = 0.06358
    const val GAMMA: Double = 0.024
    const val BETA1: Double = -0.09318
    const val A1: Double = 0.3855
    const val A2: Double = 0.1977
    const val BETA_L1: Double = -0.0026
    const val BETA_L2: Double = -0.957756
    const val SIGMA: Double = 0.0400692
    const val G: Double = 33.75
    const val ALPHA0: Double = 0.05
    const val DELTA: Double = 0.0075
    const val P: Double = 1.5
    const val I0: Double = 9325.0

    override val dim: Int = 3
    override val initialState: DoubleArray get() = doubleArrayOf(0.82041911, 1.71383697, 0.52318122)

    private val omega = 2 * PI / TAU
    private val couplingCos = KC / 2 * cos(BETA1)
    private val couplingSin = KC / 2 * sin(BETA1)

    override fun alpha(lux: Double): Double {
        val l = CircadianModel.clampLux(lux)
        if (l <= 0.0) return 0.0
        val lp = if (P == 1.5) l * sqrt(l) else l.pow(P)
        return ALPHA0 * lp / (lp + I0)
    }

    override fun deriv(state: DoubleArray, lux: Double, out: DoubleArray) {
        val r = state[0]
        val psi = state[1]
        val n = state[2]
        val a = alpha(lux)
        val r4 = r * r * r * r
        val r8 = r4 * r4
        var lR = 0.0
        var lPsi = 0.0
        if (a != 0.0) {
            val bHat = G * (1 - n) * a
            lR = A1 / 2 * bHat * (1 - r4) * cos(psi + BETA_L1) + A2 / 2 * bHat * r * (1 - r8) * cos(2 * psi + BETA_L2)
            lPsi = SIGMA * bHat - A1 / 2 * bHat * (r * r * r + 1 / r) * sin(psi + BETA_L1) -
                A2 / 2 * bHat * (1 + r8) * sin(2 * psi + BETA_L2)
        }
        out[0] = -GAMMA * r + couplingCos * r * (1 - r4) + lR
        out[1] = omega + couplingSin * (1 + r4) + lPsi
        out[2] = 60.0 * (a * (1 - n) - DELTA * n)
    }

    override fun cbtMinTimes(trajectory: Trajectory): List<Double> {
        val times = trajectory.times
        val states = trajectory.states
        val out = ArrayList<Double>()
        for (i in 0 until states.size - 1) {
            val p0 = states[i][1]
            val p1 = states[i + 1][1]
            val k0 = floor((p0 - PI) / (2 * PI))
            val k1 = floor((p1 - PI) / (2 * PI))
            if (k1 > k0) {
                val target = PI + 2 * PI * k1
                val fr = (target - p0) / (p1 - p0)
                out += times[i] + fr * (times[i + 1] - times[i])
            }
        }
        return out
    }
}
