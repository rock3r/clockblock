package dev.sebastiano.clockblocker.opus.core.data.plan

import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Trip
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The one rule for which trip is "now": the trip in progress, or else the first upcoming one. The Now tab (through
 * [dev.sebastiano.clockblocker.opus.core.data.PlanRepository.currentPlan], which also feeds widgets, the Now
 * notification and alarms) and the Trips tab both use it, so a trip is never on both tabs and the next trip is never
 * on neither.
 *
 * A trip is **under way** while [isUnderWay]: from the start of its [window] (inclusive) to its end (exclusive). When
 * several are under way (an outbound trip's adaptation tail and its return trip's pre-travel days), the one departing
 * last wins. With none under way, the next trip to depart is "now".
 */
object NowTrip {

    /** Without a plan, a trip counts as under way for this long after landing. */
    val UnplannedSettlingTime: Duration = Duration.ofDays(2)

    /**
     * The span [trip] is under way: with a [plan], its first plan day's start to the end of its last plan day or
     * last advice (the last night's sleep runs past midnight), whichever is later, always covering the flights;
     * without one, take-off to [UnplannedSettlingTime] after landing.
     */
    fun window(trip: Trip, plan: JetLagPlan?): TripWindow =
        plan?.window(trip) ?: TripWindow(trip.departure, trip.arrival.plus(UnplannedSettlingTime))

    fun isUnderWay(trip: Trip, plan: JetLagPlan?, now: Instant): Boolean = now in window(trip, plan)

    /**
     * Picks the "now" entry among [entries] at [now]: the latest-departing one under way (its [window] contains
     * [now]), else the soonest to depart after [now], else null. [trip] and [window] read each entry's trip and
     * window (see [NowTrip.window]).
     */
    fun <T> pick(entries: List<T>, now: Instant, trip: (T) -> Trip, window: (T) -> TripWindow): T? {
        val underWay = entries.filter { now in window(it) }.maxByOrNull { trip(it).departure }
        return underWay ?: entries.filter { trip(it).departure.isAfter(now) }.minByOrNull { trip(it).departure }
    }

    /** [pick] over (trip, optional plan) pairs. */
    fun pick(entries: List<Pair<Trip, JetLagPlan?>>, now: Instant): Pair<Trip, JetLagPlan?>? =
        pick(entries, now, { it.first }, { window(it.first, it.second) })
}

/** A half-open span of time, [start] inclusive to [end] exclusive. */
data class TripWindow(val start: Instant, val end: Instant) {
    operator fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)
}

/**
 * The span a plan covers: its first day's start to its last day's end, widened to include the flights and every
 * advice block (the last night's sleep ends the morning after the last plan day).
 */
fun JetLagPlan.window(trip: Trip): TripWindow {
    var start = trip.departure
    var end = trip.arrival
    days.firstOrNull()?.let { first ->
        val s = first.date.atStartOfDay(ZoneId.of(first.zoneId)).toInstant()
        if (s.isBefore(start)) start = s
    }
    days.lastOrNull()?.let { last ->
        val e = last.date.plusDays(1).atStartOfDay(ZoneId.of(last.zoneId)).toInstant()
        if (e.isAfter(end)) end = e
    }
    allAdvice.forEach {
        if (it.start.isBefore(start)) start = it.start
        if (it.end.isAfter(end)) end = it.end
    }
    return TripWindow(start, end)
}
