package dev.sebastiano.clockblocker.opus.feature.plan

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/** The timeline's time line: the range and the duration on one line ("22:45 – 01:00 · 2 h 15 min"). */
class RailTimeLineTest {

    @Test
    fun `the duration follows the range on the same line`() {
        railTimeLine("22:45 – 01:00", "2 h 15 min") shouldBe "22:45\u00A0–\u00A001:00 · 2\u00A0h\u00A015\u00A0min"
    }

    @Test
    fun `a narrow row breaks only between the range and the duration`() {
        val line = railTimeLine("10:00 PM – 1:00 AM", "3 h")
        line.substringBefore(" · ") shouldNotContain " "
        line.substringAfter(" · ") shouldNotContain " "
        line shouldBe "10:00\u00A0PM\u00A0–\u00A01:00\u00A0AM · 3\u00A0h"
    }

    @Test
    fun `no duration leaves the range alone`() {
        railTimeLine("22:00", null) shouldBe "22:00"
    }
}
