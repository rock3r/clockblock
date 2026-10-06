package dev.sebastiano.clockblocker.opus.feature.plan

import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.realPlan
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class RailRowsTest {

    private val day2 = LocalDate.parse("2026-06-17")
    private fun at(time: String): Instant = Instant.parse("2026-06-17T${time}:00Z")
    private fun advice(id: String, type: AdviceType, start: String, end: String) =
        Advice(id, type, at(start), at(end), AdviceReason.LightAdvancesClock)

    private fun plan(vararg days: PlanDay) = JetLagPlan(
        tripId = "t",
        generatedAt = at("00:00"),
        strategy = AdaptationStrategy.Adapt,
        direction = ShiftDirection.Advance,
        shiftHours = 8.0,
        originZoneId = "America/Los_Angeles",
        destinationZoneId = "UTC",
        days = days.toList(),
        phase = emptyList(),
        estimatedDaysToAdapt = 3.0,
        estimatedDaysWithoutPlan = 6.0,
    )

    private fun rows(plan: JetLagPlan, now: Instant): List<RailRow> =
        buildRailRows(plan.railDays(now, emptyMap()), now, showEarlier = true)

    @Test
    fun `a short lower-priority block inside a longer one nests under it, so the time column never repeats`() {
        val light = advice("light", AdviceType.SeeBrightLight, "10:00", "13:30")
        val coffee = advice("coffee", AdviceType.Caffeine, "10:00", "10:30")
        val later = advice("later", AdviceType.SeeLight, "13:30", "17:30")
        val blocks = rows(plan(PlanDay(1, DayKind.Arrival, day2, "UTC", listOf(light, coffee, later))), at("08:00"))
            .filterIsInstance<RailRow.Block>()

        blocks.map { it.item.advice.id } shouldContainExactly listOf("light", "later")
        blocks[0].children.map { it.advice.id } shouldContainExactly listOf("coffee")
        blocks[1].children.shouldBeEmpty()
    }

    @Test
    fun `a more important block inside a longer one keeps its own row`() {
        val noCoffee = advice("no-coffee", AdviceType.AvoidCaffeine, "17:00", "23:00")
        val dark = advice("dark", AdviceType.AvoidLight, "22:00", "23:00")
        val blocks = rows(plan(PlanDay(1, DayKind.Arrival, day2, "UTC", listOf(noCoffee, dark))), at("08:00"))
            .filterIsInstance<RailRow.Block>()

        blocks.map { it.item.advice.id } shouldContainExactly listOf("no-coffee", "dark")
    }

    @Test
    fun `a block sharing the previous row's start hides its start time`() {
        // Same start, but it outlasts the first block, so it can't nest: the time column shows 13:30 once.
        val light = advice("light", AdviceType.SeeLight, "13:30", "17:30")
        val noCoffee = advice("no-coffee", AdviceType.AvoidCaffeine, "13:30", "23:00")
        val blocks = rows(plan(PlanDay(1, DayKind.Arrival, day2, "UTC", listOf(light, noCoffee))), at("08:00"))
            .filterIsInstance<RailRow.Block>()

        blocks.map { it.showTime } shouldContainExactly listOf(true, false)
    }

    @Test
    fun `the sky band of each row ends where the next row starts, so the strip is continuous`() {
        val light = advice("light", AdviceType.SeeBrightLight, "10:00", "13:30")
        val coffee = advice("coffee", AdviceType.Caffeine, "10:00", "10:30")
        val noCoffee = advice("no-coffee", AdviceType.AvoidCaffeine, "12:00", "23:00")
        val blocks = rows(plan(PlanDay(1, DayKind.Arrival, day2, "UTC", listOf(light, coffee, noCoffee))), at("08:00"))
            .filterIsInstance<RailRow.Block>()

        blocks[0].bandEnd shouldBe at("12:00")
        blocks[1].bandEnd shouldBe at("23:00")
    }

    @Test
    fun `now inside a parent keeps the marker on the parent row`() {
        val light = advice("light", AdviceType.SeeBrightLight, "10:00", "14:00")
        val coffee = advice("coffee", AdviceType.Caffeine, "10:00", "10:30")
        val now = at("11:00")
        val block = rows(plan(PlanDay(1, DayKind.Arrival, day2, "UTC", listOf(light, coffee))), now)
            .filterIsInstance<RailRow.Block>().single()

        block.nowFraction shouldBe 0.25f
    }

    @Test
    fun `blocks that start while airborne are marked in flight`() {
        val flight = advice("flight", AdviceType.Flight, "02:00", "12:00")
        val sleep = advice("sleep", AdviceType.Sleep, "04:00", "09:00")
        val after = advice("after", AdviceType.SeeBrightLight, "12:00", "14:00")
        val items = plan(PlanDay(0, DayKind.Travel, day2, "UTC", listOf(flight, sleep, after)))
            .railDays(at("00:00"), emptyMap()).single().items

        items.associate { it.advice.id to it.inFlight } shouldBe mapOf("flight" to false, "sleep" to true, "after" to false)
    }

    @Test
    fun `the displayed zone switching between days gets a divider row before the new day`() {
        val all = rows(realPlan, PlanFixtures.MidAdaptation.minusSeconds(86_400L * 10))
        val switch = all.filterIsInstance<RailRow.ZoneSwitch>()
        switch shouldHaveSize 1
        val row = switch.single()
        row.from.id shouldBe realPlan.originZoneId
        row.to.id shouldBe realPlan.destinationZoneId
        // Right before the first day shown in the destination zone.
        val next = all[all.indexOf(row) + 1]
        next.shouldBeInstanceOf<RailRow.Header>()
        next.day.zone.id shouldBe realPlan.destinationZoneId
        all[all.indexOf(row) - 1].shouldBeInstanceOf<RailRow.Block>().day.zone.id shouldBe realPlan.originZoneId
    }

    @Test
    fun `no divider when the earlier day is folded away`() {
        // Mid-adaptation: travel and pre-trip days are folded behind "Show earlier days".
        val rows = buildRailRows(realPlan.railDays(PlanFixtures.MidAdaptation, emptyMap()), PlanFixtures.MidAdaptation, showEarlier = false)
        rows.filterIsInstance<RailRow.ZoneSwitch>().shouldBeEmpty()
        rows.filterIsInstance<RailRow.Header>().firstOrNull().shouldNotBeNull()
    }
}
