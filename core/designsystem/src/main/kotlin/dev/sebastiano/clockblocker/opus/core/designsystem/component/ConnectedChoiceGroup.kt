package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

/**
 * A connected single-choice button group (M3 Expressive `ToggleButton`s with the connected shapes), shared by
 * onboarding (Head start, the sleep dial's Work days / Days off when it can't stand beside the dial) and Settings
 * (theme, reminder lead, the You editor's tabs). Each option is a radio button inside one selectable group.
 *
 * When more than two options can't each get [minOptionWidth] (scaled with the font) they split into two connected
 * rows. Labels wrap onto at most [maxLines] lines, only between words, and step down in size (never below 10 sp) only
 * when even that doesn't fit, so a word is never broken. Options in a row are as tall as the tallest. [checkIcon],
 * when set, adds a tick to the selected option.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkIcon: Painter? = null,
    minOptionWidth: Dp = 64.dp,
    maxLines: Int = 1,
    labelStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = ButtonGroupDefaults.ConnectedSpaceBetween
        val fontScale = LocalDensity.current.fontScale
        val perOption = (maxWidth - gap * (options.size - 1)) / options.size
        val rows = if (options.size > 2 && perOption < minOptionWidth * fontScale) {
            options.chunked((options.size + 1) / 2)
        } else {
            listOf(options)
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { row ->
                EqualHeights(Modifier.fillMaxWidth(), vertical = false) {
                    row.forEachIndexed { index, option ->
                        val checked = option == selected
                        ToggleButton(
                            checked = checked,
                            onCheckedChange = { onSelect(option) },
                            enabled = enabled,
                            colors = choiceColors(),
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                row.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            contentPadding = ConnectedChoiceDefaults.HorizontalContentPadding,
                            modifier = Modifier
                                .heightIn(min = ConnectedChoiceDefaults.MinOptionSize)
                                .semantics { role = Role.RadioButton }
                                .testTag(tag(option)),
                        ) {
                            if (checked && checkIcon != null) {
                                Icon(checkIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            ChoiceLabel(label(option), labelStyle, maxLines)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The same connected choice stacked vertically, one option above the other, for when it sits beside the thing it
 * controls (the sleep dial's Work days / Days off). It is M3's `ToggleButton` with the connected shapes turned on their
 * side: the group's outer corners are fully round and its inner corners small (8 dp), with M3's 2 dp gap between
 * options; a pressed option squares its inner corners a little more (4 dp), and the selected one morphs to a full
 * pill with the toggle's checked colours. Shape, colour and state layers are the toggle button's own.
 *
 * Options are as wide as the group, at least 48 dp tall and all as tall as the tallest. Labels wrap like
 * [ConnectedChoice]'s, onto at most [maxLines] lines and only between words; size the group with
 * [ConnectedChoiceDefaults.fittedWidth] so they keep their full size.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> VerticalConnectedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.labelLarge,
    maxLines: Int = ConnectedChoiceDefaults.MaxLines,
) {
    EqualHeights(modifier.selectableGroup(), vertical = true) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                colors = choiceColors(),
                shapes = verticalShapes(leading = index == 0, trailing = index == options.lastIndex),
                contentPadding = ConnectedChoiceDefaults.VerticalContentPadding,
                modifier = Modifier
                    .heightIn(min = ConnectedChoiceDefaults.MinOptionSize)
                    .semantics { role = Role.RadioButton }
                    .testTag(tag(option)),
            ) {
                ChoiceLabel(label(option), labelStyle, maxLines)
            }
        }
    }
}

/** Measurements for laying out a connected choice before composing it. */
object ConnectedChoiceDefaults {
    /** [VerticalConnectedChoice] labels wrap onto at most this many lines. */
    const val MaxLines: Int = 2

    /** The smallest option: the 48 dp touch target. */
    val MinOptionSize: Dp = 48.dp

