package dev.sebastiano.clockblocker.opus.core.testing

import android.annotation.SuppressLint
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeTonalSpot

/** How the system turns a wallpaper seed into a scheme: Pixel's default and its "Expressive" style. */
enum class SeedStyle { TonalSpot, Expressive }

/**
 * Wallpaper seeds that stand for the dynamic palettes people actually get: one per broad hue family, so a colour
 * audit covers what dynamic colour can do to the surfaces and the harmonised advice colours.
 */
object WallpaperSeeds {
    val Purple = Color(0xFF6750A4)
    val Blue = Color(0xFF1B6EF3)
    val Green = Color(0xFF3B8A3E)
    val Orange = Color(0xFFE8710A)

    val all: List<Pair<String, Color>> = listOf("purple" to Purple, "blue" to Blue, "green" to Green, "orange" to Orange)
}

/**
 * The colour scheme Android's dynamic colour builds from a wallpaper [seed] (material-color-utilities, the same
 * library the system uses), for tests that need to check colour roles under wallpapers the test device doesn't have.
 */
@SuppressLint("RestrictedApi")
fun seedColorScheme(seed: Color, dark: Boolean, style: SeedStyle = SeedStyle.TonalSpot): ColorScheme {
    val hct = Hct.fromInt(seed.toArgb())
    val scheme: DynamicScheme = when (style) {
        SeedStyle.TonalSpot -> SchemeTonalSpot(hct, dark, 0.0)
        SeedStyle.Expressive -> SchemeExpressive(hct, dark, 0.0)
    }
    val m = MaterialDynamicColors()
    fun DynamicColor.c() = Color(getArgb(scheme))
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = m.primary().c(),
        onPrimary = m.onPrimary().c(),
        primaryContainer = m.primaryContainer().c(),
        onPrimaryContainer = m.onPrimaryContainer().c(),
        inversePrimary = m.inversePrimary().c(),
        secondary = m.secondary().c(),
        onSecondary = m.onSecondary().c(),
        secondaryContainer = m.secondaryContainer().c(),
        onSecondaryContainer = m.onSecondaryContainer().c(),
        tertiary = m.tertiary().c(),
        onTertiary = m.onTertiary().c(),
        tertiaryContainer = m.tertiaryContainer().c(),
        onTertiaryContainer = m.onTertiaryContainer().c(),
        background = m.background().c(),
        onBackground = m.onBackground().c(),
        surface = m.surface().c(),
        onSurface = m.onSurface().c(),
        surfaceVariant = m.surfaceVariant().c(),
        onSurfaceVariant = m.onSurfaceVariant().c(),
        surfaceTint = m.surfaceTint().c(),
        inverseSurface = m.inverseSurface().c(),
        inverseOnSurface = m.inverseOnSurface().c(),
        error = m.error().c(),
        onError = m.onError().c(),
        errorContainer = m.errorContainer().c(),
        onErrorContainer = m.onErrorContainer().c(),
        outline = m.outline().c(),
        outlineVariant = m.outlineVariant().c(),
        scrim = m.scrim().c(),
        surfaceBright = m.surfaceBright().c(),
        surfaceDim = m.surfaceDim().c(),
        surfaceContainerLowest = m.surfaceContainerLowest().c(),
        surfaceContainerLow = m.surfaceContainerLow().c(),
        surfaceContainer = m.surfaceContainer().c(),
        surfaceContainerHigh = m.surfaceContainerHigh().c(),
        surfaceContainerHighest = m.surfaceContainerHighest().c(),
    )
}
