package dev.sebastiano.clockblocker.opus.feature.trips.editor

import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Place
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Everything the stateless editor UI can ask for. Implemented by [TripEditorViewModel]; previews and
 * screenshot tests pass [NoOp].
 */
interface TripEditorActions {
    fun onTitleChange(title: String)
    fun onFlightNumberChange(legIndex: Int, value: String)
    fun onDepartureDateChange(legIndex: Int, date: LocalDate)
    fun onDepartureTimeChange(legIndex: Int, time: LocalTime)
    fun onArrivalDateChange(legIndex: Int, date: LocalDate)
    fun onArrivalTimeChange(legIndex: Int, time: LocalTime)
    fun onReturnDateChange(date: LocalDate)
    fun onReturnTimeChange(time: LocalTime)
    fun clearReturn()
    fun onStrategyChange(strategy: AdaptationStrategy?)
    fun addLeg()
    fun removeLeg(legIndex: Int)
    fun applySuggestedArrival(legIndex: Int, arrival: LocalDateTime)
    fun useConnectingOrigin(legIndex: Int)
    fun delayLeg(legIndex: Int, delay: Duration)
    fun onPlaceQueryChange(field: PlaceFieldRef, query: String)
    fun onPlaceSelected(field: PlaceFieldRef, place: Place)
    fun pickTopResult(field: PlaceFieldRef): Boolean
    fun dismissSearch()
    fun save()

    /** Does nothing; for previews and screenshots of fixed states. */
    object NoOp : TripEditorActions {
        override fun onTitleChange(title: String) = Unit
        override fun onFlightNumberChange(legIndex: Int, value: String) = Unit
        override fun onDepartureDateChange(legIndex: Int, date: LocalDate) = Unit
        override fun onDepartureTimeChange(legIndex: Int, time: LocalTime) = Unit
        override fun onArrivalDateChange(legIndex: Int, date: LocalDate) = Unit
        override fun onArrivalTimeChange(legIndex: Int, time: LocalTime) = Unit
        override fun onReturnDateChange(date: LocalDate) = Unit
        override fun onReturnTimeChange(time: LocalTime) = Unit
        override fun clearReturn() = Unit
        override fun onStrategyChange(strategy: AdaptationStrategy?) = Unit
        override fun addLeg() = Unit
        override fun removeLeg(legIndex: Int) = Unit
        override fun applySuggestedArrival(legIndex: Int, arrival: LocalDateTime) = Unit
        override fun useConnectingOrigin(legIndex: Int) = Unit
        override fun delayLeg(legIndex: Int, delay: Duration) = Unit
        override fun onPlaceQueryChange(field: PlaceFieldRef, query: String) = Unit
        override fun onPlaceSelected(field: PlaceFieldRef, place: Place) = Unit
        override fun pickTopResult(field: PlaceFieldRef): Boolean = false
        override fun dismissSearch() = Unit
        override fun save() = Unit
    }
}
