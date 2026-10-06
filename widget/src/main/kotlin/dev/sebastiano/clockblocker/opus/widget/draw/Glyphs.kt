package dev.sebastiano.clockblocker.opus.widget.draw

import dev.sebastiano.clockblocker.opus.core.model.AdviceType

/** What the leading glyph of a widget shows. */
sealed interface GlyphKind {
    data class Advice(val type: AdviceType) : GlyphKind

    /** Between blocks: nothing to do right now. */
    data object Free : GlyphKind

    /** Plan finished: adapted ("Clockblocked"). */
    data object Adapted : GlyphKind

    /** No trip planned. */
    data object NoTrip : GlyphKind

    /** Advice whose kind stays off the lock screen (redacted melatonin): a neutral dot, paired with "Plan step". */
    data object PlanStep : GlyphKind
}

/**
 * Advice glyphs as display lists in a unit box: a container in the advice container colour with a simple mark in
 * the on-container colour. The container is the advice's signature `MaterialShapes` silhouette (design.md §2.4
 * "shapes carry meaning": VerySunny = bright light, Sunny = some light, SemiCircle = avoid light, Pill = sleep,
 * Bun = nap, PuffyDiamond = melatonin, Cookie4Sided = caffeine, SoftBurst = peak fatigue, Arrow = flight; Flower =
 * adapted), so the shape alone tells advice apart even without colour. The mark inside keeps the older icon
 * vocabulary (sun, sun below the horizon, crescent, pill, sparkle, cup, low battery, arrow). Always paired with a
 * text label.
 */
object Glyphs {

    fun colors(kind: GlyphKind, p: WidgetPalette): Pair<Int, Int> = when (kind) {
        is GlyphKind.Advice -> p.advice(kind.type).let { it.container to it.onContainer }
        GlyphKind.Free, GlyphKind.PlanStep -> p.track to p.onSurfaceVariant
        GlyphKind.Adapted, GlyphKind.NoTrip -> p.primaryContainer to p.onPrimaryContainer
    }

    fun build(kind: GlyphKind, p: WidgetPalette): List<DrawOp> {
        val (container, on) = colors(kind, p)
        val shape: DrawOp = when (kind) {
            is GlyphKind.Advice -> WidgetShapes.advice(kind.type, container)
            GlyphKind.Adapted -> WidgetShapes.flower(container)
            GlyphKind.Free, GlyphKind.NoTrip, GlyphKind.PlanStep -> DrawOp.Circle(0f, 0f, 1f, container)
        }
        val (scale, dy) = markFit(kind)
        return listOf(shape) + mark(kind, on, container).map { it.scaled(scale, dy) }
    }

    /**
     * How much to shrink (and nudge down) the mark so it sits inside the silhouette's inscribed area: the circle
     * container fit marks at full size, the irregular shapes are smaller inside.
     */
    private fun markFit(kind: GlyphKind): Pair<Float, Float> = when (kind) {
        is GlyphKind.Advice -> when (kind.type) {
            AdviceType.SeeBrightLight -> 0.78f to 0f
            AdviceType.SeeLight -> 0.82f to 0f
            AdviceType.AvoidLight -> 0.78f to 0.12f
            AdviceType.Sleep -> 0.72f to 0f
            AdviceType.Nap, AdviceType.OptionalNap -> 0.8f to 0f
            AdviceType.Melatonin -> 0.66f to 0f
            AdviceType.Caffeine, AdviceType.AvoidCaffeine -> 0.82f to 0f
            AdviceType.PeakFatigue -> 0.8f to 0f
            AdviceType.Flight -> 0.5f to 0.08f
        }
        GlyphKind.Adapted -> 0.82f to 0f
        GlyphKind.Free, GlyphKind.NoTrip, GlyphKind.PlanStep -> 1f to 0f
    }

