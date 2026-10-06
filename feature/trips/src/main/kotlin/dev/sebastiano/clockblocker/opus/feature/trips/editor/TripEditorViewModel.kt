package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.clockblocker.opus.core.circadian.JetLagPlanner
import dev.sebastiano.clockblocker.opus.core.data.PlaceSearch
import dev.sebastiano.clockblocker.opus.core.data.ProfileRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.data.time.AppDispatchers
import dev.sebastiano.clockblocker.opus.core.data.trip.FlightEstimates
import dev.sebastiano.clockblocker.opus.core.data.trip.ReturnTripFactory
import dev.sebastiano.clockblocker.opus.core.data.trip.TripIssue
import dev.sebastiano.clockblocker.opus.core.data.trip.TripTitleSuggester
import dev.sebastiano.clockblocker.opus.core.data.trip.TripValidator
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Which trip the editor opens: [tripId] to edit, [returnOfTripId] to prefill a return trip, neither = new. */
data class TripEditorArgs(val tripId: String? = null, val returnOfTripId: String? = null)

/** One-off editor outcomes. */
sealed interface TripEditorEvent {
    data class Saved(val tripId: String) : TripEditorEvent
}

/**
 * Full-screen trip editor. Holds an [EditorForm] of local wall-clock times per airport, validates it live with
 * [TripValidator] (inline issues + one-tap fixes), searches airports offline, and saves through
 * [TripRepository] (plans re-derive automatically from there).
 *
 * While editing it also fills untouched arrivals from the flight distance ([FlightEstimates]), offers popular
 * airports for empty airport fields, and previews the plan for a complete draft with the same [JetLagPlanner] the
 * saved trip will use.
 */
