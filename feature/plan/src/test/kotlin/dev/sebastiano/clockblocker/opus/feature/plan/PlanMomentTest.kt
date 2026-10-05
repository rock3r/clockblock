package dev.sebastiano.clockblocker.opus.feature.plan

import dev.sebastiano.clockblocker.opus.core.circadian.daySpans
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.fakePlan
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.realPlan
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class PlanMomentTest {

    @Test
    fun `before the plan it is upcoming, in the origin zone`() {
        val moment = fakePlan.momentAt(Instant.parse("2026-06-12T16:00:00Z"))
        moment.stage shouldBe PlanStage.Upcoming
        moment.zone.id shouldBe "America/Los_Angeles"
        moment.secondaryZone.id shouldBe "Europe/London"
        moment.day.shouldBeNull()
    }

    @Test
    fun `pre-trip morning shows bright light in home time`() {
        val moment = fakePlan.momentAt(PlanFixtures.FakePreTrip)
        moment.stage shouldBe PlanStage.PreTrip
        moment.day?.index shouldBe -1
        moment.zone.id shouldBe "America/Los_Angeles"
        moment.active?.type shouldBe AdviceType.SeeBrightLight
        moment.remaining shouldBe Duration.ofMinutes(90)
        moment.upNext.shouldNotBeEmpty()
        moment.upNext.all { it.start.isAfter(PlanFixtures.FakePreTrip) }.shouldBeTrue()
    }

    @Test
    fun `in flight the flight is the active block on the travel day`() {
        val moment = fakePlan.momentAt(PlanFixtures.FakeInFlight)
        moment.stage shouldBe PlanStage.Travel
        moment.day?.kind shouldBe DayKind.Travel
        moment.active?.type shouldBe AdviceType.Flight
    }

    @Test
    fun `arrival days are adapting and switch to destination time`() {
        val moment = fakePlan.momentAt(PlanFixtures.FakeArrival)
        moment.stage shouldBe PlanStage.Adapting
        moment.day?.index shouldBe 2
        moment.zone.id shouldBe "Europe/London"
        moment.secondaryZone.id shouldBe "America/Los_Angeles"
        moment.active?.type shouldBe AdviceType.SeeBrightLight
    }

    @Test
    fun `the adapted day is adapted and due a celebration`() {
        val moment = fakePlan.momentAt(PlanFixtures.FakeAdapted)
        moment.stage shouldBe PlanStage.Adapted
        fakePlan.isCelebrationDue(PlanFixtures.FakeAdapted).shouldBeTrue()
    }

    @Test
    fun `no celebration before landing nor long after the plan`() {
        fakePlan.isCelebrationDue(PlanFixtures.FakePreTrip).shouldBeFalse()
        fakePlan.isCelebrationDue(PlanFixtures.FakeInFlight).shouldBeFalse()
        val end = fakePlan.daySpans().last().end
        fakePlan.isCelebrationDue(end.plus(Duration.ofDays(3))).shouldBeFalse()
        fakePlan.momentAt(end.plus(Duration.ofDays(3))).stage shouldBe PlanStage.Complete
    }

    @Test
    fun `avoid light asks for night-safe`() {
        val moment = fakePlan.momentAt(PlanFixtures.FakeAvoidLight)
        moment.active?.type shouldBe AdviceType.AvoidLight
        moment.wantsNightSafe.shouldBeTrue()
        fakePlan.momentAt(PlanFixtures.FakeArrival).wantsNightSafe.shouldBeFalse()
    }

    @Test
    fun `the real plan's body clock is behind on arrival and in sync once adapted`() {
        val arrival = realPlan.momentAt(PlanFixtures.MidAdaptation)
        (arrival.bodyAheadHours < -0.5f).shouldBeTrue()
        arrival.progress shouldBeBetween (0f to 0.95f)
        val adapted = realPlan.momentAt(PlanFixtures.Adapted.plus(Duration.ofDays(1)))
        (kotlin.math.abs(adapted.bodyAheadHours) < 1.5f).shouldBeTrue()
    }

    @Test
    fun `plan kinds`() {
        fakePlan.kind shouldBe PlanKind.Adapt
        fakePlan.copy(shiftHours = 0.5, direction = ShiftDirection.None).kind shouldBe PlanKind.NoShift
        fakePlan.copy(strategy = AdaptationStrategy.StayOnHomeTime).kind shouldBe PlanKind.StayOnHomeTime
        fakePlan.copy(strategy = AdaptationStrategy.StayOnHomeTime).isCelebrationDue(PlanFixtures.FakeAdapted).shouldBeFalse()
    }

    @Test
    fun `active advice is what momentAt reports, for any instant`() = runTest {
        val spans = realPlan.daySpans()
        val from = spans.first().start.minus(Duration.ofDays(1)).epochSecond
        val to = spans.last().end.plus(Duration.ofDays(1)).epochSecond
        checkAll(200, Arb.long(from..to)) { seconds ->
            val instant = Instant.ofEpochSecond(seconds)
            val moment = realPlan.momentAt(instant)
            val active = realPlan.activeAdviceAt(instant)
            moment.active shouldBe active.firstOrNull()
            moment.remaining.isNegative.shouldBeFalse()
            moment.upNext.all { it.start.isAfter(instant) }.shouldBeTrue()
            (moment.progress in 0f..1f).shouldBeTrue()
            (moment.bodyAheadHours >= -12f && moment.bodyAheadHours < 12f).shouldBeTrue()
        }
    }

    @Test
    fun `rail marks past, now and future blocks and carries outcomes`() {
        val doneId = fakePlan.days.first { it.index == -1 }.advice.first().id
        val days = fakePlan.railDays(PlanFixtures.FakeInFlight, mapOf(doneId to AdviceOutcome.Done))
        val travel = days.single { it.day.kind == DayKind.Travel }
        travel.nowIndex shouldBe 0
        travel.nowInsideBlock.shouldBeTrue()
        travel.items.first().status shouldBe RailStatus.Now
        days.first { it.day.index == -1 }.isPast.shouldBeTrue()
        days.first { it.day.index == -1 }.items.first().outcome shouldBe AdviceOutcome.Done
        days.last().items.all { it.status == RailStatus.Future }.shouldBeTrue()
    }

    @Test
    fun `past days fold behind the earlier toggle`() {
        val days = fakePlan.railDays(PlanFixtures.FakeInFlight, emptyMap())
        val folded = buildRailRows(days, PlanFixtures.FakeInFlight, showEarlier = false)
        folded.first().shouldBeInstanceOf<RailRow.Earlier>().count shouldBe 2
        folded.filterIsInstance<RailRow.Header>().map { it.day.day.index } shouldBe listOf(0, 1, 2, 3, 4)
        val nowRow = folded[folded.nowRowIndex()].shouldBeInstanceOf<RailRow.Block>()
        nowRow.item.advice.type shouldBe AdviceType.Flight
        nowRow.nowFraction.shouldNotBeNull()

        val expanded = buildRailRows(days, PlanFixtures.FakeInFlight, showEarlier = true)
        expanded.filterIsInstance<RailRow.Header>().map { it.day.day.index } shouldContain -2
        expanded.first().shouldBeInstanceOf<RailRow.Earlier>().expanded.shouldBeTrue()
    }

    @Test
    fun `a now marker sits between blocks in free time`() {
        // 12:00 BST on fake day 2: between bright light (07–09) and avoid light (21–23).
        val noon = Instant.parse("2026-06-17T11:00:00Z")
        val rows = buildRailRows(fakePlan.railDays(noon, emptyMap()), noon, showEarlier = false)
        rows[rows.nowRowIndex()].shouldBeInstanceOf<RailRow.NowMarker>()
        fakePlan.momentAt(noon).active.shouldBeNull()
    }

    @Test
    fun `the flight line never repeats the route`() {
        // Without a flight number the planner's detail falls back to "MXP→SIN", the same route the UI prints.
        flightDetails("MXP → SIN", "MXP→SIN") shouldBe listOf("MXP → SIN")
        flightDetails("MXP → SIN", "SQ 355") shouldBe listOf("MXP → SIN", "SQ 355")
        flightDetails(null, "MXP→SIN") shouldBe listOf("MXP→SIN")
        flightDetails("MXP → SIN", null) shouldBe listOf("MXP → SIN")
    }

    @Test
    fun `each day's body-sky band has exactly one start and one end`() {
        val noon = Instant.parse("2026-06-17T11:00:00Z")
        val rows = buildRailRows(fakePlan.railDays(noon, emptyMap()), noon, showEarlier = true)
        val blocksByDay = rows.filterIsInstance<RailRow.Block>().groupBy { it.day.day.index }
        blocksByDay.values.forEach { blocks ->
            blocks.count { it.first } shouldBe 1
            blocks.count { it.last } shouldBe 1
            blocks.first().first.shouldBeTrue()
            blocks.last().last.shouldBeTrue()
        }
    }

    @Test
    fun `after the trip nothing is folded and there is no now row`() {
        val later = fakePlan.daySpans().last().end.plus(Duration.ofDays(1))
        val rows = buildRailRows(fakePlan.railDays(later, emptyMap()), later, showEarlier = false)
        rows.filterIsInstance<RailRow.Earlier>().shouldBeEmpty()
        rows.nowRowIndex() shouldBe -1
    }

    @Test
    fun `export file names are safe`() {
        fileNameFor("San Francisco → London") shouldBe "San-Francisco-London-jet-lag-plan.ics"
        fileNameFor("→").shouldEndWith("trip-jet-lag-plan.ics")
    }

    @Test
    fun `the celebration rings start behind on arrival after flying east`() {
        val ahead = realPlan.arrivalBodyAheadMinutes(java.time.ZoneId.of(realPlan.destinationZoneId))
        // Eastward: the body lands behind local time, by hours, so the rings visibly turn into alignment.
        (ahead < -60f).shouldBeTrue()
    }

    @Test
    fun `without a flight the celebration rings start at the planned shift`() {
        val noFlight = realPlan.copy(days = realPlan.days.map { day -> day.copy(advice = day.advice.filter { it.type != AdviceType.Flight }) })
        noFlight.arrivalBodyAheadMinutes(java.time.ZoneId.of(realPlan.destinationZoneId)) shouldBe (-realPlan.shiftHours * 60.0).toFloat()
    }

    private infix fun Float.shouldBeBetween(range: Pair<Float, Float>) {
        (this >= range.first && this <= range.second).shouldBeTrue()
    }
}
