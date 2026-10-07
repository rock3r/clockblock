package dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec

import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialPalettes
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.Daylight
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.numericFloat
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

/** The sky rings under real suns: short days, short nights, and the polar ones that never end. */
class DialSkyTest {

    private val palette = DialPalettes.Light
    private val labels = DefaultDialLabels(is24Hour = true)

    /** Tokyo, 15:20, body 7 h behind; solar noon at 11:40. */
    private val tokyo = DialState(
        instant = Instant.parse("2026-03-11T06:20:00Z"),
        displayZoneId = "Asia/Tokyo",
        localMinute = 15 * 60f + 20f,
        bodyAheadMinutes = -420f,
        sunriseMinute = 345f,
        sunsetMinute = 1035f,
    )
    private val polarNight = tokyo.copy(daylight = Daylight.AlwaysDown, sunriseMinute = 700f, sunsetMinute = 700f)
    private val midnightSun = tokyo.copy(daylight = Daylight.AlwaysUp, sunriseMinute = 700f, sunsetMinute = 700f)

    private fun spec(state: DialState, mode: BodyRingMode = BodyRingMode.Simple) =
        TwoSkies.spec(state, palette, labels, 328f, 328f, mode = mode)

    private fun ring(state: DialState, part: DialPart, mode: BodyRingMode = BodyRingMode.Simple) =
        spec(state, mode).ops.filterIsInstance<DialOp.SweepRing>().single { it.part == part }

    private fun ringLabels(state: DialState) =
        spec(state).ops.filterIsInstance<DialOp.CurvedText>().filter { it.part == DialPart.RingLabel }.map { it.text }

    @Test
    fun `a polar night paints both skies night all round and labels only the night`() {
        ring(polarNight, DialPart.LocalSky).colors.toSet() shouldBe setOf(palette.sky.night)
        ring(polarNight, DialPart.BodySky).colors.toSet() shouldBe setOf(palette.sky.night)
        val texts = ringLabels(polarNight)
        texts shouldContain "TOKYO NIGHT"
        texts shouldNotContain "TOKYO DAY"
    }

    @Test
    fun `the midnight sun paints both skies day all round and labels only the day`() {
        ring(midnightSun, DialPart.LocalSky).colors.toSet() shouldBe setOf(palette.sky.day)
        ring(midnightSun, DialPart.BodySky).colors.toSet() shouldBe setOf(palette.sky.day)
        val texts = ringLabels(midnightSun)
        texts shouldContain "TOKYO DAY"
        texts shouldNotContain "TOKYO NIGHT"
    }

    @Test
    fun `the precise body ring keeps the biological night under a polar sky`() {
        ring(polarNight, DialPart.BodySky, BodyRingMode.Precise).colors.toSet() shouldNotBe setOf(palette.sky.night)
    }

    @Test
    fun `a polar night is centred on solar midnight and a polar day on solar noon`() {
        val night = BodySky.localNight(polarNight)
        night.lengthMinutes shouldBe 1440f
        night.centre shouldBe (1420f plusOrMinus 0.01f)
        val day = BodySky.localNight(midnightSun)
        day.lengthMinutes shouldBe 0f
        day.dayCentre shouldBe (700f plusOrMinus 0.01f)
    }

    @Test
    fun `the body ring carries a polar sky turned by the jet lag`() {
        val night = BodySky.nightInLocal(polarNight, BodyRingMode.Simple)
        night.lengthMinutes shouldBe 1440f
        night.centre shouldBe ((1420f + 420f).mod(1440f) plusOrMinus 0.01f)
    }

    @Test
    fun `mid-day is day and mid-night is night however short either is`() = runTest {
        checkAll(Arb.numericFloat(0f, 1439f), Arb.numericFloat(10f, 1430f)) { sunrise, dayLength ->
            val sunset = (sunrise + dayLength).mod(1440f)
            palette.sky(sunrise + dayLength / 2f, sunrise, sunset) shouldBe palette.sky.day
            palette.sky(sunset + (1440f - dayLength) / 2f, sunrise, sunset) shouldBe palette.sky.night
        }
    }

    @Test
    fun `a polar sky is one colour`() {
        palette.sky(0f, 700f, 700f, Daylight.AlwaysDown) shouldBe palette.sky.night
        palette.sky(700f, 700f, 700f, Daylight.AlwaysUp) shouldBe palette.sky.day
    }
}
