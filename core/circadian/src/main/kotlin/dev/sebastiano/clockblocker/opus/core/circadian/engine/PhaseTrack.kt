package dev.sebastiano.clockblocker.opus.core.circadian.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sign

/** The estimated body clock over time: offset (hours, unwrapped) and the nearest CBTmin, in planner hours. */
internal sealed interface PhaseTrack {
    fun bodyOffset(t: Double): Double
    fun nearestCbtMin(t: Double): Double

    /** Body clock fixed at [offset] (home-time mode). [t0Clock] = CBTmin on that clock. */
    class Constant(private val offset: Double, private val t0Clock: Double) : PhaseTrack {
        override fun bodyOffset(t: Double) = offset
        override fun nearestCbtMin(t: Double) = nearestOnClock(t, t0Clock - offset)
    }

    /** No plan (|Δ| < MIN_SHIFT): the clock drifts from [start] by [delta] at [ratePerDay] after [arrival]. */
    class Drift(
        private val start: Double,
        private val delta: Double,
        private val arrival: Double,
        private val ratePerDay: Double,
        t0Clock: Double,
    ) : PhaseTrack {
        /** One CBTmin per cycle: the last one on the home clock before arrival, then shifted day by day. */
        private val series: DoubleArray = buildList {
            var c = nearestOnClock(arrival, t0Clock - start).let { if (it > arrival) it - 24.0 else it }
            add(c)
            repeat(if (ratePerDay > 0.0) (abs(delta) / ratePerDay).toInt() + 2 else 0) {
                c += 24.0 - (bodyOffset(c + 24.0) - bodyOffset(c))
                add(c)
            }
        }.toDoubleArray()

        override fun bodyOffset(t: Double): Double {
            if (t <= arrival) return start
            return start + sign(delta) * minOf(abs(delta), (t - arrival) / 24.0 * ratePerDay)
        }

        override fun nearestCbtMin(t: Double) = nearestInSeries(series, t)
    }

    /**
     * Piecewise-linear interpolation between the planned CBTmins (each cycle has `bodyOffset = home + φ`);
     * constant outside. CBTmins continue every 24 h before the first and after the last cycle and across gaps
     * between segments.
     */
    class Cycles(cycles: List<Cycle>, private val initialOffset: Double) : PhaseTrack {
        private val ts = cycles.map { it.t }.toDoubleArray()
        private val bs = cycles.map { it.bodyOffset }.toDoubleArray()
        private val series: DoubleArray = buildList {
            for (i in ts.indices) {
                add(ts[i])
                if (i < ts.lastIndex) {
                    var x = ts[i] + 24.0
                    while (x < ts[i + 1] - 12.0) {
                        add(x)
                        x += 24.0
                    }
                }
            }
        }.toDoubleArray()

        override fun bodyOffset(t: Double): Double {
            if (ts.isEmpty() || t <= ts[0]) return if (ts.isEmpty()) initialOffset else bs[0]
            if (t >= ts.last()) return bs.last()
            val i = upperIndex(ts, t)
            val f = (t - ts[i - 1]) / (ts[i] - ts[i - 1])
            return bs[i - 1] + (bs[i] - bs[i - 1]) * f
        }

        override fun nearestCbtMin(t: Double): Double = if (series.isEmpty()) t else nearestInSeries(series, t)
    }

    companion object {
        /** The instant of the form `cbt + 24 n` nearest to [t]. */
        fun nearestOnClock(t: Double, cbt: Double): Double {
            val base = cbt + 24.0 * floor((t - cbt) / 24.0)
            return if (t - base <= 12.0) base else base + 24.0
        }

        /** Nearest element of a sorted, non-empty CBTmin [series], continued every 24 h beyond both ends. */
        fun nearestInSeries(series: DoubleArray, t: Double): Double {
            if (t <= series[0]) return series[0] - 24.0 * ((series[0] - t) / 24.0).roundToLong()
            if (t >= series.last()) return series.last() + 24.0 * ((t - series.last()) / 24.0).roundToLong()
            val i = upperIndex(series, t)
            return if (t - series[i - 1] <= series[i] - t) series[i - 1] else series[i]
        }

        /** Index of the first element greater than [t] (binary search; `a.lastIndex` if none). */
        fun upperIndex(a: DoubleArray, t: Double): Int {
            var lo = 0
            var hi = a.size - 1
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (a[mid] <= t) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }
}
