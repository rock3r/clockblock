package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import android.content.res.Resources
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.toPath
import dev.sebastiano.clockblocker.opus.core.designsystem.component.RollingMetricText
import dev.sebastiano.clockblocker.opus.core.designsystem.component.RollingTimeText
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialGeometry
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyPalette
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.feature.onboarding.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Test tags for the e2e suite. */
object SleepDialTags {
    const val Dial = "sleep_dial"
    const val Bedtime = "sleep_dial_bedtime"
    const val Wake = "sleep_dial_wake"
    const val Egg = "sleep_dial_egg"
    const val Duration = "sleep_dial_duration"
    const val BedtimePill = "sleep_dial_bedtime_pill"
    const val WakePill = "sleep_dial_wake_pill"
    const val PickerConfirm = "sleep_dial_picker_confirm"
}

private enum class EggState { Hidden, Peek, Note }

/**
 * The sleep-arc dial picker: a 24 h ring (noon at the top, midnight at the bottom, like the Two Clocks dial) with
 * a moon handle for bedtime and a sun handle for wake-up. Drag a handle to move it, or the arc to slide the whole
 * window. Values snap to 5 minutes with a `CLOCK_TICK` haptic every 15.
 *
 * Under the dial, a bedtime pill and a wake pill show the two times (with AM/PM in 12-hour mode). They follow the
 * dial live while dragging (no roll, the value changes every frame) and roll their changed digits on discrete
 * changes (TalkBack, keyboard, the egg's rubber band). Tapping a pill opens a time picker for an exact minute.
 *
 * Accessibility: each handle is its own adjustable node (TalkBack swipe up/down or the custom actions move it by
 * 15 minutes) and is keyboard-focusable (arrow keys ±15 minutes); the pills are buttons that open the picker.
 *
 * Easter egg **24.2** (design.md §2.7 #4, when [easterEggEnabled]): push the wake handle past a full day and
 * "24:12" peeks out with the Czeisler line before the arc rubber-bands back. Under reduce motion the peek and the
 * rubber band are skipped and the line shows as a brief inline note instead (the static carrier).
 *
 * [maxDialSize] caps the ring's diameter (it otherwise fills the width up to 360 dp). [maxHeight], when finite,
 * shrinks the ring further so the ring plus the time pills fit that height (the pills are measured, so this holds
 * at any font scale); the ring never goes below 140 dp for it, and the hint line under the pills may scroll.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SleepDial(
    window: SleepWindow,
    onWindowChange: (SleepWindow) -> Unit,
    modifier: Modifier = Modifier,
    easterEggEnabled: Boolean = true,
    maxDialSize: Dp = DefaultMaxDialSize,
    maxHeight: Dp = Dp.Infinity,
) {
    val colors = MaterialTheme.colorScheme
    val advice = ClockblockTheme.adviceColors
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    val textStyles = ClockblockTheme.textStyles
    val art = ClockblockTheme.artColors
    val sky = ClockblockTheme.sky
    val formatter = rememberTimeFormatter()
    val view = LocalView.current
    val density = LocalDensity.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 8)

    val currentWindow by rememberUpdatedState(window)
    val currentOnChange by rememberUpdatedState(onWindowChange)
    val currentReduce by rememberUpdatedState(reduce)
    val currentEggEnabled by rememberUpdatedState(easterEggEnabled)

    /** Raw minutes pushed past the longest window; drawn with resistance. */
    val overshoot = remember { Animatable(0f) }
    /** Extra minutes on the wake end while the arc rubber-bands back after the egg. */
    val wakeReturn = remember { Animatable(0f) }
    var activeHandle by remember { mutableStateOf<SleepHandle?>(null) }
    var focusedHandle by remember { mutableStateOf<SleepHandle?>(null) }
    var picking by rememberSaveable { mutableStateOf<SleepHandle?>(null) }
    var egg by remember { mutableStateOf(EggState.Hidden) }
    var eggToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(eggToken) {
        if (eggToken > 0) {
            delay(EggHoldMillis)
            egg = EggState.Hidden
        }
    }
    // The gate can close mid-egg (a planned sleep block starts): drop the peek or note and stop the rubber band.
    LaunchedEffect(easterEggEnabled) {
        if (!easterEggEnabled) {
            egg = EggState.Hidden
            wakeReturn.snapTo(0f)
        }
    }

    val sleepRole = advice[AdviceType.Sleep]
    val sunColor = advice[AdviceType.SeeBrightLight].color
    val sunPath = remember { MaterialShapes.Sunny.toPath().asComposePath().normalised() }

    fun nudge(handle: SleepHandle, delta: Int) {
        currentOnChange(SleepDialMath.nudge(currentWindow, handle, delta))
    }

    val bedText = formatter.format(window.bedtime)
    val wakeText = formatter.format(window.wake)
    val durationMinutes = SleepDialMath.durationMinutes(window)
    val durationText = formatDuration(durationMinutes)
    val description = stringResource(R.string.sleep_dial_description, bedText, wakeText, durationText)
    val laterLabel = stringResource(R.string.sleep_action_later)
    val earlierLabel = stringResource(R.string.sleep_action_earlier)
    // Readouts follow a drag live; they only roll for discrete changes (MOTION.md: never per-frame values).
    val rollReadouts = activeHandle == null
    val durationFormat: (Float) -> String = remember(resources) { { formatDuration(resources, it.roundToInt()) } }
    val durationTemplate = remember(resources) { formatDuration(resources, DurationTemplateMinutes) }

    val dialContent = @Composable {
        BoxWithConstraints(Modifier) {
            val sidePx = constraints.maxWidth.toFloat()
            Canvas(
                Modifier
                    .matchParentSize()
                    .testTag(SleepDialTags.Dial)
                    .clearAndSetSemantics { contentDescription = description }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val u = min(size.width, size.height) / 320f
                            val r = (down.position - c).getDistance()
                            if (r < (TrackRadius - TrackWidth) * u || r > (TrackRadius + TrackWidth) * u) return@awaitEachGesture
                            var lastAngle = DialGeometry.angleOf(down.position.x - c.x, down.position.y - c.y)
                            val start = currentWindow
                            val handle = SleepDialMath.pick(DialGeometry.minuteForAngle(lastAngle), start, PickToleranceMinutes)
                                ?: return@awaitEachGesture
                            down.consume()
                            val drag = SleepDrag(handle, start)
                            // Release velocities (minutes/s) so the settle springs continue the finger's motion
                            // instead of starting from rest: the raw overshoot, and the drawn sweep (window +
                            // resisted overshoot) that the egg's rubber band returns from.
                            val overshootVelocity = VelocityTracker1D(isDataDifferential = false)
                            val sweepVelocity = VelocityTracker1D(isDataDifferential = false)
                            fun track(timeMillis: Long) {
                                overshootVelocity.addDataPoint(timeMillis, drag.overshootMinutes)
                                sweepVelocity.addDataPoint(
                                    timeMillis,
                                    SleepDialMath.durationMinutes(drag.window) + resisted(drag.overshootMinutes),
                                )
                            }
                            track(down.uptimeMillis)
                            var eggFired = false
                            activeHandle = handle
                            if (egg == EggState.Note) egg = EggState.Hidden
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val angle = DialGeometry.angleOf(change.position.x - c.x, change.position.y - c.y)
                                val delta = DialGeometry.angleDelta(lastAngle, angle) / 360f * DialGeometry.MinutesPerDay
                                lastAngle = angle
                                val before = drag.window
                                val after = drag.moveBy(delta)
                                if (after != before) {
                                    val (old, new) = if (handle == SleepHandle.Wake) {
                                        SleepDialMath.minuteOf(before.wake) to SleepDialMath.minuteOf(after.wake)
                                    } else {
                                        SleepDialMath.minuteOf(before.bedtime) to SleepDialMath.minuteOf(after.bedtime)
                                    }
                                    if (SleepDialMath.crossedStep(old, new)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    currentOnChange(after)
                                }
                                val o = drag.overshootMinutes
                                scope.launch { overshoot.snapTo(o) }
                                track(change.uptimeMillis)
                                if (drag.eggTriggered && !eggFired && currentEggEnabled) {
                                    eggFired = true
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    egg = if (currentReduce) EggState.Note else EggState.Peek
                                    eggToken++
                                }
                                change.consume()
                            }
                            activeHandle = null
                            val endDuration = SleepDialMath.durationMinutes(drag.window) + resisted(drag.overshootMinutes)
                            val releaseOvershootVelocity = overshootVelocity.calculateVelocity(MaxReleaseVelocity)
                            val releaseSweepVelocity = sweepVelocity.calculateVelocity(MaxReleaseVelocity)
                            scope.launch {
                                if (eggFired) {
                                    // Rubber-band back to where the gesture started.
                                    overshoot.snapTo(0f)
                                    currentOnChange(start)
                                    if (currentReduce || !currentEggEnabled) {
                                        wakeReturn.snapTo(0f)
                                    } else {
                                        wakeReturn.snapTo(endDuration - SleepDialMath.durationMinutes(start))
                                        wakeReturn.animateTo(0f, motion.rewindReturn(), initialVelocity = releaseSweepVelocity)
                                    }
                                } else {
                                    overshoot.animateTo(0f, motion.dataSpatial(), initialVelocity = releaseOvershootVelocity)
                                }
                            }
                        }
                    },
            ) {
                // Animated values are read here, in the draw phase only.
                val u = size.minDimension / 320f
                val w = currentWindow
                val bed = SleepDialMath.minuteOf(w.bedtime).toFloat()
                val sweep = SleepDialMath.durationMinutes(w) + wakeReturn.value + resisted(overshoot.value)
                val painter = SleepDialPainter(this, u)
                painter.face(colors.surfaceContainerLow, colors.outlineVariant)
                painter.skyRing(sky)
                painter.track(colors.surfaceContainerHigh)
                painter.ticks(colors.outline, colors.onSurfaceVariant, measurer, textStyles.timeLabel, formatter.is24Hour, density.fontScale)
                painter.marks(sunPath, sunColor, sleepRole.color)
                painter.arc(bed, sweep, sleepRole.color, sleepRole.onColor)
                painter.handle(bed, Handle.Moon, activeHandle == SleepHandle.Bedtime || activeHandle == SleepHandle.Both, focusedHandle == SleepHandle.Bedtime, colors.surfaceContainerLowest, sleepRole.color, colors.primary, art.shadow, sunPath)
                painter.handle(bed + sweep, Handle.Sun, activeHandle == SleepHandle.Wake || activeHandle == SleepHandle.Both, focusedHandle == SleepHandle.Wake, colors.surfaceContainerLowest, sunColor, colors.primary, art.shadow, sunPath)
            }

            // Centre readout: duration, or the 24:12 peek. Both lines step down in size on small dials (the
            // settings editor on a landscape phone) instead of clipping to "7 h 35 / of". The duration is sized
            // for the widest value ("22 h 55 m"), not the current one, so dragging never makes it grow and shrink.
            val centreWidthPx = (sidePx * 0.48f).roundToInt()
            val durationStyle = remember(measurer, textStyles.timeHeadline, durationTemplate, centreWidthPx, density) {
                measurer.fitted(textStyles.timeHeadline.copy(fontSize = 30.sp), listOf(durationTemplate), centreWidthPx, 12.sp)
            }
            Box(Modifier.align(Alignment.Center).width(with(density) { centreWidthPx.toDp() })) {
                AnimatedContent(
                    targetState = egg == EggState.Peek,
                    transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
                    label = "sleepCentre",
                    modifier = Modifier.align(Alignment.Center),
                ) { peek ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (peek) {
                            BasicText(
                                text = stringResource(R.string.sleep_egg_time),
                                style = textStyles.bodyClockDisplay.copy(color = colors.primary, textAlign = TextAlign.Center),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 38.sp),
                                modifier = Modifier.fillMaxWidth().testTag(SleepDialTags.Egg),
                            )
                        } else {
                            RollingMetricText(
                                value = durationMinutes.toFloat(),
                                style = durationStyle,
                                color = colors.onSurface,
                                format = durationFormat,
                                animateChanges = rollReadouts,
                                modifier = Modifier.testTag(SleepDialTags.Duration),
                            )
                        }
                        val caption = MaterialTheme.typography.labelLarge
                        BasicText(
                            text = stringResource(if (peek) R.string.sleep_egg_caption else R.string.sleep_duration_caption),
                            style = caption.copy(color = colors.onSurfaceVariant, textAlign = TextAlign.Center),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = caption.fontSize),
                        )
                    }
                }
            }

            // Adjustable, focusable nodes over each handle (TalkBack, keyboard, e2e).
            listOf(SleepHandle.Bedtime, SleepHandle.Wake).forEach { handle ->
                val time = if (handle == SleepHandle.Bedtime) window.bedtime else window.wake
                val minute = SleepDialMath.minuteOf(time)
                val timeText = formatter.format(time)
                val label = stringResource(
                    if (handle == SleepHandle.Bedtime) R.string.sleep_handle_bedtime_description else R.string.sleep_handle_wake_description,
                    timeText,
                )
                Box(
                    Modifier
                        .size(HandleTouchSize)
                        .offset {
                            val u = sidePx / 320f
                            val m = if (handle == SleepHandle.Bedtime) {
                                SleepDialMath.minuteOf(currentWindow.bedtime).toFloat()
                            } else {
                                SleepDialMath.minuteOf(currentWindow.bedtime) + SleepDialMath.durationMinutes(currentWindow) +
                                    wakeReturn.value + resisted(overshoot.value)
                            }
                            val p = pointOn(sidePx / 2f, sidePx / 2f, m, TrackRadius * u)
                            val half = HandleTouchSize.toPx() / 2f
                            IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt())
                        }
                        .testTag(if (handle == SleepHandle.Bedtime) SleepDialTags.Bedtime else SleepDialTags.Wake)
                        .onFocusChanged { focusedHandle = if (it.isFocused) handle else if (focusedHandle == handle) null else focusedHandle }
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.DirectionRight, Key.DirectionUp -> { nudge(handle, SleepDialMath.StepMinutes); true }
                                Key.DirectionLeft, Key.DirectionDown -> { nudge(handle, -SleepDialMath.StepMinutes); true }
                                else -> false
                            }
                        }
                        .semantics {
                            contentDescription = label
                            stateDescription = timeText
                            progressBarRangeInfo = ProgressBarRangeInfo(
                                current = minute.toFloat(),
                                range = 0f..SleepDialMath.MinutesPerDay.toFloat(),
                                steps = SleepDialMath.MinutesPerDay / SleepDialMath.StepMinutes - 1,
                            )
                            setProgress { target ->
                                val delta = ((target - minute) / SleepDialMath.StepMinutes).roundToInt() * SleepDialMath.StepMinutes
                                if (delta == 0) false else { nudge(handle, delta); true }
                            }
                            customActions = listOf(
                                CustomAccessibilityAction(laterLabel) { nudge(handle, SleepDialMath.StepMinutes); true },
                                CustomAccessibilityAction(earlierLabel) { nudge(handle, -SleepDialMath.StepMinutes); true },
                            )
                        }
                        .focusable(),
                )
            }
        }
    }

    val pillsContent = @Composable {
        BoxWithConstraints(Modifier) {
            // Both pills share one fitted style, sized for the widest time this format can show ("10:55 PM"), so
            // the times never resize while the dial is dragged, at any font scale.
            val pillInnerPx = with(density) { ((maxWidth - PillGap) / 2 - PillPadding * 2).roundToPx() }
            val timeTemplates = remember(formatter) { TimeTemplates.map { formatter.formatFull(it) } }
            val timeStyle = remember(measurer, textStyles.timeTitle, timeTemplates, pillInnerPx, density) {
                measurer.fitted(textStyles.timeTitle, timeTemplates, pillInnerPx, 12.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PillGap)) {
                SleepTimePill(
                    label = stringResource(R.string.sleep_bedtime),
                    time = window.bedtime,
                    timeStyle = timeStyle,
                    animateChanges = rollReadouts,
                    onClickLabel = stringResource(R.string.sleep_pick_bedtime),
                    onClick = { picking = SleepHandle.Bedtime },
                    tag = SleepDialTags.BedtimePill,
                    modifier = Modifier.weight(1f),
                ) { drawPath(crescent(size.minDimension / 2f, center), sleepRole.color) }
                SleepTimePill(
                    label = stringResource(R.string.sleep_wake),
                    time = window.wake,
                    timeStyle = timeStyle,
                    animateChanges = rollReadouts,
                    onClickLabel = stringResource(R.string.sleep_pick_wake),
                    onClick = { picking = SleepHandle.Wake },
                    tag = SleepDialTags.WakePill,
                    modifier = Modifier.weight(1f),
                ) { drawSun(sunPath, center, size.minDimension, sunColor) }
            }
        }
    }

    val hint = when {
        egg != EggState.Hidden -> stringResource(R.string.sleep_egg_line)
        durationMinutes < ShortSleepMinutes -> stringResource(R.string.sleep_hint_short)
        durationMinutes > LongSleepMinutes -> stringResource(R.string.sleep_hint_long)
        else -> null
    }
    val hintContent = @Composable {
        AnimatedVisibility(
            visible = hint != null,
            // One tier: the hint line grows in place, so its fade rides the same container spring as its height.
            enter = expandVertically(motion.containerSpatial()) + fadeIn(motion.containerSpatial()),
            exit = shrinkVertically(motion.containerSpatial()) + fadeOut(motion.containerSpatial()),
        ) {
            Text(
                text = hint.orEmpty(),
                style = if (egg != EggState.Hidden) textStyles.editorialBody else MaterialTheme.typography.bodyMedium,
                color = if (egg != EggState.Hidden) colors.primary else colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp).widthIn(max = PillsMaxWidth),
            )
        }
    }

    // Dial, pills, hint. The pills are measured first so the ring takes what [maxHeight] leaves (never less than
    // MinFitDialSize); the hint is left out of the fit, it appears only for unusual nights and may scroll.
    Layout(contents = listOf(dialContent, pillsContent, hintContent), modifier = modifier) { (dialM, pillsM, hintM), constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else maxDialSize.roundToPx()
        val pillsWidth = min(width, PillsMaxWidth.roundToPx())
        val pills = pillsM.map { it.measure(Constraints.fixedWidth(pillsWidth)) }
        val pillsHeight = pills.maxOfOrNull { it.height } ?: 0
        val gap = PillsTopGap.roundToPx()
        val byHeight = if (maxHeight.isFinite) maxHeight.roundToPx() - gap - pillsHeight else Int.MAX_VALUE
        val side = minOf(width, maxDialSize.roundToPx(), maxOf(byHeight, MinFitDialSize.roundToPx()))
        val dial = dialM.map { it.measure(Constraints.fixed(side, side)) }
        val hints = hintM.map { it.measure(Constraints(maxWidth = width)) }
        val hintHeight = hints.maxOfOrNull { it.height } ?: 0
        val layoutWidth = constraints.constrainWidth(width)
        layout(layoutWidth, constraints.constrainHeight(side + gap + pillsHeight + hintHeight)) {
            dial.forEach { it.place((layoutWidth - it.width) / 2, 0) }
            pills.forEach { it.place((layoutWidth - it.width) / 2, side + gap) }
            hints.forEach { it.place((layoutWidth - it.width) / 2, side + gap + pillsHeight) }
        }
    }

    picking?.let { handle ->
        SleepTimePickerDialog(
            title = stringResource(if (handle == SleepHandle.Bedtime) R.string.sleep_picker_bedtime_title else R.string.sleep_picker_wake_title),
            initial = if (handle == SleepHandle.Bedtime) window.bedtime else window.wake,
            onDismiss = { picking = null },
            onConfirm = { time -> currentOnChange(SleepDialMath.withTime(currentWindow, handle, time)) },
        )
    }
}

