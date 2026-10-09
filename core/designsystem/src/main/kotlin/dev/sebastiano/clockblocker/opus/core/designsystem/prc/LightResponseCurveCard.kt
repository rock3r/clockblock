package dev.sebastiano.clockblocker.opus.core.designsystem.prc

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.R
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.rememberDialPalette
import dev.sebastiano.clockblocker.opus.core.designsystem.prc.LightResponseCurve.Effect
import dev.sebastiano.clockblocker.opus.core.designsystem.prc.LightResponseCurve.Tick
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.AdvicePattern
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.pattern
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Test tags of [LightResponseCurveCard]. */
object LightResponseCurveTags {
    /** The draggable curve; carries the readout as its state and the Earlier/Later/Reset actions. */
    const val Curve = "light_curve"
}

/**
 * The light response curve card (issue #20): a schematic human phase response curve to light around the body's
 * coldest point, with a sun the user drags along it to see what light does at each hour. Shown in the Why sheet
 * of light advice (with that block's [window]) and in About ("How the plan works", no window).
 *
 * A rare surface, so the handle may grow on the Expressive container spring while held; where it sits is data, so
 * jumps (tap, TalkBack actions, Reset) settle without overshoot, and everything snaps when motion is reduced. The
 * lobes take the dial's sky colours (dawn for earlier, twilight for later) and carry direct labels, so no legend
 * is needed and meaning never rests on hue. The readout stays qualitative ([LightResponseCurve.effectAt]).
 *
 * @param window the advice block on the curve (see [LightResponseCurve.window]), or null.
 * @param windowType the block's advice type, which colours the window band.
 */
