package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.AdviceColorRole
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.AdviceColors
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ColorMath
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyPalette
import dev.sebastiano.clockblocker.opus.core.model.AdviceType

/**
 * The rail's (the plan timeline's) colours, resolved once per theme.
 *
 * Advice capsules and chips are the only things on the rail in advice colours; everything else is neutral and stays
 * out of their way. The pale advice containers (Sleep, Avoid light, naps, melatonin) sit only a tone or two from the
 * surface containers, so the grey Now row and the grey chips read as more advice pills (owner, device QA). Rows and
 * chips no longer fill with a grey container: the Now row is a card one step *lighter* than the page (the lowest
 * container: white in light theme) with a [nowEdge] outline, chips are outlined, and every capsule draws an edge in its
 * advice hue's deep tone ([capsule]), so a pill reads as a pill whatever it sits on.
 *
 * @property page the page under the rail.
 * @property emphasisedRow the card behind the Now row and rows the dial highlights.
 * @property nowEdge the Now row card's outline.
 * @property highlightEdge the outline of a row the dial highlights (that isn't Now).
 * @property chip a chip's fill (a shorter block riding inside a row) at rest; [chipEdge] its outline.
 * @property text titles and times; [quietText] durations, the other zone's times and past rows; [nowTime] the Now
 *   row's start time.
 */
@Immutable
internal class RailColors(
    val page: Color,
    val emphasisedRow: Color,
    val nowEdge: Color,
    val highlightEdge: Color,
    val chip: Color,
    val chipEdge: Color,
    val text: Color,
    val quietText: Color,
    val nowTime: Color,
    private val advice: AdviceColors,
    private val edges: Map<AdviceType, Color>,
    private val pastEdges: Map<AdviceType, Color>,
) {
    /** The colours of a [type] block's capsule in [state]. */
    fun capsule(type: AdviceType, state: CapsuleState): CapsuleColors {
        val role = advice[type]
        val edge = edges.getValue(type)
        return when (state) {
            CapsuleState.Now -> CapsuleColors(fill = role.color, edge = edge, ink = role.onColor.copy(alpha = PatternInkAlpha))
            CapsuleState.Upcoming -> CapsuleColors(fill = role.container, edge = edge, ink = role.onContainer.copy(alpha = PatternInkAlpha))
            CapsuleState.Past -> CapsuleColors(
                fill = ColorMath.mix(role.container, page, PastWash),
                edge = pastEdges.getValue(type),
                ink = role.onContainer.copy(alpha = PatternInkAlpha * (1f - PastWash)),
            )
        }
    }

    /** The edge of a [type] chip that is on now or highlighted (the same deep tone as its capsule's). */
    fun edge(type: AdviceType): Color = edges.getValue(type)

    companion object {
        /**
         * The rail's colours for [scheme], [advice] and the body-clock [sky] (whose strip, at [SkyBandAlpha], runs
         * behind the capsules). Capsule edges start from the advice hue's deep tone ([AdviceColorRole.outline] where
         * the vivid colour is too pale, else the vivid colour) and are pushed darker (light theme) or lighter (dark)
         * until they keep [EdgeContrast] against the page, the Now card and the strip over either (WCAG 1.4.11,
         * with a margin), so they hold under any wallpaper palette. Past edges wash [PastWash] into the page but keep
         * [PastEdgeContrast].
         */
        fun of(scheme: ColorScheme, advice: AdviceColors, sky: SkyPalette): RailColors {
            val page = scheme.surface
            val card = scheme.surfaceContainerLowest
            val strips = listOf(page, card).flatMap { bg ->
                (0 until StripSamples).map { ColorMath.over(sky.gradientAt(it * 24f / StripSamples).mid.copy(alpha = SkyBandAlpha), bg) }
            }
            // Only the extremes matter: the darkest and lightest backgrounds bound every contrast in between.
            val backgrounds = (listOf(page, card) + strips).let { all -> listOf(all.minBy { it.luminance() }, all.maxBy { it.luminance() }) }
            val darkPage = page.luminance() < 0.5f
            val edges = AdviceType.entries.associateWith { type ->
                advice[type].deepTone.withContrastAtLeast(EdgeContrast, backgrounds, lighten = darkPage)
            }
            val pastEdges = AdviceType.entries.associateWith { type ->
                ColorMath.mix(edges.getValue(type), page, PastWash).withContrastAtLeast(PastEdgeContrast, backgrounds, lighten = darkPage)
            }
            return RailColors(
                page = page,
                emphasisedRow = card,
                nowEdge = scheme.primary,
                highlightEdge = scheme.outline,
                chip = card,
                chipEdge = scheme.outlineVariant,
                text = scheme.onSurface,
                quietText = scheme.onSurfaceVariant,
                nowTime = scheme.primary,
                advice = advice,
                edges = edges,
                pastEdges = pastEdges,
            )
        }
    }
}

/** The rail's colours for the current theme. */
@Composable
internal fun rememberRailColors(): RailColors {
    val scheme = MaterialTheme.colorScheme
    val advice = ClockblockTheme.adviceColors
    val sky = ClockblockTheme.sky
    return remember(scheme, advice, sky) { RailColors.of(scheme, advice, sky) }
}

/** Where a capsule's block is in time: past ones step back, the one happening now leads. */
internal enum class CapsuleState { Past, Now, Upcoming }

/** A capsule's [fill], its [edge] (drawn inside the shape) and its pattern [ink]. */
@Immutable
internal data class CapsuleColors(val fill: Color, val edge: Color, val ink: Color)

/** The advice hue's deep tone: its outline where it has one (pale vivid colours), else the vivid colour. */
private val AdviceColorRole.deepTone: Color get() = if (hasOutline) outline else color

/**
 * This colour, or the nearest tone of its hue (darker, or lighter when [lighten]) that keeps [min] contrast against
 * every one of [backgrounds].
 */
private fun Color.withContrastAtLeast(min: Float, backgrounds: List<Color>, lighten: Boolean): Color {
    fun ok(c: Color) = backgrounds.all { ColorMath.contrast(c, it) >= min }
    if (ok(this)) return this
    val start = ColorMath.lightness(this)
    for (step in 1..50) {
        val l = (start + (if (lighten) 0.01f else -0.01f) * step).coerceIn(0f, 1f)
        val candidate = ColorMath.withLightness(this, l)
        if (ok(candidate) || l == 0f || l == 1f) return candidate
    }
    return this
}

/** Capsule edges keep this much contrast against everything they sit on (3:1 plus a margin for blending). */
private const val EdgeContrast = 3.2f

/** Past capsule edges: quieter, but still a visible pill. */
private const val PastEdgeContrast = 2.2f

/** How far past capsules wash into the page. */
internal const val PastWash = 0.45f

/** Body-clock strip samples (every half hour) when looking for its darkest and lightest colour. */
private const val StripSamples = 48

/** The capsules' patterns (hatch, star dots, round dots) in the capsule's ink at this alpha. */
private const val PatternInkAlpha = 0.22f
