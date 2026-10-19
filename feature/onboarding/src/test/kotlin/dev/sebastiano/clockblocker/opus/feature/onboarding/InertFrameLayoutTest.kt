package dev.sebastiano.clockblocker.opus.feature.onboarding

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sample notification's real document is an example, not a control: taps never reach its actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class InertFrameLayoutTest {
    @Test
    fun `a tap on the played document never reaches its click actions`() {
        val frame = InertFrameLayout(ApplicationProvider.getApplicationContext())
        var clicks = 0
        val child = View(frame.context).apply { setOnClickListener { clicks++ } }
        frame.addView(child, ViewGroup.LayoutParams(100, 100))
        frame.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, 100, 100)

        tap(frame, 50f, 50f)

        clicks shouldBe 0
        frame.isClickable shouldBe false
    }

    @Test
    fun `is hidden from accessibility and blocks descendant focus`() {
        val frame = InertFrameLayout(ApplicationProvider.getApplicationContext())
        frame.importantForAccessibility shouldBe View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        frame.descendantFocusability shouldBe ViewGroup.FOCUS_BLOCK_DESCENDANTS
        frame.isFocusable shouldBe false
    }

    private fun tap(view: View, x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(down, down + 50, MotionEvent.ACTION_UP, x, y, 0))
    }
}
