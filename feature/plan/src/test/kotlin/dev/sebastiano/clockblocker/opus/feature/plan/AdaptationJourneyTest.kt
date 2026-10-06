package dev.sebastiano.clockblocker.opus.feature.plan

import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
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
import java.time.Instant

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
        daysSaved(withPlan = 3.2, withoutPlan = 8.6) shouldBe 6
        daysSaved(withPlan = 4.0, withoutPlan = 4.2).shouldBeNull()
        daysSaved(withPlan = 5.0, withoutPlan = 3.0).shouldBeNull()
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
}
