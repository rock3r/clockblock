package dev.sebastiano.clockblocker.opus.core.data.export

import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class IcsExporterTest {
    private val exporter = IcsExporter()
    private val trip = DemoData.sfoToLhr()
    private val plan = FakeJetLagPlanner().plan(trip, DemoData.profile, Instant.parse("2026-06-01T12:00:00Z"))

    /** Unfolded content lines. */
    private fun lines(ics: String) = ics.replace("\r\n ", "").split("\r\n").dropLast(1)

    @Test
    fun `calendar structure is valid RFC 5545`() {
        val ics = exporter.export(plan, trip)
        ics shouldStartWith "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"
        ics shouldEndWith "END:VCALENDAR\r\n"
        ics.replace("\r\n", "") shouldNotContain "\n"
        ics.split("\r\n").forEach { (it.encodeToByteArray().size <= 75) shouldBe true }
        val l = lines(ics)
        l.count { it == "BEGIN:VEVENT" } shouldBe plan.allAdvice.size
        l.count { it == "END:VEVENT" } shouldBe plan.allAdvice.size
        l shouldContainAll listOf("PRODID:-//Opus Clockblock//Jet lag plan//EN", "X-WR-CALNAME:San Francisco → London")
        l.filter { it.startsWith("DTSTAMP:") }.toSet() shouldBe setOf("DTSTAMP:20260601T120000Z")
    }

    @Test
    fun `events use the local zone of their plan day`() {
        val l = lines(exporter.export(plan, trip))
        // Pre-trip light window at 07:00 in San Francisco.
        l shouldContainAll listOf(
            "DTSTART;TZID=America/Los_Angeles:20260613T070000",
            "DTEND;TZID=America/Los_Angeles:20260613T090000",
        )
        // The flight, shown in the travel day's zone; then London nights at 23:00 local.
        l shouldContainAll listOf(
            "DTSTART;TZID=America/Los_Angeles:20260615T193000",
            "DTEND;TZID=America/Los_Angeles:20260616T055000",
            "SUMMARY:Flight (BA 286)",
            "DTSTART;TZID=Europe/London:20260616T230000",
        )
    }

    @Test
    fun `every referenced zone has a VTIMEZONE with the right offsets`() {
        val l = lines(exporter.export(plan, trip))
        l shouldContainAll listOf("TZID:America/Los_Angeles", "TZID:Europe/London")
        val la = l.subList(l.indexOf("TZID:America/Los_Angeles"), l.size).takeWhile { it != "END:VTIMEZONE" }
        la shouldContainAll listOf("BEGIN:DAYLIGHT", "DTSTART:20260308T020000", "TZOFFSETFROM:-0800", "TZOFFSETTO:-0700")
        val london = l.subList(l.indexOf("TZID:Europe/London"), l.size).takeWhile { it != "END:VTIMEZONE" }
        london shouldContainAll listOf("DTSTART:20260329T010000", "TZOFFSETFROM:+0000", "TZOFFSETTO:+0100")
    }

    @Test
    fun `uids are stable and labels are always present`() {
        val a = exporter.export(plan, trip)
        a shouldBe exporter.export(plan, trip)
        val l = lines(a)
        l shouldContainAll plan.allAdvice.map { "UID:${it.id}.${plan.tripId}@opusclockblock.app" }
        l.count { it.startsWith("SUMMARY:") } shouldBe plan.allAdvice.size
        l shouldContainAll listOf("SUMMARY:See bright light", "SUMMARY:Sleep", "SUMMARY:Avoid light")
        l.first { it.startsWith("DESCRIPTION:") } shouldContain "Not medical advice."
    }

    @Test
    fun `filter, reminders and custom labels`() {
        val ics = exporter.export(
            plan,
            trip,
            labels = ExportLabels(adviceTitle = { "T-${it.name}" }, disclaimer = "D"),
            include = { it.type == AdviceType.Sleep },
            reminderMinutesBefore = 15,
        )
        val l = lines(ics)
        l.count { it == "BEGIN:VEVENT" } shouldBe plan.allAdvice.count { it.type == AdviceType.Sleep }
        l.filter { it.startsWith("SUMMARY:") }.toSet() shouldBe setOf("SUMMARY:T-Sleep")
        l.count { it == "TRIGGER:-PT15M" } shouldBe plan.allAdvice.count { it.type == AdviceType.Sleep }
    }

    @Test
    fun `moments have no end and fixed-offset zones get a single observance`() {
        val t = Instant.parse("2026-06-15T20:00:00Z")
        val momentPlan = JetLagPlan(
            tripId = "t", generatedAt = t, strategy = AdaptationStrategy.Adapt, direction = ShiftDirection.None,
            shiftHours = 0.0, originZoneId = "UTC", destinationZoneId = "UTC",
            days = listOf(
                PlanDay(0, DayKind.Travel, LocalDate.of(2026, 6, 15), "UTC", listOf(Advice("m", AdviceType.Melatonin, t, t, AdviceReason.MelatoninAdvances, "0.5 mg"))),
            ),
            phase = emptyList(), estimatedDaysToAdapt = 0.0, estimatedDaysWithoutPlan = 0.0,
        )
        val l = lines(exporter.export(momentPlan))
        l shouldContainAll listOf("DTSTART;TZID=UTC:20260615T200000", "SUMMARY:Take melatonin (0.5 mg)", "X-WR-CALNAME:Jet lag plan")
        l.none { it.startsWith("DTEND") } shouldBe true
        l shouldContainAll listOf("BEGIN:STANDARD", "DTSTART:19700101T000000", "TZOFFSETFROM:+0000", "TZOFFSETTO:+0000")
    }

    @Test
    fun `text escaping`() {
        IcsExporter.escape("a,b;c\\d\ne\r") shouldBe "a\\,b\\;c\\\\d\\ne"
    }

    @Test
    fun `folding never splits a multi-byte character and unfolds losslessly`() {
        val line = "SUMMARY:" + "é😀".repeat(40)
        val folded = IcsExporter.fold(line)
        folded.split("\r\n").forEach { (it.encodeToByteArray().size <= 75) shouldBe true }
        folded.split("\r\n").drop(1).forEach { it shouldStartWith " " }
        folded.replace("\r\n ", "") shouldBe line
        IcsExporter.fold("SHORT") shouldBe "SHORT"
    }
}
