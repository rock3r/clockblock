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
enum class OpusThemeVariant {
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

internal fun artColorsFor(scheme: ColorScheme, variant: OpusThemeVariant, dark: Boolean): ArtColors = ArtColors(
    primary = scheme.primary,
    primaryContainer = scheme.primaryContainer,
    secondaryContainer = scheme.secondaryContainer,
    tertiary = if (variant == OpusThemeVariant.Standard) {
        if (dark) scheme.tertiary else ColorMath.mix(scheme.tertiary, DuskPalette.MarigoldSeed, 0.75f)
    } else {
        scheme.primary
    },
    tertiaryContainer = scheme.tertiaryContainer,
    surface = scheme.surface,
    surfaceHighest = scheme.surfaceContainerHighest,
    inverse = if (variant == OpusThemeVariant.NightSafe) scheme.surfaceContainerHighest else scheme.inverseSurface,
    onInverse = if (variant == OpusThemeVariant.NightSafe) scheme.onSurfaceVariant else scheme.inverseOnSurface,
    ink = scheme.onSurface.copy(alpha = 0.8f),
    shadow = if (dark) Color.Black.copy(alpha = 0.45f) else scheme.onSurface.copy(alpha = 0.14f),
    highlight = Color.White.copy(alpha = if (variant == OpusThemeVariant.NightSafe) 0.12f else 0.3f),
)

/** Shapes: cards stay rounded rectangles (28 dp extra-large); MaterialShapes are reserved for meaning. */
val OpusShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The Now card is the most emphasised surface: one step rounder than the rest. */
val NowCardShape = RoundedCornerShape(32.dp)

@Immutable
private data class OpusExtras(
    val variant: OpusThemeVariant,
    val dark: Boolean,
    val adviceColors: AdviceColors,
    val sky: SkyPalette,
    val text: OpusTextStyles,
    val motion: OpusMotion,
    val art: ArtColors,
)

private val LocalOpusExtras = staticCompositionLocalOf {
    OpusExtras(
        variant = OpusThemeVariant.Standard,
        dark = false,
        adviceColors = AdviceColors.Light,
        sky = SkyPalette.Default,
        text = DefaultOpusTextStyles,
        motion = OpusMotion(MotionScheme.expressive(), reduceMotion = false),
        art = artColorsFor(OpusLightColors, OpusThemeVariant.Standard, dark = false),
    )
}

/** The caller-facing parameters of the innermost [OpusTheme], so nested themes can inherit them. */
@Immutable
private data class OpusThemeParams(
    val darkTheme: Boolean,
    val dynamicColor: Boolean,
    val opusMode: Boolean,
    val reduceMotion: Boolean,
    val calmMotion: Boolean,
)

private val LocalOpusThemeParams = staticCompositionLocalOf<OpusThemeParams?> { null }

/**
 * The Opus Clockblock theme: `MaterialExpressiveTheme` + Dusk Instrument colours, Google Sans Flex type, the
 * semantic advice palette, the sky ramp and motion tokens.
 *
 * Precedence: [nightSafe] wins over [opusMode] (it is functional), which wins over dynamic/static colour.
 * Any change of colours (Night-safe, light/dark, Opus mode) cross-fades on [OpusMotion.themeCrossFade] rather
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
fun OpusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    nightSafe: Boolean = false,
    opusMode: Boolean = false,
    reduceMotion: Boolean = false,
    calmMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val variant = when {
        nightSafe -> OpusThemeVariant.NightSafe
        opusMode -> OpusThemeVariant.Opus
        else -> OpusThemeVariant.Standard
    }
    val dark = darkTheme || variant != OpusThemeVariant.Standard
    val context = LocalContext.current
    val useDynamic = dynamicColor && variant == OpusThemeVariant.Standard && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val systemAnimations = rememberSystemAnimationsEnabled()
    val reduce = reduceMotion || !systemAnimations
    val motionScheme = when {
        reduce -> StillMotionScheme
        variant == OpusThemeVariant.NightSafe || calmMotion -> CalmMotionScheme
        else -> MotionScheme.expressive()
    }
    val targetPalette = remember(variant, dark, useDynamic, context) {
        val colorScheme = when {
            variant == OpusThemeVariant.NightSafe -> NightSafeColors
            variant == OpusThemeVariant.Opus -> OpusConcertColors
            useDynamic && dark -> dynamicDarkColorScheme(context)
            useDynamic -> dynamicLightColorScheme(context)
            dark -> OpusDarkColors
            else -> OpusLightColors
        }
        val baseAdvice = if (dark) AdviceColors.Dark else AdviceColors.Light
        val advice = when (variant) {
            OpusThemeVariant.NightSafe -> AdviceColors.Dark.dimmed()
            OpusThemeVariant.Opus -> AdviceColors.Dark.harmonizedWith(colorScheme.primary)
            OpusThemeVariant.Standard -> if (useDynamic) baseAdvice.harmonizedWith(colorScheme.primary) else baseAdvice
        }
        val sky = when {
            variant == OpusThemeVariant.NightSafe -> SkyPalette.Default.dimmed()
            useDynamic || variant == OpusThemeVariant.Opus -> SkyPalette.Default.blendedToward(colorScheme.primary, 0.15f)
            else -> SkyPalette.Default
        }
        ThemePalette(colorScheme, advice, sky, artColorsFor(colorScheme, variant, dark))
    }
    val motion = remember(motionScheme, reduce) { OpusMotion(motionScheme, reduce) }
    val palette = animateThemePalette(targetPalette, motion.themeCrossFade())
    val extras = remember(variant, dark, palette, motion) {
        OpusExtras(
            variant = variant,
            dark = dark,
            adviceColors = palette.adviceColors,
            sky = palette.sky,
            text = if (variant == OpusThemeVariant.Opus) ConcertOpusTextStyles else DefaultOpusTextStyles,
            motion = motion,
            art = palette.art,
        )
    }
    val params = OpusThemeParams(darkTheme, dynamicColor, opusMode, reduceMotion, calmMotion)
    CompositionLocalProvider(
        LocalOpusExtras provides extras,
        LocalReduceMotion provides reduce,
        LocalOpusThemeParams provides params,
    ) {
        MaterialExpressiveTheme(
            colorScheme = palette.colorScheme,
            motionScheme = motionScheme,
            shapes = OpusShapes,
            typography = if (variant == OpusThemeVariant.Opus) OpusConcertTypography else OpusTypography,
            content = content,
        )
    }
}

/**
 * Re-themes [content] Night-safe (or back) while inheriting every other parameter of the enclosing [OpusTheme].
 * Always call it with the same shape (toggle [nightSafe], never wrap conditionally) so the subtree keeps its
 * state and the colours cross-fade.
 */
@Composable
fun NightSafeTheme(nightSafe: Boolean, content: @Composable () -> Unit) {
    val outer = LocalOpusThemeParams.current
    if (outer == null) {
        OpusTheme(nightSafe = nightSafe, content = content)
    } else {
        OpusTheme(
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

/** Accessors for the Opus-specific parts of the theme. */
object OpusTheme {
    val variant: OpusThemeVariant
        @Composable @ReadOnlyComposable get() = LocalOpusExtras.current.variant

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalOpusExtras.current.dark

    val adviceColors: AdviceColors
        @Composable @ReadOnlyComposable get() = LocalOpusExtras.current.adviceColors

    val sky: SkyPalette
        @Composable @ReadOnlyComposable get() = LocalOpusExtras.current.sky

    /** Time, body-clock and editorial styles beyond the M3 scale (`MaterialTheme.typography`). */
    val textStyles: OpusTextStyles
        @Composable @ReadOnlyComposable get() = LocalOpusExtras.current.text

    val motion: OpusMotion
        @Composable @ReadOnlyComposable get() = LocalOpusExtras.current.motion

    val artColors: ArtColors
        @Composable @ReadOnlyComposable get() = LocalOpusExtras.current.art

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
    val extras = LocalOpusExtras.current
    val text = remember(extras.text, enabled) { if (enabled) extras.text.pixelated() else extras.text }
    val pixelExtras = remember(extras, text) { extras.copy(text = text) }
    CompositionLocalProvider(LocalOpusExtras provides pixelExtras, LocalPixelMode provides enabled, content = content)
}

private fun OpusTextStyles.pixelated(): OpusTextStyles {
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
