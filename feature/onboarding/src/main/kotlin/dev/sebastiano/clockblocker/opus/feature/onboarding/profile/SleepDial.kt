package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import android.content.res.Resources
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.toPath
import des.c5inco.torph.compose.MorphClip
import des.c5inco.torph.compose.MorphEase
import des.c5inco.torph.compose.TextMorph
import dev.sebastiano.clockblocker.opus.core.designsystem.component.ConnectedChoice
import dev.sebastiano.clockblocker.opus.core.designsystem.component.ConnectedChoiceDefaults
import dev.sebastiano.clockblocker.opus.core.designsystem.component.ConnectedChoiceDefaults.fittedWidth
import dev.sebastiano.clockblocker.opus.core.designsystem.component.ConnectedChoiceDefaults.horizontalHeight
import dev.sebastiano.clockblocker.opus.core.designsystem.component.ConnectedChoiceDefaults.verticalHeight
import dev.sebastiano.clockblocker.opus.core.designsystem.component.RollingMetricText
import dev.sebastiano.clockblocker.opus.core.designsystem.component.VerticalConnectedChoice
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialGeometry
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.feature.onboarding.LocalOnboardingDialBounds
import dev.sebastiano.clockblocker.opus.feature.onboarding.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
    const val WorkDays = "sleep_dial_work_days"
    const val DaysOff = "sleep_dial_days_off"
    const val Caption = "sleep_dial_caption"
    const val Hint = "sleep_dial_hint"
    const val HintSlot = "sleep_dial_hint_slot"
}

private enum class EggState { Hidden, Peek, Note }

/** Which of the user's two usual nights the dial is editing. */
enum class SleepSchedule { WorkDays, DaysOff }

/**
 * The sleep-arc dial picker for one night: [SleepDial] with no second schedule (no toggle, no outer ring).
 */
@Composable
fun SleepDial(
    window: SleepWindow,
    onWindowChange: (SleepWindow) -> Unit,
    modifier: Modifier = Modifier,
    easterEggEnabled: Boolean = true,
    maxDialSize: Dp = DefaultMaxDialSize,
    maxHeight: Dp = Dp.Infinity,
    reserveHintSpace: Boolean = true,
) = SleepDialImpl(
    window, null, twoSchedules = false, onWindowChange, onWindowChange, modifier, easterEggEnabled, maxDialSize, maxHeight,
    reserveHintSpace, preferChoiceBeside = false,
)

/**
 * The sleep-arc dial picker for both usual nights: work days and days off (the nights you wake up without an alarm),
 * on a 24 h ring (noon at the top, midnight at the bottom, like the Two Clocks dial). The schedule being edited is the
 * full ring, with a moon handle for bedtime and a sun handle for wake-up (they sit on the arc and grow while held);
 * the other one is the narrower, dimmed ring outside it. A Work days / Days off choice beside the dial (a vertical
 * connected button group with radio semantics; above the dial when it can't stand beside it), or a tap on the outer
 * ring, swaps them: the two rings trade radius and thickness on the Expressive
 * container spring, while their arcs keep their angles (the times are data and never bounce). Under reduce motion
 * the swap cross-fades instead.
 *
 * [daysOff] is `null` while it is linked to [work] (the same nights): the outer ring then shows the same arc, and the
 * first change made on Days off unlinks it through [onDaysOffChange].
 *
 * Drag a handle to move it, or the arc to slide the whole window. Values snap to 5 minutes with a `CLOCK_TICK` haptic
 * every 15.
 *
 * Under the dial, a bedtime pill and a wake pill show the edited schedule's two times (with AM/PM in 12-hour mode).
 * They follow the dial live while dragging and morph on discrete changes (TalkBack, keyboard, a picked time, the
 * egg's rubber band, a schedule switch), with torph's morphing text on the data spring. Tapping a pill opens a time
 * picker for an exact minute.
 *
 * Accessibility: each handle is its own adjustable node (TalkBack swipe up/down or the custom actions move it by
 * 15 minutes) and is keyboard-focusable (arrow keys ±15 minutes); the pills are buttons that open the picker. The
 * outer ring is left out of accessibility: the Work days / Days off choice reaches it.
 *
 * Easter egg **24.2** (design.md §2.7 #4, when [easterEggEnabled]): push the wake handle past a full day and
 * "24:12" peeks out with the Czeisler line before the arc rubber-bands back. Under reduce motion the peek and the
 * rubber band are skipped and the line shows as a brief inline note instead (the static carrier).
 *
 * The centre readout shows the duration in the locale's narrow form ("7h 35m"), sized so it never touches the hour
 * numerals; TalkBack hears the spaced form ("7 h 35 m"). Under the pills, a hint line for unusual nights (or, on linked
 * days off, why a switch changed nothing) fades in and out. With [reserveHintSpace] its slot keeps its height whether a
 * hint shows or not, so nothing moves; without it (a surface that scrolls anyway) the line takes room only while it
 * shows.
 *
 * [maxDialSize] caps the ring's diameter (it otherwise fills the width the choice leaves, up to 360 dp). [maxHeight],
 * when finite, shrinks the ring further so the ring, the choice, the time pills and one line of the hint slot fit that
 * height (all measured, so this holds at any font scale); the ring never goes below 140 dp for it, and a hint taller
 * than one line uses height the ring doesn't need. [preferChoiceBeside] keeps the Work days / Days off choice beside
 * the dial whenever the ring keeps at least 200 dp there (onboarding, where height is short); otherwise it stands
 * beside the dial only when that gives a ring at least as big as the choice above it would.
 */
