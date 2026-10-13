package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import kotlin.math.ceil

/**
 * M3 Expressive's connected button group, stacked vertically: a single choice between [options], one above the other,
 * for when the choice sits beside the thing it controls (the sleep dial's Work days / Days off).
 *
 * It is the horizontal group (`ButtonGroup` + `ToggleButton`, as in the app's `ConnectedChoice`) turned on its side:
 * the group's outer corners are fully round and its inner corners small (8 dp), with M3's 2 dp gap between options;
 * a pressed option squares its inner corners a little more (4 dp), and the selected one morphs to a full pill, filled
 * with the toggle button's checked colours. Shape morphs ride the fast spatial spring and colours the effects one
 * (both from `ClockblockTheme.motion`, so under reduce motion the shape snaps; the fill carries the selection either
 * way). State layers (press, hover, focus) come from M3's `ripple()`.
 *
 * Each option is at least 48 dp tall and as wide as the group, a radio button inside one selectable group, so
 * TalkBack reads "Work days, radio button, selected, 1 of 2". Labels are centred and wrap onto at most [maxLines]
 * lines rather than ever clipping or shrinking; size the group with [ConnectedChoiceDefaults.fittedWidth] to know
 * whether whole words fit.
 */
@Composable
fun <T> VerticalConnectedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    tag: (T) -> String? = { null },
    labelStyle: TextStyle = MaterialTheme.typography.labelLarge,
    maxLines: Int = ConnectedChoiceDefaults.MaxLines,
) {
    Column(
        modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            Segment(
                text = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                leading = index == 0,
                trailing = index == options.lastIndex,
                vertical = true,
                labelStyle = labelStyle,
                maxLines = maxLines,
                modifier = Modifier.fillMaxWidth().then(tag(option)?.let { Modifier.testTag(it) } ?: Modifier),
            )
        }
    }
}

/**
 * The same single choice side by side: [VerticalConnectedChoice]'s horizontal twin, for when there is no room beside
 * the thing it controls. Options share the width equally and are as tall as the tallest; labels wrap onto at most
 * [maxLines] lines instead of shrinking (large fonts, narrow windows).
 */
@Composable
fun <T> HorizontalConnectedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    tag: (T) -> String? = { null },
    labelStyle: TextStyle = MaterialTheme.typography.labelLarge,
    maxLines: Int = ConnectedChoiceDefaults.MaxLines,
) {
    Row(
        modifier.height(IntrinsicSize.Min).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            Segment(
                text = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                leading = index == 0,
                trailing = index == options.lastIndex,
                vertical = false,
                labelStyle = labelStyle,
                maxLines = maxLines,
                modifier = Modifier.weight(1f).fillMaxHeight().then(tag(option)?.let { Modifier.testTag(it) } ?: Modifier),
            )
        }
    }
}

/** Measurements for laying out a connected choice before composing it. */
object ConnectedChoiceDefaults {
    /** Labels wrap onto at most this many lines. */
    const val MaxLines: Int = 2

    /** The smallest option: the 48 dp touch target. */
    val MinOptionSize: Dp = 48.dp

    /** Space around a label inside its option. */
    val LabelPaddingHorizontal: Dp = 12.dp
    val LabelPaddingVertical: Dp = 8.dp

