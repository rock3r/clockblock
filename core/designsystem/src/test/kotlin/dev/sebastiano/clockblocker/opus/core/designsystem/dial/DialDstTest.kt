package dev.sebastiano.clockblocker.opus.core.designsystem.dial

import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DefaultDialLabels
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialOp
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.TwoSkies
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.focusAt
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PhasePoint
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The dial is a 24 h wall-clock face: across a DST change its arcs are drawn, and the hand scrubs, in wall-clock
 * minutes, while the instants it reports stay real (#66). New York, 2026: clocks spring forward on 8 March at 02:00
 * EST (07:00Z) and fall back on 1 November at 02:00 EDT (06:00Z).
 */
class DialDstTest {

    private val newYork = ZoneId.of("America/New_York")

    private fun advice(id: String, type: AdviceType, start: String, end: String) =
        Advice(id, type, Instant.parse(start), Instant.parse(end), AdviceReason.LightAdvancesClock)

    private fun plan(date: LocalDate, vararg advice: Advice): JetLagPlan {
        val at = advice.first().start
        return JetLagPlan(
            tripId = "t",
            generatedAt = at,
            strategy = AdaptationStrategy.entries.first(),
            direction = ShiftDirection.Advance,
            shiftHours = 6.0,
            originZoneId = "Europe/Lisbon",
            destinationZoneId = "America/New_York",
            days = listOf(PlanDay(1, DayKind.Arrival, date, "America/New_York", advice.toList())),
            phase = listOf(PhasePoint(at, -240, at)),
            estimatedDaysToAdapt = 3.0,
            estimatedDaysWithoutPlan = 6.0,
        )
    }

    // region Spring forward: 01:00 EST → 04:00 EDT is 2 h of real time and 3 h of the face.

    private val springNow = Instant.parse("2026-03-08T05:30:00Z") // 00:30 EST
    private val spring = plan(
        LocalDate.of(2026, 3, 8),
        advice("sleep", AdviceType.Sleep, "2026-03-08T06:00:00Z", "2026-03-08T08:00:00Z"),
        // 16:30–17:30 EDT: 16 h of the face after now, so outside the window (though only 15 h of real time ahead).
        advice("late", AdviceType.SeeBrightLight, "2026-03-08T20:30:00Z", "2026-03-08T21:30:00Z"),
    ).toDialState(springNow, newYork)

    @Test
    fun `a block across spring forward is drawn with its wall-clock length`() {
        val sleep = spring.arcs.single { it.adviceId == "sleep" }

        sleep.startMinute shouldBe (60f plusOrMinus 0.01f)
        sleep.sweepMinutes shouldBe (180f plusOrMinus 0.01f)
        sleep.endMinute shouldBe (240f plusOrMinus 0.01f)
    }

    @Test
    fun `a block across spring forward is current until it ends on the face`() {
        spring.focusAt(3 * 60f + 30f).current?.adviceId shouldBe "sleep"
    }

    @Test
    fun `the window is 24 h of the face, so a wall-clock day never overlaps itself`() {
        spring.arcs.map { it.adviceId } shouldBe listOf("sleep")
    }

    @Test
    fun `scrubbing across spring forward reports the real instant under the hand`() {
        blockBoundaries(spring) shouldBe listOf(30f, 210f)
        spring.instantAt(210f) shouldBe Instant.parse("2026-03-08T08:00:00Z")
        spring.scrubbedTo(210f).instant shouldBe Instant.parse("2026-03-08T08:00:00Z")
        spring.scrubbedTo(210f).localTime shouldBe LocalTime.of(4, 0)
    }

    @Test
    fun `the skipped hour reports the change itself`() {
        spring.instantAt(120f) shouldBe Instant.parse("2026-03-08T07:00:00Z") // 02:30 never happens
    }

    @Test
    fun `an arc clipped where the window ends inside the skipped hour stops at the window's face end`() {
        // From 10:30 EST the day before, the window ends 16 h of the face later, at 02:30: a time that never happens.
        val state = plan(
            LocalDate.of(2026, 3, 7),
            advice("sleep", AdviceType.Sleep, "2026-03-08T03:00:00Z", "2026-03-08T10:00:00Z"), // 22:00 EST → 06:00 EDT
        ).toDialState(Instant.parse("2026-03-07T15:30:00Z"), newYork)

        val sleep = state.arcs.single()
        sleep.startMinute shouldBe (22 * 60f plusOrMinus 0.01f)
        sleep.sweepMinutes shouldBe (270f plusOrMinus 0.01f) // to 02:30, never into the past sector
    }

    @Test
    fun `an arc clipped where the window starts inside the skipped hour starts at the window's face start`() {
        // From 10:30 EDT after the change, the window starts 8 h of the face earlier, at 02:30: a time that never happens.
        val state = plan(
            LocalDate.of(2026, 3, 8),
            advice("sleep", AdviceType.Sleep, "2026-03-08T06:00:00Z", "2026-03-08T08:00:00Z"), // 01:00 EST → 04:00 EDT
        ).toDialState(Instant.parse("2026-03-08T14:30:00Z"), newYork)

        val sleep = state.arcs.single()
        sleep.startMinute shouldBe (150f plusOrMinus 0.01f) // 02:30
        sleep.sweepMinutes shouldBe (90f plusOrMinus 0.01f) // to 04:00
    }

    @Test
    fun `the body clock follows real time when the hand crosses the change`() {
        // The body runs on UTC−4: 01:30 at 00:30 EST, and 04:00 at 04:00 EDT, 2½ h of real time later.
        spring.bodyTime shouldBe LocalTime.of(1, 30)
        spring.scrubbedTo(210f).bodyTime shouldBe LocalTime.of(4, 0)
        // Inside the skipped hour the hand reports the change itself (07:00Z), and the body reads 03:00 then.
        spring.scrubbedTo(120f).bodyTime shouldBe LocalTime.of(3, 0)
        val texts = TwoSkies.spec(spring, DialPalettes.Light, DefaultDialLabels(is24Hour = true), 328f, 328f, scrubMinutes = 210f)
            .ops.filterIsInstance<DialOp.Text>().map { it.text }
        texts shouldContain "04:00 body"
    }

    @Test
    fun `a body offset pushed past 12 h by the change wraps round`() {
        // 11½ h behind at 00:30 EST; the clocks jump an hour, so at 04:00 EDT the body is 11½ h ahead, not 12½ h behind.
        val labels = DefaultDialLabels(is24Hour = true)
        val texts = TwoSkies.spec(spring.copy(bodyAheadMinutes = -690f), DialPalettes.Light, labels, 328f, 328f, scrubMinutes = 210f)
            .ops.filterIsInstance<DialOp.Text>().map { it.text }
        texts shouldContain labels.offset(690f)
    }

    // endregion

    // region Fall back: 00:00 EDT → 03:00 EST is 4 h of real time and 3 h of the face.

    private val fallNow = Instant.parse("2026-11-01T04:30:00Z") // 00:30 EDT
    private fun fall(now: Instant = fallNow) = plan(
        LocalDate.of(2026, 11, 1),
        advice("sleep", AdviceType.Sleep, "2026-11-01T04:00:00Z", "2026-11-01T08:00:00Z"),
        // 01:50 EDT → 01:10 EST: ends "before" it starts on the face; drawn with its real 20 minutes instead.
        advice("light", AdviceType.AvoidLight, "2026-11-01T05:50:00Z", "2026-11-01T06:10:00Z"),
    ).toDialState(now, newYork)

    @Test
    fun `a block across fall back is drawn with its wall-clock length`() {
        val sleep = fall().arcs.single { it.adviceId == "sleep" }

        sleep.startMinute shouldBe (0f plusOrMinus 0.01f)
        sleep.sweepMinutes shouldBe (180f plusOrMinus 0.01f)
        sleep.endMinute shouldBe (180f plusOrMinus 0.01f)
    }

    @Test
    fun `a block across fall back stops being current where it ends on the face`() {
        fall().focusAt(3 * 60f + 30f).current.shouldBeNull()
    }

    @Test
    fun `a block that ends earlier on the face than it starts keeps its real length`() {
        fall().arcs.single { it.adviceId == "light" }.sweepMinutes shouldBe (20f plusOrMinus 0.01f)
    }

    @Test
    fun `scrubbing across fall back reports the real instant under the hand`() {
        val state = fall()
        blockBoundaries(state).first() shouldBe (-30f plusOrMinus 0.01f)
        blockBoundaries(state).last() shouldBe (150f plusOrMinus 0.01f)
        state.instantAt(150f) shouldBe Instant.parse("2026-11-01T08:00:00Z") // 03:00 EST, where sleep ends
        state.instantAt(60f) shouldBe Instant.parse("2026-11-01T05:30:00Z") // 01:30 the first time (EDT)
    }

    @Test
    fun `inside the repeated hour the hand stays on the side of the change it is on`() {
        val state = fall(Instant.parse("2026-11-01T06:30:00Z")) // 01:30 EST, the second time
        state.instantAt(10f) shouldBe Instant.parse("2026-11-01T06:40:00Z")
    }

    // endregion
}
