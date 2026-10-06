package dev.sebastiano.clockblocker.opus.widget.draw

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ColorMath
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyPalette
import dev.sebastiano.clockblocker.opus.core.model.AdviceType

/** Which palette a widget render uses. */
enum class WidgetTheme {
    Light,
    Dark,

    /**
     * The plan says avoid light or sleep and "Night-safe automatically" is on (design.md §2.3 G): true black, dim amber
     * accents, dimmed advice colours and sky. Our own widget must not sabotage the advice.
     */
    NightSafe,
    ;

    val isDark: Boolean get() = this != Light
}

/**
 * Widget colours, consistent with docs/design.md §2.4 ("Dusk Instrument"): Twilight Indigo #4F46E5 chrome,
 * fixed semantic advice colours (Marigold, Ink Plum, Midnight…). Kept local to the widget module on purpose:
 * widgets render out-of-process and must not depend on Compose theme state. The sky ramp comes from the app's
 * [SkyPalette] (plain data), so the dial's outer ring matches the in-app dial.
 *
 * Colours are ARGB ints so the same palette feeds Remote Compose (`Color(argb).rc`) and android.graphics.
 */
data class WidgetPalette(
    val theme: WidgetTheme,
    val surface: Int,
    /** Cards inside a widget (the now card, Up next rows, the Done button track). */
    val surfaceContainer: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val track: Int,
    val primary: Int,
    /** Text on a filled [primary] button (the widget Done button). */
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val wedge: Int,
    val hand: Int,
    val sun: Int,
    val moon: Int,
    val bodyRing: Int,
    val bodyNight: Int,
    val cbtMin: Int,
    /** Sky colours for every hour 0..24 (the last repeats the first), for the dial's outer local-time ring. */
    val sky: List<Int>,
    private val advice: Map<AdviceType, AdviceColors>,
) {
    val isDark: Boolean get() = theme.isDark

    fun advice(type: AdviceType): AdviceColors = advice.getValue(type)

    /**
     * Background for a card about [type]: the surface blended [amount] towards the advice container, so the card
     * reads as "about light" or "about sleep" without losing text contrast. Night-safe never tints (true black).
     */
    fun tinted(type: AdviceType?, amount: Float = 0.45f): Int {
        if (type == null || theme == WidgetTheme.NightSafe) return surface
        return mix(surface, advice(type).container, amount)
    }

    /** A card inside the widget: [surfaceContainer], leaning towards the advice container like [tinted]. */
    fun card(type: AdviceType?, amount: Float = 0.6f): Int {
        if (type == null || theme == WidgetTheme.NightSafe) return surfaceContainer
        return mix(surfaceContainer, advice(type).container, amount)
    }

    companion object {
        fun of(theme: WidgetTheme): WidgetPalette = when (theme) {
            WidgetTheme.Light -> Light
            WidgetTheme.Dark -> Dark
            WidgetTheme.NightSafe -> NightSafe
        }

        val Light = WidgetPalette(
            theme = WidgetTheme.Light,
            surface = 0xFFFCF8FF.toInt(),
            surfaceContainer = 0xFFF0ECF7.toInt(),
            onSurface = 0xFF1B1B21.toInt(),
            onSurfaceVariant = 0xFF47464F.toInt(),
            outline = 0xFFC8C5D0.toInt(),
            track = 0xFFECE9F4.toInt(),
            primary = 0xFF4F46E5.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            primaryContainer = 0xFFE2DFFF.toInt(),
            onPrimaryContainer = 0xFF100069.toInt(),
            wedge = 0x264F46E5,
            hand = 0xFF1B1B21.toInt(),
            sun = 0xFFFFB000.toInt(),
            moon = 0xFF3B2F5C.toInt(),
            bodyRing = 0xFFE2DFFF.toInt(),
            bodyNight = 0xFF1E2A78.toInt(),
            cbtMin = 0xFFB69DF8.toInt(),
            sky = skyRamp(SkyPalette.Default),
            advice = mapOf(
                AdviceType.SeeBrightLight to AdviceColors(0xFFFFB000, 0xFFFFDEA0, 0xFF261900),
                AdviceType.SeeLight to AdviceColors(0xFFFFD27A, 0xFFFFEFD3, 0xFF261900),
                AdviceType.AvoidLight to AdviceColors(0xFFE8DEFF, 0xFFE8DEFF, 0xFF1F1640, hatch = 0xFF3B2F5C),
                AdviceType.Sleep to AdviceColors(0xFF1E2A78, 0xFFDEE0FF, 0xFF00105C),
                AdviceType.Nap to AdviceColors(0xFF7C8CFF, 0xFFE0E3FF, 0xFF1A1F66),
                AdviceType.OptionalNap to AdviceColors(0xFFB4BEFF, 0xFFE0E3FF, 0xFF1A1F66),
                AdviceType.Melatonin to AdviceColors(0xFF8B6FE0, 0xFFEADDFF, 0xFF25005A),
                AdviceType.Caffeine to AdviceColors(0xFFB5652B, 0xFFFFDBC8, 0xFF331200),
                AdviceType.AvoidCaffeine to AdviceColors(0xFFB5652B, 0xFFFFDBC8, 0xFF331200),
                AdviceType.PeakFatigue to AdviceColors(0xFFFF5A4E, 0xFFFFDAD5, 0xFF410001),
                AdviceType.Flight to AdviceColors(0xFF00A3A3, 0xFFB9F0EF, 0xFF002020),
            ),
        )

        val Dark = WidgetPalette(
            theme = WidgetTheme.Dark,
            surface = 0xFF131318.toInt(),
            surfaceContainer = 0xFF1F1F25.toInt(),
            onSurface = 0xFFE5E1EA.toInt(),
            onSurfaceVariant = 0xFFC8C5D0.toInt(),
            outline = 0xFF47464F.toInt(),
            track = 0xFF24232B.toInt(),
            primary = 0xFFC3C0FF.toInt(),
            onPrimary = 0xFF1F1A75.toInt(),
            primaryContainer = 0xFF3730A3.toInt(),
            onPrimaryContainer = 0xFFE2DFFF.toInt(),
            wedge = 0x33C3C0FF,
            hand = 0xFFE5E1EA.toInt(),
            sun = 0xFFFFB000.toInt(),
            moon = 0xFFF3EBD3.toInt(),
            bodyRing = 0xFF2C2A4A.toInt(),
            bodyNight = 0xFF7C8CFF.toInt(),
            cbtMin = 0xFFD0BCFF.toInt(),
            sky = skyRamp(SkyPalette.Default.forDarkTheme()),
            advice = DarkAdvice,
        )

        /** True black, dim amber (the app's night-safe scheme), every advice colour dimmed. */
        val NightSafe = WidgetPalette(
            theme = WidgetTheme.NightSafe,
            surface = 0xFF000000.toInt(),
            surfaceContainer = 0xFF0C0906.toInt(),
            onSurface = 0xFFB7A48D.toInt(),
            onSurfaceVariant = 0xFF8E7F71.toInt(),
            outline = 0xFF4A3E33.toInt(),
            track = 0xFF18130E.toInt(),
            primary = 0xFFA9640A.toInt(),
            onPrimary = 0xFF120A00.toInt(),
            primaryContainer = 0xFF2E1C06.toInt(),
            onPrimaryContainer = 0xFFD9B88C.toInt(),
            wedge = 0x33A9640A,
            hand = 0xFFB7A48D.toInt(),
            sun = 0xFFA9640A.toInt(),
            moon = 0xFFB7A48D.toInt(),
            bodyRing = 0xFF18130E.toInt(),
            bodyNight = 0xFF5A4630.toInt(),
            cbtMin = dim(0xFFFFDEA0.toInt(), 0.6f),
            sky = skyRamp(SkyPalette.Default.dimmed()),
            advice = DarkAdvice.mapValues { (_, c) ->
                AdviceColors(
                    arc = dim(c.arc, 0.62f, 0.55f),
                    container = dim(c.container, 0.55f, 0.6f),
                    onContainer = dim(c.onContainer, 0.72f, 0.45f),
                    hatch = c.hatch?.let { dim(it, 0.62f, 0.55f) },
                )
            },
        )

        private val DarkAdvice: Map<AdviceType, AdviceColors>
            get() = mapOf(
                AdviceType.SeeBrightLight to AdviceColors(0xFFFFB000, 0xFF5C4300, 0xFFFFDEA0),
                AdviceType.SeeLight to AdviceColors(0xFF9C7A2E, 0xFF3F2E00, 0xFFFFDEA0),
                AdviceType.AvoidLight to AdviceColors(0xFF362B5E, 0xFF362B5E, 0xFFE8DEFF, hatch = 0xFFB9A7F0),
                AdviceType.Sleep to AdviceColors(0xFF4A5BD0, 0xFF2B3A8F, 0xFFDEE0FF),
                AdviceType.Nap to AdviceColors(0xFF7C8CFF, 0xFF3A4399, 0xFFE0E3FF),
                AdviceType.OptionalNap to AdviceColors(0xFF5A66B8, 0xFF3A4399, 0xFFE0E3FF),
                AdviceType.Melatonin to AdviceColors(0xFFB69DF8, 0xFF4F378B, 0xFFEADDFF),
                AdviceType.Caffeine to AdviceColors(0xFFD98A50, 0xFF6B3A12, 0xFFFFDBC8),
                AdviceType.AvoidCaffeine to AdviceColors(0xFFD98A50, 0xFF6B3A12, 0xFFFFDBC8),
                AdviceType.PeakFatigue to AdviceColors(0xFFFF8A80, 0xFF8C1D18, 0xFFFFDAD5),
                AdviceType.Flight to AdviceColors(0xFF4FD1D1, 0xFF004F4F, 0xFFB9F0EF),
            )

        /** Zenith colour of [sky] at every hour 0..24 (the in-app dial's outer ring uses the same `top` colours). */
        internal fun skyRamp(sky: SkyPalette): List<Int> = (0..24).map { h -> sky.gradientAt((h % 24).toFloat()).top.toArgb() }

        internal fun mix(a: Int, b: Int, t: Float): Int = ColorMath.mix(Color(a), Color(b), t).toArgb()

        private fun dim(color: Int, lightness: Float, chroma: Float = lightness): Int =
            ColorMath.dim(Color(color), lightness, chroma).toArgb()
    }
}

/**
 * @property arc colour of the advice arc on the dial
 * @property container glyph container
 * @property onContainer glyph / text on the container
 * @property hatch optional 45° hatch drawn over the arc (avoid light: colour-blind-safe pattern)
 */
data class AdviceColors(val arc: Int, val container: Int, val onContainer: Int, val hatch: Int? = null) {
    constructor(arc: Long, container: Long, onContainer: Long, hatch: Long? = null) :
        this(arc.toInt(), container.toInt(), onContainer.toInt(), hatch?.toInt())
}