@Composable
fun SleepDial(
    work: SleepWindow,
    daysOff: SleepWindow?,
    onWorkChange: (SleepWindow) -> Unit,
    onDaysOffChange: (SleepWindow) -> Unit,
    modifier: Modifier = Modifier,
    easterEggEnabled: Boolean = true,
    maxDialSize: Dp = DefaultMaxDialSize,
    maxHeight: Dp = Dp.Infinity,
    reserveHintSpace: Boolean = true,
    preferChoiceBeside: Boolean = true,
) = SleepDialImpl(
    work, daysOff, twoSchedules = true, onWorkChange, onDaysOffChange, modifier, easterEggEnabled, maxDialSize, maxHeight,
    reserveHintSpace, preferChoiceBeside,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SleepDialImpl(
    work: SleepWindow,
    daysOff: SleepWindow?,
    twoSchedules: Boolean,
    onWorkChange: (SleepWindow) -> Unit,
    onDaysOffChange: (SleepWindow) -> Unit,
    modifier: Modifier,
    easterEggEnabled: Boolean,
    maxDialSize: Dp,
    maxHeight: Dp,
    reserveHintSpace: Boolean,
    preferChoiceBeside: Boolean,
) {
    // An explicit key: rotating a phone swaps the step between its one- and two-pane layouts, which moves the dial in
    // the tree; a position-based key would forget which schedule was being edited.
    var schedule by rememberSaveable(key = "sleepDialSchedule") { mutableStateOf(SleepSchedule.WorkDays) }
    val editingDaysOff = twoSchedules && schedule == SleepSchedule.DaysOff
    val window = if (editingDaysOff) daysOff ?: work else work
    val onWindowChange = if (editingDaysOff) onDaysOffChange else onWorkChange
    val colors = MaterialTheme.colorScheme
    val advice = ClockblockTheme.adviceColors
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    val textStyles = ClockblockTheme.textStyles
    val art = ClockblockTheme.artColors
    val formatter = rememberTimeFormatter()
    val view = LocalView.current
    val density = LocalDensity.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val toggleLabelStyle = MaterialTheme.typography.labelLarge

    val currentWindow by rememberUpdatedState(window)
    val currentOnChange by rememberUpdatedState(onWindowChange)
    val currentReduce by rememberUpdatedState(reduce)
    val currentEggEnabled by rememberUpdatedState(easterEggEnabled)
    val currentTwoSchedules by rememberUpdatedState(twoSchedules)
    val currentWork by rememberUpdatedState(work)
    val currentDaysOff by rememberUpdatedState(daysOff ?: work)
    val currentEditingDaysOff by rememberUpdatedState(editingDaysOff)

    // The schedule swap: 0 = work days on the inner ring, 1 = days off. The rings trade radius and thickness on the
    // container spring (Expressive by day); their angles never animate. Under reduce motion the geometry snaps and
    // [swapFade] cross-fades the old picture into the new one.
    val swap = remember { Animatable(if (editingDaysOff) 1f else 0f) }
    val swapFade = remember { Animatable(1f) }
    var fadeFrom by remember { mutableStateOf<SwapPicture?>(null) }
    fun switchTo(next: SleepSchedule) {
        if (!currentTwoSchedules || next == schedule) return
        if (currentReduce) {
            fadeFrom = SwapPicture(swap.value, currentWork, currentDaysOff, currentEditingDaysOff)
            scope.launch { swapFade.snapTo(0f); swapFade.animateTo(1f, motion.colour()); fadeFrom = null }
        }
        schedule = next
    }
    LaunchedEffect(editingDaysOff, reduce) {
        val target = if (editingDaysOff) 1f else 0f
        if (reduce) swap.snapTo(target) else swap.animateTo(target, motion.containerSpatial())
    }

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

    // A held handle grows a little (a rare setup surface, so the container spring may bounce); snaps when reduced.
    val bedGrow = remember { Animatable(1f) }
    val wakeGrow = remember { Animatable(1f) }
    LaunchedEffect(activeHandle) {
        val held = activeHandle
        launch { bedGrow.animateTo(if (held == SleepHandle.Bedtime || held == SleepHandle.Both) HeldHandleScale else 1f, motion.containerSpatial()) }
        launch { wakeGrow.animateTo(if (held == SleepHandle.Wake || held == SleepHandle.Both) HeldHandleScale else 1f, motion.containerSpatial()) }
    }

    fun nudge(handle: SleepHandle, delta: Int) {
        currentOnChange(SleepDialMath.nudge(currentWindow, handle, delta))
    }

    val bedText = formatter.format(window.bedtime)
    val wakeText = formatter.format(window.wake)
    val durationMinutes = SleepDialMath.durationMinutes(window)
    val durationText = formatDuration(durationMinutes)
    val scheduleName = stringResource(if (editingDaysOff) R.string.sleep_days_off else R.string.sleep_work_days)
    val scheduleLabel = stringResource(R.string.sleep_schedule_prefix)
    /** With two schedules, TalkBack hears which one a node edits: "Days off: Bedtime, 23:30". */
    fun named(text: String) = if (twoSchedules) scheduleLabel.format(scheduleName, text) else text
    val description = named(stringResource(R.string.sleep_dial_description, bedText, wakeText, durationText))
    val laterLabel = stringResource(R.string.sleep_action_later)
    val earlierLabel = stringResource(R.string.sleep_action_earlier)
    // Readouts follow a drag live; they only roll for discrete changes (MOTION.md: never per-frame values).
    val rollReadouts = activeHandle == null
    // The centre readout shows the narrow unit form ("12h 25m" in English, from ICU for the locale): it has to fit
    // inside the hour numerals. TalkBack hears the spaced form ([durationText]), as everywhere else in the app.
    val locale = LocalConfiguration.current.locales[0]
    val narrowFormat = remember(locale) { MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.NARROW) }
    val durationFormat: (Float) -> String = remember(narrowFormat) { { narrowFormat.duration(it.roundToInt()) } }
    val durationTemplate = remember(narrowFormat) { narrowFormat.duration(DurationTemplateMinutes) }

    val dialBounds = LocalOnboardingDialBounds.current
    val dialContent: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.onGloballyPositioned { dialBounds?.value = it.boundsInRoot() }) {
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
                            if (r < (TrackRadius - TrackWidth) * u || r > (TrackRadius + TrackWidth / 2f + HandleSlop) * u) {
                                // A tap on the other schedule's ring switches to it (the toggle does the same).
                                val onOuterRing = r >= (OuterRingRadius - OuterRingWidth / 2f - 1f) * u &&
                                    r <= (OuterRingRadius + OuterRingWidth / 2f + OuterRingSlop) * u
                                if (currentTwoSchedules && onOuterRing) {
                                    down.consume()
                                    if (waitForUpOrCancellation() != null) {
                                        switchTo(if (currentEditingDaysOff) SleepSchedule.WorkDays else SleepSchedule.DaysOff)
                                    }
                                }
                                return@awaitEachGesture
                            }
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
                val painter = SleepDialPainter(this, u)
                painter.face(colors.surfaceContainerLow, colors.outlineVariant)
                painter.ticks(colors.outline, colors.onSurfaceVariant, measurer, textStyles.timeLabel, formatter.is24Hour)
                val ringColors = RingColors(colors.surfaceContainerHigh, sleepRole.color, sleepRole.onColor)
                val handles = HandleColors(colors.surfaceContainerLowest, colors.primary, colors.onPrimary, sunColor, colors.primary, art.shadow)
                fun picture(p: Float, workWindow: SleepWindow, daysOffWindow: SleepWindow, editedDaysOff: Boolean, live: Boolean, alpha: Float) {
                    painter.schedules(
                        swap = p,
                        twoSchedules = currentTwoSchedules,
                        work = workWindow,
                        daysOff = daysOffWindow,
                        editedDaysOff = editedDaysOff,
                        extraWakeMinutes = if (live) wakeReturn.value + resisted(overshoot.value) else 0f,
                        bedGrow = bedGrow.value,
                        wakeGrow = wakeGrow.value,
                        focused = focusedHandle,
                        rings = ringColors,
                        handles = handles,
                        sunPath = sunPath,
                        alpha = alpha,
                    )
                }
                val from = fadeFrom
                val f = swapFade.value
                if (from != null && f < 1f) picture(from.swap, from.work, from.daysOff, from.editedDaysOff, live = false, alpha = 1f - f)
                picture(swap.value, currentWork, currentDaysOff, currentEditingDaysOff, live = true, alpha = if (from != null) f else 1f)
            }

            // Centre readout: duration, or the 24:12 peek, over its caption. Both lines are sized together, for the
            // widest duration (two-digit hours and minutes) rather than the current one so dragging never resizes
            // them, and step down until each line keeps a clear inset from the hour numerals and the ticks
            // (measured, so it holds at any dial size, font scale and clock format).
            val captionBase = MaterialTheme.typography.labelLarge
            val captions = listOf(stringResource(R.string.sleep_duration_caption), stringResource(R.string.sleep_egg_caption))
            val readout = remember(measurer, textStyles.timeHeadline, captionBase, durationTemplate, captions, sidePx, formatter.is24Hour, density) {
                val labels = numeralRects(sidePx, measurer, textStyles.timeLabel, formatter.is24Hour, density)
                density.fitReadout(measurer, textStyles.timeHeadline.copy(fontSize = 30.sp, lineHeight = 36.sp), captionBase, durationTemplate, captions, sidePx, labels)
            }
            Column(
                Modifier.align(Alignment.Center).width(with(density) { readout.widthPx.toDp() }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = egg == EggState.Peek,
                    transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
                    label = "sleepCentre",
                ) { peek ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (peek) {
                            BasicText(
                                text = stringResource(R.string.sleep_egg_time),
                                style = textStyles.bodyClockDisplay.copy(
                                    color = colors.primary,
                                    textAlign = TextAlign.Center,
                                    fontSize = readout.duration.fontSize,
                                    lineHeight = readout.duration.lineHeight,
                                ),
                                maxLines = 1,
                                modifier = Modifier.testTag(SleepDialTags.Egg),
                            )
                        } else {
                            RollingMetricText(
                                value = durationMinutes.toFloat(),
                                style = readout.duration,
                                color = colors.onSurface,
                                format = durationFormat,
                                animateChanges = rollReadouts,
                                contentDescription = durationText,
                                modifier = Modifier.testTag(SleepDialTags.Duration),
                            )
                        }
                        BasicText(
                            text = if (peek) captions[1] else captions[0],
                            style = readout.caption.copy(color = colors.onSurfaceVariant, textAlign = TextAlign.Center),
                            maxLines = 1,
                            modifier = Modifier.testTag(SleepDialTags.Caption),
                        )
                    }
                }
            }

            // Adjustable, focusable nodes over each handle (TalkBack, keyboard, e2e).
            listOf(SleepHandle.Bedtime, SleepHandle.Wake).forEach { handle ->
                val time = if (handle == SleepHandle.Bedtime) window.bedtime else window.wake
                val minute = SleepDialMath.minuteOf(time)
                val timeText = formatter.format(time)
                val label = named(
                    stringResource(
                        if (handle == SleepHandle.Bedtime) R.string.sleep_handle_bedtime_description else R.string.sleep_handle_wake_description,
                        timeText,
                    ),
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

    /** The two pills, side by side, or one above the other ([stacked]) in the column beside a short dial. */
    val pillsContent: @Composable (stacked: Boolean) -> Unit = { stacked ->
        BoxWithConstraints(Modifier) {
            // Both pills share one fitted style, sized for the widest time this format can show ("10:55 PM"), so
            // the times never resize while the dial is dragged, at any font scale.
            val pillWidth = if (stacked) maxWidth else (maxWidth - PillGap) / 2
            val pillInnerPx = with(density) { (pillWidth - PillPadding * 2).roundToPx() }
            val timeTemplates = remember(formatter) { TimeTemplates.map { formatter.formatFull(it) } }
            val timeStyle = remember(measurer, textStyles.timeTitle, timeTemplates, pillInnerPx, density) {
                measurer.fitted(textStyles.timeTitle, timeTemplates, pillInnerPx, 12.sp)
            }
            val pills = @Composable { pillModifier: Modifier ->
                SleepTimePill(
                    label = stringResource(R.string.sleep_bedtime),
                    time = window.bedtime,
                    timeStyle = timeStyle,
                    animateChanges = rollReadouts,
                    onClickLabel = stringResource(
                        when {
                            !twoSchedules -> R.string.sleep_pick_bedtime
                            editingDaysOff -> R.string.sleep_pick_bedtime_days_off
                            else -> R.string.sleep_pick_bedtime_work_days
                        },
                    ),
                    onClick = { picking = SleepHandle.Bedtime },
                    tag = SleepDialTags.BedtimePill,
                    modifier = pillModifier,
                ) { drawPath(crescent(size.minDimension / 2f, center), colors.primary) }
                SleepTimePill(
                    label = stringResource(R.string.sleep_wake),
                    time = window.wake,
                    timeStyle = timeStyle,
                    animateChanges = rollReadouts,
                    onClickLabel = stringResource(
                        when {
                            !twoSchedules -> R.string.sleep_pick_wake
                            editingDaysOff -> R.string.sleep_pick_wake_days_off
                            else -> R.string.sleep_pick_wake_work_days
                        },
                    ),
                    onClick = { picking = SleepHandle.Wake },
                    tag = SleepDialTags.WakePill,
                    modifier = pillModifier,
                ) { drawSun(sunPath, center, size.minDimension, sunColor) }
            }
            if (stacked) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PillGap)) { pills(Modifier.fillMaxWidth()) }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PillGap)) { pills(Modifier.weight(1f)) }
            }
        }
    }

    val hintShort = stringResource(R.string.sleep_hint_short)
    val hintLong = stringResource(R.string.sleep_hint_long)
    val hintLinked = stringResource(R.string.sleep_hint_linked)
    val eggLine = stringResource(R.string.sleep_egg_line)
    val hint = when {
        egg != EggState.Hidden -> SleepHint(eggLine, egg = true)
        durationMinutes < ShortSleepMinutes -> SleepHint(hintShort, egg = false)
        durationMinutes > LongSleepMinutes -> SleepHint(hintLong, egg = false)
        // Linked days off show the work-day times, so a switch changes nothing on the dial: say why (an unusual night's
        // hint matters more, so it wins).
        editingDaysOff && daysOff == null -> SleepHint(hintLinked, egg = false)
        else -> null
    }
    val hintStyle = MaterialTheme.typography.bodyMedium
    /**
     * The hint under the pills. With a [reserve] its slot keeps that height (or more, when a taller hint shows: the
     * slot is the last thing in the layout, so it only grows downwards, into height the layout planned for it where it
     * could), so nothing moves when a hint appears or goes. It is feedback on the value being dragged, so it only
     * fades, in place (no spatial motion; under reduce motion the fade still carries it). A null [reserve] keeps no
     * slot: the line takes room only while it shows.
     */
    val hintContent: @Composable (reserve: Dp?) -> Unit = { reserve ->
        Box(
            Modifier.fillMaxWidth().then(if (reserve != null) Modifier.heightIn(min = reserve + HintTopGap) else Modifier).testTag(SleepDialTags.HintSlot),
            contentAlignment = Alignment.TopCenter,
        ) {
            Crossfade(targetState = hint, animationSpec = motion.fade(), label = "sleepHint") { shown ->
                if (shown != null) {
                    Text(
                        text = shown.text,
                        style = if (shown.egg) textStyles.editorialBody else hintStyle,
                        color = if (shown.egg) colors.primary else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = HintTopGap).widthIn(max = PillsMaxWidth).testTag(SleepDialTags.Hint),
                    )
                }
            }
        }
    }

    val toggleLabels = listOf(stringResource(R.string.sleep_work_days), stringResource(R.string.sleep_days_off))
    val toggleTag: (SleepSchedule) -> String = { if (it == SleepSchedule.WorkDays) SleepDialTags.WorkDays else SleepDialTags.DaysOff }
    val toggleLabel: @Composable (SleepSchedule) -> String = { toggleLabels[it.ordinal] }
    /** The Work days / Days off choice stacked beside the dial (the usual place on a phone). */
    val verticalToggle = @Composable {
        VerticalConnectedChoice(SleepSchedule.entries, schedule, ::switchTo, toggleLabel, toggleTag, Modifier.fillMaxWidth(), toggleLabelStyle)
    }
    /** The same choice side by side, above the dial or in the column beside it, when it can't stand beside the dial. */
    val horizontalToggle = @Composable {
        ConnectedChoice(
            SleepSchedule.entries, schedule, ::switchTo, toggleLabel, toggleTag,
            maxLines = ConnectedChoiceDefaults.MaxLines, labelStyle = toggleLabelStyle,
        )
    }
    val pillLabelStyle = MaterialTheme.typography.labelLarge
    val pillTimeTemplate = formatter.formatFull(TimeTemplates.last())
    // Text-only measurements, planned once per size: the layout block re-runs on every drag frame, and must not shape
    // text each time.
    val planMeasurer = rememberTextMeasurer(cacheSize = 0)
    val planner = remember(
        planMeasurer, density, twoSchedules, preferChoiceBeside, reserveHintSpace, maxDialSize, toggleLabels, toggleLabelStyle,
        pillLabelStyle, textStyles.timeTitle, pillTimeTemplate, hintStyle, hintShort, hintLong, hintLinked, eggLine,
        textStyles.editorialBody, easterEggEnabled,
    ) {
        val hints = buildList {
            add(hintShort to hintStyle)
            add(hintLong to hintStyle)
            if (twoSchedules) add(hintLinked to hintStyle)
            if (easterEggEnabled) add(eggLine to textStyles.editorialBody)
        }
        SleepDialPlanner(
            density, planMeasurer, twoSchedules, preferChoiceBeside, reserveHintSpace, maxDialSize,
            toggleLabels, toggleLabelStyle, pillLabelStyle, textStyles.timeTitle, pillTimeTemplate, hintStyle, hints,
        )
    }

    // Where the egg's line is taller than the hint slot can be (a large font on a phone), it takes the pills' place
    // for as long as it shows: the pills fade out under it and back (read in their layer), and nothing moves.
    var eggCoversPills by remember { mutableStateOf(false) }
    val pillsAlpha = remember { Animatable(1f) }
    LaunchedEffect(eggCoversPills) { pillsAlpha.animateTo(if (eggCoversPills) 0f else 1f, motion.fade()) }

    // Two arrangements, both planned from measured text so they hold at any font scale (see SleepDialPlanner).
    SubcomposeLayout(modifier) { constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else maxDialSize.roundToPx()
        val maxH = if (maxHeight.isFinite) maxHeight.roundToPx() else null
        val plan = planner.plan(width, maxH)
        val gap = PillsTopGap.roundToPx()
        val layoutWidth = constraints.constrainWidth(width)
        val reserve = plan.hintSlot?.toDp()
        if (plan.sideBySide) {
            val side = plan.side
            val columnWidth = plan.columnWidth
            val toggle = if (twoSchedules) {
                subcompose(SleepDialSlot.Toggle, horizontalToggle).map { it.measure(Constraints.fixedWidth(columnWidth)) }
            } else {
                emptyList()
            }
            val toggleHeight = toggle.maxOfOrNull { it.height + gap } ?: 0
            val pills = subcompose(SleepDialSlot.Pills) { pillsContent(plan.stackPills) }.map { it.measure(Constraints.fixedWidth(columnWidth)) }
            val pillsHeight = pills.maxOfOrNull { it.height } ?: 0
            val hints = subcompose(SleepDialSlot.Hint) { hintContent(reserve) }.map { it.measure(Constraints.fixedWidth(columnWidth)) }
            val columnHeight = toggleHeight + pillsHeight + (hints.maxOfOrNull { it.height } ?: 0)
            val dial = subcompose(SleepDialSlot.Dial, dialContent).map { it.measure(Constraints.fixed(side, side)) }
            val rowHeight = maxOf(side, columnHeight)
            val start = (layoutWidth - (side + gap + columnWidth)) / 2
            val columnX = start + side + gap
            val columnY = (rowHeight - columnHeight) / 2
            layout(layoutWidth, constraints.constrainHeight(rowHeight)) {
                dial.forEach { it.placeRelative(start, (rowHeight - side) / 2) }
                toggle.forEach { it.placeRelative(columnX, columnY) }
                pills.forEach { it.placeRelative(columnX, columnY + toggleHeight) }
                hints.forEach { it.placeRelative(columnX, columnY + toggleHeight + pillsHeight) }
            }
        } else {
            val side = plan.side
            val pillsWidth = plan.pillsWidth
            val pills = subcompose(SleepDialSlot.Pills) { pillsContent(false) }.map { it.measure(Constraints.fixedWidth(pillsWidth)) }
            val pillsHeight = pills.maxOfOrNull { it.height } ?: 0
            val toggle = when (plan.place) {
                TogglePlace.Beside -> subcompose(SleepDialSlot.Toggle, verticalToggle).map { it.measure(Constraints.fixedWidth(plan.besideWidth)) }
                TogglePlace.Above -> subcompose(SleepDialSlot.Toggle, horizontalToggle).map { it.measure(Constraints.fixedWidth(pillsWidth)) }
                TogglePlace.None -> emptyList()
            }
            val toggleHeight = toggle.maxOfOrNull { it.height } ?: 0
            val topHeight = if (plan.place == TogglePlace.Above) toggleHeight + gap else 0
            val dial = subcompose(SleepDialSlot.Dial, dialContent).map { it.measure(Constraints.fixed(side, side)) }
            val beside = plan.place == TogglePlace.Beside
            val besideGap = BesideGap.roundToPx()
            val rowHeight = if (beside) maxOf(side, toggleHeight) else side
            val rowWidth = if (beside) side + besideGap + plan.besideWidth else side
            val rowX = (layoutWidth - rowWidth) / 2
            val hints = subcompose(SleepDialSlot.Hint) { hintContent(reserve) }.map { it.measure(Constraints.fixedWidth(pillsWidth)) }
            val hintHeight = hints.maxOfOrNull { it.height } ?: 0
            val slotHeight = plan.hintSlot?.let { it + HintTopGap.roundToPx() }
            val covers = hint?.egg == true && slotHeight != null && hintHeight > slotHeight && hintHeight <= pillsHeight + slotHeight
            if (covers != eggCoversPills) eggCoversPills = covers
            val pillsY = topHeight + rowHeight + gap
            val belowPills = if (covers) slotHeight else hintHeight
            layout(layoutWidth, constraints.constrainHeight(pillsY + pillsHeight + belowPills)) {
                if (plan.place == TogglePlace.Above) toggle.forEach { it.placeRelative((layoutWidth - it.width) / 2, 0) }
                dial.forEach { it.placeRelative(rowX, topHeight + (rowHeight - side) / 2) }
                if (beside) toggle.forEach { it.placeRelative(rowX + side + besideGap, topHeight + (rowHeight - it.height) / 2) }
                pills.forEach { it.placeRelativeWithLayer((layoutWidth - it.width) / 2, pillsY) { alpha = pillsAlpha.value } }
                hints.forEach { it.placeRelative((layoutWidth - it.width) / 2, if (covers) pillsY - HintTopGap.roundToPx() else pillsY + pillsHeight) }
            }
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

/** A line under the pills: an unusual night's hint, or the egg's line. */
private data class SleepHint(val text: String, val egg: Boolean)

/** Where each part of [SleepDial] goes for one size: what [SleepDialPlanner] decides before anything is measured. */
private class SleepDialPlan(
    /** The dial on the start side with a column (choice, pills, hint) beside it, rather than stacked. */
    val sideBySide: Boolean,
    /** The ring's diameter. */
    val side: Int,
    /** Stacked: where the Work days / Days off choice goes. */
    val place: TogglePlace,
    /** Stacked, with the choice beside the dial: the choice's width. */
    val besideWidth: Int,
    /** Stacked: the pills' (and the hint's) width. */
    val pillsWidth: Int,
    /** Side by side: the column's width, and whether its two pills go one above the other. */
    val columnWidth: Int,
    val stackPills: Boolean,
    /** The hint slot's reserved height (without its top gap); null for no reserved slot. */
    val hintSlot: Int?,
)

/**
 * Plans [SleepDial]'s layout from measured text, so it holds at any font scale, and remembers the last plan: the
 * layout block runs on every drag frame, and only a new size (width or height) shapes text again.
 *
 * Stacked (phones in portrait, Settings): the dial with the Work days / Days off choice beside it (a vertical
 * connected group, its labels whole on one line, or wrapped between words onto two when the ring would lose width for
 * one line) or above it, then the pills, then the hint's slot. The ring takes the width the choice leaves and shrinks
 * so everything fits the height (never below MinFitDialSize). It pays for one line of hint at most: a slot is as tall
 * as the tallest hint where the height allows it, and never less than one line, so a hint never moves anything (a
 * taller one may reach below the reserved line).
 *
 * Side by side (short, wide spaces such as a landscape phone): the dial on the start side and a column with the
 * choice, the two pills (one above the other when the height allows, else side by side) and the hint, chosen when it
 * gives a clearly bigger ring, or overflows the height less.
 */
private class SleepDialPlanner(
    private val density: Density,
    private val measurer: TextMeasurer,
    private val twoSchedules: Boolean,
    private val preferChoiceBeside: Boolean,
    private val reserveHint: Boolean,
    private val maxDialSize: Dp,
    private val labels: List<String>,
    private val labelStyle: TextStyle,
    private val pillLabelStyle: TextStyle,
    private val pillTimeStyle: TextStyle,
    private val pillTimeTemplate: String,
    private val hintStyle: TextStyle,
    /** Every line the hint slot may show, each in its style. */
    private val hints: List<Pair<String, TextStyle>>,
) {
    private var lastSize: Pair<Int, Int?>? = null
    private var last: SleepDialPlan? = null

    /** The plan for [width] and, when finite, [maxHeight] (px). */
    fun plan(width: Int, maxHeight: Int?): SleepDialPlan {
        val size = width to maxHeight
        last?.let { if (lastSize == size) return it }
        return compute(width, maxHeight).also { lastSize = size; last = it }
    }

    private fun compute(width: Int, maxHeight: Int?): SleepDialPlan = with(density) {
        val maxH = maxHeight ?: (Int.MAX_VALUE / 2)
        val gap = PillsTopGap.roundToPx()
        val pillGap = PillGap.roundToPx()
        val hintGap = HintTopGap.roundToPx()
        val maxDialPx = maxDialSize.roundToPx()
        val minFit = MinFitDialSize.roundToPx()
        fun sideFor(budget: Int) = minOf(width, maxDialPx, maxOf(budget, minFit))

        val pill = maxOf(
            PillMinHeight.roundToPx(),
            measurer.measure("Bg", pillLabelStyle, maxLines = 1).size.height +
                measurer.measure(pillTimeTemplate, pillTimeStyle, maxLines = 1).size.height +
                (PillVerticalPadding * 2).roundToPx(),
        )
        fun tallestHint(w: Int) = hints.maxOf { (text, style) ->
            measurer.measure(text, style, constraints = Constraints(maxWidth = minOf(w, PillsMaxWidth.roundToPx()))).size.height
        }
        val pillsWidth = min(width, PillsMaxWidth.roundToPx())
        val tallest = tallestHint(pillsWidth)
        val hintLine = min(measurer.measure("Bg", hintStyle, maxLines = 1).size.height, tallest)

        // Stacked.
        val budget = maxH - gap - pill - if (reserveHint) hintGap + hintLine else 0
        var place = TogglePlace.None
        var side = sideFor(budget)
        var besideWidth = 0
        var top = side
        if (twoSchedules) {
            val aboveHeight = horizontalHeight(measurer, labels, labelStyle, pillsWidth, ConnectedChoiceDefaults.MaxLines) + gap
            val aboveSide = sideFor(budget - aboveHeight)
            val besideGap = BesideGap.roundToPx()
            val oneLine = fittedWidth(measurer, labels, labelStyle, BesideColumnMaxWidth.roundToPx(), maxLines = 1)
            val narrowest = fittedWidth(measurer, labels, labelStyle, minOf((width * BesideColumnMaxFraction).roundToInt(), BesideColumnMaxWidth.roundToPx()))
            val choiceWidth = oneLine?.takeIf { minOf(width - it - besideGap, maxDialPx) >= side } ?: narrowest
            val besideSide = choiceWidth?.let { minOf(width - it - besideGap, maxDialPx, maxOf(budget, minFit)) } ?: 0
            val beside = besideSide > 0 && (besideSide >= aboveSide || (preferChoiceBeside && besideSide >= MinBesideDialSize.roundToPx()))
            if (beside && choiceWidth != null) {
                place = TogglePlace.Beside
                side = besideSide
                besideWidth = choiceWidth
                top = maxOf(side, verticalHeight(measurer, labels, labelStyle, choiceWidth))
            } else {
                place = TogglePlace.Above
                side = aboveSide
                top = aboveHeight + side
            }
        }
        val used = top + gap + pill
        val slot = when {
            !reserveHint -> null
            maxHeight == null -> tallest
            else -> tallest.coerceAtMost(maxOf(hintLine, maxH - used - hintGap))
        }
        val stackedOverflow = (used + (slot?.let { hintGap + it } ?: 0) - maxH).coerceAtLeast(0)

        // Side by side: the dial takes the height; the column takes the rest of the width (at least SideColumnWidth),
        // so the pills get room for their labels at large fonts. In the column the pills go one above the other while
        // that fits the height, else side by side; the hint keeps a slot under them only while that still fits too (on
        // a landscape phone at a large font it doesn't).
        val sideBySideSide = minOf(width - gap - SideColumnWidth.roundToPx(), maxDialPx, maxH)
        val columnWidth = minOf(width - gap - sideBySideSide, SideColumnMaxWidth.roundToPx())
        val columnToggle = if (twoSchedules) horizontalHeight(measurer, labels, labelStyle, columnWidth, ConnectedChoiceDefaults.MaxLines) + gap else 0
        val stackPills = columnToggle + pill * 2 + pillGap <= maxH
        val columnPills = if (stackPills) pill * 2 + pillGap else pill
        val columnSlot = if (reserveHint) tallestHint(columnWidth).takeIf { columnToggle + columnPills + hintGap + it <= maxH } else null
        val sideBySide = maxHeight != null && sideBySideSide >= minFit && run {
            val column = columnToggle + columnPills + (columnSlot?.let { hintGap + it } ?: 0)
            val overflow = (maxOf(sideBySideSide, column) - maxH).coerceAtLeast(0)
            overflow < stackedOverflow || (overflow == 0 && sideBySideSide >= side + SideBySideGain.roundToPx())
        }
        return if (sideBySide) {
            SleepDialPlan(true, sideBySideSide, TogglePlace.None, 0, pillsWidth, columnWidth, stackPills, columnSlot)
        } else {
            SleepDialPlan(false, side, place, besideWidth, pillsWidth, 0, false, slot)
        }
    }
}

/** Where the Work days / Days off choice goes in the stacked arrangement. */
private enum class TogglePlace { None, Beside, Above }

/** The parts of [SleepDial]'s layout. */
private enum class SleepDialSlot { Toggle, Dial, Pills, Hint }

/** Space above the hint line. */
private val HintTopGap = 12.dp

/** The vertical choice beside the dial: at most this share of the width, or this wide, and this far from the ring. */
private const val BesideColumnMaxFraction = 0.34f
private val BesideColumnMaxWidth = 168.dp
private val BesideGap = 12.dp

/** Below this ring size the choice leaves the dial's side and goes above it. */
private val MinBesideDialSize = 200.dp

/** A duration in the locale's narrow unit form: "12h 25m", "8h" in English. */
internal fun MeasureFormat.duration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        m == 0 -> formatMeasures(Measure(h, MeasureUnit.HOUR))
        h == 0 -> formatMeasures(Measure(m, MeasureUnit.MINUTE))
        else -> formatMeasures(Measure(h, MeasureUnit.HOUR), Measure(m, MeasureUnit.MINUTE))
    }
}

