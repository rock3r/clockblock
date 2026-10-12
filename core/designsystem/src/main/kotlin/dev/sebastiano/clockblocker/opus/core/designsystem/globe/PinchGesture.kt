package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import kotlin.math.abs
import kotlin.math.ln

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

/** How far the fingers spread (×1.8) to go all the way from the dial to the world; its inverse goes back. */
const val WorldPinchRatio: Float = 1.8f

/** A release faster than this (progress per second) settles the way the fingers were heading, wherever it is. */
private const val SettleFlingVelocity: Float = 1f

/**
 * Pure: the transition's progress (0 = dial, 1 = world) for fingers now [ratio] times as far apart as when the pinch
 * began at [from]. Even on a log scale (spreading ×1.34 is half way, ×1.8 all the way), clamped to 0…1.
 */
fun pinchProgress(from: Float, ratio: Float, fullRatio: Float = WorldPinchRatio): Float {
    if (ratio <= 0f) return from
    return (from + ln(ratio) / ln(fullRatio)).coerceIn(0f, 1f)
}

/**
 * Pure: where a released pinch settles. A flick ([velocity] in progress per second) goes the way it was heading;
 * otherwise the nearer side wins.
 */
fun settleTarget(progress: Float, velocity: Float): Float = when {
    abs(velocity) >= SettleFlingVelocity -> if (velocity > 0f) 1f else 0f
    progress >= 0.5f -> 1f
    else -> 0f
}

/**
 * A continuous two-finger pinch, for a transition the fingers drive. One finger is left alone: until a second finger
 * comes down nothing is consumed, so a one-finger scrub, drag, tap or long-press underneath works as before. From the
 * second finger on, the gesture is this modifier's: it consumes every change on the way in ([PointerEventPass.Initial]),
 * so content underneath stops following the first finger (and the finger left after the pinch stays the pinch's).
 * Put it on a parent of the content, or before the content's own `pointerInput`.
 *
 * - [onStart]: the second finger came down.
 * - [onPinch]: the fingers are now `ratio` times as far apart as when the pinch began (every move).
 * - [onEnd]: a finger lifted (`cancelled` false) or the gesture was cancelled (`cancelled` true), with the
 *   release speed of `ln(ratio)` per second, for a flick that carries on.
 */
fun Modifier.pinchTransition(
    enabled: Boolean,
    onStart: () -> Unit = {},
    onPinch: (ratio: Float) -> Unit,
    onEnd: (logRatioVelocity: Float, cancelled: Boolean) -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    composed {
        val currentStart by rememberUpdatedState(onStart)
        val currentPinch by rememberUpdatedState(onPinch)
        val currentEnd by rememberUpdatedState(onEnd)
        pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var start = -1f
                var active = false
                var ended = false
                val tracker = VelocityTracker1D(isDataDifferential = false)
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size < 2) {
                            if (active) {
                                // A finger lifted: the pinch is over. The finger left behind stays the pinch's, so it
                                // can't start a scrub or a drag underneath.
                                active = false
                                ended = true
                                currentEnd(tracker.calculateVelocity().takeIf { it.isFinite() } ?: 0f, false)
                            }
                            if (ended) event.changes.forEach { it.consume() }
                            if (pressed.isEmpty()) break
                            continue
                        }
                        event.changes.forEach { it.consume() }
                        if (ended) continue
                        val distance = (pressed[0].position - pressed[1].position).getDistance()
                        if (start < 0f) {
                            if (distance <= 0f) continue
                            start = distance
                            active = true
                            currentStart()
                        }
                        val ratio = distance / start
                        tracker.addDataPoint(pressed[0].uptimeMillis, ln(ratio))
                        currentPinch(ratio)
                    }
                } finally {
                    // Cancelled (the system took the touch, or the content left): put things back as they were.
                    if (active) currentEnd(0f, true)
                }
            }
        }
    }
}
