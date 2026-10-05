package dev.sebastiano.clockblocker.opus.core.circadian

import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shared builders for planner tests. */
object Fixtures {
    fun place(code: String, zone: String) = Place(code, code, code, "XX", zone, 0.0, 0.0)

    val SFO = place("SFO", "America/Los_Angeles")
    val LHR = place("LHR", "Europe/London")
    val SIN = place("SIN", "Asia/Singapore")
    val SYD = place("SYD", "Australia/Sydney")
    val JFK = place("JFK", "America/New_York")
    val LAX = place("LAX", "America/Los_Angeles")
    val NRT = place("NRT", "Asia/Tokyo")
    val CDG = place("CDG", "Europe/Paris")
    val CXI = place("CXI", "Pacific/Kiritimati")
    val HNL = place("HNL", "Pacific/Honolulu")
    val KTM = place("KTM", "Asia/Kathmandu")
    val CHT = place("CHT", "Pacific/Chatham")
    val DEL = place("DEL", "Asia/Kolkata")

    fun leg(id: String, from: Place, to: Place, dep: String, arr: String, number: String? = null) =
        FlightLeg(id, from, to, LocalDateTime.parse(dep), LocalDateTime.parse(arr), number)

    fun trip(vararg legs: FlightLeg, id: String = "trip-1", returnDeparture: Instant? = null) =
        Trip(id, "Test", legs.toList(), Instant.parse("2026-01-01T00:00:00Z"), returnDeparture = returnDeparture)

    fun profile(
        home: Place,
        chronotype: Chronotype = Chronotype.Intermediate,
        melatonin: Boolean = true,
        caffeine: Boolean = true,
        sleepOnPlanes: Boolean = true,
        preAdjust: Boolean = true,
        intensity: Intensity = Intensity.Balanced,
        sleep: SleepWindow = SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 0)),
    ) = UserProfile(home.zoneId, sleep, chronotype, melatonin, caffeine, sleepOnPlanes, preAdjust, intensity)

    val sfoLhr = trip(leg("1", SFO, LHR, "2026-06-15T16:30", "2026-06-16T10:45", "BA286"))
    val lhrSyd = trip(
        leg("1", LHR, SIN, "2026-11-10T21:00", "2026-11-11T17:30"),
        leg("2", SIN, SYD, "2026-11-11T20:00", "2026-11-12T06:30"),
    )
    val jfkLax = trip(leg("1", JFK, LAX, "2026-03-20T08:00", "2026-03-20T11:20"))
    val nrtJfk = trip(leg("1", NRT, JFK, "2026-01-15T11:00", "2026-01-15T10:30"))

    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    /** Formats like the §14 tables: home clock before departure, destination clock from departure on. */
    fun JetLagPlan.windows(type: AdviceType, trip: Trip): List<String> = allAdvice.filter { it.type == type }.sortedBy { it.start }.map { a ->
        val zone = zoneFor(a, trip)
        if (a.type.isMoment) a.start.atZone(zone).format(hm) else "${a.start.atZone(zone).format(hm)}-${a.end.atZone(zone).format(hm)}"
    }

    private fun zoneFor(a: Advice, trip: Trip): ZoneId = if (a.start.isBefore(trip.departure)) trip.origin.zone else trip.destination.zone

    val NOW: Instant = Instant.parse("2026-01-01T00:00:00Z")
}
