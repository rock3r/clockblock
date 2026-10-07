package dev.sebastiano.clockblocker.opus.widget.rc

import kotlin.math.abs

/** A size in dp: a responsive bucket's minimum, or a launcher cell. */
data class CellDp(val width: Float, val height: Float) {
    /** Whether this size fits inside [other] (RemoteViews' own rule, with its rounding slack). */
    fun fitsIn(other: CellDp): Boolean = width - other.width < FIT_SLACK && height - other.height < FIT_SLACK

    override fun toString(): String = "${width.toInt()}×${height.toInt()} dp"

    private companion object {
        const val FIT_SLACK = 0.01f
    }
}

/**
 * One responsive entry of a widget: the host plays [layout] fitted for [min] whenever [min] is the closest size that
 * fits the widget (see [WidgetSizes.pick]). So [min] is the smallest size this layout can ever be drawn at, and the
 * size [LabelFit] fits its text for: what fits there fits at every larger size the host may stretch it to. One
 * layout can have several buckets (a short and a taller row), each captured and fitted separately.
 */
data class Bucket<L>(val layout: L, val min: CellDp)

/**
 * The responsive buckets of both widgets, the single source of truth for `WidgetRenderer` (which hands them to the
 * host), [LabelFit] (which fits each bucket's text for its [Bucket.min]) and the tests.
 *
 * The minimums follow real launcher cells, from the Android docs' example device ([docsCells]): portrait cells are
 * narrow and tall, landscape cells wide and short. A size below every bucket gets the smallest one, the 1×1
 * ([FLOOR]): the guarantee that no label clips holds from [FLOOR] up.
 */
object WidgetSizes {
    /** The smallest cell the widgets promise to fit: a portrait 1×1 is 57 dp wide, a landscape one 51 dp tall. */
    val FLOOR = CellDp(57f, 51f)

    /**
     * Next up, smallest area first. Each minimum is the smallest size at which every label fits whole at font scale
     * 1.3 on every density WidgetLabelFitTest measures, plus a few dp, and no larger than the launcher cells it is
     * meant for:
     * 2×1 portrait (130×102) → the taller Medium, 2×1 landscape (269×51) → the short Wide, 4×1 portrait (276×102) →
     * the taller Wide, 2×2 portrait (130×220) → Square, 2×2 landscape (269×117) and 4×2 portrait → Ribbon, 2×3 and up
     * → Tall. A layout gets a taller variant where the common cell has room for more (the "until" line, a second
     * "Up next" row) than the layout's minimum.
     */
    val NEXT_UP: List<Bucket<NextUpLayout>> = listOf(
        Bucket(NextUpLayout.Small, FLOOR),
        Bucket(NextUpLayout.Medium, CellDp(117f, 80f)),
        Bucket(NextUpLayout.Wide, CellDp(250f, 46f)),
        Bucket(NextUpLayout.Medium, CellDp(117f, 100f)),
        Bucket(NextUpLayout.Square, CellDp(111f, 146f)),
        Bucket(NextUpLayout.Wide, CellDp(250f, 84f)),
        Bucket(NextUpLayout.Tall, CellDp(111f, 240f)),
        Bucket(NextUpLayout.Ribbon, CellDp(250f, 110f)),
        Bucket(NextUpLayout.Tall, CellDp(120f, 330f)),
        Bucket(NextUpLayout.Tall, CellDp(250f, 240f)),
    )

    /**
     * Two Clocks, smallest area first: the dial alone, the dial with a caption, and the dial with a now card. 2×2
     * portrait (130×220) → Square, 2×3 portrait → Tall, 2×2 landscape (269×117) → Wide, 4×2 portrait and up → Large.
     */
    val TWO_CLOCKS: List<Bucket<TwoClocksLayout>> = listOf(
        Bucket(TwoClocksLayout.Compact, FLOOR),
        Bucket(TwoClocksLayout.Square, CellDp(101f, 121f)),
        Bucket(TwoClocksLayout.Wide, CellDp(269f, 108f)),
        Bucket(TwoClocksLayout.Tall, CellDp(123f, 248f)),
        Bucket(TwoClocksLayout.Large, CellDp(269f, 220f)),
    )

    /**
     * The bucket the host plays at [size]: the closest of those that fit it, else the smallest. This mirrors
     * `RemoteViews`' choice for a sized mapping (`findBestFitLayout`).
     */
    fun <L> pick(buckets: List<Bucket<L>>, size: CellDp): Bucket<L> =
        buckets.filter { it.min.fitsIn(size) }.minByOrNull { distanceSquared(it.min, size) }
            ?: buckets.minBy { it.min.width * it.min.height }

    /** The smallest bucket of [layout]: what a preview of that layout is fitted for when no size is given. */
    fun <L> smallest(buckets: List<Bucket<L>>, layout: L): Bucket<L> =
        buckets.filter { it.layout == layout }.minBy { it.min.width * it.min.height }

    /**
     * Launcher cells from the Android docs' sizing example ("Determine a size for your widget"): n×m cells are
     * (73n − 16) × (118m − 16) dp in portrait and (142n − 15) × (66m − 15) dp in landscape.
     */
    fun docsCells(columns: IntRange = 1..5, rows: IntRange = 1..4): List<Pair<String, CellDp>> =
        columns.flatMap { n ->
            rows.flatMap { m ->
                listOf(
                    "$n×$m portrait" to CellDp(73f * n - 16, 118f * m - 16),
                    "$n×$m landscape" to CellDp(142f * n - 15, 66f * m - 15),
                )
            }
        }

    private fun distanceSquared(a: CellDp, b: CellDp): Float {
        val dw = abs(a.width - b.width)
        val dh = abs(a.height - b.height)
        return dw * dw + dh * dh
    }
}
