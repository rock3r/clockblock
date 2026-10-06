package dev.sebastiano.clockblocker.opus.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Which flavour of the theme is active. */
enum class ClockblockThemeVariant {
    Standard,

    /** True black, dim amber, calm motion: the screen itself respects "avoid light". */
    NightSafe,

    /** Easter egg: "Opus No. 1 in Jet-Lag Minor", concert-hall black and gold, Fraunces everywhere. */
    Opus,
}

/**
 * Colour roles for illustrations (design.md §2.4 Illustration style): 2–3 flat fills from the colour scheme, one
 * accent, a darker "cut-paper" offset shadow and an ink for 2 dp detail strokes.
 */
@Immutable
data class ArtColors(
    val primary: Color,
    val primaryContainer: Color,
    val secondaryContainer: Color,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val surface: Color,
    val surfaceHighest: Color,
    val inverse: Color,
    val onInverse: Color,
    val ink: Color,
    val shadow: Color,
    val highlight: Color,
)

internal fun artColorsFor(scheme: ColorScheme, variant: ClockblockThemeVariant, dark: Boolean): ArtColors = ArtColors(
    primary = scheme.primary,
    primaryContainer = scheme.primaryContainer,
    secondaryContainer = scheme.secondaryContainer,
    tertiary = if (variant == ClockblockThemeVariant.Standard) {
        if (dark) scheme.tertiary else ColorMath.mix(scheme.tertiary, DuskPalette.MarigoldSeed, 0.75f)
    } else {
        scheme.primary
    },
    tertiaryContainer = scheme.tertiaryContainer,
    surface = scheme.surface,
    surfaceHighest = scheme.surfaceContainerHighest,
    inverse = if (variant == ClockblockThemeVariant.NightSafe) scheme.surfaceContainerHighest else scheme.inverseSurface,
    onInverse = if (variant == ClockblockThemeVariant.NightSafe) scheme.onSurfaceVariant else scheme.inverseOnSurface,
    ink = scheme.onSurface.copy(alpha = 0.8f),
    shadow = if (dark) Color.Black.copy(alpha = 0.45f) else scheme.onSurface.copy(alpha = 0.14f),
    highlight = Color.White.copy(alpha = if (variant == ClockblockThemeVariant.NightSafe) 0.12f else 0.3f),
)

/** Shapes: cards stay rounded rectangles (28 dp extra-large); MaterialShapes are reserved for meaning. */
val ClockblockShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The Now card is the most emphasised surface: one step rounder than the rest. */
val NowCardShape = RoundedCornerShape(32.dp)

@Immutable
private data class ClockblockExtras(
    val variant: ClockblockThemeVariant,
    val dark: Boolean,
    val adviceColors: AdviceColors,
    val sky: SkyPalette,
    val text: ClockblockTextStyles,
    val motion: ClockblockMotion,
    val art: ArtColors,
)

private val LocalClockblockExtras = staticCompositionLocalOf {
    ClockblockExtras(
        variant = ClockblockThemeVariant.Standard,
        dark = false,
        adviceColors = AdviceColors.Light,
        sky = SkyPalette.Default,
        text = DefaultClockblockTextStyles,
        motion = ClockblockMotion(MotionScheme.expressive(), reduceMotion = false),
        art = artColorsFor(ClockblockLightColors, ClockblockThemeVariant.Standard, dark = false),
    )
}

/** The caller-facing parameters of the innermost [ClockblockTheme], so nested themes can inherit them. */
@Immutable
private data class ClockblockThemeParams(
    val darkTheme: Boolean,
    val dynamicColor: Boolean,
    val opusMode: Boolean,
    val reduceMotion: Boolean,
    val calmMotion: Boolean,
)

private val LocalClockblockThemeParams = staticCompositionLocalOf<ClockblockThemeParams?> { null }

