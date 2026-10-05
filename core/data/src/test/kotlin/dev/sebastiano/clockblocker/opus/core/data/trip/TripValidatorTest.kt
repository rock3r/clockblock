package dev.sebastiano.clockblocker.opus.core.data.trip

import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Trip
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

class TripValidatorTest {
    private val validator = TripValidator()

    private fun leg(from: Place, to: Place, dep: LocalDateTime, arr: LocalDateTime, id: String = "l") =
        FlightLeg(id, from, to, dep, arr)

    /** A leg whose arrival is derived from a real block time, so it's consistent by construction. */
    private fun legFor(from: Place, to: Place, dep: LocalDateTime, block: Duration, id: String = "l") =
        FlightLeg(id, from, to, dep, dep.atZone(from.zone).toInstant().plus(block).atZone(to.zone).toLocalDateTime())

    @Test
    fun `demo trips are clean`() {
        DemoData.trips().forEach { validator.validate(it).issues.shouldBeEmpty() }
    }

    @Test
    fun `no flights is an error`() {
        validator.validate(emptyList<FlightLeg>()).issues shouldContainExactly listOf(TripIssue.NoFlights)
        validator.validate(emptyList<FlightLeg>()).isValid shouldBe false
    }

    @Test
    fun `same origin and destination is an error`() {
        val r = validator.validate(listOf(leg(DemoData.LHR, DemoData.LHR, at(15, 10), at(15, 12))))
        r.errors.single().shouldBeInstanceOf<TripIssue.SameOriginAndDestination>()
    }

    @Test
    fun `overnight flight entered with the departure date suggests the next day`() {
        // SFO 19:30 -> LHR "13:50" on the same date: arrival is before departure in real time.
        val r = validator.validate(listOf(leg(DemoData.SFO, DemoData.LHR, at(15, 19, 30), at(15, 13, 50))))
        val issue = r.errors.single().shouldBeInstanceOf<TripIssue.ArrivalNotAfterDeparture>()
        issue.suggestedArrivalLocal shouldBe at(16, 13, 50)
        r.isValid shouldBe false
    }

    @Test
    fun `date line typo LAX to SYD suggests the right day`() {
        // Real: LAX 22:30 Jun 15 -> SYD 06:30 Jun 17. Entered as Jun 16: arrival 9 h *before* departure.
        val r = validator.validate(listOf(leg(DemoData.LAX, SYD, at(15, 22, 30), at(16, 6, 30))))
        val issue = r.errors.single().shouldBeInstanceOf<TripIssue.ArrivalNotAfterDeparture>()
        issue.suggestedArrivalLocal shouldBe at(17, 6, 30)
    }

    @Test
    fun `crossing the date line westward is reported with the calendar jump`() {
        // LAX 22:30 Jun 15 -> SYD 06:30 Jun 17: +2 calendar days for a 15 h flight.
        val r = validator.validate(listOf(legFor(DemoData.LAX, SYD, at(15, 22, 30), Duration.ofHours(15))))
        r.isValid shouldBe true
        r.warnings.shouldBeEmpty()
        r.infos shouldContainExactly listOf(TripIssue.CrossesDateLine(0, 2))
    }

    @Test
    fun `crossing the date line eastward can land the day before`() {
        // AKL 00:30 Jun 16 -> HNL 11:15 Jun 15.
        val r = validator.validate(listOf(legFor(DemoData.AKL, DemoData.HNL, at(16, 0, 30), Duration.ofMinutes(8 * 60 + 45))))
        r.infos shouldContainExactly listOf(TripIssue.CrossesDateLine(0, -1))
        r.forLeg(0).single().legIndex shouldBe 0
    }

    @Test
    fun `date line typo that makes a flight too long suggests the previous day`() {
        // AKL 00:30 Jun 16 -> HNL entered as 11:15 Jun 16 (should be Jun 15): 32 h 45 min.
        val r = validator.validate(listOf(leg(DemoData.AKL, DemoData.HNL, at(16, 0, 30), at(16, 11, 15))))
        val issue = r.warnings.single().shouldBeInstanceOf<TripIssue.ImplausiblyLong>()
        issue.suggestedArrivalLocal shouldBe at(15, 11, 15)
    }

