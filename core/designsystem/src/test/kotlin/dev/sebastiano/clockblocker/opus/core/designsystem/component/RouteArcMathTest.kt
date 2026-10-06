package dev.sebastiano.clockblocker.opus.core.designsystem.component

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.float
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration

class RouteArcMathTest {

    @Test
    fun `longer flights draw taller arcs, within bounds`() {
        val short = RouteArcDefaults.apexForDuration(Duration.ofMinutes(55))
        val medium = RouteArcDefaults.apexForDuration(Duration.ofHours(6))
        val long = RouteArcDefaults.apexForDuration(Duration.ofHours(16))
        medium shouldBeGreaterThan short
        long shouldBeGreaterThan medium
        short shouldBe (RouteArcDefaults.MinApex plusOrMinus 0.05f)
        long shouldBe 1f
        RouteArcDefaults.apexForDuration(Duration.ZERO) shouldBe RouteArcDefaults.MinApex
        RouteArcDefaults.apexForDuration(Duration.ofHours(-3)) shouldBe RouteArcDefaults.MinApex
    }

    @Test
    fun `distance scales the arc the same way`() {
        RouteArcDefaults.apexForDistance(300.0) shouldBe (RouteArcDefaults.MinApex plusOrMinus 0.05f)
        RouteArcDefaults.apexForDistance(14_000.0) shouldBe 1f
        RouteArcDefaults.apexForDistance(9_000.0) shouldBeGreaterThan RouteArcDefaults.apexForDistance(2_000.0)
    }

    @Test
    fun `the quadratic control point puts the arc's peak exactly at the apex`() = runTest {
        checkAll(Arb.float(10f, 500f).filter { it.isFinite() }, Arb.float(0f, 200f).filter { it.isFinite() }) { base, apex ->
            val control = routeControlY(base, apex)
            // Quadratic Bezier at t = 0.5: (P0 + 2C + P2) / 4, with both ends on the baseline.
            val peak = (base + 2f * control + base) / 4f
            peak shouldBe ((base - apex) plusOrMinus 1e-3f)
        }
    }

    @Test
    fun `the plane never covers the endpoint markers`() {
        planeDistance(progress = 0f, length = 200f, inset = 10f) shouldBe 10f
        planeDistance(progress = 1f, length = 200f, inset = 10f) shouldBe 190f
        planeDistance(progress = 0.5f, length = 200f, inset = 10f) shouldBe 100f
        // Overshoot and tiny routes stay on the route.
        planeDistance(progress = 1.2f, length = 200f, inset = 10f) shouldBe 190f
        planeDistance(progress = 0.5f, length = 12f, inset = 10f) shouldBe 6f
    }
}