/**
 * The Clockblock theme: `MaterialExpressiveTheme` + Dusk Instrument colours, Google Sans Flex type, the
 * semantic advice palette, the sky ramp and motion tokens.
 *
 * Precedence: [nightSafe] wins over [opusMode] (it is functional), which wins over dynamic/static colour.
 * Any change of colours (Night-safe, light/dark, Opus mode) cross-fades on [ClockblockMotion.themeCrossFade] rather
 * than cutting, and keeps the call shape stable so callers never rebuild their subtree to switch.
 *
 * @param dynamicColor wallpaper colours on Android 12+; advice colours are harmonised and skies blended 15 %
 *   towards the dynamic primary so they still belong.
 * @param nightSafe true black, dim amber, calm motion, dimmed illustrations (plan says Avoid light / Sleep).
 * @param opusMode the concert-hall easter-egg theme.
 * @param reduceMotion the in-app toggle; combined with the system "Remove animations" setting into
 *   [LocalReduceMotion].
 * @param calmMotion "syrupy by night": use [CalmMotionScheme] whatever the colours (the shell sets it while the
 *   body clock is in its night). Night-safe implies it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ClockblockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    nightSafe: Boolean = false,
    opusMode: Boolean = false,
    reduceMotion: Boolean = false,
    calmMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val variant = when {
        nightSafe -> ClockblockThemeVariant.NightSafe
        opusMode -> ClockblockThemeVariant.Opus
        else -> ClockblockThemeVariant.Standard
    }
    val dark = darkTheme || variant != ClockblockThemeVariant.Standard
    val context = LocalContext.current
    val useDynamic = dynamicColor && variant == ClockblockThemeVariant.Standard && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val systemAnimations = rememberSystemAnimationsEnabled()
    val reduce = reduceMotion || !systemAnimations
    val motionScheme = when {
        reduce -> StillMotionScheme
        variant == ClockblockThemeVariant.NightSafe || calmMotion -> CalmMotionScheme
        else -> MotionScheme.expressive()
    }
    val targetPalette = remember(variant, dark, useDynamic, context) {
        val colorScheme = when {
            variant == ClockblockThemeVariant.NightSafe -> NightSafeColors
            variant == ClockblockThemeVariant.Opus -> OpusConcertColors
            useDynamic && dark -> dynamicDarkColorScheme(context).withQuietDarkContainers()
            useDynamic -> dynamicLightColorScheme(context)
            dark -> ClockblockDarkColors
            else -> ClockblockLightColors
        }
        val baseAdvice = if (dark) AdviceColors.Dark else AdviceColors.Light
        val advice = when (variant) {
            ClockblockThemeVariant.NightSafe -> AdviceColors.Dark.dimmed()
            ClockblockThemeVariant.Opus -> AdviceColors.Dark.harmonizedWith(colorScheme.primary)
            ClockblockThemeVariant.Standard -> if (useDynamic) baseAdvice.harmonizedWith(colorScheme.primary) else baseAdvice
        }
        val sky = when {
            variant == ClockblockThemeVariant.NightSafe -> SkyPalette.Default.dimmed()
            variant == ClockblockThemeVariant.Opus -> SkyPalette.Default.blendedToward(colorScheme.primary, 0.15f).forDarkTheme()
            useDynamic -> SkyPalette.Default.blendedToward(colorScheme.primary, 0.15f).let { if (dark) it.forDarkTheme() else it }
            dark -> SkyPalette.Default.forDarkTheme()
            else -> SkyPalette.Default
        }
        ThemePalette(colorScheme, advice, sky, artColorsFor(colorScheme, variant, dark))
    }
    val motion = remember(motionScheme, reduce) { ClockblockMotion(motionScheme, reduce) }
    val palette = animateThemePalette(targetPalette, motion.themeCrossFade())
    val extras = remember(variant, dark, palette, motion) {
        ClockblockExtras(
            variant = variant,
            dark = dark,
            adviceColors = palette.adviceColors,
            sky = palette.sky,
            text = if (variant == ClockblockThemeVariant.Opus) OpusConcertTextStyles else DefaultClockblockTextStyles,
            motion = motion,
            art = palette.art,
        )
    }
    val params = ClockblockThemeParams(darkTheme, dynamicColor, opusMode, reduceMotion, calmMotion)
    CompositionLocalProvider(
        LocalClockblockExtras provides extras,
        LocalReduceMotion provides reduce,
        LocalClockblockThemeParams provides params,
    ) {
        MaterialExpressiveTheme(
            colorScheme = palette.colorScheme,
            motionScheme = motionScheme,
            shapes = ClockblockShapes,
            typography = if (variant == ClockblockThemeVariant.Opus) OpusConcertTypography else ClockblockTypography,
            content = content,
        )
    }
}

/**
 * Re-themes [content] Night-safe (or back) while inheriting every other parameter of the enclosing [ClockblockTheme].
 * Always call it with the same shape (toggle [nightSafe], never wrap conditionally) so the subtree keeps its
 * state and the colours cross-fade.
 */
