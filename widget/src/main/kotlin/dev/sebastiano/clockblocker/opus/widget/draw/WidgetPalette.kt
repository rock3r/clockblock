package dev.sebastiano.clockblocker.opus.widget.draw

import dev.sebastiano.clockblocker.opus.core.model.AdviceType

/**
 * Widget colours, consistent with docs/design.md §2.4 ("Dusk Instrument"): Twilight Indigo #4F46E5 chrome,
 * fixed semantic advice colours (Marigold, Ink Plum, Midnight…). Kept local to the widget module on purpose:
 * widgets render out-of-process and must not depend on Compose theme state.
 *
 * Colours are ARGB ints so the same palette feeds Remote Compose (`Color(argb).rc`) and android.graphics.
 */
data class WidgetPalette(
    val isDark: Boolean,
    val surface: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val track: Int,
    val primary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val wedge: Int,
    val hand: Int,
    val sun: Int,
    val bodyRing: Int,
    val bodyNight: Int,
    val cbtMin: Int,
    private val advice: Map<AdviceType, AdviceColors>,
) {
    fun advice(type: AdviceType): AdviceColors = advice.getValue(type)

    companion object {
        fun of(dark: Boolean): WidgetPalette = if (dark) Dark else Light

        val Light = WidgetPalette(
            isDark = false,
            surface = 0xFFFCF8FF.toInt(),
            onSurface = 0xFF1B1B21.toInt(),
            onSurfaceVariant = 0xFF47464F.toInt(),
            outline = 0xFFC8C5D0.toInt(),
            track = 0xFFECE9F4.toInt(),
            primary = 0xFF4F46E5.toInt(),
            primaryContainer = 0xFFE2DFFF.toInt(),
            onPrimaryContainer = 0xFF100069.toInt(),
            wedge = 0x264F46E5,
            hand = 0xFF1B1B21.toInt(),
            sun = 0xFFFFB000.toInt(),
            bodyRing = 0xFFE2DFFF.toInt(),
            bodyNight = 0xFF1E2A78.toInt(),
            cbtMin = 0xFFB69DF8.toInt(),
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
            isDark = true,
            surface = 0xFF131318.toInt(),
            onSurface = 0xFFE5E1EA.toInt(),
            onSurfaceVariant = 0xFFC8C5D0.toInt(),
            outline = 0xFF47464F.toInt(),
            track = 0xFF24232B.toInt(),
            primary = 0xFFC3C0FF.toInt(),
            primaryContainer = 0xFF3730A3.toInt(),
            onPrimaryContainer = 0xFFE2DFFF.toInt(),
            wedge = 0x33C3C0FF,
            hand = 0xFFE5E1EA.toInt(),
            sun = 0xFFFFB000.toInt(),
            bodyRing = 0xFF2C2A4A.toInt(),
            bodyNight = 0xFF7C8CFF.toInt(),
            cbtMin = 0xFFD0BCFF.toInt(),
            advice = mapOf(
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
            ),
        )
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
