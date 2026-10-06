package dev.sebastiano.clockblocker.opus.core.designsystem.component

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalTime

class RollingTextTest {

    private fun List<RollSegment>.render(): List<String> = map { if (it.rolls) "[${it.from}>${it.to}]" else it.to }

    @Test
    fun `only the minute digit rolls when the minute ticks`() {
        rollSegments("14:20", "14:21").render() shouldBe listOf("14:2", "[0>1]")
    }

    @Test
    fun `equal-length changes roll digit by digit and keep the separator still`() {
        rollSegments("09:59", "10:00").render() shouldBe listOf("[0>1]", "[9>0]", ":", "[5>0]", "[9>0]")
    }

    @Test
    fun `a unit suffix stays still while the number rolls as one`() {
        rollSegments("+3\u00BD h", "+2 h").render() shouldBe listOf("+", "[3\u00BD>2]", " h")
        rollSegments("9 h", "10 h").render() shouldBe listOf("[9>10]", " h")
    }

    @Test
    fun `a new leading digit rolls in from nothing`() {
        rollSegments("9d", "19d").render() shouldBe listOf("[>1]", "9d")
    }

    @Test
    fun `identical text is a single still segment`() {
        rollSegments("62%", "62%").render() shouldBe listOf("62%")
        rollSegments("", "").render() shouldBe emptyList()
    }

    @Test
    fun `segments always rebuild both texts`() = runTest {
        checkAll(Arb.string(0, 8), Arb.string(0, 8)) { a, b ->
            val segments = rollSegments(a, b)
            segments.joinToString("") { it.from } shouldBe a
            segments.joinToString("") { it.to } shouldBe b
        }
    }

    @Test
    fun `numbers are read with their sign and halves`() {
        numericValue("+3\u00BD h") shouldBe 3.5
        numericValue("\u22125 h") shouldBe -5.0
        numericValue("-2") shouldBe -2.0
        numericValue("\u00BD h") shouldBe 0.5
        numericValue("62%") shouldBe 62.0
        numericValue("14:20") shouldBe 1420.0
        numericValue("Adapted") shouldBe null
    }

    @Test
    fun `growing values roll up, shrinking values roll down`() {
        rollDirection("\u22128 h", "\u22126\u00BD h") shouldBe RollDirection.Up
        rollDirection("+3\u00BD h", "+2 h") shouldBe RollDirection.Down
        rollDirection("9d", "10d") shouldBe RollDirection.Up
        rollDirection("Day", "Night") shouldBe RollDirection.Up
    }

    @Test
    fun `time rolls forward the short way round midnight`() {
        timeRollDirection(LocalTime.of(23, 59), LocalTime.of(0, 0)) shouldBe RollDirection.Up
        timeRollDirection(LocalTime.of(0, 0), LocalTime.of(23, 59)) shouldBe RollDirection.Down
        timeRollDirection(LocalTime.of(14, 20), LocalTime.of(14, 21)) shouldBe RollDirection.Up
    }
}
