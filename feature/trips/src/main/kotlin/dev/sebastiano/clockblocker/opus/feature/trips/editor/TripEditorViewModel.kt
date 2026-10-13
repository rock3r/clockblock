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
import dev.sebastiano.clockblocker.opus.core.data.trip.ReturnTripLinks
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
import java.time.ZoneId
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

    /** Every trip when the editor opened: where a linked return trip (or one to relink) is looked up. */
    private var allTrips: List<Trip> = emptyList()
    private var searchJob: Job? = null
    private var popularJob: Job? = null
    private var popularPlaces: List<Place>? = null
    private var previewJob: Job? = null
    private var previewTrip: Trip? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        allTrips = trips.trips.first()
        val (mode, form) = when {
            args.tripId != null -> {
                val trip = trips.trip(args.tripId).first()
                if (trip == null) {
                    _state.update { it.copy(loading = false, notFound = true) }
                    return
                }
                original = trip
                // A saved trip's start is settled, whatever its shape: null stays the departure city.
                EditorMode.Edit to formOf(trip, titleEdited = trip.title != titles.suggest(trip)).copy(bodyClockStartPicked = true)
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
        val home = profiles.profile.first()?.homeZoneId
        val base = TripEditorUiState(
            loading = false,
            mode = mode,
            homeZoneId = home,
            returnAlreadySet = original?.returnDeparture != null,
            returnTrip = original?.let { ReturnTripLinks.linked(it, allTrips) },
        )
        _state.value = derive(base, form)
        schedulePreview(_state.value)
    }

    private fun formOf(trip: Trip, titleEdited: Boolean): EditorForm {
        val back = trip.returnDeparture?.atZone(trip.destination.zone)
        return EditorForm(
            legs = trip.legs.map(LegDraft::from),
            title = if (titleEdited) trip.title else "",
            titleEdited = titleEdited,
            returnFlight = back != null,
            returnDate = back?.toLocalDate(),
            returnTime = back?.toLocalTime()?.withSecond(0)?.withNano(0),
            strategy = trip.strategyOverride,
            bodyClockStartZoneId = trip.bodyClockStartZoneId,
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

    override fun onReturnDateChange(date: LocalDate) = editForm {
        it.copy(returnFlight = true, returnDate = date, returnTime = it.returnTime ?: DefaultReturnTime)
    }

    override fun onReturnTimeChange(time: LocalTime) = editForm { it.copy(returnFlight = true, returnTime = time) }

    /**
     * On: an edited trip that already has a return trip (linked, or one left behind when the switch was turned off,
     * see [ReturnTripLinks.reusable]) takes that trip's departure, and Save moves that trip rather than adding one.
     * Otherwise it suggests flying back [ReturnTripFactory.DefaultStay] after the last landing, at the time of day the
     * trip leaves (as Create return trip does), unless a date is already set. With nothing to go on yet, the date stays
     * empty for the user to pick. Off: forgets the return date; an existing return trip stays unless the user also
     * asks to delete it ([onDeleteReturnTripChange]).
     */
    override fun onReturnFlightChange(on: Boolean) {
        if (on && _state.value.returnTrip == null) {
            val reusable = original?.let { ReturnTripLinks.reusable(it, allTrips) }
            if (reusable != null) _state.update { it.copy(returnTrip = reusable) }
        }
        val existing = _state.value.returnTrip?.legs?.first()?.departureLocal
        editForm { form ->
            if (!on) return@editForm form.copy(returnFlight = false, returnDate = null, returnTime = null)
            val landed = form.legs.lastOrNull()?.let { it.arrivalDate ?: it.departureDate }
            form.copy(
                returnFlight = true,
                returnDate = form.returnDate ?: existing?.toLocalDate() ?: landed?.plusDays(ReturnTripFactory.DefaultStay.toDays()),
                returnTime = form.returnTime ?: existing?.toLocalTime() ?: form.legs.firstOrNull()?.departureTime ?: DefaultReturnTime,
                deleteReturnTrip = false,
            )
        }
    }

    /** With the switch off: whether Save also deletes the trip's existing return trip (kept by default). */
    override fun onDeleteReturnTripChange(delete: Boolean) = editForm { it.copy(deleteReturnTrip = delete) }

    override fun onStrategyChange(strategy: AdaptationStrategy?) = editForm { it.copy(strategy = strategy) }

    /**
     * "Body clock when you leave": Home stores the offered home zone, the departure city stores nothing. Either way
     * the pick sticks: later edits to the route no longer change it.
     */
    override fun onBodyClockStartChange(fromHome: Boolean) {
        val home = _state.value.bodyClockStart?.homeZoneId ?: return
        editForm { it.copy(bodyClockStartZoneId = if (fromHome) home else null, bodyClockStartPicked = true) }
    }

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

    /**
     * Searches as the user types in the airport sheet. The ticket keeps its current airport until another one is
     * picked, so a half-typed search can be dismissed without losing anything.
     */
    override fun onPlaceQueryChange(field: PlaceFieldRef, query: String) {
        searchJob?.cancel()
        popularJob?.cancel()
        if (query.isBlank()) {
            // Empty again: back to the popular airports.
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

    /** Opens the search for [field] at once (the sheet shows straight away), with the popular airports. */
    override fun openPlaceSearch(field: PlaceFieldRef) {
        if (_state.value.legs.getOrNull(field.legIndex) == null) return
        searchJob?.cancel()
        showPopular(field)
    }

    /**
     * Shows [PopularAirports] for [field] while its query is empty, minus the airport already at the leg's other
     * end. The search state is set synchronously (with the list if it's loaded already), so the sheet never closes
     * between two fields; the list fills in once loaded.
     */
    private fun showPopular(field: PlaceFieldRef) {
        popularJob?.cancel()
        _state.update { current -> current.copy(search = popularSearch(current, field, popularPlaces.orEmpty())) }
        if (popularPlaces != null) return
        popularJob = viewModelScope.launch {
            val all = PopularAirports.Codes.mapNotNull { places.byCode(it) }.also { popularPlaces = it }
            _state.update { current ->
                if (current.search?.field != field || current.search.query.isNotBlank()) current
                else current.copy(search = popularSearch(current, field, all))
            }
        }
    }

    private fun popularSearch(state: TripEditorUiState, field: PlaceFieldRef, all: List<Place>): PlaceSearchState {
        val other = state.legs.getOrNull(field.legIndex)
            ?.place(if (field.end == LegEnd.Origin) LegEnd.Destination else LegEnd.Origin)?.place
        val results = all.filter { it != other }.take(PopularLimit)
        return PlaceSearchState(field, "", results, searching = false, popular = true)
    }

    // endregion

    /**
     * Persists the trip, then keeps its return trip in step and emits [TripEditorEvent.Saved]:
     * - switch on with a return trip already linked (or relinked): moves that trip to the new departure;
     * - switch on without one: adds it (reversed, like Create return trip) and links it;
     * - switch off: keeps the return trip (and the link, so turning the switch on again reuses it), or deletes it
     *   when asked to;
     * - a return trip being edited moves its outbound's return date along; a new one made by Create return trip is
     *   linked to its outbound.
     */
    override fun save() {
        val current = _state.value
        if (!current.canSave) return
        val legs = current.legs.mapNotNull(LegDraft::toFlightLeg)
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val destination = legs.last().destination
            val returnLocal = current.returnLocal
            val existingReturn = current.returnTrip?.let { trips.trip(it.id).first() }
            var trip = Trip(
                id = original?.id ?: newId(),
                title = current.effectiveTitle,
                legs = legs,
                createdAt = original?.createdAt ?: clock.instant(),
                strategyOverride = current.form.strategy,
                returnDeparture = returnLocal?.atZone(destination.zone)?.toInstant(),
                bodyClockStartZoneId = current.effectiveBodyClockStartZoneId,
                // A link to a trip that's gone (deleted since) is dropped.
                returnTripId = existingReturn?.id,
            )
            if (current.offersReturnFlight) {
                when {
                    current.form.returnFlight && returnLocal != null && existingReturn != null -> {
                        val moved = returnTrips.moveTo(existingReturn, returnLocal)
                        if (moved != existingReturn) trips.upsert(moved)
                        trip = trip.copy(returnDeparture = moved.departure, returnTripId = moved.id)
                    }
                    current.createsReturnTrip && returnLocal != null -> {
                        val back = returnTripOf(trip, returnLocal, current.homeZoneId)
                        trips.upsert(back)
                        trip = trip.copy(returnTripId = back.id)
                    }
                    !current.form.returnFlight && existingReturn != null && current.form.deleteReturnTrip -> {
                        trips.delete(existingReturn.id)
                        trip = trip.copy(returnTripId = null)
                    }
                }
            }
            trips.upsert(trip)
            outbound?.let { out ->
                if (ReturnTripLinks.linked(out, allTrips) == null) trips.upsert(returnTrips.linkOutbound(out, trip))
            }
            original?.let { before ->
                // Editing a return trip: its outbound's return date follows it (unless that trip is now one way).
                val out = ReturnTripLinks.outboundOf(before, allTrips)?.let { trips.trip(it.id).first() }
                if (out?.returnDeparture != null && out.returnDeparture != trip.departure) {
                    trips.upsert(out.copy(returnDeparture = trip.departure, returnTripId = trip.id))
                }
            }
            baseline = current.form
            _state.update { it.copy(saving = false, dirty = false) }
            events.send(TripEditorEvent.Saved(trip.id))
        }
    }

    /**
     * The Return flight switch's trip: [outbound] reversed by [ReturnTripFactory] (as Create return trip does),
     * leaving at [departureLocal]. Its body clock start gets the editor's default for a new trip: Home when it flies
     * you home from somewhere on other hours (HOME → destination → HOME, #145), else the departure city.
     */
    private fun returnTripOf(outbound: Trip, departureLocal: LocalDateTime, homeZoneId: String?): Trip {
        val back = returnTrips.create(outbound, departureLocal, newId = ::newId)
        val start = bodyClockStartChoice(formOf(back, titleEdited = false), homeZoneId)?.takeIf { it.fromHome }?.homeZoneId
        return back.copy(bodyClockStartZoneId = start)
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
            bodyClockStart = bodyClockStartChoice(form, base.homeZoneId),
        )
    }

    /**
     * Offers "Body clock when you leave" once the first departure (airport and date) is known and the Home option's
     * zone is on a different UTC offset from it then; otherwise the choice can't change the plan. The Home option is
     * the trip's saved zone if it has one (kept even if home has changed since), else the profile's home zone.
     *
     * Until the start is picked ([EditorForm.bodyClockStartPicked]), Home is preselected for a trip back home and the
     * departure city otherwise; Save then stores that default explicitly.
     */
    private fun bodyClockStartChoice(form: EditorForm, homeZoneId: String?): BodyClockStartChoice? {
        val departures = form.legs.mapNotNull { leg ->
            val place = leg.origin.place ?: return@mapNotNull null
            val date = leg.departureDate ?: return@mapNotNull null
            Triple(leg, place, date.atTime(leg.departureTime ?: LocalTime.NOON).atZone(place.zone).toInstant())
        }
        val (_, departure, at) = departures.minByOrNull { it.third } ?: return null
        val homeId = form.bodyClockStartZoneId ?: homeZoneId ?: return null
        val home = runCatching { ZoneId.of(homeId) }.getOrNull() ?: return null
        if (home.rules.getOffset(at) == departure.zone.rules.getOffset(at)) return null
        val fromHome = if (form.bodyClockStartPicked) {
            form.bodyClockStartZoneId != null
        } else {
            departures.maxBy { it.third }.first.landsOnTimeOf(home)
        }
        return BodyClockStartChoice(departure, homeId, fromHome)
    }

    /**
     * A trip back home: this (last) flight lands somewhere on [home]'s UTC offset at its arrival (the departure, while
     * the arrival isn't known). Compared by offset like the choice itself, so any airport on home time counts.
     */
    private fun LegDraft.landsOnTimeOf(home: ZoneId): Boolean {
        val place = destination.place ?: return false
        val local = arrivalDate?.atTime(arrivalTime ?: LocalTime.NOON)
            ?: departureDate?.atTime(departureTime ?: LocalTime.NOON)
            ?: return false
        val at = local.atZone(place.zone).toInstant()
        return home.rules.getOffset(at) == place.zone.rules.getOffset(at)
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
     * Re-plans the draft (debounced, off the main thread) when what the planner sees changed: legs, strategy,
     * return or where the body clock starts. Titles and flight numbers don't move the plan, so typing them never re-runs it. The previous preview
     * stays up while a new one is computed, and goes away as soon as the draft is incomplete or has errors. When the
     * planner can't plan the draft (or there's no profile), the preview is cleared and marked unavailable.
     */
    private fun schedulePreview(state: TripEditorUiState) {
        val trip = if (!state.loading && state.complete && !state.hasErrors) previewTripOf(state) else null
        if (trip == previewTrip) return
        previewTrip = trip
        previewJob?.cancel()
        if (trip == null) {
            _state.update { it.copy(preview = null, previewUnavailable = false) }
            return
        }
        previewJob = viewModelScope.launch {
            delay(PreviewDebounceMillis)
            val profile = profiles.profile.first()
            val plan = profile?.let {
                withContext(dispatchers.default) { runCatching { planner.plan(trip, it, clock.instant()) }.getOrNull() }
            }
            if (plan == null) {
                // Never leave an older draft's numbers (or "Working out…") up for one that can't be planned.
                _state.update { if (previewTrip == trip) it.copy(preview = null, previewUnavailable = true) else it }
                return@launch
            }
            val preview = ShiftPreview(
                shiftHours = plan.shiftHours,
                direction = plan.direction,
                strategy = plan.strategy,
                daysToAdapt = plan.estimatedDaysToAdapt,
                daysWithoutPlan = plan.estimatedDaysWithoutPlan,
            )
            _state.update { if (previewTrip == trip) it.copy(preview = preview, previewUnavailable = false) else it }
        }
    }

    private fun previewTripOf(state: TripEditorUiState): Trip? {
        val legs = state.legs.map { it.toFlightLeg()?.copy(flightNumber = null) ?: return null }
        if (legs.isEmpty()) return null
        val returnDeparture = state.returnLocal?.atZone(legs.last().destination.zone)?.toInstant()
        return Trip(
            id = original?.id ?: PreviewTripId,
            title = "",
            legs = legs,
            createdAt = original?.createdAt ?: PlaceholderInstant,
            strategyOverride = state.form.strategy,
            returnDeparture = returnDeparture,
            bodyClockStartZoneId = state.effectiveBodyClockStartZoneId,
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
        val DefaultReturnTime: LocalTime = TripEditorUiState.DefaultReturnTime
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
