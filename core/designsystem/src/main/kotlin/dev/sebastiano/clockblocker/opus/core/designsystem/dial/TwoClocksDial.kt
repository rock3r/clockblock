package dev.sebastiano.clockblocker.opus.core.designsystem.dial

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.clockblocker.opus.core.designsystem.R
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.label
import dev.sebastiano.clockblocker.opus.core.designsystem.component.measureRolling
import dev.sebastiano.clockblocker.opus.core.designsystem.component.rollDirection
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.ShapeMorph
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.drawHatch
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.AdviceColors
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ColorMath
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockThemeVariant
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.LocalReduceMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyPalette
import dev.sebastiano.clockblocker.opus.core.designsystem.time.TimeFormatter
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * **Two Clocks**: the signature 24 h dial (design.md §2.3 A). Noon at the top, midnight at the bottom.
 *
 * - Outer ring: local clock, painted with the sky of each hour; a `Sunny` sun (or a crescent at night) rides it.
 * - Inner ring: the body clock: navy biological night and a `PuffyDiamond` at CBTmin. It is drawn in body time
 *   and rotated by the jet lag, turning towards alignment when [state] moves to a new day.
 * - Between them: advice arcs (light lane outside, rest lane inside; avoid-light is hatched).
 * - Jet-lag wedge from local midnight to body midnight, labelled with the body clock relative to local time
 *   ("−5 h" = body 5 h behind; the app-wide convention, see [formatJetLagHours]).
 * - Centre: local time upright, body time slanted.
 *
 * Drag the hand to scrub (hour-tick haptics, [onScrub]); tap the centre to return to now. Easter egg: long-press
 * the centre, then spin a full turn counter-clockwise → [onRewind].
 *
 * @param returnOnRelease when true, lifting the finger after a scrub springs the hand back to now
 *   (`ClockblockMotion.dataSpatial`) right after [onScrubEnd]: a "peek" preview rather than a persistent one.
 * @param onRingsAligned called once the inner ring has finished turning into alignment (after the CONFIRM click).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TwoClocksDial(
    state: DialState,
    modifier: Modifier = Modifier,
    onScrub: ((Instant) -> Unit)? = null,
    onScrubEnd: (() -> Unit)? = null,
    onRewind: (() -> Unit)? = null,
    easterEggsEnabled: Boolean = true,
    returnOnRelease: Boolean = false,
    onRingsAligned: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val advice = ClockblockTheme.adviceColors
    val sky = ClockblockTheme.sky
    val motion = ClockblockTheme.motion
    val textStyles = ClockblockTheme.textStyles
    val reduce = LocalReduceMotion.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 48)
    val formatter = rememberTimeFormatter()
    val nightSafe = ClockblockTheme.variant == ClockblockThemeVariant.NightSafe
    val sunColor = advice[AdviceType.SeeBrightLight].color
    val moonColor = if (nightSafe) colors.onSurface else Moonlight
    val cbtColor = if (nightSafe) ColorMath.dim(CbtGold, 0.6f) else CbtGold

    val currentState by rememberUpdatedState(state)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
    val currentOnRewind by rememberUpdatedState(onRewind)
    val currentReturnOnRelease by rememberUpdatedState(returnOnRelease)

    /**
     * Minutes the hand is ahead of "now" (0 = now). Owned by the dial; [onScrub] reports it out. Saveable so a
     * configuration change mid-preview restores the hand where the cards are (F-007).
     */
    val scrub = rememberSaveable(saver = ScrubSaver) { Animatable(0f) }
    var rewinding by remember { mutableStateOf(false) }

    // Inner ring rotation: animate along the shortest path when the day (body offset) changes. The rings "click"
    // (CONFIRM haptic, [onRingsAligned]) once the turn into alignment has landed, not when it starts.
    val bodyAhead = remember { Animatable(state.bodyAheadMinutes) }
    var wasAligned by remember { mutableStateOf(state.isAligned) }
    val currentOnRingsAligned by rememberUpdatedState(onRingsAligned)
    // The wedge pill rolls from the old offset's label to the new one as the ring turns (draw phase, see wedgeLabel).
    var labelFrom by remember { mutableFloatStateOf(state.bodyAheadMinutes) }
    var labelTo by remember { mutableFloatStateOf(state.bodyAheadMinutes) }
    LaunchedEffect(state.bodyAheadMinutes, state.isAligned) {
        val target = bodyAhead.value + DialGeometry.minuteDelta(bodyAhead.value, state.bodyAheadMinutes)
        labelFrom = bodyAhead.value
        labelTo = target
        if (reduce) bodyAhead.snapTo(target) else bodyAhead.animateTo(target, motion.dialDayRotation())
        labelFrom = target
        if (state.isAligned && !wasAligned) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            currentOnRingsAligned?.invoke()
        }
        wasAligned = state.isAligned
    }

    val boundaries = remember(state) { blockBoundaries(state) }

    fun report(offset: Float) {
        currentOnScrub?.invoke(currentState.instant.plusSeconds((offset * 60f).toLong()))
    }

    // A restored preview (see ScrubSaver) is reported once so the host's cards agree with the hand.
    LaunchedEffect(Unit) { if (scrub.value != 0f) report(scrub.value) }

    /**
     * Moves the hand on [spec] and reports every frame, so the cards and the sky travel with it: the hand and the
     * data it drives are one event, never two that disagree (motion review: scrub release, Rewind).
     */
    suspend fun animateReporting(target: Float, spec: AnimationSpec<Float>, initialVelocity: Float = 0f) {
        scrub.animateTo(target, spec, initialVelocity) { report(value) }
        report(target)
    }

    fun animateScrubTo(offset: Float) {
        scope.launch { animateReporting(offset, motion.dataSpatial()) }
    }

    // Semantics (composition): coarse scrub so TalkBack text doesn't recompose every frame.
    val coarseScrub by remember { derivedStateOf { (scrub.value / 5f).toInt() * 5f } }
    val shown = if (coarseScrub == 0f) state else state.scrubbedTo(coarseScrub)
    val description = dialDescription(shown, formatter)
    val nextLabel = stringResource(R.string.dial_action_next_block)
    val previousLabel = stringResource(R.string.dial_action_previous_block)
    val nowLabel = stringResource(R.string.dial_action_back_to_now)
    val bodySuffix = stringResource(R.string.dial_body_suffix)

    Canvas(
        modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clearAndSetSemantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) {
                        boundaries.firstOrNull { it > scrub.value + 1f }?.let { animateScrubTo(it); true } ?: false
                    },
                    CustomAccessibilityAction(previousLabel) {
                        boundaries.lastOrNull { it < scrub.value - 1f }?.let { animateScrubTo(it); true } ?: false
                    },
                    CustomAccessibilityAction(nowLabel) { animateScrubTo(0f); true },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val u = min(size.width, size.height) / 320f
                    val r = (down.position - c).getDistance()
                    val haptics = ScrubHaptics(view, currentState, boundaries)
                    if (r < CentreRadius * u) {
                        val allowEgg = easterEggsEnabled && currentOnRewind != null && !reduce
                        val longPress = if (allowEgg) awaitLongPressOrCancellation(down.id) else null
                        if (longPress == null) {
                            // A tap on the centre returns the hand to now; the cards travel with it.
                            if (scrub.value != 0f) {
                                scope.launch {
                                    animateReporting(0f, motion.dataSpatial())
                                    currentOnScrubEnd?.invoke()
                                }
                            }
                            return@awaitEachGesture
                        }
                        // Rewind: follow the finger around the dial, accumulating signed rotation.
                        rewinding = true
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        var lastAngle = DialGeometry.angleOf(longPress.position.x - c.x, longPress.position.y - c.y)
                        var total = 0f
                        var fired = false
                        val velocity = VelocityTracker1D(isDataDifferential = false)
                        velocity.addDataPoint(longPress.uptimeMillis, 0f)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val angle = DialGeometry.angleOf(change.position.x - c.x, change.position.y - c.y)
                            val delta = DialGeometry.angleDelta(lastAngle, angle)
                            lastAngle = angle
                            total += delta
                            val offset = total / 360f * DialGeometry.MinutesPerDay
                            velocity.addDataPoint(change.uptimeMillis, offset)
                            scope.launch { scrub.snapTo(offset) }
                            haptics.update(offset)
                            // The day runs backwards under the finger: cards and sky follow the hand.
                            report(offset)
                            change.consume()
                            if (!fired && total <= -360f) {
                                fired = true
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                }
                                currentOnRewind?.invoke()
                                break
                            }
                        }
                        rewinding = false
                        // Finger momentum carries into the return (F-002), in minutes per second.
                        val release = velocity.calculateVelocity().takeIf { it.isFinite() } ?: 0f
                        scope.launch {
                            if (fired) {
                                // The payoff: the whole day spins back from the raw −1440 to now, so the
                                // overshoot is visible and the sky reverses with it.
                                animateReporting(0f, motion.rewindReturn(), release)
                            } else {
                                // Abandoned spin: settle to now along the short way, no celebration.
                                scrub.snapTo(DialGeometry.minuteDelta(0f, scrub.value))
                                animateReporting(0f, motion.dataSpatial(), release)
                            }
                            currentOnScrubEnd?.invoke()
                        }
                        return@awaitEachGesture
                    }
                    if (currentOnScrub == null || r < InnerRingInner * u * 0.9f) return@awaitEachGesture
                    // Scrub: jump the hand under the finger, then follow incrementally (clamped to the window).
                    val now = currentState.localMinute
                    var offset = DialGeometry.relativeMinute(
                        now,
                        DialGeometry.minuteForAngle(DialGeometry.angleOf(down.position.x - c.x, down.position.y - c.y)),
                    )
                    var lastAngle = DialGeometry.angleOf(down.position.x - c.x, down.position.y - c.y)
                    scope.launch { scrub.snapTo(offset) }
                    haptics.update(offset)
                    report(offset)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.positionChange() == Offset.Zero) continue
                        val angle = DialGeometry.angleOf(change.position.x - c.x, change.position.y - c.y)
                        offset = (offset + DialGeometry.angleDelta(lastAngle, angle) / 360f * DialGeometry.MinutesPerDay)
                            .coerceIn(-DialState.PastWindowMinutes, DialGeometry.MinutesPerDay - DialState.PastWindowMinutes - 1f)
                        lastAngle = angle
                        val o = offset
                        scope.launch { scrub.snapTo(o) }
                        haptics.update(o)
                        report(o)
                        change.consume()
                    }
                    // A peek ends as one event: the hand snaps home on the same frame the cards return to now
                    // (no animation, so no velocity to hand over).
                    if (currentReturnOnRelease) scope.launch { scrub.snapTo(0f) }
                    currentOnScrubEnd?.invoke()
                }
            },
    ) {
        // Everything below reads animated values in the draw phase only.
        val s = currentState
        val offset = scrub.value
        val ahead = bodyAhead.value
        val u = min(size.width, size.height) / 320f
        val center = Offset(size.width / 2f, size.height / 2f)
        val displayMinute = (s.localMinute + offset).mod(DialGeometry.MinutesPerDay)
        val painter = DialPainter(this, center, u)

        painter.face(colors.surfaceContainerLow, colors.outlineVariant)
        if (!s.isAligned) painter.wedgeFill(ahead, colors.primary)
        painter.ticks(colors.outlineVariant, colors.onSurfaceVariant, measurer, textStyles.timeLabel, formatter)
        painter.skyRing(sky, s.sunriseMinute, s.sunsetMinute, colors.outlineVariant)
        painter.arcs(s, offset, advice)
        painter.bodyRing(ahead, s, advice, colors.surfaceContainerHighest, colors.onSurfaceVariant, cbtColor)
        if (!s.isAligned) painter.wedgeEdges(ahead, colors.primary)
        if (offset != 0f) painter.nowTick(s.localMinute, colors.onSurface)
        painter.hand(displayMinute, colors.onSurface, colors.surfaceContainerLow)
        painter.sun(displayMinute, s.sunriseMinute, s.sunsetMinute, sunColor, moonColor, colors.surfaceContainerLow)
        if (!s.isAligned) {
            // One event with the ring: the label's roll progress is the rotation's own progress.
            val span = labelTo - labelFrom
            val roll = if (abs(span) < 0.5f) 1f else ((ahead - labelFrom) / span).coerceIn(0f, 1f)
            painter.wedgeLabel(
                ahead, wedgeLabelText(labelFrom), wedgeLabelText(labelTo), roll, measurer,
                colors.primary, colors.onPrimary, textStyles.timeLabel,
            )
        }
        val displayTime = DialGeometry.timeOf(displayMinute)
        painter.readouts(
            local = formatter.format(displayTime),
            marker = formatter.marker(displayTime),
            body = formatter.format(DialGeometry.timeOf(displayMinute + ahead)) + " " + bodySuffix,
            measurer = measurer,
            localStyle = textStyles.timeDisplay,
            bodyStyle = textStyles.bodyClockTitle,
            localColor = colors.onSurface,
            bodyColor = colors.primary,
            fontScale = density.fontScale,
        )
    }
}

