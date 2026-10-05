package dev.sebastiano.clockblocker.opus.widget.draw

import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.widget.state.DialArc
import dev.sebastiano.clockblocker.opus.widget.state.DialMath
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The "Two Clocks" dial (docs/design.md §2.3 A) as a display list in unit space (outer radius = 1):
 *
 * - outer ring (r 0.94) = local clock, 24 h, **noon at the top**, hour ticks;
 * - advice track (r 0.78) = the next 24 h of advice (marigold light, hatched plum avoid-light, midnight sleep…);
 * - inner ring (r 0.60) = body clock: biological night arc, CBTmin marker and the body's noon;
 * - jet lag wedge between local midnight and body midnight.
 *
 * The hand is not part of [build]: on API 36+ it is driven by host time (see `RestrictedRemoteCompose.kt`),
 * on the fallback it is frozen at render time via [hand].
 */
object TwoClocksDial {
    const val LOCAL_RING_R = 0.94f
    const val LOCAL_RING_W = 0.10f
    const val TRACK_R = 0.78f
    const val TRACK_W = 0.13f
    const val BODY_RING_R = 0.60f
    const val BODY_RING_W = 0.07f
    const val WEDGE_R = 0.535f

    /** Hand geometry shared by the static and the host-time-driven hand. */
    object Hand {
        const val INNER_R = 0.655f
        const val OUTER_R = 0.865f
        const val STROKE = 0.03f
        const val HEAD_R = 0.072f
        const val HEAD_RING = 0.024f
    }

    /** Rings and hour ticks only: the empty-state dial. */
    fun empty(p: WidgetPalette): List<DrawOp> = buildList {
        add(DrawOp.Circle(0f, 0f, LOCAL_RING_R, p.track, stroke = LOCAL_RING_W))
        add(DrawOp.Circle(0f, 0f, TRACK_R, p.track, stroke = TRACK_W))
        add(DrawOp.Circle(0f, 0f, BODY_RING_R, p.bodyRing, stroke = BODY_RING_W))
        // Hour ticks on the local ring; cardinal hours (00, 06, 12, 18) are larger.
        for (h in 0 until 24) {
            val cardinal = h % 6 == 0
            val (x, y) = polar(LOCAL_RING_R, DialMath.canvasDegrees(h * 60))
            add(DrawOp.Circle(x, y, if (cardinal) 0.024f else 0.011f, if (cardinal) p.onSurfaceVariant else p.outline))
        }
    }

    fun build(state: WidgetState.Active, p: WidgetPalette): List<DrawOp> = buildList {
        addAll(empty(p))

        // Jet lag wedge: local midnight (bottom) → body midnight.
        val mis = state.misalignmentMinutes
        if (abs(mis) >= 15) {
            val (start, sweep) = normalised(DialMath.canvasDegrees(0), mis / 4f)
            add(DrawOp.Arc(0f, 0f, WEDGE_R, start, sweep, p.wedge, stroke = 0f, fill = true))
        }

        // Body clock: night, its noon, CBTmin.
        state.bodyNight?.let { night ->
            add(arc(night, BODY_RING_R, BODY_RING_W, p.bodyNight, round = true))
        }
        val bodyNoon = polar(BODY_RING_R, DialMath.canvasDegrees(DialMath.wrap(12 * 60 + mis)))
        add(DrawOp.Circle(bodyNoon.first, bodyNoon.second, 0.03f, p.sun))
        state.cbtMinMinute?.let { m ->
            val (x, y) = polar(BODY_RING_R, DialMath.canvasDegrees(m))
            add(DrawOp.Circle(x, y, 0.05f, p.surface))
            add(DrawOp.Circle(x, y, 0.034f, p.cbtMin))
        }

        // Advice track.
        state.arcs.forEach { addAll(adviceArc(it, p)) }
    }

    /** Static hand at [minute] (fallback renderer and previews). */
    fun hand(minute: Float, p: WidgetPalette): List<DrawOp> {
        val deg = DialMath.canvasDegrees(minute)
        val (x0, y0) = polar(Hand.INNER_R, deg)
        val (x1, y1) = polar(Hand.OUTER_R, deg)
        val (hx, hy) = polar(LOCAL_RING_R, deg)
        return listOf(
            DrawOp.Line(x0, y0, x1, y1, p.hand, Hand.STROKE),
            DrawOp.Circle(hx, hy, Hand.HEAD_R + Hand.HEAD_RING, p.hand),
            DrawOp.Circle(hx, hy, Hand.HEAD_R, p.sun),
        )
    }

    private fun adviceArc(arc: DialArc, p: WidgetPalette): List<DrawOp> {
        val type = arc.type ?: return emptyList()
        val colors = p.advice(type)
        if (arc.sweepMinutes == 0) {
            val (x, y) = polar(TRACK_R, DialMath.canvasDegrees(arc.startMinute))
            return listOf(
                DrawOp.Circle(x, y, 0.062f, p.surface),
                DrawOp.Circle(x, y, 0.044f, colors.arc),
            )
        }
        val width = when (type) {
            AdviceType.Caffeine, AdviceType.AvoidCaffeine, AdviceType.Flight -> TRACK_W * 0.4f
            else -> TRACK_W
        }
        val radius = when (type) {
            AdviceType.Caffeine, AdviceType.AvoidCaffeine, AdviceType.Flight -> TRACK_R + TRACK_W * 0.3f
            else -> TRACK_R
        }
        val ops = mutableListOf<DrawOp>(arc(arc, radius, width, colors.arc, round = false))
        colors.hatch?.let { hatch -> ops += hatch(arc, radius, width, hatch) }
        return ops
    }

    /** 45°-ish hatch lines across the band: colour-blind-safe "avoid" pattern. */
    private fun hatch(arc: DialArc, radius: Float, width: Float, color: Int): List<DrawOp> {
        val start = DialMath.canvasDegrees(arc.startMinute)
        val sweep = arc.sweepMinutes / 4f
        val step = 7f
        val lean = 5f
        val inner = radius - width / 2f + 0.012f
        val outer = radius + width / 2f - 0.012f
        val lines = mutableListOf<DrawOp>()
        var a = 2f
        while (a + lean <= sweep - 1f) {
            val (x0, y0) = polar(inner, start + a)
            val (x1, y1) = polar(outer, start + a + lean)
            lines += DrawOp.Line(x0, y0, x1, y1, color, 0.018f, roundCap = true)
            a += step
        }
        return lines
    }

    private fun arc(arc: DialArc, r: Float, w: Float, color: Int, round: Boolean): DrawOp.Arc {
        val sweep = (arc.sweepMinutes / 4f).coerceAtMost(359.9f)
        return DrawOp.Arc(0f, 0f, r, DialMath.canvasDegrees(arc.startMinute), sweep, color, w, roundCap = round)
    }

    private fun normalised(start: Float, sweep: Float): Pair<Float, Float> =
        if (sweep >= 0) start to sweep else (start + sweep) to -sweep

    fun polar(r: Float, deg: Float): Pair<Float, Float> {
        val rad = Math.toRadians(deg.toDouble())
        return (r * cos(rad)).toFloat() to (r * sin(rad)).toFloat()
    }
}