/** The four hour numerals on the face (00, 06, 12, 18, or 12a, 6a, 12p, 6p). */
private fun numeralText(hour: Int, is24Hour: Boolean) = if (is24Hour) "%02d".format(hour) else TwelveHourNumerals.getValue(hour)

/** The numerals' style on a dial where 1 u = [u] px: 10 u tall whatever the font scale (they're part of the drawing). */
private fun Density.numeralStyle(base: TextStyle, u: Float): TextStyle = base.copy(fontSize = (10f * u / density / fontScale).sp)

/** Where each hour numeral's centre sits on a dial where 1 u = [u] px, relative to the dial's centre. */
private fun numeralCentre(hour: Int, u: Float): Offset = pointOn(0f, 0f, hour * 60f, NumeralRadius * u)

/**
 * The hour numerals' boxes on a [sidePx] dial, relative to its centre, as drawn by [SleepDialPainter.ticks]: what the
 * centre readout must keep clear of.
 */
private fun numeralRects(sidePx: Float, measurer: TextMeasurer, base: TextStyle, is24Hour: Boolean, density: Density): List<Rect> {
    val u = sidePx / 320f
    val style = density.numeralStyle(base, u)
    return listOf(0, 6, 12, 18).map { h ->
        val size = measurer.measure(numeralText(h, is24Hour), style).size
        val c = numeralCentre(h, u)
        Rect(c.x - size.width / 2f, c.y - size.height / 2f, c.x + size.width / 2f, c.y + size.height / 2f)
    }
}

