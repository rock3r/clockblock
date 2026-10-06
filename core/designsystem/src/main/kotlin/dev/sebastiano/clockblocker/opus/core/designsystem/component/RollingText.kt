package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.LocalReduceMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import java.time.LocalTime
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Which way changed characters roll: [Up] brings the new text in from below (an odometer counting up). */
enum class RollDirection { Up, Down }

/** One run of a rolling change: still when [from] equals [to], rolling otherwise. */
data class RollSegment(val from: String, val to: String) {
    val rolls: Boolean get() = from != to
}

/**
 * Splits a change from [from] to [to] into still and rolling runs. The common prefix and suffix stay still
 * (`+3½ h` → `+2 h` rolls only `3½` → `2`; the unit stays put). When the changed middle keeps its length, each
 * character rolls on its own and unchanged ones in between stay still (`09:59` → `10:00` keeps the colon).
 */
fun rollSegments(from: String, to: String): List<RollSegment> {
    if (from == to) return if (to.isEmpty()) emptyList() else listOf(RollSegment(to, to))
    val shorter = min(from.length, to.length)
    var prefix = 0
    while (prefix < shorter && from[prefix] == to[prefix]) prefix++
    var suffix = 0
    while (suffix < shorter - prefix && from[from.length - 1 - suffix] == to[to.length - 1 - suffix]) suffix++

    val out = mutableListOf<RollSegment>()
    if (prefix > 0) to.substring(0, prefix).let { out += RollSegment(it, it) }
    val middleFrom = from.substring(prefix, from.length - suffix)
    val middleTo = to.substring(prefix, to.length - suffix)
    if (middleFrom.length == middleTo.length) {
        var i = 0
        while (i < middleTo.length) {
            if (middleFrom[i] == middleTo[i]) {
                var j = i
                while (j < middleTo.length && middleFrom[j] == middleTo[j]) j++
                middleTo.substring(i, j).let { out += RollSegment(it, it) }
                i = j
            } else {
                out += RollSegment(middleFrom[i].toString(), middleTo[i].toString())
                i++
            }
        }
    } else {
        out += RollSegment(middleFrom, middleTo)
    }
    if (suffix > 0) to.substring(to.length - suffix).let { out += RollSegment(it, it) }
    return out
}

/**
 * The number a short readout shows, for choosing a roll direction: its digits read as one number (`14:20` →
 * 1420), plus ½, negative when a minus sign (`-` or `−`) precedes the first digit. Null when there is no number.
 */
fun numericValue(text: String): Double? {
    val first = text.indexOfFirst { it.isDigit() || it == '\u00BD' }
    if (first < 0) return null
    val negative = text.substring(0, first).any { it == '-' || it == '\u2212' }
    var value = text.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
    if ('\u00BD' in text) value += 0.5
    return if (negative) -value else value
}

/** [RollDirection.Down] when the number in [to] is smaller than the one in [from]; [RollDirection.Up] otherwise. */
fun rollDirection(from: String, to: String): RollDirection {
    val a = numericValue(from)
    val b = numericValue(to)
    return if (a != null && b != null && b < a) RollDirection.Down else RollDirection.Up
}

/** Forward in time (the short way round midnight) rolls up; backwards rolls down. */
fun timeRollDirection(from: LocalTime, to: LocalTime): RollDirection {
    val delta = (to.toSecondOfDay() - from.toSecondOfDay()).mod(SecondsPerDay)
    return if (delta <= SecondsPerDay / 2) RollDirection.Up else RollDirection.Down
}

private const val SecondsPerDay = 86_400

/** A measured rolling change: the whole texts plus each run, ready to draw at any progress. */
internal class RollingTextLayout(
    val fromText: String,
    val toText: String,
    val from: TextLayoutResult,
    val to: TextLayoutResult,
    val direction: RollDirection,
    private val segments: List<Pair<TextLayoutResult, TextLayoutResult>>,
) {
    val height: Int = max(from.size.height, to.size.height)
    val firstBaseline: Float = to.firstBaseline

    fun width(progress: Float): Float = lerp(from.size.width.toFloat(), to.size.width.toFloat(), progress.coerceIn(0f, 1f))

    /** Draws the change at [progress] (0 = [fromText], 1 = [toText]) with its top-left corner at [topLeft]. */
    fun draw(scope: DrawScope, topLeft: Offset, progress: Float) = with(scope) {
        val p = progress.coerceIn(0f, 1f)
        // At rest the texts draw as single runs, so kerning matches a plain Text exactly.
        if (p >= 1f || fromText == toText) return@with drawText(to, topLeft = topLeft)
        if (p <= 0f) return@with drawText(from, topLeft = topLeft)
        val h = height.toFloat()
        val dir = if (direction == RollDirection.Up) 1f else -1f
        var x = topLeft.x
        for ((a, b) in segments) {
            if (a === b) {
                drawText(b, topLeft = Offset(x, topLeft.y))
                x += b.size.width
                continue
            }
            val w = lerp(a.size.width.toFloat(), b.size.width.toFloat(), p)
            clipRect(x, topLeft.y, x + w, topLeft.y + h) {
                drawText(a, topLeft = Offset(x, topLeft.y - dir * p * h), alpha = 1f - p)
                drawText(b, topLeft = Offset(x, topLeft.y + dir * (1f - p) * h), alpha = p)
            }
            x += w
        }
    }
}

