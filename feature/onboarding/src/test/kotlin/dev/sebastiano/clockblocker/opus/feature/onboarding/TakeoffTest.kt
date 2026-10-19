package dev.sebastiano.clockblocker.opus.feature.onboarding

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Onboarding's finish: the plane takes off while the content leaves, bottom first, all gone by the end. */
class TakeoffTest {
    @Test
    fun `everything is in place at the start and gone at the end`() {
        (Takeoff.BottomBar..Takeoff.TopBar).forEach { slot ->
            Takeoff.exit(slot, 0f) shouldBe 0f
            Takeoff.exit(slot, 1f) shouldBe 1f
        }
        Takeoff.alpha(0f) shouldBe 1f
        Takeoff.alpha(1f) shouldBe 0f
    }

    @Test
    fun `the content leaves bottom first`() {
        val p = 0.3f
        Takeoff.exit(Takeoff.BottomBar, p) shouldBeGreaterThan Takeoff.exit(Takeoff.Body, p)
        Takeoff.exit(Takeoff.Body, p) shouldBeGreaterThan Takeoff.exit(Takeoff.TopBar, p)
    }

    @Test
    fun `the plane rolls from its holding point past the end of the runway, rotates, then climbs`() {
        Takeoff.roll(0f) shouldBe 0f
        Takeoff.roll(1f) shouldBeGreaterThan 1f
        // Accelerating: it covers more ground in the second half than in the first.
        (Takeoff.roll(1f) - Takeoff.roll(0.5f)) shouldBeGreaterThan Takeoff.roll(0.5f)
        // Still on the ground while it starts rotating; off it once rotated.
        Takeoff.climb(0.4f) shouldBe 0f
        Takeoff.pitch(0.4f) shouldBeGreaterThan 0f
        Takeoff.pitch(1f) shouldBe (12f plusOrMinus 0.01f)
        Takeoff.climb(0.9f) shouldBeGreaterThan 0f
        Takeoff.alpha(0.6f) shouldBe 1f
        Takeoff.alpha(0.9f) shouldBeLessThan 1f
    }
}