private val Moonlight = Color(0xFFF3EBD3)
private val CbtGold = Color(0xFFFFDEA0)

// Geometry in dial units (1 u = side / 320).
private const val FaceRadius = 152f
private const val OuterRingCentre = 144f
private const val OuterRingWidth = 12f
private const val LightLane = 131f
private const val LightLaneWidth = 9f
private const val RestLane = 120f
private const val RestLaneWidth = 8f
private const val InnerRingCentre = 101f
private const val InnerRingWidth = 20f
private const val InnerRingInner = InnerRingCentre - InnerRingWidth / 2f
private const val CentreRadius = 86f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val SunMorph by lazy { ShapeMorph(MaterialShapes.Sunny, MaterialShapes.Sunny) }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val CbtMorph by lazy { ShapeMorph(MaterialShapes.PuffyDiamond, MaterialShapes.PuffyDiamond) }

/** Stateless drawing helpers for the dial; every call happens inside the Canvas draw lambda. */
private class DialPainter(val scope: DrawScope, val center: Offset, val u: Float) {

    private fun point(angleDeg: Float, radius: Float): Offset {
        val rad = Math.toRadians(angleDeg.toDouble())
        return Offset(center.x + (radius * u * cos(rad)).toFloat(), center.y + (radius * u * sin(rad)).toFloat())
    }