@AssistedInject
class TripEditorViewModel(
    @Assisted private val args: TripEditorArgs,
    private val trips: TripRepository,
    private val places: PlaceSearch,
    private val validator: TripValidator,
    private val titles: TripTitleSuggester,
    private val returnTrips: ReturnTripFactory,
    private val clock: Clock,
    private val planner: JetLagPlanner,
    private val profiles: ProfileRepository,
    private val dispatchers: AppDispatchers,
) : ViewModel(), TripEditorActions {

    private val _state = MutableStateFlow(TripEditorUiState())
    val state: StateFlow<TripEditorUiState> = _state.asStateFlow()

    private val events = Channel<TripEditorEvent>(Channel.BUFFERED)
    val eventFlow: Flow<TripEditorEvent> = events.receiveAsFlow()

    private var baseline: EditorForm? = null
    private var original: Trip? = null
    private var outbound: Trip? = null
    private var searchJob: Job? = null
    private var popularJob: Job? = null
    private var popularPlaces: List<Place>? = null
    private var previewJob: Job? = null
    private var previewTrip: Trip? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val (mode, form) = when {
            args.tripId != null -> {
                val trip = trips.trip(args.tripId).first()
                if (trip == null) {
                    _state.update { it.copy(loading = false, notFound = true) }
                    return
                }
                original = trip
                EditorMode.Edit to formOf(trip, titleEdited = trip.title != titles.suggest(trip))
            }

            args.returnOfTripId != null -> {
                val out = trips.trip(args.returnOfTripId).first()
                if (out == null) {
                    EditorMode.New to EditorForm(listOf(LegDraft(newId())))
                } else {
                    outbound = out
                    EditorMode.Return to formOf(returnTrips.create(out, newId = ::newId), titleEdited = false)
                }
            }

            else -> EditorMode.New to EditorForm(listOf(LegDraft(newId())))
        }
        baseline = form
        _state.value = derive(TripEditorUiState(loading = false, mode = mode), form)
        schedulePreview(_state.value)
    }

    private fun formOf(trip: Trip, titleEdited: Boolean): EditorForm {
        val back = trip.returnDeparture?.atZone(trip.destination.zone)
        return EditorForm(
            legs = trip.legs.map(LegDraft::from),
            title = if (titleEdited) trip.title else "",
            titleEdited = titleEdited,
            returnDate = back?.toLocalDate(),
            returnTime = back?.toLocalTime()?.withSecond(0)?.withNano(0),
            strategy = trip.strategyOverride,
        )
    }

    // region Form edits

    private fun editForm(transform: (EditorForm) -> EditorForm) {
        _state.update { current ->
            if (current.loading) current else derive(current, transform(current.form).let(::withEstimatedArrivals))
        }
        schedulePreview(_state.value)
    }

    /**
     * Fills every leg whose arrival the user hasn't touched with the distance-based estimate, or clears a stale
     * estimate when its inputs are gone. Arrivals the user set (or that came from a saved trip) are never changed.
     */
    private fun withEstimatedArrivals(form: EditorForm): EditorForm {
        val legs = form.legs.map { leg ->
            if (!leg.arrivalUntouched) return@map leg
            val from = leg.origin.place
            val to = leg.destination.place
            val departure = leg.departureLocal
            if (from != null && to != null && departure != null && from != to) {
                val arrival = FlightEstimates.arrivalLocal(from, to, departure)
                leg.copy(arrivalDate = arrival.toLocalDate(), arrivalTime = arrival.toLocalTime(), arrivalEstimated = true)
            } else if (leg.arrivalEstimated) {
                leg.copy(arrivalDate = null, arrivalTime = null, arrivalEstimated = false)
            } else {
                leg
            }
        }
        return if (legs == form.legs) form else form.copy(legs = legs)
    }

    private fun editLeg(index: Int, transform: (LegDraft) -> LegDraft) = editForm { form ->
        if (index !in form.legs.indices) form else form.copy(legs = form.legs.toMutableList().also { it[index] = transform(it[index]) })
    }

    override fun onTitleChange(title: String) = editForm { it.copy(title = title, titleEdited = true) }

    override fun onFlightNumberChange(legIndex: Int, value: String) = editLeg(legIndex) { it.copy(flightNumber = value.take(MaxFlightNumberLength)) }

    override fun onDepartureDateChange(legIndex: Int, date: LocalDate) = editLeg(legIndex) { leg ->
        // Moving the departure date carries an untouched same-day arrival along with it.
        val previous = leg.departureDate
        val arrival = if (previous != null && leg.arrivalDate != null) {
            leg.arrivalDate.plusDays(ChronoUnit.DAYS.between(previous, date))
        } else {
            leg.arrivalDate
        }
        leg.copy(departureDate = date, arrivalDate = arrival)
    }

    override fun onDepartureTimeChange(legIndex: Int, time: LocalTime) = editLeg(legIndex) { it.copy(departureTime = time) }

    override fun onArrivalDateChange(legIndex: Int, date: LocalDate) = editLeg(legIndex) {
        it.copy(arrivalDate = date, arrivalEstimated = false)
    }

    override fun onArrivalTimeChange(legIndex: Int, time: LocalTime) = editLeg(legIndex) { leg ->
        // First arrival time entered without a date: assume the departure date (the validator then offers +1 day).
        leg.copy(arrivalTime = time, arrivalDate = leg.arrivalDate ?: leg.departureDate, arrivalEstimated = false)
    }

    override fun onReturnDateChange(date: LocalDate) = editForm { it.copy(returnDate = date, returnTime = it.returnTime ?: DefaultReturnTime) }

    override fun onReturnTimeChange(time: LocalTime) = editForm { it.copy(returnTime = time) }

    override fun clearReturn() = editForm { it.copy(returnDate = null, returnTime = null) }

    override fun onStrategyChange(strategy: AdaptationStrategy?) = editForm { it.copy(strategy = strategy) }

    /** Adds a connecting flight that leaves from where the last one lands, on the day it lands. */
    override fun addLeg() = editForm { form ->
        val last = form.legs.lastOrNull()
        val leg = LegDraft(
            id = newId(),
            origin = PlaceInput.of(last?.destination?.place),
            departureDate = last?.arrivalDate,
        )
        form.copy(legs = form.legs + leg)
    }

    override fun removeLeg(legIndex: Int) {
        dismissSearch()
        editForm { form ->
            if (form.legs.size <= 1 || legIndex !in form.legs.indices) form
            else form.copy(legs = form.legs.filterIndexed { i, _ -> i != legIndex })
        }
    }

    /** One-tap fix from a validation issue (e.g. "Arrives next day?"). */
    override fun applySuggestedArrival(legIndex: Int, arrival: LocalDateTime) = editLeg(legIndex) {
        it.copy(arrivalDate = arrival.toLocalDate(), arrivalTime = arrival.toLocalTime(), arrivalEstimated = false)
    }

    /** Fix for a disconnected itinerary: depart from the airport the previous flight lands at. */
    override fun useConnectingOrigin(legIndex: Int) {
        val previous = _state.value.legs.getOrNull(legIndex - 1)?.destination?.place ?: return
        editLeg(legIndex) { it.copy(origin = PlaceInput.of(previous)) }
    }

    override fun swapPlaces(legIndex: Int) {
        dismissSearch()
        editLeg(legIndex) { it.copy(origin = it.destination, destination = it.origin) }
    }

    /**
     * "I'm delayed": moves leg [legIndex] later by [delay], departure and arrival alike. Shifted through the
     * instants, so a delay across a DST change still lands on the right wall-clock time.
     */
    override fun delayLeg(legIndex: Int, delay: Duration) = editLeg(legIndex) { leg ->
        val flight = leg.toFlightLeg() ?: return@editLeg leg
        val departure = flight.departure.plus(delay).atZone(flight.origin.zone).toLocalDateTime()
        val arrival = flight.arrival.plus(delay).atZone(flight.destination.zone).toLocalDateTime()
        leg.copy(
            departureDate = departure.toLocalDate(),
            departureTime = departure.toLocalTime(),
            arrivalDate = arrival.toLocalDate(),
            arrivalTime = arrival.toLocalTime(),
        )
    }

    // endregion

    // region Airport search

    override fun onPlaceQueryChange(field: PlaceFieldRef, query: String) {
        editLeg(field.legIndex) { it.withPlace(field.end, PlaceInput(query, place = null)) }
        searchJob?.cancel()
        popularJob?.cancel()
        if (query.isBlank()) {
            // Still in the field, now empty again: back to the popular airports.
            _state.update { it.copy(search = null) }
            showPopular(field)
            return
        }
        _state.update { current ->
            // Keep the previous matches on screen while the new search runs (see pickTopResult), but never the
            // popular picks: they don't match what's typed.
            val previous = current.search?.takeIf { s -> s.field == field && !s.popular }?.results.orEmpty()
            current.copy(search = PlaceSearchState(field, query, previous, searching = true))
        }
        searchJob = viewModelScope.launch {
            delay(SearchDebounceMillis)
            val results = places.search(query.trim(), SearchLimit)
            _state.update { current ->
                if (current.search?.field == field && current.search.query == query) {
                    current.copy(search = PlaceSearchState(field, query, results, searching = false))
                } else {
                    current
                }
            }
        }
    }

    override fun onPlaceSelected(field: PlaceFieldRef, place: Place) {
        searchJob?.cancel()
        popularJob?.cancel()
        editForm { form ->
            val legs = form.legs.toMutableList()
            legs[field.legIndex] = legs[field.legIndex].withPlace(field.end, PlaceInput.of(place))
            // Chain connections: the next flight leaves from where this one lands, unless already set.
            val next = legs.getOrNull(field.legIndex + 1)
            if (field.end == LegEnd.Destination && next != null && next.origin.place == null && next.origin.query.isBlank()) {
                legs[field.legIndex + 1] = next.copy(origin = PlaceInput.of(place))
            }
            form.copy(legs = legs)
        }
        _state.update { it.copy(search = null) }
    }

    /**
     * IME action on an airport field: picks the top match on screen. Returns whether a place was picked.
     *
     * While a new search runs, the previous results stay on screen, and those are what Enter picks: keyboards may
     * autocorrect the query as they submit it ("hnd" → "Hand", even with autocorrect off), which restarts the search
     * just before this is called.
     */
    override fun pickTopResult(field: PlaceFieldRef): Boolean {
        val search = _state.value.search?.takeIf { it.field == field && !it.popular && it.query.isNotBlank() } ?: return false
        val top = search.results.firstOrNull() ?: return false
        onPlaceSelected(field, top)
        return true
    }

    override fun dismissSearch() {
        searchJob?.cancel()
        popularJob?.cancel()
        _state.update { it.copy(search = null) }
    }

    override fun onPlaceFieldFocused(field: PlaceFieldRef) {
        val input = _state.value.legs.getOrNull(field.legIndex)?.place(field.end) ?: return
        if (input.place != null || input.query.isNotBlank()) return
        if (_state.value.search?.field == field) return
        searchJob?.cancel()
        showPopular(field)
    }

    /** Offers [PopularAirports] under an empty [field], minus the airport already at the leg's other end. */
    private fun showPopular(field: PlaceFieldRef) {
        popularJob?.cancel()
        popularJob = viewModelScope.launch {
            val all = popularPlaces ?: PopularAirports.Codes.mapNotNull { places.byCode(it) }.also { popularPlaces = it }
            _state.update { current ->
                val leg = current.legs.getOrNull(field.legIndex) ?: return@update current
                val input = leg.place(field.end)
                if (input.place != null || input.query.isNotBlank()) return@update current
                val other = leg.place(if (field.end == LegEnd.Origin) LegEnd.Destination else LegEnd.Origin).place
                val results = all.filter { it != other }.take(PopularLimit)
                current.copy(search = PlaceSearchState(field, "", results, searching = false, popular = true))
            }
        }
    }

    // endregion

    /** Persists the trip (and links a return trip to its outbound), then emits [TripEditorEvent.Saved]. */
    override fun save() {
        val current = _state.value
        if (!current.canSave) return
        val legs = current.legs.mapNotNull(LegDraft::toFlightLeg)
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val destination = legs.last().destination
            val returnDeparture = current.form.returnDate?.let { date ->
                date.atTime(current.form.returnTime ?: DefaultReturnTime).atZone(destination.zone).toInstant()
            }
            val trip = Trip(
                id = original?.id ?: newId(),
                title = current.effectiveTitle,
                legs = legs,
                createdAt = original?.createdAt ?: clock.instant(),
                strategyOverride = current.form.strategy,
                returnDeparture = returnDeparture,
            )
            trips.upsert(trip)
            outbound?.let { out -> if (out.returnDeparture == null) trips.upsert(returnTrips.linkOutbound(out, trip)) }
            baseline = current.form
            _state.update { it.copy(saving = false, dirty = false) }
            events.send(TripEditorEvent.Saved(trip.id))
        }
    }

    private fun derive(base: TripEditorUiState, form: EditorForm): TripEditorUiState {
        val built = form.legs.map(LegDraft::toFlightLeg)
        val complete = built.isNotEmpty() && built.all { it != null }
        val now = clock.instant()
        val trip = original
        val canDelay = base.mode == EditorMode.Edit && trip != null &&
            now.isAfter(trip.departure.minus(DelayWindowBeforeDeparture)) && now.isBefore(trip.arrival)
        return base.copy(
            form = form,
            suggestedTitle = titles.suggest(routeOnly(form.legs)),
            issues = validate(built),
            complete = complete,
            dirty = form != baseline,
            canReportDelay = canDelay,
            delayLegIndex = built.indexOfFirst { it != null && it.arrival.isAfter(now) }.coerceAtLeast(0),
        )
    }

    /** Whole-trip validation when every leg is complete; otherwise each complete leg on its own. */
    private fun validate(built: List<FlightLeg?>): List<TripIssue> {
        if (built.isEmpty()) return emptyList()
        if (built.all { it != null }) return validator.validate(built.filterNotNull()).issues
        return built.flatMapIndexed { index, leg ->
            if (leg == null) emptyList() else validator.validate(listOf(leg)).issues.mapNotNull { it.atLeg(index) }
        }
    }

    /**
     * Re-plans the draft (debounced, off the main thread) when what the planner sees changed: legs, strategy or
     * return. Titles and flight numbers don't move the plan, so typing them never re-runs it. The previous preview
     * stays up while a new one is computed, and goes away as soon as the draft can't be planned.
     */
    private fun schedulePreview(state: TripEditorUiState) {
        val trip = if (!state.loading && state.complete && !state.hasErrors) previewTripOf(state) else null
        if (trip == previewTrip) return
        previewTrip = trip
        previewJob?.cancel()
        if (trip == null) {
            _state.update { it.copy(preview = null) }
            return
        }
        previewJob = viewModelScope.launch {
            delay(PreviewDebounceMillis)
            val profile = profiles.profile.first() ?: return@launch
            val plan = withContext(dispatchers.default) { runCatching { planner.plan(trip, profile, clock.instant()) }.getOrNull() }
                ?: return@launch
            val preview = ShiftPreview(
                shiftHours = plan.shiftHours,
                direction = plan.direction,
                strategy = plan.strategy,
                daysToAdapt = plan.estimatedDaysToAdapt,
                daysWithoutPlan = plan.estimatedDaysWithoutPlan,
            )
            _state.update { if (previewTrip == trip) it.copy(preview = preview) else it }
        }
    }

    private fun previewTripOf(state: TripEditorUiState): Trip? {
        val legs = state.legs.map { it.toFlightLeg()?.copy(flightNumber = null) ?: return null }
        if (legs.isEmpty()) return null
        val returnDeparture = state.form.returnDate?.let { date ->
            date.atTime(state.form.returnTime ?: DefaultReturnTime).atZone(legs.last().destination.zone).toInstant()
        }
        return Trip(
            id = original?.id ?: PreviewTripId,
            title = "",
            legs = legs,
            createdAt = original?.createdAt ?: PlaceholderInstant,
            strategyOverride = state.form.strategy,
            returnDeparture = returnDeparture,
        )
    }

    /** Title suggestions only need the route, so incomplete legs with both airports still count. */
    private fun routeOnly(legs: List<LegDraft>): List<FlightLeg> = legs.mapNotNull { leg ->
        val from = leg.origin.place ?: return@mapNotNull null
        val to = leg.destination.place ?: return@mapNotNull null
        FlightLeg(leg.id, from, to, PlaceholderTime, PlaceholderTime)
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(args: TripEditorArgs): TripEditorViewModel
    }

    companion object {
        const val SearchDebounceMillis: Long = 120
        const val SearchLimit: Int = 6
        const val PopularLimit: Int = 8
        const val PreviewDebounceMillis: Long = 300
        private const val PreviewTripId = "preview"
        private val PlaceholderInstant: Instant = Instant.EPOCH
        const val MaxFlightNumberLength: Int = 8
        val DelayWindowBeforeDeparture: Duration = Duration.ofHours(48)
        val DefaultReturnTime: LocalTime = LocalTime.of(12, 0)
        private val PlaceholderTime: LocalDateTime = LocalDateTime.of(2000, 1, 1, 0, 0)

        private fun newId(): String = UUID.randomUUID().toString()
    }
}

/** Re-targets a single-leg validation issue at [index] in the full itinerary. */
private fun TripIssue.atLeg(index: Int): TripIssue? = when (this) {
    is TripIssue.SameOriginAndDestination -> copy(legIndex = index)
    is TripIssue.ArrivalNotAfterDeparture -> copy(legIndex = index)
    is TripIssue.ImplausiblyShort -> copy(legIndex = index)
    is TripIssue.ImplausiblyLong -> copy(legIndex = index)
    is TripIssue.CrossesDateLine -> copy(legIndex = index)
    else -> null
}
