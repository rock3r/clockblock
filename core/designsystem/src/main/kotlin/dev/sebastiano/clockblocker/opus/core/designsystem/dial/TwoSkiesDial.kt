package dev.sebastiano.clockblocker.opus.core.designsystem.dial

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.R
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.label
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.BodyRingMode
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.TwoSkies
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.LocalReduceMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.time.TimeFormatter
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * **Two skies**: the signature 24 h dial (design.md §A, issue #46). Noon at the top, midnight at the bottom.
 *
 * - Outer ring: the sky where you are, labelled "{place} day / night".
 * - Inner ring: the sky your body thinks it is under, i.e. the same sky turned by the jet lag ([bodyRing]). It
 *   turns towards alignment when [state] moves to a new day; once adapted the two skies are identical.
 * - One needle across both; the advice under it is one labelled arc outside the rings, narrated on the rim.
 * - Centre: local time upright, body time slanted, the offset in words ("7 h behind").
 *
 * The marks come from the renderer-agnostic [TwoSkies] spec (shared with the widget and a future watch face);
 * this composable adds the interaction. Drag the hand to scrub (hour-tick haptics, [onScrub]); tap the centre to
 * return to now. Easter egg: long-press the centre, then spin a full turn counter-clockwise → [onRewind].
 *
 * @param returnOnRelease when true, lifting the finger after a scrub snaps the hand back to now right after
 *   [onScrubEnd]: a "peek" preview rather than a persistent one.
 * @param onRingsAligned called once the inner ring has finished turning into alignment (after the CONFIRM click).
 * @param bodyRing what the inner ring shows: the turned local sky (default) or the planner's biological night.
 */
@Composable
fun TwoSkiesDial(
    state: DialState,
    modifier: Modifier = Modifier,
    onScrub: ((Instant) -> Unit)? = null,
    onScrubEnd: (() -> Unit)? = null,
    onRewind: (() -> Unit)? = null,
    easterEggsEnabled: Boolean = true,
    returnOnRelease: Boolean = false,
    onRingsAligned: (() -> Unit)? = null,
    bodyRing: BodyRingMode = BodyRingMode.Simple,
) {
    val motion = ClockblockTheme.motion
    val reduce = LocalReduceMotion.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val formatter = rememberTimeFormatter()
    val palette = rememberDialPalette()
    val labels = rememberDialLabels()
    val fonts = rememberDialFonts()
    val pixelGlyphs = ClockblockTheme.pixelMode

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

    // Body sky rotation: animate along the shortest path when the day (body offset) changes. The offset pill reads
    // the same animated value, so the words and the ring move as one event. The rings "click" (CONFIRM haptic,
    // [onRingsAligned]) once the turn into alignment has landed, not when it starts.
    val bodyAhead = remember { Animatable(state.bodyAheadMinutes) }
    var wasAligned by remember { mutableStateOf(state.isAligned) }
    val currentOnRingsAligned by rememberUpdatedState(onRingsAligned)
    LaunchedEffect(state.bodyAheadMinutes, state.isAligned) {
        val target = bodyAhead.value + DialGeometry.minuteDelta(bodyAhead.value, state.bodyAheadMinutes)
        if (reduce) bodyAhead.snapTo(target) else bodyAhead.animateTo(target, motion.dialDayRotation())
        if (state.isAligned && !wasAligned) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            currentOnRingsAligned?.invoke()
        }
        wasAligned = state.isAligned
    }

    val boundaries = remember(state) { blockBoundaries(state) }
    val stops = remember(state) { blockBoundaryStops(state) }

    // The real instant the hand last landed on, when it isn't the face's own (a block boundary in a fall-back night's
    // repeated hour, see blockBoundaryInstant). The drawn body clock and the description follow it, as the host's
    // cards do; any other report clears it. Saved with the scrub offset, so a configuration change keeps it.
    var landed by rememberSaveable(stateSaver = LandingSaver) { mutableStateOf<Landing?>(null) }
    val landing by remember { derivedStateOf { landed?.takeIf { it.offset == scrub.value } } }

    // The scrub offset is minutes of the wall-clock face (as are the arcs and boundaries); the host gets the real
    // instant under the hand, which differs across a DST change. [at] overrides it when the caller knows better (see
    // landed above), resolved when now was [anchor]: the host gets it moved on to the current now, as the dial shows it.
    fun report(offset: Float, at: Instant? = null, anchor: Instant = currentState.instant) {
        val landing = at?.let { Landing(offset, it, anchor) }
        landed = landing
        currentOnScrub?.invoke(landing?.atFor(currentState) ?: currentState.instantAt(offset))
    }

    // A preview is reported again whenever now moves on, so the host's cards agree with the hand: the instant under an
    // offset hand moves with now, and a landing moves on by the real time since it was resolved (#113). The first run
    // covers a restored preview (see ScrubSaver): off now, or at now on the face but in the other run of a repeated
    // hour (a landing at offset 0). A hand at now reports nothing; the host already follows now.
    LaunchedEffect(state.instant) {
        val current = landing
        if (scrub.value != 0f || current != null) report(scrub.value, current?.at, current?.anchor ?: currentState.instant)
    }

    /**
     * Moves the hand on [spec] and reports every frame, so the cards and the sky travel with it: the hand and the
     * data it drives are one event, never two that disagree (motion review: scrub release, Rewind). The last report
     * is [at] (resolved when now was [anchor]) when given.
     */
    suspend fun animateReporting(
        target: Float,
        spec: AnimationSpec<Float>,
        initialVelocity: Float = 0f,
        at: Instant? = null,
        anchor: Instant? = null,
    ) {
        scrub.animateTo(target, spec, initialVelocity) { report(value) }
        report(target, at, anchor ?: currentState.instant)
    }

    fun animateScrubTo(offset: Float, at: Instant? = null, anchor: Instant? = null) {
        scope.launch { animateReporting(offset, motion.dataSpatial(), at = at, anchor = anchor) }
    }

    /**
     * Next/Previous block: lands on the boundary's real instant, so the cards agree the block has changed. The
     * boundary is resolved against now as it is here; if the minute ticks over during the move, the landing moves on
     * with it rather than being measured against the new now.
     */
    fun animateScrubToBoundary(stop: BoundaryStop) {
        animateScrubTo(stop.offset, stop.instant, currentState.instant)
    }

    // Next/Previous block step through the boundaries in real time from the instant under the hand (#106).
    fun handInstant(): Instant = landing?.atFor(currentState) ?: currentState.instantAt(scrub.value)

    // The stop the hand is on: within a minute of it both on the face and in real time. Either alone isn't enough:
    // two boundaries can share one face time in a fall-back night, or sit a real minute apart across a spring-forward
    // gap while an hour apart on the face.
    fun BoundaryStop.isUnderHand(hand: Instant): Boolean =
        abs(offset - scrub.value) < 1f && Duration.between(hand, instant).abs() < StopTolerance

    // Semantics (composition): coarse scrub so TalkBack text doesn't recompose every frame.
    val coarseScrub by remember { derivedStateOf { (scrub.value / 5f).toInt() * 5f } }
    val shown = landing?.let { state.scrubbedTo(it.offset, it.atFor(state)) } ?: if (coarseScrub == 0f) state else state.scrubbedTo(coarseScrub)
    val description = dialDescription(shown, formatter)
    val nextLabel = stringResource(R.string.dial_action_next_block)
    val previousLabel = stringResource(R.string.dial_action_previous_block)
    val nowLabel = stringResource(R.string.dial_action_back_to_now)

    Canvas(
        modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clearAndSetSemantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) {
                        val hand = handInstant()
                        stops.firstOrNull { it.instant > hand && !it.isUnderHand(hand) }?.let { animateScrubToBoundary(it); true } ?: false
                    },
                    CustomAccessibilityAction(previousLabel) {
                        val hand = handInstant()
                        stops.lastOrNull { it.instant < hand && !it.isUnderHand(hand) }?.let { animateScrubToBoundary(it); true } ?: false
                    },
                    CustomAccessibilityAction(nowLabel) { animateScrubTo(0f); true },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val hub = TwoSkies.hubRadius(min(size.width, size.height) / density) * density
                    val r = (down.position - c).getDistance()
                    val haptics = ScrubHaptics(view, currentState, boundaries)
                    if (r < hub) {
                        val allowEgg = easterEggsEnabled && currentOnRewind != null && !reduce
                        val longPress = if (allowEgg) awaitLongPressOrCancellation(down.id) else null
                        if (longPress == null) {
                            // A tap on the centre returns the hand to now; the cards travel with it. A landing at
                            // offset 0 (now on the face, but the other run of a repeated hour) is a preview too.
                            if (scrub.value != 0f || landing != null) {
                                scope.launch {
                                    animateReporting(0f, motion.dataSpatial())
                                    currentOnScrubEnd?.invoke()
                                }
                            }
                            return@awaitEachGesture
                        }
                        // Rewind: follow the finger around the dial, accumulating signed rotation.
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
                            // The day runs backwards under the finger: cards and skies follow the hand.
                            report(offset)
                            change.consume()
                            if (!fired && total <= -360f) {
                                fired = true
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                currentOnRewind?.invoke()
                                break
                            }
                        }
                        // Finger momentum carries into the return (F-002), in minutes per second.
                        val release = velocity.calculateVelocity().takeIf { it.isFinite() } ?: 0f
                        scope.launch {
                            if (fired) {
                                // The payoff: the whole day spins back from the raw −1440 to now, so the
                                // overshoot is visible and the needle sweeps the skies with it.
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
                    if (currentOnScrub == null) return@awaitEachGesture
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
        // The spec is laid out in the draw phase from the animated values (scrub, body offset), so a moving hand
        // or a turning sky never recomposes.
        val spec = TwoSkies.spec(
            state = currentState,
            palette = palette,
            labels = labels,
            widthDp = size.width / density,
            heightDp = size.height / density,
            scrubMinutes = scrub.value,
            // The spec turns the body sky by the clock change under the hand on the face; a landing in the second run
            // of the repeated hour is that much more real time on, so the body clock reads that much later.
            bodyAheadMinutes = bodyAhead.value + (landing?.let { currentState.minutesPastFace(it) } ?: 0f),
            mode = bodyRing,
            measurer = ComposeDialTextMeasurer(measurer, fonts, this),
            textGrowth = fontScale,
        )
        drawDialSpec(spec, measurer, fonts, pixelGlyphs)
    }
}

/**
 * The Two skies dial as a still picture at [state] (no gestures, no semantics): previews, sheets and the
 * widget-font comparison. [fonts] picks the face, e.g. [DialFonts.System] for what a widget will look like.
 */
@Composable
fun StaticTwoSkiesDial(
    state: DialState,
    modifier: Modifier = Modifier,
    scrubMinutes: Float = 0f,
    bodyRing: BodyRingMode = BodyRingMode.Simple,
    fonts: DialFonts = rememberDialFonts(),
) {
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val palette = rememberDialPalette()
    val labels = rememberDialLabels()
    val pixelGlyphs = ClockblockTheme.pixelMode
    Canvas(modifier) {
        val spec = TwoSkies.spec(
            state = state,
            palette = palette,
            labels = labels,
            widthDp = size.width / density,
            heightDp = size.height / density,
            scrubMinutes = scrubMinutes,
            mode = bodyRing,
            measurer = ComposeDialTextMeasurer(measurer, fonts, this),
            textGrowth = fontScale,
        )
        drawDialSpec(spec, measurer, fonts, pixelGlyphs)
    }
}

/** Fires CLOCK_TICK on every hour crossed while scrubbing, SEGMENT_TICK on advice boundaries. */
private class ScrubHaptics(private val view: View, private val state: DialState, private val boundaries: List<Float>) {
    private var lastHour: Int? = null
    private var lastOffset: Float? = null

    fun update(offset: Float) {
        val hour = floor((state.localMinute + offset) / 60f).toInt()
        val previous = lastOffset
        if (previous != null) {
            val crossedBoundary = boundaries.any { b -> (previous < b && offset >= b) || (previous > b && offset <= b) }
            when {
                crossedBoundary ->
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

/**
 * The real instant of the block boundary [offset] minutes of the face from now (one of [blockBoundaries]): the
 * start or end of the block drawn there, when the window doesn't clip it. The face alone can't tell the two runs of
 * a fall-back night's repeated hour apart ([DialState.instantAt] takes the side the hand started on), but the block
 * knows which 01:30 it ends at (#94). Anything else, or an arc without instants, falls back to [DialState.instantAt].
 */
internal fun blockBoundaryInstant(state: DialState, offset: Float): Instant {
    val zone = runCatching { ZoneId.of(state.displayZoneId) }.getOrNull() ?: return state.instantAt(offset)
    fun at(minutes: Float) = abs(minutes - offset) < BoundaryToleranceMinutes
    for (arc in state.arcs) {
        val start = DialGeometry.relativeMinute(state.localMinute, arc.startMinute)
        val end = start + arc.sweepMinutes
        arc.startInstant?.takeIf { at(start) && state.isBoundaryHere(it, offset, zone) }?.let { return it }
        arc.endInstant?.takeIf { at(end) && state.isBoundaryHere(it, offset, zone) }?.let { return it }
    }
    return state.instantAt(offset)
}

/**
 * Whether [candidate] is a block boundary drawn [offset] minutes of the face from now. Only an instant whose
 * wall-clock time is the boundary's: not a start the window clips, nor the real end of a block drawn forward past it
 * (one that ends earlier on the face than it starts). And only one on the same side of now in real time as on the
 * face: from the second run of the repeated hour, a first-run 01:45 sits ahead on the face but is already past. At
 * the hand's own face time (offset 0) either run will do: the other one is an hour away in real time, but at now on
 * the face.
 */
private fun DialState.isBoundaryHere(candidate: Instant, offset: Float, zone: ZoneId): Boolean {
    val real = Duration.between(instant, candidate)
    val sameSide = when {
        offset > 0f -> real > Duration.ZERO
        offset < 0f -> real < Duration.ZERO
        else -> true
    }
    return sameSide && abs(faceMinutesFrom(instant, candidate, zone) - offset) < BoundaryToleranceMinutes
}

/** A block boundary the hand can land on: [offset] minutes of the face from now, at the real [instant]. */
internal data class BoundaryStop(val offset: Float, val instant: Instant)

/**
 * The block boundaries ([blockBoundaries]) in real-time order, each at its own block's real instant when that is the
 * one drawn there (see [blockBoundaryInstant]), for Next/Previous block. Two boundaries can share one face time in a
 * fall-back night (a block ending at the first 01:30, another starting at the second): listed by face offset only one
 * of them could ever be visited (#106); listed by instant, both are stops.
 */
internal fun blockBoundaryStops(state: DialState): List<BoundaryStop> {
    val zone = runCatching { ZoneId.of(state.displayZoneId) }.getOrNull()
    return state.arcs
        .flatMap { arc ->
            val start = DialGeometry.relativeMinute(state.localMinute, arc.startMinute)
            if (arc.sweepMinutes == 0f) {
                listOf(start to arc.startInstant)
            } else {
                listOf(start to arc.startInstant, start + arc.sweepMinutes to arc.endInstant)
            }
        }
        .filter { (offset, _) -> offset >= -DialState.PastWindowMinutes && offset < DialGeometry.MinutesPerDay - DialState.PastWindowMinutes }
        .map { (offset, own) ->
            val here = own?.takeIf { zone != null && state.isBoundaryHere(it, offset, zone) }
            (here != null) to BoundaryStop(offset, here ?: blockBoundaryInstant(state, offset))
        }
        // Two stops at one instant: keep a block's own boundary over a fallback. A window edge clipped inside a
        // spring-forward gap falls back to the change itself, the instant a block starting at the change really has.
        .groupBy { (_, stop) -> stop.instant }
        .map { (_, same) -> (same.firstOrNull { (own, _) -> own } ?: same.first()).second }
        .sortedBy { it.instant }
}

private const val BoundaryToleranceMinutes = 0.01f

// How close in real time a stop must be, as well as on the face, to count as the one under the hand (isUnderHand).
private val StopTolerance: Duration = Duration.ofMinutes(1)

/**
 * The hand at [offset] on the face, at the real instant [at] (see blockBoundaryInstant), landed when the dial was
 * anchored at [anchor]. The offset is from now, so as now moves on the hand and its instant move with it ([atFor]).
 */
private data class Landing(val offset: Float, val at: Instant, val anchor: Instant) {
    /**
     * The landing's instant for [state]: moved on by the real time since [anchor] while that is still the wall-clock
     * time under the hand (the same run of the repeated hour). Once now itself crosses the change, the moved instant
     * no longer sits at [offset] on the face, and the face's own instant is the hand's.
     */
    fun atFor(state: DialState): Instant {
        val moved = at.plus(Duration.between(anchor, state.instant))
        val zone = runCatching { ZoneId.of(state.displayZoneId) }.getOrNull() ?: return moved
        return if (abs(faceMinutesFrom(state.instant, moved, zone) - offset) < BoundaryToleranceMinutes) moved else state.instantAt(offset)
    }
}

/** Real minutes from the instant the face gives the [landing]'s offset to the landing's own instant. */
private fun DialState.minutesPastFace(landing: Landing): Float =
    Duration.between(instantAt(landing.offset), landing.atFor(this)).seconds / 60f

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

/** A [Landing] as its offset and two epoch milliseconds; nothing when there is none. */
private val LandingSaver: Saver<Landing?, Any> = Saver(
    save = { landing -> landing?.let { arrayListOf(it.offset, it.at.toEpochMilli(), it.anchor.toEpochMilli()) } },
    restore = { saved ->
        (saved as List<*>).let { Landing(it[0] as Float, Instant.ofEpochMilli(it[1] as Long), Instant.ofEpochMilli(it[2] as Long)) }
    },
)