    @Test
    fun `implausibly short and long flights are warnings`() {
        val short = validator.validate(listOf(leg(DemoData.LHR, DemoData.CDG, at(15, 10), at(15, 11, 10))))
        // 10 min in real time (London is UTC+1, Paris UTC+2).
        val s = short.warnings.single().shouldBeInstanceOf<TripIssue.ImplausiblyShort>()
        s.duration shouldBe Duration.ofMinutes(10)
        s.suggestedArrivalLocal shouldBe null
        short.isValid shouldBe true

        val long = validator.validate(listOf(leg(DemoData.LHR, DemoData.CDG, at(15, 10), at(15, 20))))
        val l = long.warnings.single().shouldBeInstanceOf<TripIssue.ImplausiblyLong>()
        l.distanceKm shouldBe 348L
    }

    @Test
    fun `durations use real instants across a DST change`() {
        // Night of the EU spring-forward: LHR 00:30 GMT -> CDG 03:45 CEST is 1 h 15 min, not 3 h 15 min.
        val l = leg(DemoData.LHR, DemoData.CDG, LocalDateTime.of(2026, 3, 29, 0, 30), LocalDateTime.of(2026, 3, 29, 3, 45))
        l.duration shouldBe Duration.ofMinutes(75)
        validator.validate(listOf(l)).issues.shouldBeEmpty()
    }

    @Test
    fun `overlapping legs are an error`() {
        val first = legFor(DemoData.LHR, DemoData.SIN, at(15, 21, 35), Duration.ofMinutes(13 * 60 + 30), "a")
        val second = legFor(DemoData.SIN, SYD, first.arrivalLocal.minusHours(1), Duration.ofHours(8), "b")
        val r = validator.validate(listOf(first, second))
        r.errors shouldContainExactly listOf(TripIssue.LegsOverlap(1, Duration.ofHours(1)))
    }

    @Test
    fun `layover sanity`() {
        val first = legFor(DemoData.LHR, DemoData.SIN, at(15, 21, 35), Duration.ofMinutes(13 * 60 + 30), "a")
        fun after(gap: Duration) = legFor(DemoData.SIN, SYD, first.arrivalLocal.plus(gap), Duration.ofHours(8), "b")

        validator.validate(listOf(first, after(Duration.ofMinutes(20)))).warnings shouldContainExactly
            listOf(TripIssue.LayoverTooShort(1, Duration.ofMinutes(20), TripValidator.MinLayover))
        validator.validate(listOf(first, after(Duration.ofHours(30)))).warnings shouldContainExactly
            listOf(TripIssue.LayoverTooLong(1, Duration.ofHours(30), TripValidator.MaxLayover))
        validator.validate(listOf(first, after(Duration.ofHours(2)))).issues.shouldBeEmpty()
    }

    @Test
    fun `legs that don't connect are flagged`() {
        val first = legFor(DemoData.SFO, DemoData.LHR, at(15, 19, 30), Duration.ofHours(10), "a")
        val second = legFor(DemoData.LGW, DemoData.LIS, first.arrivalLocal.plusHours(4), Duration.ofHours(3), "b")
        validator.validate(listOf(first, second)).warnings shouldContainExactly
            listOf(TripIssue.LegsNotConnected(1, DemoData.LHR, DemoData.LGW))
    }

    @Test
    fun `validate trip delegates to legs`() {
        val trip = Trip("t", "x", listOf(leg(DemoData.SFO, DemoData.LHR, at(15, 19, 30), at(15, 13, 50))), Instant.EPOCH)
        validator.validate(trip) shouldBe validator.validate(trip.legs)
    }

    @Test
    fun `any consistently entered plausible flight passes without errors or warnings`() = runTest {
        val places = DemoData.places
        checkAll(300, Arb.element(places), Arb.element(places), Arb.long(0L..365L * 24 * 60), Arb.double(0.0..1.0)) { a, b, offsetMin, t ->
            if (a.code == b.code) return@checkAll
            val km = TripValidator.distanceKm(a, b)
            val min = TripValidator.minimumDuration(km).seconds
            val max = TripValidator.maximumDuration(km).seconds
            val block = Duration.ofSeconds(min + ((max - min) * t).toLong())
            val dep = LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(offsetMin)
            val leg = legFor(a, b, dep, block)
            // Arrival inside a DST fall-back hour is ambiguous as a local time; not a validator concern.
            if (leg.duration != block) return@checkAll
            val r = validator.validate(listOf(leg))
            r.errors.shouldBeEmpty()
            r.warnings.shouldBeEmpty()
        }
    }

    private companion object {
        val SYD = DemoData.SYD
        fun at(day: Int, hour: Int, minute: Int = 0): LocalDateTime = LocalDateTime.of(2026, 6, day, hour, minute)
    }
}
