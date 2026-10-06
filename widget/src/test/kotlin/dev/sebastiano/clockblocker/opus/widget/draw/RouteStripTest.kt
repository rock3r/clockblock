package dev.sebastiano.clockblocker.opus.widget.draw

import dev.sebastiano.clockblocker.opus.core.designsystem.component.DotMatrixFont
import dev.sebastiano.clockblocker.opus.widget.state.WidgetRoute
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RouteStripTest {
    private val code = 0xFF112233.toInt()
    private val line = 0xFF445566.toInt()

    private fun litDots(text: String) =
        text.sumOf { c -> (0 until DotMatrixFont.Rows).sumOf { r -> (0 until DotMatrixFont.Columns).count { DotMatrixFont.isLit(c, r, it) } } }

    @Test
    fun `codes are drawn dot for dot in the design system's dot-matrix face`() {
        val ops = RouteStrip.build(WidgetRoute("LIS", "HND"), code, line)
        ops.filterIsInstance<DrawOp.Circle>().count { it.color == code } shouldBe litDots("LIS") + litDots("HND")
        // The connector between the codes is the only other ink.
        ops.filter { it.color != code }.all { it is DrawOp.Line && it.color == line } shouldBe true
    }

    @Test
    fun `everything stays inside the strip`() {
        val halfW = RouteStrip.WIDTH / 2f
        val halfH = RouteStrip.HEIGHT / 2f
        RouteStrip.build(WidgetRoute("WWW", "MMM"), code, line).filter { op ->
            when (op) {
                is DrawOp.Circle -> op.cx - op.r < -halfW || op.cx + op.r > halfW || op.cy - op.r < -halfH || op.cy + op.r > halfH
                is DrawOp.Line -> listOf(op.x0, op.x1).any { it !in -halfW..halfW } || listOf(op.y0, op.y1).any { it !in -halfH..halfH }
                else -> true
            }
        }.shouldBeEmpty()
    }

    @Test
    fun `short codes keep the strip's width and lowercase reads as uppercase`() {
        RouteStrip.build(WidgetRoute("lis", "hnd"), code, line) shouldBe RouteStrip.build(WidgetRoute("LIS", "HND"), code, line)
        // A two-letter code is padded, so the destination sits where it always does.
        val short = RouteStrip.build(WidgetRoute("LI", "HND"), code, line).filterIsInstance<DrawOp.Circle>()
        val full = RouteStrip.build(WidgetRoute("LIS", "HND"), code, line).filterIsInstance<DrawOp.Circle>()
        short.takeLast(litDots("HND")) shouldBe full.takeLast(litDots("HND"))
    }
}
