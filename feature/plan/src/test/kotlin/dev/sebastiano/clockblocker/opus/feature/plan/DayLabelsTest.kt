package dev.sebastiano.clockblocker.opus.feature.plan

import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Days counted around the flight: "−2 −1 ✈ +1", the weekday under each, an overnight travel day "Fri–Sat". */
class DayLabelsTest {

    private val la = ZoneId.of("America/Los_Angeles")
    private val london = ZoneId.of("Europe/London")

    private fun day(index: Int, kind: DayKind, date: LocalDate, advice: List<Advice> = emptyList()) =
        PlanDay(index, kind, date, la.id, advice)

    private fun flight(id: String, start: ZonedDateTime, end: ZonedDateTime) =
        Advice(id, AdviceType.Flight, start.toInstant(), end.toInstant(), AdviceReason.TravelMarker)

    /** Los Angeles until 18:00 UTC on 16 Jun 2026, London after: a stand-in for the plan's zones. */
    /** What a day pill shows under its number: [weekdaySpan] of [dayDates]. */
    private fun dayWeekdays(day: PlanDay, zoneAt: (Instant) -> ZoneId, locale: Locale): String =
        dayDates(day, zoneAt).let { (from, to) -> weekdaySpan(from, to, locale) }

    private fun zoneAt(landing: Instant): (Instant) -> ZoneId = { at -> if (at.isBefore(landing)) la else london }

    @Test
    fun `days before and after the flight are signed, the travel day has no number`() {
        dayStripNumber(day(-2, DayKind.PreTrip, LocalDate.of(2026, 6, 13))) shouldBe "\u22122"
        dayStripNumber(day(-1, DayKind.PreTrip, LocalDate.of(2026, 6, 14))) shouldBe "\u22121"
        dayStripNumber(day(0, DayKind.Travel, LocalDate.of(2026, 6, 15))).shouldBeNull()
        dayStripNumber(day(1, DayKind.Arrival, LocalDate.of(2026, 6, 16))) shouldBe "+1"
        dayStripNumber(day(4, DayKind.Adapted, LocalDate.of(2026, 6, 19))) shouldBe "+4"
    }

    @Test
    fun `an ordinary day reads its weekday`() {
        val d = day(-1, DayKind.PreTrip, LocalDate.of(2026, 6, 14))
        dayWeekdays(d, zoneAt(Instant.MAX), Locale.UK) shouldBe "Sun"
    }

    @Test
    fun `an overnight travel day spans the departure and landing weekdays`() {
        // SFO Mon 19:30 → LHR Tue 13:50.
        val out = flight("f", ZonedDateTime.of(2026, 6, 15, 19, 30, 0, 0, la), ZonedDateTime.of(2026, 6, 16, 13, 50, 0, 0, london))
        val d = day(0, DayKind.Travel, LocalDate.of(2026, 6, 15), listOf(out))
        dayWeekdays(d, zoneAt(out.end), Locale.UK) shouldBe "Mon\u2013Tue"
    }

    @Test
    fun `a same-day travel day reads one weekday`() {
        // LHR 08:00 → JFK 11:00 the same Monday, local times.
        val ny = ZoneId.of("America/New_York")
        val out = Advice(
            "f", AdviceType.Flight,
            ZonedDateTime.of(2026, 6, 15, 8, 0, 0, 0, london).toInstant(),
            ZonedDateTime.of(2026, 6, 15, 11, 0, 0, 0, ny).toInstant(),
            AdviceReason.TravelMarker,
        )
        val d = PlanDay(0, DayKind.Travel, LocalDate.of(2026, 6, 15), london.id, listOf(out))
        dayWeekdays(d, { at -> if (at.isBefore(out.end)) london else ny }, Locale.UK) shouldBe "Mon"
    }

    @Test
    fun `connections span from the first take-off to the last landing`() {
        val first = flight("a", ZonedDateTime.of(2026, 6, 15, 21, 0, 0, 0, la), ZonedDateTime.of(2026, 6, 15, 23, 0, 0, 0, la))
        val second = flight("b", ZonedDateTime.of(2026, 6, 16, 1, 0, 0, 0, la), ZonedDateTime.of(2026, 6, 16, 18, 0, 0, 0, london))
        val d = day(0, DayKind.Travel, LocalDate.of(2026, 6, 15), listOf(second, first))
        dayWeekdays(d, zoneAt(second.end), Locale.UK) shouldBe "Mon\u2013Tue"
    }

    @Test
    fun `a landing just after the clocks change still lands on the right day`() {
        // Europe springs forward at 01:00 UTC on Sun 29 Mar 2026: 00:30 UTC is 00:30 GMT on Sunday, 01:30 UTC is 02:30 BST.
        val out = Advice(
            "f", AdviceType.Flight,
            ZonedDateTime.of(2026, 3, 28, 17, 0, 0, 0, la).toInstant(),
            Instant.parse("2026-03-29T01:30:00Z"),
            AdviceReason.TravelMarker,
        )
        val d = day(0, DayKind.Travel, LocalDate.of(2026, 3, 28), listOf(out))
        dayWeekdays(d, zoneAt(out.end), Locale.UK) shouldBe "Sat\u2013Sun"
    }

    @Test
    fun `crossing the date line to an earlier calendar date keeps the landing weekday`() {
        // Auckland Mon 15 Jun 09:00 (+12) → Honolulu Sun 14 Jun 19:30 (−10).
        val auckland = ZoneId.of("Pacific/Auckland")
        val honolulu = ZoneId.of("Pacific/Honolulu")
        val out = flight(
            "f",
            ZonedDateTime.of(2026, 6, 15, 9, 0, 0, 0, auckland),
            ZonedDateTime.of(2026, 6, 14, 19, 30, 0, 0, honolulu),
        )
        val d = PlanDay(0, DayKind.Travel, LocalDate.of(2026, 6, 15), auckland.id, listOf(out))
        val zones: (Instant) -> ZoneId = { at -> if (at.isBefore(out.end)) auckland else honolulu }
        dayDates(d, zones) shouldBe (LocalDate.of(2026, 6, 15) to LocalDate.of(2026, 6, 14))
        dayWeekdays(d, zones, Locale.UK) shouldBe "Mon\u2013Sun"
    }

    @Test
    fun `a travel day without flights falls back to its own date`() {
        dayWeekdays(day(0, DayKind.Travel, LocalDate.of(2026, 6, 15)), zoneAt(Instant.MAX), Locale.UK) shouldBe "Mon"
    }
}
