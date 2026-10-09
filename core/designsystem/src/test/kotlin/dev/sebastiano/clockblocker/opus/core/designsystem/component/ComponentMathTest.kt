package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyGradient
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
import java.time.Instant
import java.time.ZoneId

class ComponentMathTest {

    @Test
    fun `the 8-bit sky samples six flat bands from top to bottom`() {
        val gradient = SkyGradient(Color.Black, Color.White)
        val bands = gradient.bands(SkyPixelBands)
        bands.size shouldBe 6
        // Band centres, not the edges: neither pure black nor pure white, and monotonically lighter.
        bands.first().luminance() shouldBeGreaterThan 0f
        bands.last().luminance() shouldBeLessThan 1f
        bands.zipWithNext().forEach { (a, b) -> b.luminance() shouldBeGreaterThan a.luminance() }
    }

    @Test
    fun `a flat sky stays flat when banded`() {
        val navy = Color(0xFF101830)
        SkyGradient(navy, navy).bands(SkyPixelBands).forEach { it.toArgb() shouldBe navy.toArgb() }
    }

    @Test
    fun `sun rises on the left, peaks at solar noon and sets on the right`() {
        val rise = celestialPosition(6.5f, 6.5f, 19f)
        val noon = celestialPosition(12.75f, 6.5f, 19f)
        val set = celestialPosition(19f, 6.5f, 19f)
        rise.isSun shouldBe true
        rise.x shouldBe (0.1f plusOrMinus 1e-4f)
        noon.x shouldBe (0.5f plusOrMinus 1e-4f)
        set.x shouldBe (0.9f plusOrMinus 1e-4f)
        noon.y shouldBeLessThan rise.y
        rise.y shouldBe (set.y plusOrMinus 1e-4f)
    }

    @Test
    fun `the moon owns the night and peaks halfway between sunset and sunrise`() {
        val midnightish = celestialPosition(0.75f, 6.5f, 19f)
        midnightish.isSun shouldBe false
        midnightish.x shouldBe (0.5f plusOrMinus 1e-4f)
        celestialPosition(3f, 6.5f, 19f).isSun shouldBe false
    }

    @Test
    fun `a sunset after midnight keeps the sun up past midnight and the moon owns the short night (#97)`() {
        // Sunrise 01:30, sunset 00:30 the next day: 23 h of sun, an hour of night.
        celestialPosition(13f, 1.5f, 24.5f).let { it.isSun shouldBe true; it.x shouldBe (0.5f plusOrMinus 1e-4f) }
        celestialPosition(0.25f, 1.5f, 24.5f).isSun shouldBe true
        celestialPosition(1f, 1.5f, 24.5f).let { it.isSun shouldBe false; it.x shouldBe (0.5f plusOrMinus 1e-4f) }
        // The same day with its sunset as an hour of the clock.
        celestialPosition(1f, 1.5f, 0.5f) shouldBe celestialPosition(1f, 1.5f, 24.5f)
    }

    @Test
    fun `celestial position stays inside the box for any hour`() = runTest {
        checkAll(Arb.float(-48f, 48f).filter { it.isFinite() }) { h ->
            val p = celestialPosition(h)
            p.x shouldBeGreaterThan 0.09f
            p.x shouldBeLessThan 0.91f
            p.y shouldBeGreaterThan 0.29f
            p.y shouldBeLessThan 0.93f
        }
    }

    @Test
    fun `misalignment fraction is flat when adapted and full at the start`() {
        misalignmentFraction(0f, 8f) shouldBe 0f
        misalignmentFraction(8f, 8f) shouldBe 1f
        misalignmentFraction(-4f, -8f) shouldBe (0.5f plusOrMinus 1e-6f)
        misalignmentFraction(12f, 8f) shouldBe 1f
        misalignmentFraction(3f, 0f) shouldBe 0f
    }

    @Test
    fun `day delta compares calendar dates across zones`() {
        val lisbon = ZoneId.of("Europe/Lisbon")
        val tokyo = ZoneId.of("Asia/Tokyo")
        // 2026-10-10 23:00 in Lisbon (WEST, +1) is 07:00 on the 11th in Tokyo.
        val instant = Instant.parse("2026-10-10T22:00:00Z")
        dayDelta(instant, lisbon, tokyo) shouldBe 1
        dayDelta(instant, tokyo, lisbon) shouldBe -1
        dayDelta(Instant.parse("2026-10-10T03:00:00Z"), lisbon, tokyo) shouldBe 0
        dayDeltaSuffix(1) shouldBe "+1"
        dayDeltaSuffix(-1) shouldBe "\u22121"
        dayDeltaSuffix(0) shouldBe null
    }

    @Test
    fun `confetti launches upward then falls under gravity`() {
        val particles = confettiParticles(36, 7L)
        particles.forEach { p ->
            confettiOffset(p, 0f).getDistance() shouldBe (0f plusOrMinus 1e-6f)
            confettiOffset(p, 0.25f).y shouldBeLessThan 0f
            confettiOffset(p, 2.2f).y shouldBeGreaterThan confettiOffset(p, 0.25f).y
        }
        confettiParticles(36, 7L) shouldBe particles
    }
}
