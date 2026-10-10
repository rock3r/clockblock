package dev.sebastiano.clockblocker.opus.widget.rc

import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.TimeAxis
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The strips' now line on the host ([stripShift]): it follows the clock round the whole day, never stopping. */
class StripShiftTest {
    // 24 h over 240 dp from 09:00: 10 dp an hour. Captured at 16:00 (x = 70).
    private val axis = TimeAxis(left = 0f, right = 240f, startMinute = 9 * 60f, spanMinutes = 1440f)
    private val now = 16 * 60f

    private fun xAfter(minutes: Float) = axis.x(now) + stripShift(axis, now, minutes)

    @Test
    fun `it moves along the axis with the clock`() {
        xAfter(0f) shouldBe (70f plusOrMinus 0.01f)
        xAfter(120f) shouldBe (90f plusOrMinus 0.01f)
    }

    @Test
    fun `past the end of the window it wraps to the start, at the same minute of the day`() {
        // 17 h later is 09:00 the next day: back at the left edge, not pinned to the right one.
        xAfter(17 * 60f) shouldBe (0f plusOrMinus 0.01f)
        // 20 h later is 12:00: 3 h in.
        xAfter(20 * 60f) shouldBe (30f plusOrMinus 0.01f)
    }
}
