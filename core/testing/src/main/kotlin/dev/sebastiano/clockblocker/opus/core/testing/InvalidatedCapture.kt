package dev.sebastiano.clockblocker.opus.core.testing

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Duration
import org.robolectric.Shadows.shadowOf

/**
 * Captures the compose root with Roborazzi after letting platform animations settle and forcing every view to
 * redraw.
 *
 * Robolectric 4.17 at SDK 37 often never draws the ComposeView of a fresh test activity, so static content
 * captures as the bare window background (only the first test in a sandbox reliably draws). Invalidating the
 * view tree right before the capture makes it draw. Platform-drawn animations (ripples, overscroll stretch) run
 * on the system clock, not the compose test clock, so the looper idles for [SettleTime] first and every drawable
 * jumps to its end state: a capture after a click shows the settled state, not a random ripple frame.
 * Use this instead of `onRoot().captureRoboImage(...)`.
 */
fun ComposeTestRule.captureRoboImageInvalidated(filePath: String) {
    shadowOf(Looper.getMainLooper()).idleFor(SettleTime)
    invalidateAllViews()
    onRoot().captureRoboImage(filePath)
}

private val SettleTime: Duration = Duration.ofSeconds(2)

/** Ends drawable animations and invalidates every view of every resumed activity, once compose is idle. */
fun ComposeTestRule.invalidateAllViews() {
    runOnIdle {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach {
            invalidateTree(it.window.decorView)
        }
    }
}

private fun invalidateTree(view: View) {
    // Ends platform ripples: Compose hosts them in a RippleHostView whose RippleDrawable never finishes its exit
    // animation under Robolectric, and its sparkle noise differs on every run.
    view.jumpDrawablesToCurrentState()
    view.invalidate()
    if (view is ViewGroup) for (i in 0 until view.childCount) invalidateTree(view.getChildAt(i))
}
