package dev.sebastiano.clockblocker.opus.feature.onboarding

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The full-height gate keeps its promise for real rows: whenever the search stays in its normal layout with the
 * keyboard up, at least [MinVisibleResults] whole results show above the keyboard. Swept over the room a host can
 * give it, at a large font, where results wrap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w600dp-h1600dp-xxhdpi")
class HomeSearchGateTest : ClockblockScreenshotTest() {
    private var room by mutableStateOf(600.dp)
    private val state = HomeSearchState()
    private lateinit var view: View
    private lateinit var focusManager: FocusManager

    private val results = persistentListOf(DemoData.LAX, DemoData.SFO, DemoData.LIS, DemoData.LHR, DemoData.SIN, DemoData.SYD)

    private fun keyboard(heightDp: Int) {
        compose.runOnUiThread {
            val px = (heightDp * view.resources.displayMetrics.density).toInt()
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
                .setVisible(WindowInsetsCompat.Type.ime(), px > 0)
                .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    @Test
    fun `in the normal layout three whole results always show above the keyboard`() {
        setContent(fontScale = 1.5f) {
            view = LocalView.current
            focusManager = LocalFocusManager.current
            // The search's room runs down to the window's bottom, as on a screen; the keyboard covers that bottom.
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(room).padding(horizontal = 24.dp)) {
                HomeSearch(
                    state = state,
                    query = "an",
                    results = results,
                    resultsQuery = "an",
                    now = DemoData.Now,
                    room = room,
                    onQueryChange = {},
                    onPick = {},
                )
                }
            }
        }
        val window = 1600.dp
        var normalLayouts = 0
        for (roomDp in 700..1600 step 25) {
            compose.runOnUiThread { focusManager.clearFocus() }
            keyboard(0)
            room = roomDp.dp
            compose.waitForIdle()
            compose.onNodeWithTag(HomeZoneTags.Search).performSemanticsAction(SemanticsActions.RequestFocus)
            keyboard(KeyboardDp)
            if (state.fullHeight) continue
            normalLayouts++
            val keyboardTop: Dp = window - KeyboardDp.dp
            val whole = results.count { compose.onNodeWithTag(HomeZoneTags.result(it)).getUnclippedBoundsInRoot().bottom <= keyboardTop }
            check(whole >= MinVisibleResults) { "room $roomDp dp: normal layout with only $whole whole results above the keyboard" }
        }
        // The sweep reaches rooms where the normal layout is kept, so the check above ran.
        check(normalLayouts > 0)
    }

    private companion object {
        const val KeyboardDp = 900
    }
}