    private fun arcRect(radius: Float): Pair<Offset, Size> {
        val r = radius * u
        return Offset(center.x - r, center.y - r) to Size(2 * r, 2 * r)
    }

    private fun DrawScope.ringArc(
        radius: Float,
        width: Float,
        startMinute: Float,
        sweepMinutes: Float,
        color: Color,
        cap: StrokeCap = StrokeCap.Round,
        pathEffect: PathEffect? = null,
        blendMode: BlendMode = BlendMode.SrcOver,
    ) {
        val (tl, sz) = arcRect(radius)
        drawArc(
            color = color,
            startAngle = DialGeometry.angleForMinute(startMinute),
            sweepAngle = sweepMinutes / DialGeometry.MinutesPerDay * 360f,
            useCenter = false,
            topLeft = tl,
            size = sz,
            style = Stroke(width * u, cap = cap, pathEffect = pathEffect),
            blendMode = blendMode,
        )
    }

    fun face(fill: Color, outline: Color) = with(scope) {
        drawCircle(fill, FaceRadius * u, center)
        drawCircle(outline.copy(alpha = 0.5f), FaceRadius * u, center, style = Stroke(1f * u))
    }

    fun ticks(minor: Color, label: Color, measurer: TextMeasurer, style: TextStyle, formatter: TimeFormatter) = with(scope) {
        for (h in 0 until 24) {
            val a = DialGeometry.angleForMinute(h * 60f)
            val major = h % 6 == 0
            val inner = if (major) RestLane - RestLaneWidth / 2f - 1f else RestLane - 1.5f
            val outer = LightLane + LightLaneWidth / 2f + 1f
            drawLine(
                minor.copy(alpha = if (major) 0.9f else 0.55f),
                point(a, inner),
                point(a, outer),
                strokeWidth = (if (major) 1.5f else 1f) * u,
                cap = StrokeCap.Round,
            )
        }
        // Hour numerals for 00 / 06 / 12 / 18 just inside the body ring.
        val numeralStyle = style.copy(fontSize = (9.5f * u / density / fontScale).sp, color = label.copy(alpha = 0.8f))
        listOf(0, 6, 12, 18).forEach { h ->
            val text = if (formatter.is24Hour) "%02d".format(h) else listOf("12a", "6a", "12p", "6p")[h / 6]
            val layout = measurer.measure(text, numeralStyle)
            val p = point(DialGeometry.angleForMinute(h * 60f), InnerRingInner - 9f)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
    }

    fun skyRing(sky: SkyPalette, sunrise: Float, sunset: Float, outline: Color) = with(scope) {
        val stops = (0..48).map { i ->
            val fraction = i / 48f
            val minute = DialGeometry.minuteForAngle(fraction * 360f)
            fraction to sky.gradientAt(minute / 60f, sunrise / 60f, sunset / 60f).top
        }.toTypedArray()
        drawCircle(
            brush = Brush.sweepGradient(*stops, center = center),
            radius = OuterRingCentre * u,
            center = center,
            style = Stroke(OuterRingWidth * u),
        )
        drawCircle(outline.copy(alpha = 0.35f), (OuterRingCentre + OuterRingWidth / 2f) * u, center, style = Stroke(0.75f * u))
        drawCircle(outline.copy(alpha = 0.35f), (OuterRingCentre - OuterRingWidth / 2f) * u, center, style = Stroke(0.75f * u))
    }

    fun arcs(state: DialState, scrubOffset: Float, palette: AdviceColors) = with(scope) {
        val display = state.localMinute + scrubOffset
        // Draw rest lane, then light lane; "now" arcs last so they sit on top.
        val ordered = state.arcs.sortedWith(compareBy({ it.isNow }, { lane(it.type) }))
        for (arc in ordered) {
            val role = palette[arc.type]
            val rel = DialGeometry.relativeMinute(state.localMinute, arc.startMinute)
            val past = rel + arc.sweepMinutes <= display - state.localMinute && arc.sweepMinutes > 0f
            val alpha = if (past) 0.38f else 1f
            val emphasis = if (arc.isNow) 2.5f else 0f
            when (arc.type) {
                AdviceType.SeeBrightLight ->
                    ringArc(LightLane, LightLaneWidth + emphasis, arc.startMinute, arc.sweepMinutes, role.color.copy(alpha = alpha))
                AdviceType.SeeLight ->
                    ringArc(LightLane, LightLaneWidth + emphasis, arc.startMinute, arc.sweepMinutes, role.color.copy(alpha = alpha * 0.85f))
                AdviceType.AvoidLight -> {
                    // Hatch: the arc in its container colour, hatch lines composited only onto it (SrcAtop).
                    val r = (LightLane + LightLaneWidth) * u
                    val layer = Paint().apply { this.alpha = alpha }
                    drawContext.canvas.saveLayer(Rect(center.x - r, center.y - r, center.x + r, center.y + r), layer)
                    ringArc(LightLane, LightLaneWidth + emphasis, arc.startMinute, arc.sweepMinutes, role.container)
                    hatchOver(role.color)
                    drawContext.canvas.restore()
                }
                AdviceType.Sleep -> ringArc(RestLane, RestLaneWidth + emphasis, arc.startMinute, arc.sweepMinutes, role.color.copy(alpha = alpha))
                AdviceType.Nap -> ringArc(RestLane, RestLaneWidth + emphasis, arc.startMinute, arc.sweepMinutes, role.color.copy(alpha = alpha))
                AdviceType.OptionalNap -> ringArc(
                    RestLane, RestLaneWidth + emphasis, arc.startMinute, arc.sweepMinutes, role.color.copy(alpha = alpha),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 6f * u)),
                )
                AdviceType.Flight -> ringArc(
                    RestLane, RestLaneWidth * 0.6f + emphasis, arc.startMinute, arc.sweepMinutes, role.color.copy(alpha = alpha),
                    cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * u, 3f * u)),
                )
                AdviceType.PeakFatigue -> ringArc(
                    RestLane, RestLaneWidth * 0.5f + emphasis, arc.startMinute, arc.sweepMinutes, role.color.copy(alpha = alpha),
                )
                AdviceType.Caffeine, AdviceType.AvoidCaffeine -> {
                    // Copper ticks every 20 min across the window (avoid = hollow ticks with lower alpha).
                    val count = (arc.sweepMinutes / 20f).toInt().coerceAtLeast(1)
                    for (i in 0..count) {
                        val m = arc.startMinute + arc.sweepMinutes * i / count
                        val a = DialGeometry.angleForMinute(m)
                        val tickAlpha = if (arc.type == AdviceType.AvoidCaffeine) 0.45f else 1f
                        drawLine(
                            role.color.copy(alpha = alpha * tickAlpha),
                            point(a, RestLane - RestLaneWidth / 2f - 3.5f),
                            point(a, RestLane - RestLaneWidth / 2f - 0.5f),
                            strokeWidth = 2f * u,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                AdviceType.Melatonin -> {
                    val p = point(DialGeometry.angleForMinute(arc.startMinute), RestLane)
                    drawCircle(role.onColor.copy(alpha = 0.25f * alpha), 6.5f * u, p)
                    drawCircle(role.color.copy(alpha = alpha), 5f * u, p)
                }
            }
        }
    }