/** Cap on the release velocity fed to the settle springs, in dial minutes per second (≈ 1.5 turns/s). */
private const val MaxReleaseVelocity = 2_160f

/** Default cap on the dial's diameter. */
val DefaultMaxDialSize: Dp = 360.dp

/** A sleep duration as "8 h" / "7 h 30 m". */
@Composable
fun formatDuration(minutes: Int): String = formatDuration(LocalResources.current, minutes)

/** A sleep duration as "8 h" / "7 h 30 m", outside composition (rolling readouts format on the fly). */
internal fun formatDuration(resources: Resources, minutes: Int): String =
    if (minutes % 60 == 0) {
        resources.getString(R.string.sleep_duration_hours, minutes / 60)
    } else {
        resources.getString(R.string.sleep_duration_hours_minutes, minutes / 60, minutes % 60)
    }

/**
 * A bedtime or wake readout under the dial: glyph and label over the time, as one button that opens the time
 * picker. TalkBack reads it as "Bedtime, 23:00, button, double-tap to set bedtime".
 */
@Composable
private fun SleepTimePill(
    label: String,
    time: LocalTime,
    timeStyle: TextStyle,
    animateChanges: Boolean,
    onClickLabel: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    glyph: DrawScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = colors.surfaceContainerHigh, modifier = modifier) {
        Column(
            Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = PillPadding, vertical = 8.dp)
                .testTag(tag),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(14.dp), onDraw = glyph)
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RollingTimeText(time, style = timeStyle, color = colors.onSurface, animateChanges = animateChanges)
        }
    }
}

