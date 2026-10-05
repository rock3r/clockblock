package dev.sebastiano.clockblocker.opus.core.notifications.now

import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Flight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.SeeBrightLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Sleep
import dev.sebastiano.clockblocker.opus.core.notifications.advice
import dev.sebastiano.clockblocker.opus.core.notifications.planOf
import dev.sebastiano.clockblocker.opus.core.notifications.utc
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.nonNegativeInt
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TravelPlannerTest {

    // LHR 12:00Z -> HND 23:00Z, with a protocol around it.
    private val flight = advice(Flight, "2026-10-10T12:00", "2026-10-10T23:00", detail = "BA7")
    private val plan = planOf(
        flight,
        advice(SeeBrightLight, "2026-10-10T09:00", "2026-10-10T11:00"),
        advice(AvoidLight, "2026-10-10T14:00", "2026-10-10T17:00"),
        advice(Sleep, "2026-10-10T17:00", "2026-10-10T22:00"),
    )

    @Test
    fun `window spans pre-departure to post-arrival`() {
        val window = TravelPlanner.window(plan).shouldNotBeNull()

        window.start shouldBe utc("2026-10-10T09:00")
        window.end shouldBe utc("2026-10-11T01:00")
        window.minutes shouldBe 16 * 60
    }

    @Test
    fun `no flights, no travel window`() {
        TravelPlanner.window(planOf(advice(Sleep, "2026-10-10T22:00", "2026-10-11T06:00"))).shouldBeNull()
    }

    @Test
    fun `segments are coloured by headline advice and add up to the window`() {
        val progress = TravelPlanner.progress(plan, utc("2026-10-10T15:00")).shouldNotBeNull()

        progress.blocks shouldContainExactly listOf(
            ProgressBlock(SeeBrightLight, 120), // 09:00-11:00
            ProgressBlock(null, 60), // 11:00-12:00, nothing planned (before take-off)
            ProgressBlock(Flight, 120), // 12:00-14:00 on board
            ProgressBlock(AvoidLight, 180), // 14:00-17:00
            ProgressBlock(Sleep, 300), // 17:00-22:00
            ProgressBlock(Flight, 60), // 22:00-23:00
            ProgressBlock(null, 120), // 23:00-01:00 arrival
        )
        progress.blocks.sumOf { it.minutes } shouldBe progress.totalMinutes
        progress.progressMinutes shouldBe 6 * 60
    }

    @Test
    fun `points mark take-off and landing`() {
        val progress = TravelPlanner.progress(plan, utc("2026-10-10T15:00")).shouldNotBeNull()

        progress.marks shouldContainExactly listOf(
            ProgressMark(MarkKind.TakeOff, 180),
            ProgressMark(MarkKind.Landing, 840),
        )
    }

    @Test
    fun `multi-leg points are capped keeping first take-off and last landing`() {
        val legs = planOf(
            advice(Flight, "2026-10-10T06:00", "2026-10-10T08:00", id = "l1"),
            advice(Flight, "2026-10-10T10:00", "2026-10-10T12:00", id = "l2"),
            advice(Flight, "2026-10-10T14:00", "2026-10-10T16:00", id = "l3"),
        )
        val window = TravelPlanner.window(legs).shouldNotBeNull()

        val marks = TravelPlanner.marks(window)

        marks shouldHaveSize TravelPlanner.MAX_POINTS
        marks.first() shouldBe ProgressMark(MarkKind.TakeOff, 180)
        marks.last() shouldBe ProgressMark(MarkKind.Landing, 180 + 600)
    }

    @Test
    fun `segments are capped at the platform limit without losing length`() = runTest {
        checkAll(100, Arb.list(Arb.int(1, 120), 1..40)) { lengths ->
            val types = AdviceType.entries
            val blocks = lengths.mapIndexed { i, m -> ProgressBlock(types[i % types.size], m) }

            val capped = TravelPlanner.capSegments(blocks)

            (capped.size <= TravelPlanner.MAX_SEGMENTS) shouldBe true
            if (blocks.size <= TravelPlanner.MAX_SEGMENTS) capped shouldBe blocks
            capped.sumOf { it.minutes } shouldBe blocks.sumOf { it.minutes }
        }
    }

    @Test
    fun `live update only while a protocol window is active inside the travel window`() {
        TravelPlanner.isLiveUpdateActive(plan, utc("2026-10-10T15:00")) shouldBe true // Avoid light on board
        TravelPlanner.isLiveUpdateActive(plan, utc("2026-10-10T13:00")) shouldBe false // only the flight marker
        TravelPlanner.isLiveUpdateActive(plan, utc("2026-10-10T11:30")) shouldBe false // gap at the airport
        TravelPlanner.isLiveUpdateActive(plan, utc("2026-10-10T08:30")) shouldBe false // before the window
    }

    @Test
    fun `progress is null outside the window`() {
        TravelPlanner.progress(plan, utc("2026-10-11T01:00")).shouldBeNull()
    }

    @Test
    fun `chip counts down in the last hour, otherwise shows the end time`() = runTest {
        val until = utc("2026-10-10T18:00")

        TravelPlanner.chip(until, utc("2026-10-10T17:15")).shouldBeInstanceOf<LiveChip.Countdown>()
        TravelPlanner.chip(until, utc("2026-10-10T17:00")).shouldBeInstanceOf<LiveChip.Countdown>()
        TravelPlanner.chip(until, utc("2026-10-10T16:59")).shouldBeInstanceOf<LiveChip.EndsAt>()

        checkAll(Arb.nonNegativeInt(24 * 60)) { minutesLeft ->
            val chip = TravelPlanner.chip(until, until.minusSeconds(minutesLeft * 60L))
            (chip is LiveChip.Countdown) shouldBe (minutesLeft <= 60)
        }
    }
}
