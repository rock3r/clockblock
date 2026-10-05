package dev.sebastiano.clockblocker.opus.core.circadian

import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.LHR
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.NOW
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.SFO
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.lhrSyd
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.profile
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.sfoLhr
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PhasePoint
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class BodyClockTest {
    private val t0 = Instant.parse("2026-06-10T00:00:00Z")

    private fun synthetic(shiftHours: Double, vararg offsetsMinutes: Int) = JetLagPlan(
        tripId = "t",
        generatedAt = NOW,
        strategy = AdaptationStrategy.Adapt,
        direction = if (shiftHours > 0) ShiftDirection.Advance else ShiftDirection.Delay,
        shiftHours = shiftHours,
        originZoneId = "UTC",
        destinationZoneId = "UTC",
        days = emptyList(),
        phase = offsetsMinutes.mapIndexed { i, m -> PhasePoint(t0.plus(Duration.ofHours(i * 24L)), m, t0) },
        estimatedDaysToAdapt = 0.0,
        estimatedDaysWithoutPlan = 0.0,
    )

    @Test
    fun `body clock time is the instant read at the body offset`() {
        val plan = synthetic(8.0, -420, -420)
        plan.bodyClockTimeAt(Instant.parse("2026-06-10T10:00:00Z")) shouldBe LocalTime.of(3, 0)
    }

    @Test
    fun `body clock time follows the planned shift`() {
        val plan = DefaultJetLagPlanner().plan(sfoLhr, profile(SFO), NOW)
        // home-entrained before the pre-flight days, BST-entrained (within 30 min) at the end
        plan.bodyClockTimeAt(plan.phase.first().instant) shouldBe plan.phase.first().instant.atZone(SFO.zone).toLocalTime()
        val last = plan.phase.last().instant
        val diff = plan.bodyClockTimeAt(last).toSecondOfDay() - last.atZone(LHR.zone).toLocalTime().toSecondOfDay()
        val lagMinutes = minOf(Math.floorMod(diff, 86_400), Math.floorMod(-diff, 86_400)) / 60
        (lagMinutes <= 30) shouldBe true
    }

    @Test
    fun `progress is the share of the shift already done, clamped`() {
        val plan = synthetic(8.0, -420, -300, -180, 60)
        plan.adaptationProgressAt(t0.minusSeconds(3600)) shouldBe 0f
        plan.adaptationProgressAt(t0) shouldBe 0f
        plan.adaptationProgressAt(t0.plus(Duration.ofDays(1))) shouldBe (0.25f plusOrMinus 1e-4f)
        plan.adaptationProgressAt(t0.plus(Duration.ofHours(36))) shouldBe (0.375f plusOrMinus 1e-4f)
        plan.adaptationProgressAt(t0.plus(Duration.ofDays(3))) shouldBe 1f
        plan.adaptationProgressAt(t0.plus(Duration.ofDays(30))) shouldBe 1f
    }

    @Test
    fun `progress unwraps offsets stored across the date line`() {
        // +9 h shifting 6 h east: +11, +13 (stored -11), +15 (stored -9)
        val plan = synthetic(6.0, 540, 660, -660, -540)
        plan.adaptationProgressAt(t0.plus(Duration.ofDays(2))) shouldBe (4f / 6f plusOrMinus 1e-4f)
        plan.adaptationProgressAt(t0.plus(Duration.ofDays(3))) shouldBe 1f
    }

    @Test
    fun `progress works for delays and never goes negative`() {
        val plan = synthetic(-4.0, 0, 60, -120, -240)
        plan.adaptationProgressAt(t0.plus(Duration.ofDays(1))) shouldBe 0f
        plan.adaptationProgressAt(t0.plus(Duration.ofDays(2))) shouldBe (0.5f plusOrMinus 1e-4f)
    }

    @Test
    fun `nothing to shift means adapted`() {
        synthetic(0.0, 60, 60).adaptationProgressAt(t0) shouldBe 1f
        synthetic(0.0).adaptationProgressAt(t0) shouldBe 1f
        synthetic(5.0).adaptationProgressAt(t0) shouldBe 0f
    }

    @Test
    fun `day spans are contiguous calendar days and the day after landing starts at the arrival`() {
        val plan = DefaultJetLagPlanner().plan(sfoLhr, profile(SFO), NOW)
        val spans = plan.daySpans()
        spans.map { it.day } shouldBe plan.days
        spans.zipWithNext().forEach { (a, b) -> a.end shouldBe b.start }
        spans.first().start shouldBe LocalDate.parse("2026-06-11").atStartOfDay(SFO.zone).toInstant()
        val travel = spans.single { it.day.kind == DayKind.Travel }
        travel.start shouldBe LocalDate.parse("2026-06-15").atStartOfDay(SFO.zone).toInstant()
        travel.end shouldBe sfoLhr.arrival // 10:45 BST: the rest of the day is the first arrival day
        spans.last().end shouldBe spans.last().day.date.plusDays(1).atStartOfDay(LHR.zone).toInstant()
        spans.forEach { span -> span.day.advice.forEach { (it.start >= span.start && it.start < span.end) shouldBe true } }
    }

    @Test
    fun `an evening arrival keeps the travel day until midnight`() {
        val plan = DefaultJetLagPlanner().plan(lhrSyd, profile(LHR), NOW)
        val spans = plan.daySpans()
        val travel = spans.single { it.day.kind == DayKind.Travel }
        // LHR 21:00 departure, SYD 06:30 arrival two days later: travel day runs to the arrival
        travel.end shouldBe lhrSyd.arrival
        spans.zipWithNext().forEach { (a, b) -> a.end shouldBe b.start }

        val evening = Fixtures.trip(Fixtures.leg("1", LHR, Fixtures.JFK, "2026-05-02T14:00", "2026-05-02T19:00"))
        val eveningSpans = DefaultJetLagPlanner().plan(evening, profile(LHR), NOW).daySpans()
        val eveningTravel = eveningSpans.single { it.day.kind == DayKind.Travel }
        eveningTravel.end shouldBe LocalDate.parse("2026-05-03").atStartOfDay(Fixtures.JFK.zone).toInstant()
        eveningSpans.zipWithNext().forEach { (a, b) -> a.end shouldBe b.start }
    }

    @Test
    fun `current day is the span containing the instant`() {
        val plan = DefaultJetLagPlanner().plan(sfoLhr, profile(SFO), NOW)
        plan.currentDay(sfoLhr.departure)?.index shouldBe 0
        plan.currentDay(sfoLhr.arrival)?.index shouldBe 1
        plan.currentDay(sfoLhr.arrival.minusSeconds(1))?.index shouldBe 0
        plan.currentDay(Instant.parse("2026-06-18T12:00:00Z"))?.date shouldBe LocalDate.parse("2026-06-18")
        plan.currentDay(Instant.parse("2026-01-01T00:00:00Z")).shouldBeNull()
        plan.currentDay(Instant.parse("2027-01-01T00:00:00Z")).shouldBeNull()
    }

    @Test
    fun `body night reads the sleep window on the body clock, wrapping midnight`() {
        // body offset −7 h: at 06:00Z the body reads 23:00, at 14:00Z it reads 07:00
        val plan = synthetic(8.0, -420, -420)
        val sleep = SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 0))
        plan.isBodyNightAt(Instant.parse("2026-06-10T06:00:00Z"), sleep) shouldBe true
        plan.isBodyNightAt(Instant.parse("2026-06-10T13:59:00Z"), sleep) shouldBe true
        plan.isBodyNightAt(Instant.parse("2026-06-10T14:00:00Z"), sleep) shouldBe false
        plan.isBodyNightAt(Instant.parse("2026-06-10T05:59:00Z"), sleep) shouldBe false
    }

    @Test
    fun `sleep windows that do not wrap midnight`() {
        val sleep = SleepWindow(LocalTime.of(1, 0), LocalTime.of(9, 0))
        LocalTime.of(0, 59).isWithin(sleep) shouldBe false
        LocalTime.of(1, 0).isWithin(sleep) shouldBe true
        LocalTime.of(8, 59).isWithin(sleep) shouldBe true
        LocalTime.of(9, 0).isWithin(sleep) shouldBe false
    }
}