    /** Space around a label inside a [ConnectedChoice] option. */
    val HorizontalContentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 4.dp)

    /** Space around a label inside a [VerticalConnectedChoice] option. */
    val VerticalContentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

    /**
     * The narrowest width (px), up to [maxWidthPx], of a [VerticalConnectedChoice] in which every one of [labels]
     * reads whole in [style]: on one line when that fits, else wrapped onto at most [maxLines] lines without breaking
     * a word. Null when even that doesn't fit (lay the choice out another way).
     */
    fun Density.fittedWidth(measurer: TextMeasurer, labels: List<String>, style: TextStyle, maxWidthPx: Int, maxLines: Int = MaxLines): Int? {
        val chrome = chromePx(VerticalContentPadding)
        val room = maxWidthPx - chrome
        if (room <= 0) return null
        val singleLine = labels.maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }
        if (singleLine <= room) return maxOf(singleLine + chrome, MinOptionSize.roundToPx())
        val widestWord = labels.flatMap { it.split(' ', '\u00A0') }.filter { it.isNotEmpty() }
            .maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }
        if (widestWord > room) return null
        // Wrapped, the narrowest width is the widest word's, as long as every label still fits its lines there; else
        // the widest wrapped line at the full room.
        val atWord = labels.map { measurer.measure(it, style, constraints = Constraints(maxWidth = widestWord)) }
        if (atWord.none { it.lineCount > maxLines }) return widestWord + chrome
        val layouts = labels.map { measurer.measure(it, style, constraints = Constraints(maxWidth = room)) }
        if (layouts.any { it.lineCount > maxLines }) return null
        val widestLine = layouts.maxOf { layout -> (0 until layout.lineCount).maxOf { ceil(layout.getLineRight(it) - layout.getLineLeft(it)).toInt() } }
        return maxOf(widestLine, widestWord) + chrome
    }

    /** A [VerticalConnectedChoice]'s height (px) at [widthPx] (from [fittedWidth]): every option as tall as the tallest. */
    fun Density.verticalHeight(measurer: TextMeasurer, labels: List<String>, style: TextStyle, widthPx: Int, maxLines: Int = MaxLines): Int =
        labels.maxOf { optionHeight(measurer, it, style, widthPx, maxLines, VerticalContentPadding) } * labels.size +
            ButtonGroupDefaults.ConnectedSpaceBetween.roundToPx() * (labels.size - 1).coerceAtLeast(0)

    /** A one-row [ConnectedChoice]'s height (px) at [widthPx]: its tallest option. */
    fun Density.horizontalHeight(measurer: TextMeasurer, labels: List<String>, style: TextStyle, widthPx: Int, maxLines: Int): Int {
        val gaps = ButtonGroupDefaults.ConnectedSpaceBetween.roundToPx() * (labels.size - 1).coerceAtLeast(0)
        val optionWidth = (widthPx - gaps) / labels.size.coerceAtLeast(1)
        return labels.maxOf { optionHeight(measurer, it, style, optionWidth, maxLines, HorizontalContentPadding) }
    }

    private fun Density.optionHeight(measurer: TextMeasurer, label: String, style: TextStyle, widthPx: Int, maxLines: Int, padding: PaddingValues): Int {
        val room = (widthPx - chromePx(padding)).coerceAtLeast(1)
        val text = measurer.measure(label, style, maxLines = maxLines, constraints = Constraints(maxWidth = room)).size.height
        return maxOf(MinOptionSize.roundToPx(), text + (padding.calculateTopPadding() + padding.calculateBottomPadding()).roundToPx())
    }

    /** An option's horizontal padding, plus a dp of slack (a label fitted to the pixel can still wrap). */
    private fun Density.chromePx(padding: PaddingValues): Int =
        ceil((padding.calculateLeftPadding(LayoutDirection.Ltr) * 2 + 2.dp).toPx()).toInt()
}

