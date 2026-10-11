package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Which way two fingers moved apart or together. */
enum class Pinch { Out, In }

/** Pure: the pinch two fingers [startDistance] apart have made once they are [distance] apart, if any yet. */
fun pinchOf(startDistance: Float, distance: Float, outRatio: Float = PinchOutRatio, inRatio: Float = PinchInRatio): Pinch? {
    if (startDistance <= 0f) return null
    val ratio = distance / startDistance
    return when {
        ratio >= outRatio -> Pinch.Out
        ratio <= inRatio -> Pinch.In
        else -> null
    }
}

/** Fingers have to spread this much (×1.3) to count as a pinch out… */
const val PinchOutRatio: Float = 1.3f

/** …or close this much (×0.77) to count as a pinch in. */
const val PinchInRatio: Float = 0.77f

/**
 * Calls [onPinch] once per gesture when two fingers pinch out or in. One finger is left alone: until a second finger
 * comes down nothing is consumed, so a one-finger scrub, a tap or a long-press underneath works as before. From the
 * second finger on, the gesture is this modifier's: it consumes the changes on the way in ([PointerEventPass.Initial]),
 * so whatever is underneath stops following the first finger. Put it before the content's own `pointerInput`.
 */
fun Modifier.pinchGesture(enabled: Boolean, onPinch: (Pinch) -> Unit): Modifier = if (!enabled) this else composed {
    val current by rememberUpdatedState(onPinch)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var start = -1f
            var fired = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size < 2) {
                    if (start >= 0f) event.changes.forEach { it.consume() }
                    continue
                }
                val distance = (pressed[0].position - pressed[1].position).length()
                if (start < 0f) start = distance
                event.changes.forEach { it.consume() }
                if (!fired) {
                    pinchOf(start, distance)?.let {
                        fired = true
                        current(it)
                    }
                }
            }
        }
    }
}

private fun Offset.length(): Float = getDistance()
