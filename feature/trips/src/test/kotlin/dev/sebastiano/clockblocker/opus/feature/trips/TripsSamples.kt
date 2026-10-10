package dev.sebastiano.clockblocker.opus.feature.trips

import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.data.trip.TripTitleSuggester
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import dev.sebastiano.clockblocker.opus.feature.trips.list.TripPhase
import dev.sebastiano.clockblocker.opus.feature.trips.list.TripSummaries
import dev.sebastiano.clockblocker.opus.feature.trips.list.TripsUiState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Deterministic trips in every phase, for screenshots and UI tests. */
object TripsSamples {
    /** Mid-trip for SFO → LHR (day 2 of 4). */
    val Now: Instant = Instant.parse("2026-06-17T12:00:00Z")

    val lisbonTokyo: Trip = trip(
        id = "lis-hnd",
        from = DemoData.LIS,
        to = DemoData.HND,
        departure = LocalDateTime.of(2026, 4, 2, 10, 5),
        block = Duration.ofMinutes(14 * 60 + 25),
        title = "Lisbon → Tokyo",
    )

    val newYorkParis: Trip = trip(
        id = "jfk-cdg",
        from = DemoData.JFK,
        to = DemoData.CDG,
        departure = LocalDateTime.of(2026, 7, 9, 18, 40),
        block = Duration.ofMinutes(7 * 60 + 15),
        title = "",
    )

    val trips: List<Trip> = listOf(DemoData.sfoToLhr(), DemoData.lhrToSydneyViaSingapore(), newYorkParis, lisbonTokyo)

    fun state(now: Instant = Now, trips: List<Trip> = this.trips): TripsUiState {
        val planner = FakeJetLagPlanner()
        val titles = TripTitleSuggester()
        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        val summaries = trips.map { trip ->
            TripSummaries.summarize(trip, planner.plan(trip, DemoData.profile, now), now, today, titles.suggest(trip))
        }
        val sections = TripSummaries.sections(summaries)
        return TripsUiState(
            loading = false,
            inProgress = sections.getValue(TripPhase.InProgress),
            upcoming = sections.getValue(TripPhase.Upcoming),
            past = sections.getValue(TripPhase.Past),
            returnCandidate = TripSummaries.returnCandidate(summaries),
            sky = TripSummaries.sky(sections.getValue(TripPhase.InProgress), now, ZoneOffset.UTC),
        )
    }

    /** LIS → HND whose arrival was typed on the departure date: lands "before" it leaves. */
    fun lisbonTokyoWrongDate(): Trip = lisbonTokyo.copy(
        id = "lis-hnd-typo",
        legs = lisbonTokyo.legs.map { it.copy(arrivalLocal = it.arrivalLocal.minusDays(1)) },
    )

    /**
     * NYC → CDG for a Los Angeles resident who only just got to New York: the body clock still starts on home time
     * (issue #9). Not in [trips], so the list goldens don't change.
     */
    fun newYorkParisFromHome(): Trip = newYorkParis.copy(id = "jfk-cdg-from-home", title = "New York → Paris", bodyClockStartZoneId = "America/Los_Angeles")

    private fun trip(id: String, from: dev.sebastiano.clockblocker.opus.core.model.Place, to: dev.sebastiano.clockblocker.opus.core.model.Place, departure: LocalDateTime, block: Duration, title: String): Trip {
        val arrival = departure.atZone(from.zone).toInstant().plus(block).atZone(to.zone).toLocalDateTime()
        return Trip(
            id = id,
            title = title,
            legs = listOf(FlightLeg("$id-1", from, to, departure, arrival)),
            createdAt = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(),
        )
    }
}
