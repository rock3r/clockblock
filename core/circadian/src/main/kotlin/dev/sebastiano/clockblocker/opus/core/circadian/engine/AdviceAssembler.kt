package dev.sebastiano.clockblocker.opus.core.circadian.engine

import dev.sebastiano.clockblocker.opus.core.circadian.PlannerConfig
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import kotlin.math.floor

/** Advice before instants and day assignment: planner hours, a type, a reason and an optional detail. */
internal data class RawAdvice(
    val type: AdviceType,
    val interval: Interval,
    val reason: AdviceReason,
    val detail: String? = null,
) {
    val start: Double get() = interval.start
    val end: Double get() = interval.end
}

/**
 * Turns per-cycle windows into a consistent set of cards:
 * 1. [resolve] removes cross-cycle overlaps with the validator's precedence (sleep > avoid > seek > neutral),
 *    so no card contradicts another (e.g. a clamped sleep from one cycle never overlaps the next seek window);
 * 2. [practicality] applies the §12.3 UI filter (merge < 15 min gaps, round to 15 min, seek ≥ 30 min).
 * Neither step changes the science: the ODE validator always runs on the raw cycle windows.
 */
internal class AdviceAssembler(private val config: PlannerConfig) {

    fun resolve(raw: List<RawAdvice>): List<RawAdvice> {
        fun of(type: AdviceType, filter: (RawAdvice) -> Boolean = { true }) = mergeOverlaps(raw.filter { it.type == type && filter(it) })
        val sleep = of(AdviceType.Sleep)
        val sleepIv = sleep.map { it.interval }
        val rest = of(AdviceType.AvoidLight) { it.reason == AdviceReason.RestInFlight }.minusAll(sleepIv)
        val avoid = of(AdviceType.AvoidLight) { it.reason != AdviceReason.RestInFlight }.minusAll(sleepIv + rest.ivs())
        val naps = (of(AdviceType.Nap) + of(AdviceType.OptionalNap)).minusAll(sleepIv)
        val dark = sleepIv + avoid.ivs() + rest.ivs() + naps.ivs()
        val seek = of(AdviceType.SeeBrightLight).minusAll(dark)
        val seeLight = of(AdviceType.SeeLight).minusAll(dark + seek.ivs())
        val avoidCaffeine = of(AdviceType.AvoidCaffeine).minusAll(sleepIv)
        val caffeine = of(AdviceType.Caffeine).minusAll(sleepIv + avoidCaffeine.ivs())
        val fatigue = of(AdviceType.PeakFatigue).minusAll(sleepIv)
        val moments = raw.filter { it.type == AdviceType.Melatonin || it.type == AdviceType.Flight }
        return sleep + rest + avoid + naps + seek + seeLight + avoidCaffeine + caffeine + fatigue + moments
    }

    fun practicality(items: List<RawAdvice>): List<RawAdvice> {
        val gap = config.mergeGapMinutes / 60.0
        val merged = items.groupBy { it.type to it.reason }.flatMap { (key, group) ->
            if (key.first == AdviceType.Flight || key.first.isMoment) return@flatMap group
            val sorted = group.sortedBy { it.start }
            val out = ArrayList<RawAdvice>()
            for (item in sorted) {
                val last = out.lastOrNull()
                val g = if (last == null) Double.NaN else item.start - last.end
                if (last != null && g < gap && (g <= 0 || items.none { conflicts(item.type, it.type) && it.interval.overlaps(Interval(last.end, item.start)) })) {
                    out[out.lastIndex] = last.copy(interval = Interval(last.start, maxOf(last.end, item.end)))
                } else {
                    out += item
                }
            }
            out
        }
        val minSeek = config.minSeekMinutes / 60.0
        return merged.mapNotNull { a ->
            when {
                a.type == AdviceType.Flight -> a
                a.type.isMoment -> round(a.start).let { a.copy(interval = Interval(it, it)) }
                else -> {
                    val r = Interval(round(a.start), round(a.end))
                    when {
                        r.length <= EPS -> null
                        (a.type == AdviceType.SeeBrightLight || a.type == AdviceType.SeeLight) && r.length < minSeek - EPS -> null
                        else -> a.copy(interval = r)
                    }
                }
            }
        }
    }

    /**
     * Rounds to the display grid. Planner hours count from a UTC midnight and every modern UTC offset is a
     * multiple of 15 min, so the UTC grid is also the local grid (Kathmandu +5:45 and Chatham +12:45 included).
     * Monotone, hence it never creates overlaps.
     */
    private fun round(x: Double): Double {
        val perHour = 60.0 / config.roundingMinutes
        return floor(x * perHour + 0.5) / perHour
    }

    private fun List<RawAdvice>.ivs() = map { it.interval }

    private fun List<RawAdvice>.minusAll(cuts: List<Interval>): List<RawAdvice> =
        flatMap { a -> subtract(a.interval, cuts).map { a.copy(interval = it) } }

    /** Union of overlapping/touching windows of one type; the earliest window's reason and detail win. */
    private fun mergeOverlaps(items: List<RawAdvice>): List<RawAdvice> {
        val sorted = items.filter { it.interval.length > EPS }.sortedBy { it.start }
        val out = ArrayList<RawAdvice>()
        for (item in sorted) {
            val last = out.lastOrNull()
            if (last != null && last.reason == item.reason && item.start <= last.end + EPS) {
                out[out.lastIndex] = last.copy(interval = Interval(last.start, maxOf(last.end, item.end)))
            } else if (last != null && item.start < last.end) {
                // different reason, overlapping: keep the earlier card whole, trim the later one
                if (item.end > last.end) out += item.copy(interval = Interval(last.end, item.end))
            } else {
                out += item
            }
        }
        return out
    }

    companion object {
        private val light = setOf(AdviceType.SeeBrightLight, AdviceType.SeeLight)
        private val dark = setOf(AdviceType.AvoidLight, AdviceType.Nap, AdviceType.OptionalNap)

        /** Pairs of card types that must never overlap. */
        fun conflicts(a: AdviceType, b: AdviceType): Boolean {
            if (a == AdviceType.Flight || b == AdviceType.Flight || a.isMoment || b.isMoment) return false
            if (a == b) return false
            if (a == AdviceType.Sleep || b == AdviceType.Sleep) return true
            if ((a in light && (b in dark || b in light)) || (b in light && a in dark)) return true
            return setOf(a, b) == setOf(AdviceType.Caffeine, AdviceType.AvoidCaffeine)
        }
    }
}
