package dev.sebastiano.clockblocker.opus.feature.trips.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.data.time.Ticker
import dev.sebastiano.clockblocker.opus.core.data.trip.TripTitleSuggester
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

/** What the trips list renders. */
data class TripsUiState(
    val loading: Boolean = true,
    val inProgress: List<TripSummary> = emptyList(),
    val upcoming: List<TripSummary> = emptyList(),
    val past: List<TripSummary> = emptyList(),
    /** Target of the FAB menu's "Return trip" shortcut, if any. */
    val returnCandidate: TripSummary? = null,
) {
    val isEmpty: Boolean get() = !loading && inProgress.isEmpty() && upcoming.isEmpty() && past.isEmpty()

    fun section(phase: TripPhase): List<TripSummary> = when (phase) {
        TripPhase.InProgress -> inProgress
        TripPhase.Upcoming -> upcoming
        TripPhase.Past -> past
    }
}

/** One-off things the list screen reacts to (navigation, snackbars). */
sealed interface TripsEvent {
    data class OpenTrip(val tripId: String) : TripsEvent

    /** Shown as "Trip deleted · Undo"; [trip] is what Undo restores. */
    data class Deleted(val trip: Trip) : TripsEvent

    data class Duplicated(val tripId: String) : TripsEvent
}

/**
 * Trips list: every trip with its plan, grouped into In progress / Upcoming / Past and re-evaluated as time
 * passes (the [Ticker]), plus delete-with-undo, duplicate and the demo trip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Inject
@ViewModelKey(TripsViewModel::class)
@ContributesIntoMap(AppScope::class)
class TripsViewModel(
    private val trips: TripRepository,
    private val plans: PlanRepository,
    private val clock: Clock,
    private val ticker: Ticker,
    private val titles: TripTitleSuggester,
) : ViewModel() {

    private val events = Channel<TripsEvent>(Channel.BUFFERED)

    /** Consumed once by the screen. */
    val eventFlow: Flow<TripsEvent> = events.receiveAsFlow()

    val state: StateFlow<TripsUiState> = combine(tripsWithPlans(), ticker.ticks()) { pairs, now ->
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val summaries = pairs.map { (trip, plan) ->
            TripSummaries.summarize(trip, plan, now, today, titles.suggest(trip))
        }
        val sections = TripSummaries.sections(summaries)
        TripsUiState(
            loading = false,
            inProgress = sections.getValue(TripPhase.InProgress),
            upcoming = sections.getValue(TripPhase.Upcoming),
            past = sections.getValue(TripPhase.Past),
            returnCandidate = TripSummaries.returnCandidate(summaries),
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), TripsUiState())

    private fun tripsWithPlans(): Flow<List<Pair<Trip, JetLagPlan?>>> = trips.trips.flatMapLatest { list ->
        if (list.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(list.map { trip -> plans.plan(trip.id).map { trip to it } }) { it.toList() }
        }
    }

    /** Creates (or refreshes) the demo trip departing in three days and opens it. */
    fun createDemoTrip() {
        viewModelScope.launch {
            val today = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
            val trip = DemoData.tryItTrip(today).copy(createdAt = clock.instant())
            trips.upsert(trip)
            events.send(TripsEvent.OpenTrip(trip.id))
        }
    }

    /** Deletes right away; the [TripsEvent.Deleted] event carries the trip so [undoDelete] can restore it. */
    fun delete(tripId: String) {
        viewModelScope.launch {
            val trip = trips.trip(tripId).first() ?: return@launch
            trips.delete(tripId)
            events.send(TripsEvent.Deleted(trip))
        }
    }

    fun undoDelete(trip: Trip) {
        viewModelScope.launch { trips.upsert(trip) }
    }

    /** Copies a trip with fresh ids (so it gets its own plan) and the same flights. */
    fun duplicate(tripId: String) {
        viewModelScope.launch {
            val trip = trips.trip(tripId).first() ?: return@launch
            val copy = trip.copy(
                id = UUID.randomUUID().toString(),
                legs = trip.legs.map { it.copy(id = UUID.randomUUID().toString()) },
                createdAt = clock.instant(),
            )
            trips.upsert(copy)
            events.send(TripsEvent.Duplicated(copy.id))
        }
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}