/**
 * M3 time picker in a dialog for one end of the window; 12/24 h follows the system setting. The mode toggle
 * switches to keyboard entry (TimeInput), the quickest way to type an exact minute.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimePickerDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val formatter = rememberTimeFormatter()
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = formatter.is24Hour)
    var keyboard by rememberSaveable { mutableStateOf(false) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(LocalTime.of(state.hour, state.minute))
                    onDismiss()
                },
                modifier = Modifier.testTag(SleepDialTags.PickerConfirm),
            ) { Text(stringResource(R.string.sleep_picker_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sleep_picker_cancel)) } },
        modeToggleButton = {
            IconButton(onClick = { keyboard = !keyboard }) {
                Icon(
                    painterResource(if (keyboard) R.drawable.onboarding_ic_schedule else R.drawable.onboarding_ic_keyboard),
                    contentDescription = stringResource(if (keyboard) R.string.sleep_picker_mode_dial else R.string.sleep_picker_mode_keyboard),
                )
            }
        },
    ) {
        if (keyboard) TimeInput(state = state) else TimePicker(state = state)
    }
}

/** [style] scaled down (never up) so the widest of [templates] fits [maxWidthPx] on one line. */
private fun TextMeasurer.fitted(style: TextStyle, templates: List<String>, maxWidthPx: Int, minFontSize: TextUnit): TextStyle {
    val widest = templates.maxOf { measure(it, style, maxLines = 1, softWrap = false).size.width }
    if (maxWidthPx <= 0 || widest <= maxWidthPx) return style
    val k = maxWidthPx.toFloat() / widest
    val fontSize = (style.fontSize.value * k).coerceAtLeast(minFontSize.value)
    val lineHeight = if (style.lineHeight.isSp) (style.lineHeight.value * fontSize / style.fontSize.value).sp else style.lineHeight
    return style.copy(fontSize = fontSize.sp, lineHeight = lineHeight)
}

