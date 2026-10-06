package dev.sebastiano.clockblocker.opus.core.circadian

import dev.sebastiano.clockblocker.opus.core.circadian.CircadianMath.norm12
import dev.sebastiano.clockblocker.opus.core.circadian.engine.CyclePlanner
import dev.sebastiano.clockblocker.opus.core.circadian.engine.Direction
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filter
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalTime

/** §14.5 pure-logic fixtures (no ODE). Planner-level cases (NoPlan, HomeTimePlan) live in the planner tests. */
class PureLogicTest {

    @Test
    fun `norm12 handles the date line, JST to EST and quarter-hour zones`() {
        norm12(14.0 - (-10.0)) shouldBe 0.0
        norm12(-5.0 - 9.0) shouldBe 10.0
        norm12(5.75 - 0.0) shouldBe 5.75
        norm12(12.0) shouldBe 12.0
        norm12(-12.0) shouldBe 12.0
    }

    @Test
    fun `norm12 always lands in the half-open range and preserves the value modulo 24`() = runTest {
        checkAll(Arb.double(-1000.0, 1000.0).filter { it.isFinite() }) { x ->
            val y = norm12(x)
            (y > -12.0 && y <= 12.0) shouldBe true
            CircadianMath.mod24(y - x).let { minOf(it, 24.0 - it) } shouldBe (0.0 plusOrMinus 1e-9)
        }
    }

    @Test
    fun `late chronotype with habitual 00_30-08_30 has CBTmin at 06_00`() {
        CyclePlanner.cbtMinClock(habitualOnset = 0.5, habitualWake = 8.5, offset = PlannerConfig().cbtFromMidSleep(ChronotypeClass.Late)) shouldBe 6.0
        CyclePlanner.cbtMinClock(23.0, 7.0, PlannerConfig().cbtFromMidSleep(ChronotypeClass.Neutral)) shouldBe 4.0
        CyclePlanner.cbtMinClock(23.0, 7.0, PlannerConfig().cbtFromMidSleep(ChronotypeClass.Early)) shouldBe 3.5
    }

    @Test
    fun `MCTQ sleep-debt corrected mid-sleep`() {
        val msf = Chronotypes.mctqMidSleepFreeDays(sleepOnsetFree = LocalTime.of(1, 0), sleepDurationFreeHours = 9.0)
        msf shouldBe (5.5 plusOrMinus 1e-9)
        val sc = Chronotypes.mctqMsfSc(sleepOnsetFree = LocalTime.of(1, 0), sleepDurationFreeHours = 9.0, sleepDurationWorkHours = 7.0)
        sc shouldBe ((5.5 - (9.0 - 53.0 / 7.0) / 2) plusOrMinus 1e-9)
        Math.round(sc * 60) shouldBe 4 * 60 + 47L
        Chronotypes.fromMsfSc(sc) shouldBe ChronotypeClass.Neutral
        Chronotypes.fromMsfSc(2.5) shouldBe ChronotypeClass.Early
        Chronotypes.fromMsfSc(18.0) shouldBe ChronotypeClass.Early
        Chronotypes.fromMsfSc(23.0) shouldBe ChronotypeClass.Early
        Chronotypes.fromMsfSc(5.5) shouldBe ChronotypeClass.Late
    }

    @Test
    fun `MEQ categories`() {
        Chronotypes.fromMeq(60) shouldBe Chronotype.ModerateMorning
        Chronotypes.fromMeq(60).toClass() shouldBe ChronotypeClass.Early
        Chronotypes.fromMeq(75) shouldBe Chronotype.DefiniteMorning
        Chronotypes.fromMeq(50) shouldBe Chronotype.Intermediate
        Chronotypes.fromMeq(35) shouldBe Chronotype.ModerateEvening
        Chronotypes.fromMeq(20) shouldBe Chronotype.DefiniteEvening
    }

    @Test
    fun `five-level chronotype maps to early, neutral and late`() {
        Chronotype.DefiniteMorning.toClass() shouldBe ChronotypeClass.Early
        Chronotype.ModerateMorning.toClass() shouldBe ChronotypeClass.Early
        Chronotype.Intermediate.toClass() shouldBe ChronotypeClass.Neutral
        Chronotype.ModerateEvening.toClass() shouldBe ChronotypeClass.Late
        Chronotype.DefiniteEvening.toClass() shouldBe ChronotypeClass.Late
    }

    @Test
    fun `advance-or-delay threshold`() {
        val p = CyclePlanner(PlannerConfig())
        p.defaultDirection(eastwardHours = 9.0, chronotype = ChronotypeClass.Neutral) shouldBe Direction.Advance
        p.defaultDirection(9.5, ChronotypeClass.Neutral) shouldBe Direction.Delay
        p.defaultDirection(9.5, ChronotypeClass.Early) shouldBe Direction.Advance
        p.defaultDirection(8.5, ChronotypeClass.Late) shouldBe Direction.Delay
        p.defaultDirection(8.0, ChronotypeClass.Late) shouldBe Direction.Advance
        p.defaultDirection(21.0, ChronotypeClass.Neutral) shouldBe Direction.Delay
    }
}
