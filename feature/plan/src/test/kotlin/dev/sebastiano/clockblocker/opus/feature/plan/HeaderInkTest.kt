package dev.sebastiano.clockblocker.opus.feature.plan

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalTime

/** Header ink during the sky cross-fade on a day pick (issue #23): it follows the sky that is mostly showing. */
class HeaderInkTest {
    private val night = LocalTime.of(3, 0)
    private val day = LocalTime.of(11, 0)

    @Test
    fun `with no fade, the ink follows the live sky`() {
        headerInkTime(from = null, to = day, progress = 0f) shouldBe day
    }

    @Test
    fun `until the new sky is half faded in, the ink stays with the old one`() {
        headerInkTime(from = night, to = day, progress = 0f) shouldBe night
        headerInkTime(from = night, to = day, progress = 0.49f) shouldBe night
    }

    @Test
    fun `from halfway on, the ink follows the new sky`() {
        headerInkTime(from = night, to = day, progress = 0.5f) shouldBe day
        headerInkTime(from = night, to = day, progress = 1f) shouldBe day
    }
}
