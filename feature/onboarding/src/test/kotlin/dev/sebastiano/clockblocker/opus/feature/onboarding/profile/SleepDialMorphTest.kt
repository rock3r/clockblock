package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialGeometry
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.feature.onboarding.ClockblockScreenshotTest
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Bedtime / Wake pills morph (torph) on every discrete change with motion on, however the change comes after the
 * last one: a toggle, a toggle back, a toggle mid-morph, a toggle after a drag. Under reduce motion they snap.
 * Black-box: a pill mid-morph must look different from the same pill once settled.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h800dp-xxhdpi")
class SleepDialMorphTest : ClockblockScreenshotTest() {

    private val work = SleepWindow(LocalTime.of(23, 30), LocalTime.of(7, 15))
    private val daysOff = SleepWindow(LocalTime.of(0, 45), LocalTime.of(9, 30))

    private fun show(reduceMotion: Boolean) {
        var editedWork by mutableStateOf(work)
        var editedDaysOff by mutableStateOf<SleepWindow?>(daysOff)
        setContent(reduceMotion = reduceMotion) {
            SleepDial(editedWork, editedDaysOff, { editedWork = it }, { editedDaysOff = it })
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun pill(): ImageBitmap = compose.onNodeWithTag(SleepDialTags.BedtimePill).captureToImage()

    private fun ImageBitmap.sameAs(other: ImageBitmap): Boolean {
        if (width != other.width || height != other.height) return false
        val a = toPixelMap()
        val b = other.toPixelMap()
        for (x in 0 until width) for (y in 0 until height) if (a[x, y] != b[x, y]) return false
        return true
    }

    /** Taps [tag], then returns (a frame a little way in, the settled frame). */
    private fun toggle(tag: String, midMillis: Long = MidMillis): Pair<ImageBitmap, ImageBitmap> {
        compose.onNodeWithTag(tag).performClick()
        compose.mainClock.advanceTimeBy(midMillis)
        val mid = pill()
        compose.mainClock.advanceTimeBy(SettleMillis)
        return mid to pill()
    }

    private fun assertMorphs(frames: Pair<ImageBitmap, ImageBitmap>) {
        frames.first.sameAs(frames.second) shouldBe false
    }

    @Test
    fun toggling_back_and_forth_morphs_every_time() {
        show(reduceMotion = false)
        repeat(3) {
            assertMorphs(toggle(SleepDialTags.DaysOff))
            assertMorphs(toggle(SleepDialTags.WorkDays))
        }
    }

    @Test
    fun toggling_mid_morph_morphs_back() {
        show(reduceMotion = false)
        compose.onNodeWithTag(SleepDialTags.DaysOff).performClick()
        compose.mainClock.advanceTimeBy(MidMillis)
        assertMorphs(toggle(SleepDialTags.WorkDays))
    }

    @Test
    fun toggling_after_a_drag_morphs() {
        show(reduceMotion = false)
        compose.onNodeWithTag(SleepDialTags.Dial).performTouchInput {
            val r = 122f * minOf(width, height) / 320f
            fun at(minute: Float): Offset {
                val a = Math.toRadians(DialGeometry.angleForMinute(minute).toDouble())
                return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
            }
            // Drag the work-day bedtime (23:30) back to 23:00, frame by frame.
            down(at(23 * 60f + 30f))
            var minute = 23 * 60f + 30f
            while (minute > 23 * 60f) {
                minute -= 5f
                moveTo(at(minute))
                advanceEventTime(16)
            }
            up()
        }
        compose.mainClock.advanceTimeBy(SettleMillis)
        assertMorphs(toggle(SleepDialTags.DaysOff))
        assertMorphs(toggle(SleepDialTags.WorkDays))
    }

    @Test
    fun reduce_motion_snaps() {
        show(reduceMotion = true)
        val (mid, settled) = toggle(SleepDialTags.DaysOff, midMillis = 16)
        mid.sameAs(settled) shouldBe true
    }

    private companion object {
        const val MidMillis = 48L
        const val SettleMillis = 2_000L
    }
}
