package dev.sebastiano.clockblocker.opus.core.notifications.now

import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidCaffeine
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Caffeine
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Flight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Melatonin
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.SeeBrightLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Sleep
import dev.sebastiano.clockblocker.opus.core.notifications.advice
import dev.sebastiano.clockblocker.opus.core.notifications.planOf
import dev.sebastiano.clockblocker.opus.core.notifications.utc
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

class NowStateCalculatorTest {

    private val avoid = advice(AvoidLight, "2026-10-10T14:00", "2026-10-10T18:00")
    private val sleep = advice(Sleep, "2026-10-10T18:00", "2026-10-11T02:00")
    private val plan = planOf(avoid, sleep)

    @Test
    fun `headline with its own end and what comes next`() {
        val state = NowStateCalculator.compute(plan, utc("2026-10-10T15:00")).shouldNotBeNull()

        state.headline shouldBe avoid
        state.until shouldBe utc("2026-10-10T18:00")
        state.next shouldBe sleep
        state.alongside.shouldBeEmpty()
    }

    @Test
    fun `sleep wins over overlapping windows`() {
        val overlapping = planOf(
            advice(AvoidLight, "2026-10-10T20:00", "2026-10-11T01:00"),
            advice(Sleep, "2026-10-10T22:00", "2026-10-11T06:00"),
        )

        NowStateCalculator.headline(overlapping, utc("2026-10-10T23:00"))!!.type shouldBe Sleep
    }

    /** Issue #43: the app and the widgets say "Avoid caffeine until 20:00"; so must the notification. */
    @Nested
    inner class OverlappingBlocks {
        private val noCaffeine = advice(AvoidCaffeine, "2026-10-10T12:00", "2026-10-10T20:00")
        private val avoidLight = advice(AvoidLight, "2026-10-10T17:00", "2026-10-10T20:00")
        private val bed = advice(Sleep, "2026-10-10T21:00", "2026-10-11T05:00")
        private val evening = planOf(noCaffeine, avoidLight, bed)

        @Test
        fun `until is the headline's own end, even when a higher-priority block starts first`() {
            val state = NowStateCalculator.compute(evening, utc("2026-10-10T15:00")).shouldNotBeNull()

            state.headline shouldBe noCaffeine
            state.until shouldBe utc("2026-10-10T20:00")
            state.next shouldBe avoidLight
            state.alongside.shouldBeEmpty()
        }

        @Test
        fun `once the higher-priority block is the headline, the one still running is listed alongside`() {
            val state = NowStateCalculator.compute(evening, utc("2026-10-10T17:30")).shouldNotBeNull()

            state.headline shouldBe avoidLight
            state.until shouldBe utc("2026-10-10T20:00")
            state.alongside shouldContainExactly listOf(noCaffeine)
            state.next shouldBe bed
        }
    }

    @Test
    fun `a higher-priority window starting later is next, without cutting the headline's until short`() {
        val overlapping = planOf(
            advice(AvoidLight, "2026-10-10T20:00", "2026-10-11T01:00"),
            advice(Sleep, "2026-10-10T22:00", "2026-10-11T06:00"),
        )

        val state = NowStateCalculator.compute(overlapping, utc("2026-10-10T21:00")).shouldNotBeNull()

        state.headline!!.type shouldBe AvoidLight
        state.until shouldBe utc("2026-10-11T01:00")
        state.next!!.type shouldBe Sleep
    }

    @Test
    fun `a lower-priority window already running is alongside, not next`() {
        val overlapping = planOf(
            advice(SeeBrightLight, "2026-10-10T08:00", "2026-10-10T10:00"),
            advice(Caffeine, "2026-10-10T09:00", "2026-10-10T12:00"),
        )

        val state = NowStateCalculator.compute(overlapping, utc("2026-10-10T09:30")).shouldNotBeNull()

        state.headline!!.type shouldBe SeeBrightLight
        state.alongside shouldContainExactly listOf(overlapping.allAdvice[1])
        state.next.shouldBeNull()
    }

    @Test
    fun `simultaneous starts - next is the one that will be the headline`() {
        val simultaneous = planOf(
            avoid,
            advice(Caffeine, "2026-10-10T18:00", "2026-10-10T20:00"),
            sleep,
        )

        NowStateCalculator.compute(simultaneous, utc("2026-10-10T15:00"))!!.next shouldBe sleep
    }

    @Test
    fun `the flight marker is never listed alongside`() {
        val travel = planOf(
            advice(Flight, "2026-10-10T12:00", "2026-10-10T23:00", detail = "BA7"),
            advice(AvoidLight, "2026-10-10T15:00", "2026-10-10T18:00"),
        )

        NowStateCalculator.compute(travel, utc("2026-10-10T16:00"))!!.alongside.shouldBeEmpty()
    }

    @Test
    fun `flight is the headline only when nothing else is active`() {
        val travel = planOf(
            advice(Flight, "2026-10-10T12:00", "2026-10-10T23:00", detail = "BA7"),
            advice(AvoidLight, "2026-10-10T15:00", "2026-10-10T18:00"),
        )

        NowStateCalculator.headline(travel, utc("2026-10-10T13:00"))!!.type shouldBe Flight
        NowStateCalculator.headline(travel, utc("2026-10-10T16:00"))!!.type shouldBe AvoidLight
    }

    @Test
    fun `melatonin moments are never the headline`() {
        val withMelatonin = planOf(advice(Melatonin, "2026-10-10T15:00"), avoid)

        NowStateCalculator.headline(withMelatonin, utc("2026-10-10T15:00")) shouldBe avoid
    }

    @Test
    fun `in a gap the next window is named`() {
        val gappy = planOf(
            advice(SeeBrightLight, "2026-10-10T08:00", "2026-10-10T10:00"),
            sleep,
        )

        val state = NowStateCalculator.compute(gappy, utc("2026-10-10T12:00")).shouldNotBeNull()

        state.headline.shouldBeNull()
        state.until.shouldBeNull()
        state.next shouldBe sleep
    }

    @Test
    fun `when the headline ends into a gap, then names what comes after the gap`() {
        val gappy = planOf(advice(SeeBrightLight, "2026-10-10T08:00", "2026-10-10T10:00"), sleep)

        val state = NowStateCalculator.compute(gappy, utc("2026-10-10T09:00")).shouldNotBeNull()

        state.until shouldBe utc("2026-10-10T10:00")
        state.next shouldBe sleep
    }

    @Test
    fun `not in progress before the plan starts or after it ends`() {
        NowStateCalculator.compute(plan, utc("2026-10-10T13:00")).shouldBeNull()
        NowStateCalculator.compute(plan, utc("2026-10-10T13:50"), lead = Duration.ofMinutes(15)).shouldNotBeNull()
        NowStateCalculator.compute(plan, utc("2026-10-11T02:00")).shouldBeNull()
    }

    @Test
    fun `logged outcome for the headline is reported`() {
        val logs = listOf(AdviceLog("other", AdviceOutcome.Done), AdviceLog(avoid.id, AdviceOutcome.CantDo))

        NowStateCalculator.compute(plan, utc("2026-10-10T15:00"), logs)!!.outcome shouldBe AdviceOutcome.CantDo
    }

    @Test
    fun `last window has no next`() {
        NowStateCalculator.compute(plan, utc("2026-10-10T20:00"))!!.next.shouldBeNull()
    }
}
