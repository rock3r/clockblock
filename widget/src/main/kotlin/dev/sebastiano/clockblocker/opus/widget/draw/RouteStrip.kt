package dev.sebastiano.clockblocker.opus.widget.draw

import dev.sebastiano.clockblocker.opus.core.designsystem.component.DotMatrixFont
import dev.sebastiano.clockblocker.opus.core.designsystem.component.IataLength
import dev.sebastiano.clockblocker.opus.core.designsystem.component.dotMatrixCells
import dev.sebastiano.clockblocker.opus.widget.state.WidgetRoute

/**
 * The trip's route as "LIS → HND" in the design system's dot-matrix face ([DotMatrixFont], the in-app `IataCode`
 * look at label size: no unlit grid, dots nearly touching). Built as a [DrawOp] display list, so the Remote Compose
 * and classic backends draw the same dots.
 *
 * Unit space: one unit = one dot pitch, centred on (0, 0); the strip is [WIDTH] × [HEIGHT] units. Codes are padded
 * to three cells, so the strip never changes width.
 */
object RouteStrip {
    private const val CELL_GAP = 1
    private const val GAP = 2
    private const val CONNECTOR = 7
    private const val DOT_FILL = 0.86f
    private const val CODE_WIDTH = IataLength * DotMatrixFont.Columns + (IataLength - 1) * CELL_GAP

    /** Strip size in dot pitches: two codes and the connector between them. */
    const val WIDTH = CODE_WIDTH + GAP + CONNECTOR + GAP + CODE_WIDTH
    const val HEIGHT = DotMatrixFont.Rows

    /** Dot pitch on the widgets: 1.5 dp, so the codes stand about as tall as the 11 sp header text. */
    const val PITCH_DP = 1.5f

    fun build(route: WidgetRoute, codeColor: Int, connectorColor: Int): List<DrawOp> = buildList {
        val left = -WIDTH / 2f
        addAll(code(route.origin, left, codeColor))
        // A thin line with a chevron: the direction of travel, centred on the dots' middle row.
        val x0 = left + CODE_WIDTH + GAP
        val x1 = x0 + CONNECTOR
        val stroke = 0.6f
        add(DrawOp.Line(x0 + stroke, 0f, x1 - stroke, 0f, connectorColor, stroke))
        add(DrawOp.Line(x1 - 2.2f, -1.8f, x1 - stroke, 0f, connectorColor, stroke))
        add(DrawOp.Line(x1 - 2.2f, 1.8f, x1 - stroke, 0f, connectorColor, stroke))
        addAll(code(route.destination, x1 + GAP, codeColor))
    }

    private fun code(text: String, left: Float, color: Int): List<DrawOp> = buildList {
        val top = -HEIGHT / 2f
        val radius = DOT_FILL / 2f
        dotMatrixCells(text, IataLength).take(IataLength).forEachIndexed { i, c ->
            val x0 = left + i * (DotMatrixFont.Columns + CELL_GAP)
            for (r in 0 until DotMatrixFont.Rows) {
                for (col in 0 until DotMatrixFont.Columns) {
                    if (DotMatrixFont.isLit(c, r, col)) add(DrawOp.Circle(x0 + col + 0.5f, top + r + 0.5f, radius, color))
                }
            }
        }
    }
}