@Composable
fun LightResponseCurveCard(
    modifier: Modifier = Modifier,
    window: LightResponseCurve.Window? = null,
    windowType: AdviceType? = null,
) {
    val home = LightResponseCurve.snap(window?.middle ?: LightResponseCurve.PeakAdvanceAtHours)
    var hours by rememberSaveable(home) { mutableDoubleStateOf(home) }
    val position = remember(home) { Animatable(hours.toFloat()) }
    val motion by rememberUpdatedState(ClockblockTheme.motion)
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    fun select(target: Double): Double {
        val snapped = LightResponseCurve.snap(target)
        when (LightResponseCurve.tickBetween(hours, snapped)) {
            Tick.ColdestPoint -> view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            Tick.Hour -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            null -> Unit
        }
        hours = snapped
        return snapped
    }

    fun jumpTo(target: Double) {
        val snapped = select(target)
        scope.launch { position.animateTo(snapped.toFloat(), motion.dataSpatial()) }
    }

    val positionText = positionText(hours, window)
    val effectText = stringResource(LightResponseCurve.effectAt(hours).textRes)
    val resetLabel = stringResource(if (window != null) R.string.light_curve_action_reset_block else R.string.light_curve_action_reset)
    val earlierLabel = stringResource(R.string.light_curve_action_earlier)
    val laterLabel = stringResource(R.string.light_curve_action_later)
    val description = stringResource(R.string.light_curve_description)

    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.light_curve_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.light_curve_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            CurvePlot(
                position = { position.value },
                window = window,
                windowType = windowType,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag(LightResponseCurveTags.Curve)
                    .semantics {
                        contentDescription = description
                        stateDescription = "$positionText. $effectText"
                        customActions = listOf(
                            CustomAccessibilityAction(earlierLabel) { jumpTo(hours - 1); true },
                            CustomAccessibilityAction(laterLabel) { jumpTo(hours + 1); true },
                            CustomAccessibilityAction(resetLabel) { jumpTo(home); true },
                        )
                    },
                onTap = { jumpTo(it) },
                onDrag = { raw ->
                    select(raw)
                    scope.launch { position.snapTo(raw.coerceIn(-LightResponseCurve.SpanHours, LightResponseCurve.SpanHours).toFloat()) }
                },
                onDragEnd = { jumpTo(hours) },
            )
            AxisLabels()
            // The plot node already speaks the readout (state description); the text is for sighted users.
            Column(Modifier.padding(top = 8.dp).clearAndSetSemantics {}) {
                Text(positionText, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                Text(effectText, style = MaterialTheme.typography.titleMediumEmphasized, color = colors.onSurface)
            }
            Text(
                stringResource(R.string.light_curve_source),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun CurvePlot(
    position: () -> Float,
    window: LightResponseCurve.Window?,
    windowType: AdviceType?,
    onTap: (Double) -> Unit,
    onDrag: (Double) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val sky = rememberDialPalette().sky
    val earlierFill = Color(sky.dawn.value)
    val laterFill = Color(sky.twilight.value)
    val sun = ClockblockTheme.adviceColors[AdviceType.SeeBrightLight]
    val band = windowType?.let { ClockblockTheme.adviceColors[it] }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurface, textAlign = TextAlign.Center)
    val bandStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface)
    val earlierText = stringResource(R.string.light_curve_earlier)
    val laterText = stringResource(R.string.light_curve_later)
    val bandText = stringResource(R.string.light_curve_this_block)
    val density = LocalDensity.current
    // Held by a press or a drag (tracked apart: a drag cancels the press before it starts).
    var pressing by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val handleScale by animateFloatAsState(
        if (pressing || dragging) HeldScale else 1f,
        ClockblockTheme.motion.containerSpatial(),
        label = "lightCurveHandle",
    )
    val tap by rememberUpdatedState(onTap)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        val labelWidth = (widthPx * LabelWidthFraction).roundToInt()
        val earlier = remember(earlierText, labelStyle, labelWidth) { measurer.measure(earlierText, labelStyle, constraints = Constraints(maxWidth = labelWidth)) }
        val later = remember(laterText, labelStyle, labelWidth) { measurer.measure(laterText, labelStyle, constraints = Constraints(maxWidth = labelWidth)) }
        val bandLabel = remember(bandText, bandStyle) { measurer.measure(bandText, bandStyle) }
        val geometry = with(density) {
            PlotGeometry(
                width = widthPx.toFloat(),
                inset = HandleHalo.toPx(),
                topLabel = earlier.size.height + LabelGap.toPx() + HandleRadius.toPx() * HeldScale,
                bottomLabel = later.size.height + LabelGap.toPx() + HandleRadius.toPx() * HeldScale,
                curveHeight = CurveHeight.toPx(),
            )
        }
        val heightDp = with(density) { geometry.height.toDp() }
        val latestGeometry by rememberUpdatedState(geometry)

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(heightDp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressing = true
                            tryAwaitRelease()
                            pressing = false
                        },
                        onTap = { tap(latestGeometry.hoursAt(it.x)) },
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            drag(latestGeometry.hoursAt(it.x))
                        },
                        onDragEnd = {
                            dragging = false
                            dragEnd()
                        },
                        onDragCancel = {
                            dragging = false
                            dragEnd()
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            drag(latestGeometry.hoursAt(change.position.x))
                        },
                    )
                },
        ) {
            val g = geometry
            drawLobe(g, from = -LightResponseCurve.SpanHours, to = 0.0, laterFill)
            drawLobe(g, from = 0.0, to = LightResponseCurve.SpanHours, earlierFill)
            // The block's window: a tinted band over the lobes, edged in the advice colour, hatched for Avoid light
            // (the app's colour-blind pattern). A block that wraps past ±12 h is two bands, unedged at the seam.
            if (window != null && band != null && windowType != null) {
                val edge = if (band.hasOutline) band.outline else band.color
                for (segment in window.segments) {
                    val left = g.xAt(segment.start)
                    val right = g.xAt(segment.endInclusive)
                    drawRect(band.color.copy(alpha = BandAlpha), Offset(left, g.curveTop), Size(right - left, g.curveBottom - g.curveTop))
                    if (windowType.pattern == AdvicePattern.Hatch) drawHatch(left, right, g.curveTop, g.curveBottom, edge)
                    for (h in listOf(segment.start, segment.endInclusive)) {
                        if (window.isSeam(h)) continue
                        val x = g.xAt(h)
                        drawLine(edge, Offset(x, g.curveTop), Offset(x, g.curveBottom), 1.5.dp.toPx())
                    }
                }
            }
            // Axis, three-hour ticks and the coldest point.
            drawLine(colors.outline, Offset(g.inset, g.zeroY), Offset(g.width - g.inset, g.zeroY), 1.dp.toPx())
            for (h in -12..12 step 3) {
                val x = g.xAt(h.toDouble())
                drawLine(colors.outline, Offset(x, g.zeroY - 3.dp.toPx()), Offset(x, g.zeroY + 3.dp.toPx()), 1.dp.toPx())
            }
            drawLine(
                colors.onSurfaceVariant,
                Offset(g.xAt(0.0), g.curveTop),
                Offset(g.xAt(0.0), g.curveBottom),
                1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            drawPath(g.curvePath(-LightResponseCurve.SpanHours, LightResponseCurve.SpanHours), colors.onSurface, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            // Direct labels, next to the extreme of their lobe.
            drawLabel(earlier, g.xAt(LightResponseCurve.PeakAdvanceAtHours), top = 0f, g)
            drawLabel(later, g.xAt(LightResponseCurve.PeakDelayAtHours), top = g.height - later.size.height, g)
            // The band's label, on a pill by the zero line on the side its lobe leaves empty: below the line after
            // the coldest point, above it before.
            if (window != null && band != null) {
                val pad = 4.dp.toPx()
                val pill = Size(bandLabel.size.width + 2 * pad, bandLabel.size.height.toFloat())
                val segment = window.largest
                val after = segment.start + segment.endInclusive >= 0
                val gap = 5.dp.toPx()
                // Kept on its own side of the coldest point, so it never hides the crossover.
                val coldest = g.xAt(0.0)
                val (minLeft, maxLeft) = if (after) coldest + gap to g.width - pill.width else 0f to coldest - gap - pill.width
                val centred = (g.xAt(segment.start) + g.xAt(segment.endInclusive) - pill.width) / 2
                val left = if (minLeft <= maxLeft) centred.coerceIn(minLeft, maxLeft) else centred.coerceIn(0f, g.width - pill.width)
                val top = if (after) g.zeroY + gap else g.zeroY - gap - pill.height
                drawRoundRect(colors.surfaceContainerHighest, Offset(left, top), pill, CornerRadius(pill.height / 2))
                drawText(bandLabel, topLeft = Offset(left + pad, top))
            }
            // The sun handle, read in draw so dragging never recomposes.
            val h = position().toDouble()
            val center = Offset(g.xAt(h), g.yAt(LightResponseCurve.shiftAt(h)))
            val scale = handleScale
            drawLine(colors.onSurface.copy(alpha = 0.5f), center, Offset(center.x, g.zeroY), 1.5.dp.toPx())
            drawCircle(sun.color.copy(alpha = 0.3f), HandleHalo.toPx() * scale, center)
            drawCircle(sun.color, HandleRadius.toPx() * scale, center)
            drawCircle(if (sun.hasOutline) sun.outline else sun.onColor, HandleRadius.toPx() * scale, center, style = Stroke(1.5.dp.toPx()))
        }
    }
}