    /**
     * The narrowest width (px), up to [maxWidthPx], of a [VerticalConnectedChoice] in which every one of [labels]
     * reads whole in [style]: on one line when that fits, else wrapped onto at most [maxLines] lines without breaking
     * a word. Null when even that doesn't fit (lay the choice out another way).
     */
    fun Density.fittedWidth(measurer: TextMeasurer, labels: List<String>, style: TextStyle, maxWidthPx: Int, maxLines: Int = MaxLines): Int? {
        val chrome = chromePx()
        val room = maxWidthPx - chrome
        if (room <= 0) return null
        val singleLine = labels.maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }
        if (singleLine <= room) return maxOf(singleLine + chrome, MinOptionSize.roundToPx())
        val widestWord = labels.flatMap { it.split(' ', '\u00A0') }.filter { it.isNotEmpty() }
            .maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }
        if (widestWord > room) return null
        // Wrapped at the full room, every label must stay within maxLines; then shrink to the widest wrapped line.
        val layouts = labels.map { measurer.measure(it, style, constraints = Constraints(maxWidth = room)) }
        if (layouts.any { it.lineCount > maxLines }) return null
        val widestLine = layouts.maxOf { layout -> (0 until layout.lineCount).maxOf { ceil(layout.getLineRight(it) - layout.getLineLeft(it)).toInt() } }
        return maxOf(widestLine, widestWord) + chrome
    }

    /** A [VerticalConnectedChoice]'s height (px) at [widthPx] (from [fittedWidth]). */
    fun Density.verticalHeight(measurer: TextMeasurer, labels: List<String>, style: TextStyle, widthPx: Int, maxLines: Int = MaxLines): Int =
        labels.sumOf { optionHeight(measurer, it, style, widthPx, maxLines) } +
            ButtonGroupDefaults.ConnectedSpaceBetween.roundToPx() * (labels.size - 1).coerceAtLeast(0)

    /** A [HorizontalConnectedChoice]'s height (px) at [widthPx]: its tallest option. */
    fun Density.horizontalHeight(measurer: TextMeasurer, labels: List<String>, style: TextStyle, widthPx: Int, maxLines: Int = MaxLines): Int {
        val gaps = ButtonGroupDefaults.ConnectedSpaceBetween.roundToPx() * (labels.size - 1).coerceAtLeast(0)
        val optionWidth = (widthPx - gaps) / labels.size.coerceAtLeast(1)
        return labels.maxOf { optionHeight(measurer, it, style, optionWidth, maxLines) }
    }

    private fun Density.optionHeight(measurer: TextMeasurer, label: String, style: TextStyle, widthPx: Int, maxLines: Int): Int {
        val room = (widthPx - chromePx()).coerceAtLeast(1)
        val text = measurer.measure(label, style, maxLines = maxLines, constraints = Constraints(maxWidth = room)).size.height
        return maxOf(MinOptionSize.roundToPx(), text + (LabelPaddingVertical * 2).roundToPx())
    }

    /** An option's horizontal padding, plus a dp of slack (a label fitted to the pixel can still wrap). */
    private fun Density.chromePx(): Int = ceil((LabelPaddingHorizontal * 2 + 2.dp).toPx()).toInt()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Segment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: Boolean,
    trailing: Boolean,
    vertical: Boolean,
    labelStyle: TextStyle,
    maxLines: Int,
    modifier: Modifier,
) {
    val motion = ClockblockTheme.motion
    // The toggle button's colours, with the unchecked container lifted a step (as ConnectedChoice does), so unselected
    // options still read as buttons on surfaceContainer cards.
    val colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    // 0 = resting (small inner corners), 1 = fully round (selected); pressing an unselected option squares them a little.
    val round = remember { Animatable(if (selected) 1f else 0f) }
    val press = remember { Animatable(0f) }
    LaunchedEffect(selected, motion) { round.animateTo(if (selected) 1f else 0f, motion.glyphMorph()) }
    LaunchedEffect(pressed, selected, motion) { press.animateTo(if (pressed && !selected) 1f else 0f, motion.glyphMorph()) }
    val fill by animateColorAsState(if (selected) colors.checkedContainerColor else colors.containerColor, motion.colour(), label = "segmentFill")
    val ink by animateColorAsState(if (selected) colors.checkedContentColor else colors.contentColor, motion.colour(), label = "segmentInk")
    Box(
        modifier
            .heightIn(min = ConnectedChoiceDefaults.MinOptionSize)
            // Animated corners are read here, in the layer block, never in composition.
            .graphicsLayer {
                val full = size.minDimension / 2f
                val inner = InnerCorner.toPx() + (PressedInnerCorner.toPx() - InnerCorner.toPx()) * press.value
                val innerNow = (inner + (full - inner) * round.value).coerceAtMost(full)
                val first = if (leading) full else innerNow
                val last = if (trailing) full else innerNow
                shape = if (vertical) {
                    RoundedCornerShape(topStart = first, topEnd = first, bottomStart = last, bottomEnd = last)
                } else {
                    RoundedCornerShape(topStart = first, bottomStart = first, topEnd = last, bottomEnd = last)
                }
                clip = true
            }
            .drawBehind { drawRect(fill) }
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = ConnectedChoiceDefaults.LabelPaddingHorizontal, vertical = ConnectedChoiceDefaults.LabelPaddingVertical),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = labelStyle.copy(textAlign = TextAlign.Center),
            color = { ink },
            maxLines = maxLines,
        )
    }
}

/** M3's connected inner corner (small), and the tighter one while pressed (extra small). */
private val InnerCorner: Dp = 8.dp
private val PressedInnerCorner: Dp = 4.dp
