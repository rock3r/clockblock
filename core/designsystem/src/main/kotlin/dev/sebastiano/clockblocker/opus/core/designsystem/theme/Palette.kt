package dev.sebastiano.clockblocker.opus.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * "Dusk Instrument" raw palette. Hand-tuned tonal ramps (HCT-style tones) around the seed
 * **Twilight Indigo `#4F46E5`** with a **Marigold `#FFB000`** tertiary. Never reference these from feature code;
 * use `MaterialTheme.colorScheme`, [ClockblockTheme.adviceColors] or [ClockblockTheme.sky].
 */
internal object DuskPalette {
    val Seed = Color(0xFF4F46E5)
    val MarigoldSeed = Color(0xFFFFB000)

    // Primary: Twilight Indigo.
    val Indigo10 = Color(0xFF12006E)
    val Indigo20 = Color(0xFF2300A8)
    val Indigo30 = Color(0xFF3729C9)
    val Indigo40 = Color(0xFF4F46E5)
    val Indigo80 = Color(0xFFC3C0FF)
    val Indigo90 = Color(0xFFE2DFFF)

    // Secondary: dusk lavender-grey.
    val Dusk10 = Color(0xFF1A1838)
    val Dusk20 = Color(0xFF2F2D4E)
    val Dusk30 = Color(0xFF464466)
    val Dusk40 = Color(0xFF5E5C80)
    val Dusk80 = Color(0xFFC7C3EC)
    val Dusk90 = Color(0xFFE4DFFF)

    // Tertiary: Marigold.
    val Marigold10 = Color(0xFF271900)
    val Marigold20 = Color(0xFF422C00)
    val Marigold30 = Color(0xFF5F4100)
    val Marigold40 = Color(0xFF7D5700)
    val Marigold80 = Color(0xFFFFBA3B)
    val Marigold90 = Color(0xFFFFDEA0)

    // Neutral (indigo-tinted).
    val N4 = Color(0xFF0D0C18)
    val N6 = Color(0xFF12111D)
    val N10 = Color(0xFF1C1B27)
    val N12 = Color(0xFF201F2B)
    val N17 = Color(0xFF2A2936)
    val N20 = Color(0xFF31303D)
    val N22 = Color(0xFF363541)
    val N24 = Color(0xFF3A3946)
    val N87 = Color(0xFFDCD8E9)
    val N90 = Color(0xFFE5E1F2)
    val N92 = Color(0xFFEBE7F8)
    val N94 = Color(0xFFF1ECFD)
    val N95 = Color(0xFFF4EFFF)
    val N96 = Color(0xFFF7F2FF)
    val N98 = Color(0xFFFCF8FF)

    // Neutral variant.
    val NV30 = Color(0xFF474557)
    val NV50 = Color(0xFF787589)
    val NV60 = Color(0xFF9290A4)
    val NV80 = Color(0xFFC8C4DA)
    val NV90 = Color(0xFFE4E0F6)

    // Error.
    val Error40 = Color(0xFFBA1A1A)
    val Error80 = Color(0xFFFFB4AB)
    val Error90 = Color(0xFFFFDAD6)
    val Error10 = Color(0xFF410002)
    val Error20 = Color(0xFF690005)
    val Error30 = Color(0xFF93000A)

    // Night-safe: true black + dim amber (tones 30–45) so the screen itself respects "avoid light".
    val NightAmber = Color(0xFFA9640A)
    val NightAmberDim = Color(0xFF7A4A10)
    val NightAmberContainer = Color(0xFF2E1C06)
    val NightOnAmberContainer = Color(0xFFD9A76A)
    val NightInk = Color(0xFFB7A48D)
    val NightInkVariant = Color(0xFF8E7F71)
    val NightOutline = Color(0xFF4A3E33)

    // Opus concert hall: black + gold.
    val Gold = Color(0xFFD4AF37)
    val GoldContainer = Color(0xFF3A2E05)
    val OnGoldContainer = Color(0xFFF5E3A1)
    val Ivory = Color(0xFFF3EBD3)
    val Velvet = Color(0xFFE3A49F)
    val VelvetContainer = Color(0xFF5C1A1B)
}

internal val ClockblockLightColors: ColorScheme = with(DuskPalette) {
    lightColorScheme(
        primary = Indigo40,
        onPrimary = Color.White,
        primaryContainer = Indigo90,
        onPrimaryContainer = Indigo30,
        inversePrimary = Indigo80,
        secondary = Dusk40,
        onSecondary = Color.White,
        secondaryContainer = Dusk90,
        onSecondaryContainer = Dusk30,
        tertiary = Marigold40,
        onTertiary = Color.White,
        tertiaryContainer = Marigold90,
        onTertiaryContainer = Marigold30,
        background = N98,
        onBackground = N10,
        surface = N98,
        onSurface = N10,
        surfaceVariant = NV90,
        onSurfaceVariant = NV30,
        surfaceTint = Indigo40,
        inverseSurface = N20,
        inverseOnSurface = N95,
        error = Error40,
        onError = Color.White,
        errorContainer = Error90,
        onErrorContainer = Error30,
        outline = NV50,
        outlineVariant = NV80,
        scrim = Color.Black,
        surfaceBright = N98,
        surfaceDim = N87,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = N96,
        surfaceContainer = N94,
        surfaceContainerHigh = N92,
        surfaceContainerHighest = N90,
    )
}

