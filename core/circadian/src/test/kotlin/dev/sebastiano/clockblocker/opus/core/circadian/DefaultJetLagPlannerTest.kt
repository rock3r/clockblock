package dev.sebastiano.clockblocker.opus.core.circadian

import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.CDG
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.CXI
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.HNL
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.JFK
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.LHR
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.NOW
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.NRT
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.SFO
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.jfkLax
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.leg
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.lhrSyd
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.nrtJfk
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.place
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.profile
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.sfoLhr
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.trip
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.windows
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldStartWith
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class DefaultJetLagPlannerTest {
    private val planner = DefaultJetLagPlanner()
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    // ---------------------------------------------------------------- §14.1 SFO → LHR
    @Test
    fun `14_1 SFO-LHR advances 8 h with the tabulated windows`() {
        val plan = planner.plan(sfoLhr, profile(SFO), NOW)
        plan.strategy shouldBe AdaptationStrategy.Adapt
        plan.direction shouldBe ShiftDirection.Advance
        plan.shiftHours shouldBe 8.0
        plan.windows(AdviceType.Sleep, sfoLhr) shouldContainExactly listOf(
            "23:00-07:00", "22:00-06:00", "21:00-05:00", "20:00-04:00", // home (PDT)
            "04:00-10:00", "02:00-10:00", "01:00-09:00", "23:30-07:30", // flight + LHR (BST)
        )
        plan.windows(AdviceType.SeeBrightLight, sfoLhr) shouldContainExactly listOf(
            "07:00-10:00", "06:00-09:00", "05:00-08:00", "04:00-07:00",
            "10:00-15:00", "10:00-13:30", "09:00-12:00", "07:30-10:30",
        )
        plan.windows(AdviceType.AvoidLight, sfoLhr) shouldContainExactly listOf(
            "20:00-23:00", "19:00-22:00", "18:00-21:00", "17:00-20:00",
            "01:00-04:00", "23:30-02:00", "22:00-01:00", "20:30-23:30",
        )
        // k4's 00:00 BST dose is 30 min before departure, so it prints on the home clock (16:00 PDT).
        plan.windows(AdviceType.Melatonin, sfoLhr) shouldContainExactly listOf("19:00", "18:00", "17:00", "16:00", "16:00", "22:30", "21:00")
        plan.allAdvice.filter { it.type == AdviceType.Melatonin }.forEach {
            it.detail shouldBe "0.5 mg"
            it.reason shouldBe AdviceReason.MelatoninAdvances
        }
        plan.allAdvice.single { it.type == AdviceType.Flight }.let {
            it.start shouldBe sfoLhr.departure
            it.end shouldBe sfoLhr.arrival
            it.detail shouldBe "BA286"
            it.reason shouldBe AdviceReason.TravelMarker
        }
        plan.estimatedDaysToAdapt shouldBe (2.747 plusOrMinus 0.05)
        plan.estimatedDaysWithoutPlan shouldBe (5.756 plusOrMinus 0.05)
    }

    @Test
    fun `14_1 days are indexed from the departure day with kinds`() {
        val plan = planner.plan(sfoLhr, profile(SFO), NOW)
        plan.days.map { it.index } shouldContainExactly (-4..4).toList()
        plan.days.map { it.kind } shouldContainExactly listOf(
            DayKind.PreTrip, DayKind.PreTrip, DayKind.PreTrip, DayKind.PreTrip,
            DayKind.Travel, DayKind.Arrival, DayKind.Arrival, DayKind.Arrival, DayKind.Adapted,
        )
        plan.days.first { it.index == 0 }.let {
            it.zoneId shouldBe "America/Los_Angeles"
            it.date.toString() shouldBe "2026-06-15"
        }
        plan.days.first { it.index == 1 }.let {
            it.zoneId shouldBe "Europe/London"
            it.date.toString() shouldBe "2026-06-16"
        }
        plan.days.forEach { day -> day.advice.map { it.start } shouldBe day.advice.map { it.start }.sorted() }
    }

    @Test
    fun `14_1 phase starts on home time and ends on destination time`() {
        val plan = planner.plan(sfoLhr, profile(SFO), NOW)
        plan.phase.first().bodyUtcOffsetMinutes shouldBe -7 * 60
        plan.phase.last().bodyUtcOffsetMinutes shouldBe 30 // -7 h + 7.5 h: done within DONE_TOL of BST
        plan.bodyOffsetAt(sfoLhr.departure) shouldBe ZoneOffset.ofHours(-4) // 3 h pre-flight advance
        Duration.between(plan.phase[0].instant, plan.phase[1].instant) shouldBe Duration.ofHours(1)
        // nearest CBTmin before departure is 01:00 PDT (cycle 3)
        plan.phase.first { it.instant == sfoLhr.departure.minus(Duration.ofMinutes(30)) }.cbtMin
            .atZone(SFO.zone).toLocalTime() shouldBe LocalTime.of(1, 0)
    }

    // ---------------------------------------------------------------- §14.2 LHR → SIN → SYD
    @Test
    fun `14_2 LHR-SIN-SYD delays 13 h through a pass-through layover`() {
        val plan = planner.plan(lhrSyd, profile(LHR), NOW)
        plan.direction shouldBe ShiftDirection.Delay
        plan.shiftHours shouldBe -13.0
        plan.windows(AdviceType.Sleep, lhrSyd) shouldContainExactly listOf(
            "23:00-07:00", "00:30-08:30", "02:00-10:00", "02:00-10:00", // home, capped at -3 h
            "13:00-19:45", // in flight, ends before the SIN connection (layover blocking)
            "20:00-04:00", "20:00-04:00", "20:00-04:00", "21:00-05:00", "23:00-07:00",
        )
        plan.windows(AdviceType.SeeBrightLight, lhrSyd) shouldContainExactly listOf(
            "22:00-23:00", "23:30-00:30", "01:00-02:00", "01:00-02:00",
            "12:00-13:00", "14:00-20:00", "16:00-20:00", "18:00-20:00", "20:00-21:00", "22:00-23:00",
        )
        plan.allAdvice.none { it.type == AdviceType.Melatonin } shouldBe true
        plan.allAdvice.filter { it.type == AdviceType.Flight } shouldHaveSize 2
        plan.estimatedDaysToAdapt shouldBe (5.9 plusOrMinus 1.0)
        plan.estimatedDaysWithoutPlan shouldBe (10.9 plusOrMinus 1.0)
    }

    @Test
    fun `14_2 reference layover semantics reproduce the in-flight sleep`() {
        val plan = DefaultJetLagPlanner(PlannerConfig(blockLayoverSleep = false)).plan(lhrSyd, profile(LHR), NOW)
        plan.windows(AdviceType.Sleep, lhrSyd)[4] shouldBe "13:00-21:00"
    }

    // ---------------------------------------------------------------- §14.3 JFK → LAX
    @Test
    fun `14_3 JFK-LAX delays 3 h with no pre-flight delay and a truncated last night`() {
        val plan = planner.plan(jfkLax, profile(JFK), NOW)
        plan.direction shouldBe ShiftDirection.Delay
        plan.shiftHours shouldBe -3.0
        plan.windows(AdviceType.Sleep, jfkLax) shouldContainExactly listOf(
            "23:00-07:00", "23:00-07:00", "23:00-07:00", "23:00-05:00", "20:00-04:00", "22:00-06:00", "23:00-07:00",
        )
        plan.windows(AdviceType.SeeBrightLight, jfkLax) shouldContainExactly listOf(
            "22:00-23:00", "22:00-23:00", "22:00-23:00", "22:00-23:00", "19:00-20:00", "21:00-22:00", "22:00-23:00",
        )
        plan.estimatedDaysToAdapt shouldBe (1.658 plusOrMinus 0.05)
        plan.estimatedDaysWithoutPlan shouldBe (3.662 plusOrMinus 0.05)
    }

    // ---------------------------------------------------------------- §14.4 NRT → JFK
    @Test
    fun `14_4 NRT-JFK neutral delays 14 h`() {
        val plan = planner.plan(nrtJfk, profile(NRT), NOW)
        plan.direction shouldBe ShiftDirection.Delay
        plan.shiftHours shouldBe -14.0
        val cbt = plan.phase.map { it.cbtMin }.distinct().filter { !it.isBefore(nrtJfk.arrival) }
            .map { it.atZone(JFK.zone).format(hm) }
        cbt.distinct() shouldStartWith listOf("15:00", "17:00", "19:00", "21:00", "23:00", "01:00", "03:00", "04:00")
        plan.windows(AdviceType.Sleep, nrtJfk).drop(4).take(5) shouldBe listOf(
            "02:00-09:45", "20:00-04:00", "20:00-04:00", "20:00-04:00", "20:00-04:00",
        )
        plan.estimatedDaysToAdapt shouldBe (7.694 plusOrMinus 0.05)
        plan.estimatedDaysWithoutPlan shouldBe (5.763 plusOrMinus 0.05)
    }

    @Test
    fun `14_4 NRT-JFK early chronotype advances 10 h with melatonin`() {
        val plan = planner.plan(nrtJfk, profile(NRT, chronotype = Chronotype.ModerateMorning), NOW)
        plan.direction shouldBe ShiftDirection.Advance
        plan.shiftHours shouldBe 10.0
        plan.windows(AdviceType.Sleep, nrtJfk).first() shouldBe "23:00-07:00"
        plan.windows(AdviceType.SeeBrightLight, nrtJfk).first() shouldBe "07:00-09:30"
        plan.windows(AdviceType.AvoidLight, nrtJfk).first() shouldBe "19:30-23:00"
        plan.windows(AdviceType.Melatonin, nrtJfk).first() shouldBe "18:30"
        plan.estimatedDaysToAdapt shouldBe (3.763 plusOrMinus 0.05)
    }

    // ---------------------------------------------------------------- §14.5 planner-level cases
    @Test
    fun `Kiribati to Hawaii crosses the date line with no shift`() {
        val t = trip(leg("1", CXI, HNL, "2026-05-02T10:00", "2026-05-01T12:00"))
        val plan = planner.plan(t, profile(CXI), NOW)
        plan.direction shouldBe ShiftDirection.None
        plan.strategy shouldBe AdaptationStrategy.Adapt
        plan.allAdvice.filter { it.type == AdviceType.Flight } shouldHaveSize 1
        plan.allAdvice.filter { it.type == AdviceType.Sleep }.shouldNotBeEmpty()
    }

    @Test
    fun `a one-hour shift gets no plan but destination sleep`() {
        val t = trip(leg("1", LHR, CDG, "2026-05-02T10:00", "2026-05-02T12:20"))
        val plan = planner.plan(t, profile(LHR), NOW)
        plan.direction shouldBe ShiftDirection.None
        plan.shiftHours shouldBe 1.0
        val sleeps = plan.allAdvice.filter { it.type == AdviceType.Sleep }
        sleeps shouldHaveSize 3
        sleeps.forEach {
            it.start.atZone(CDG.zone).toLocalTime() shouldBe LocalTime.of(23, 0)
            it.reason shouldBe AdviceReason.DestinationSleep
        }
        plan.allAdvice.none { it.type == AdviceType.SeeBrightLight || it.type == AdviceType.Melatonin } shouldBe true
    }

    @Test
    fun `a 48 h stay 6 h east stays on home time`() {
        val dhaka = place("DAC", "Asia/Dhaka")
        val out = leg("1", LHR, dhaka, "2026-05-02T10:00", "2026-05-03T02:00")
        val t = trip(out, returnDeparture = out.arrival.plus(Duration.ofHours(48)))
        val plan = planner.plan(t, profile(LHR), NOW)
        plan.strategy shouldBe AdaptationStrategy.StayOnHomeTime
        plan.direction shouldBe ShiftDirection.None
        plan.shiftHours shouldBe 0.0
        plan.estimatedDaysToAdapt shouldBe 0.0
        plan.phase.map { it.bodyUtcOffsetMinutes }.distinct() shouldBe listOf(60) // BST all along
        val nightsAway = plan.allAdvice.filter { it.type == AdviceType.Sleep && it.start.isAfter(out.arrival) }
        nightsAway.shouldNotBeEmpty()
        nightsAway.forEach { it.start.atZone(LHR.zone).toLocalTime() shouldBe LocalTime.of(23, 0) }
        plan.allAdvice.filter { it.type == AdviceType.AvoidLight }.forEach { it.reason shouldBe AdviceReason.StayOnHomeTime }
        plan.allAdvice.none { it.type == AdviceType.SeeBrightLight || it.type == AdviceType.Melatonin } shouldBe true
        plan.allAdvice.all { !it.end.isAfter(t.returnDeparture) } shouldBe true
    }

    @Test
    fun `strategy override wins over stay length`() {
        val long = sfoLhr.copy(strategyOverride = AdaptationStrategy.StayOnHomeTime)
        planner.plan(long, profile(SFO), NOW).strategy shouldBe AdaptationStrategy.StayOnHomeTime
        val short = sfoLhr.copy(strategyOverride = AdaptationStrategy.Adapt, returnDeparture = sfoLhr.arrival.plus(Duration.ofHours(30)))
        planner.plan(short, profile(SFO), NOW).strategy shouldBe AdaptationStrategy.Adapt
        val autoShort = sfoLhr.copy(returnDeparture = sfoLhr.arrival.plus(Duration.ofHours(30)))
        planner.plan(autoShort, profile(SFO), NOW).strategy shouldBe AdaptationStrategy.StayOnHomeTime
    }

    // ---------------------------------------------------------------- profile mapping
    @Test
    fun `caffeine and melatonin toggles`() {
        val on = planner.plan(sfoLhr, profile(SFO), NOW).allAdvice.map { it.type }.toSet()
        (AdviceType.Caffeine in on && AdviceType.AvoidCaffeine in on && AdviceType.Melatonin in on) shouldBe true
        val off = planner.plan(sfoLhr, profile(SFO, melatonin = false, caffeine = false), NOW).allAdvice.map { it.type }.toSet()
        (AdviceType.Caffeine in off || AdviceType.AvoidCaffeine in off || AdviceType.Melatonin in off) shouldBe false
    }

    @Test
    fun `can't sleep on planes turns in-flight sleep into resting in the dark`() {
        val plan = planner.plan(sfoLhr, profile(SFO, sleepOnPlanes = false), NOW)
        val flight = plan.allAdvice.single { it.type == AdviceType.Flight }
        plan.allAdvice.filter { it.type == AdviceType.Sleep }.none { it.start < flight.end && flight.start < it.end } shouldBe true
        val rest = plan.allAdvice.filter { it.type == AdviceType.AvoidLight && it.reason == AdviceReason.RestInFlight }
        rest shouldHaveSize 1
        rest.single().start.atZone(Fixtures.LHR.zone).format(hm) shouldBe "04:00"
    }

    @Test
    fun `pre-flight adjustment follows the profile`() {
        planner.plan(sfoLhr, profile(SFO, preAdjust = false), NOW).bodyOffsetAt(sfoLhr.departure) shouldBe ZoneOffset.ofHours(-7)
        val gentle = planner.plan(sfoLhr, profile(SFO, intensity = Intensity.Gentle), NOW)
        gentle.bodyOffsetAt(sfoLhr.departure) shouldBe ZoneOffset.ofHours(-5)
        val balanced = planner.plan(sfoLhr, profile(SFO), NOW)
        gentle.days.count { it.kind == DayKind.PreTrip } shouldBeLessThan balanced.days.count { it.kind == DayKind.PreTrip }
    }

    @Test
    fun `Max intensity lets the model choose the direction for 8-12 h east`() {
        planner.plan(lhrSyd, profile(LHR), NOW).direction shouldBe ShiftDirection.Delay
        planner.plan(lhrSyd, profile(LHR, intensity = Intensity.Max), NOW).direction shouldBe ShiftDirection.Advance
        // outside 8–12 h Max changes nothing
        planner.plan(jfkLax, profile(JFK, intensity = Intensity.Max), NOW).direction shouldBe ShiftDirection.Delay
    }

    // ---------------------------------------------------------------- ids, determinism, robustness, speed
    @Test
    fun `ids are unique, stable and survive re-planning with different pre-flight days`() {
        val a = planner.plan(sfoLhr, profile(SFO), NOW)
        val b = planner.plan(sfoLhr, profile(SFO), NOW.plusSeconds(3600))
        a.allAdvice.map { it.id }.toSet() shouldHaveSize a.allAdvice.size
        a.days shouldBe b.days
        a.phase shouldBe b.phase
        val flightA = a.allAdvice.single { it.type == AdviceType.Flight }.id
        val gentle = planner.plan(sfoLhr, profile(SFO, intensity = Intensity.Gentle), NOW)
        gentle.allAdvice.single { it.type == AdviceType.Flight }.id shouldBe flightA
        planner.plan(sfoLhr.copy(id = "other"), profile(SFO), NOW).allAdvice.single { it.type == AdviceType.Flight }.id shouldNotBe flightA
    }

    @Test
    fun `trips in progress and in the past still plan`() {
        planner.plan(sfoLhr, profile(SFO), sfoLhr.departure.plusSeconds(3600)).days.shouldNotBeEmpty()
        planner.plan(sfoLhr, profile(SFO), sfoLhr.arrival.plus(Duration.ofDays(400))).days.shouldNotBeEmpty()
    }

    @Test
    fun `plan generation is fast`() {
        val max = profile(LHR, intensity = Intensity.Max)
        repeat(3) { planner.plan(lhrSyd, max, NOW) } // JIT warm-up
        val times = (1..5).map {
            val t0 = System.nanoTime()
            planner.plan(lhrSyd, max, NOW)
            (System.nanoTime() - t0) / 1_000_000
        }
        println("plan() wall time (ms), Tier-2 multi-leg: $times")
        times.sorted()[2] shouldBeLessThan 300L
    }
}
