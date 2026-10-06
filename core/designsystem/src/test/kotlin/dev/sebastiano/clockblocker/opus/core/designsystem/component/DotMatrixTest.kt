package dev.sebastiano.clockblocker.opus.core.designsystem.component

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DotMatrixTest {

    @Test
    fun `covers every IATA character plus the data punctuation`() {
        val wanted = ('A'..'Z') + ('0'..'9') + listOf('\u00B7', '+', '-', '\u2212', ' ', ':', '?')
        DotMatrixFont.supported shouldContainAll wanted
    }

    @Test
    fun `every glyph fits the 5 by 7 grid`() {
        DotMatrixFont.supported.forEach { c ->
            val rows = DotMatrixFont.glyph(c)
            rows.size shouldBe DotMatrixFont.Rows
            rows.forEach { row -> (row in 0 until (1 shl DotMatrixFont.Columns)) shouldBe true }
        }
    }

    @Test
    fun `letters and digits are all distinct`() {
        val shapes = (('A'..'Z') + ('0'..'9')).map { DotMatrixFont.glyph(it).toList() }
        shapes.toSet().size shouldBe shapes.size
    }

    @Test
    fun `the resting dot is a single centred dot`() {
        val lit = (0 until DotMatrixFont.Rows).flatMap { r ->
            (0 until DotMatrixFont.Columns).filter { c -> DotMatrixFont.isLit('\u00B7', r, c) }.map { r to it }
        }
        lit shouldBe listOf(3 to 2)
    }

    @Test
    fun `lowercase reads as uppercase and unknown characters fall back to the question mark`() {
        DotMatrixFont.glyph('s').toList() shouldBe DotMatrixFont.glyph('S').toList()
        DotMatrixFont.glyph('\u00E9').toList() shouldBe DotMatrixFont.glyph('?').toList()
        DotMatrixFont.glyph(' ').all { it == 0 } shouldBe true
    }

    @Test
    fun `T has its bar on the top row`() {
        (0 until 5).all { DotMatrixFont.isLit('T', 0, it) } shouldBe true
        DotMatrixFont.isLit('T', 6, 2) shouldBe true
        DotMatrixFont.isLit('T', 6, 0) shouldBe false
    }

    @Test
    fun `an empty slot shows one resting dot per cell`() {
        dotMatrixCells(null, minLength = 3) shouldBe "\u00B7\u00B7\u00B7"
        dotMatrixCells("", minLength = 3) shouldBe "\u00B7\u00B7\u00B7"
        dotMatrixCells("  ", minLength = 3) shouldBe "\u00B7\u00B7\u00B7"
    }

    @Test
    fun `codes are uppercased and padded so the width never jumps`() {
        dotMatrixCells("sfo", minLength = 3) shouldBe "SFO"
        dotMatrixCells("AB", minLength = 3) shouldBe "AB "
        dotMatrixCells("ABCD", minLength = 3) shouldBe "ABCD"
    }

    @Test
    fun `the reveal runs left to right`() {
        // Midway, the first character is further along than the last, and the left column of a cell leads its right.
        val p = 0.5f
        dotRevealFraction(0, 2, p, 3) shouldBeGreaterThan dotRevealFraction(2, 2, p, 3)
        dotRevealFraction(1, 0, 0.45f, 3) shouldBeGreaterThan dotRevealFraction(1, 4, 0.45f, 3)
    }

    @Test
    fun `the reveal starts dark and ends fully lit`() = runTest {
        checkAll(Arb.int(0, 7), Arb.int(0, 4), Arb.int(1, 8)) { i, c, n ->
            val index = i % n
            dotRevealFraction(index, c, 0f, n) shouldBe 0f
            dotRevealFraction(index, c, 1f, n) shouldBe 1f
        }
    }

    @Test
    fun `the reveal never leaves 0 to 1 and never goes backwards`() = runTest {
        checkAll(Arb.float(0f, 1f).filter { it.isFinite() }, Arb.float(0f, 1f).filter { it.isFinite() }, Arb.int(0, 2), Arb.int(0, 4)) { a, b, i, c ->
            val lo = minOf(a, b)
            val hi = maxOf(a, b)
            val f = dotRevealFraction(i, c, lo, 3)
            (f in 0f..1f) shouldBe true
            (dotRevealFraction(i, c, hi, 3) >= f) shouldBe true
        }
    }

    @Test
    fun `a single cell still reveals`() {
        dotRevealFraction(0, 0, 0.5f, 1) shouldBeGreaterThan 0f
        dotRevealFraction(0, 4, 0.5f, 1) shouldBeLessThan 1f
    }

    @Test
    fun `spoken codes are spelled out letter by letter`() {
        spellOut("SFO") shouldBe "S F O"
        spellOut("lis") shouldBe "L I S"
        spellOut("LIS") shouldNotBe "LIS"
    }
}
