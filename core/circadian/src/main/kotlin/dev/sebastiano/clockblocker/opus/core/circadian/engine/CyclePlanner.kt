package dev.sebastiano.clockblocker.opus.core.circadian.engine

import dev.sebastiano.clockblocker.opus.core.circadian.ChronotypeClass
import dev.sebastiano.clockblocker.opus.core.circadian.CircadianMath.mod24
import dev.sebastiano.clockblocker.opus.core.circadian.CircadianMath.norm12
import dev.sebastiano.clockblocker.opus.core.circadian.PlannerConfig
import kotlin.math.abs
import kotlin.math.min

/** One flight in planner hours (UTC hours from the reference midnight) with the UTC offsets at each end. */
internal data class LegHours(val dep: Double, val arr: Double, val depOffset: Double, val arrOffset: Double)

internal enum class Direction { Advance, Delay }

/** Where the traveller is at a cycle's CBTmin (`where` in the reference). */
internal enum class Where { Home, Flight, Dest }

/**
 * One body-clock cycle (CBTmin to CBTmin, §10.5) of the reference planner, all in UTC hours.
 *
 * @property bodyOffset the body clock expressed as a UTC offset at [t] (hours, unwrapped).
 * @property sleepClampHours `dev − dev_c` of the destination practicality clamp (0 if not clamped).
 */
internal data class Cycle(
    val k: Int,
    val t: Double,
    val phi: Double,
    val where: Where,
    val direction: Direction,
    val bodyOffset: Double,
    val sleep: Interval,
    val sleepParts: List<Interval>,
    val sleepClampHours: Double,
    val sleepTruncated: Boolean,
    val seek: List<Interval>,
    val avoid: List<Interval>,
    val melatonin: Double?,
    val caffeineOk: Interval? = null,
    val caffeineUse: List<Interval> = emptyList(),
    val nap: Interval? = null,
)

/**
 * Input for one planning segment (§10.3: a journey to a destination where the traveller stays ≥ 72 h).
 *
 * @property bodyOffset the clock the body is entrained to at the start, as a UTC offset (`home_off`).
 * @property startT continue from this CBTmin instead of searching the pre-flight window (multi-segment).
 * @property stopAt stop before emitting a cycle whose CBTmin is at/after this time.
 * @property extraSleepBlocks additional no-sleep intervals (layovers, return flight).
 * @property holdIntervals post-departure cycles whose CBTmin falls in here do not shift (opposite stopovers).
 */
internal data class SegmentInput(
    val bodyOffset: Double,
    val legs: List<LegHours>,
    val habitualOnset: Double,
    val habitualWake: Double,
    val chronotype: ChronotypeClass,
    val preDays: Int,
    val useMelatonin: Boolean,
    val forcedDirection: Direction? = null,
    val startT: Double? = null,
    val stopAt: Double? = null,
    val previousSleep: Interval? = null,
    val extraSleepBlocks: List<Interval> = emptyList(),
    val holdIntervals: List<Interval> = emptyList(),
)

internal data class SegmentPlan(
    val delta: Double,
    val direction: Direction,
    val targetPhi: Double,
    val t0Clock: Double,
    val psiOn: Double,
    val psiWake: Double,
    val sleepDuration: Double,
    val preCapTotal: Double,
    val cycles: List<Cycle>,
    /** True if the plan reached |φ* − φ| ≤ DONE_TOL at the destination. */
    val done: Boolean,
    /** CBTmin and φ of the next (not emitted) cycle. */
    val nextT: Double,
    val nextPhi: Double,
)

/**
 * Faithful Kotlin port of `docs/research/reference/planner.py` (§12.3), generalised with a few optional
 * inputs for multi-segment itineraries. With default [SegmentInput] extras it prints exactly what the
 * reference prints (see `CyclePlannerGoldenTest`).
 */
internal class CyclePlanner(private val config: PlannerConfig) {

    fun defaultDirection(eastwardHours: Double, chronotype: ChronotypeClass): Direction =
        if (eastwardHours <= config.advanceThreshold(chronotype)) Direction.Advance else Direction.Delay

