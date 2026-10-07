package dev.sebastiano.clockblocker.opus.core.designsystem.dial

import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PhasePoint
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
    fun `arcs span the wall clock across a DST change`() {
        // New York springs forward at 02:00 on 8 March 2026: sleep 01:00 EST to 04:00 EDT is 2 h long but covers
        // 3 h of the clock face, and the dial is a clock face.
        val newYork = ZoneId.of("America/New_York")
        val base = plan(60)
        val sleep = advice("s", AdviceType.Sleep, "2026-03-08T06:00:00Z", "2026-03-08T08:00:00Z")
        val dst = base.copy(days = listOf(base.days.single().copy(date = LocalDate.of(2026, 3, 8), zoneId = newYork.id, advice = listOf(sleep))))
        val state = dst.toDialState(Instant.parse("2026-03-08T07:30:00Z"), newYork)

        val arc = state.arcs.single()
        arc.startMinute shouldBe (60f plusOrMinus 0.01f)
        arc.sweepMinutes shouldBe (180f plusOrMinus 0.01f)
        arc.endMinute shouldBe (240f plusOrMinus 0.01f)
    }
}