/** The widest value the duration readout can show (two-digit hours and minutes). */
private const val DurationTemplateMinutes = 22 * 60 + 55

/** Two-digit hours, morning and evening: the widest times either clock format can show. */
private val TimeTemplates = listOf(LocalTime.of(10, 55), LocalTime.of(22, 55))

private enum class Handle { Moon, Sun }

/** Resistance past the wall: at most [MaxVisualOvershoot] minutes of visible give. */
private fun resisted(rawMinutes: Float): Float =
    if (rawMinutes <= 0f) 0f else MaxVisualOvershoot * (1f - exp(-rawMinutes / 60f))

private fun pointOn(cx: Float, cy: Float, minute: Float, radius: Float): Offset {
    val rad = Math.toRadians(DialGeometry.angleForMinute(minute).toDouble())
    return Offset(cx + (radius * cos(rad)).toFloat(), cy + (radius * sin(rad)).toFloat())
}

/** Scales a path into the unit square (0..1), so it can be drawn at any size. */
private fun Path.normalised(): Path {
    val b = getBounds()
    val s = 1f / maxOf(b.width, b.height)
    val out = Path()
    out.addPath(this, Offset(-b.left, -b.top))
    out.transform(androidx.compose.ui.graphics.Matrix().apply { scale(s, s) })
    return out
}

