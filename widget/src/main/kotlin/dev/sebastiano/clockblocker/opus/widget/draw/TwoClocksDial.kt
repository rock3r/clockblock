package dev.sebastiano.clockblocker.opus.widget.draw

import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.widget.state.DialArc
import dev.sebastiano.clockblocker.opus.widget.state.DialMath
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The "Two Clocks" dial (docs/design.md §2.3 A) as a display list in unit space (outer radius = 1), laid out like the
 * in-app dial (`:core:designsystem` `TwoClocksDial`):
 *
 * - outer ring (r 0.92) = local clock, 24 h, **noon at the top**, filled with the sky ramp (night at the bottom,
 *   day at the top, dawn and dusk on the sides);
 * - two advice lanes for the next 24 h: the outer **Light** lane (see bright / some light, hatched avoid light) and
 *   the inner **Rest** lane (sleep, naps, flights, peak fatigue, with caffeine ticks and the melatonin dot);
 * - inner ring (r 0.60) = body clock: biological night arc, the body's noon and the CBTmin marker (PuffyDiamond);
 * - jet lag wedge between local midnight and body midnight.
 *
 * The hand is not part of [build]: it is driven by host time (see `RestrictedRemoteCompose.kt` and
 * `RemoteDrawOps.kt`). Its head is a Sunny sun by day and a crescent moon at night (same rule as the app: day =
 * between [SUNRISE_MINUTE] and [SUNSET_MINUTE] local time).
 */
object TwoClocksDial {
    const val LOCAL_RING_R = 0.93f
    const val LOCAL_RING_W = 0.09f
    const val LIGHT_LANE_R = 0.845f
    const val LIGHT_LANE_W = 0.062f
    const val REST_LANE_R = 0.775f
    const val REST_LANE_W = 0.062f
    const val BODY_RING_R = 0.695f
    const val BODY_RING_W = 0.05f
    const val WEDGE_R = 0.665f

    /** The in-app dial's default daylight window (06:30–19:00), used for the sky ramp and the sun/moon head. */
    const val SUNRISE_MINUTE = 390
    const val SUNSET_MINUTE = 1140

    /** Geometry of the host-time-driven hand. */
    object Hand {
        const val INNER_R = 0.72f
        const val OUTER_R = 0.88f
        const val STROKE = 0.028f
        const val HEAD_R = 0.08f
        const val HEAD_RING = 0.026f
    }

    /** Lanes, ticks and the sky ring only: the empty-state dial (and the base of [build]). */
    fun empty(p: WidgetPalette): List<DrawOp> = buildList {
        add(DrawOp.SweepRing(0f, 0f, LOCAL_RING_R, LOCAL_RING_W, p.sky, startDeg = DialMath.canvasDegrees(0)))
        add(DrawOp.Circle(0f, 0f, LOCAL_RING_R + LOCAL_RING_W / 2f, p.outline, stroke = 0.008f))
        add(DrawOp.Circle(0f, 0f, LOCAL_RING_R - LOCAL_RING_W / 2f, p.outline, stroke = 0.008f))
        add(DrawOp.Circle(0f, 0f, LIGHT_LANE_R, p.track, stroke = LIGHT_LANE_W))
        add(DrawOp.Circle(0f, 0f, REST_LANE_R, p.track, stroke = REST_LANE_W))
        add(DrawOp.Circle(0f, 0f, BODY_RING_R, p.bodyRing, stroke = BODY_RING_W))
        // Hour ticks across both lanes, as in the app; cardinal hours (00, 06, 12, 18) are longer and darker.
        for (h in 0 until 24) {
            val cardinal = h % 6 == 0
            val deg = DialMath.canvasDegrees(h * 60)
            val inner = if (cardinal) REST_LANE_R - REST_LANE_W / 2f else REST_LANE_R + REST_LANE_W / 2f - 0.01f
            val (x0, y0) = polar(inner, deg)
            val (x1, y1) = polar(LIGHT_LANE_R + LIGHT_LANE_W / 2f, deg)
            add(DrawOp.Line(x0, y0, x1, y1, if (cardinal) p.onSurfaceVariant else p.outline, if (cardinal) 0.014f else 0.009f))
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
            add(WidgetShapes.puffyDiamond(p.surface, 0.068f, x, y))
            add(WidgetShapes.puffyDiamond(p.cbtMin, 0.05f, x, y))
        }

        // Advice lanes: rest lane first, then light lane, so nothing in the outer lane is overdrawn.
        state.arcs.sortedBy { lane(it.type) }.forEach { addAll(adviceArc(it, p)) }
    }

    /** The disc behind the hand head, at the origin (it lifts the head off the sky ring). */
    fun headHalo(p: WidgetPalette): List<DrawOp> = listOf(
        DrawOp.Circle(0f, 0f, Hand.HEAD_R + Hand.HEAD_RING, p.surface),
        DrawOp.Circle(0f, 0f, Hand.HEAD_R + Hand.HEAD_RING, p.hand, stroke = 0.012f),
    )

    /** Sunny head, at the origin. */
    fun sunHead(p: WidgetPalette): List<DrawOp> = listOf(WidgetShapes.sun(p.sun, Hand.HEAD_R, 0f, 0f))

    /** Crescent head, at the origin: a disc with a bite in the halo colour. */
    fun moonHead(p: WidgetPalette): List<DrawOp> = listOf(
        DrawOp.Circle(0f, 0f, Hand.HEAD_R * 0.86f, p.moon),
        DrawOp.Circle(Hand.HEAD_R * 0.42f, -Hand.HEAD_R * 0.32f, Hand.HEAD_R * 0.7f, p.surface),
    )

    /** 0 = rest lane (drawn first), 1 = light lane. */
    private fun lane(type: AdviceType?): Int = when (type) {
        AdviceType.SeeBrightLight, AdviceType.SeeLight, AdviceType.AvoidLight -> 1
        else -> 0
    }

    private fun adviceArc(arc: DialArc, p: WidgetPalette): List<DrawOp> {
        val type = arc.type ?: return emptyList()
        val colors = p.advice(type)
        val laneR = if (lane(type) == 1) LIGHT_LANE_R else REST_LANE_R
        val laneW = if (lane(type) == 1) LIGHT_LANE_W else REST_LANE_W
        if (arc.sweepMinutes == 0) {
            val (x, y) = polar(laneR, DialMath.canvasDegrees(arc.startMinute))
            return listOf(
                DrawOp.Circle(x, y, laneW * 0.62f, p.surface),
                DrawOp.Circle(x, y, laneW * 0.44f, colors.arc),
            )
        }
        // Caffeine windows and flights are thin bands on the lane's inner / outer edge, so a sleep or nap under
        // them stays readable; everything else fills the lane.
        val (radius, width) = when (type) {
            AdviceType.Caffeine, AdviceType.AvoidCaffeine -> (laneR - laneW * 0.3f) to (laneW * 0.4f)
            AdviceType.Flight -> (laneR + laneW * 0.3f) to (laneW * 0.4f)
            AdviceType.PeakFatigue -> laneR to (laneW * 0.55f)
            else -> laneR to laneW
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
        val inner = radius - width / 2f + 0.01f
        val outer = radius + width / 2f - 0.01f
        val lines = mutableListOf<DrawOp>()
        var a = 2f
        while (a + lean <= sweep - 1f) {
            val (x0, y0) = polar(inner, start + a)
            val (x1, y1) = polar(outer, start + a + lean)
            lines += DrawOp.Line(x0, y0, x1, y1, color, 0.016f, roundCap = true)
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
