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
import io.kotest.matchers.collections.shouldBeSortedBy
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AdaptationJourneyTest {

    private fun plan(origin: String, destination: String, direction: ShiftDirection, shift: Double) = JetLagPlan(
        tripId = "t",
        generatedAt = Instant.parse("2026-06-15T12:00:00Z"),
        strategy = AdaptationStrategy.Adapt,
        direction = direction,
        shiftHours = shift,
        originZoneId = origin,
        destinationZoneId = destination,
        days = emptyList(),
        phase = emptyList(),
        estimatedDaysToAdapt = 4.0,
        estimatedDaysWithoutPlan = 9.0,
    )

    @Test
    fun `the plan line starts where the pre-trip shift left the body and ends in sync`() {
        val journey = realPlan.journey().shouldNotBeNull()
        journey.withPlan shouldBeSortedBy { it.day }
        journey.startDay shouldBeLessThan 0f
        // San Francisco → London is 8 h; the plan starts shifting before the flight.
        val atLanding = journey.withPlan.first { it.day >= 0f }.hours
        atLanding shouldBeLessThan 8f
        journey.withPlan.last().hours shouldBeLessThan 1f
        journey.maxHours shouldBeGreaterThanOrEqual 8f
    }

    @Test
    fun `the no-plan line stays on home time until landing, then closes at the model's no-plan estimate`() {
        val journey = realPlan.journey().shouldNotBeNull()
        val without = journey.withoutPlan
        without.first().day shouldBe journey.startDay
        without.first().hours shouldBe (8f plusOrMinus 0.01f)
        without.first { it.day == 0f }.hours shouldBe (8f plusOrMinus 0.01f)
        val days = realPlan.estimatedDaysWithoutPlan.toFloat()
        if (days <= journey.endDay) {
            without.last().day shouldBe (days plusOrMinus 0.01f)
            without.last().hours shouldBe 0f
        } else {
            without.last().day shouldBe journey.endDay
        }
    }

    @Test
    fun `the comparison only claims days saved when the plan is faster`() {
        // Never more than the modelled difference (5.4 → 5) nor than the visible "3 d" vs "9 d" (6).
        daysSaved(withPlan = 3.2, withoutPlan = 8.6) shouldBe DaysSaved(5, atLeast = false)
        daysSaved(withPlan = 2.6, withoutPlan = 6.3) shouldBe DaysSaved(3, atLeast = false)
        // Straddling a rounding boundary ("3 d" vs "4 d") is half an hour, not a day.
        daysSaved(withPlan = 3.49, withoutPlan = 3.51).shouldBeNull()
        daysSaved(withPlan = 4.0, withoutPlan = 4.2).shouldBeNull()
        daysSaved(withPlan = 5.0, withoutPlan = 3.0).shouldBeNull()
    }

    @Test
    fun `a no-plan estimate at the horizon only supports an at-least claim`() {
        // Not adapted by day 21 without a plan: 15 days with it is at least 6 days faster, not "about 6".
        daysSaved(withPlan = 15.0, withoutPlan = EstimateHorizonDays) shouldBe DaysSaved(6, atLeast = true)
        // A lower bound rounds down: 21 − 15.49 only guarantees 5.51 days.
        daysSaved(withPlan = 15.49, withoutPlan = EstimateHorizonDays) shouldBe DaysSaved(5, atLeast = true)
        daysSaved(withPlan = 20.6, withoutPlan = EstimateHorizonDays).shouldBeNull()
        // Both censored: nothing to claim.
        daysSaved(withPlan = EstimateHorizonDays, withoutPlan = EstimateHorizonDays).shouldBeNull()
    }

    @Test
    fun `an eastward trip solved by moving later is the long way round`() {
        // Los Angeles (UTC−7 in June) → Moscow (UTC+3): 10 h east, delayed by 14 h.
        plan("America/Los_Angeles", "Europe/Moscow", ShiftDirection.Delay, -14.0).longWayRound() shouldBe LongWayRound.EastByDelaying
        plan("America/Los_Angeles", "Europe/Moscow", ShiftDirection.Advance, 10.0).longWayRound().shouldBeNull()
        // London (UTC+1) → Honolulu (UTC−10): 11 h west, advanced by 13 h.
        plan("Europe/London", "Pacific/Honolulu", ShiftDirection.Advance, 13.0).longWayRound() shouldBe LongWayRound.WestByAdvancing
        realPlan.longWayRound().shouldBeNull()
        // The real planner delays an 11 h eastward trip.
        PlanFixtures.longWayPlan.longWayRound() shouldBe LongWayRound.EastByDelaying
        PlanFixtures.longWayPlan.journey().shouldNotBeNull()
    }

    @Test
    fun `shift durations keep half and quarter hour zones`() {
        shiftDuration(10.5) shouldBe Duration.ofMinutes(630)
        shiftDuration(-5.75) shouldBe Duration.ofMinutes(345)
        shiftDuration(13.0) shouldBe Duration.ofHours(13)
        // Float noise snaps to the nearest quarter hour rather than surfacing odd minutes.
        shiftDuration(10.4999) shouldBe Duration.ofMinutes(630)
    }

    @Test
    fun `the geographic shift reads the origin at departure and the destination at landing`() {
        // New York falls back (−4 → −5) at 06:00Z on 1 Nov 2026, mid-flight; London is already on GMT. The
        // planner anchors home to the departure offset, so the trip is 4 h east, not the 5 h seen at landing.
        val departure = Instant.parse("2026-11-01T02:00:00Z")
        val landing = Instant.parse("2026-11-01T09:00:00Z")
        val flight = Advice("flight-1", AdviceType.Flight, departure, landing, AdviceReason.TravelMarker)
        val day = PlanDay(0, DayKind.Travel, LocalDate.of(2026, 10, 31), "America/New_York", listOf(flight))
        val plan = plan("America/New_York", "Europe/London", ShiftDirection.Advance, 4.0).copy(days = listOf(day))
        plan.geographicShiftHours() shouldBe 4f
    }

    @Test
    fun `the shift starts from where the body clock starts`() {
        // Still on Los Angeles time (−7) when leaving New York for London (GMT on 1 Nov): 7 h east, not 4.
        val departure = Instant.parse("2026-11-01T02:00:00Z")
        val landing = Instant.parse("2026-11-01T09:00:00Z")
        val flight = Advice("flight-1", AdviceType.Flight, departure, landing, AdviceReason.TravelMarker)
        val day = PlanDay(0, DayKind.Travel, LocalDate.of(2026, 10, 31), "America/New_York", listOf(flight))
        val plan = plan("America/New_York", "Europe/London", ShiftDirection.Advance, 7.0)
            .copy(days = listOf(day), bodyClockStartZoneId = "America/Los_Angeles")
        plan.startZoneId shouldBe "America/Los_Angeles"
        plan.geographicShiftHours() shouldBe 7f
    }

    @Test
    fun `zone deltas take the short way across the date line`() {
        val june = Instant.parse("2026-06-15T12:00:00Z")
        // Kiritimati (+14) → Honolulu (−10): same clock time, a day apart.
        zoneDeltaHours(ZoneId.of("Pacific/Kiritimati"), ZoneId.of("Pacific/Honolulu"), june) shouldBe 0f
        // Tonga (+13) → Los Angeles (−7 in June): raw −20 h, really 4 h ahead.
        zoneDeltaHours(ZoneId.of("Pacific/Tongatapu"), ZoneId.of("America/Los_Angeles"), june) shouldBe 4f
        zoneDeltaHours(ZoneId.of("Europe/London"), ZoneId.of("Asia/Kolkata"), june) shouldBe 4.5f
    }

    @Test
    fun `zone deltas keep real civil differences past 12 hours`() {
        val january = Instant.parse("2026-01-15T12:00:00Z")
        // Chatham is UTC+13:45 in January: London (UTC+0) → Chatham is 13¾ h ahead, not 10¼ h behind.
        zoneDeltaHours(ZoneId.of("Europe/London"), ZoneId.of("Pacific/Chatham"), january) shouldBe 13.75f
        zoneDeltaHours(ZoneId.of("UTC"), ZoneId.of("Pacific/Tongatapu"), january) shouldBe 13f
        // Los Angeles (UTC−8) → Chatham: raw +21¾ h is really 2¼ h behind (a day ahead on the calendar).
        zoneDeltaHours(ZoneId.of("America/Los_Angeles"), ZoneId.of("Pacific/Chatham"), january) shouldBe -2.25f
    }

    @Test
    fun `zone deltas print quarter hours instead of rounding them away`() {
        formatZoneDelta(5.75f) shouldBe "+5\u00BE h" // UTC → Kathmandu
        formatZoneDelta(12.75f) shouldBe "+12\u00BE h" // UTC → Chatham (summer)
        formatZoneDelta(-0.25f) shouldBe "\u2212\u00BC h"
        formatZoneDelta(-9.5f) shouldBe "\u22129\u00BD h"
        formatZoneDelta(8f) shouldBe "+8 h"
        formatZoneDelta(0f) shouldBe "0 h"
    }

    @Test
    fun `journey hour labels keep half and quarter hours`() {
        formatQuarterHours(5.5f) shouldBe "5\u00BD h"
        formatQuarterHours(-5.75f) shouldBe "5\u00BE h"
        formatQuarterHours(0.25f) shouldBe "\u00BC h"
        formatQuarterHours(8.04f) shouldBe "8 h"
        formatQuarterHours(0f) shouldBe "0 h"
    }
}
