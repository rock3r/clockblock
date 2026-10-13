package dev.sebastiano.clockblocker.opus.e2e

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeTags
import dev.sebastiano.clockblocker.opus.feature.plan.PlanTags
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The globes with real multi-touch, through the system's input pipeline (not Compose's test injection): the dial ↔
 * world zoom following two fingers, dragging and flinging the globe, and pinch-zooming the full-screen globe.
 * The gestures run at a finger's pace, so a screen recording of this test shows the motion as a person sees it.
 */
@RunWith(AndroidJUnit4::class)
class GlobeGesturesTest : ClockblockE2eTest() {

    @Test
    fun fingersZoomFromTheDialToTheWorldAndTheGlobeTurnsAndZooms() {
        seedOnboarded()
        val active = graph.seedTripWithActiveAdvice()
        launch(deepLink = "clockblock://plan/${active.trip.id}")
        awaitTag(PlanTags.NowCard, LongTimeoutMillis)
        SystemClock.sleep(Pause)

        // A slow two-finger spread on the dial, the second finger landing a moment after the first (on the ring).
        val dial = bounds(PlanTags.Dial)
        pinch(dial.center, from = dial.width * 0.12f, to = dial.width * 0.3f, millis = 1_400, holdMillis = 400)
        awaitTag(PlanTags.World)
        awaitTag(PlanTags.WorldCaption).assertIsDisplayed()
        SystemClock.sleep(Pause)

        // Drag the globe round, then flick it (frame timing logged for these, under GlobeGesturesTest).
        shell("dumpsys gfxinfo ${context.packageName} reset")
        val world = bounds(PlanTags.WorldGlobe)
        drag(world.center - Offset(world.width * 0.25f, 0f), world.center + Offset(world.width * 0.25f, 0f), millis = 700)
        SystemClock.sleep(Pause)
        drag(world.center + Offset(world.width * 0.2f, 0f), world.center - Offset(world.width * 0.2f, 0f), millis = 120)
        SystemClock.sleep(Pause * 2)
        logFrames("world drag + fling")
        awaitTag(GlobeTags.Recentre).performClick()
        SystemClock.sleep(Pause)

        // Full screen: spread to zoom in, drag it about, then close.
        awaitTag(PlanTags.WorldFullScreen).performClick()
        awaitTag(PlanTags.WorldOverlay).assertIsDisplayed()
        SystemClock.sleep(Pause)
        shell("dumpsys gfxinfo ${context.packageName} reset")
        val full = bounds(PlanTags.WorldOverlayGlobe)
        pinch(full.center, from = full.width * 0.1f, to = full.width * 0.3f, millis = 800)
        SystemClock.sleep(Pause)
        drag(full.center + Offset(0f, full.height * 0.15f), full.center - Offset(0f, full.height * 0.1f), millis = 600)
        SystemClock.sleep(Pause)
        logFrames("full-screen pinch zoom + drag")
        device.pressBack()
        awaitGone(PlanTags.WorldOverlay)
        SystemClock.sleep(Pause)

        // Two fingers closing on the world zoom back in to the dial.
        val back = bounds(PlanTags.World)
        pinch(back.center, from = back.width * 0.4f, to = back.width * 0.12f, millis = 1_000)
        awaitGone(PlanTags.World)
        awaitTag(PlanTags.Dial).assertIsDisplayed()
        SystemClock.sleep(Pause)
    }

    private fun logFrames(what: String) {
        val stats = shell("dumpsys gfxinfo ${context.packageName}").lines()
            .filter { line -> FrameStatKeys.any { line.trim().startsWith(it) } }
        android.util.Log.i("GlobeGesturesTest", "$what: ${stats.joinToString(" | ") { it.trim() }}")
    }

    private fun bounds(tag: String): Rect = awaitTag(tag).fetchSemanticsNode().boundsInWindow

    /** Two fingers, horizontally either side of [centre], going from [from] to [to] px off it over [millis]. */
    private fun pinch(centre: Offset, from: Float, to: Float, millis: Long, holdMillis: Long = 0) {
        val down = SystemClock.uptimeMillis()
        fun points(d: Float) = listOf(centre - Offset(d, 0f), centre + Offset(d, 0f))
        inject(down, down, MotionEvent.ACTION_DOWN, points(from).take(1))
        SystemClock.sleep(StaggerMillis)
        inject(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), points(from))
        val start = SystemClock.uptimeMillis()
        while (true) {
            val t = ((SystemClock.uptimeMillis() - start).toFloat() / millis).coerceAtMost(1f)
            val eased = t * t * (3f - 2f * t)
            inject(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, points(from + (to - from) * eased))
            if (t >= 1f) break
            SystemClock.sleep(FrameMillis)
        }
        SystemClock.sleep(holdMillis)
        inject(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), points(to))
        inject(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, points(to).take(1))
    }

    private fun drag(from: Offset, to: Offset, millis: Long) {
        val down = SystemClock.uptimeMillis()
        inject(down, down, MotionEvent.ACTION_DOWN, listOf(from))
        val start = SystemClock.uptimeMillis()
        var last = from
        while (true) {
            val t = ((SystemClock.uptimeMillis() - start).toFloat() / millis).coerceAtMost(1f)
            last = from + (to - from) * t
            inject(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, listOf(last))
            if (t >= 1f) break
            SystemClock.sleep(FrameMillis)
        }
        inject(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, listOf(last))
    }

    private fun inject(downTime: Long, eventTime: Long, action: Int, points: List<Offset>) {
        val properties = Array(points.size) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(points.size) { i -> MotionEvent.PointerCoords().apply { x = points[i].x; y = points[i].y; pressure = 1f; size = 1f } }
        val event = MotionEvent.obtain(downTime, eventTime, action, points.size, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        instrumentation.uiAutomation.injectInputEvent(event, true)
        event.recycle()
    }

    private companion object {
        const val Pause = 600L
        const val FrameMillis = 8L
        const val StaggerMillis = 40L
        val FrameStatKeys = listOf("Total frames", "Janky frames", "50th", "90th", "95th", "99th", "Number Frame deadline missed")
    }
}
