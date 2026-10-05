package dev.sebastiano.clockblocker.opus.core.circadian.ode

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test

/** §4.6 model reference fixtures: 60 days from the published IC, RK4, integer step counter. */
class CircadianModelTest {

    private fun schedule(wakeLux: Double, lunch: Boolean): (Double) -> Double = { t ->
        val h = ((t % 24.0) + 24.0) % 24.0
        when {
            h >= 23.0 || h < 7.0 -> 0.0
            lunch && h >= 12.0 && h < 14.0 -> 3000.0
            else -> wakeLux
        }
    }

    private fun entrainedCbtMin(model: CircadianModel, lux: (Double) -> Double, dt: Double = 0.1): Double {
        val steps = Math.round(60 * 24 / dt).toInt()
        val trajectory = Rk4.simulate(model, model.initialState, t0 = 0.0, steps = steps, dt = dt, lux = lux)
        val mins = model.cbtMinTimes(trajectory)
        return ((mins.last() % 24.0) + 24.0) % 24.0
    }

    @Test
    fun `Forger99 entrains to 04_14 on the typical day`() {
        entrainedCbtMin(Forger99, schedule(250.0, lunch = true)) shouldBe (4.237 plusOrMinus 0.01)
    }

    @Test
    fun `Hannay19 entrains to 04_00 on the typical day`() {
        entrainedCbtMin(Hannay19, schedule(250.0, lunch = true)) shouldBe (3.997 plusOrMinus 0.01)
    }

    @ParameterizedTest
    @ValueSource(doubles = [0.25, 0.2, 0.05, 0.01])
    fun `integer stepping gives the same entrained phase at every documented step size`(dt: Double) {
        entrainedCbtMin(Forger99, schedule(250.0, lunch = true), dt) shouldBe (4.237 plusOrMinus 0.05)
        entrainedCbtMin(Hannay19, schedule(250.0, lunch = true), dt) shouldBe (3.997 plusOrMinus 0.05)
    }

    @Test
    fun `constant-light rows match the report within 0_15 h`() {
        val f99 = mapOf(100.0 to 4.73, 250.0 to 4.56, 500.0 to 4.48, 1000.0 to 4.42)
        val h19 = mapOf(100.0 to 4.17, 250.0 to 4.48, 500.0 to 4.71, 1000.0 to 4.83)
        f99.forEach { (lux, expected) -> entrainedCbtMin(Forger99, schedule(lux, false)) shouldBe (expected plusOrMinus 0.15) }
        h19.forEach { (lux, expected) -> entrainedCbtMin(Hannay19, schedule(lux, false)) shouldBe (expected plusOrMinus 0.15) }
    }

    @Test
    fun `Forger99 stays finite at 100k lux with dt 0_25`() {
        val trajectory = Rk4.simulate(Forger99, Forger99.initialState, 0.0, steps = 4 * 24 * 10, dt = 0.25) { 100_000.0 }
        trajectory.states.forEach { s -> s.forEach { it.isFinite() shouldBe true } }
        kotlin.math.abs(trajectory.states.last()[0]) shouldBeLessThan 5.0
    }

    @Test
    fun `step times come from an integer counter`() {
        val trajectory = Rk4.simulate(Hannay19, Hannay19.initialState, t0 = 10.0, steps = 1000, dt = 0.1) { 0.0 }
        trajectory.times.last() shouldBe 10.0 + 1000 * 0.1
        trajectory.times[0] shouldBe 10.0
    }

    @Test
    fun `light drive is zero in darkness and saturates for Hannay19`() {
        Forger99.alpha(0.0) shouldBe 0.0
        Hannay19.alpha(0.0) shouldBe 0.0
        Hannay19.alpha(443.0) shouldBe (0.025 plusOrMinus 0.0015)
        Hannay19.alpha(1_000_000.0) shouldBe (0.05 plusOrMinus 0.001)
        Forger99.alpha(9500.0) shouldBe (0.05 plusOrMinus 1e-12)
        // clamped to 100k lux
        Forger99.alpha(1e9) shouldBe Forger99.alpha(100_000.0)
    }
}