/** The centre readout's two line styles and the width they share. */
private class ReadoutStyles(val duration: TextStyle, val caption: TextStyle, val widthPx: Int)

/**
 * Whether the readout's two lines, the duration ([durationW] × [durationH]) over its caption ([captionW] ×
 * [captionH]), each centred and stacked as one block centred on a [sidePx] dial, keep [ReadoutInset] clear of every one
 * of [numerals] (boxes relative to the centre) and stay inside the hour ticks. Each line is checked on its own: the
 * duration sits above the 06 and 18 numerals, so it may be wider than the gap between them.
 */
private fun readoutFits(durationW: Float, durationH: Float, captionW: Float, captionH: Float, sidePx: Float, numerals: List<Rect>): Boolean {
    val u = sidePx / 320f
    val inset = ReadoutInset * u
    val top = -(durationH + captionH) / 2f
    val lines = listOf(
        Rect(-durationW / 2f, top, durationW / 2f, top + durationH),
        Rect(-captionW / 2f, top + durationH, captionW / 2f, top + durationH + captionH),
    )
    val limit = (TicksInnerRadius - ReadoutInset) * u
    return lines.all { line ->
        numerals.none { it.overlaps(line.inflate(inset)) } &&
            listOf(line.topLeft, line.topRight, line.bottomLeft, line.bottomRight).all { it.getDistance() <= limit }
    }
}

