package dev.sebastiano.clockblocker.opus.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.sebastiano.clockblocker.opus.core.model.AdviceType

/**
 * One semantic advice colour mapped to M3 roles. [color] is the vivid mark (dial arcs, glyph fill), [onColor]
 * is legible on it; [container]/[onContainer] are for cards and chips.
 */
@Immutable
data class AdviceColorRole(
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * Colour-blind safety: every advice type also has a pattern so meaning never rests on hue alone
 * (design.md §2.4 colour table).
 */
enum class AdvicePattern {
    Solid,

    /** 50 % solid: same hue, lighter fill (See some light). */
    HalfSolid,

    /** 45° diagonal hatch (Avoid light). */
    Hatch,

    /** Solid plus sparse star dots (Sleep). */
    StarDots,

    /** Rounded dots (Nap / optional nap). */
    RoundDots,

    /** Outline with a strike (Avoid caffeine). */
    Strike,

    /** Zig-zag edge (Peak fatigue). */
    ZigZag,

    /** Dashed (Travel). */
    Dashed,
}

val AdviceType.pattern: AdvicePattern
    get() = when (this) {
        AdviceType.SeeBrightLight -> AdvicePattern.Solid
        AdviceType.SeeLight -> AdvicePattern.HalfSolid
        AdviceType.AvoidLight -> AdvicePattern.Hatch
        AdviceType.Sleep -> AdvicePattern.StarDots
        AdviceType.Nap, AdviceType.OptionalNap -> AdvicePattern.RoundDots
        AdviceType.Melatonin -> AdvicePattern.Solid
        AdviceType.Caffeine -> AdvicePattern.Solid
        AdviceType.AvoidCaffeine -> AdvicePattern.Strike
        AdviceType.PeakFatigue -> AdvicePattern.ZigZag
        AdviceType.Flight -> AdvicePattern.Dashed
    }

/** The semantic advice palette. Look up with `OpusTheme.adviceColors[type]`. */
@Immutable
class AdviceColors internal constructor(private val roles: Map<AdviceType, AdviceColorRole>) {

    operator fun get(type: AdviceType): AdviceColorRole = roles.getValue(type)

    /** Rotates every role a little towards [primary] (dynamic colour) so advice colours sit in the wallpaper. */
    fun harmonizedWith(primary: Color): AdviceColors = AdviceColors(
        roles.mapValues { (_, r) ->
            AdviceColorRole(
                color = ColorMath.harmonize(r.color, primary),
                onColor = ColorMath.harmonize(r.onColor, primary),
                container = ColorMath.harmonize(r.container, primary),
                onContainer = ColorMath.harmonize(r.onContainer, primary),
            )
        },
    )

    /** Lower luminance and chroma for Night-safe surfaces. Containers sink towards black, inks stay legible. */
    fun dimmed(): AdviceColors = AdviceColors(
        roles.mapValues { (_, r) ->
            AdviceColorRole(
                color = ColorMath.dim(r.color, 0.62f, 0.55f),
                onColor = Color.Black,
                container = ColorMath.dim(r.container, 0.55f, 0.6f),
                onContainer = ColorMath.dim(r.onContainer, 0.72f, 0.45f),
            )
        },
    )

    /** Per-role blend towards [to] (theme cross-fade). */
    internal fun lerp(to: AdviceColors, t: Float): AdviceColors =
        AdviceColors(roles.mapValues { (type, r) -> r.lerp(to[type], t) })

    override fun equals(other: Any?): Boolean = other is AdviceColors && other.roles == roles
    override fun hashCode(): Int = roles.hashCode()

    companion object {
        val Light: AdviceColors = AdviceColors(
            mapOf(
                AdviceType.SeeBrightLight to role(0xFFFFB000, 0xFF261900, 0xFFFFDEA0, 0xFF261900),
                AdviceType.SeeLight to role(0xFFFFD57E, 0xFF261900, 0xFFFFEFD3, 0xFF261900),
                AdviceType.AvoidLight to role(0xFF3B2F5C, 0xFFFFFFFF, 0xFFE8DEFF, 0xFF1F1640),
                AdviceType.Sleep to role(0xFF1E2A78, 0xFFFFFFFF, 0xFFDEE0FF, 0xFF00105C),
                AdviceType.Nap to role(0xFF7C8CFF, 0xFF1A1F66, 0xFFE0E3FF, 0xFF1A1F66),
                AdviceType.OptionalNap to role(0xFF9AA6FF, 0xFF1A1F66, 0xFFEDEEFF, 0xFF1A1F66),
                AdviceType.Melatonin to role(0xFFB69DF8, 0xFF25005A, 0xFFEADDFF, 0xFF25005A),
                AdviceType.Caffeine to role(0xFFB5652B, 0xFFFFFFFF, 0xFFFFDBC8, 0xFF331200),
                AdviceType.AvoidCaffeine to role(0xFFB5652B, 0xFFFFFFFF, 0xFFFFF1EA, 0xFF6B3A12),
                AdviceType.PeakFatigue to role(0xFFE5483D, 0xFFFFFFFF, 0xFFFFDAD5, 0xFF410001),
                AdviceType.Flight to role(0xFF008A8A, 0xFFFFFFFF, 0xFFB9F0EF, 0xFF002020),
            ),
        )

        val Dark: AdviceColors = AdviceColors(
            mapOf(
                AdviceType.SeeBrightLight to role(0xFFFFB000, 0xFF261900, 0xFF5C4300, 0xFFFFDEA0),
                AdviceType.SeeLight to role(0xFFE9C77F, 0xFF261900, 0xFF3F2E00, 0xFFFFDEA0),
                AdviceType.AvoidLight to role(0xFFC9B8FF, 0xFF1F1640, 0xFF362B5E, 0xFFE8DEFF),
                AdviceType.Sleep to role(0xFF5867D6, 0xFFFFFFFF, 0xFF2B3A8F, 0xFFDEE0FF),
                AdviceType.Nap to role(0xFF9AA6FF, 0xFF1A1F66, 0xFF3A4399, 0xFFE0E3FF),
                AdviceType.OptionalNap to role(0xFFB4BCFF, 0xFF1A1F66, 0xFF2E3570, 0xFFE0E3FF),
                AdviceType.Melatonin to role(0xFFC7B3FF, 0xFF25005A, 0xFF4F378B, 0xFFEADDFF),
                AdviceType.Caffeine to role(0xFFE8955C, 0xFF331200, 0xFF6B3A12, 0xFFFFDBC8),
                AdviceType.AvoidCaffeine to role(0xFFE8955C, 0xFF331200, 0xFF2C1A0E, 0xFFFFDBC8),
                AdviceType.PeakFatigue to role(0xFFFF8A80, 0xFF410001, 0xFF8C1D18, 0xFFFFDAD5),
                AdviceType.Flight to role(0xFF4FD8D6, 0xFF002020, 0xFF004F4F, 0xFFB9F0EF),
            ),
        )

        private fun role(color: Long, on: Long, container: Long, onContainer: Long) =
            AdviceColorRole(Color(color), Color(on), Color(container), Color(onContainer))
    }
}
