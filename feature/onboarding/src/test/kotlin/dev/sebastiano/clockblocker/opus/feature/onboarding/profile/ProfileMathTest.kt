package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import dev.sebastiano.clockblocker.opus.core.circadian.Chronotypes
import dev.sebastiano.clockblocker.opus.core.circadian.toClass
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalTime

class SleepDialMathTest {
    private val default = SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 0))

    @Test
    fun `snaps to five minutes and wraps the day`() {
        SleepDialMath.snap(62.4f) shouldBe 60
        SleepDialMath.snap(63f) shouldBe 65
        SleepDialMath.snap(1438f) shouldBe 0
        SleepDialMath.snap(-3f) shouldBe 1435
    }

    @Test
    fun `dragging the wake handle moves only wake`() {
        val drag = SleepDrag(SleepHandle.Wake, default)
        drag.moveBy(30f) shouldBe SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 30))
    }

    @Test
    fun `dragging the bedtime handle moves only bedtime, across midnight`() {
        val drag = SleepDrag(SleepHandle.Bedtime, default)
        drag.moveBy(90f) shouldBe SleepWindow(LocalTime.of(0, 30), LocalTime.of(7, 0))
    }

    @Test
    fun `dragging the arc slides the whole window`() {
        val drag = SleepDrag(SleepHandle.Both, default)
        drag.moveBy(-60f) shouldBe SleepWindow(LocalTime.of(22, 0), LocalTime.of(6, 0))
    }

    @Test
    fun `duration never drops below one hour`() {
        val drag = SleepDrag(SleepHandle.Wake, default)
        drag.moveBy(-10 * 60f)
        SleepDialMath.durationMinutes(drag.window) shouldBe 60
        val bed = SleepDrag(SleepHandle.Bedtime, default)
        bed.moveBy(10 * 60f)
        bed.window shouldBe SleepWindow(LocalTime.of(6, 0), LocalTime.of(7, 0))
    }

    @Test
    fun `wake cannot wrap past bedtime, it builds overshoot instead`() {
        val drag = SleepDrag(SleepHandle.Wake, default)
        drag.moveBy(16 * 60f + 20f) // 8 h + 16 h 20 m = 24 h 20 m
        SleepDialMath.durationMinutes(drag.window) shouldBe SleepDialMath.MaxDurationMinutes
        drag.overshootMinutes shouldBe 25f
        drag.eggTriggered.shouldBeFalse()
    }

    @Test
    fun `pushing past the end of the day triggers 24_2 once and stays triggered`() {
        val drag = SleepDrag(SleepHandle.Wake, default)
        drag.moveBy(16 * 60f + 60f)
        drag.eggTriggered.shouldBeTrue()
        drag.moveBy(-200f)
        drag.eggTriggered.shouldBeTrue()
    }

    @Test
    fun `bedtime handle never triggers the egg`() {
        val drag = SleepDrag(SleepHandle.Bedtime, default)
        drag.moveBy(-20 * 60f)
        drag.eggTriggered.shouldBeFalse()
        drag.overshootMinutes shouldBe 0f
    }

    @Test
    fun `nudge steps a handle by the accessibility step`() {
        SleepDialMath.nudge(default, SleepHandle.Bedtime, -15) shouldBe SleepWindow(LocalTime.of(22, 45), LocalTime.of(7, 0))
        SleepDialMath.nudge(default, SleepHandle.Wake, 15) shouldBe SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 15))
    }

    @Test
    fun `a picked time sets that end to the exact minute and keeps the other end`() {
        SleepDialMath.withTime(default, SleepHandle.Bedtime, LocalTime.of(22, 47)) shouldBe
            SleepWindow(LocalTime.of(22, 47), LocalTime.of(7, 0))
        SleepDialMath.withTime(default, SleepHandle.Wake, LocalTime.of(6, 13)) shouldBe
            SleepWindow(LocalTime.of(23, 0), LocalTime.of(6, 13))
        // Across midnight the other way: a 01:30 bedtime is a later night, not a 29 h one.
        SleepDialMath.withTime(default, SleepHandle.Bedtime, LocalTime.of(1, 30)) shouldBe
            SleepWindow(LocalTime.of(1, 30), LocalTime.of(7, 0))
    }

    @Test
    fun `a picked time too close to the other end keeps the pick and pushes the other end out to the shortest night`() {
        // Bedtime 06:30 with wake 07:00 would be 30 minutes: wake moves to 07:30.
        SleepDialMath.withTime(default, SleepHandle.Bedtime, LocalTime.of(6, 30)) shouldBe
            SleepWindow(LocalTime.of(6, 30), LocalTime.of(7, 30))
        // Wake 23:20 right after a 23:00 bedtime: bedtime moves back to 22:20.
        SleepDialMath.withTime(default, SleepHandle.Wake, LocalTime.of(23, 20)) shouldBe
            SleepWindow(LocalTime.of(22, 20), LocalTime.of(23, 20))
        // Bedtime picked at (or just after) wake time reads as the shortest night, not a full day.
        SleepDialMath.withTime(default, SleepHandle.Bedtime, LocalTime.of(7, 0)) shouldBe
            SleepWindow(LocalTime.of(7, 0), LocalTime.of(8, 0))
        SleepDialMath.withTime(default, SleepHandle.Wake, LocalTime.of(23, 2)) shouldBe
            SleepWindow(LocalTime.of(22, 2), LocalTime.of(23, 2))
    }

    @Test
    fun `any picked time keeps the window within limits and keeps the pick`() = runTest {
        checkAll(Arb.int(0, 287), Arb.int(12, 287), Arb.int(0, 1439)) { bedStep, durStep, picked ->
            val start = SleepWindow(SleepDialMath.timeOf(bedStep * 5), SleepDialMath.timeOf(bedStep * 5 + durStep * 5))
            val time = SleepDialMath.timeOf(picked)
            listOf(SleepHandle.Bedtime, SleepHandle.Wake).forEach { handle ->
                val window = SleepDialMath.withTime(start, handle, time)
                val d = SleepDialMath.durationMinutes(window)
                (d in SleepDialMath.MinDurationMinutes..SleepDialMath.MaxDurationMinutes).shouldBeTrue()
                (if (handle == SleepHandle.Bedtime) window.bedtime else window.wake) shouldBe time
            }
        }
    }

    @Test
    fun `picks the nearer handle, then the arc, then nothing`() {
        SleepDialMath.pick(23 * 60f + 10f, default, 40f) shouldBe SleepHandle.Bedtime
        SleepDialMath.pick(6 * 60f + 45f, default, 40f) shouldBe SleepHandle.Wake
        SleepDialMath.pick(3 * 60f, default, 40f) shouldBe SleepHandle.Both
        SleepDialMath.pick(15 * 60f, default, 40f) shouldBe null
    }

    @Test
    fun `haptic step crossings`() {
        SleepDialMath.crossedStep(0, 10).shouldBeFalse()
        SleepDialMath.crossedStep(10, 15).shouldBeTrue()
        SleepDialMath.crossedStep(30, 25).shouldBeTrue()
    }

    @Test
    fun `any sequence of drags keeps the window within limits`() = runTest {
        checkAll(Arb.int(0, 287), Arb.int(12, 287), Arb.list(Arb.int(-9000, 9000).map { it / 10f }, 1..12)) { bedStep, durStep, moves ->
            val start = SleepWindow(SleepDialMath.timeOf(bedStep * 5), SleepDialMath.timeOf(bedStep * 5 + durStep * 5))
            SleepHandle.entries.forEach { handle ->
                val drag = SleepDrag(handle, start)
                moves.forEach { drag.moveBy(it) }
                val d = SleepDialMath.durationMinutes(drag.window)
                (d in SleepDialMath.MinDurationMinutes..SleepDialMath.MaxDurationMinutes).shouldBeTrue()
                (drag.window.bedtime.minute % 5 == 0 && drag.window.wake.minute % 5 == 0).shouldBeTrue()
            }
        }
    }
}

