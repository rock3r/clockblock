package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.float
import io.kotest.property.checkAll
import kotlin.math.abs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The sun ⇄ moon swap when another chronotype is picked: a cross-fade with a small pop and a wobble. */
class BodySwapTest {
    @Test
    fun `the swap starts and ends at rest, exactly`() {
        BodySwap.moonFraction(from = 0f, to = 1f, progress = 0f) shouldBe 0f
        BodySwap.moonFraction(from = 0f, to = 1f, progress = 1f) shouldBe 1f
        BodySwap.pop(0f) shouldBe 0f
        BodySwap.pop(1f) shouldBe (0f plusOrMinus 1e-6f)
        BodySwap.wobble(0f) shouldBe 0f
        BodySwap.wobble(1f) shouldBe (0f plusOrMinus 1e-4f)
    }

    @Test
    fun `halfway, the sun and the moon are both half there, at the top of the pop`() {
        BodySwap.moonFraction(from = 0f, to = 1f, progress = 0.5f) shouldBe (0.5f plusOrMinus 0.01f)
        BodySwap.moonFraction(from = 1f, to = 0f, progress = 0.5f) shouldBe (0.5f plusOrMinus 0.01f)
        BodySwap.pop(0.5f) shouldBe (1f plusOrMinus 1e-6f)
    }

    @Test
    fun `an interrupted swap reverses from where the cross-fade was`() {
        BodySwap.moonFraction(from = 0.3f, to = 0f, progress = 0f) shouldBe 0.3f
    }

    @Test
    fun `the wobble shakes both ways and settles`() = runTest {
        val early = (1..40).map { BodySwap.wobble(it / 100f) }
        early.max() shouldBeGreaterThan 0f
        early.min() shouldBeLessThan 0f
        checkAll(Arb.float(0f, 1f).filter { it.isFinite() }) { p ->
            abs(BodySwap.wobble(p)) shouldBeLessThan BodySwap.WobbleDegrees + 1e-3f
            BodySwap.pop(p) shouldBeGreaterThan -1e-6f
        }
        abs(BodySwap.wobble(0.95f)) shouldBeLessThan BodySwap.WobbleDegrees * 0.1f
    }
}
