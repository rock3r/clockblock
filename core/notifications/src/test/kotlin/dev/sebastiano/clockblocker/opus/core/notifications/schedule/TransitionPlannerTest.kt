package dev.sebastiano.clockblocker.opus.core.notifications.schedule

import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Caffeine
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Flight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Melatonin
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Nap
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.SeeBrightLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Sleep
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialGeometry
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.MomentDueMinutes
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.notifications.advice
import dev.sebastiano.clockblocker.opus.core.notifications.planOf
import dev.sebastiano.clockblocker.opus.core.notifications.planOfDays
import dev.sebastiano.clockblocker.opus.core.notifications.utc
import io.kotest.matchers.collections.shouldBeSortedBy
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.bind
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class TransitionPlannerTest {

    private val settings = AppSettings(reminderLeadMinutes = 15)

    private fun summary(plan: dev.sebastiano.clockblocker.opus.core.model.JetLagPlan, s: AppSettings = settings) =
        TransitionPlanner.transitions(plan, s).map { Triple(it.at, it.kind, it.advice.type) }

    @Nested
    inner class LeadTimes {
        @Test
        fun `window gets a lead reminder, a silent start and a silent end`() {
            val plan = planOf(advice(AvoidLight, "2026-10-10T18:00", "2026-10-10T20:00"))

            summary(plan) shouldContainExactly listOf(
                Triple(utc("2026-10-10T17:45"), TransitionKind.Upcoming, AvoidLight),
                Triple(utc("2026-10-10T18:00"), TransitionKind.Start, AvoidLight),
                Triple(utc("2026-10-10T20:00"), TransitionKind.End, AvoidLight),
            )
        }

        @Test
        fun `lead time follows the setting`() {
            val plan = planOf(advice(SeeBrightLight, "2026-10-10T08:00", "2026-10-10T10:00"))

            val upcoming = TransitionPlanner.transitions(plan, settings.copy(reminderLeadMinutes = 30))
                .single { it.kind == TransitionKind.Upcoming }

            upcoming.at shouldBe utc("2026-10-10T07:30")
        }

        @Test
        fun `zero lead merges reminder and start into one alerting alarm`() {
            val plan = planOf(advice(AvoidLight, "2026-10-10T18:00", "2026-10-10T20:00"))

            val points = TransitionPlanner.upcoming(plan, settings.copy(reminderLeadMinutes = 0), utc("2026-10-10T12:00"))

            points.first().at shouldBe utc("2026-10-10T18:00")
            points.first().transitions.map { it.kind } shouldContainExactly listOf(TransitionKind.Upcoming, TransitionKind.Start)
            points.first().alerts shouldBe true
            points shouldHaveSize 2
        }

        @Test
        fun `lead is absolute time across a DST change`() {
            // Europe/London falls back at 2026-10-25 02:00 BST -> 01:00 GMT (01:00Z).
            val start = ZonedDateTime.of(2026, 10, 25, 1, 10, 0, 0, ZoneId.of("Europe/London")).withLaterOffsetAtOverlap()
            val plan = planOf(
                advice(SeeBrightLight, start.toInstant().toString(), start.plusHours(2).toInstant().toString()),
            )

            val upcoming = TransitionPlanner.transitions(plan, settings.copy(reminderLeadMinutes = 30))
                .single { it.kind == TransitionKind.Upcoming }

            Duration.between(upcoming.at, start.toInstant()) shouldBe Duration.ofMinutes(30)
            // 30 real minutes before 01:10 GMT is 01:40 BST on the wall clock: the lead never jumps by an hour.
            upcoming.at.atZone(ZoneId.of("Europe/London")).toLocalTime().toString() shouldBe "01:40"
        }

        @Test
        fun `transitions are independent of the plan's zones`() {
            val advice = arrayOf(
                advice(Sleep, "2026-10-10T22:00", "2026-10-11T06:00"),
                advice(SeeBrightLight, "2026-10-11T06:30", "2026-10-11T08:00"),
            )
            val westward = planOf(*advice, origin = "Asia/Tokyo", destination = "America/Los_Angeles")
            val eastward = planOf(*advice, origin = "America/Los_Angeles", destination = "Asia/Tokyo")

            summary(westward) shouldBe summary(eastward)
        }
    }

    @Nested
    inner class SleepSuppression {
        private val night = advice(Sleep, "2026-10-10T23:00", "2026-10-11T07:00")

        @Test
        fun `nothing fires strictly inside a sleep window except the wake-up`() {
            val plan = planOf(
                night,
                advice(AvoidLight, "2026-10-10T21:00", "2026-10-10T23:30"), // ends inside
                advice(Caffeine, "2026-10-11T03:00", "2026-10-11T04:00"), // entirely inside
                advice(SeeBrightLight, "2026-10-11T06:30", "2026-10-11T09:00"), // starts inside, ends after
            )

            val transitions = TransitionPlanner.transitions(plan, settings)

            transitions.filter { it.at.isAfter(night.start) && it.at.isBefore(night.end) }.shouldHaveSize(0)
            transitions.map { it.advice.type } shouldNotContain Caffeine
            transitions.single { it.kind == TransitionKind.WakeUp }.at shouldBe night.end
            // Bright light starts while asleep: no lead reminder for it, only its end after waking.
            transitions.filter { it.advice.type == SeeBrightLight }.map { it.kind } shouldContainExactly
                listOf(TransitionKind.End)
        }

        @Test
        fun `sleep itself is reminded before and started at its edge`() {
            val transitions = TransitionPlanner.transitions(planOf(night), settings)

            transitions.map { it.kind to it.at } shouldContainExactly listOf(
                TransitionKind.Upcoming to utc("2026-10-10T22:45"),
                TransitionKind.Start to night.start,
                TransitionKind.WakeUp to night.end,
            )
        }

        @Test
        fun `melatonin at bedtime is kept, melatonin in the middle of the night is not`() {
            val plan = planOf(
                night,
                advice(Melatonin, "2026-10-10T23:00", id = "mel-bedtime"),
                advice(Melatonin, "2026-10-11T02:00", id = "mel-night"),
            )

            val moments = TransitionPlanner.transitions(plan, settings).filter { it.kind == TransitionKind.Moment }

            moments.map { it.advice.id } shouldContainExactly listOf("mel-bedtime")
        }

        @Test
        fun `a lead reminder that would ring during sleep is dropped even if the window starts after waking`() {
            val plan = planOf(night, advice(SeeBrightLight, "2026-10-11T07:05", "2026-10-11T09:00"))

            val kinds = TransitionPlanner.transitions(plan, settings).filter { it.advice.type == SeeBrightLight }.map { it.kind }

            kinds shouldContainExactly listOf(TransitionKind.Start, TransitionKind.End)
        }

        @Test
        fun `naps also protect sleep and end with a wake-up`() {
            val plan = planOf(
                advice(Nap, "2026-10-10T14:00", "2026-10-10T14:40"),
                advice(Caffeine, "2026-10-10T14:20", "2026-10-10T16:00"),
            )

            val transitions = TransitionPlanner.transitions(plan, settings)

            transitions.single { it.advice.type == Nap && it.kind == TransitionKind.WakeUp }.at shouldBe utc("2026-10-10T14:40")
            transitions.filter { it.advice.type == Caffeine }.map { it.kind } shouldContainExactly listOf(TransitionKind.End)
        }
    }

    @Nested
    inner class Deduplication {
        @Test
        fun `advice repeated across plan days is scheduled once`() {
            val sleep = advice(Sleep, "2026-10-10T23:00", "2026-10-11T07:00", id = "night-1")
            val plan = planOfDays(listOf(listOf(sleep), listOf(sleep)))

            TransitionPlanner.transitions(plan, settings).map { it.key }.shouldBeUnique()
            TransitionPlanner.transitions(plan, settings) shouldHaveSize 3
        }

        @Test
        fun `identical windows with different ids are merged`() {
            val plan = planOf(
                advice(AvoidLight, "2026-10-10T18:00", "2026-10-10T20:00", id = "a"),
                advice(AvoidLight, "2026-10-10T18:00", "2026-10-10T20:00", id = "b"),
            )

            TransitionPlanner.transitions(plan, settings) shouldHaveSize 3
        }

        @Test
        fun `an end and a start at the same instant share one alarm`() {
            val plan = planOf(
                advice(SeeBrightLight, "2026-10-10T08:00", "2026-10-10T10:00"),
                advice(AvoidLight, "2026-10-10T10:00", "2026-10-10T12:00"),
            )

            val points = TransitionPlanner.upcoming(plan, settings.copy(reminderLeadMinutes = 0), utc("2026-10-10T09:00"))

            points.first().at shouldBe utc("2026-10-10T10:00")
            points.first().transitions.map { it.kind to it.advice.type }.toSet() shouldBe setOf(
                TransitionKind.Upcoming to AvoidLight,
                TransitionKind.Start to AvoidLight,
                TransitionKind.End to SeeBrightLight,
            )
        }
    }

    @Nested
    inner class Selection {
        private val plan = planOf(
            advice(SeeBrightLight, "2026-10-10T08:00", "2026-10-10T10:00"),
            advice(AvoidLight, "2026-10-10T18:00", "2026-10-10T20:00"),
            advice(Sleep, "2026-10-10T23:00", "2026-10-11T07:00"),
        )

        @Test
        fun `only instants strictly after now, in order, limited`() {
            val points = TransitionPlanner.upcoming(plan, settings, now = utc("2026-10-10T08:00"), limit = 3)

            points.map { it.at } shouldContainExactly listOf(
                utc("2026-10-10T10:00"),
                utc("2026-10-10T17:45"),
                utc("2026-10-10T18:00"),
            )
        }

        @Test
        fun `transitionsAt re-derives what happens at a fired alarm from the current plan`() {
            TransitionPlanner.transitionsAt(plan, settings, utc("2026-10-10T17:45")).map { it.kind } shouldContainExactly
                listOf(TransitionKind.Upcoming)
            TransitionPlanner.transitionsAt(plan, settings, utc("2026-10-10T17:46")) shouldHaveSize 0
        }

        @Test
        fun `flights refresh at take-off and landing but never remind`() {
            val flight = planOf(advice(Flight, "2026-10-10T12:00", "2026-10-10T23:00", detail = "BA7"))

            TransitionPlanner.transitions(flight, settings).map { it.kind } shouldContainExactly
                listOf(TransitionKind.Start, TransitionKind.End)
        }

        @Test
        fun `with reminders disabled only silent refreshes remain`() {
            val withMelatonin = planOf(*plan.allAdvice.toTypedArray(), advice(Melatonin, "2026-10-10T21:00"))

            val transitions = TransitionPlanner.transitions(withMelatonin, settings.copy(remindersEnabled = false))

            transitions.none { it.kind.alerts } shouldBe true
            transitions.map { it.advice.type } shouldNotContain Melatonin
            transitions.single { it.advice.type == Sleep && it.at == utc("2026-10-11T07:00") }.kind shouldBe TransitionKind.End
        }
    }

    @Test
    fun `property - alarms are sorted, unique, in the future, limited and never ring during sleep`() = runTest {
        val base = utc("2026-10-10T00:00")
        val adviceArb = Arb.bind(
            Arb.enum<AdviceType>(),
            Arb.int(0, 72 * 60),
            Arb.int(10, 10 * 60),
        ) { type, startMinute, length ->
            val start = base.plus(Duration.ofMinutes(startMinute.toLong()))
            val end = if (type.isMoment) start else start.plus(Duration.ofMinutes(length.toLong()))
            advice(type, start.toString(), end.toString(), id = "$type-$startMinute-$length")
        }
        checkAll(200, Arb.list(adviceArb, 1..25), Arb.int(0, 72 * 60), Arb.int(0, 60)) { list, nowMinute, lead ->
            val plan = planOf(*list.toTypedArray())
            val now = base.plus(Duration.ofMinutes(nowMinute.toLong()))
            val s = AppSettings(reminderLeadMinutes = lead)

            val points = TransitionPlanner.upcoming(plan, s, now, limit = 8)

            points.size shouldBe points.size.coerceAtMost(8)
            points.map { it.at }.shouldBeUnique()
            points.shouldBeSortedBy { it.at }
            points.all { it.at.isAfter(now) } shouldBe true
            val sleepers = list.filter { it.type == AdviceType.Sleep || it.type == AdviceType.Nap }
            points.flatMap { it.transitions }.forEach { t ->
                val ringsWhileAsleep = sleepers.any { s2 -> s2.id != t.advice.id && t.at.isAfter(s2.start) && t.at.isBefore(s2.end) }
                ringsWhileAsleep shouldBe false
            }
        }
    }

    @Nested
    inner class DialLookahead {
        @Test
        fun `the next refresh for the widget dial is when the next block comes into its view`() {
            val plan = planOf(
                advice(AvoidLight, "2026-10-10T18:00", "2026-10-10T20:00"),
                advice(Sleep, "2026-10-11T23:00", "2026-10-12T07:00"),
            )

            // The avoid block is already in view (it starts in 6 h); the sleep enters 15 h before it starts.
            TransitionPlanner.nextDialEntry(plan, utc("2026-10-10T12:00")) shouldBe utc("2026-10-11T08:00")
            TransitionPlanner.nextDialEntry(plan, utc("2026-10-11T08:00")) shouldBe null
        }

        @Test
        fun `the lookahead is the dial's future, less an hour for a clock change on the way`() {
            val future = DialGeometry.MinutesPerDay - DialState.PastWindowMinutes
            TransitionPlanner.DIAL_ENTRY_LEAD.plusHours(1).toMinutes() shouldBe future.toLong()
        }

        @Test
        fun `a moment gets a refresh when it stops being due, so the widget stops featuring it`() {
            val plan = planOf(
                advice(Melatonin, "2026-10-10T19:00"),
                advice(AvoidLight, "2026-10-10T18:00", "2026-10-10T20:00"),
            )

            TransitionPlanner.nextMomentEnd(plan, utc("2026-10-10T12:00")) shouldBe utc("2026-10-10T19:01")
            TransitionPlanner.nextMomentEnd(plan, utc("2026-10-10T19:00")) shouldBe utc("2026-10-10T19:01")
            // Windows never get one: their end is already a transition.
            TransitionPlanner.nextMomentEnd(plan, utc("2026-10-10T19:01")) shouldBe null
        }

        @Test
        fun `a moment's refresh is when the dial stops featuring it`() {
            TransitionPlanner.MOMENT_DUE.toMinutes() shouldBe MomentDueMinutes.toLong()
        }

        @Test
        fun `the widget dial's next refresh is the earlier of a block coming into view and a moment ending`() {
            val plan = planOf(
                advice(Melatonin, "2026-10-10T19:00"),
                advice(Sleep, "2026-10-11T23:00", "2026-10-12T07:00"),
            )

            TransitionPlanner.nextDialRefresh(plan, utc("2026-10-10T12:00")) shouldBe utc("2026-10-10T19:01")
            TransitionPlanner.nextDialRefresh(plan, utc("2026-10-10T19:01")) shouldBe utc("2026-10-11T08:00")
        }
    }
}