    /** Draws the hatch over whatever is already in the current layer (SrcAtop). */
    private fun DrawScope.hatchOver(lineColor: Color) {
        val step = 4.5f * u
        val w = size.width
        val h = size.height
        var x = -h
        while (x < w) {
            drawLine(lineColor, Offset(x, h), Offset(x + h, 0f), 1.6f * u, blendMode = BlendMode.SrcAtop)
            x += step
        }
    }

    private fun lane(type: AdviceType): Int = when (type) {
        AdviceType.SeeBrightLight, AdviceType.SeeLight, AdviceType.AvoidLight -> 1
        AdviceType.Melatonin, AdviceType.Caffeine, AdviceType.AvoidCaffeine -> 2
        else -> 0
    }

    fun bodyRing(ahead: Float, state: DialState, palette: AdviceColors, track: Color, ink: Color, cbtColor: Color) = with(scope) {
        drawCircle(track, InnerRingCentre * u, center, style = Stroke(InnerRingWidth * u))
        fun local(bodyMinute: Float) = (bodyMinute - ahead).mod(DialGeometry.MinutesPerDay)
        val nightStart = state.biologicalNightStartBodyMinute
        val nightSweep = (state.biologicalNightEndBodyMinute - nightStart).mod(DialGeometry.MinutesPerDay)
        val night = palette[AdviceType.Sleep]
        ringArc(InnerRingCentre, InnerRingWidth - 4f, local(nightStart), nightSweep, night.color)
        // Body hour ticks (00/06/12/18 body): the ring visibly turns.
        for (h in 0 until 24 step 3) {
            val a = DialGeometry.angleForMinute(local(h * 60f))
            val major = h % 6 == 0
            val inNight = ((h * 60f - nightStart).mod(DialGeometry.MinutesPerDay)) < nightSweep
            val c = if (inNight) night.onColor.copy(alpha = 0.6f) else ink.copy(alpha = 0.45f)
            val len = if (major) 4f else 2.5f
            drawLine(c, point(a, InnerRingCentre - len), point(a, InnerRingCentre + len), (if (major) 1.5f else 1f) * u, cap = StrokeCap.Round)
        }
        // CBTmin pivot.
        val p = point(DialGeometry.angleForMinute(local(state.cbtMinBodyMinute)), InnerRingCentre)
        val d = 15f * u
        translate(p.x - d / 2f, p.y - d / 2f) {
            drawPath(CbtMorph.toPath(0f, Size(d, d)), cbtColor)
        }
    }

