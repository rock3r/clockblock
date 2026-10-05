package dev.sebastiano.clockblocker.opus.core.circadian.engine

import dev.sebastiano.clockblocker.opus.core.circadian.ChronotypeClass
import dev.sebastiano.clockblocker.opus.core.circadian.PlannerConfig
import dev.sebastiano.clockblocker.opus.core.circadian.ode.CircadianModel
import dev.sebastiano.clockblocker.opus.core.circadian.ode.Forger99
import dev.sebastiano.clockblocker.opus.core.circadian.ode.Hannay19
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.math.abs

/**
 * §13 validation of the §14 fixtures. Expected values: `docs/science.md` §14 tables (±1 day, assertion c) and,
 * for single-leg itineraries, the reference `validate.py` re-run with an integer step counter (±0.05 day).
 */
class PlanValidatorTest {

    internal data class Case(
        val name: String,
        val home: Double,
        val legs: List<LegHours>,
        val chrono: ChronotypeClass,
        val model: CircadianModel,
        val plan: Double?,
        val noPlan: Double?,
        val exact: Boolean,
        val net: Double,
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    internal fun `days to adapt match the fixtures`(case: Case) {
        val planner = CyclePlanner(PlannerConfig(blockLayoverSleep = false))
        val seg = planner.plan(SegmentInput(case.home, case.legs, 23.0, 7.0, case.chrono, 3, useMelatonin = true))
        val itinerary = Itinerary(case.home, case.legs)
        val result = PlanValidator(case.model, 23.0, 7.0).validate(itinerary, LightPlan.of(seg.cycles), daysAfter = 14)

        val tol = if (case.exact) 0.05 else 1.0
        if (case.plan == null) result.plan.adaptDays.shouldBeNull() else result.plan.adaptDays.shouldNotBeNull() shouldBe (case.plan plusOrMinus tol)
        if (case.noPlan == null) result.noPlan.adaptDays.shouldBeNull() else result.noPlan.adaptDays.shouldNotBeNull() shouldBe (case.noPlan plusOrMinus tol)
        // (a) direction: net shift has the sign of the plan (earlier = negative on the home clock for advances)
        result.plan.netShift shouldBe (case.net plusOrMinus 1.0)
        if (seg.direction == Direction.Advance) result.plan.netShift shouldBeLessThan 0.0 else result.plan.netShift shouldBeGreaterThan 0.0
        abs(abs(result.plan.netShift) - abs(seg.targetPhi)) shouldBeLessThan 1.0
    }

    companion object {
        private val sfoLhr = -7.0 to listOf(LegHours(23.5, 33.75, -7.0, 1.0))
        private val lhrSyd = 0.0 to listOf(LegHours(21.0, 33.5, 0.0, 8.0), LegHours(36.0, 43.5, 8.0, 11.0))
        private val jfkLax = -4.0 to listOf(LegHours(12.0, 18.33, -4.0, -7.0))
        private val nrtJfk = 9.0 to listOf(LegHours(2.0, 15.5, 9.0, -5.0))

        private fun c(n: String, it: Pair<Double, List<LegHours>>, ch: ChronotypeClass, m: CircadianModel, p: Double?, np: Double?, exact: Boolean, net: Double) =
            Arguments.of(Case("$n $ch $m", it.first, it.second, ch, m, p, np, exact, net))

        @JvmStatic
        fun cases(): List<Arguments> {
            val n = ChronotypeClass.Neutral
            val e = ChronotypeClass.Early
            val l = ChronotypeClass.Late
            return listOf(
                // 14.1 SFO-LHR
                c("SFO-LHR", sfoLhr, n, Forger99, 4.766, 6.768, true, -7.95),
                c("SFO-LHR", sfoLhr, n, Hannay19, 2.747, 5.756, true, -7.99),
                c("SFO-LHR", sfoLhr, e, Hannay19, 2.734, 5.756, true, -7.99),
                c("SFO-LHR", sfoLhr, l, Hannay19, 3.743, 5.756, true, -7.98),
                c("SFO-LHR", sfoLhr, l, Forger99, 4.766, 6.768, true, -7.95),
                // 14.2 LHR-SIN-SYD (report values; the app treats the SIN layover as on the ground)
                c("LHR-SYD", lhrSyd, n, Forger99, 10.9, null, false, 12.7),
                c("LHR-SYD", lhrSyd, n, Hannay19, 5.9, 10.9, false, 13.0),
                c("LHR-SYD", lhrSyd, e, Forger99, 9.9, null, false, 12.7),
                // 14.3 JFK-LAX
                c("JFK-LAX", jfkLax, n, Forger99, 3.678, 4.669, true, 2.97),
                c("JFK-LAX", jfkLax, n, Hannay19, 1.658, 3.662, true, 3.01),
                // 14.4 NRT-JFK
                c("NRT-JFK", nrtJfk, n, Forger99, 11.706, null, true, 13.57),
                c("NRT-JFK", nrtJfk, n, Hannay19, 7.694, 5.763, true, 13.99),
                c("NRT-JFK", nrtJfk, e, Forger99, 5.766, null, true, -9.95),
                c("NRT-JFK", nrtJfk, e, Hannay19, 3.763, 5.763, true, -10.02),
            )
        }
    }
}
