package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

/** A deliberate one-finger swipe, in physical screen directions (the code is the code, in RTL too). */
internal enum class Swipe { Up, Down, Left, Right }

/** ↑ ↑ ↓ ↓ ← → ← → (B and A are left to the imagination). */
internal val KonamiSequence: List<Swipe> = listOf(
    Swipe.Up, Swipe.Up, Swipe.Down, Swipe.Down, Swipe.Left, Swipe.Right, Swipe.Left, Swipe.Right,
)

/**
 * Classifies a finished one-finger gesture that travelled ([dx], [dy]) pixels. Null when it's too short (a tap
 * or a wobble) or too diagonal to be a deliberate direction: the dominant axis must be at least twice the other.
 */
internal fun classifySwipe(dx: Float, dy: Float, thresholdPx: Float): Swipe? {
    val ax = abs(dx)
    val ay = abs(dy)
    if (max(ax, ay) < thresholdPx) return null
    return when {
        ax >= ay * 2f -> if (dx > 0f) Swipe.Right else Swipe.Left
        ay >= ax * 2f -> if (dy > 0f) Swipe.Down else Swipe.Up
        else -> null
    }
}

/**
 * Recognises [sequence] in a stream of swipes. A wrong swipe doesn't throw everything away: progress falls back
 * to the longest prefix of the code that the recent swipes still spell (so ↑ ↑ ↑ ↓ ↓ … still counts). A pause
 * longer than [timeoutMillis] starts over.
 */
internal class KonamiDetector(
    private val sequence: List<Swipe> = KonamiSequence,
    private val timeoutMillis: Long = 2_500L,
) {
    init {
        require(sequence.isNotEmpty()) { "sequence must not be empty" }
    }

    /** How many swipes of [sequence] have been matched so far. */
    var progress: Int = 0
        private set

    private var lastAtMillis: Long? = null

    /** Feeds one swipe made at [atMillis] (uptime). Returns true when it completes the code (then starts over). */
    fun onSwipe(swipe: Swipe, atMillis: Long): Boolean {
        val last = lastAtMillis
        if (last != null && atMillis - last > timeoutMillis) progress = 0
        lastAtMillis = atMillis
        val history = sequence.subList(0, progress) + swipe
        progress = (minOf(history.size, sequence.size) downTo 0).first { k ->
            history.subList(history.size - k, history.size) == sequence.subList(0, k)
        }
        if (progress == sequence.size) {
            progress = 0
            lastAtMillis = null
            return true
        }
        return false
    }
}

/**
 * Watches one-finger swipes on this node for the Konami code without consuming anything (the list still
 * scrolls; observed on the Initial pass). Multi-touch gestures are ignored. [enabled] false = no listener.
 * The listener is keyed on nothing so progress survives recomposition: pass an [onCode] that reads the latest
 * state itself (e.g. through `rememberUpdatedState`).
 */
internal fun Modifier.konamiCode(enabled: Boolean, onCode: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        val detector = KonamiDetector()
        val threshold = SwipeThreshold.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var end = down.position
            var multiTouch = false
            var upAt = down.uptimeMillis
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) multiTouch = true
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                end = change.position
                upAt = change.uptimeMillis
                if (!change.pressed) break
            }
            if (multiTouch) return@awaitEachGesture
            val delta = end - down.position
            val swipe = classifySwipe(delta.x, delta.y, threshold) ?: return@awaitEachGesture
            if (detector.onSwipe(swipe, upAt)) onCode()
        }
    }

private val SwipeThreshold = 48.dp

/** The 8-bit easter egg lasts for the app session only (not persisted, not a setting). */
internal object EightBitSession {
    var enabled: Boolean by mutableStateOf(false)
}
