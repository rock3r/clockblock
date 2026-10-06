package dev.sebastiano.clockblocker.opus.feature.trips.editor

import dev.sebastiano.clockblocker.opus.core.data.trip.IssueSeverity
import dev.sebastiano.clockblocker.opus.core.data.trip.TripIssue
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Which end of a leg an airport field edits. */
enum class LegEnd { Origin, Destination }

/** Identifies one airport field in the editor. */
data class PlaceFieldRef(val legIndex: Int, val end: LegEnd)

/** An airport field: free text while searching, a [place] once one is picked. */
data class PlaceInput(val query: String = "", val place: Place? = null) {
    companion object {
        fun of(place: Place?): PlaceInput = PlaceInput(place?.let(::fieldText).orEmpty(), place)

        /** What the field shows once [place] is picked: the city (the IATA code sits in a chip beside it). */
        fun fieldText(place: Place): String = place.city.ifBlank { place.name }
    }
}

/**
 * One flight as typed in the editor. Every time is wall-clock time *at that airport* (as on a boarding pass);
 * nothing is converted until [toFlightLeg].
 */
data class LegDraft(
    val id: String,
    val origin: PlaceInput = PlaceInput(),
    val destination: PlaceInput = PlaceInput(),
    val departureDate: LocalDate? = null,
    val departureTime: LocalTime? = null,
    val arrivalDate: LocalDate? = null,
    val arrivalTime: LocalTime? = null,
    val flightNumber: String = "",
    /**
     * The arrival was filled in from the flight distance ([dev.sebastiano.clockblocker.opus.core.data.trip.FlightEstimates]),
     * not typed: it keeps following the airports and departure until the user sets an arrival themselves.
     */
    val arrivalEstimated: Boolean = false,
) {
    val departureLocal: LocalDateTime? get() = departureDate?.let { d -> departureTime?.let { d.atTime(it) } }
    val arrivalLocal: LocalDateTime? get() = arrivalDate?.let { d -> arrivalTime?.let { d.atTime(it) } }

    fun place(end: LegEnd): PlaceInput = if (end == LegEnd.Origin) origin else destination

    fun withPlace(end: LegEnd, input: PlaceInput): LegDraft =
        if (end == LegEnd.Origin) copy(origin = input) else copy(destination = input)

    /** Whether the user hasn't set any part of the arrival, so the editor may estimate it. */
    val arrivalUntouched: Boolean get() = arrivalEstimated || (arrivalDate == null && arrivalTime == null)

    /** The leg as a domain object, or null while anything required is missing. */
    fun toFlightLeg(): FlightLeg? {
        val from = origin.place ?: return null
        val to = destination.place ?: return null
        val departure = departureLocal ?: return null
        val arrival = arrivalLocal ?: return null
        return FlightLeg(
            id = id,
            origin = from,
            destination = to,
            departureLocal = departure,
            arrivalLocal = arrival,
            flightNumber = flightNumber.trim().uppercase().ifBlank { null },
        )
    }

    companion object {
        fun from(leg: FlightLeg): LegDraft = LegDraft(
            id = leg.id,
            origin = PlaceInput.of(leg.origin),
            destination = PlaceInput.of(leg.destination),
            departureDate = leg.departureLocal.toLocalDate(),
            departureTime = leg.departureLocal.toLocalTime(),
            arrivalDate = leg.arrivalLocal.toLocalDate(),
            arrivalTime = leg.arrivalLocal.toLocalTime(),
            flightNumber = leg.flightNumber.orEmpty(),
        )
    }
}

/** The editable data of a trip: what "dirty" compares and what Save persists. */
data class EditorForm(
    val legs: List<LegDraft>,
    /** What the user typed; [titleEdited] false means "use the suggestion". */
    val title: String = "",
    val titleEdited: Boolean = false,
    val returnDate: LocalDate? = null,
    val returnTime: LocalTime? = null,
    val strategy: AdaptationStrategy? = null,
)

enum class EditorMode { New, Edit, Return }

/**
 * Airport search results for the field being typed in. [popular] = the field is still empty and [results] are
 * common hubs offered as one-tap picks (never picked by the keyboard's Enter).
 */
data class PlaceSearchState(
    val field: PlaceFieldRef,
    val query: String,
    val results: List<Place>,
    val searching: Boolean,
    val popular: Boolean = false,
)

/**
 * What the planner says about the trip as entered, before it is saved: straight from a [dev.sebastiano.clockblocker.opus.core.model.JetLagPlan]
 * computed for the draft, so the editor never claims more than the plan will.
 */
data class ShiftPreview(
    /** Destination minus origin, positive = east (as the planner normalises it). */
    val shiftHours: Double,
    val direction: ShiftDirection,
    val strategy: AdaptationStrategy,
    val daysToAdapt: Double,
    val daysWithoutPlan: Double,
)

/** Everything the editor renders. Derived from [EditorForm] by the ViewModel. */
data class TripEditorUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val mode: EditorMode = EditorMode.New,
    val form: EditorForm = EditorForm(emptyList()),
    val suggestedTitle: String = "",
    /** Validator findings, indexed like [EditorForm.legs]. */
    val issues: List<TripIssue> = emptyList(),
    /** Every leg has airports and times. */
    val complete: Boolean = false,
    val search: PlaceSearchState? = null,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    /** The trip is under way or departs soon, so "I'm delayed" is offered. */
    val canReportDelay: Boolean = false,
    /** Index of the leg a delay most likely applies to (the next one not yet landed). */
    val delayLegIndex: Int = 0,
    /** The planner's take on the draft, once every leg is complete and error-free (null until then). */
    val preview: ShiftPreview? = null,
) {
    val legs: List<LegDraft> get() = form.legs

    /** The title Save will use. */
    val effectiveTitle: String get() = if (form.titleEdited) form.title.ifBlank { suggestedTitle } else suggestedTitle

    val hasErrors: Boolean get() = issues.any { it.severity == IssueSeverity.Error }

    /** Save is disabled only for errors (and for legs that can't be planned yet). */
    val canSave: Boolean get() = !loading && !saving && complete && !hasErrors

    fun issuesFor(legIndex: Int): List<TripIssue> = issues.filter { it.legIndex == legIndex }

    /** Time on the ground before [legIndex] (≥ 1), when both neighbours are complete. */
    fun layoverBefore(legIndex: Int): Duration? {
        val prev = legs.getOrNull(legIndex - 1)?.toFlightLeg() ?: return null
        val next = legs.getOrNull(legIndex)?.toFlightLeg() ?: return null
        return Duration.between(prev.arrival, next.departure)
    }
}
