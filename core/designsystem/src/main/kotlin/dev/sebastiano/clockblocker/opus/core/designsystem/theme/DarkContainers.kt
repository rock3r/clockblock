package dev.sebastiano.clockblocker.opus.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** Oklab lightness above which a dark-theme container reads as a light surface (M3 dark containers sit near 0.37). */
private const val MaxDarkContainerLightness = 0.55f

/** Where a quietened container lands, and its text: roughly M3 tones 30 and 90 of the same hue. */
private const val QuietContainerLightness = 0.38f
private const val QuietOnContainerLightness = 0.92f

/**
 * Some wallpaper schemes (seen with the Expressive dynamic variant on Pixel) give dark-theme *containers* a light,
 * saturated tone: a card or FAB in `primaryContainer` then glares bright cyan on a near-black screen, dark text
 * sits on it, and anything drawn in `primary` on top (wavy lines, art) disappears.
 *
 * This keeps every container the system made dark exactly as it is, and moves only the light ones down to a
 * dark tone of the **same hue** (so it still reads as the wallpaper's colour), with a light tone of that hue as
 * its content colour. Applied to dynamic dark schemes only; the static Opus palettes already follow M3 tones.
 */
internal fun ColorScheme.withQuietDarkContainers(): ColorScheme {
    val (primary, onPrimary) = quiet(primaryContainer, onPrimaryContainer)
    val (secondary, onSecondary) = quiet(secondaryContainer, onSecondaryContainer)
    val (tertiary, onTertiary) = quiet(tertiaryContainer, onTertiaryContainer)
    return copy(
        primaryContainer = primary,
        onPrimaryContainer = onPrimary,
        secondaryContainer = secondary,
        onSecondaryContainer = onSecondary,
        tertiaryContainer = tertiary,
        onTertiaryContainer = onTertiary,
    )
}

private fun quiet(container: Color, onContainer: Color): Pair<Color, Color> {
    if (ColorMath.lightness(container) <= MaxDarkContainerLightness) return container to onContainer
    // A dark tone can't hold the light tone's chroma; ease it so the result isn't neon.
    return ColorMath.withLightness(container, QuietContainerLightness, chromaFactor = 0.7f) to
        ColorMath.withLightness(container, QuietOnContainerLightness, chromaFactor = 0.5f)
}
