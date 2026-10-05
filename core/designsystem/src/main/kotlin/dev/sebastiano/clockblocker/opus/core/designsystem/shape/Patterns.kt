package dev.sebastiano.clockblocker.opus.core.designsystem.shape

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/*
 * Colour-blind-safe fills (design.md §2.4). Each fills the *current clip*: wrap calls in `clipPath(...)`.
 */

/** 45° diagonal hatch (Avoid light). */
fun DrawScope.drawHatch(color: Color, spacing: Dp = 5.dp, strokeWidth: Dp = 1.5.dp) {
    val step = spacing.toPx()
    val w = size.width
    val h = size.height
    var x = -h
    while (x < w) {
        drawLine(color, Offset(x, h), Offset(x + h, 0f), strokeWidth.toPx(), cap = StrokeCap.Round)
        x += step
    }
}

/** Sparse star dots (Sleep): a deterministic, slightly irregular lattice. */
fun DrawScope.drawStarDots(color: Color, spacing: Dp = 9.dp, radius: Dp = 0.9.dp) {
    val step = spacing.toPx()
    val r = radius.toPx()
    var row = 0
    var y = step / 2f
    while (y < size.height) {
        var x = if (row % 2 == 0) step / 2f else step
        var col = 0
        while (x < size.width) {
            val jitter = ((row * 31 + col * 17) % 7 - 3) / 7f * step * 0.25f
            val big = (row + col) % 3 == 0
            drawCircle(color, if (big) r * 1.5f else r, Offset(x + jitter, y - jitter))
            x += step
            col++
        }
        y += step * 0.866f
        row++
    }
}

/** Rounded dots on a hex grid (Nap). */
fun DrawScope.drawRoundDots(color: Color, spacing: Dp = 6.dp, radius: Dp = 1.4.dp) {
    val step = spacing.toPx()
    val r = radius.toPx()
    var row = 0
    var y = step / 2f
    while (y < size.height + step) {
        var x = if (row % 2 == 0) step / 2f else step
        while (x < size.width + step) {
            drawCircle(color, r, Offset(x, y))
            x += step
        }
        y += step * sqrt(3f) / 2f
        row++
    }
}

/** A single diagonal strike across the area (Avoid caffeine). */
fun DrawScope.drawStrike(color: Color, strokeWidth: Dp = 2.dp, inset: Float = 0.18f) {
    val w = size.width
    val h = size.height
    drawLine(
        color,
        Offset(w * inset, h * (1f - inset)),
        Offset(w * (1f - inset), h * inset),
        strokeWidth.toPx(),
        cap = StrokeCap.Round,
    )
}
