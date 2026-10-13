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
     * The plan of the [NowTrip] at [now] (the trip under way, latest departure first, or else the next to depart),
     * or null. Only trips that could be under way are planned to decide: one departing more than [MaxPlanLead] from
     * now, or landed more than [MaxPlanTail] ago, can't be, whatever its plan.
     */
    suspend fun currentPlan(all: List<Trip>, profile: UserProfile, now: Instant): JetLagPlan? {
        val entries = all.map { trip ->
            val couldBeUnderWay = !trip.arrival.plus(MaxPlanTail).isBefore(now) && !trip.departure.minus(MaxPlanLead).isAfter(now)
            trip to if (couldBeUnderWay) planFor(trip, profile) else null
        }
        val (trip, plan) = NowTrip.pick(entries, now) ?: return null
        return plan ?: planFor(trip, profile)
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