@Composable
fun NightSafeTheme(nightSafe: Boolean, content: @Composable () -> Unit) {
    val outer = LocalClockblockThemeParams.current
    if (outer == null) {
        ClockblockTheme(nightSafe = nightSafe, content = content)
    } else {
        ClockblockTheme(
            darkTheme = outer.darkTheme,
            dynamicColor = outer.dynamicColor,
            nightSafe = nightSafe,
            opusMode = outer.opusMode,
            reduceMotion = outer.reduceMotion,
            calmMotion = outer.calmMotion,
            content = content,
        )
    }
}

/** Accessors for the Clockblock-specific parts of the theme. */
object ClockblockTheme {
    val variant: ClockblockThemeVariant
        @Composable @ReadOnlyComposable get() = LocalClockblockExtras.current.variant

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalClockblockExtras.current.dark

    val adviceColors: AdviceColors
        @Composable @ReadOnlyComposable get() = LocalClockblockExtras.current.adviceColors

    val sky: SkyPalette
        @Composable @ReadOnlyComposable get() = LocalClockblockExtras.current.sky

    /** Time, body-clock and editorial styles beyond the M3 scale (`MaterialTheme.typography`). */
    val textStyles: ClockblockTextStyles
        @Composable @ReadOnlyComposable get() = LocalClockblockExtras.current.text

    val motion: ClockblockMotion
        @Composable @ReadOnlyComposable get() = LocalClockblockExtras.current.motion

    val artColors: ArtColors
        @Composable @ReadOnlyComposable get() = LocalClockblockExtras.current.art

    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current

    /** The 8-bit easter egg is on ([EightBitMode]): glyphs go pixel, skies band, times go monospace. */
    val pixelMode: Boolean
        @Composable @ReadOnlyComposable get() = LocalPixelMode.current

    /** Convenience: `MaterialTheme.colorScheme`. */
    val colorScheme: ColorScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme
}

private val LocalPixelMode = staticCompositionLocalOf { false }

/**
 * The 8-bit easter egg (MOTION.md "Konami 8-bit"): while [enabled], advice glyphs morph to `PixelCircle` /
 * `PixelTriangle`, body-clock skies quantise into bands and time styles switch to a monospace face. Call it with
 * a stable shape (toggle [enabled]) so the subtree keeps its state and the glyphs can morph rather than swap.
 */
@Composable
fun EightBitMode(enabled: Boolean, content: @Composable () -> Unit) {
    val extras = LocalClockblockExtras.current
    val text = remember(extras.text, enabled) { if (enabled) extras.text.pixelated() else extras.text }
    val pixelExtras = remember(extras, text) { extras.copy(text = text) }
    CompositionLocalProvider(LocalClockblockExtras provides pixelExtras, LocalPixelMode provides enabled, content = content)
}

private fun ClockblockTextStyles.pixelated(): ClockblockTextStyles {
    fun TextStyle.mono() = copy(fontFamily = FontFamily.Monospace, fontStyle = FontStyle.Normal, letterSpacing = 0.sp)
    return copy(
        timeDisplay = timeDisplay.mono(),
        timeDisplayEmphasized = timeDisplayEmphasized.mono(),
        timeHeadline = timeHeadline.mono(),
        timeTitle = timeTitle.mono(),
        timeLabel = timeLabel.mono(),
        bodyClockDisplay = bodyClockDisplay.mono(),
        bodyClockTitle = bodyClockTitle.mono(),
        bodyClockLabel = bodyClockLabel.mono(),
    )
}
