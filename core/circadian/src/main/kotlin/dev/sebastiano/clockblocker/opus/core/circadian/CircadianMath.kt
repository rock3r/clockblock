package dev.sebastiano.clockblocker.opus.core.circadian

import kotlin.math.floor

/** Small clock-arithmetic helpers shared by the planner, the validator and the UI. Hours as `Double`. */
object CircadianMath {
    /** Normalises [hours] to (−12, +12] (`docs/science.md` §10.2). `norm12(+14 − (−10)) == 0`. */
    fun norm12(hours: Double): Double {
        var y = (hours + 12.0) % 24.0
        if (y <= 0.0) y += 24.0
        return y - 12.0
    }

    /** [hours] modulo 24 in [0, 24) (Python `%` semantics). */
    fun mod24(hours: Double): Double {
        val r = hours - 24.0 * floor(hours / 24.0)
        return if (r >= 24.0) r - 24.0 else r
    }
}
