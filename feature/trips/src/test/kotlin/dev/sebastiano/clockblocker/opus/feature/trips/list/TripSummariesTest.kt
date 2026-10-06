package dev.sebastiano.clockblocker.opus.feature.trips.list

import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import io.kotest.matchers.collections.shouldContainExactly
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
}
