package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import kotlin.math.pow

/**
 * The plan's dial ↔ world zoom at one point of its progress: a camera pulling back from "your day" to "the world".
 *
 * At 0 the dial fills its place and the globe is hidden, zoomed in [zoomIn] times on where the traveller is (the
 * marker), so that spot sits right under the dial's centre. As the progress grows the camera pulls back at an even
 * pace on a log scale: the globe shrinks to its full view while the dial, pinned to the marker as if it were drawn on
 * the Earth, shrinks with it towards the marker's place on the globe and fades. The globe's labels and caption (the
 * chrome) come in last, once the Earth has settled.
 *
 * Offsets are in units of the globe's radius from the centre of the view, x right and y down. The globe draws its
 * marker at `globeTranslation + globeScale × marker`, which is always [dialCentre].
 *
 * [still] (reduce motion): nothing scales or moves; the dial and the globe only cross-fade in place.
 */
@Immutable
data class WorldCamera(
    val globeScale: Float,
    val globeTranslation: Offset,
    val globeAlpha: Float,
    val dialScale: Float,
    val dialCentre: Offset,
    val dialAlpha: Float,
    val chromeAlpha: Float,
) {
    companion object {
        /** How far in the globe starts, so the traveller's patch of Earth roughly fills the dial's face. */
        const val DefaultZoomIn: Float = 4f

        fun at(progress: Float, marker: Offset, zoomIn: Float = DefaultZoomIn, still: Boolean = false): WorldCamera {
            val p = progress.coerceIn(0f, 1f)
            val dialAlpha = 1f - smoothstep(0f, 0.6f, p)
            val globeAlpha = smoothstep(0f, 0.45f, p)
            val chromeAlpha = smoothstep(0.75f, 1f, p)
            if (still) {
                return WorldCamera(1f, Offset.Zero, globeAlpha, 1f, Offset.Zero, dialAlpha, chromeAlpha)
            }
            val scale = zoomIn.pow(1f - p)
            return WorldCamera(
                globeScale = scale,
                globeTranslation = marker * (p - scale),
                globeAlpha = globeAlpha,
                dialScale = scale / zoomIn,
                dialCentre = marker * p,
                dialAlpha = dialAlpha,
                chromeAlpha = chromeAlpha,
            )
        }

        private fun smoothstep(from: Float, to: Float, x: Float): Float {
            val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}
