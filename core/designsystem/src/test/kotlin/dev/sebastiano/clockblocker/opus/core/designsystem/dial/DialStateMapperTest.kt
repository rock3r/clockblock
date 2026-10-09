package dev.sebastiano.clockblocker.opus.core.designsystem.dial

import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PhasePoint
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.core.model.Sun
import dev.sebastiano.clockblocker.opus.core.model.SunDay
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DialStateMapperTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val t0 = Instant.parse("2026-10-10T05:00:00Z") // 14:00 Tokyo, 06:00 Lisbon (WEST)

    private fun advice(id: String, type: AdviceType, start: String, end: String) =
        Advice(id, type, Instant.parse(start), Instant.parse(end), AdviceReason.LightAdvancesClock)

    private fun plan(bodyOffsetMinutes: Int, cbtMin: Instant = Instant.parse("2026-10-10T03:30:00Z")) = JetLagPlan(
        tripId = "t",
        generatedAt = t0,
        strategy = AdaptationStrategy.entries.first(),
        direction = ShiftDirection.Advance,
        shiftHours = 8.0,
        originZoneId = "Europe/Lisbon",
        destinationZoneId = "Asia/Tokyo",
        days = listOf(
            PlanDay(
                index = 1,
                kind = DayKind.Arrival,
                date = LocalDate.of(2026, 10, 10),
                zoneId = "Asia/Tokyo",
                advice = listOf(
                    advice("a", AdviceType.SeeBrightLight, "2026-10-10T04:30:00Z", "2026-10-10T06:00:00Z"),
                    advice("b", AdviceType.AvoidLight, "2026-10-10T06:00:00Z", "2026-10-10T09:00:00Z"),
                    advice("c", AdviceType.Sleep, "2026-10-10T14:00:00Z", "2026-10-10T22:00:00Z"),
                    advice("d", AdviceType.Melatonin, "2026-10-10T12:30:00Z", "2026-10-10T12:30:00Z"),
                    // Entirely outside the ±12 h window: dropped.
                    advice("e", AdviceType.Caffeine, "2026-10-11T08:00:00Z", "2026-10-11T09:00:00Z"),
                ),
            ),
        ),
        phase = listOf(
            PhasePoint(t0.minusSeconds(3600), bodyOffsetMinutes, cbtMin),
            PhasePoint(t0.plusSeconds(3600), bodyOffsetMinutes, cbtMin),
        ),
        estimatedDaysToAdapt = 4.0,
        estimatedDaysWithoutPlan = 8.0,
    )

    @Test
    fun `local and body clocks come from the display zone and the phase trajectory`() {
        val state = plan(bodyOffsetMinutes = 60).toDialState(t0, tokyo)

        state.localTime shouldBe LocalTime.of(14, 0)
        state.bodyTime shouldBe LocalTime.of(6, 0)
        state.bodyAheadMinutes shouldBe (-480f plusOrMinus 0.01f)
        state.jetLagHours shouldBe (8f plusOrMinus 0.01f)
        state.isAligned shouldBe false
        state.displayZoneId shouldBe "Asia/Tokyo"
    }

    @Test
    fun `arcs are projected into display minutes and clipped to the 8 h past, 16 h future window`() {
        val state = plan(60).toDialState(t0, tokyo)

        state.arcs.map { it.adviceId } shouldBe listOf("a", "b", "d", "c")
        val light = state.arcs.first { it.adviceId == "a" }
        light.startMinute shouldBe (13.5f * 60 plusOrMinus 0.01f)
        light.sweepMinutes shouldBe (90f plusOrMinus 0.01f)
        light.isNow shouldBe true

        // Sleep 14:00Z–22:00Z ends after the window end (05:00Z + 16 h = 21:00Z) → clipped to 7 h.
        val sleep = state.arcs.first { it.adviceId == "c" }
        sleep.startMinute shouldBe (23f * 60 plusOrMinus 0.01f)
        sleep.sweepMinutes shouldBe (420f plusOrMinus 0.01f)
        sleep.isNow shouldBe false

        val melatonin = state.arcs.first { it.adviceId == "d" }
        melatonin.sweepMinutes shouldBe 0f
    }

    @Test
    fun `now and next narrate the current block and the following one`() {
        val state = plan(60).toDialState(t0, tokyo)

        val now = state.now.shouldNotBeNull()
        now.type shouldBe AdviceType.SeeBrightLight
        now.end shouldBe LocalTime.of(15, 0)
        val next = state.next.shouldNotBeNull()
        next.type shouldBe AdviceType.AvoidLight
        next.start shouldBe LocalTime.of(15, 0)
        next.end shouldBe LocalTime.of(18, 0)
    }

    @Test
    fun `cbt min and biological night are expressed in body time`() {
        // CBTmin at 03:30Z, body offset +1 h → 04:30 on the body clock.
        val state = plan(60).toDialState(t0, tokyo)
        state.cbtMinBodyMinute shouldBe (270f plusOrMinus 0.01f)
        state.biologicalNightStartBodyMinute shouldBe ((270f - 360f + 1440f) plusOrMinus 0.01f)
        state.biologicalNightEndBodyMinute shouldBe ((270f + 150f) plusOrMinus 0.01f)
    }

    @Test
    fun `adapted plan has aligned rings`() {
        val state = plan(bodyOffsetMinutes = 9 * 60).toDialState(t0, tokyo)
        state.bodyAheadMinutes shouldBe (0f plusOrMinus 0.01f)
        state.isAligned shouldBe true
        state.bodyTime shouldBe state.localTime
    }

    @Test
    fun `day metadata comes from the plan day containing the instant`() {
        val state = plan(60).toDialState(t0, tokyo)
        state.dayKind shouldBe DayKind.Arrival
        state.dayIndex shouldBe 1
    }

    @Test
    fun `instant outside every plan day has no day metadata and no advice`() {
        val later = Instant.parse("2026-12-01T00:00:00Z")
        val state = plan(60).toDialState(later, tokyo)
        state.dayKind.shouldBeNull()
        state.arcs shouldHaveSize 0
        state.now.shouldBeNull()
    }

    @Test
    fun `a clipped arc keeps its real end for narration`() {
        // A flight block from 1 h ago to 18 h from now: the arc stops at the window's end (+16 h), the words don't.
        val base = plan(60)
        val flight = advice("f", AdviceType.AvoidLight, "2026-10-10T04:00:00Z", "2026-10-10T23:00:00Z")
        val long = base.copy(days = listOf(base.days.single().copy(advice = listOf(flight))))
        val arc = long.toDialState(t0, tokyo).arcs.single()

        arc.sweepMinutes shouldBe (17 * 60f plusOrMinus 0.01f)
        arc.endMinute shouldBe (6 * 60f plusOrMinus 0.01f) // 06:00 Tokyo, the window's end
        arc.narratedEndMinute shouldBe (8 * 60f plusOrMinus 0.01f) // 08:00 Tokyo, when it really ends
        // The real instants, unclipped: what orders it against other advice.
        arc.startInstant shouldBe flight.start
        arc.endInstant shouldBe flight.end
    }

    @Test
    fun `the sky rings follow the real sun at the place`() {
        val haneda = Place("HND", "Haneda", "Tokyo", "JP", "Asia/Tokyo", 35.550, 139.787)
        val state = plan(bodyOffsetMinutes = 60).toDialState(t0, tokyo, haneda)

        val sun = Sun.on(LocalDate.of(2026, 10, 10), tokyo, 35.550, 139.787).shouldBeInstanceOf<SunDay.RisesAndSets>()
        state.daylight shouldBe Daylight.RisesAndSets
        state.sunriseMinute shouldBe (DialGeometry.minuteOfDay(sun.sunrise.atZone(tokyo).toLocalTime()) plusOrMinus 0.01f)
        state.sunsetMinute shouldBe (DialGeometry.minuteOfDay(sun.sunset.atZone(tokyo).toLocalTime()) plusOrMinus 0.01f)
        // USNO: 05:43 and 17:13 that day.
        state.sunriseMinute shouldBe (5 * 60f + 43f plusOrMinus 1f)
        state.sunsetMinute shouldBe (17 * 60f + 13f plusOrMinus 1f)
    }

    @Test
    fun `the dial is named after the place`() {
        val narita = Place("NRT", "Narita International Airport", "Narita", "JP", "Asia/Tokyo", 35.765, 140.386)
        plan(bodyOffsetMinutes = 60).toDialState(t0, tokyo, narita).placeName shouldBe "Narita"
        plan(bodyOffsetMinutes = 60).toDialState(t0, tokyo).placeName.shouldBeNull()
    }

    @Test
    fun `without a place the sky keeps its default sun`() {
        val state = plan(bodyOffsetMinutes = 60).toDialState(t0, tokyo)
        state.daylight shouldBe Daylight.RisesAndSets
        state.sunriseMinute shouldBe 390f
        state.sunsetMinute shouldBe 1140f
    }

    @Test
    fun `a polar day keeps the sun's noon so the sky can centre on it`() {
        val oslo = ZoneId.of("Europe/Oslo")
        val tromso = Place("TOS", "Tromsø", "Tromsø", "NO", "Europe/Oslo", 69.683, 18.919)
        val december = Instant.parse("2026-12-21T12:00:00Z")
        val night = plan(bodyOffsetMinutes = 60).toDialState(december, oslo, tromso)
        night.daylight shouldBe Daylight.AlwaysDown
        // Solar noon in Tromsø that day is 11:42–11:43 local.
        night.sunriseMinute shouldBe (11 * 60f + 42.5f plusOrMinus 2f)
        night.sunsetMinute shouldBe night.sunriseMinute

        val june = plan(bodyOffsetMinutes = 60).toDialState(Instant.parse("2026-06-21T12:00:00Z"), oslo, tromso)
        june.daylight shouldBe Daylight.AlwaysUp
    }
}