    fun wedgeFill(ahead: Float, color: Color) = with(scope) {
        val start = DialGeometry.angleForMinute(0f)
        val sweep = -ahead / DialGeometry.MinutesPerDay * 360f
        val (tl, sz) = arcRect((InnerRingInner + OuterRingCentre + OuterRingWidth / 2f) / 2f)
        val width = (OuterRingCentre + OuterRingWidth / 2f) - InnerRingInner
        drawArc(color.copy(alpha = 0.16f), start, sweep, false, tl, sz, style = Stroke(width * u, cap = StrokeCap.Butt))
    }

    fun wedgeEdges(ahead: Float, color: Color) = with(scope) {
        listOf(0f, (-ahead).mod(DialGeometry.MinutesPerDay)).forEachIndexed { i, m ->
            val a = DialGeometry.angleForMinute(m)
            drawLine(
                color.copy(alpha = if (i == 0) 0.55f else 0.9f),
                point(a, InnerRingInner - 2f),
                point(a, OuterRingCentre + OuterRingWidth / 2f + 2f),
                strokeWidth = 1.5f * u,
                cap = StrokeCap.Round,
                pathEffect = if (i == 0) PathEffect.dashPathEffect(floatArrayOf(3f * u, 3f * u)) else null,
            )
        }
    }

