package dev.sebastiano.clockblocker.opus.feature.trips.list

import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import dev.sebastiano.clockblocker.opus.core.circadian.adaptationProgressAt
import dev.sebastiano.clockblocker.opus.core.circadian.daySpans
import dev.sebastiano.clockblocker.opus.core.data.plan.DefaultPlanRepository
import dev.sebastiano.clockblocker.opus.core.data.plan.NowTrip
import dev.sebastiano.clockblocker.opus.core.data.time.AppDispatchers
import dev.sebastiano.clockblocker.opus.core.data.trip.ReturnTripFactory
import dev.sebastiano.clockblocker.opus.core.data.trip.TripTitleSuggester
import dev.sebastiano.clockblocker.opus.core.testing.FakeProfileRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import dev.sebastiano.clockblocker.opus.core.testing.MutableClock
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.take
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.LocalDateTime
import kotlin.math.roundToInt
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TripSummariesTest {

    private val trip = DemoData.sfoToLhr() // SFO 2026-06-15 19:30 → LHR 2026-06-16 13:50
    private val planner = FakeJetLagPlanner()

    private fun summarize(t: Trip = trip, now: Instant, withPlan: Boolean = true) = TripSummaries.summarize(
        trip = t,
        plan = if (withPlan) planner.plan(t, DemoData.profile, DemoData.Now) else null,
        now = now,
        today = now.atZone(ZoneOffset.UTC).toLocalDate(),
        fallbackTitle = "fallback",
    )

    @Test
    fun `before the plan starts a trip is upcoming, counted in calendar days`() {
        val summary = summarize(now = Instant.parse("2026-06-12T16:00:00Z"))
        summary.phase shouldBe TripPhase.Upcoming
        summary.status shouldBe TripStatus.StartsIn(3)
        summary.adaptation.shouldBeNull()
        summary.flightProgress shouldBe 0f
    }

    @Test
    fun `without a plan, departure day says today`() {
        val summary = summarize(now = Instant.parse("2026-06-15T09:00:00Z"), withPlan = false)
        summary.status shouldBe TripStatus.StartsIn(0)
    }

    @Test
    fun `pre-trip plan days are in progress and say how far departure is`() {
        val summary = summarize(now = Instant.parse("2026-06-13T18:00:00Z"))
        summary.phase shouldBe TripPhase.InProgress
        summary.status shouldBe TripStatus.Adjusting(2)
        summary.adaptation.shouldNotBeNull()
    }

    @Test
    fun `between take-off and landing the trip is in the air with a proportional plane`() {
        val now = trip.departure.plus(Duration.between(trip.departure, trip.arrival).dividedBy(2))
        val summary = summarize(now = now)
        summary.phase shouldBe TripPhase.InProgress
        summary.status shouldBe TripStatus.InTheAir
        summary.flightProgress shouldBe (0.5f plusOrMinus 0.01f)
    }

    @Test
    fun `after landing, days count from the arrival day out of the plan's days`() {
        val summary = summarize(now = Instant.parse("2026-06-17T12:00:00Z"))
        summary.phase shouldBe TripPhase.InProgress
        summary.status shouldBe TripStatus.Day(number = 2, total = 4)
        val adaptation = summary.adaptation.shouldNotBeNull()
        adaptation.progress shouldBeGreaterThan 0.3f
        adaptation.progress shouldBeLessThan 1f
        adaptation.misalignment shouldBeGreaterThan 0f
    }

    @Test
    fun `once the plan has ended the trip is past and adapted`() {
        val summary = summarize(now = Instant.parse("2026-06-25T12:00:00Z"))
        summary.phase shouldBe TripPhase.Past
        summary.status shouldBe TripStatus.Adapted
        summary.adaptation.shouldBeNull()
        summary.flightProgress shouldBe 1f
    }

    @Test
    fun `home-time plans never show a wavy line and end as stayed on home time`() {
        val homeTrip = trip.copy(strategyOverride = AdaptationStrategy.StayOnHomeTime)
        summarize(homeTrip, now = Instant.parse("2026-06-17T12:00:00Z")).adaptation.shouldBeNull()
        summarize(homeTrip, now = Instant.parse("2026-06-25T12:00:00Z")).status shouldBe TripStatus.StayedOnHomeTime
    }

    @Test
    fun `without a plan a landed trip settles for two days, then is done`() {
        summarize(now = Instant.parse("2026-06-17T10:00:00Z"), withPlan = false).status shouldBe TripStatus.Day(2, null)
        val past = summarize(now = Instant.parse("2026-06-19T10:00:00Z"), withPlan = false)
        past.phase shouldBe TripPhase.Past
        past.status shouldBe TripStatus.Finished
    }

    @Test
    fun `shift is destination minus origin offset, normalised to the shorter way`() {
        TripSummaries.shiftHours(DemoData.sfoToLhr()) shouldBe 8f
        TripSummaries.shiftHours(DemoData.lhrToSydneyViaSingapore()) shouldBe 9f
        val westbound = DemoData.sfoToLhr().let { t ->
            t.copy(legs = listOf(t.legs.first().copy(origin = DemoData.LHR, destination = DemoData.SFO)))
        }
        TripSummaries.shiftHours(westbound) shouldBe -8f
    }

    @Test
    fun `untitled trips fall back to the suggested title`() {
        summarize(trip.copy(title = ""), now = DemoData.Now).title shouldBe "fallback"
        summarize(now = DemoData.Now).title shouldBe trip.title
    }

    @Test
    fun `sections order in progress and upcoming soonest first, past most recent first`() {
        val now = Instant.parse("2026-07-30T12:00:00Z")
        val trips = listOf(
            DemoData.sfoToLhr(LocalDate.of(2026, 6, 1), id = "old"),
            DemoData.sfoToLhr(LocalDate.of(2026, 7, 1), id = "recent"),
            DemoData.sfoToLhr(LocalDate.of(2026, 9, 1), id = "later"),
            DemoData.sfoToLhr(LocalDate.of(2026, 8, 10), id = "sooner"),
        )
        val summaries = trips.map { summarize(it, now = now, withPlan = false) }
        val sections = TripSummaries.sections(summaries)
        sections.getValue(TripPhase.Past).map { it.id } shouldContainExactly listOf("recent", "old")
        sections.getValue(TripPhase.Upcoming).map { it.id } shouldContainExactly listOf("sooner", "later")
        TripSummaries.returnCandidate(summaries).shouldNotBeNull().id shouldBe "recent"
    }

    @Test
    fun `upcoming trips carry the planner's days to adapt, others don't`() {
        val plan = planner.plan(trip, DemoData.profile, DemoData.Now)
        summarize(now = Instant.parse("2026-06-12T16:00:00Z")).daysToAdapt shouldBe plan.estimatedDaysToAdapt
        summarize(now = Instant.parse("2026-06-13T18:00:00Z")).daysToAdapt.shouldBeNull()
        summarize(now = Instant.parse("2026-06-12T16:00:00Z"), withPlan = false).daysToAdapt.shouldBeNull()
        val homeTime = trip.copy(strategyOverride = AdaptationStrategy.StayOnHomeTime)
        summarize(t = homeTime, now = Instant.parse("2026-06-12T16:00:00Z")).daysToAdapt.shouldBeNull()
    }

    @Test
    fun `in-progress trips with a plan know the body clock time`() {
        val now = Instant.parse("2026-06-17T09:00:00Z")
        val plan = planner.plan(trip, DemoData.profile, DemoData.Now)
        val summary = summarize(now = now)
        summary.bodyTime shouldBe now.atOffset(plan.bodyOffsetAt(now)).toLocalTime()
        summarize(now = Instant.parse("2026-06-12T16:00:00Z")).bodyTime.shouldBeNull()
        summarize(now = now, withPlan = false).bodyTime.shouldBeNull()
    }

    @Test
    fun `the list sky follows the body clock of a trip under way, else local time`() {
        val now = Instant.parse("2026-06-17T09:00:00Z")
        val zone = ZoneOffset.ofHours(2)
        val active = summarize(now = now)
        val upcoming = summarize(now = Instant.parse("2026-06-12T16:00:00Z"))
        TripSummaries.sky(listOf(upcoming, active), now, zone) shouldBe TripsSky(active.bodyTime!!, bodyClock = true)
        TripSummaries.sky(listOf(upcoming), now, zone) shouldBe TripsSky(java.time.LocalTime.of(11, 0), bodyClock = false)
    }

    @Test
    fun `when active trips overlap the sky follows the latest departure, like the plan repository`() {
        val now = Instant.parse("2026-06-17T09:00:00Z")
        val outbound = summarize(now = now)
        val later = trip.copy(
            id = "later",
            legs = trip.legs.map { it.copy(departureLocal = it.departureLocal.plusDays(2), arrivalLocal = it.arrivalLocal.plusDays(2)) },
        )
        val next = outbound.copy(trip = later, bodyTime = java.time.LocalTime.of(3, 0))
        TripSummaries.sky(listOf(outbound, next), now, ZoneOffset.UTC) shouldBe TripsSky(java.time.LocalTime.of(3, 0), bodyClock = true)
    }

    @Test
    fun `partition puts the in-progress trip on Now and all upcoming trips in Trips`() {
        val now = Instant.parse("2026-06-17T12:00:00Z")
        val inProgress = summarize(DemoData.sfoToLhr(LocalDate.of(2026, 6, 15), id = "active"), now = now)
        val upcoming1 = summarize(DemoData.sfoToLhr(LocalDate.of(2026, 7, 1), id = "next-1"), now = now)
        val upcoming2 = summarize(DemoData.sfoToLhr(LocalDate.of(2026, 8, 1), id = "next-2"), now = now)
        val past = summarize(DemoData.sfoToLhr(LocalDate.of(2026, 5, 1), id = "past"), now = now)

        val partitioned = TripSummaries.partition(listOf(upcoming2, past, inProgress, upcoming1), now)
        partitioned.nowTrip?.id shouldBe "active"
        partitioned.upcoming.map { it.id } shouldContainExactly listOf("next-1", "next-2")
        partitioned.past.map { it.id } shouldContainExactly listOf("past")
    }

    @Test
    fun `partition without an in-progress trip puts the first upcoming trip on Now and the following ones in Trips`() {
        val now = Instant.parse("2026-06-01T12:00:00Z")
        val first = summarize(DemoData.sfoToLhr(LocalDate.of(2026, 6, 15), id = "first"), now = now)
        val second = summarize(DemoData.sfoToLhr(LocalDate.of(2026, 7, 10), id = "second"), now = now)
        val third = summarize(DemoData.sfoToLhr(LocalDate.of(2026, 8, 5), id = "third"), now = now)
        val past = summarize(DemoData.sfoToLhr(LocalDate.of(2026, 4, 1), id = "past"), now = now)

        val partitioned = TripSummaries.partition(listOf(third, past, first, second), now)
        partitioned.nowTrip?.id shouldBe "first"
        partitioned.upcoming.map { it.id } shouldContainExactly listOf("second", "third")
        partitioned.past.map { it.id } shouldContainExactly listOf("past")
    }

    @Test
    fun `partition with a single upcoming trip leaves Trips upcoming empty because that trip is on Now`() {
        val now = Instant.parse("2026-06-01T12:00:00Z")
        val only = summarize(DemoData.sfoToLhr(LocalDate.of(2026, 6, 15), id = "only"), now = now)

        val partitioned = TripSummaries.partition(listOf(only), now)
        partitioned.nowTrip?.id shouldBe "only"
        partitioned.upcoming shouldContainExactly emptyList()
        partitioned.past shouldContainExactly emptyList()
    }

    @Test
    fun `partition with only past trips has no Now trip and keeps past trips most-recent first`() {
        val now = Instant.parse("2026-09-01T12:00:00Z")
        val older = summarize(DemoData.sfoToLhr(LocalDate.of(2026, 5, 1), id = "older"), now = now)
        val newer = summarize(DemoData.sfoToLhr(LocalDate.of(2026, 7, 1), id = "newer"), now = now)

        val partitioned = TripSummaries.partition(listOf(older, newer), now)
        partitioned.nowTrip.shouldBeNull()
        partitioned.upcoming shouldContainExactly emptyList()
        partitioned.past.map { it.id } shouldContainExactly listOf("newer", "older")
    }

    @Test
    fun `partition moves an outbound active trip superseded by a return trip to past instead of upcoming`() {
        val now = Instant.parse("2026-06-17T12:00:00Z")
        val outbound = summarize(DemoData.sfoToLhr(LocalDate.of(2026, 6, 15), id = "outbound"), now = now)
        val returnTrip = summarize(DemoData.sfoToLhr(LocalDate.of(2026, 6, 19), id = "return"), now = now)

        outbound.phase shouldBe TripPhase.InProgress
        returnTrip.phase shouldBe TripPhase.InProgress

        val partitioned = TripSummaries.partition(listOf(outbound, returnTrip), now)
        partitioned.nowTrip?.id shouldBe "return"
        partitioned.upcoming shouldContainExactly emptyList()
        partitioned.past.map { it.id } shouldContainExactly listOf("outbound")
        partitioned.past.single().phase shouldBe TripPhase.Past
        // Mid-adaptation when the return trip took over: still adapting, so not "Adapted".
        val status = partitioned.past.single().status
        (status is TripStatus.PartlyAdapted && status.percent in 1..99) shouldBe true
    }

    @Test
    fun `a superseded trip whose plan kept home time says so, whoever chose it`() {
        val now = Instant.parse("2026-06-17T12:00:00Z")
        val shortTrip = DemoData.sfoToLhr(LocalDate.of(2026, 6, 15), id = "short")
        val plannerChoseHome = planner.plan(shortTrip, DemoData.profile, DemoData.Now).copy(strategy = AdaptationStrategy.StayOnHomeTime)
        val outbound = TripSummaries.summarize(shortTrip, plannerChoseHome, now, now.atZone(ZoneOffset.UTC).toLocalDate(), "short")
        val back = summarize(DemoData.sfoToLhr(LocalDate.of(2026, 6, 19), id = "return"), now = now)

        TripSummaries.partition(listOf(outbound, back), now).past.single().status shouldBe TripStatus.StayedOnHomeTime
    }

    @Test
    fun `a superseded trip's progress is frozen when the next trip took over`() {
        val outboundTrip = DemoData.sfoToLhr(LocalDate.of(2026, 6, 15), id = "outbound")
        val backTrip = DemoData.sfoToLhr(LocalDate.of(2026, 6, 19), id = "return")
        val takeover = NowTrip.window(backTrip, planner.plan(backTrip, DemoData.profile, DemoData.Now)).start
        val expected = (planner.plan(outboundTrip, DemoData.profile, DemoData.Now).adaptationProgressAt(takeover) * 100).roundToInt()

        listOf(Instant.parse("2026-06-17T12:00:00Z"), Instant.parse("2026-06-18T20:00:00Z")).forEach { now ->
            val partitioned = TripSummaries.partition(listOf(summarize(outboundTrip, now = now), summarize(backTrip, now = now)), now)
            withClue("at $now") { partitioned.past.single().status shouldBe TripStatus.PartlyAdapted(expected) }
        }
    }

    @Test
    fun `the last night's sleep still belongs to the trip, on its last day`() {
        val plan = planner.plan(trip, DemoData.profile, DemoData.Now)
        val lastMidnight = plan.daySpans().last().end
        val end = NowTrip.window(trip, plan).end
        val total = plan.daySpans().last().day.index

        val asleep = summarize(now = lastMidnight.plusSeconds(3600))
        asleep.phase shouldBe TripPhase.InProgress
        asleep.status shouldBe TripStatus.Day(total, total)
        summarize(now = end.minusSeconds(1)).phase shouldBe TripPhase.InProgress
        val over = summarize(now = end)
        over.phase shouldBe TripPhase.Past
        over.status shouldBe TripStatus.Adapted
    }

    @Test
    fun `the Trips tab and the Now tab always agree on the now trip, and every trip shows exactly once`() = runTest {
        val trips = listOf(
            DemoData.sfoToLhr(),
            ReturnTripFactory(MutableClock(), TripTitleSuggester()).create(DemoData.sfoToLhr(), LocalDateTime.of(2026, 6, 20, 11, 0)) { "back" },
            DemoData.lhrToSydneyViaSingapore(LocalDate.of(2026, 6, 28), id = "next"),
            DemoData.sfoToLhr(LocalDate.of(2026, 8, 30), id = "far"),
        )
        val clock = MutableClock(DemoData.Now)
        val repo = DefaultPlanRepository(
            trips = FakeTripRepository(trips),
            profiles = FakeProfileRepository(DemoData.profile),
            planner = planner,
            clock = clock,
            ticker = clock,
            dispatchers = AppDispatchers.single(StandardTestDispatcher(testScheduler)),
            scope = backgroundScope,
        )
        val plans = trips.associate { it.id to planner.plan(it, DemoData.profile, DemoData.Now) }
        // Every boundary of every window, a second either side, plus random instants.
        val boundaries = trips.flatMap { t ->
            val w = NowTrip.window(t, plans.getValue(t.id))
            listOf(w.start, w.end, t.departure, t.arrival, plans.getValue(t.id).daySpans().last().end)
        }.flatMap { listOf(it.minusSeconds(1), it, it.plusSeconds(1)) }
        val from = Instant.parse("2026-06-01T00:00:00Z").epochSecond
        val to = Instant.parse("2026-10-01T00:00:00Z").epochSecond
        val instants = boundaries + Arb.long(from, to).take(500, RandomSource.seeded(42)).map(Instant::ofEpochSecond).toList()
        instants.forEach { now ->
            val today = now.atZone(ZoneOffset.UTC).toLocalDate()
            val summaries = trips.map { TripSummaries.summarize(it, plans.getValue(it.id), now, today, it.id) }
            val partitioned = TripSummaries.partition(summaries, now)
            withClue("at $now") {
                partitioned.nowTrip?.id shouldBe repo.currentPlan(trips, DemoData.profile, now)?.tripId
                val shown = listOfNotNull(partitioned.nowTrip) + partitioned.upcoming + partitioned.past
                shown.map { it.id }.sorted() shouldContainExactly trips.map { it.id }.sorted()
            }
        }
    }

    @Test
    fun `summaries record days saved when the plan shifts the body clock`() {
        val plan = planner.plan(trip, DemoData.profile, DemoData.Now)
        val expected = (plan.estimatedDaysWithoutPlan - plan.estimatedDaysToAdapt).coerceAtLeast(0.0)
        summarize(now = Instant.parse("2026-06-25T12:00:00Z")).daysSaved shouldBe expected
        val homeTime = trip.copy(strategyOverride = AdaptationStrategy.StayOnHomeTime)
        summarize(t = homeTime, now = Instant.parse("2026-06-25T12:00:00Z")).daysSaved shouldBe 0.0
    }
}