/**
 * Sizes the centre readout: [duration] (sized for [template], the widest value) over the widest of [captions], scaled
 * down together until both lines [readoutFits] the face, the duration never below [ReadoutMinFontSize] and the
 * caption never below [CaptionMinFontSize] (sizes at the default font scale, so a large font can't break them).
 *
 * The steps are physical (half a sp at the default font scale): a larger font starts bigger and steps down through the
 * same sizes, so on the same dial the duration is never smaller than at the default font scale. The caption gives way
 * first, so a long caption at a large font doesn't shrink the duration.
 */
private fun Density.fitReadout(
    measurer: TextMeasurer,
    duration: TextStyle,
    caption: TextStyle,
    template: String,
    captions: List<String>,
    sidePx: Float,
    numerals: List<Rect>,
): ReadoutStyles {
    // The floors are physical sizes (12 and 10 sp at the default font size): a large font can't push the readout
    // into the numerals on a small dial.
    val durationFloor = (ReadoutMinFontSize.value / fontScale).sp
    val captionFloor = (CaptionMinFontSize.value / fontScale).sp
    val start = duration.fontSize.value * fontScale
    var physical = start
    while (true) {
        val k = physical / start
        val d = duration.scaled(k, durationFloor)
        val dl = measurer.measure(template, d, maxLines = 1, softWrap = false).size
        val durationAtFloor = d.fontSize.value <= durationFloor.value
        // The caption (the longer egg caption above all) steps down on its own first, down to its floor, so it never
        // costs the duration size; it starts no bigger than the duration's scale allows, keeping the hierarchy.
        var captionK = k
        while (true) {
            val c = caption.scaled(captionK, captionFloor)
            val cw = captions.maxOf { measurer.measure(it, c, maxLines = 1, softWrap = false).size.width }
            val ch = measurer.measure(captions.first(), c, maxLines = 1).size.height
            val captionAtFloor = c.fontSize.value <= captionFloor.value
            val fits = readoutFits(dl.width + 1f, dl.height.toFloat(), cw + 1f, ch.toFloat(), sidePx, numerals)
            if (fits || (durationAtFloor && captionAtFloor)) return ReadoutStyles(d, c, maxOf(dl.width, cw) + 1)
            if (captionAtFloor) break
            captionK -= ReadoutStepSp / start
        }
        physical -= ReadoutStepSp
    }
}

