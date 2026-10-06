package dev.sebastiano.clockblocker.opus.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp

/**
 * Every colour the theme hands out, as one value so a theme switch (Night-safe on/off, light/dark, Opus mode) can
 * cross-fade all of it together instead of cutting from black to bright in one frame.
 */
@Immutable
internal data class ThemePalette(
    val colorScheme: ColorScheme,
    val adviceColors: AdviceColors,
    val sky: SkyPalette,
    val art: ArtColors,
) {
    fun lerp(to: ThemePalette, t: Float): ThemePalette = when {
        t <= 0f -> this
        t >= 1f -> to
        else -> ThemePalette(
            colorScheme = colorScheme.lerp(to.colorScheme, t),
            adviceColors = adviceColors.lerp(to.adviceColors, t),
            sky = sky.lerp(to.sky, t),
            art = art.lerp(to.art, t),
        )
    }
}

/**
 * Cross-fades between [ThemePalette]s whenever [target] changes identity. The first composition shows [target]
 * directly (nothing to fade from). Recomposes the themed subtree per frame while fading, which is acceptable
 * because it happens a few times a day at most (see MOTION.md, "Night-safe cross-fade").
 */
@Composable
internal fun animateThemePalette(target: ThemePalette, spec: AnimationSpec<Float>): ThemePalette {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target === to) return@LaunchedEffect
        // Interrupted mid-fade: continue from what is on screen right now.
        from = from.lerp(to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, spec)
    }
    val t = progress.value
    return if (t >= 1f) to else from.lerp(to, t)
}

internal fun ColorScheme.lerp(to: ColorScheme, t: Float): ColorScheme = to.copy(
    primary = lerp(primary, to.primary, t),
    onPrimary = lerp(onPrimary, to.onPrimary, t),
    primaryContainer = lerp(primaryContainer, to.primaryContainer, t),
    onPrimaryContainer = lerp(onPrimaryContainer, to.onPrimaryContainer, t),
    inversePrimary = lerp(inversePrimary, to.inversePrimary, t),
    secondary = lerp(secondary, to.secondary, t),
    onSecondary = lerp(onSecondary, to.onSecondary, t),
    secondaryContainer = lerp(secondaryContainer, to.secondaryContainer, t),
    onSecondaryContainer = lerp(onSecondaryContainer, to.onSecondaryContainer, t),
    tertiary = lerp(tertiary, to.tertiary, t),
    onTertiary = lerp(onTertiary, to.onTertiary, t),
    tertiaryContainer = lerp(tertiaryContainer, to.tertiaryContainer, t),
    onTertiaryContainer = lerp(onTertiaryContainer, to.onTertiaryContainer, t),
    background = lerp(background, to.background, t),
    onBackground = lerp(onBackground, to.onBackground, t),
    surface = lerp(surface, to.surface, t),
    onSurface = lerp(onSurface, to.onSurface, t),
    surfaceVariant = lerp(surfaceVariant, to.surfaceVariant, t),
    onSurfaceVariant = lerp(onSurfaceVariant, to.onSurfaceVariant, t),
    surfaceTint = lerp(surfaceTint, to.surfaceTint, t),
    inverseSurface = lerp(inverseSurface, to.inverseSurface, t),
    inverseOnSurface = lerp(inverseOnSurface, to.inverseOnSurface, t),
    error = lerp(error, to.error, t),
    onError = lerp(onError, to.onError, t),
    errorContainer = lerp(errorContainer, to.errorContainer, t),
    onErrorContainer = lerp(onErrorContainer, to.onErrorContainer, t),
    outline = lerp(outline, to.outline, t),
    outlineVariant = lerp(outlineVariant, to.outlineVariant, t),
    scrim = lerp(scrim, to.scrim, t),
    surfaceBright = lerp(surfaceBright, to.surfaceBright, t),
    surfaceContainer = lerp(surfaceContainer, to.surfaceContainer, t),
    surfaceContainerHigh = lerp(surfaceContainerHigh, to.surfaceContainerHigh, t),
    surfaceContainerHighest = lerp(surfaceContainerHighest, to.surfaceContainerHighest, t),
    surfaceContainerLow = lerp(surfaceContainerLow, to.surfaceContainerLow, t),
    surfaceContainerLowest = lerp(surfaceContainerLowest, to.surfaceContainerLowest, t),
    surfaceDim = lerp(surfaceDim, to.surfaceDim, t),
)

internal fun ArtColors.lerp(to: ArtColors, t: Float): ArtColors = ArtColors(
    primary = lerp(primary, to.primary, t),
    primaryContainer = lerp(primaryContainer, to.primaryContainer, t),
    secondaryContainer = lerp(secondaryContainer, to.secondaryContainer, t),
    tertiary = lerp(tertiary, to.tertiary, t),
    tertiaryContainer = lerp(tertiaryContainer, to.tertiaryContainer, t),
    surface = lerp(surface, to.surface, t),
    surfaceHighest = lerp(surfaceHighest, to.surfaceHighest, t),
    inverse = lerp(inverse, to.inverse, t),
    onInverse = lerp(onInverse, to.onInverse, t),
    ink = lerp(ink, to.ink, t),
    shadow = lerp(shadow, to.shadow, t),
    highlight = lerp(highlight, to.highlight, t),
)

internal fun AdviceColorRole.lerp(to: AdviceColorRole, t: Float): AdviceColorRole = AdviceColorRole(
    color = lerp(color, to.color, t),
    onColor = lerp(onColor, to.onColor, t),
    container = lerp(container, to.container, t),
    onContainer = lerp(onContainer, to.onContainer, t),
    outline = lerp(outline, to.outline, t),
)
