package dev.sebastiano.clockblocker.opus.core.circadian.engine

import dev.sebastiano.clockblocker.opus.core.circadian.DefaultJetLagPlanner
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.JFK
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.LHR
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.NOW
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.SFO
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.leg
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.place
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.profile
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.trip
import dev.sebastiano.clockblocker.opus.core.circadian.PlannerConfig
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.LocalTime

/**
 * Issue #9: a trip can say where the body clock is when it starts ([dev.sebastiano.clockblocker.opus.core.model.Trip.bodyClockStartZoneId]),
 * e.g. still on home time for someone who has only just arrived in the departure city. Null keeps the departure
 * city, exactly as before.
 */
class BodyClockStartTest {
    private val planner = DefaultJetLagPlanner()

    /** New York → London in June: EDT (UTC−4) to BST (UTC+1), 5 h east from the departure city. */
    private val jfkLhr = trip(leg("1", JFK, LHR, "2026-06-15T18:00", "2026-06-16T06:30"))

    /** The same trip for someone whose body clock is still on Los Angeles time (PDT, UTC−7): 8 h east. */
    private val fromLosAngeles = jfkLhr.copy(bodyClockStartZoneId = SFO.zoneId)

    private val traveller = profile(SFO)

    @Test
    fun `null starts from the departure city, exactly as before`() {
        val explicit = jfkLhr.copy(bodyClockStartZoneId = JFK.zoneId)
        planner.plan(explicit, traveller, NOW) shouldBe planner.plan(jfkLhr, traveller, NOW)
        planner.plan(jfkLhr, traveller, NOW).shiftHours shouldBe 5.0
    }

    @Test
    fun `the profile's home zone alone doesn't move the start`() {
        planner.plan(jfkLhr, profile(SFO), NOW) shouldBe planner.plan(jfkLhr, profile(JFK), NOW)
    }

    @Test
    fun `the start phase follows the chosen zone`() {
        val plan = planner.plan(fromLosAngeles, traveller, NOW)
        plan.phase.first().bodyUtcOffsetMinutes shouldBe -7 * 60
        plan.shiftHours shouldBe 8.0
        plan.direction shouldBe ShiftDirection.Advance

        plan.startZoneId shouldBe SFO.zoneId
        plan.originZoneId shouldBe JFK.zoneId // Days and "local time" still follow where the traveller is.

        val before = planner.plan(jfkLhr, traveller, NOW)
        before.phase.first().bodyUtcOffsetMinutes shouldBe -4 * 60
        before.startZoneId shouldBe JFK.zoneId
    }

    @Test
    fun `home time mode keeps the chosen offset`() {
        val trip = fromLosAngeles.copy(strategyOverride = AdaptationStrategy.StayOnHomeTime)
        val plan = planner.plan(trip, traveller, NOW)
        plan.strategy shouldBe AdaptationStrategy.StayOnHomeTime
        plan.phase.map { it.bodyUtcOffsetMinutes }.toSet() shouldContainOnly setOf(-7 * 60)
        // Bedtime 23:00 on Los Angeles time, not New York's.
        val firstSleep = plan.allAdvice.filter { it.type == AdviceType.Sleep }.minBy { it.start }
        firstSleep.start.atZone(SFO.zone).toLocalTime() shouldBe LocalTime.of(23, 0)
    }

    @Test
    fun `the validator's itinerary starts from the chosen zone too`() {
        val config = PlannerConfig().forProfile(traveller)
        PlanBuilder(config, fromLosAngeles, traveller, NOW).itinerary.homeOffset shouldBe -7.0
        PlanBuilder(config, jfkLhr, traveller, NOW).itinerary.homeOffset shouldBe -4.0

        val with = planner.plan(fromLosAngeles, traveller, NOW)
        val without = planner.plan(jfkLhr, traveller, NOW)
        with.estimatedDaysWithoutPlan shouldNotBe without.estimatedDaysWithoutPlan
    }

    @Test
    fun `a trip within one zone still plans when the body clock starts elsewhere`() {
        val yyz = place("YYZ", "America/Toronto")
        val sameZone = trip(leg("1", JFK, yyz, "2026-06-15T18:00", "2026-06-15T19:30"))
        planner.plan(sameZone, traveller, NOW).direction shouldBe ShiftDirection.None

        val plan = planner.plan(sameZone.copy(bodyClockStartZoneId = LHR.zoneId), traveller, NOW)
        plan.strategy shouldBe AdaptationStrategy.Adapt
        plan.direction shouldBe ShiftDirection.Delay
        plan.shiftHours shouldBe -5.0
    }

    @Test
    fun `an unknown start zone falls back to the departure city`() {
        val bad = jfkLhr.copy(bodyClockStartZoneId = "Mars/Olympus_Mons")
        planner.plan(bad, traveller, NOW) shouldBe planner.plan(jfkLhr, traveller, NOW)
    }
}
