package dev.sebastiano.clockblocker.opus.feature.plan

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class KonamiCodeTest {

    @Test
    fun `swipes are classified by their dominant axis`() {
        classifySwipe(0f, -200f, 48f) shouldBe Swipe.Up
        classifySwipe(10f, 200f, 48f) shouldBe Swipe.Down
        classifySwipe(-200f, 30f, 48f) shouldBe Swipe.Left
        classifySwipe(200f, -60f, 48f) shouldBe Swipe.Right
    }

    @Test
    fun `taps and diagonals are not swipes`() {
        classifySwipe(10f, 20f, 48f).shouldBeNull()
        classifySwipe(150f, 140f, 48f).shouldBeNull()
        classifySwipe(-120f, 80f, 48f).shouldBeNull()
    }

    @Test
    fun `the full code is recognised once`() {
        val detector = KonamiDetector()
        val results = KonamiSequence.mapIndexed { i, swipe -> detector.onSwipe(swipe, i * 300L) }
        results.dropLast(1).none { it }.shouldBeTrue()
        results.last().shouldBeTrue()
        detector.progress shouldBe 0
    }

    @Test
    fun `a wrong swipe resets progress`() {
        val detector = KonamiDetector()
        detector.onSwipe(Swipe.Up, 0)
        detector.onSwipe(Swipe.Up, 100)
        detector.onSwipe(Swipe.Down, 200)
        detector.onSwipe(Swipe.Right, 300).shouldBeFalse()
        detector.progress shouldBe 0
    }

    @Test
    fun `an extra up still counts as the start of the code`() {
        val detector = KonamiDetector()
        var t = 0L
        detector.onSwipe(Swipe.Up, t)
        // ↑ ↑ ↑ ↓ ↓ ← → ← →: the third ↑ keeps the last two as a valid prefix.
        val fired = KonamiSequence.map { t += 200; detector.onSwipe(it, t) }
        fired.last().shouldBeTrue()
    }

    @Test
    fun `a wrong swipe that starts the code counts as its first step`() {
        val detector = KonamiDetector()
        detector.onSwipe(Swipe.Left, 0)
        detector.onSwipe(Swipe.Up, 100)
        detector.progress shouldBe 1
    }

    @Test
    fun `a long pause starts over`() {
        val detector = KonamiDetector(timeoutMillis = 1_000)
        detector.onSwipe(Swipe.Up, 0)
        detector.onSwipe(Swipe.Up, 500)
        detector.onSwipe(Swipe.Down, 5_000)
        detector.progress shouldBe 0
    }
}
