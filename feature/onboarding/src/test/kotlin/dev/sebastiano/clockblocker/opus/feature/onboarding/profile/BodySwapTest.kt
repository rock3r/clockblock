package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.float
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The sun ⇄ moon swap when another chronotype is picked: a cross-fade with a small pop (and a haptic at its top). */
class BodySwapTest {
    @Test
    fun `the swap starts and ends at rest, exactly`() {
        BodySwap.moonFraction(from = 0f, to = 1f, progress = 0f) shouldBe 0f
        BodySwap.moonFraction(from = 0f, to = 1f, progress = 1f) shouldBe 1f
        BodySwap.pop(0f) shouldBe 0f
        BodySwap.pop(1f) shouldBe (0f plusOrMinus 1e-6f)
    }

    @Test
    fun `at the haptic, the sun and the moon are both half there, at the top of the pop`() {
        BodySwap.moonFraction(from = 0f, to = 1f, progress = BodySwap.PopPeak) shouldBe (0.5f plusOrMinus 0.01f)
        BodySwap.moonFraction(from = 1f, to = 0f, progress = BodySwap.PopPeak) shouldBe (0.5f plusOrMinus 0.01f)
        BodySwap.pop(BodySwap.PopPeak) shouldBe (1f plusOrMinus 1e-6f)
    }

    @Test
    fun `an interrupted swap reverses from where the cross-fade was`() {
        BodySwap.moonFraction(from = 0.3f, to = 0f, progress = 0f) shouldBe 0.3f
    }

    @Test
    fun `the pop only grows the body, and never past its top`() = runTest {
        checkAll(Arb.float(0f, 1f).filter { it.isFinite() }) { p ->
            BodySwap.pop(p) shouldBeGreaterThan -1e-6f
            BodySwap.pop(p) shouldBeLessThan 1f + 1e-6f
        }
    }
}
