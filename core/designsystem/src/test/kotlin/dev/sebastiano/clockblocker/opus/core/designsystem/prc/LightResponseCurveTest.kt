package dev.sebastiano.clockblocker.opus.core.designsystem.prc

import dev.sebastiano.clockblocker.opus.core.designsystem.prc.LightResponseCurve.Effect
import dev.sebastiano.clockblocker.opus.core.designsystem.prc.LightResponseCurve.Tick
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs

class LightResponseCurveTest {

    private val samples = (-1200..1200).map { it / 100.0 }

    @Test
    fun `the curve crosses over at the body's coldest point`() {
        LightResponseCurve.shiftAt(0.0) shouldBe (0.0 plusOrMinus 1e-9)
        LightResponseCurve.shiftAt(-0.25) shouldBeLessThan 0.0
        LightResponseCurve.shiftAt(0.25) shouldBeGreaterThan 0.0
    }

    @Test
    fun `light before the coldest point delays, light after it advances`() = runTest {
        checkAll(Arb.numericDouble(-11.99, -0.01)) { x -> LightResponseCurve.shiftAt(x) shouldBeLessThan 0.0 }
        checkAll(Arb.numericDouble(0.01, 11.99)) { x -> LightResponseCurve.shiftAt(x) shouldBeGreaterThan 0.0 }
    }

    @Test
    fun `the largest delay is about 3_4 h, 3 to 4 h before the coldest point`() {
        val min = samples.minBy(LightResponseCurve::shiftAt)
        min shouldBeIn -4.0..-3.0
        LightResponseCurve.shiftAt(min) shouldBe (-LightResponseCurve.PeakDelayHours plusOrMinus 0.01)
        LightResponseCurve.PeakDelayHours shouldBe (3.4 plusOrMinus 1e-9)
    }

    @Test
    fun `the largest advance is about 2 h, 2 to 3 h after the coldest point`() {
        val max = samples.maxBy(LightResponseCurve::shiftAt)
        max shouldBeIn 2.0..3.0
        LightResponseCurve.shiftAt(max) shouldBe (LightResponseCurve.PeakAdvanceHours plusOrMinus 0.01)
        LightResponseCurve.PeakAdvanceHours shouldBe (2.0 plusOrMinus 1e-9)
    }

    @Test
    fun `the delay lobe is larger than the advance lobe`() {
        val delayArea = samples.filter { it < 0 }.sumOf { -LightResponseCurve.shiftAt(it) }
        val advanceArea = samples.filter { it > 0 }.sumOf { LightResponseCurve.shiftAt(it) }
        delayArea shouldBeGreaterThan advanceArea
        LightResponseCurve.PeakDelayHours shouldBeGreaterThan LightResponseCurve.PeakAdvanceHours
    }

    @Test
    fun `every shift stays within the two peaks`() = runTest {
        checkAll(Arb.numericDouble(-48.0, 48.0)) { x ->
            val shift = LightResponseCurve.shiftAt(x)
            shift shouldBeGreaterThanOrEqual -LightResponseCurve.PeakDelayHours - 1e-9
            shift shouldBeLessThanOrEqual LightResponseCurve.PeakAdvanceHours + 1e-9
        }
    }

    @Test
    fun `the curve repeats every 24 h and is weak only around 12 h from the coldest point`() = runTest {
        checkAll(Arb.numericDouble(-12.0, 12.0)) { x ->
            LightResponseCurve.shiftAt(x + 24) shouldBe (LightResponseCurve.shiftAt(x) plusOrMinus 1e-9)
        }
        abs(LightResponseCurve.shiftAt(12.0)) shouldBeLessThan 0.05
        abs(LightResponseCurve.shiftAt(-11.5)) shouldBeLessThan 0.1 * LightResponseCurve.PeakDelayHours
        abs(LightResponseCurve.shiftAt(11.5)) shouldBeLessThan 0.1 * LightResponseCurve.PeakDelayHours
        // No long dead zone (Khalsa 2003): light moves the clock noticeably for most of the day.
        for (x in samples.filter { abs(it) in 0.5..8.0 }) abs(LightResponseCurve.shiftAt(x)) shouldBeGreaterThan 0.2
    }

    @Test
    fun `the readout is qualitative`() {
        LightResponseCurve.effectAt(-3.5) shouldBe Effect.LaterStrongly
        LightResponseCurve.effectAt(-8.5) shouldBe Effect.LaterALittle
        LightResponseCurve.effectAt(2.5) shouldBe Effect.EarlierStrongly
        LightResponseCurve.effectAt(6.0) shouldBe Effect.EarlierALittle
        LightResponseCurve.effectAt(0.0) shouldBe Effect.Barely
        LightResponseCurve.effectAt(12.0) shouldBe Effect.Barely
    }