    /**
     * The jet-lag pill, rolling from [from] to [to] at [progress] (1 = at rest on [to]). Rolls up when the body
     * clock moves later (the number grows), down when it moves earlier.
     */
    fun wedgeLabel(
        ahead: Float,
        from: String,
        to: String,
        progress: Float,
        measurer: TextMeasurer,
        bg: Color,
        fg: Color,
        style: TextStyle,
    ) = with(scope) {
        val mid = (-ahead / 2f).mod(DialGeometry.MinutesPerDay)
        val p = point(DialGeometry.angleForMinute(mid), (LightLane + RestLane) / 2f)
        val labelStyle = style.copy(fontSize = (11f * u / density / fontScale).sp, color = fg)
        val layout = measurer.measureRolling(from, to, labelStyle, rollDirection(from, to))
        val padH = 7f * u
        val padV = 2.5f * u
        val textW = layout.width(progress)
        val w = textW + padH * 2
        val h = layout.height + padV * 2
        drawRoundRect(bg, Offset(p.x - w / 2f, p.y - h / 2f), Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2f))
        layout.draw(this, Offset(p.x - textW / 2f, p.y - layout.height / 2f), progress)
    }

    fun nowTick(minute: Float, color: Color) = with(scope) {
        val a = DialGeometry.angleForMinute(minute)
        drawLine(color.copy(alpha = 0.6f), point(a, OuterRingCentre + 8f), point(a, OuterRingCentre + 12f), 2f * u, cap = StrokeCap.Round)
    }

    fun hand(minute: Float, color: Color, halo: Color) = with(scope) {
        val a = DialGeometry.angleForMinute(minute)
        val inner = InnerRingInner - 3f
        val outer = LightLane + LightLaneWidth / 2f + 2f
        val p0 = point(a, inner)
        val p1 = point(a, outer)
        drawLine(halo, p0, p1, 9f * u, cap = StrokeCap.Round)
        drawLine(color, p0, p1, 5f * u, cap = StrokeCap.Round)
    }

    fun sun(minute: Float, sunrise: Float, sunset: Float, sunColor: Color, moonColor: Color, halo: Color) = with(scope) {
        val p = point(DialGeometry.angleForMinute(minute), OuterRingCentre)
        val isDay = minute in sunrise..sunset
        if (isDay) {
            val d = 24f * u
            translate(p.x - d / 2f, p.y - d / 2f) {
                drawPath(SunMorph.toPath(0f, Size(d, d), scale = 1.18f), halo)
                drawPath(SunMorph.toPath(0f, Size(d, d)), sunColor)
            }
        } else {
            val r = 8.5f * u
            drawCircle(halo, r + 2.5f * u, p)
            val moon = Path().apply {
                addOval(Rect(p, r))
            }
            val bite = Path().apply { addOval(Rect(Offset(p.x + r * 0.45f, p.y - r * 0.35f), r * 0.85f)) }
            val crescent = Path().apply { op(moon, bite, androidx.compose.ui.graphics.PathOperation.Difference) }
            drawPath(crescent, moonColor)
        }
    }

    fun readouts(
        local: String,
        marker: String?,
        body: String,
        measurer: TextMeasurer,
        localStyle: TextStyle,
        bodyStyle: TextStyle,
        localColor: Color,
        bodyColor: Color,
        fontScale: Float,
    ) = with(scope) {
        // Sized to the dial (not the font scale) so it always fits; allow a little growth for large text.
        val grow = fontScale.coerceIn(1f, 1.15f)
        val localLayout = measurer.measure(
            local,
            localStyle.copy(fontSize = (44f * u * grow / density / fontScale).sp, lineHeight = TextStyle.Default.lineHeight, color = localColor),
        )
        val bodyLayout = measurer.measure(
            body,
            bodyStyle.copy(fontSize = (17f * u * grow / density / fontScale).sp, lineHeight = TextStyle.Default.lineHeight, color = bodyColor),
        )
        val markerLayout = marker?.let {
            measurer.measure(it, bodyStyle.copy(fontSize = (12f * u * grow / density / fontScale).sp, fontStyle = null, color = localColor.copy(alpha = 0.7f)))
        }
        val gap = 0f
        val total = localLayout.size.height + gap + bodyLayout.size.height
        val top = center.y - total / 2f - 4f * u
        drawText(localLayout, topLeft = Offset(center.x - localLayout.size.width / 2f, top))
        if (markerLayout != null) {
            // 12-hour clocks: a small AM/PM after the digits, on the cap line.
            drawText(markerLayout, topLeft = Offset(center.x + localLayout.size.width / 2f + 2f * u, top + localLayout.size.height * 0.22f))
        }
        drawText(bodyLayout, topLeft = Offset(center.x - bodyLayout.size.width / 2f, top + localLayout.size.height + gap))
    }
}

