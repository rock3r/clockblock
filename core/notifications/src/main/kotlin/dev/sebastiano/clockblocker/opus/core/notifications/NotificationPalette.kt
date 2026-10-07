package dev.sebastiano.clockblocker.opus.core.notifications

import androidx.annotation.ColorInt
import dev.sebastiano.clockblocker.opus.core.model.AdviceType

/**
 * The colours of one advice kind in a custom notification view, for one shade theme. Mirrors the design system's
 * `AdviceColors` ([container]/[onContainer] for the glyph chip; [mark] is the vivid colour, or its deeper `outline`
 * tone where the vivid one is too pale to read, for the progress bar). This module has no Compose, so the values
 * are copied; `NotificationPaletteTest` keeps them in step.
 */
internal data class ChipColors(
    @param:ColorInt val container: Int,
    @param:ColorInt val onContainer: Int,
    @param:ColorInt val mark: Int,
)

/** Light and dark shade colours of an advice kind; `null` is the neutral (Twilight Indigo) chip of a gap. */
internal object NotificationPalette {

    fun of(type: AdviceType?, dark: Boolean): ChipColors =
        if (type == null) (if (dark) BrandDark else BrandLight) else (if (dark) Dark else Light).getValue(type)

    /** Indigo 90 / 30 / 40, the app's primary container roles. */
    private val BrandLight = ChipColors(0xFFE2DFFF.toInt(), 0xFF3729C9.toInt(), 0xFF4F46E5.toInt())

    /** Indigo 30 / 90 / 80. */
    private val BrandDark = ChipColors(0xFF3729C9.toInt(), 0xFFE2DFFF.toInt(), 0xFFC3C0FF.toInt())

    private val Light: Map<AdviceType, ChipColors> = mapOf(
        AdviceType.SeeBrightLight to chip(0xFFFFDEA0, 0xFF261900, 0xFF8A5A00),
        AdviceType.SeeLight to chip(0xFFFFEFD3, 0xFF261900, 0xFF8F6400),
        AdviceType.AvoidLight to chip(0xFFE8DEFF, 0xFF1F1640, 0xFF3B2F5C),
        AdviceType.Sleep to chip(0xFFDEE0FF, 0xFF00105C, 0xFF1E2A78),
        AdviceType.Nap to chip(0xFFE0E3FF, 0xFF1A1F66, 0xFF4A58D0),
        AdviceType.OptionalNap to chip(0xFFEDEEFF, 0xFF1A1F66, 0xFF5560D8),
        AdviceType.Melatonin to chip(0xFFEADDFF, 0xFF25005A, 0xFF7552C4),
        AdviceType.Caffeine to chip(0xFFFFDBC8, 0xFF331200, 0xFFB5652B),
        AdviceType.AvoidCaffeine to chip(0xFFFFF1EA, 0xFF6B3A12, 0xFFB5652B),
        AdviceType.PeakFatigue to chip(0xFFFFDAD5, 0xFF410001, 0xFFE5483D),
        AdviceType.Flight to chip(0xFFB9F0EF, 0xFF002020, 0xFF008A8A),
    )

    private val Dark: Map<AdviceType, ChipColors> = mapOf(
        AdviceType.SeeBrightLight to chip(0xFF5C4300, 0xFFFFDEA0, 0xFFFFB000),
        AdviceType.SeeLight to chip(0xFF3F2E00, 0xFFFFDEA0, 0xFFE9C77F),
        AdviceType.AvoidLight to chip(0xFF362B5E, 0xFFE8DEFF, 0xFFC9B8FF),
        AdviceType.Sleep to chip(0xFF2B3A8F, 0xFFDEE0FF, 0xFFAAB4FF),
        AdviceType.Nap to chip(0xFF3A4399, 0xFFE0E3FF, 0xFF9AA6FF),
        AdviceType.OptionalNap to chip(0xFF2E3570, 0xFFE0E3FF, 0xFFB4BCFF),
        AdviceType.Melatonin to chip(0xFF4F378B, 0xFFEADDFF, 0xFFC7B3FF),
        AdviceType.Caffeine to chip(0xFF6B3A12, 0xFFFFDBC8, 0xFFE8955C),
        AdviceType.AvoidCaffeine to chip(0xFF2C1A0E, 0xFFFFDBC8, 0xFFE8955C),
        AdviceType.PeakFatigue to chip(0xFF8C1D18, 0xFFFFDAD5, 0xFFFF8A80),
        AdviceType.Flight to chip(0xFF004F4F, 0xFFB9F0EF, 0xFF4FD8D6),
    )

    private fun chip(container: Long, onContainer: Long, mark: Long) =
        ChipColors(container.toInt(), onContainer.toInt(), mark.toInt())
}
