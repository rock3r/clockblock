package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.sign

/**
 * Whether the plan's floating toolbar steps aside for the reader: [hidden] once they have scrolled on through the
 * plan by [thresholdPx], back as soon as they scroll back as far. A change of direction starts the count again, so
 * jitter under a resting finger never toggles it.
 *
 * Only the user's own scrolling counts (it reaches [connection] through nested scroll); the toolbar's jumps (Now, a
 * picked day) scroll the list directly and never hide it.
 */
@Stable
internal class ToolbarScrollState(private val thresholdPx: Float) {
    var hidden by mutableStateOf(false)
        private set

    private var travel = 0f

    /** [delta]: the scroll the list consumed, in px; negative = the content moved up (reading on). */
    fun onScrolled(delta: Float) {
        if (delta == 0f) return
        if (sign(delta) != sign(travel)) travel = 0f
        travel += delta
        if (travel <= -thresholdPx) hidden = true else if (travel >= thresholdPx) hidden = false
    }

    /** Back on screen, counting from here. */
    fun show() {
        hidden = false
        travel = 0f
    }

    /** Hand this to the scrolling list's `nestedScroll`: it reports what the list itself consumed. */
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            onScrolled(consumed.y)
            return Offset.Zero
        }
    }
}