/** Fires CLOCK_TICK on every hour crossed while scrubbing, SEGMENT_TICK (API 34+) on advice boundaries. */
private class ScrubHaptics(private val view: View, private val state: DialState, private val boundaries: List<Float>) {
    private var lastHour: Int? = null
    private var lastOffset: Float? = null

    fun update(offset: Float) {
        val hour = floor((state.localMinute + offset) / 60f).toInt()
        val previous = lastOffset
        if (previous != null) {
            val crossedBoundary = boundaries.any { b -> (previous < b && offset >= b) || (previous > b && offset <= b) }
            when {
                crossedBoundary && Build.VERSION.SDK_INT >= 34 ->
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                hour != lastHour -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
        lastHour = hour
        lastOffset = offset
    }
}

/** Advice boundaries as scrub offsets from now (minutes, ascending, within the dial window). */
internal fun blockBoundaries(state: DialState): List<Float> = state.arcs
    .flatMap { arc ->
        val start = DialGeometry.relativeMinute(state.localMinute, arc.startMinute)
        if (arc.sweepMinutes == 0f) listOf(start) else listOf(start, start + arc.sweepMinutes)
    }
    .filter { it >= -DialState.PastWindowMinutes && it < DialGeometry.MinutesPerDay - DialState.PastWindowMinutes }
    .distinct()
    .sorted()

@Composable
private fun dialDescription(state: DialState, formatter: TimeFormatter): String {
    val parts = mutableListOf(
        stringResource(R.string.dial_description_clocks, formatter.formatFull(state.localTime), formatter.formatFull(state.bodyTime)),
    )
    if (state.isAligned) {
        parts += stringResource(R.string.dial_description_aligned)
    } else {
        val label = formatHoursMagnitude(state.bodyOffsetHours)
        parts += if (state.bodyOffsetHours < 0f) {
            stringResource(R.string.dial_description_body_behind, label)
        } else {
            stringResource(R.string.dial_description_body_ahead, label)
        }
    }
    // Narrate the block under the hand (scrubbed or now).
    val minute = state.localMinute
    val under = state.arcs
        .filter { it.sweepMinutes > 0f }
        .filter { (minute - it.startMinute).mod(DialGeometry.MinutesPerDay) < it.sweepMinutes }
        .minByOrNull { it.type.ordinal }
    val now = under?.let { it.type to DialGeometry.timeOf(it.endMinute) } ?: state.now?.let { it.type to it.end }
    if (now != null) {
        parts += stringResource(R.string.dial_description_now, now.first.label().lowercaseFirst(), formatter.formatFull(now.second))
    }
    val next = state.arcs
        .filter { it.sweepMinutes > 0f && it.type != under?.type }
        .map { it to DialGeometry.relativeMinute(minute, it.startMinute) }
        .filter { it.second > 0f }
        .minByOrNull { it.second }
        ?.first
    if (next != null) {
        parts += stringResource(
            R.string.dial_description_next,
            next.type.label().lowercaseFirst(),
            formatter.formatFull(DialGeometry.timeOf(next.startMinute)),
            formatter.formatFull(DialGeometry.timeOf(next.endMinute)),
        )
    }
    return parts.joinToString(" ")
}

private fun String.lowercaseFirst(): String = replaceFirstChar { it.lowercase() }

/** Saves the hand's scrub offset (minutes from now); restores it at rest. */
private val ScrubSaver: Saver<Animatable<Float, *>, Float> = Saver(
    save = { it.value },
    restore = { Animatable(it) },
)

/**
 * The wedge chip: the body clock relative to local time, the app-wide convention ("−3½ h" = body 3½ h behind
 * local; see [formatJetLagHours]). Rounded like the header so both always say the same thing.
 */
internal fun wedgeLabelText(bodyAheadMinutes: Float): String = formatJetLagHours(bodyAheadMinutes / 60f)
