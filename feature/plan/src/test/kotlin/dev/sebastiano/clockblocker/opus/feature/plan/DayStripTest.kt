package dev.sebastiano.clockblocker.opus.feature.plan

import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.realPlan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

class DayStripTest {

    private val london = ZoneId.of("Europe/London")
    private val now = PlanFixtures.MidAdaptation // Wed 17 Jun, 11:00 London (Day 2)
    private val days = realPlan.railDays(now, emptyMap())
    private fun day(kind: DayKind, index: Int) = days.first { it.day.kind == kind && it.day.index == index }
    private fun arrival(index: Int) = days.first { it.day.index == index && it.day.kind != DayKind.PreTrip && it.day.kind != DayKind.Travel }

    @Test
    fun `today previews now itself`() {
        arrival(2).previewInstant(now, london) shouldBe now
    }

    @Test
    fun `another day previews the same wall-clock time on that day, in that day's zone`() {
        arrival(3).previewInstant(now, london) shouldBe Instant.parse("2026-06-18T10:00:00Z")
        // Pre-trip days run on home time: 11:00 in Los Angeles.
        day(DayKind.PreTrip, -1).previewInstant(now, london) shouldBe Instant.parse("2026-06-14T18:00:00Z")
    }

    @Test
    fun `a time outside the day's span is kept inside it`() {
        // Day 1 starts at landing (13:50 London), so 11:00 that day would still be the travel day.
        val day1 = arrival(1)
        day1.previewInstant(now, london) shouldBe day1.start
        // Every preview stays inside its own day.
        days.forEach { day ->
            val preview = day.previewInstant(Instant.parse("2026-06-17T22:59:00Z"), london)
            (preview >= day.start && preview < day.end) shouldBe true
        }
    }

    @Test
    fun `picking today or nothing means live`() {
        selectedDayBase(days, selectedDay = null, now = now, nowZone = london).shouldBeNull()
        selectedDayBase(days, selectedDay = 2, now = now, nowZone = london).shouldBeNull()
        selectedDayBase(days, selectedDay = 3, now = now, nowZone = london) shouldBe Instant.parse("2026-06-18T10:00:00Z")
        // A day that is no longer in the plan is ignored.
        selectedDayBase(days, selectedDay = 99, now = now, nowZone = london).shouldBeNull()
    }

    @Test
    fun `the pill's body offset is taken at the day's local noon, and fades as the plan works`() {
        val pre = dayStripOffsets(realPlan, days)
        val first = pre.getValue(days.first().day.index)
        val last = pre.getValue(days.last().day.index)
        abs(last) shouldBeLessThan abs(first).coerceAtLeast(1f)
        abs(last) shouldBeLessThan 1f
    }
}