private fun crescent(radius: Float, c: Offset): Path {
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, radius)) }
    val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + radius * 0.55f, c.y - radius * 0.45f), radius * 0.85f)) }
    return Path.combine(PathOperation.Difference, disc, bite)
}

private fun DrawScope.drawSun(unitPath: Path, c: Offset, side: Float, color: Color) {
    withTransform({
        translate(c.x - side / 2f, c.y - side / 2f)
        scale(side, side, pivot = Offset.Zero)
    }) { drawPath(unitPath, color) }
}

/** Stateless drawing for [SleepDial]; geometry in dial units (1 u = side / 320). */
private class SleepDialPainter(val scope: DrawScope, val u: Float) {
    private val c = scope.center

    private fun point(minute: Float, radius: Float) = pointOn(c.x, c.y, minute, radius * u)

    fun face(fill: Color, outline: Color) = with(scope) {
        drawCircle(fill, FaceRadius * u, c)
        drawCircle(outline.copy(alpha = 0.5f), FaceRadius * u, c, style = Stroke(1f * u))
    }

    /** A thin day/night ring outside the track, so noon reads as day and midnight as night at a glance. */
    fun skyRing(sky: SkyPalette) = with(scope) {
        val stops = (0..48).map { i ->
            val fraction = i / 48f
            fraction to sky.gradientAt(DialGeometry.minuteForAngle(fraction * 360f) / 60f).top
        }.toTypedArray()
        drawCircle(Brush.sweepGradient(*stops, center = c), SkyRingRadius * u, c, style = Stroke(SkyRingWidth * u))
    }

