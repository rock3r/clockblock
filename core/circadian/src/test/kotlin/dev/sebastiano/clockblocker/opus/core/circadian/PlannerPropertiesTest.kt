package dev.sebastiano.clockblocker.opus.core.circadian

import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.NOW
import dev.sebastiano.clockblocker.opus.core.circadian.Fixtures.place
import dev.sebastiano.clockblocker.opus.core.circadian.engine.AdviceAssembler
import dev.sebastiano.clockblocker.opus.core.circadian.engine.PlanBuilder
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

/** Invariants that must hold for every trip, profile and zone combination (kotest-property). */
class PlannerPropertiesTest {
    private val planner = DefaultJetLagPlanner()

    data class Scenario(val trip: Trip, val profile: UserProfile) {
        override fun toString() = buildString {
            append(trip.legs.joinToString(" | ") { "${it.origin.zoneId} ${it.departureLocal} -> ${it.destination.zoneId} ${it.arrivalLocal}" })
            append(" return=${trip.returnDeparture} override=${trip.strategyOverride} profile=$profile")
        }
    }

    private val zones = listOf(
        "Pacific/Kiritimati", "Pacific/Honolulu", "Asia/Kathmandu", "Pacific/Chatham", "Asia/Kolkata",
        "America/St_Johns", "Australia/Eucla", "Pacific/Pago_Pago", "Etc/GMT+12", "Europe/London",
        "America/New_York", "Asia/Tokyo", "America/Los_Angeles", "Australia/Sydney", "Pacific/Auckland",
        "Asia/Singapore", "America/Sao_Paulo", "Pacific/Tongatapu", "Europe/Berlin", "Asia/Dubai",
    )

    private val arbProfile = arbitrary {
        val bedtime = LocalTime.of(Arb.int(19..27).bind() % 24, Arb.element(0, 15, 30, 45).bind())
        val sleepMinutes = Arb.int(16..44).bind() * 15L
        UserProfile(
            homeZoneId = Arb.element(zones).bind(),
            sleep = SleepWindow(bedtime, bedtime.plusMinutes(sleepMinutes)),
            chronotype = Arb.enum<Chronotype>().bind(),
            useMelatonin = Arb.boolean().bind(),
            useCaffeine = Arb.boolean().bind(),
            canSleepOnPlanes = Arb.boolean().bind(),
            adjustBeforeDeparture = Arb.boolean().bind(),
            intensity = Arb.enum<Intensity>().bind(),
        )
    }

    /** Realistic itineraries: 1–3 legs, short/24–72 h/≥72 h stops, optional return, optional override. */
    private val arbTrip = arbitrary {
        val legCount = Arb.int(1..3).bind()
        val places = (0..legCount).map { place("P$it", Arb.element(zones).bind()) }
        var dep = LocalDate.of(2026, 1, 1).plusDays(Arb.int(0..700).bind().toLong())
            .atTime(Arb.int(0..23).bind(), Arb.element(0, 7, 15, 30, 45, 53).bind())
            .atZone(places[0].zone).toInstant()
        val legs = ArrayList<FlightLeg>()
        var arr = dep
        for (i in 0 until legCount) {
            arr = dep.plus(Duration.ofMinutes(Arb.int(30..20 * 60).bind().toLong()))
            val from = places[i]
            val to = places[i + 1]
            legs += FlightLeg("L$i", from, to, LocalDateTime.ofInstant(dep, from.zone), LocalDateTime.ofInstant(arr, to.zone))
            val stopMinutes = when (Arb.int(0..2).bind()) {
                0 -> Arb.int(45..600).bind()
                1 -> Arb.int(24 * 60..72 * 60).bind()
                else -> Arb.int(72 * 60..10 * 24 * 60).bind()
            }
            dep = arr.plus(Duration.ofMinutes(stopMinutes.toLong()))
        }
        val returnDeparture = when (Arb.int(0..3).bind()) {
            0, 1 -> null
            2 -> arr.plus(Duration.ofHours(Arb.int(6..71).bind().toLong()))
            else -> arr.plus(Duration.ofHours(Arb.int(72..500).bind().toLong()))
        }
        val override = Arb.element(AdaptationStrategy.entries).orNull(0.6).bind()
        Trip("trip-${Arb.int(0..9).bind()}", "Prop", legs, NOW, override, returnDeparture)
    }

    private val arbScenario = arbitrary { Scenario(arbTrip.bind(), arbProfile.bind()) }

    /** Garbage itineraries: random local times in any order (arrival before departure, overlapping legs…). */
    private val arbMessyScenario = arbitrary {
        val legCount = Arb.int(1..3).bind()
        val base = LocalDateTime.of(2026, 3, 1, 0, 0)
        val legs = (0 until legCount).map {
            FlightLeg(
                "M$it",
                place("A$it", Arb.element(zones).bind()),
                place("B$it", Arb.element(zones).bind()),
                base.plusMinutes(Arb.int(0..14 * 24 * 60).bind().toLong()),
                base.plusMinutes(Arb.int(0..14 * 24 * 60).bind().toLong()),
            )
        }
        val ret = Instant.parse("2026-03-01T00:00:00Z").plus(Duration.ofHours(Arb.int(-48..600).bind().toLong())).takeIf { Arb.boolean().bind() }
        Scenario(Trip("messy", "Messy", legs, NOW, Arb.element(AdaptationStrategy.entries).orNull(0.5).bind(), ret), arbProfile.bind())
    }