    @Test
    fun `every readout follows the curve's sign`() = runTest {
        checkAll(Arb.numericDouble(-12.0, 12.0)) { x ->
            val shift = LightResponseCurve.shiftAt(x)
            when (LightResponseCurve.effectAt(x)) {
                Effect.LaterStrongly, Effect.LaterALittle -> shift shouldBeLessThan 0.0
                Effect.EarlierStrongly, Effect.EarlierALittle -> shift shouldBeGreaterThan 0.0
                Effect.Barely -> abs(shift) shouldBeLessThan LightResponseCurve.BarelyHours
            }
        }
    }

    @Test
    fun `dragging ticks on every hour and marks the coldest point`() {
        LightResponseCurve.tickBetween(-3.75, -3.5) shouldBe null
        LightResponseCurve.tickBetween(-3.25, -2.75) shouldBe Tick.Hour
        LightResponseCurve.tickBetween(2.25, 1.75) shouldBe Tick.Hour
        LightResponseCurve.tickBetween(-0.25, 0.25) shouldBe Tick.ColdestPoint
        LightResponseCurve.tickBetween(0.5, -0.5) shouldBe Tick.ColdestPoint
        LightResponseCurve.tickBetween(-0.25, 0.0) shouldBe Tick.ColdestPoint
        LightResponseCurve.tickBetween(1.0, 1.0) shouldBe null
    }

    @Test
    fun `positions snap to quarter hours within the curve`() {
        LightResponseCurve.snap(1.13) shouldBe 1.25
        LightResponseCurve.snap(-0.1) shouldBe 0.0
        LightResponseCurve.snap(13.0) shouldBe 12.0
        LightResponseCurve.snap(-20.0) shouldBe -12.0
    }

    @Test
    fun `a window is placed in hours around the coldest point`() {
        val cbtMin = Instant.parse("2026-05-01T04:30:00Z")
        LightResponseCurve.window(cbtMin, cbtMin.plusSeconds(3600), cbtMin.plusSeconds(4 * 3600)).segments shouldBe listOf(1.0..4.0)
        LightResponseCurve.window(cbtMin, cbtMin.minusSeconds(5400), cbtMin.plusSeconds(1800)).segments shouldBe listOf(-1.5..0.5)
    }

    @Test
    fun `a window that runs past either end wraps round to the other, because the curve repeats every 24 h`() {
        val cbtMin = Instant.parse("2026-05-01T04:30:00Z")
        val home = LightResponseCurve.window(cbtMin, cbtMin.plusSeconds(3 * 3600), cbtMin.plusSeconds(16 * 3600))
        home.segments shouldBe listOf(3.0..12.0, -12.0..-8.0)
        val early = LightResponseCurve.window(cbtMin, cbtMin.minusSeconds(14 * 3600), cbtMin.minusSeconds(10 * 3600))
        early.segments shouldBe listOf(10.0..12.0, -12.0..-10.0)
        LightResponseCurve.window(cbtMin, cbtMin.plusSeconds(13 * 3600), cbtMin.plusSeconds(15 * 3600)).segments shouldBe listOf(-11.0..-9.0)
    }

    @Test
    fun `a wrapped window keeps its middle and contains only its own hours`() {
        val window = LightResponseCurve.Window.between(3.0, 16.0)
        window.middle shouldBe 9.5
        LightResponseCurve.Window.between(10.0, 16.0).middle shouldBe -11.0
        (11.0 in window) shouldBe true
        (-10.0 in window) shouldBe true
        (-7.0 in window) shouldBe false
        (0.0 in window) shouldBe false
        window.largest shouldBe 3.0..12.0
    }

    @Test
    fun `a window never covers more than the whole curve`() = runTest {
        checkAll(Arb.numericDouble(-48.0, 48.0), Arb.numericDouble(0.0, 72.0)) { start, length ->
            val window = LightResponseCurve.Window.between(start, start + length)
            window.segments.sumOf { it.endInclusive - it.start } shouldBe (minOf(length, 24.0) plusOrMinus 1e-9)
            window.segments.forEach {
                it.start shouldBeIn -12.0..12.0
                it.endInclusive shouldBeIn -12.0..12.0
            }
            window.middle shouldBeIn -12.0..12.0
        }
    }
}
