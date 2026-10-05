package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import kotlin.math.max

/** The separator between two inline parts of one line ("62% adapted · about 2 days to go"). */
internal const val Separator = " · "

/** Test tag on the separator [InlineOrStacked] draws while the parts share a line. */
internal const val InlineSeparatorTag = "plan:inline-separator"

/**
 * Two parts of one statement: on one line, baseline-aligned with a " · " between them, when both fit; otherwise
 * stacked, with no separator, so a large font never leaves a dangling bullet or squeezes the second part into a
 * narrow column beside the first (device QA #9, #13).
 */
@Composable
internal fun InlineOrStacked(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    separator: @Composable () -> Unit = { Text(Separator, Modifier.testTag(InlineSeparatorTag).clearAndSetSemantics {}) },
) {
    Layout(contents = listOf(first, separator, second), modifier = modifier) { (firsts, separators, seconds), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val a = firsts.first()
        val s = separators.first()
        val b = seconds.first()
        val fits = constraints.maxWidth == Constraints.Infinity ||
            a.maxIntrinsicWidth(Constraints.Infinity) + s.maxIntrinsicWidth(Constraints.Infinity) +
            b.maxIntrinsicWidth(Constraints.Infinity) <= constraints.maxWidth
        val pa = a.measure(loose)
        if (fits) {
            val ps = s.measure(loose)
            val pb = b.measure(loose.copy(maxWidth = (loose.maxWidth - pa.width - ps.width).coerceAtLeast(0)))
            val row = listOf(pa, ps, pb)
            val baseline = row.maxOf { it.baselineOrHeight() }
            val height = row.maxOf { baseline - it.baselineOrHeight() + it.height }
            val width = row.sumOf { it.width }
            layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
                var x = 0
                row.forEach { p ->
                    p.placeRelative(x, baseline - p.baselineOrHeight())
                    x += p.width
                }
            }
        } else {
            val pb = b.measure(loose)
            val width = max(pa.width, pb.width)
            layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), (pa.height + pb.height).coerceIn(constraints.minHeight, constraints.maxHeight)) {
                pa.placeRelative(0, 0)
                pb.placeRelative(0, pa.height)
            }
        }
    }
}

private fun Placeable.baselineOrHeight(): Int = this[FirstBaseline].takeIf { it != androidx.compose.ui.layout.AlignmentLine.Unspecified } ?: height

/**
 * One line of "[optional] · [essential]" that drops [optional] (not [essential]) when the line can't hold both,
 * so a large font loses "Day 1 · Adapting" before it ellipsises "body 2½ h ahead" (device QA #10). TalkBack
 * reads what is shown; the stage is repeated by the rail's day header.
 */
@Composable
internal fun PriorityLine(
    optional: String,
    essential: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    Layout(
        content = {
            Text(optional + Separator, style = style, color = color, maxLines = 1)
            Text(essential, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val (o, e) = measurables
        val both = constraints.maxWidth == Constraints.Infinity ||
            o.maxIntrinsicWidth(Constraints.Infinity) + e.maxIntrinsicWidth(Constraints.Infinity) <= constraints.maxWidth
        if (both) {
            val po = o.measure(loose)
            val pe = e.measure(loose.copy(maxWidth = (loose.maxWidth - po.width).coerceAtLeast(0)))
            layout((po.width + pe.width).coerceIn(constraints.minWidth, constraints.maxWidth), max(po.height, pe.height)) {
                po.placeRelative(0, 0)
                pe.placeRelative(po.width, 0)
            }
        } else {
            val pe = e.measure(loose)
            layout(pe.width.coerceIn(constraints.minWidth, constraints.maxWidth), pe.height) { pe.placeRelative(0, 0) }
        }
    }
}
