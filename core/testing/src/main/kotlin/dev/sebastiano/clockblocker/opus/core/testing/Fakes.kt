package dev.sebastiano.clockblocker.opus.core.testing

import dev.sebastiano.clockblocker.opus.core.data.AdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.data.PlaceSearch
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.ProfileRepository
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.data.places.PlaceIndex
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [ProfileRepository]. Starts with [initial] (null = onboarding not done).
 * Inspect [current] / [saveCount], or drive it with [set].
 */
class FakeProfileRepository(initial: UserProfile? = null) : ProfileRepository {
    private val state = MutableStateFlow(initial)
    override val profile: Flow<UserProfile?> = state

    val current: UserProfile? get() = state.value
    var saveCount: Int = 0
        private set

    override suspend fun save(profile: UserProfile) {
        saveCount++
        state.value = profile
    }

    /** Sets the profile without counting it as a user save. */
    fun set(profile: UserProfile?) {
        state.value = profile
    }

    companion object {
        /** Onboarded with the demo profile. */
        fun onboarded(profile: UserProfile = DemoData.profile) = FakeProfileRepository(profile)
    }
}

/** In-memory [TripRepository], sorted by departure like the real one. */
class FakeTripRepository(initial: List<Trip> = emptyList()) : TripRepository {
    private val state = MutableStateFlow(initial.associateBy { it.id })

    override val trips: Flow<List<Trip>> = state.map(::sorted).distinctUntilChanged()

    override fun trip(id: String): Flow<Trip?> = state.map { it[id] }.distinctUntilChanged()

    /** Snapshot, sorted by departure. */
    val current: List<Trip> get() = sorted(state.value)

    /** Ids passed to [delete], in order. */
    val deletedIds: MutableList<String> = mutableListOf()

    override suspend fun upsert(trip: Trip) {
        state.update { it + (trip.id to trip) }
    }

    override suspend fun delete(id: String) {
        deletedIds += id
        state.update { it - id }
    }

    fun setTrips(trips: List<Trip>) {
        state.value = trips.associateBy { it.id }
    }

    private fun sorted(map: Map<String, Trip>) = map.values.sortedWith(compareBy<Trip> { it.departure }.thenBy { it.id })

    companion object {
        /** Pre-filled with [DemoData.trips]. */
        fun withDemoTrips() = FakeTripRepository(DemoData.trips())
    }
}

/** In-memory [SettingsRepository]. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    val current: AppSettings get() = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.update(transform)
    }

    fun set(settings: AppSettings) {
        state.value = settings
    }
}

/** In-memory [AdviceLogRepository]; re-logging an advice replaces its outcome, like the real one. */
class FakeAdviceLogRepository(initial: Map<String, List<AdviceLog>> = emptyMap()) : AdviceLogRepository {
    private val state = MutableStateFlow(initial)

    override fun logs(tripId: String): Flow<List<AdviceLog>> = state.map { it[tripId].orEmpty() }.distinctUntilChanged()

    /** Snapshot for one trip. */
    fun current(tripId: String): List<AdviceLog> = state.value[tripId].orEmpty()

    /** Outcome logged for [adviceId] in [tripId], if any. */
    fun outcomeOf(tripId: String, adviceId: String): AdviceOutcome? =
        current(tripId).lastOrNull { it.adviceId == adviceId }?.outcome

    override suspend fun log(tripId: String, adviceId: String, outcome: AdviceOutcome) {
        state.update { all ->
            all + (tripId to (all[tripId].orEmpty().filterNot { it.adviceId == adviceId } + AdviceLog(adviceId, outcome)))
        }
    }

    override suspend fun clear(tripId: String, adviceId: String) {
        state.update { all ->
            val entries = all[tripId] ?: return@update all
            all + (tripId to entries.filterNot { it.adviceId == adviceId })
        }
    }

    override suspend fun logIfAbsent(tripId: String, adviceId: String, outcome: AdviceOutcome): Boolean {
        var logged = false
        state.update { all ->
            val entries = all[tripId].orEmpty()
            logged = entries.none { it.adviceId == adviceId }
            if (logged) all + (tripId to entries + AdviceLog(adviceId, outcome)) else all
        }
        return logged
    }

    override suspend fun replaceAll(tripId: String, entries: List<AdviceLog>) {
        val unique = entries.asReversed().distinctBy { it.adviceId }.asReversed()
        state.update { all -> if (unique.isEmpty()) all - tripId else all + (tripId to unique) }
    }
}

/**
 * [PlanRepository] with plans set by hand. [currentPlan] starts as `current` (default: the first plan) and
 * changes only through [setCurrentPlan].
 *
 * Prefer `DefaultPlanRepository` + [FakeJetLagPlanner] when the test is about derivation itself.
 */
class FakePlanRepository(
    plans: List<JetLagPlan> = emptyList(),
    current: JetLagPlan? = plans.firstOrNull(),
) : PlanRepository {
    private val plansState = MutableStateFlow(plans.associateBy { it.tripId })
    private val currentState = MutableStateFlow(current)

    override fun plan(tripId: String): Flow<JetLagPlan?> = plansState.map { it[tripId] }.distinctUntilChanged()

    override val currentPlan: Flow<JetLagPlan?> = currentState

    fun setPlan(plan: JetLagPlan) {
        plansState.update { it + (plan.tripId to plan) }
    }

    fun removePlan(tripId: String) {
        plansState.update { it - tripId }
    }

    fun setCurrentPlan(plan: JetLagPlan?) {
        plan?.let(::setPlan)
        currentState.value = plan
    }

    companion object {
        /** Plans for [DemoData.trips] from [FakeJetLagPlanner], first one current. */
        fun withDemoPlans(): FakePlanRepository {
            val planner = FakeJetLagPlanner()
            return FakePlanRepository(DemoData.trips().map { planner.plan(it, DemoData.profile, DemoData.Now) })
        }
    }
}

/**
 * [PlaceSearch] over a small in-memory list using the production ranking ([PlaceIndex]), so "lon" →
 * London airports behaves like the app. Defaults to [DemoData.places].
 *
 * @param latencyMillis simulated search latency, to exercise loading states.
 */
class FakePlaceSearch(
    places: List<Place> = DemoData.places,
    var latencyMillis: Long = 0,
) : PlaceSearch {
    private val index = PlaceIndex.of(places)

    /** Every query received, in order. */
    val queries: MutableList<String> = mutableListOf()

    override suspend fun search(query: String, limit: Int): List<Place> {
        queries += query
        if (latencyMillis > 0) delay(latencyMillis)
        return index.search(query, limit)
    }

    override suspend fun byCode(code: String): Place? = index.byCode(code)
}
