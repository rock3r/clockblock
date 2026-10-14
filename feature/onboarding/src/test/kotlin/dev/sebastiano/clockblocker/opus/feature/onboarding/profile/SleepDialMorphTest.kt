package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import android.animation.ValueAnimator
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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

    private fun show(reduceMotion: Boolean, daysOff: SleepWindow? = this.daysOff) {
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

    /** The pill's rows above its time (glyph and label), as a fresh capture each call. */
    private fun labelBand(pill: String, time: String): () -> ImageBitmap {
        val pillTop = compose.onNodeWithTag(pill).getBoundsInRoot().top
        val timeTop = compose.onNodeWithContentDescription(time, useUnmergedTree = true).getBoundsInRoot().top
        val rows = with(compose.density) { (timeTop - pillTop).roundToPx() }
        return { compose.onNodeWithTag(pill).captureToImage().crop(0, rows) }
    }

    /** The pill's time row: the band of its morphing text. */
    private fun timeRow(pill: String, time: String): ImageBitmap {
        val pillBounds = compose.onNodeWithTag(pill).getBoundsInRoot()
        val timeBounds = compose.onNodeWithContentDescription(time, useUnmergedTree = true).getBoundsInRoot()
        val (top, bottom) = with(compose.density) { (timeBounds.top - pillBounds.top).roundToPx() to (timeBounds.bottom - pillBounds.top).roundToPx() }
        return compose.onNodeWithTag(pill).captureToImage().crop(top, bottom)
    }

    private fun ImageBitmap.crop(top: Int, bottom: Int): ImageBitmap {
        val source = asAndroidBitmap()
        return Bitmap.createBitmap(source, 0, top, source.width, bottom - top).asImageBitmap()
    }

    /** The share of pixels that differ between two same-sized images. */
    private fun ImageBitmap.differingFraction(other: ImageBitmap): Float {
        val a = toPixelMap()
        val b = other.toPixelMap()
        var differing = 0
        for (x in 0 until width) for (y in 0 until height) if (a[x, y] != b[x, y]) differing++
        return differing.toFloat() / (width * height)
    }

    /**
     * The process's animator duration scale, as the window manager sets it (`ValueAnimator.setDurationScale` is hidden
     * API, so reflection; Robolectric's shadow takes the call), without touching the animator duration setting.
     */
    private fun setProcessAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
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

    /**
     * Device finding (Pixel 7 Pro): rolling digits drew a quarter line past their own line box, so the 8 and 9 of a
     * 09:30 → 07:15 roll painted over the pill's "Wake" label. The label row must not change on any frame.
     */
    @Test
    fun a_rolling_digit_never_paints_over_the_pill_label() {
        show(reduceMotion = false)
        val band = labelBand(SleepDialTags.WakePill, time = "07:15")
        val settled = band()
        compose.onNodeWithTag(SleepDialTags.DaysOff).performClick()
        repeat(24) {
            compose.mainClock.advanceTimeBy(FrameMillis)
            band().sameAs(settled) shouldBe true
        }
    }

    /**
     * Device finding (Pixel 7 Pro): on the data spring a one-digit roll (09:00 → 07:00) was all but over in about
     * 125 ms (15 frames at 120 Hz), too quick to read as a morph. 200 ms in, the digit must still be visibly rolling
     * (here the data spring leaves ~1.3 % of the time row still changing; the time morph spring ~3.3 %).
     */
    @Test
    fun a_one_digit_morph_is_still_visibly_rolling_200_ms_in() {
        show(reduceMotion = false, daysOff = work.copy(wake = LocalTime.of(8, 15)))
        compose.onNodeWithTag(SleepDialTags.DaysOff).performClick()
        repeat(13) { compose.mainClock.advanceTimeBy(FrameMillis) }
        val mid = timeRow(SleepDialTags.WakePill, time = "08:15")
        compose.mainClock.advanceTimeBy(SettleMillis)
        val settled = timeRow(SleepDialTags.WakePill, time = "08:15")
        (mid.differingFraction(settled) >= StillRollingFraction) shouldBe true
    }

    /**
     * New users start with days off linked to work days (the owner's "only sometimes"): a switch then has nothing to
     * morph, because both pills show the same times. That's by design; the cue is the two rings trading places.
     */
    @Test
    fun a_linked_switch_keeps_the_times_and_swaps_the_rings() {
        show(reduceMotion = false, daysOff = null)
        val pillBefore = pill()
        val dialBefore = compose.onNodeWithTag(SleepDialTags.Dial).captureToImage()
        compose.onNodeWithTag(SleepDialTags.DaysOff).performClick()
        compose.mainClock.advanceTimeBy(MidMillis)
        pill().sameAs(pillBefore) shouldBe true
        compose.onNodeWithTag(SleepDialTags.Dial).captureToImage().sameAs(dialBefore) shouldBe false
    }

    /** A TalkBack nudge (setProgress, what a swipe up or down on the handle sends) is a discrete change: it morphs. */
    @Test
    fun a_talkback_nudge_morphs() {
        show(reduceMotion = false)
        compose.onNodeWithTag(SleepDialTags.Bedtime).performSemanticsAction(SemanticsActions.SetProgress) {
            it(23 * 60f + 45f)
        }
        compose.mainClock.advanceTimeBy(MidMillis)
        val mid = pill()
        compose.mainClock.advanceTimeBy(SettleMillis)
        assertMorphs(mid to pill())
    }

    /**
     * Device finding (Pixel 7 Pro), the owner's "only sometimes": torph decided on its own whether the system had
     * turned animations off, from `ValueAnimator.areAnimatorsEnabled()` read once when the pills first composed. The
     * process's animator scale can be 0 while the animator duration setting is on (the window manager disables
     * animators for an app in some states, such as battery saver on some builds), and then the pills snapped while the
     * rings, the toggle and every other animation in the app still moved. The pills must follow the app's own reduce
     * motion (the setting, watched live, and Compose's MotionDurationScale) like everything else.
     */
    @Test
    fun the_pills_morph_when_only_the_process_animator_scale_is_off() {
        setProcessAnimatorScale(0f)
        try {
            show(reduceMotion = false)
            assertMorphs(toggle(SleepDialTags.DaysOff))
        } finally {
            setProcessAnimatorScale(1f)
        }
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
        const val FrameMillis = 16L
        const val StillRollingFraction = 0.02f
    }
}