    @Test
    fun `every plan satisfies the invariants`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 20_261_004L, iterations = 400), arbScenario) { s ->
                withClue(s) { checkInvariants(s, strict = true) }
            }
        }
    }

    @Test
    fun `malformed itineraries never throw and still produce consistent plans`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 4_102_026L, iterations = 200), arbMessyScenario) { s ->
                withClue(s) { checkInvariants(s, strict = false) }
            }
        }
    }

    @Test
    fun `extreme zone pairs plan in both directions`() {
        val extremes = listOf("Pacific/Kiritimati", "Etc/GMT+12", "Pacific/Pago_Pago", "Asia/Kathmandu", "Pacific/Chatham", "Australia/Eucla")
        val profile = Fixtures.profile(place("H", "Europe/London"))
        for (a in extremes) for (b in extremes) {
            if (a == b) continue
            val from = place("A", a)
            val to = place("B", b)
            val dep = LocalDateTime.of(2026, 10, 1, 9, 0)
            val arrInstant = dep.atZone(from.zone).toInstant().plus(Duration.ofHours(11))
            val trip = Trip("x", "x", listOf(FlightLeg("1", from, to, dep, LocalDateTime.ofInstant(arrInstant, to.zone))), NOW)
            withClue("$a -> $b") { checkInvariants(Scenario(trip, profile), strict = true) }
        }
    }

    private fun checkInvariants(s: Scenario, strict: Boolean) {
        val plan = planner.plan(s.trip, s.profile, NOW)
        val advice = plan.allAdvice

        // finite, non-negative estimates; a finite shift
        (plan.estimatedDaysToAdapt.isFinite() && plan.estimatedDaysToAdapt >= 0.0) shouldBe true
        (plan.estimatedDaysWithoutPlan.isFinite() && plan.estimatedDaysWithoutPlan >= 0.0) shouldBe true
        plan.shiftHours.isFinite() shouldBe true
        plan.days.isNotEmpty() shouldBe true

        // ids: unique, deterministic, independent of `now`
        advice.map { it.id }.toSet().size shouldBe advice.size
        planner.plan(s.trip, s.profile, NOW.plus(Duration.ofDays(3))).let {
            it.days shouldBe plan.days
            it.phase shouldBe plan.phase
        }

        // cards are sorted within each day and never contradict each other
        plan.days.forEach { day -> day.advice.zipWithNext().all { (a, b) -> !b.start.isBefore(a.start) } shouldBe true }
        advice.forEach { (!it.end.isBefore(it.start)) shouldBe true }
        conflictingPairs(advice) shouldBe emptyList()

        // every card sits inside its day; day spans reconstruct exactly what the planner used
        val spans = plan.daySpans()
        spans.zipWithNext().all { (a, b) -> a.end == b.start && a.start.isBefore(a.end) } shouldBe true
        spans.forEach { span -> span.day.advice.all { it.start in span } shouldBe true }
        advice.all { !it.end.isAfter(spans.last().end) } shouldBe true
        val builder = PlanBuilder(PlannerConfig().forProfile(s.profile), s.trip, s.profile, NOW).also { it.build() }
        spans.map { Triple(it.day.index, it.start, it.end) } shouldBe builder.spans.map { Triple(it.index, it.start, it.end) }

        // hourly phase covering the days
        plan.phase.first().instant shouldBe spans.first().start
        plan.phase.zipWithNext().all { (a, b) -> Duration.between(a.instant, b.instant) == Duration.ofHours(1) } shouldBe true

        // no cycle shifts the clock by more than the largest per-cycle cap
        val cbts = plan.phase.map { it.cbtMin }.distinct().sorted()
        cbts.zipWithNext().forEach { (a, b) ->
            val shift = 24.0 - Duration.between(a, b).toMillis() / 3_600_000.0
            withClue("CBTmin $a -> $b") { (abs(shift) <= MAX_CYCLE_SHIFT + 0.02) shouldBe true }
        }

        if (!strict) return
        // with nowhere else to go, the clock ends up on destination time
        if (s.trip.returnDeparture == null && plan.strategy == AdaptationStrategy.Adapt && plan.direction != ShiftDirection.None) {
            val dest = s.trip.destination.zone.rules.getOffset(s.trip.arrival).totalSeconds / 60
            val diff = Math.floorMod(plan.phase.last().bodyUtcOffsetMinutes - dest + 720, 1440) - 720
            withClue("final body offset ${plan.phase.last().bodyUtcOffsetMinutes} vs destination $dest") {
                (abs(diff) <= DONE_TOLERANCE_MINUTES + 1) shouldBe true
            }
        }
    }

    private fun conflictingPairs(advice: List<Advice>): List<Pair<Advice, Advice>> {
        val windows = advice.filter { !it.type.isMoment && it.type != AdviceType.Flight }.sortedBy { it.start }
        val out = ArrayList<Pair<Advice, Advice>>()
        for (i in windows.indices) {
            for (j in i + 1 until windows.size) {
                if (!windows[j].start.isBefore(windows[i].end)) break
                if (AdviceAssembler.conflicts(windows[i].type, windows[j].type)) out += windows[i] to windows[j]
            }
        }
        return out
    }

    private operator fun PlanDaySpan.contains(i: Instant) = !i.isBefore(start) && i.isBefore(end)

    private companion object {
        /** max(POST_DEL_CAP, POST_ADV_CAP, PRE_DEL_CAP_LIGHTBOX) */
        const val MAX_CYCLE_SHIFT = 2.0
        const val DONE_TOLERANCE_MINUTES = 30
    }
}
