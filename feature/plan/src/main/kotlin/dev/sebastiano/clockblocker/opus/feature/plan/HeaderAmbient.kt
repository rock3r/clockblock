package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext

/**
 * The header sky's slow ambient life: now and then (an ambient *session*), a few stars breathe in and out at night and
 * a small plane crosses with a contrail that fades. [sessionMillis] is how far into the current session it is, or -1
 * at rest; it is only read in the draw phase, so a session redraws the header without recomposing anything.
 *
 * The plan screen is opened many times a day and stays on screen, so the sky is still almost all the time: one session
 * shortly after the header appears, then one every [AmbientRestMillis]. Nothing here carries meaning (the sky, the
 * sun and the words do), so at rest, under Reduce motion, in sleep windows or with animations removed it simply
 * doesn't happen.
 */
@Stable
internal class HeaderAmbient {
    var sessionMillis by mutableLongStateOf(-1L)
}

/**
 * The header's [HeaderAmbient], running sessions while [enabled]. Self-timed: every wait and the session clock follow
 * [MotionDurationScale], and with animations removed (scale 0) no session ever starts. [preview] pins the session at a
 * fixed moment instead (screenshot tests).
 */
@Composable
internal fun rememberHeaderAmbient(enabled: Boolean, preview: Long? = null): HeaderAmbient {
    val ambient = remember { HeaderAmbient() }
    if (preview != null) {
        SideEffect { ambient.sessionMillis = preview }
        return ambient
    }
    LaunchedEffect(enabled) {
        ambient.sessionMillis = -1L
        if (!enabled) return@LaunchedEffect
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (scale <= 0f) return@LaunchedEffect
        try {
            delay((AmbientFirstDelayMillis * scale).toLong())
            while (true) {
                val start = withFrameMillis { it }
                var elapsed = 0L
                while (elapsed < AmbientSessionMillis) {
                    elapsed = withFrameMillis { ((it - start) / scale).toLong() }
                    ambient.sessionMillis = elapsed.coerceAtMost(AmbientSessionMillis)
                }
                ambient.sessionMillis = -1L
                delay((AmbientRestMillis * scale).toLong())
            }
        } finally {
            ambient.sessionMillis = -1L
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

/** The first session starts this long after the header appears (not at once: opening the screen stays calm). */
internal const val AmbientFirstDelayMillis = 2_500L

/** How long a session lasts, and the rest between two. */
internal const val AmbientSessionMillis = 45_000L
internal const val AmbientRestMillis = 240_000L

/** The plane takes off this far into a session, crosses in [PlaneCrossMillis], and its trail fades over [PlaneTrailFadeMillis]. */
internal const val PlaneStartMillis = 4_000L
internal const val PlaneCrossMillis = 18_000L
internal const val PlaneTrailFadeMillis = 9_000L