    fun plan(input: SegmentInput): SegmentPlan {
        val c = config
        val dep = input.legs.first().dep
        val arr = input.legs.last().arr
        val destOff = input.legs.last().arrOffset
        val delta = norm12(destOff - input.bodyOffset)
        val a = mod24(delta)
        val direction = input.forcedDirection ?: defaultDirection(a, input.chronotype)
        val target = if (direction == Direction.Advance) a else a - 24.0

        val rawSd = mod24(input.habitualWake - input.habitualOnset)
        val sd = rawSd.coerceIn(MIN_SLEEP, MAX_SLEEP)
        val t0Clock = cbtMinClock(input.habitualOnset, input.habitualOnset + sd, c.cbtFromMidSleep(input.chronotype))
        val psiOn = mod24(t0Clock - input.habitualOnset)
        val psiWake = if (sd == rawSd) mod24(input.habitualWake - t0Clock) else sd - psiOn

        val preDays = input.preDays
        var t = input.startT ?: run {
            var x = t0Clock - input.bodyOffset
            while (x < dep - 24.0 * (preDays + 1)) x += 24.0
            while (x > dep - 24.0 * preDays) x -= 24.0
            x
        }
        val sgn = if (target > 0) 1.0 else -1.0
        var preCapTotal = c.preMaxShift
        if (sgn < 0) {
            var tl = t
            while (tl + 24.0 < dep) tl += 24.0
            preCapTotal = maxOf(0.0, min(preCapTotal, (dep - c.preDepartureWake) - (tl + psiWake)))
        }
        val blocks = listOf(
            Interval(dep - c.preDepartureWake, dep + c.postDepartureNoSleep),
            Interval(arr - c.arrivalSleepEnd, arr + c.postArrivalNoSleep),
        ) + input.extraSleepBlocks

        var phi = 0.0
        val cycles = ArrayList<Cycle>()
        var done = false
        var k = 0
        while (k < c.maxCycles) {
            if (input.stopAt != null && t >= input.stopAt) break
            val where = if (t < dep) Where.Home else if (t < arr) Where.Flight else Where.Dest
            // 1. sleep
            val onAligned = t - psiOn
            var s0: Double
            var s1: Double
            var clamp = 0.0
            if (where != Where.Dest) {
                s0 = onAligned
                s1 = t + psiWake
            } else {
                val onLoc = mod24(onAligned + destOff)
                val dev = norm12(onLoc - input.habitualOnset)
                val devC = dev.coerceIn(-c.maxSleepDeviation, c.maxSleepDeviation)
                s0 = onAligned - dev + devC
                s1 = s0 + sd
                if (abs(dev - devC) > EPS) clamp = dev - devC
            }
            val parts = subtract(Interval(s0, s1), blocks)
            val truncated = parts.isNotEmpty() && abs(parts.sumOf { it.length } - (s1 - s0)) > EPS
            val sleepParts = parts.ifEmpty { listOf(Interval(s0, s0)) }
            val sleep = if (parts.isNotEmpty()) Interval(parts.first().start, parts.last().end) else Interval(s0, s0)
            // 2. light windows (Khalsa 2003)
            val (seekRaw, avoidRaw) = if (direction == Direction.Advance) {
                Interval(t, t + c.seekLength) to Interval(t - c.avoidLength, t)
            } else {
                Interval(t - c.seekLength, t) to Interval(t, t + c.avoidLength)
            }
            val seek = subtract(seekRaw, sleepParts)
            val avoid = subtract(avoidRaw, sleepParts)
            // 3. melatonin
            var mel: Double? = null
            val notDone = abs(target - phi) > c.doneTolerance
            val prevSleep = cycles.lastOrNull()?.sleep ?: input.previousSleep
            val asleep = { x: Double -> x in sleep || (prevSleep != null && x in prevSleep) }
            if (input.useMelatonin && notDone && direction == Direction.Advance) {
                var m: Double? = t + c.melatoninAdvanceOffset
                if (asleep(m!!)) {
                    val lo = t + c.melatoninWindowStart
                    val hi = t + c.melatoninWindowEnd
                    m = if (sleep.start in lo..hi) sleep.start - 0.25 else null
                }
                mel = m
            } else if (input.useMelatonin && notDone && direction == Direction.Delay && c.melatoninForDelay) {
                val m = t + c.melatoninDelayOffset
                mel = when {
                    !asleep(m) -> m
                    sleep.end in (t + 2.0)..(t + 5.0) -> sleep.end
                    else -> null
                }
            }
            cycles += Cycle(
                k = k, t = t, phi = phi, where = where, direction = direction, bodyOffset = input.bodyOffset + phi,
                sleep = sleep, sleepParts = sleepParts, sleepClampHours = clamp, sleepTruncated = truncated,
                seek = seek, avoid = avoid, melatonin = mel,
            )
            if (abs(target - phi) <= c.doneTolerance && where == Where.Dest) {
                done = true
                break
            }
            // 4. step to the next cycle
            var cap: Double
            if (t < dep) {
                cap = if (direction == Direction.Advance) c.preAdvanceCap else if (c.lightBox) c.preDelayCapLightBox else c.preDelayCap
                if (preDays == 0) cap = 0.0
                cap = min(cap, maxOf(0.0, preCapTotal - abs(phi)))
                val tn = t + 24.0 - sgn * cap
                if (tn < dep && sgn < 0) {
                    val latestWake = dep - c.preDepartureWake
                    if (tn + psiWake > latestWake) cap = maxOf(0.0, cap - ((tn + psiWake) - latestWake))
                }
            } else {
                cap = if (direction == Direction.Advance) c.postAdvanceCap else c.postDelayCap
                if (input.holdIntervals.containsPoint(t)) cap = 0.0
            }
            val step = sgn * min(cap, abs(target - phi))
            phi += step
            t = t + 24.0 - step
            k++
        }
        return SegmentPlan(
            delta = delta, direction = direction, targetPhi = target, t0Clock = t0Clock, psiOn = psiOn,
            psiWake = psiWake, sleepDuration = sd, preCapTotal = preCapTotal,
            cycles = fillCaffeineAndNaps(cycles), done = done, nextT = t, nextPhi = phi,
        )
    }

