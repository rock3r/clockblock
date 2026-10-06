package dev.sebastiano.clockblocker.opus.core.circadian

import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.LHR
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.NOW
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.SFO
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.profile
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.sfoLhr
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class PlanZonesTest {
    private val plan = DefaultJetLagPlanner().plan(sfoLhr, profile(SFO), NOW)
    private val deviceZone: TimeZone = TimeZone.getDefault()

    @AfterEach
    fun restoreDeviceZone() = TimeZone.setDefault(deviceZone)

    @Test
    fun `local zone is the zone of the plan day containing the instant`() {
        plan.localZoneAt(plan.daySpans().first().start) shouldBe SFO.zone
        plan.localZoneAt(sfoLhr.departure) shouldBe SFO.zone
        plan.localZoneAt(plan.daySpans().last().start) shouldBe LHR.zone
    }

    @Test
    fun `the travel day keeps the departure zone until landing`() {
        // The day after the travel day starts at the arrival, not at midnight in London.
        plan.localZoneAt(sfoLhr.arrival.minusSeconds(1)) shouldBe SFO.zone
        plan.localZoneAt(sfoLhr.arrival) shouldBe LHR.zone
    }

    @Test
    fun `an evening arrival switches to the destination at landing, not at midnight`() {
        // LHR 14:00 → JFK 19:00: the travel day runs to midnight in New York, but the traveller is on New York time.
        val evening = Fixtures.trip(Fixtures.leg("1", LHR, Fixtures.JFK, "2026-05-02T14:00", "2026-05-02T19:00"))
        val plan = DefaultJetLagPlanner().plan(evening, profile(LHR), NOW)

        plan.localZoneAt(evening.arrival.minusSeconds(1)) shouldBe LHR.zone
        plan.localZoneAt(evening.arrival) shouldBe Fixtures.JFK.zone
        plan.localZoneAt(evening.arrival.plusSeconds(3 * 3600)) shouldBe Fixtures.JFK.zone
    }

    @Test
    fun `before the plan it is the first day's zone, after it the last day's`() {
        plan.localZoneAt(Instant.parse("2026-01-01T00:00:00Z")) shouldBe SFO.zone
        plan.localZoneAt(Instant.parse("2027-01-01T00:00:00Z")) shouldBe LHR.zone
    }

    @Test
    fun `a plan without days uses the destination`() {
        plan.copy(days = emptyList()).localZoneAt(sfoLhr.departure) shouldBe LHR.zone
    }

    @Test
    fun `the device zone plays no part`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))

        plan.localZoneAt(sfoLhr.departure) shouldBe SFO.zone
    }

    @Test
    fun `the secondary zone is the destination, or home once local time is the destination's`() {
        plan.secondaryZoneFor(SFO.zone) shouldBe LHR.zone
        plan.secondaryZoneFor(LHR.zone) shouldBe SFO.zone
        // A day in a stopover's zone still shows where the trip is going.
        plan.secondaryZoneFor(ZoneId.of("Asia/Singapore")) shouldBe LHR.zone
    }

    @Test
    fun `display zones pair local and secondary at an instant`() {
        plan.displayZonesAt(sfoLhr.departure) shouldBe PlanDisplayZones(SFO.zone, LHR.zone)
        plan.displayZonesAt(sfoLhr.arrival) shouldBe PlanDisplayZones(LHR.zone, SFO.zone)
    }

    @Test
    fun `a stopover day shows its own zone`() {
        val stopover = JetLagPlan(
            tripId = "t",
            generatedAt = NOW,
            strategy = AdaptationStrategy.Adapt,
            direction = ShiftDirection.Advance,
            shiftHours = 8.0,
            originZoneId = "Europe/London",
            destinationZoneId = "Australia/Sydney",
            days = listOf(
                PlanDay(0, DayKind.Travel, LocalDate.parse("2026-05-01"), "Europe/London", emptyList()),
                PlanDay(1, DayKind.Arrival, LocalDate.parse("2026-05-02"), "Asia/Singapore", emptyList()),
            ),
            phase = emptyList(),
            estimatedDaysToAdapt = 3.0,
            estimatedDaysWithoutPlan = 7.0,
        )

        stopover.displayZonesAt(Instant.parse("2026-05-02T06:00:00Z")) shouldBe
            PlanDisplayZones(ZoneId.of("Asia/Singapore"), ZoneId.of("Australia/Sydney"))
    }
}