/** Measures a change from [from] to [to] (single line) for [RollingTextLayout.draw]. */
internal fun TextMeasurer.measureRolling(from: String, to: String, style: TextStyle, direction: RollDirection): RollingTextLayout {
    fun m(s: String) = measure(s, style, maxLines = 1, softWrap = false)
    val toLayout = m(to)
    if (from == to) return RollingTextLayout(from, to, toLayout, toLayout, direction, emptyList())
    val segments = rollSegments(from, to).map { s -> if (s.rolls) m(s.from) to m(s.to) else m(s.to).let { it to it } }
    return RollingTextLayout(from, to, m(from), toLayout, direction, segments)
}

private class PreviousText(var value: String)

/**
 * A short single-line readout whose changed characters roll vertically when [text] changes (design.md §2.4:
 * "digits roll vertically"), while unchanged characters and units stay still. The roll binds to
 * `OpusMotion.dataSpatial()` (Standard, no bounce: the readout is data) with a cross-fade derived from the same
 * progress; under reduce motion, or when [animateChanges] is false, the new text simply replaces the old.
 *
 * Everything that moves is read in layout (width) and draw (offsets), never in composition. Use it for values
 * that change at discrete moments (a day step, a new estimate), not for values that change every frame such as
 * a scrubbed time: pass `animateChanges = false` while scrubbing.
 *
 * TalkBack reads the current [text] (or [contentDescription] when given); it is exposed as semantics text, so
 * `onNodeWithText` finds it in tests.
 *
 * @param direction which way changes roll; null picks it from the numbers in the old and new text ([rollDirection]).
 */
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    direction: RollDirection? = null,
    animateChanges: Boolean = true,
    contentDescription: String? = null,
) {
    val resolved = style.copy(color = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } })
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val reduce = LocalReduceMotion.current
    val motion = OpusTheme.motion
    val previous = remember { PreviousText(text) }
    val roll = remember(text, resolved, direction, measurer) {
        val from = previous.value
        measurer.measureRolling(from, text, resolved, direction ?: rollDirection(from, text))
    }
    SideEffect { previous.value = text }
    val play = animateChanges && !reduce && roll.fromText != roll.toText
    val progress = remember(roll) { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(roll, play) {
        if (play) progress.animateTo(1f, motion.dataSpatial()) else progress.snapTo(1f)
    }
    Spacer(
        modifier
            .clearAndSetSemantics {
                this.text = AnnotatedString(text)
                if (contentDescription != null) this.contentDescription = contentDescription
            }
            .layout { measurable, constraints ->
                val w = ceil(roll.width(progress.value)).toInt().coerceIn(constraints.minWidth, constraints.maxWidth)
                val h = roll.height.coerceIn(constraints.minHeight, constraints.maxHeight)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                val baseline = roll.firstBaseline.roundToInt()
                layout(w, h, mapOf(FirstBaseline to baseline, LastBaseline to baseline)) { placeable.place(0, 0) }
            }
            .drawBehind { roll.draw(this, Offset.Zero, progress.value) },
    )
}

private class PreviousTime(var value: LocalTime)

/**
 * A clock time ("14:20" or "2:20 PM", following the system 12/24-hour setting) that rolls its changed digits
 * forwards or backwards in time ([timeRollDirection]). A building block: on advice surfaces pair it with a
 * secondary zone (`DualTimeText`'s rule: local time plus a secondary zone).
 *
 * @param withMarker include AM/PM in 12-hour mode.
 */
@Composable
fun RollingTimeText(
    time: LocalTime,
    modifier: Modifier = Modifier,
    style: TextStyle = OpusTheme.textStyles.timeTitle,
    color: Color = Color.Unspecified,
    withMarker: Boolean = true,
    animateChanges: Boolean = true,
    contentDescription: String? = null,
) {
    val formatter = rememberTimeFormatter()
    val text = if (withMarker) formatter.formatFull(time) else formatter.format(time)
    val previous = remember { PreviousTime(time) }
    val direction = remember(time) { timeRollDirection(previous.value, time) }
    SideEffect { previous.value = time }
    RollingText(text, modifier, style, color, direction, animateChanges, contentDescription)
}

private class PreviousValue(var value: Float)

/**
 * A metric readout ("+3½ h", "3d", "62%") that rolls up when [value] grows and down when it shrinks; [format]
 * turns the value into text (only the characters that change roll).
 */
@Composable
fun RollingMetricText(
    value: Float,
    modifier: Modifier = Modifier,
    style: TextStyle = OpusTheme.textStyles.timeHeadline,
    color: Color = Color.Unspecified,
    format: (Float) -> String = { it.roundToInt().toString() },
    animateChanges: Boolean = true,
    contentDescription: String? = null,
) {
    val previous = remember { PreviousValue(value) }
    val direction = remember(value) { if (value < previous.value) RollDirection.Down else RollDirection.Up }
    SideEffect { previous.value = value }
    RollingText(format(value), modifier, style, color, direction, animateChanges, contentDescription)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
