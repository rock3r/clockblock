package dev.sebastiano.clockblocker.opus.core.designsystem.component

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The header sky's atmosphere: when the stars are out, how strong the sun's glow is, and a stable star field. */
class SkyAtmosphereTest {

    private val sunrise = 6.5f
    private val sunset = 19f

    @Test
    fun `no stars by day, all of them deep in the night`() {
        starVisibility(12f, sunrise, sunset) shouldBe 0f
        starVisibility(1f, sunrise, sunset) shouldBe 1f
    }

    @Test
    fun `stars come out through dusk and fade through dawn`() {
        starVisibility(sunset, sunrise, sunset) shouldBe 0f
        val dusk = starVisibility(sunset + 0.75f, sunrise, sunset)
        (dusk > 0f && dusk < 1f) shouldBe true
        starVisibility(sunset + 2f, sunrise, sunset) shouldBe 1f
        val dawn = starVisibility(sunrise - 0.75f, sunrise, sunset)
        (dawn > 0f && dawn < 1f) shouldBe true
        starVisibility(sunrise, sunrise, sunset) shouldBe 0f
    }

    @Test
    fun `a sunset after midnight keeps the night on the right side`() {
        // Midnight-sun-ish: sunrise 02:00, sunset 23:30.
        starVisibility(23f, 2f, 23.5f) shouldBe 0f
        (starVisibility(0.75f, 2f, 23.5f) > 0f) shouldBe true
    }

    @Test
    fun `the glow peaks at sunrise and sunset, stays soft at noon and is gone at night`() {
        val atSunset = sunGlowStrength(sunset, sunrise, sunset)
        val atNoon = sunGlowStrength(12.75f, sunrise, sunset)
        atSunset shouldBe (1f plusOrMinus 0.001f)
        (atNoon > 0f && atNoon < atSunset) shouldBe true
        sunGlowStrength(1f, sunrise, sunset) shouldBe 0f
    }

    @Test
    fun `the same seed always lays out the same sky`() {
        SkyStars.generate(seed = 11, count = 30).points shouldBe SkyStars.generate(seed = 11, count = 30).points
        SkyStars.generate(seed = 11, count = 30).points.size shouldBe 30
    }

    @Test
    fun `stars twinkle around their brightness only while a session runs, and rest exactly there otherwise`() {
        val star = SkyStar(0.5f, 0.5f, 1f, 0.8f, phase = 1.2f, twinkles = true)
        star.alphaAt(sessionMillis = -1L, sessionLengthMillis = 45_000L) shouldBe 0.8f
        val samples = (0..40).map { star.alphaAt(sessionMillis = 10_000L + it * 250L, sessionLengthMillis = 45_000L) }
        (samples.min() < 0.8f) shouldBe true
        samples.all { it in 0f..0.8f } shouldBe true
        // A steady star never moves.
        star.copy(twinkles = false).alphaAt(sessionMillis = 12_345L, sessionLengthMillis = 45_000L) shouldBe 0.8f
    }

    @Test
    fun `a twinkle eases out at the session's real end, so resting after it never jumps`() {
        val star = SkyStar(0.5f, 0.5f, 1f, 0.8f, phase = 0.3f, twinkles = true)
        listOf(20_000L, 45_000L, 90_000L).forEach { length ->
            star.alphaAt(sessionMillis = length, sessionLengthMillis = length) shouldBe 0.8f
        }
    }
}
