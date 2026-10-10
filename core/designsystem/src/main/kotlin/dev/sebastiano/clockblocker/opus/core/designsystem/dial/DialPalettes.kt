package dev.sebastiano.clockblocker.opus.core.designsystem.dial

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.Argb
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialAdviceColors
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialPalette
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialSkyColors
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.AdviceColors
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockDarkColors
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockLightColors
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockThemeVariant
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ColorMath
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.NightSafeColors
import dev.sebastiano.clockblocker.opus.core.model.AdviceType

/**
 * The dial's palettes, resolved from the theme into the renderer-agnostic [DialPalette].
 *
 * The two skies use their own keyframes rather than the header's sky ramp: they are data (which half is night),
 * so they keep fixed hues under dynamic colour, and their lightness gap is tuned so the ring labels read on both
 * halves and day/night separate for every kind of colour vision.
 */
object DialPalettes {
    val LightSky = DialSkyColors(
        night = Argb.of(0xFF1E2461),
        twilight = Argb.of(0xFF7458A6),
        dawn = Argb.of(0xFFF7A27C),
        day = Argb.of(0xFFA6D2FF),
        dusk = Argb.of(0xFFFFAE70),
        onDay = Argb.of(0xFF0D1A3C),
        onNight = Argb.of(0xFFE6E8FF),
    )

    val DarkSky = DialSkyColors(
        night = Argb.of(0xFF2E3680),
        twilight = Argb.of(0xFF5E4D94),
        dawn = Argb.of(0xFFD08A66),
        day = Argb.of(0xFF7FA9DC),
        dusk = Argb.of(0xFFD49460),
        onDay = Argb.of(0xFF0A1530),
        onNight = Argb.of(0xFFE6E8FF),
    )

    /** The light theme's dial (static colour): previews, tests and tools. */
    val Light: DialPalette by lazy { of(ClockblockLightColors, AdviceColors.Light, ClockblockThemeVariant.Standard, dark = false) }

    /** The dark theme's dial (static colour). */
    val Dark: DialPalette by lazy { of(ClockblockDarkColors, AdviceColors.Dark, ClockblockThemeVariant.Standard, dark = true) }

    /** The night-safe dial (true black, dim amber, sunk skies): what widgets use under "Night-safe automatically". */
    val NightSafe: DialPalette by lazy {
        of(NightSafeColors, AdviceColors.Dark.dimmed(), ClockblockThemeVariant.NightSafe, dark = true)
    }

    /** The dial for a resolved theme. Night-safe sinks the skies and their inks so the dial respects "avoid light". */
    fun of(scheme: ColorScheme, advice: AdviceColors, variant: ClockblockThemeVariant, dark: Boolean): DialPalette {
        val nightSafe = variant == ClockblockThemeVariant.NightSafe
        val sky = when {
            nightSafe -> DarkSky.let { s ->
                fun dim(c: Argb) = Argb(ColorMath.dim(Color(c.value), 0.5f, 0.5f).toArgb())
                DialSkyColors(
                    night = dim(s.night),
                    twilight = dim(s.twilight),
                    dawn = dim(s.dawn),
                    day = dim(s.day),
                    dusk = dim(s.dusk),
                    onDay = scheme.onSurface.argb,
                    onNight = scheme.onSurface.argb,
                )
            }
            dark -> DarkSky
            else -> LightSky
        }
        return DialPalette(
            dark = dark,
            face = (if (dark) scheme.surfaceContainerLow else scheme.surfaceContainerLowest).argb,
            ink = scheme.onSurface.argb,
            inkMuted = scheme.onSurfaceVariant.argb,
            hairline = scheme.outlineVariant.argb,
            body = scheme.primary.argb,
            bodyContainer = scheme.primaryContainer.argb,
            onBodyContainer = scheme.onPrimaryContainer.argb,
            sky = sky,
            advice = AdviceType.entries.associateWith { type ->
                val role = advice[type]
                DialAdviceColors(role.color.argb, role.onColor.argb, role.container.argb)
            },
        )
    }

    private val Color.argb: Argb get() = Argb(toArgb())
}

/** The dial palette for the current theme. */
@Composable
fun rememberDialPalette(): DialPalette {
    val scheme = MaterialTheme.colorScheme
    val advice = ClockblockTheme.adviceColors
    val variant = ClockblockTheme.variant
    val dark = ClockblockTheme.isDark
    return remember(scheme, advice, variant, dark) { DialPalettes.of(scheme, advice, variant, dark) }
}