    /**
     * Step 5 of §12.3: caffeine windows and naps for each waking period (between cycle i's wake and cycle i+1's
     * sleep onset). Works across segment boundaries; the last cycle gets none.
     */
    fun fillCaffeineAndNaps(cycles: List<Cycle>): List<Cycle> = cycles.mapIndexed { i, cyc ->
        if (i == cycles.lastIndex) return@mapIndexed cyc
        val nextCyc = cycles[i + 1]
        val cut = if (cyc.direction == Direction.Advance) config.caffeineCutoffAdvance else config.caffeineCutoff
        val w = cyc.sleep.end
        val s = nextCyc.sleep.start
        val ok = if (s - cut > w) Interval(w, s - cut) else null
        val uses = listOf(cyc, nextCyc).mapNotNull { c2 ->
            val a = maxOf(c2.t - 7.0, w)
            val b = minOf(c2.t + 3.0, s - cut)
            if (b - a > 0.25) Interval(a, b) else null
        }
        val lo = w + 1.0
        val hi = s - config.napMinBeforeSleep
        var nap: Interval? = null
        if (hi - lo > config.napLength) {
            val seeks = cyc.seek + nextCyc.seek
            val avoid = (cyc.avoid + nextCyc.avoid).sortedWith(compareBy<Interval> { it.start }.thenBy { it.end })
            for (iv in avoid) {
                val a2 = maxOf(iv.start, lo)
                val b2 = minOf(iv.end, hi)
                if (b2 - a2 >= config.napLength) {
                    nap = Interval(a2, a2 + config.napLength)
                    break
                }
            }
            if (nap == null && cyc.direction == Direction.Delay) {
                // siesta ~7 h after wake when delaying (Eastman & Burgess 2009)
                val x = w + 7.0
                if (x in lo..hi && !seeks.containsPoint(x)) nap = Interval(x, x + config.napLength)
            }
        }
        cyc.copy(caffeineOk = ok, caffeineUse = uses, nap = nap)
    }

    companion object {
        /** Sleep durations outside this range are clamped (robustness against nonsense input). */
        const val MIN_SLEEP: Double = 3.0
        const val MAX_SLEEP: Double = 14.0

        /** T0 on the home clock = mid-sleep + chronotype offset (§8.2). */
        fun cbtMinClock(habitualOnset: Double, habitualWake: Double, offset: Double): Double {
            val sd = mod24(habitualWake - habitualOnset)
            return mod24(habitualOnset + sd / 2.0 + offset)
        }
    }
}