    private fun mark(kind: GlyphKind, on: Int, bg: Int): List<DrawOp> = when (kind) {
        is GlyphKind.Advice -> when (kind.type) {
            AdviceType.SeeBrightLight -> sun(on, core = 0.30f, rays = 8, rayFrom = 0.46f, rayTo = 0.66f)
            AdviceType.SeeLight -> sun(on, core = 0.34f, rays = 8, rayFrom = 0.52f, rayTo = 0.58f)
            AdviceType.AvoidLight -> listOf(
                DrawOp.Arc(0f, 0.16f, 0.36f, 180f, 180f, on, 0f, fill = true),
                DrawOp.Line(-0.58f, 0.24f, 0.58f, 0.24f, on, 0.1f),
                DrawOp.Line(-0.34f, 0.44f, 0.34f, 0.44f, on, 0.1f),
            )
            AdviceType.Sleep -> crescent(on, bg)
            AdviceType.Nap -> listOf(DrawOp.RoundRect(-0.56f, -0.2f, 0.56f, 0.2f, 0.2f, on))
            AdviceType.OptionalNap -> listOf(DrawOp.RoundRect(-0.52f, -0.17f, 0.52f, 0.17f, 0.17f, on, stroke = 0.09f))
            AdviceType.Melatonin -> listOf(
                DrawOp.Line(0f, -0.56f, 0f, 0.56f, on, 0.13f),
                DrawOp.Line(-0.4f, 0f, 0.4f, 0f, on, 0.13f),
                DrawOp.Circle(0f, 0f, 0.17f, on),
            )
            AdviceType.Caffeine -> cup(on, filled = true)
            AdviceType.AvoidCaffeine -> cup(on, filled = false) +
                DrawOp.Line(-0.5f, 0.5f, 0.5f, -0.5f, on, 0.09f)
            AdviceType.PeakFatigue -> listOf(
                DrawOp.RoundRect(-0.5f, -0.27f, 0.4f, 0.27f, 0.09f, on, stroke = 0.09f),
                DrawOp.RoundRect(0.44f, -0.1f, 0.55f, 0.1f, 0.03f, on),
                DrawOp.RoundRect(-0.37f, -0.14f, -0.16f, 0.14f, 0.03f, on),
            )
            AdviceType.Flight -> listOf(
                DrawOp.Line(-0.42f, 0.42f, 0.42f, -0.42f, on, 0.13f),
                DrawOp.Line(0.42f, -0.42f, 0.02f, -0.42f, on, 0.13f),
                DrawOp.Line(0.42f, -0.42f, 0.42f, -0.02f, on, 0.13f),
            )
        }
        GlyphKind.Free -> listOf(
            DrawOp.Circle(0f, 0f, 0.48f, on, stroke = 0.1f),
            DrawOp.Line(0f, 0f, 0f, -0.28f, on, 0.1f),
            DrawOp.Line(0f, 0f, 0.2f, 0.12f, on, 0.1f),
        )
        GlyphKind.Adapted -> listOf(
            DrawOp.Line(-0.4f, 0.02f, -0.12f, 0.3f, on, 0.14f),
            DrawOp.Line(-0.12f, 0.3f, 0.42f, -0.26f, on, 0.14f),
        )
        GlyphKind.PlanStep -> listOf(DrawOp.Circle(0f, 0f, 0.24f, on))
        GlyphKind.NoTrip -> listOf(
            DrawOp.RoundRect(-0.2f, -0.48f, 0.2f, -0.18f, 0.08f, on, stroke = 0.08f),
            DrawOp.RoundRect(-0.52f, -0.26f, 0.52f, 0.46f, 0.14f, on),
            DrawOp.Line(-0.52f, 0.08f, 0.52f, 0.08f, bg, 0.07f, roundCap = false),
        )
    }

    private fun sun(on: Int, core: Float, rays: Int, rayFrom: Float, rayTo: Float): List<DrawOp> = buildList {
        add(DrawOp.Circle(0f, 0f, core, on))
        repeat(rays) { i ->
            val deg = i * 360f / rays - 90f
            val (x0, y0) = TwoClocksDial.polar(rayFrom, deg)
            val (x1, y1) = TwoClocksDial.polar(rayTo, deg)
            if (rayTo - rayFrom < 0.1f) add(DrawOp.Circle(x0, y0, 0.07f, on))
            else add(DrawOp.Line(x0, y0, x1, y1, on, 0.11f))
        }
    }

    private fun crescent(on: Int, bg: Int) = listOf(
        DrawOp.Circle(-0.04f, 0.02f, 0.46f, on),
        DrawOp.Circle(0.2f, -0.16f, 0.38f, bg),
    )

    private fun cup(on: Int, filled: Boolean): List<DrawOp> = listOf(
        DrawOp.RoundRect(-0.4f, -0.12f, 0.22f, 0.44f, 0.12f, on, stroke = if (filled) 0f else 0.09f),
        DrawOp.Arc(0.24f, 0.13f, 0.15f, -90f, 180f, on, 0.09f),
        DrawOp.Line(-0.2f, -0.5f, -0.2f, -0.3f, on, 0.08f),
        DrawOp.Line(0.02f, -0.56f, 0.02f, -0.3f, on, 0.08f),
    )
}