    fun track(color: Color) = with(scope) {
        drawCircle(color, TrackRadius * u, c, style = Stroke(TrackWidth * u))
    }

    fun ticks(
        tick: Color,
        label: Color,
        measurer: androidx.compose.ui.text.TextMeasurer,
        style: androidx.compose.ui.text.TextStyle,
        is24Hour: Boolean,
        fontScale: Float,
    ) = with(scope) {
        val inner = TrackRadius - TrackWidth / 2f - 4f
        for (h in 0 until 24) {
            val major = h % 6 == 0
            val length = if (major) 9f else 5f
            drawLine(
                tick.copy(alpha = if (major) 0.9f else 0.5f),
                point(h * 60f, inner),
                point(h * 60f, inner - length),
                strokeWidth = (if (major) 1.6f else 1f) * u,
                cap = StrokeCap.Round,
            )
        }
        // 06 and 18 as numerals; noon and midnight get a sun and a moon (see marks()).
        val numeral = style.copy(fontSize = (10f * u / density / fontScale).sp, color = label.copy(alpha = 0.85f))
        listOf(6, 18).forEach { h ->
            val text = if (is24Hour) "%02d".format(h) else if (h == 6) "6a" else "6p"
            val layout = measurer.measure(text, numeral)
            val p = point(h * 60f, inner - 20f)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
    }

    fun marks(sunPath: Path, sun: Color, moon: Color) = with(scope) {
        val inner = TrackRadius - TrackWidth / 2f - 24f
        drawSun(sunPath, point(720f, inner), 12f * u, sun)
        drawPath(crescent(5.5f * u, point(0f, inner)), moon)
    }

    fun arc(startMinute: Float, sweepMinutes: Float, color: Color, dots: Color) = with(scope) {
        val r = TrackRadius * u
        drawArc(
            color = color,
            startAngle = DialGeometry.angleForMinute(startMinute),
            sweepAngle = sweepMinutes / DialGeometry.MinutesPerDay * 360f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(2 * r, 2 * r),
            style = Stroke(ArcWidth * u, cap = StrokeCap.Round),
        )
        // Star dots (the Sleep pattern), kept clear of the handles.
        var m = 45f
        var i = 0
        while (m < sweepMinutes - 45f) {
            val offset = if (i % 2 == 0) -5f else 5f
            drawCircle(dots.copy(alpha = 0.55f), (if (i % 3 == 0) 1.6f else 1.1f) * u, point(startMinute + m, TrackRadius + offset))
            m += 38f
            i++
        }
    }

    fun handle(
        minute: Float,
        kind: Handle,
        active: Boolean,
        focused: Boolean,
        disc: Color,
        glyph: Color,
        accent: Color,
        shadow: Color,
        sunPath: Path,
    ) = with(scope) {
        val p = point(minute, TrackRadius)
        if (active) drawCircle(accent.copy(alpha = 0.16f), (HandleRadius + 8f) * u, p)
        translate(1.6f * u, 1.6f * u) { drawCircle(shadow, HandleRadius * u, p) }
        drawCircle(disc, HandleRadius * u, p)
        if (focused) drawCircle(accent, (HandleRadius + 4f) * u, p, style = Stroke(2.5f * u))
        when (kind) {
            Handle.Moon -> drawPath(crescent(8.5f * u, p), glyph)
            Handle.Sun -> drawSun(sunPath, p, 22f * u, glyph)
        }
    }
}

private const val FaceRadius = 157f
private const val SkyRingRadius = 151f
private const val SkyRingWidth = 6f
private const val TrackRadius = 126f
private const val TrackWidth = 38f
private const val ArcWidth = 32f
private const val HandleRadius = 17f
private const val PickToleranceMinutes = 55f
private const val MaxVisualOvershoot = 30f
private const val ShortSleepMinutes = 5 * 60
private const val LongSleepMinutes = 12 * 60
private const val EggHoldMillis = 4_500L
private val HandleTouchSize = 48.dp

/** The ring never shrinks below this to make room for [SleepDial]'s `maxHeight`. */
private val MinFitDialSize = 140.dp
private val PillsTopGap = 12.dp
private val PillGap = 12.dp
private val PillPadding = 16.dp
private val PillsMaxWidth = 360.dp