/** [this] style at [k] times its size (line height too), never below [floor]. */
private fun TextStyle.scaled(k: Float, floor: TextUnit): TextStyle {
    val size = (fontSize.value * k).coerceAtLeast(floor.value)
    val ratio = size / fontSize.value
    val line = if (lineHeight.isSp) (lineHeight.value * ratio).sp else lineHeight
    return copy(fontSize = size.sp, lineHeight = line)
}

/** The centre readout keeps this clear of the numerals and the ticks, in dial units. */
private const val ReadoutInset = 4f
private val ReadoutMinFontSize = 12.sp
private val CaptionMinFontSize = 10.sp

/** The readout's size steps, in sp at the default font scale. */
private const val ReadoutStepSp = 0.5f

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
                .heightIn(min = PillMinHeight)
                .padding(horizontal = PillPadding, vertical = PillVerticalPadding)
                .testTag(tag),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(14.dp), onDraw = glyph)
                Spacer(Modifier.width(6.dp))
                // Steps down a little before it would ever cut the word off (narrow pills at large fonts).
                val labelStyle = MaterialTheme.typography.labelLarge
                Text(
                    label,
                    style = labelStyle,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = labelStyle.fontSize * 0.8f, maxFontSize = labelStyle.fontSize),
                )
            }
            MorphingTime(time, style = timeStyle, color = colors.onSurface, animateChanges = animateChanges)
        }
    }
}