internal val ClockblockDarkColors: ColorScheme = with(DuskPalette) {
    darkColorScheme(
        primary = Indigo80,
        onPrimary = Indigo20,
        primaryContainer = Indigo30,
        onPrimaryContainer = Indigo90,
        inversePrimary = Indigo40,
        secondary = Dusk80,
        onSecondary = Dusk20,
        secondaryContainer = Dusk30,
        onSecondaryContainer = Dusk90,
        tertiary = Marigold80,
        onTertiary = Marigold20,
        tertiaryContainer = Marigold30,
        onTertiaryContainer = Marigold90,
        background = N6,
        onBackground = N90,
        surface = N6,
        onSurface = N90,
        surfaceVariant = NV30,
        onSurfaceVariant = NV80,
        surfaceTint = Indigo80,
        inverseSurface = N90,
        inverseOnSurface = N20,
        error = Error80,
        onError = Error20,
        errorContainer = Error30,
        onErrorContainer = Error90,
        outline = NV60,
        outlineVariant = NV30,
        scrim = Color.Black,
        surfaceBright = N24,
        surfaceDim = N6,
        surfaceContainerLowest = N4,
        surfaceContainerLow = N10,
        surfaceContainer = N12,
        surfaceContainerHigh = N17,
        surfaceContainerHighest = N22,
    )
}

/** True black, dim amber, low-luminance ink. Used while the plan says Avoid light / Sleep. */
internal val NightSafeColors: ColorScheme = with(DuskPalette) {
    darkColorScheme(
        primary = NightAmber,
        onPrimary = Color.Black,
        primaryContainer = NightAmberContainer,
        onPrimaryContainer = NightOnAmberContainer,
        inversePrimary = NightAmberDim,
        secondary = NightAmberDim,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF1E140A),
        onSecondaryContainer = NightInk,
        tertiary = Color(0xFF9A4A2E),
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF2A120A),
        onTertiaryContainer = Color(0xFFC98C74),
        background = Color.Black,
        onBackground = NightInk,
        surface = Color.Black,
        onSurface = NightInk,
        surfaceVariant = Color(0xFF1A140F),
        onSurfaceVariant = NightInkVariant,
        surfaceTint = NightAmberDim,
        inverseSurface = NightInk,
        inverseOnSurface = Color.Black,
        error = Color(0xFFB0574E),
        onError = Color.Black,
        errorContainer = Color(0xFF2E0E0B),
        onErrorContainer = Color(0xFFD99A92),
        outline = NightOutline,
        outlineVariant = Color(0xFF2A221B),
        scrim = Color.Black,
        surfaceBright = Color(0xFF1A140F),
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF070503),
        surfaceContainer = Color(0xFF0C0906),
        surfaceContainerHigh = Color(0xFF120E0A),
        surfaceContainerHighest = Color(0xFF18130E),
    )
}

/** "Concert hall": black, gold, ivory and a velvet accent. The Opus easter-egg theme. */
internal val OpusConcertColors: ColorScheme = with(DuskPalette) {
    darkColorScheme(
        primary = Gold,
        onPrimary = Color(0xFF1E1600),
        primaryContainer = GoldContainer,
        onPrimaryContainer = OnGoldContainer,
        inversePrimary = Color(0xFF6E5700),
        secondary = Color(0xFFCDBE93),
        onSecondary = Color(0xFF2B2410),
        secondaryContainer = Color(0xFF2A2412),
        onSecondaryContainer = Color(0xFFEADFBC),
        tertiary = Velvet,
        onTertiary = Color(0xFF3B0A0B),
        tertiaryContainer = VelvetContainer,
        onTertiaryContainer = Color(0xFFFFDAD6),
        background = Color.Black,
        onBackground = Ivory,
        surface = Color.Black,
        onSurface = Ivory,
        surfaceVariant = Color(0xFF1F1B12),
        onSurfaceVariant = Color(0xFFCFC5AA),
        surfaceTint = Gold,
        inverseSurface = Ivory,
        inverseOnSurface = Color(0xFF1A1710),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8C8064),
        outlineVariant = Color(0xFF3A3424),
        scrim = Color.Black,
        surfaceBright = Color(0xFF26221A),
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0B0A07),
        surfaceContainer = Color(0xFF12100B),
        surfaceContainerHigh = Color(0xFF1A1710),
        surfaceContainerHighest = Color(0xFF221F16),
    )
}