/**
 * The axis under the plot: "your body's coldest point" centred under the crossover on one line, with the ends
 * ("12 h before" / "12 h after") shortening to "−12 h" / "+12 h" when the row is too tight (narrow sheets, big fonts),
 * and dropping out when even those would collide.
 */
@Composable
private fun AxisLabels() {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val coldest = stringResource(R.string.light_curve_coldest)
    val long = stringResource(R.string.light_curve_axis_before) to stringResource(R.string.light_curve_axis_after)
    val short = stringResource(R.string.light_curve_axis_before_short) to stringResource(R.string.light_curve_axis_after_short)
    val gap = with(LocalDensity.current) { AxisGap.toPx() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val half = constraints.maxWidth / 2f
        // Long ends, else short ends, else no ends (the readout still gives the hours), so the centre stays one line.
        val ends: Pair<String, String>? = remember(style, half, long, short, coldest) {
            fun width(text: String) = measurer.measure(text, style, maxLines = 1).size.width
            fun fits(pair: Pair<String, String>) = width(coldest) / 2f + maxOf(width(pair.first), width(pair.second)) + gap <= half
            when {
                fits(long) -> long
                fits(short) -> short
                else -> null
            }
        }
        if (ends != null) Text(ends.first, style = style, color = color, maxLines = 1, modifier = Modifier.align(Alignment.CenterStart))
        Text(
            coldest,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )
        if (ends != null) Text(ends.second, style = style, color = color, maxLines = 1, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/** Pixel layout of the plot: label rows above and below, the curve between, the zero line where the lobes meet. */
private class PlotGeometry(val width: Float, val inset: Float, topLabel: Float, bottomLabel: Float, curveHeight: Float) {
    val curveTop = topLabel
    val curveBottom = topLabel + curveHeight
    val height = curveBottom + bottomLabel
    private val perHour = (curveBottom - curveTop) / (LightResponseCurve.PeakAdvanceHours + LightResponseCurve.PeakDelayHours)
    val zeroY = curveTop + LightResponseCurve.PeakAdvanceHours.toFloat() * perHour.toFloat()

    fun xAt(hours: Double): Float = inset + ((hours + LightResponseCurve.SpanHours) / (2 * LightResponseCurve.SpanHours)).toFloat() * (width - 2 * inset)

    fun hoursAt(x: Float): Double = ((x - inset) / (width - 2 * inset)) * 2 * LightResponseCurve.SpanHours - LightResponseCurve.SpanHours

    fun yAt(shift: Double): Float = zeroY - (shift * perHour).toFloat()

    fun curvePath(from: Double, to: Double, closeToAxis: Boolean = false): Path = Path().apply {
        val steps = (abs(to - from) * SamplesPerHour).roundToInt()
        if (closeToAxis) moveTo(xAt(from), zeroY) else moveTo(xAt(from), yAt(LightResponseCurve.shiftAt(from)))
        for (i in 0..steps) {
            val h = from + (to - from) * i / steps
            lineTo(xAt(h), yAt(LightResponseCurve.shiftAt(h)))
        }
        if (closeToAxis) {
            lineTo(xAt(to), zeroY)
            close()
        }
    }
}

private fun DrawScope.drawLobe(g: PlotGeometry, from: Double, to: Double, color: Color) =
    drawPath(g.curvePath(from, to, closeToAxis = true), color.copy(alpha = LobeAlpha))

private fun DrawScope.drawLabel(label: TextLayoutResult, centerX: Float, top: Float, g: PlotGeometry) =
    drawText(label, topLeft = Offset(labelSpan(label, centerX, g).start, top))

/** Horizontal pixels a label takes when centred on [centerX] and kept inside the plot. */
private fun labelSpan(label: TextLayoutResult, centerX: Float, g: PlotGeometry): ClosedFloatingPointRange<Float> {
    val left = (centerX - label.size.width / 2f).coerceIn(0f, g.width - label.size.width)
    return left..left + label.size.width
}

/** 45° hatch across a band (Avoid light's colour-blind pattern, as on the dial and the rail). */
private fun DrawScope.drawHatch(left: Float, right: Float, top: Float, bottom: Float, color: Color) =
    clipRect(left, top, right, bottom) {
        val step = HatchSpacing.toPx()
        var x = left - (bottom - top)
        while (x < right) {
            drawLine(color.copy(alpha = HatchAlpha), Offset(x, bottom), Offset(x + (bottom - top), top), 1.dp.toPx())
            x += step
        }
    }

@Composable
private fun positionText(hours: Double, window: LightResponseCurve.Window?): String {
    val minutes = (abs(hours) * 60).roundToInt()
    val amount = when {
        minutes % 60 == 0 -> stringResource(R.string.light_curve_hours, minutes / 60)
        minutes < 60 -> stringResource(R.string.light_curve_minutes, minutes)
        else -> stringResource(R.string.light_curve_hours_minutes, minutes / 60, minutes % 60)
    }
    val where = when {
        minutes == 0 -> stringResource(R.string.light_curve_at_coldest)
        hours < 0 -> stringResource(R.string.light_curve_before, amount)
        else -> stringResource(R.string.light_curve_after, amount)
    }
    return if (window != null && hours in window) stringResource(R.string.light_curve_in_block, where) else where
}

private val Effect.textRes: Int
    get() = when (this) {
        Effect.LaterStrongly -> R.string.light_curve_effect_later_strongly
        Effect.LaterALittle -> R.string.light_curve_effect_later_little
        Effect.Barely -> R.string.light_curve_effect_barely
        Effect.EarlierALittle -> R.string.light_curve_effect_earlier_little
        Effect.EarlierStrongly -> R.string.light_curve_effect_earlier_strongly
    }

private val CurveHeight = 132.dp
private val HandleRadius = 10.dp
private val HandleHalo = 17.dp
private val LabelGap = 4.dp
private val AxisGap = 8.dp
private const val HeldScale = 1.2f
private const val LabelWidthFraction = 0.6f
private const val LobeAlpha = 0.8f
private const val SamplesPerHour = 8
private const val BandAlpha = 0.3f
private const val HatchAlpha = 0.7f
private val HatchSpacing = 6.dp
