package dev.sebastiano.clockblocker.opus.core.circadian

import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import io.kotest.matchers.doubles.plusOrMinus
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
 * Onboarding asks one question, the ideal night's sleep on a day off, and works the chronotype out from it: MCTQ
 * mid-sleep on free days (MSF), with no sleep-debt correction (there is no work-day sleep to correct against).
 */
class FreeDayChronotypeTest {

    @Test
    fun `typical ideal nights land on the expected chronotype`() {
        fromNight(21, 0, 5, 0) shouldBe Chronotype.DefiniteMorning // MSF 01:00
        fromNight(22, 0, 6, 0) shouldBe Chronotype.ModerateMorning // MSF 02:00
        fromNight(23, 0, 7, 0) shouldBe Chronotype.Intermediate // MSF 03:00 (the app's default night)
        fromNight(0, 0, 8, 0) shouldBe Chronotype.Intermediate // MSF 04:00
        fromNight(1, 30, 9, 30) shouldBe Chronotype.ModerateEvening // MSF 05:30
        fromNight(3, 0, 11, 0) shouldBe Chronotype.DefiniteEvening // MSF 07:00
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
    fun `a free-day night maps through its uncorrected mid-sleep`() = runTest {
        checkAll(Arb.int(0 until 24 * 60), Arb.int(60..(22 * 60))) { onsetMinute, durationMinutes ->
            val onset = LocalTime.of(onsetMinute / 60, onsetMinute % 60)
            val hours = durationMinutes / 60.0
            val msf = Chronotypes.mctqMidSleepFreeDays(onset, hours)
            msf shouldBe (CircadianMath.mod24(onsetMinute / 60.0 + hours / 2) plusOrMinus 1e-9)
            Chronotypes.fromFreeDaySleep(onset, hours) shouldBe Chronotypes.fromMidSleep(msf)
        }
    }

    private fun fromNight(bedH: Int, bedM: Int, wakeH: Int, wakeM: Int): Chronotype {
        val bed = LocalTime.of(bedH, bedM)
        val minutes = ((wakeH * 60 + wakeM) - (bedH * 60 + bedM)).mod(24 * 60)
        return Chronotypes.fromFreeDaySleep(bed, minutes / 60.0)
    }
}
