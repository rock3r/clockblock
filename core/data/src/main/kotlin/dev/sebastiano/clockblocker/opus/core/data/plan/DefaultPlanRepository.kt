package dev.sebastiano.clockblocker.opus.core.data.plan

import dev.sebastiano.clockblocker.opus.core.circadian.JetLagPlanner
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.ProfileRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.data.time.AppDispatchers
import dev.sebastiano.clockblocker.opus.core.data.time.Ticker
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * [PlanRepository] that derives plans from trips + profile with the [JetLagPlanner].
 *
 * - Planning runs on [AppDispatchers.default] inside the application [scope], so a screen going away
 *   doesn't cancel a computation that a widget is also waiting for.
 * - Plans are memoised by the *content* of (trip, profile): editing anything produces a new plan, while
 *   re-subscribing, ticking time or unrelated trip edits reuse the cached one. Concurrent requests for the
 *   same inputs share one computation.
 * - [currentPlan] re-evaluates on every [Ticker] tick, so it moves to the next trip when one ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultPlanRepository(
    private val trips: TripRepository,
    private val profiles: ProfileRepository,
    private val planner: JetLagPlanner,
    private val clock: Clock,
    private val ticker: Ticker,
    private val dispatchers: AppDispatchers,
    private val scope: CoroutineScope,
) : PlanRepository {

    private data class Key(val trip: Trip, val profile: UserProfile)

    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<Key, Deferred<Result<JetLagPlan>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Deferred<Result<JetLagPlan>>>) =
            size > CacheSize
    }

    override fun plan(tripId: String): Flow<JetLagPlan?> =
        combine(trips.trip(tripId), profiles.profile) { trip, profile -> trip to profile }
            .distinctUntilChanged()
            .mapLatest { (trip, profile) -> if (trip == null || profile == null) null else planFor(trip, profile) }
            .distinctUntilChanged()

    override val currentPlan: Flow<JetLagPlan?> =
        combine(trips.trips, profiles.profile, ticker.ticks()) { all, profile, _ -> all to profile }
            .mapLatest { (all, profile) -> profile?.let { currentPlan(all, it, clock.instant()) } }
            .distinctUntilChanged()

    /**
     * The plan for [trip] and [profile], computed once per distinct content and then served from memory.
     * Exceptions from the planner propagate to the caller and are not cached.
     */
    suspend fun planFor(trip: Trip, profile: UserProfile): JetLagPlan {
        val key = Key(trip, profile)
        val deferred = mutex.withLock {
            cache.getOrPut(key) {
                scope.async(dispatchers.default, start = CoroutineStart.LAZY) {
                    // Failures are returned, not thrown, so they never cancel the application scope.
                    runCatching { planner.plan(trip, profile, clock.instant()) }
                }
            }
        }
        return deferred.await().onFailure {
            mutex.withLock { if (cache[key] === deferred) cache.remove(key) }
        }.getOrThrow()
    }

    /**
     * Picks the plan relevant at [now]:
     * 1. among trips whose plan window (first plan day … last plan day, always including the flights)
     *    contains [now], the one departing last (a return trip's pre-travel days supersede the outbound
     *    trip's adaptation tail);
     * 2. otherwise the next trip departing after [now];
     * 3. otherwise none.
     */
    suspend fun currentPlan(all: List<Trip>, profile: UserProfile, now: Instant): JetLagPlan? {
        val sorted = all.sortedBy { it.departure }
        var active: JetLagPlan? = null
        for (trip in sorted) {
            if (trip.arrival.plus(MaxPlanTail).isBefore(now)) continue // Long over.
            if (trip.departure.minus(MaxPlanLead).isAfter(now)) break // Can't have started; nor can later ones.
            val plan = planFor(trip, profile)
            if (now in plan.window(trip)) active = plan
        }
        if (active != null) return active
        val next = sorted.firstOrNull { it.departure.isAfter(now) } ?: return null
        return planFor(next, profile)
    }

    companion object {
        /** Distinct (trip, profile) plans kept in memory. */
        const val CacheSize: Int = 32

        /** No plan starts earlier than this before departure. */
        val MaxPlanLead: Duration = Duration.ofDays(14)

        /** No plan runs longer than this after arrival. */
        val MaxPlanTail: Duration = Duration.ofDays(30)
    }
}

/** The span a plan covers: its first day's start to its last day's end, widened to include the flights. */
internal fun JetLagPlan.window(trip: Trip): ClosedRange<Instant> {
    var start = trip.departure
    var end = trip.arrival
    days.firstOrNull()?.let { first ->
        val s = first.date.atStartOfDay(java.time.ZoneId.of(first.zoneId)).toInstant()
        if (s.isBefore(start)) start = s
    }
    days.lastOrNull()?.let { last ->
        val e = last.date.plusDays(1).atStartOfDay(java.time.ZoneId.of(last.zoneId)).toInstant()
        if (e.isAfter(end)) end = e
    }
    allAdvice.forEach {
        if (it.start.isBefore(start)) start = it.start
        if (it.end.isAfter(end)) end = it.end
    }
    return start..end
}
