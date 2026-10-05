package dev.sebastiano.clockblocker.opus.core.circadian.engine

import dev.sebastiano.clockblocker.opus.core.model.DayKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A run of flights with layovers < 24 h; stays between groups are calendar days at the group's arrival zone. */
internal data class TravelGroup(
    val dep: Instant,
    val arr: Instant,
    val fromZone: ZoneId,
    val toZone: ZoneId,
    /** Kind of a stay day spanning [start, end) after this group (Arrival or Adapted). */
    val stayKind: (start: Instant, end: Instant) -> DayKind,
)

internal data class DaySpan(
    val index: Int,
    val kind: DayKind,
    val date: LocalDate,
    val zone: ZoneId,
    val start: Instant,
    val end: Instant,
)

/**
 * Maps the plan onto calendar days in the zone the traveller is in (§10.5):
 * - pre-trip days are home-zone calendar days;
 * - a Travel day runs from local midnight of the departure date to the arrival, or to the end of the arrival
 *   date when landing at/after [ARRIVAL_DAY_CUTOFF] (an evening arrival does not get its own short day);
 * - later days are calendar days at the destination, the first one starting at the arrival.
 * Indices are sequential and 0 is the (first) Travel day. `JetLagPlan.daySpans()` reconstructs the same spans.
 */
internal object DayBuilder {
    val ARRIVAL_DAY_CUTOFF: LocalTime = LocalTime.of(18, 0)

    fun build(origin: ZoneId, groups: List<TravelGroup>, minStart: Instant, maxEnd: Instant): List<DaySpan> {
        data class Raw(val kind: DayKind, val date: LocalDate, val zone: ZoneId, val start: Instant, val end: Instant)

        val out = ArrayList<Raw>()
        var zone = origin
        var cursor = startOfDay(minOf(minStart, groups.first().dep), origin)
        var stayKind: (Instant, Instant) -> DayKind = { _, _ -> DayKind.PreTrip }

        fun stay(until: Instant) {
            var t = cursor
            while (t < until) {
                val date = t.atZone(zone).toLocalDate()
                val next = minOf(startOfDay(date.plusDays(1), zone), until)
                out += Raw(stayKind(t, next), date, zone, t, next)
                t = next
            }
            cursor = maxOf(cursor, until)
        }

        for (g in groups) {
            val depDate = g.dep.atZone(zone).toLocalDate()
            val s = maxOf(startOfDay(depDate, zone), cursor)
            stay(s)
            val arrLocal = g.arr.atZone(g.toZone)
            val e = if (arrLocal.toLocalTime() < ARRIVAL_DAY_CUTOFF) g.arr else startOfDay(arrLocal.toLocalDate().plusDays(1), g.toZone)
            out += Raw(DayKind.Travel, depDate, zone, s, maxOf(e, s.plusSeconds(1)))
            cursor = maxOf(e, s.plusSeconds(1))
            zone = g.toZone
            stayKind = g.stayKind
        }
        // always at least one day after the last travel group, and the day containing maxEnd
        val lastDate = maxOf(maxEnd, cursor).atZone(zone).toLocalDate()
        stay(startOfDay(lastDate.plusDays(1), zone))

        val travel0 = out.indexOfFirst { it.kind == DayKind.Travel }
        return out.mapIndexed { i, r -> DaySpan(i - travel0, r.kind, r.date, r.zone, r.start, r.end) }
    }

    fun startOfDay(date: LocalDate, zone: ZoneId): Instant = date.atStartOfDay(zone).toInstant()

    fun startOfDay(instant: Instant, zone: ZoneId): Instant = startOfDay(instant.atZone(zone).toLocalDate(), zone)
}