/**
 * A pill's time as torph's morphing text (vendored in `:third-party:torph`): changed digits roll by place value and
 * the rest glides, on the time morph spring (no bounce: a time is data). Rolling digits are clipped to the time's
 * line box, so the neighbouring digits never paint over the label above. It follows a drag live ([animateChanges]
 * false) and snaps under the app's reduce motion, which already follows the system's animator duration setting.
 *
 * torph's own system check is off (`respectReducedMotion = false`): it read `ValueAnimator.areAnimatorsEnabled()` once,
 * when the pills first composed, and the process's animator scale can be 0 while the setting is on, so the pills
 * snapped while every other animation in the app moved. Its frame clock follows Compose's `MotionDurationScale`
 * (vendored patch), like the rest of the app's motion.
 */
@Composable
private fun MorphingTime(time: LocalTime, style: TextStyle, color: Color, animateChanges: Boolean) {
    val formatter = rememberTimeFormatter()
    val motion = ClockblockTheme.motion
    val ease = remember(motion) { motion.timeMorph<Float>().toMorphEase() }
    TextMorph(
        text = formatter.formatFull(time),
        style = style,
        color = color,
        ease = ease ?: MorphEase.Curve(),
        clip = MorphClip.lineBox(),
        disabled = !animateChanges || motion.reduceMotion || ease == null,
        respectReducedMotion = false,
    )
}

/** A spring token as torph's spring (stiffness, damping coefficient c = 2ζ√k, unit mass); null for a snap. */
private fun FiniteAnimationSpec<Float>.toMorphEase(): MorphEase? =
    (this as? SpringSpec<Float>)?.let { MorphEase.Spring(stiffness = it.stiffness, damping = 2f * it.dampingRatio * sqrt(it.stiffness), mass = 1f) }

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
private const val DurationTemplateMinutes = 23 * 60 + 55

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
    out.transform(Matrix().apply { scale(s, s) })
    return out
}

