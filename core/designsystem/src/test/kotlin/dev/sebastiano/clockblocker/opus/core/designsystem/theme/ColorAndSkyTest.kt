package dev.sebastiano.clockblocker.opus.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.float
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalTime
import kotlin.math.abs

class SkyPaletteTest {
    private val sky = SkyPalette.Default

    @Test
    fun `named phases land where the sun is`() {
        sky.phaseAt(3f) shouldBe SkyPhase.Night
        sky.phaseAt(5.75f) shouldBe SkyPhase.PreDawn
        sky.phaseAt(6.8f) shouldBe SkyPhase.Dawn
        sky.phaseAt(12f) shouldBe SkyPhase.Day
        sky.phaseAt(18.5f) shouldBe SkyPhase.Golden
        sky.phaseAt(19.6f) shouldBe SkyPhase.Dusk
        sky.phaseAt(23f) shouldBe SkyPhase.Night
    }

    @Test
    fun `phases follow sunrise and sunset`() {
        // Midsummer far north: sunrise 04:00, sunset 22:30.
        sky.phaseAt(4.3f, sunriseHour = 4f, sunsetHour = 22.5f) shouldBe SkyPhase.Dawn
        sky.phaseAt(21f, sunriseHour = 4f, sunsetHour = 22.5f) shouldBe SkyPhase.Day
    }

    /** The phases round the clock from the first PreDawn: the order a sky goes through in a day. */
    private fun cycle(sunriseHour: Float, sunsetHour: Float): List<SkyPhase> {
        val phases = sky.keyframes(sunriseHour, sunsetHour).map { it.second }
        val from = phases.indexOf(SkyPhase.PreDawn)
        return phases.drop(from) + phases.take(from)
    }

    private val dayInOrder = listOf(
        SkyPhase.PreDawn, SkyPhase.Dawn, SkyPhase.Day, SkyPhase.Day, SkyPhase.Golden, SkyPhase.Dusk, SkyPhase.Night, SkyPhase.Night,
    )

    @Test
    fun `a night of an hour still goes from dusk through night to pre-dawn (#97)`() {
        // Sunset 00:30, sunrise 01:30 (near the midnight sun), with the sunset given either side of midnight.
        cycle(sunriseHour = 1.5f, sunsetHour = 24.5f) shouldBe dayInOrder
        cycle(sunriseHour = 1.5f, sunsetHour = 0.5f) shouldBe dayInOrder
        sky.phaseAt(1f, sunriseHour = 1.5f, sunsetHour = 24.5f) shouldNotBe SkyPhase.Day
        sky.phaseAt(13f, sunriseHour = 1.5f, sunsetHour = 24.5f) shouldBe SkyPhase.Day
    }

    @Test
    fun `a day of two hours still goes from dawn through day to golden hour`() {
        // Sunrise 11:00, sunset 13:00 (near the polar night).
        cycle(sunriseHour = 11f, sunsetHour = 13f) shouldBe dayInOrder
        sky.phaseAt(3f, sunriseHour = 11f, sunsetHour = 13f) shouldBe SkyPhase.Night
    }

    @Test
    fun `gradient equals the keyframe sky at the keyframe`() {
        val noon = sky.gradientAt(12f)
        noon shouldBe sky[SkyPhase.Day].let { SkyGradient(ColorMath.mix(it.top, it.top, 0f), ColorMath.mix(it.bottom, it.bottom, 0f)) }
    }

    @Test
    fun `gradient is continuous across midnight and everywhere`() = runTest {
        val a = sky.gradientAt(23.999f)
        val b = sky.gradientAt(0f)
        a.top.distanceTo(b.top) shouldBeLessThanOrEqual 0.01f
        checkAll(Arb.float(0f, 23.98f).filter { it.isFinite() }) { h ->
            val x = sky.gradientAt(h)
            val y = sky.gradientAt(h + 0.01f)
            x.top.distanceTo(y.top) shouldBeLessThanOrEqual 0.04f
            x.bottom.distanceTo(y.bottom) shouldBeLessThanOrEqual 0.04f
        }
    }

    @Test
    fun `local time overload indexes by hour`() {
        sky.gradientAt(LocalTime.of(18, 30)) shouldBe sky.gradientAt(18.5f)
    }

    @Test
    fun `blending toward primary moves every sky`() {
        val blended = sky.blendedToward(Color(0xFF4F46E5), 0.15f)
        blended[SkyPhase.Day] shouldNotBe sky[SkyPhase.Day]
    }

    private fun Color.distanceTo(o: Color) = abs(red - o.red) + abs(green - o.green) + abs(blue - o.blue)
}

class ColorMathTest {

    @Test
    fun `hue delta wraps the short way`() {
        ColorMath.hueDelta(350f, 10f) shouldBe (20f plusOrMinus 0.001f)
        ColorMath.hueDelta(10f, 350f) shouldBe (-20f plusOrMinus 0.001f)
    }

    @Test
    fun `harmonize rotates at most 15 degrees toward primary and keeps lightness`() = runTest {
        val primary = Color(0xFF4F46E5)
        AdviceColors.Light.let { palette ->
            AdviceType.entries.forEach { type ->
                val c = palette[type].color
                val h = ColorMath.harmonize(c, primary)
                val moved = abs(ColorMath.hueDelta(ColorMath.hue(c), ColorMath.hue(h)))
                moved shouldBeLessThanOrEqual 15.5f
                ColorMath.lightness(h) shouldBe (ColorMath.lightness(c) plusOrMinus 0.03f)
                // Never moves away from the primary hue.
                val before = abs(ColorMath.hueDelta(ColorMath.hue(c), ColorMath.hue(primary)))
                val after = abs(ColorMath.hueDelta(ColorMath.hue(h), ColorMath.hue(primary)))
                after shouldBeLessThanOrEqual before + 0.5f
            }
        }
    }

    @Test
    fun `mix endpoints are the inputs`() = runTest {
        checkAll(Arb.float(0f, 1f).filter { it.isFinite() }, Arb.float(0f, 1f).filter { it.isFinite() }) { r, g ->
            val a = Color(r, g, 0.3f)
            val b = Color(0.2f, 0.9f, 0.6f)
            val m0 = ColorMath.mix(a, b, 0f)
            m0.red shouldBe (a.red plusOrMinus 0.01f)
            m0.green shouldBe (a.green plusOrMinus 0.01f)
        }
    }

    @Test
    fun `dimming lowers lightness`() {
        val c = AdviceColors.Dark[AdviceType.SeeBrightLight].color
        ColorMath.lightness(ColorMath.dim(c, 0.6f)) shouldBeLessThanOrEqual ColorMath.lightness(c) * 0.65f
    }

    @Test
    fun `every advice type has a pattern and both palettes cover every type`() {
        AdviceType.entries.forEach { type ->
            AdviceColors.Light[type]
            AdviceColors.Dark[type]
            type.pattern
        }
        AdviceType.AvoidLight.pattern shouldBe AdvicePattern.Hatch
    }
}
