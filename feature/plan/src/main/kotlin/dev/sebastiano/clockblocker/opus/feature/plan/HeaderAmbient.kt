package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.MotionDurationScale
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/**
 * The header sky's slow ambient life: once in a while (an ambient *session*), a few stars breathe in and out at night
 * and a small plane crosses with a contrail that fades. [sessionMillis] is how far into the session it is, or -1 at
 * rest; it is only read in the draw phase, so a session redraws the header without recomposing anything.
 *
 * The plan screen is opened many times a day and stays on screen, so this is rare delight (frequency rule): one
 * session at most every [AmbientSkyGap], persisted ([AmbientSkyStore]), and only once the header has been in view for
 * [AmbientFirstDelayMillis] (a quick glance doesn't spend it). Nothing here carries meaning (the sky, the sun and the
 * words do), so at rest, under Reduce motion, in sleep windows or with animations removed it simply doesn't happen.
 */
@Stable
internal class HeaderAmbient {
    var sessionMillis by mutableLongStateOf(-1L)
}

/**
 * The header's [HeaderAmbient]. A session may run while [allowed] (easter eggs on, not Night-safe, not a short
 * window) once [due] (it last played long enough ago). Before it starts it must [reserve] the session: an atomic
 * check-and-mark in the shared store, so of two headers on screen at once (two windows) only one plays, and nothing
 * is due again for a long while. That only stops the *next* session: the one under way plays to its end (unless
 * [allowed] turns off, which ends it for good: it isn't replayed when [allowed] comes back). While [visible] is false
 * (the header collapsed) a session waits, frame loop and clock stopped, and resumes when the sky shows again.
 *
 * Self-timed: every wait and the session clock follow [MotionDurationScale]; with animations removed (scale 0) no
 * session starts, and one under way pauses.
 */
@Composable
internal fun rememberHeaderAmbient(allowed: Boolean, due: Boolean, visible: () -> Boolean, reserve: suspend () -> Boolean): HeaderAmbient {
    val ambient = remember { HeaderAmbient() }
    // Latched while a session runs: marking it played turns [due] off, which must not cut short the session it
    // started. A grant that hasn't started yet goes with [due] (another header may have just spent the session).
    var granted by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(due) {
        if (due) granted = true else if (!running) granted = false
    }
    val currentVisible by rememberUpdatedState(visible)
    val currentReserve by rememberUpdatedState(reserve)
    val active = allowed && granted && !ClockblockTheme.motion.reduceMotion
    LaunchedEffect(active) {
        ambient.sessionMillis = -1L
        if (!active) return@LaunchedEffect
        fun scale(): Float = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        fun showing(): Boolean = scale() > 0f && currentVisible()
        suspend fun awaitShowing() = snapshotFlow { showing() }.first { it }
        var started = false
        try {
            // The header has to stay in view (with animations on) for the whole first delay.
            while (true) {
                awaitShowing()
                val wait = (AmbientFirstDelayMillis * scale()).toLong()
                val hidden = withTimeoutOrNull(wait) { snapshotFlow { showing() }.first { !it } }
                if (hidden == null) break
            }
            // Someone else (another header) got the session first: give the grant up.
            if (!currentReserve()) {
                granted = false
                return@LaunchedEffect
            }
            started = true
            running = true
            var elapsed = 0f
            var lastFrame = withFrameMillis { it }
            while (elapsed < AmbientSessionMillis) {
                if (!showing()) {
                    awaitShowing()
                    lastFrame = withFrameMillis { it }
                    continue
                }
                val dt = withFrameMillis { now -> (now - lastFrame).also { lastFrame = now } }
                val s = scale()
                if (s <= 0f) continue
                elapsed += dt / s
                ambient.sessionMillis = elapsed.toLong().coerceAtMost(AmbientSessionMillis)
            }
        } finally {
            ambient.sessionMillis = -1L
            running = false
            // A started session spends the grant however it ends (played out, or cut short by Reduce motion, a sleep
            // window or Night-safe), so the gate turning back on never replays it before it's due again.
            if (started) granted = false
        }
    }
    return ambient
}

/** Where the ambient plane is: [progress] 0 → 1 across the sky (1 = gone), its contrail at [trailAlpha]. */
internal data class PlaneFrame(val progress: Float, val trailAlpha: Float)

/** The plane [sessionMillis] into a session, or null when there is none (before take-off, after the trail fades). */
internal fun planeAt(sessionMillis: Long): PlaneFrame? {
    if (sessionMillis < PlaneStartMillis) return null
    val flown = sessionMillis - PlaneStartMillis
    if (flown <= PlaneCrossMillis) return PlaneFrame(flown / PlaneCrossMillis.toFloat(), 1f)
    val fading = flown - PlaneCrossMillis
    if (fading > PlaneTrailFadeMillis) return null
    return PlaneFrame(1f, 1f - fading / PlaneTrailFadeMillis.toFloat())
}

/** A session starts only once the header has been in view this long (opening the screen stays calm). */
internal const val AmbientFirstDelayMillis = 6_000L

/** How long a session lasts. */
internal const val AmbientSessionMillis = 45_000L

/** The plane takes off this far into a session, crosses in [PlaneCrossMillis], and its trail fades over [PlaneTrailFadeMillis]. */
internal const val PlaneStartMillis = 4_000L
internal const val PlaneCrossMillis = 18_000L
internal const val PlaneTrailFadeMillis = 9_000L
