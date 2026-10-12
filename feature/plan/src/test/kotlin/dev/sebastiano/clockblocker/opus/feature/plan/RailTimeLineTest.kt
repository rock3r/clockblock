package dev.sebastiano.clockblocker.opus.feature.plan

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/**
 * The timeline's time texts. The range and its duration are separate texts ([RangeAndDuration] puts them on one line
 * or stacks them, see `RailRowLayoutTest`); these helpers only decide where each may break on its own.
 */
class RailTimeLineTest {

    @Test
    fun `a range breaks only after its dash`() {
        railRange("22:45 – 01:00") shouldBe "22:45\u00A0– 01:00"
        railRange("10:00 PM – 1:00 AM") shouldBe "10:00\u00A0PM\u00A0– 1:00\u00A0AM"
    }

    @Test
    fun `a moment's single time never breaks`() {
        railRange("10:00 PM") shouldBe "10:00\u00A0PM"
    }

    @Test
    fun `a duration never breaks`() {
        railDuration("2 h 15 min") shouldNotContain " "
        railDuration("2 h 15 min") shouldBe "2\u00A0h\u00A015\u00A0min"
    }
}