class ChronotypeEstimateTest {
    private val work = SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 0))

    @Test
    fun `free-day mid-sleep places people in five bands`() {
        // 22:00–06:00 free: MSF 02:00 → moderate morning.
        ChronotypeEstimate.estimate(SleepWindow(LocalTime.of(22, 0), LocalTime.of(6, 0)), work).chronotype shouldBe
            Chronotype.ModerateMorning
        ChronotypeEstimate.estimate(SleepWindow(LocalTime.of(21, 0), LocalTime.of(5, 0)), work).chronotype shouldBe
            Chronotype.DefiniteMorning
        ChronotypeEstimate.estimate(SleepWindow(LocalTime.of(0, 0), LocalTime.of(8, 0)), work).chronotype shouldBe
            Chronotype.Intermediate
        ChronotypeEstimate.estimate(SleepWindow(LocalTime.of(1, 30), LocalTime.of(9, 30)), work).chronotype shouldBe
            Chronotype.ModerateEvening
        ChronotypeEstimate.estimate(SleepWindow(LocalTime.of(3, 0), LocalTime.of(11, 0)), work).chronotype shouldBe
            Chronotype.DefiniteEvening
    }

    @Test
    fun `oversleep on free days is corrected`() {
        // Free 01:00–11:00 (10 h) vs 8 h on work days: MSF 06:00 corrected earlier to ~05:17 → moderate evening.
        val result = ChronotypeEstimate.estimate(SleepWindow(LocalTime.of(1, 0), LocalTime.of(11, 0)), work)
        result.chronotype shouldBe Chronotype.ModerateEvening
        result.midSleep shouldBe LocalTime.of(5, 17)
    }

    @Test
    fun `five bands agree with the planner's three classes`() = runTest {
        checkAll(Arb.int(0, 23_999).map { it / 1000.0 }) { h ->
            ChronotypeEstimate.fromMidSleepHours(h).toClass() shouldBe Chronotypes.fromMsfSc(h)
        }
    }
}