/** The toggle button's colours, the unchecked container lifted a step so options still read as buttons on cards. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun choiceColors() = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)

/** M3's connected shapes turned on their side: round outer corners on top and bottom, small inner ones between. */
private fun verticalShapes(leading: Boolean, trailing: Boolean): ToggleButtonShapes {
    fun shape(inner: CornerSize): RoundedCornerShape {
        val top = if (leading) FullCorner else inner
        val bottom = if (trailing) FullCorner else inner
        return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
    }
    return ToggleButtonShapes(shape = shape(InnerCorner), pressedShape = shape(PressedInnerCorner), checkedShape = shape(FullCorner))
}

/**
 * An option's label: centred, wrapped onto at most [maxLines] lines between words, and stepped down in size only when
 * a word wouldn't otherwise fit (see [WholeWordAutoSize]).
 */
@Composable
private fun ChoiceLabel(text: String, style: TextStyle, maxLines: Int) {
    Text(
        text,
        style = style.copy(textAlign = TextAlign.Center),
        maxLines = maxLines,
        autoSize = WholeWordAutoSize(minFontSize = MinLabelFontSize, maxFontSize = style.fontSize.takeIf { it.isSp } ?: LocalTextStyle.current.fontSize),
    )
}

/**
 * Children laid out in a row ([vertical] false, sharing the width equally) or a column (each as wide as the group),
 * all as tall as the tallest one's intrinsic height, so a wrapped label never leaves its neighbour shorter.
 */
@Composable
private fun EqualHeights(modifier: Modifier, vertical: Boolean, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = ButtonGroupDefaults.ConnectedSpaceBetween.roundToPx()
        val n = measurables.size.coerceAtLeast(1)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else measurables.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) } * if (vertical) 1 else n
        // In a row the options share the width, the leftover pixels going to the first ones, as Row's weights do.
        val shared = if (vertical) width else (width - gap * (n - 1)) / n
        val leftover = if (vertical) 0 else width - gap * (n - 1) - shared * n
        fun widthOf(i: Int) = shared + if (i < leftover) 1 else 0
        val itemHeight = maxOf(measurables.maxOf { it.minIntrinsicHeight(shared) }, minOptionSizePx())
        val placeables = measurables.mapIndexed { i, it -> it.measure(Constraints.fixed(widthOf(i), itemHeight)) }
        val height = if (vertical) itemHeight * n + gap * (n - 1) else itemHeight
        layout(width, height) {
            var x = 0
            placeables.forEachIndexed { i, p ->
                if (vertical) {
                    p.placeRelative(0, i * (itemHeight + gap))
                } else {
                    p.placeRelative(x, 0)
                    x += p.width + gap
                }
            }
        }
    }
}

/**
 * Text auto size that never breaks a word: the largest size from [maxFontSize] down to [minFontSize], in 0.5 sp steps,
 * at which the text fits its lines without overflowing and every line ends between words. Below [minFontSize] it gives
 * up and lays out at the minimum (a single word wider than the option at 10 sp).
 */
private data class WholeWordAutoSize(val minFontSize: TextUnit, val maxFontSize: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = maxFontSize.value
        while (size > minFontSize.value) {
            val layout = performLayout(constraints, text, size.sp)
            if (!layout.hasVisualOverflow && !layout.breaksAWord()) return size.sp
            size -= FontStep
        }
        return minFontSize
    }
}

/** Whether a line of this layout ends inside a word (a soft break between two letters or digits). */
private fun TextLayoutResult.breaksAWord(): Boolean {
    val text = layoutInput.text.text
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line)
        if (end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()) return true
    }
    return false
}

/** The 48 dp touch target in px. */
private fun Density.minOptionSizePx(): Int = ConnectedChoiceDefaults.MinOptionSize.roundToPx()

private const val FontStep = 0.5f
private val MinLabelFontSize: TextUnit = 10.sp

/** Fully round, M3's connected inner corner (small), and the tighter one while pressed (extra small). */
private val FullCorner = CornerSize(50)
private val InnerCorner = CornerSize(8.dp)
private val PressedInnerCorner = CornerSize(4.dp)