private fun crescent(radius: Float, c: Offset): Path {
    val disc = Path().apply { addOval(Rect(c, radius)) }
    val bite = Path().apply { addOval(Rect(Offset(c.x + radius * 0.55f, c.y - radius * 0.45f), radius * 0.85f)) }
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

    /**
     * Both schedules for a [swap] position (0 = work days on the inner ring, 1 = days off). The other schedule is
     * drawn first, the edited one on top with its handles; the handles scale with their ring's thickness, so they
     * travel in with it during a swap. Only radius, thickness and emphasis change with [swap]: each arc keeps its
     * angles.
     */
    fun schedules(
        swap: Float,
        twoSchedules: Boolean,
        work: SleepWindow,
        daysOff: SleepWindow,
        editedDaysOff: Boolean,
        extraWakeMinutes: Float,
        bedGrow: Float,
        wakeGrow: Float,
        focused: SleepHandle?,
        rings: RingColors,
        handles: HandleColors,
        sunPath: Path,
        alpha: Float,
    ) {
        val workRing = RingGeometry.at(if (twoSchedules) swap else 0f)
        val daysOffRing = RingGeometry.at(1f - swap)
        val (edited, editedRing) = if (editedDaysOff) daysOff to daysOffRing else work to workRing
        val (other, otherRing) = if (editedDaysOff) work to workRing else daysOff to daysOffRing
        if (twoSchedules) ring(other, otherRing, 0f, rings, alpha)
        ring(edited, editedRing, extraWakeMinutes, rings, alpha)
        val bed = SleepDialMath.minuteOf(edited.bedtime).toFloat()
        val wake = bed + SleepDialMath.durationMinutes(edited) + extraWakeMinutes
        val k = (editedRing.width / TrackWidth).coerceIn(0f, 1.2f)
        handle(bed, editedRing.radius, Handle.Moon, bedGrow * k, focused == SleepHandle.Bedtime, handles.ring, handles.moon, handles.onMoon, handles, sunPath, alpha)
        handle(wake, editedRing.radius, Handle.Sun, wakeGrow * k, focused == SleepHandle.Wake, handles.ring, handles.sun, handles.ring, handles, sunPath, alpha)
    }

    /** One schedule: its track, the sleep arc and, while the ring is thick enough, the arc's star dots. */
    private fun ring(window: SleepWindow, geometry: RingGeometry, extraWakeMinutes: Float, colors: RingColors, alpha: Float) = with(scope) {
        val r = geometry.radius * u
        val a = alpha * geometry.emphasis
        drawCircle(colors.track, r, c, style = Stroke(geometry.width * u), alpha = alpha)
        val start = SleepDialMath.minuteOf(window.bedtime).toFloat()
        val sweep = SleepDialMath.durationMinutes(window) + extraWakeMinutes
        drawArc(
            color = colors.arc,
            startAngle = DialGeometry.angleForMinute(start),
            sweepAngle = sweep / DialGeometry.MinutesPerDay * 360f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(2 * r, 2 * r),
            style = Stroke(geometry.width * ArcRatio * u, cap = StrokeCap.Round),
            alpha = a,
        )
        // Star dots (the Sleep pattern), kept clear of the handles; only once the ring is wide enough to hold them.
        val k = geometry.width / TrackWidth
        if (k < StarsMinScale) return@with
        var m = 45f
        var i = 0
        while (m < sweep - 45f) {
            val offset = (if (i % 2 == 0) -5f else 5f) * k
            drawCircle(colors.stars, (if (i % 3 == 0) 1.6f else 1.1f) * k * u, pointOn(c.x, c.y, start + m, r + offset * u), alpha = 0.55f * a)
            m += 38f
            i++
        }
    }

    fun ticks(
        tick: Color,
        label: Color,
        measurer: TextMeasurer,
        style: TextStyle,
        is24Hour: Boolean,
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
        // Plain numerals at the four quarters. No sun or moon marks: the handles are the sun and the moon. Their
        // boxes come from the same helpers as numeralRects, which the centre readout keeps clear of.
        val numeral = numeralStyle(style, u).copy(color = label.copy(alpha = 0.85f))
        listOf(0, 6, 12, 18).forEach { h ->
            val layout = measurer.measure(numeralText(h, is24Hour), numeral)
            val p = c + numeralCentre(h, u)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
    }

    /**
     * A handle sitting on the arc (after Prime Meridian's sleep dial): a filled [fill] disc with the moon or sun in
     * [glyph], inside a [ring] of the dial's surface so it reads against the arc and the track. [scale] covers both
     * the hold (it grows while held) and the swap (it travels in with its ring).
     */
    private fun handle(
        minute: Float,
        radius: Float,
        kind: Handle,
        scale: Float,
        focused: Boolean,
        ring: Color,
        fill: Color,
        glyph: Color,
        colors: HandleColors,
        sunPath: Path,
        alpha: Float,
    ) = with(scope) {
        if (scale <= 0.01f) return@with
        val p = point(minute, radius)
        val outer = HandleRadius * scale * u
        translate(1.4f * u, 1.4f * u) { drawCircle(colors.shadow, outer, p, alpha = alpha) }
        drawCircle(ring, outer, p, alpha = alpha)
        drawCircle(fill, HandleFillRadius * scale * u, p, alpha = alpha)
        if (focused) drawCircle(colors.focus, outer + 3f * u, p, style = Stroke(2.5f * u), alpha = alpha)
        when (kind) {
            Handle.Moon -> drawPath(crescent(8.5f * scale * u, p), glyph, alpha = alpha)
            Handle.Sun -> drawSun(sunPath, p, 21f * scale * u, glyph.copy(alpha = glyph.alpha * alpha))
        }
    }
}

/** Where a schedule's ring sits: [radius] and [width] in dial units, [emphasis] its arc's opacity. */
private data class RingGeometry(val radius: Float, val width: Float, val emphasis: Float) {
    companion object {
        /** 0 = the edited ring (inside, full width), 1 = the other one (outside, narrow, dimmed). Springs may overshoot. */
        fun at(t: Float) = RingGeometry(
            radius = TrackRadius + (OuterRingRadius - TrackRadius) * t,
            width = (TrackWidth + (OuterRingWidth - TrackWidth) * t).coerceAtLeast(1f),
            emphasis = (1f + (OuterRingEmphasis - 1f) * t).coerceIn(OuterRingEmphasis, 1f),
        )
    }
}

private class RingColors(val track: Color, val arc: Color, val stars: Color)

private class HandleColors(
    val ring: Color,
    val moon: Color,
    val onMoon: Color,
    val sun: Color,
    val focus: Color,
    val shadow: Color,
)

/** The picture a reduce-motion swap cross-fades from. */
private class SwapPicture(val swap: Float, val work: SleepWindow, val daysOff: SleepWindow, val editedDaysOff: Boolean)

private val TwelveHourNumerals = mapOf(0 to "12a", 6 to "6a", 12 to "12p", 18 to "6p")

private const val FaceRadius = 157f

/** The hour numerals' centres, and the inner end of the hour ticks (the centre readout stays inside both). */
private const val NumeralRadius = 81f
private const val TicksInnerRadius = 92f

/** The edited schedule's ring. */
private const val TrackRadius = 122f
private const val TrackWidth = 34f

/** The arc's share of its ring's width. */
private const val ArcRatio = 28f / 34f

/** The other schedule's ring: outside, narrower and dimmed (it replaced the old day/night sky ring). */
private const val OuterRingRadius = 151f
private const val OuterRingWidth = 9f
private const val OuterRingEmphasis = 0.5f

/** Below this share of the full width, a ring has no room for star dots. */
private const val StarsMinScale = 0.6f

/** Touch slop around the edited ring's outer edge (the handles reach a little past it) and around the outer ring. */
private const val HandleSlop = 6f
private const val OuterRingSlop = 8f

/** Handle ring radius (a little wider than the track, like Prime Meridian's) and its filled disc. */
private const val HandleRadius = 21.5f
private const val HandleFillRadius = 18f

/** How much a handle grows while it's held: it stays clear of the outer ring (146.5) at 145.7. */
private const val HeldHandleScale = 1.1f
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
private val PillMinHeight = 56.dp
private val PillVerticalPadding = 8.dp

/** The column beside a short dial (toggle and stacked pills) when the dial and its controls sit side by side. */
private val SideColumnWidth = 232.dp
private val SideColumnMaxWidth = 360.dp

/** How much bigger the ring must get before side by side wins over stacked when both fit. */
private val SideBySideGain = 32.dp
