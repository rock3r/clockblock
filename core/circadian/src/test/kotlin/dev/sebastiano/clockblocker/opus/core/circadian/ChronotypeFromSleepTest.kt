package dev.sebastiano.clockblocker.opus.core.circadian

import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalTime

/**
 * Onboarding asks for the usual sleep on work days and on days off, and works the chronotype out from them: MCTQ
 * mid-sleep on free days corrected for catch-up sleep (MSF_sc), mapped onto the app's five chronotypes.
 */
class ChronotypeFromSleepTest {

    @Test
    fun `typical schedules land on the expected chronotype`() {
        // Days off linked to work days: nothing to correct, plain mid-sleep.
        chronotype(work = 21 to 5, off = 21 to 5) shouldBe Chronotype.DefiniteMorning // 01:00
        chronotype(work = 22 to 6, off = 22 to 6) shouldBe Chronotype.ModerateMorning // 02:00
        chronotype(work = 23 to 7, off = 23 to 7) shouldBe Chronotype.Intermediate // 03:00, the default nights
        chronotype(work = 1 to 9, off = 2 to 10) shouldBe Chronotype.ModerateEvening // 06:00
        chronotype(work = 2 to 10, off = 3 to 11) shouldBe Chronotype.DefiniteEvening // 07:00
        // A lie-in on days off is mostly catch-up sleep: 01:00–10:00 after 23:00–07:00 weeks is 05:09, not 05:30.
        chronotype(work = 23 to 7, off = 1 to 10) shouldBe Chronotype.ModerateEvening
        chronotype(work = 23 to 7, off = 0 to 9) shouldBe Chronotype.Intermediate // 04:09
    }

    @Test
    fun `band edges match the planner's three classes`() {
        Chronotypes.fromMidSleep(1.99) shouldBe Chronotype.DefiniteMorning
        Chronotypes.fromMidSleep(2.0) shouldBe Chronotype.ModerateMorning
        Chronotypes.fromMidSleep(3.0) shouldBe Chronotype.Intermediate
        Chronotypes.fromMidSleep(5.0) shouldBe Chronotype.Intermediate
        Chronotypes.fromMidSleep(5.01) shouldBe Chronotype.ModerateEvening
        Chronotypes.fromMidSleep(6.0) shouldBe Chronotype.ModerateEvening
        Chronotypes.fromMidSleep(6.01) shouldBe Chronotype.DefiniteEvening
        // Evening mid-sleeps (very early sleepers) count as before midnight, not as the latest owls.
        Chronotypes.fromMidSleep(23.0) shouldBe Chronotype.DefiniteMorning
        Chronotypes.fromMidSleep(18.0) shouldBe Chronotype.DefiniteMorning
    }

    @Test
    fun `the five levels nest inside the planner's early, neutral and late classes`() = runTest {
        checkAll(Arb.double(-48.0, 48.0).filter { it.isFinite() }) { h ->
            Chronotypes.fromMidSleep(h).toClass() shouldBe Chronotypes.fromMsfSc(h)
        }
    }

    @Test
    fun `a later mid-sleep never gives an earlier chronotype`() = runTest {
        // On the [-6, 18) clock the bands are ordered; beyond it they wrap with the day.
        checkAll(Arb.double(-6.0, 17.999).filter { it.isFinite() }, Arb.double(-6.0, 17.999).filter { it.isFinite() }) { a, b ->
            val (early, late) = if (a <= b) a to b else b to a
            Chronotypes.fromMidSleep(early).ordinal shouldBeLessThanOrEqual Chronotypes.fromMidSleep(late).ordinal
        }
    }

    @Test
    fun `mid-sleep is periodic over the day`() = runTest {
        checkAll(Arb.double(0.0, 23.999).filter { it.isFinite() }, Arb.int(-3..3)) { h, days ->
            Chronotypes.fromMidSleep(h + 24.0 * days) shouldBe Chronotypes.fromMidSleep(h)
        }
    }

    @Test
    fun `with the same nights on work days and days off, the correction changes nothing`() = runTest {
        checkAll(Arb.int(0 until 24 * 60), Arb.int(60..(22 * 60))) { onsetMinute, durationMinutes ->
            val onset = LocalTime.of(onsetMinute / 60, onsetMinute % 60)
            val hours = durationMinutes / 60.0
            Chronotypes.mctqMsfSc(onset, hours, hours) shouldBe (Chronotypes.mctqMidSleepFreeDays(onset, hours) plusOrMinus 1e-9)
        }
    }

    @Test
    fun `catch-up sleep only ever moves mid-sleep earlier, by at most half the lie-in`() = runTest {
        checkAll(Arb.int(0 until 24 * 60), Arb.int(60..(16 * 60)), Arb.int(60..(16 * 60))) { onsetMinute, freeMinutes, workMinutes ->
            val onset = LocalTime.of(onsetMinute / 60, onsetMinute % 60)
            val free = freeMinutes / 60.0
            val work = workMinutes / 60.0
            val msf = Chronotypes.mctqMidSleepFreeDays(onset, free)
            val sc = Chronotypes.mctqMsfSc(onset, free, work)
            val earlier = CircadianMath.mod24(msf - sc + 12.0) - 12.0 // signed, in (-12, 12]
            earlier shouldBeGreaterThanOrEqual -1e-9
            earlier shouldBeLessThan maxOf(0.0, (free - work) / 2) + 1e-9
        }
    }

    private fun chronotype(work: Pair<Int, Int>, off: Pair<Int, Int>): Chronotype {
        fun hours(night: Pair<Int, Int>) = (night.second - night.first).mod(24).toDouble()
        return Chronotypes.fromMidSleep(Chronotypes.mctqMsfSc(LocalTime.of(off.first, 0), hours(off), hours(work)))
    }
}
