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
import java.time.temporal.ChronoUnit

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

    /**
     * Calendar days from the departure date to the arrival date, both local to their airports (the ticket's
     * "+1 day"): 1 for an overnight flight, −1 for one that lands "the day before" across the date line. Null until
     * both dates are known.
     */
    val arrivalDayOffset: Int?
        get() {
            val from = departureDate ?: return null
            val to = arrivalDate ?: return null
            return ChronoUnit.DAYS.between(from, to).toInt()
        }

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
    /**
     * The Return flight switch. On: [returnDate] / [returnTime] are when the trip flies back (local time where it
     * ends), stored as the trip's `returnDeparture`; a new trip also gets its return trip on Save.
     */
    val returnFlight: Boolean = false,
    val returnDate: LocalDate? = null,
    val returnTime: LocalTime? = null,
    val strategy: AdaptationStrategy? = null,
    /**
     * [dev.sebastiano.clockblocker.opus.core.model.Trip.bodyClockStartZoneId]: null = the departure city. Only read
     * once [bodyClockStartPicked]; until then the editor suggests a start from the trip's shape.
     */
    val bodyClockStartZoneId: String? = null,
    /**
     * The body clock start is settled: the user picked it, or it came with a saved trip. False on a new or return
     * trip until the user picks, so the default follows the route (Home for a trip back home, see
     * [BodyClockStartChoice]).
     */
    val bodyClockStartPicked: Boolean = false,
)

/**
 * "Body clock when you leave": offered when home and the first departure city are on different UTC offsets at
 * departure, so the choice changes the plan. [homeZoneId] is what the Home option stores: the trip's saved zone if
 * it has one, else the profile's home zone. Until the user picks, Home is preselected for a trip back home (its last
 * flight lands on home time), since the body is likely still on home time; otherwise the departure city.
 */
data class BodyClockStartChoice(
    val departure: Place,
    val homeZoneId: String,
    val fromHome: Boolean,
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
    /**
     * The planned shift (`JetLagPlan.shiftHours`): positive = advance east, but a long-way plan crosses more than 12 h
     * (−14 for +10 h east delayed). [ShiftSummary] folds it back to the time difference.
     */
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
    /** The profile's home zone (null before onboarding). */
    val homeZoneId: String? = null,
    /** Where the body clock starts, when that choice matters for this draft (null: not offered). */
    val bodyClockStart: BodyClockStartChoice? = null,
    /**
     * The trip being edited already flew back when it was opened (it has a `returnDeparture`): its return trip, if
     * any, was made before, so Save only updates the date and adds no second one.
     */
    val returnAlreadySet: Boolean = false,
) {
    val legs: List<LegDraft> get() = form.legs

    /** What Save stores as the body clock start: the Home zone when that's offered and picked, else null. */
    val effectiveBodyClockStartZoneId: String? get() = bodyClockStart?.takeIf { it.fromHome }?.homeZoneId

    /** The title Save will use. */
    val effectiveTitle: String get() = if (form.titleEdited) form.title.ifBlank { suggestedTitle } else suggestedTitle

    val hasErrors: Boolean get() = issues.any { it.severity == IssueSeverity.Error }

    /** The inline Return flight switch: offered on new and edited trips, not on a return trip itself. */
    val offersReturnFlight: Boolean get() = mode != EditorMode.Return

    /** Save also creates the return trip (reversed legs, like Create return trip). */
    val createsReturnTrip: Boolean get() = offersReturnFlight && form.returnFlight && !returnAlreadySet

    /** The return flight, as local time where the trip ends; null while the switch is off or has no date. */
    val returnLocal: LocalDateTime?
        get() = if (form.returnFlight) form.returnDate?.atTime(form.returnTime ?: DefaultReturnTime) else null

    /** The return flight leaves before the last flight lands (both known). */
    val returnLeavesBeforeArrival: Boolean
        get() {
            val local = returnLocal ?: return false
            val last = legs.lastOrNull()?.toFlightLeg() ?: return false
            return !local.atZone(last.destination.zone).toInstant().isAfter(last.arrival)
        }

    /** Why the main action is disabled (null: it isn't), in the order the user should fix things. */
    val saveBlocker: SaveBlocker?
        get() = when {
            hasErrors -> SaveBlocker.Errors
            !complete -> SaveBlocker.Incomplete
            form.returnFlight && form.returnDate == null -> SaveBlocker.ReturnDateMissing
            returnLeavesBeforeArrival -> SaveBlocker.ReturnBeforeArrival
            else -> null
        }

    /** Save is disabled only for a [saveBlocker] (or while loading or saving). */
    val canSave: Boolean get() = !loading && !saving && saveBlocker == null

    fun issuesFor(legIndex: Int): List<TripIssue> = issues.filter { it.legIndex == legIndex }

    /** Time on the ground before [legIndex] (≥ 1), when both neighbours are complete. */
    fun layoverBefore(legIndex: Int): Duration? {
        val prev = legs.getOrNull(legIndex - 1)?.toFlightLeg() ?: return null
        val next = legs.getOrNull(legIndex)?.toFlightLeg() ?: return null
        return Duration.between(prev.arrival, next.departure)
    }

    companion object {
        /** Time of day for a return flight picked by date only. */
        val DefaultReturnTime: LocalTime = LocalTime.of(12, 0)
    }
}

/** Why the editor's main action is disabled; the bottom bar says it in words. */
enum class SaveBlocker { Errors, Incomplete, ReturnDateMissing, ReturnBeforeArrival }
