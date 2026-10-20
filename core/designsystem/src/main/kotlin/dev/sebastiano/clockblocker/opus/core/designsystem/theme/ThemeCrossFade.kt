package dev.sebastiano.clockblocker.opus.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.CancellationException

/**
 * Every colour the theme hands out, as one value so a theme switch (Night-safe on/off, light/dark, Opus mode)
 * changes all of it together.
 */
@Immutable
internal data class ThemePalette(
    val colorScheme: ColorScheme,
    val adviceColors: AdviceColors,
    val sky: SkyPalette,
    val art: ArtColors,
)

/**
 * Cross-fades the themed [content] whenever [target] changes identity, without recomposing it per frame: the
 * screen as it looks now is captured once, the new palette is applied in a single recomposition, and the capture
 * fades out on top of it ([spec], read in its layer only). The first composition shows [target] directly (nothing
 * to fade from); a capture that can't be taken (not attached, zero size) just switches.
 *
 * Lerping the palette instead recomposed the whole app on every frame of the fade (23–29 dropped frames per switch on
 * a Pixel 7 Pro). The capture costs two frames of latency before the switch starts.
 */
@Composable
internal fun ThemeCrossFade(target: ThemePalette, spec: AnimationSpec<Float>, content: @Composable (ThemePalette) -> Unit) {
    val state = remember { ThemeCrossFadeState(target) }
    val layer = rememberGraphicsLayer()
    LaunchedEffect(target) {
        if (target === state.shown) return@LaunchedEffect
        val capture = state.capture(layer)
        state.shown = target
        if (capture == null) return@LaunchedEffect
        state.snapshot = capture
        try {
            state.alpha.snapTo(1f)
            state.alpha.animateTo(0f, spec)
        } finally {
            // Interrupted by a newer switch: that one captured this fade as it stood (overlay included).
            if (state.snapshot === capture) state.snapshot = null
        }
    }
    Box(
        Modifier.drawWithContent {
            // Read so a capture request redraws this node (and only this one: content and overlay have layers).
            state.captureRequests
            if (state.captureWanted) {
                layer.record { this@drawWithContent.drawContent() }
                state.captureWanted = false
                state.captured = true
                drawLayer(layer)
            } else {
                drawContent()
            }
        },
        propagateMinConstraints = true,
    ) {
        Box(Modifier.graphicsLayer(), propagateMinConstraints = true) { content(state.shown) }
        CrossFadeOverlay(state, Modifier.matchParentSize())
    }
}

@Composable
private fun CrossFadeOverlay(state: ThemeCrossFadeState, modifier: Modifier) {
    val snapshot = state.snapshot ?: return
    Spacer(modifier.graphicsLayer { alpha = state.alpha.value }.drawBehind { drawImage(snapshot) })
}

@Stable
private class ThemeCrossFadeState(initial: ThemePalette) {
    /** The palette the tree is themed with. */
    var shown: ThemePalette by mutableStateOf(initial)

    /** The look before the latest switch, fading out over [shown]. */
    var snapshot: ImageBitmap? by mutableStateOf(null)
    val alpha = Animatable(0f)

    /** Bumped to redraw the host when a capture is wanted. */
    var captureRequests by mutableIntStateOf(0)

    /** Plain flags, handed between the effect and the draw pass. */
    var captureWanted = false
    var captured = false

    /** The host's current look (the previous palette, and any fade still running), or null when it can't be taken. */
    suspend fun capture(layer: GraphicsLayer): ImageBitmap? {
        captured = false
        captureWanted = true
        captureRequests++
        // The draw that records runs after the frame callbacks: wait for it (a host that isn't drawn gives up).
        repeat(MaxCaptureFrames) {
            withFrameNanos { }
            if (captured) {
                return try {
                    layer.toImageBitmap()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        }
        captureWanted = false
        return null
    }
}

private const val MaxCaptureFrames = 3
