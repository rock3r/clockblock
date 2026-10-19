package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import kotlin.math.roundToInt

/**
 * Onboarding's frame: the top bar, the Welcome disclaimer and the bottom bar around the steps, laid out so that no
 * step ever moves because of them (the Welcome → \"Where's home?\" jump).
 *
 * - The steps always get the same height: everything above the bottom bar, whose height is the same on every step.
 * - The top bar and the disclaimer don't take space from the steps' area; each step reserves its own room for them
 *   instead ([steps] receives both heights): Welcome leaves room for the disclaimer at its bottom, the other steps
 *   for the top bar at their top. A step's room never changes while it's on screen, so the bar fading in or out
 *   (with the step transition, alpha only) can't shove anything.
 * - The disclaimer sits [disclaimerOverlap] into the bottom bar's top padding, right above Get started.
 * - [searchProgress] (0 → 1, read in layout) folds the bottom bar away for the home step's full-height search.
 * - [keyboard] (its height over the navigation bar, read in layout) lifts the bottom bar over the keyboard. The
 *   steps keep their height (the keyboard only shrinks their scrolling viewport), so it never recomposes them.
 *
 * The bars are drawn under the steps: a hidden bar sits behind the step that covers it and never takes a touch.
 */
@Stable
internal class OnboardingFrameRoom {
    var topBarHeight: Int = 0
    var disclaimerHeight: Int = 0
    var bottomBarHeight: Int = 0
}

@Composable
internal fun OnboardingFrame(
    room: OnboardingFrameRoom,
    searchProgress: () -> Float,
    keyboard: Density.() -> Int,
    topBar: @Composable () -> Unit,
    disclaimer: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    steps: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    disclaimerOverlap: Dp = 0.dp,
) {
    Layout(
        content = {
            Box(Modifier.layoutId(FrameSlot.TopBar)) { topBar() }
            Box(Modifier.layoutId(FrameSlot.Disclaimer)) { disclaimer() }
            Box(Modifier.layoutId(FrameSlot.BottomBar)) { bottomBar() }
            Box(Modifier.layoutId(FrameSlot.Steps)) { steps() }
        },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = Constraints(minWidth = 0, maxWidth = width, minHeight = 0, maxHeight = height)
        val full = loose.copy(minWidth = width)
        var topBarPlaceable: Placeable? = null
        var disclaimerPlaceable: Placeable? = null
        var bottomBarPlaceable: Placeable? = null
        var stepsMeasurable: Measurable? = null
        for (i in measurables.indices) {
            val m = measurables[i]
            when (m.layoutId as FrameSlot) {
                FrameSlot.TopBar -> topBarPlaceable = m.measure(full)
                FrameSlot.Disclaimer -> disclaimerPlaceable = m.measure(loose)
                FrameSlot.BottomBar -> bottomBarPlaceable = m.measure(full)
                FrameSlot.Steps -> stepsMeasurable = m
            }
        }
        val barHeight = topBarPlaceable?.height ?: 0
        val noteOverlap = disclaimerOverlap.roundToPx()
        val noteHeight = ((disclaimerPlaceable?.height ?: 0) - noteOverlap).coerceAtLeast(0)
        val bottomHeight = bottomBarPlaceable?.height ?: 0
        room.topBarHeight = barHeight
        if (noteHeight > 0) room.disclaimerHeight = noteHeight
        room.bottomBarHeight = bottomHeight
        val stepsPlaceable = stepsMeasurable?.measure(Constraints.fixed(width, height))
        layout(width, height) {
            val shownBottom = (bottomHeight * (1f - searchProgress().coerceIn(0f, 1f))).roundToInt()
            val stepsHeight = (height - shownBottom).coerceAtLeast(0)
            topBarPlaceable?.place(0, 0)
            disclaimerPlaceable?.let { if (it.height > 0) it.place((width - it.width) / 2, stepsHeight - it.height + noteOverlap) }
            stepsPlaceable?.place(0, 0)
            bottomBarPlaceable?.place(0, stepsHeight - keyboard())
        }
    }
}

private enum class FrameSlot { TopBar, Disclaimer, BottomBar, Steps }

/**
 * The step the top bar displays: never [OnboardingStep.Welcome] (which has no bar), so while fading out toward
 * Welcome it keeps the outgoing step's label ("Step 2 of 5") and progress instead of jumping to Welcome's.
 */
internal fun topBarStep(step: OnboardingStep, current: OnboardingStep = step): OnboardingStep = when {
    step != OnboardingStep.Welcome -> step
    current != OnboardingStep.Welcome -> current
    else -> OnboardingStep.HomeZone
}

/**
 * Step N of 5, the wavy progress line ([progress], read in draw) and Skip. Always composed and measured (the steps reserve its height), shown
 * through [labelAlpha], [progressAlpha] and [skipAlpha] (scaled by [barAlpha]), read in the draw phase. With [slide]
 * on, each item sits 12 dp above its place until its drop progress ([labelDrop], [progressDrop], [skipDrop]; by
 * default its alpha) reaches 1, so entering from Welcome they drop in, staggered. Under reduced motion [slide] is off
 * and they only fade. While hidden ([visible] false: Welcome, the home
 * search, the spectacle) it is silent for TalkBack and Skip is off. The wave keeps moving while [waveMoving], which
 * stays true through the fade out toward Welcome (a speed change mid-fade makes the wave jump).
 *
 * On the last step there is nothing left to skip: [lastStep] (0 → 1, read in layout and draw) slides Skip out to the
 * end while it fades ([skipLeaves]), and its room goes to the progress line, now the runway for a little plane that
 * taxis to its holding point a third of the way along ([plane]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OnboardingTopBar(
    stepState: State<OnboardingStep>,
    progress: () -> Float,
    visible: Boolean,
    slide: Boolean,
    barAlpha: () -> Float,
    labelAlpha: () -> Float,
    progressAlpha: () -> Float,
    skipAlpha: () -> Float,
    onSkip: () -> Unit,
    waveMoving: Boolean = visible,
    labelDrop: () -> Float = labelAlpha,
    progressDrop: () -> Float = progressAlpha,
    skipDrop: () -> Float = skipAlpha,
    lastStep: () -> Float = { 0f },
    skipLeaves: () -> Float = lastStep,
    plane: RunwayPlanePose? = null,
    modifier: Modifier = Modifier,
) {
    val reduce = ClockblockTheme.reduceMotion
    val initialStep = remember { Snapshot.withoutReadObservation { stepState.value } }
    val labels = Array(OnboardingStep.Count) { index ->
        stringResource(R.string.onboarding_progress, index + 1, OnboardingStep.Count)
    }
    val progressSemantics = remember {
        StepSemanticsHolder(
            initialTag = OnboardingTags.Progress,
            initialActive = true,
            initialContentDescription = labels[initialStep.index],
        )
    }
    val labelSemantics = remember {
        Array(OnboardingStep.Count) { index ->
            StepSemanticsHolder(initialActive = index == initialStep.index)
        }
    }
    val skipSemantics = remember {
        StepSemanticsHolder(
            initialTag = OnboardingTags.Skip,
            initialActive = initialStep != OnboardingStep.Reminders,
            keepTagWhenInactive = true,
        )
    }
    BindTopBarStepSemantics(
        stepState = stepState,
        labels = labels,
        progressSemantics = progressSemantics,
        labelSemantics = labelSemantics,
        skipSemantics = skipSemantics,
    )
    val rowSemantics = remember { StepSemanticsHolder(initialActive = visible) }
    SideEffect { rowSemantics.update(newActive = visible) }
    val rowModifier = remember(modifier, rowSemantics) {
        modifier
            .fillMaxWidth()
            .then(StepSemanticsElement(rowSemantics))
            .padding(start = 24.dp, end = 8.dp, top = 8.dp)
            .heightIn(min = 56.dp)
    }
    val takeoff = LocalTakeoff.current
    val labelBoxModifier = remember(labelAlpha, barAlpha, slide, labelDrop, takeoff) {
        Modifier
            .wrapContentWidth(Alignment.Start, unbounded = true)
            .topBarItem(labelAlpha, barAlpha, slide, drop = labelDrop, takeoff = takeoff)
    }
    val progressBaseModifier = remember(progressAlpha, barAlpha, slide, progressDrop) {
        Modifier
            .fillMaxWidth()
            .topBarItem(progressAlpha, barAlpha, slide, drop = progressDrop)
    }
    val progressWithPlane = if (plane != null) progressBaseModifier.runwayPlane(plane) else progressBaseModifier
    val progressWithTakeoff = progressWithPlane.takeoffExit(Takeoff.TopBar)
    val progressModifier = remember(progressWithTakeoff, progressSemantics) {
        progressWithTakeoff.then(StepSemanticsElement(progressSemantics))
    }
    Row(
        rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Box(labelBoxModifier) {
                for (index in 0 until OnboardingStep.Count) {
                    val itemAlpha = remember(stepState, index) {
                        { if (stepState.value.index == index) 1f else 0f }
                    }
                    MorphLabel(
                        text = labels[index],
                        semanticsHolder = labelSemantics[index],
                        alpha = itemAlpha,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(6.dp))
            // "Wavy = jet-lagged, flat = adapted": the line calms down as setup completes.
            LinearWavyProgressIndicator(
                progress = progress,
                amplitude = WavyAmplitude,
                wavelength = 28.dp,
                waveSpeed = if (reduce || !waveMoving) 0.dp else 14.dp,
                modifier = progressModifier,
            )
        }
        val skipItemAlpha = remember(skipAlpha, skipLeaves) {
            { skipAlpha() * (1f - skipLeaves().coerceIn(0f, 1f)) }
        }
        val skipModifier = remember(lastStep, skipItemAlpha, barAlpha, slide, skipDrop, stepState, skipSemantics) {
            Modifier
                .skipSlot(lastStep)
                .heightIn(min = 48.dp)
                .topBarItem(skipItemAlpha, barAlpha, slide, drop = skipDrop)
                // Nothing to skip on the last step: still drawn while it leaves, but no longer a control.
                .then(StepSemanticsElement(skipSemantics))
                .focusProperties { canFocus = stepState.value != OnboardingStep.Reminders }
                .pointerInput(stepState) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (stepState.value == OnboardingStep.Reminders) event.changes.forEach { it.consume() }
                        }
                    }
                }
        }
        TextButton(
            onClick = onSkip,
            enabled = visible,
            modifier = skipModifier,
        ) { Text(stringResource(R.string.onboarding_skip)) }
    }
}

@Composable
private fun BindTopBarStepSemantics(
    stepState: State<OnboardingStep>,
    labels: Array<String>,
    progressSemantics: StepSemanticsHolder,
    labelSemantics: Array<StepSemanticsHolder>,
    skipSemantics: StepSemanticsHolder,
) {
    val step = stepState.value
    val activeIndex = step.index
    val label = labels[activeIndex]
    val skippable = step != OnboardingStep.Reminders
    SideEffect {
        progressSemantics.update(newTag = OnboardingTags.Progress, newActive = true, newContentDescription = label)
        for (i in labelSemantics.indices) {
            labelSemantics[i].setActive(i == activeIndex)
        }
        skipSemantics.update(newTag = OnboardingTags.Skip, newActive = skippable)
    }
}

private val WavyAmplitude: (Float) -> Float = { p -> (1f - p).coerceIn(0f, 1f) }

/**
 * Skip's room in the top bar: its own width plus the gap before it, shrinking as [leaving] goes 0 → 1 (read in
 * layout) to just the end inset that mirrors the line's start, while Skip itself keeps its size and slides out past
 * the end. The progress line beside it grows into the room it leaves.
 */
private fun Modifier.skipSlot(leaving: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0))
    val gap = SkipGap.roundToPx()
    val p = leaving().coerceIn(0f, 1f)
    val room = placeable.width + gap
    val width = lerp(room.toFloat(), LineEndInset.toPx(), p).roundToInt()
    layout(width, placeable.height) { placeable.placeRelative(gap + (room * p).roundToInt(), 0) }
}

private val SkipGap = 8.dp

/** With Skip gone, the line ends this far before the bar's own end padding: 24 dp from the edge, like its start. */
private val LineEndInset = 16.dp

/** Drawn, but not a control: takes no taps and no focus, and TalkBack doesn't see it. */
private fun Modifier.inertChrome(): Modifier = this
    .clearAndSetSemantics { }
    .focusProperties { canFocus = false }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

/**
 * Where the runway plane is: [taxi] its way from the start of the line to its holding point a third of the way along
 * (0 → 1), and [alpha] its opacity, both read in draw.
 */
@Stable
internal class RunwayPlanePose(val taxi: () -> Float, val alpha: () -> Float, val takeoff: () -> Float = { 0f }) {
    /**
     * The whole runway's width once Skip has left: the line shrinks back while the plane fades out on Back, and the
     * holding point stays a third of the way along the runway it parked on instead of drifting with it.
     */
    internal var runwayWidth: Float = 0f
}

/** The plane on the progress line, wheels on the line, taking off towards the end (mirrored in RTL). */
@Composable
private fun Modifier.runwayPlane(pose: RunwayPlanePose): Modifier {
    val color = MaterialTheme.colorScheme.primary
    val wing = MaterialTheme.colorScheme.tertiary
    return remember(this, pose, color, wing) {
        this.drawWithContent {
            drawContent()
            val alpha = pose.alpha().coerceIn(0f, 1f)
            if (alpha <= 0f) {
                pose.runwayWidth = 0f
                return@drawWithContent
            }
            val length = RunwayPlaneLength.toPx()
            val rtl = layoutDirection == LayoutDirection.Rtl
            // The widest the runway has been since the plane appeared (a new window size makes a new pose).
            pose.runwayWidth = maxOf(pose.runwayWidth, size.width)
            // From the start of the line to its holding point: the plane's middle a third of the way along the runway.
            val travel = pose.runwayWidth * HoldingPoint - length / 2f
            // Then, on finishing, it takes off: rolls on past the end, rotates and climbs away.
            val t = pose.takeoff().coerceIn(0f, 1f)
            val fromStart = travel * pose.taxi().coerceIn(0f, 1f) + Takeoff.roll(t) * (pose.runwayWidth - length - travel)
            val ground = size.height / 2f - RunwayStroke.toPx() / 2f + RunwayGroundNudge.toPx() - Takeoff.climb(t) * Takeoff.ClimbHeight.toPx()
            val tailX = if (rtl) size.width - fromStart else fromStart
            with(RunwayPlane) {
                drawRunwayPlane(
                    Offset(tailX, ground), length, color, alpha * Takeoff.alpha(t),
                    pitchDegrees = Takeoff.pitch(t), mirrored = rtl, wingColor = wing,
                )
            }
        }
    }
}

private val RunwayPlaneLength = 34.dp
private val RunwayStroke = 4.dp

/** The flat line sits a touch below the indicator's middle: wheels on the line, not hovering over it. */
private val RunwayGroundNudge = 2.dp
private const val HoldingPoint = 1f / 3f

private fun Modifier.topBarItem(
    itemAlpha: () -> Float,
    barAlpha: () -> Float,
    slide: Boolean,
    enabled: Boolean = true,
    drop: () -> Float = itemAlpha,
    takeoff: (() -> Float)? = null,
): Modifier = graphicsLayer {
    val p = itemAlpha().coerceIn(0f, 1f)
    val e = if (takeoff != null) Takeoff.exit(Takeoff.TopBar, takeoff()) else 0f
    alpha = if (enabled) p * barAlpha() * (1f - e) else 0f
    val dropY = if (slide) -(1f - drop().coerceIn(0f, 1f)) * TopBarDrop.toPx() else 0f
    val takeoffY = if (e > 0f) -lerp(0f, Takeoff.Rise.toPx(), e) else 0f
    translationY = dropY + takeoffY
}

private val TopBarDrop = 12.dp

/**
 * Back and the primary action, in one row whose height never changes between steps.
 *
 * Welcome ↔ the first question is a container transform of the same button: the full-width Get started shrinks into
 * the Next pill (bounds on [welcome], the step transition's spatial value: 1 on Welcome, 0 elsewhere, seeked by
 * predictive back) while the labels fade through (Get started out first on [welcomeChrome], then Next in on [chrome],
 * so they never double-expose), and Back slides out from behind its leading edge and fades in with Next's label.
 * All are read in layout and draw. Reduce motion: the bounds snap with the step and only the fades remain.
 *
 * Back and the primary never overlap: on a narrow window or at a large font Next gives up its minimum width first,
 * then Back gets what is left (and wraps).
 */
@Composable
internal fun OnboardingBottomBar(
    onWelcome: Boolean,
    onRemindersState: State<Boolean>,
    remindersHandledState: State<Boolean>,
    actions: OnboardingActions,
    wide: Boolean,
    short: Boolean,
    welcome: () -> Float,
    welcomeChrome: () -> Float,
    chrome: () -> Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.titleMedium
    val getStarted = stringResource(R.string.welcome_get_started)
    val next = stringResource(R.string.onboarding_next)
    val nextMeasuredWidth = remember(measurer, next, labelStyle) { measurer.measure(next, labelStyle).size.width }
    val getStartedMeasuredWidth = remember(measurer, getStarted, labelStyle) { measurer.measure(getStarted, labelStyle).size.width }
    val padding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
    val initialOnReminders = remember { Snapshot.withoutReadObservation { onRemindersState.value } }
    val initialHandled = remember { Snapshot.withoutReadObservation { remindersHandledState.value } }
    val primaryTag = if (onWelcome) OnboardingTags.GetStarted else OnboardingTags.Next
    val primarySemantics = remember { StepSemanticsHolder(initialTag = primaryTag, initialActive = !initialOnReminders) }
    val finishSemantics = remember { StepSemanticsHolder(initialTag = OnboardingTags.Finish, initialActive = initialOnReminders && initialHandled) }
    val maybeLaterSemantics = remember { StepSemanticsHolder(initialTag = OnboardingTags.MaybeLater, initialActive = initialOnReminders && !initialHandled) }
    val getStartedSemantics = remember { StepSemanticsHolder(initialActive = onWelcome) }
    val nextSemantics = remember { StepSemanticsHolder(initialActive = !onWelcome) }
    BindBottomBarSemantics(
        onWelcome = onWelcome,
        onRemindersState = onRemindersState,
        remindersHandledState = remindersHandledState,
        primarySemantics = primarySemantics,
        finishSemantics = finishSemantics,
        maybeLaterSemantics = maybeLaterSemantics,
        getStartedSemantics = getStartedSemantics,
        nextSemantics = nextSemantics,
    )
    val backModifier = remember(chrome, onWelcome) {
        Modifier
            .layoutId(BarSlot.Back)
            .graphicsLayer { alpha = chrome() }
            .then(if (onWelcome) Modifier.semantics { hideFromAccessibility() } else Modifier)
    }
    val primaryModifier = remember(primarySemantics, onRemindersState) {
        Modifier
            .layoutId(BarSlot.Primary)
            .heightIn(min = ButtonDefaults.MediumContainerHeight)
            .focusProperties { canFocus = !onRemindersState.value }
            .then(StepSemanticsElement(primarySemantics))
    }
    val finishModifier = remember(finishSemantics, onRemindersState, remindersHandledState) {
        Modifier
            .layoutId(BarSlot.Finish)
            .heightIn(min = ButtonDefaults.MediumContainerHeight)
            .widthIn(min = NextMinWidth)
            .focusProperties { canFocus = onRemindersState.value && remindersHandledState.value }
            .then(StepSemanticsElement(finishSemantics))
    }
    val maybeLaterModifier = remember(maybeLaterSemantics, onRemindersState, remindersHandledState) {
        Modifier
            .layoutId(BarSlot.MaybeLater)
            .heightIn(min = 56.dp)
            .focusProperties { canFocus = onRemindersState.value && !remindersHandledState.value }
            .then(StepSemanticsElement(maybeLaterSemantics))
    }
    val barModifier = remember(modifier, short) {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = bottomBarVerticalPadding(short))
    }
    Layout(
        content = {
            OnboardingBackButton(
                onBack = actions::back,
                // Hidden behind Get started on Welcome: off for touch, keyboard focus and TalkBack.
                enabled = !onWelcome,
                modifier = backModifier,
            )
            Button(
                onClick = actions::next,
                contentPadding = padding,
                modifier = primaryModifier,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MorphLabel(getStarted, semanticsHolder = getStartedSemantics, alpha = welcomeChrome)
                    MorphLabel(next, semanticsHolder = nextSemantics, alpha = chrome)
                }
            }
            // The permission is asked from the card (one Allow, not two). Until it has been asked, the step's secondary
            // action finishes without it; once handled, a normal Finish.
            Button(
                onClick = actions::finish,
                contentPadding = padding,
                modifier = finishModifier,
            ) { Text(stringResource(R.string.onboarding_finish), style = labelStyle) }
            TextButton(
                onClick = actions::finish,
                modifier = maybeLaterModifier,
            ) { Text(stringResource(R.string.reminders_maybe_later), style = labelStyle) }
        },
        modifier = barModifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = BarGap.roundToPx()
        var backMeasurable: Measurable? = null
        var primaryMeasurable: Measurable? = null
        var finishMeasurable: Measurable? = null
        var maybeLaterMeasurable: Measurable? = null
        for (i in measurables.indices) {
            val m = measurables[i]
            when (m.layoutId as BarSlot) {
                BarSlot.Back -> backMeasurable = m
                BarSlot.Primary -> primaryMeasurable = m
                BarSlot.Finish -> finishMeasurable = m
                BarSlot.MaybeLater -> maybeLaterMeasurable = m
            }
        }
        val backM = requireNotNull(backMeasurable)
        val primaryM = requireNotNull(primaryMeasurable)
        val finishM = requireNotNull(finishMeasurable)
        val maybeLaterM = requireNotNull(maybeLaterMeasurable)
        val backWidth = backM.maxIntrinsicWidth(constraints.maxHeight)
        val remindersConstraints = loose.copy(maxWidth = (width - backWidth - gap).coerceAtLeast(width / 2))
        val finishPlaceable = finishM.measure(remindersConstraints)
        val maybeLaterPlaceable = maybeLaterM.measure(remindersConstraints)
        val f = welcome().coerceIn(0f, 1f)
        val chromeWidth = (padding.horizontal(layoutDirection)).roundToPx()
        val nextLabelWidth = nextMeasuredWidth + chromeWidth
        // Next's minimum width is the first thing to give when Back needs the room.
        val nextWidth = maxOf(NextMinWidth.roundToPx(), nextLabelWidth)
            .coerceAtMost(maxOf(nextLabelWidth, width - backWidth - gap))
            .coerceAtMost(width)
        val startedWidth = if (wide) {
            maxOf(GetStartedMinWideWidth.roundToPx(), getStartedMeasuredWidth + chromeWidth).coerceAtMost(width)
        } else {
            width
        }
        val primaryWidth = lerp(nextWidth.toFloat(), startedWidth.toFloat(), f).roundToInt()
        val nextPlaceable = primaryM.measure(
            Constraints(minWidth = primaryWidth, maxWidth = primaryWidth, minHeight = 0, maxHeight = constraints.maxHeight),
        )
        val isReminders = onRemindersState.value
        val isHandled = remindersHandledState.value
        val activePrimary: Placeable
        val primaryX: Int
        // The width the primary ends up with at rest: Back gets the rest of the row.
        val primarySlot: Int
        if (isReminders) {
            activePrimary = if (isHandled) finishPlaceable else maybeLaterPlaceable
            primaryX = width - activePrimary.width
            primarySlot = activePrimary.width
        } else {
            activePrimary = nextPlaceable
            primaryX = lerp((width - nextWidth).toFloat(), ((width - startedWidth) / 2).toFloat(), f).roundToInt()
            primarySlot = nextWidth
        }
        // Back gets what Next leaves. The same height on every step: Back is always measured, even while it hides
        // behind Get started.
        val backMaxWidth = minOf(backWidth, (width - primarySlot - gap).coerceAtLeast(0))
        val back = backM.measure(loose.copy(maxWidth = backMaxWidth))
        val height = maxOf(back.height, activePrimary.height)
        val backX = if (isReminders) 0 else lerp(0f, (-gap - back.width).toFloat(), f).roundToInt()
        val offscreenX = width * 2
        layout(width, height) {
            back.placeRelative(backX, (height - back.height) / 2)
            if (!isReminders) {
                nextPlaceable.placeRelativeWithLayer(primaryX, (height - nextPlaceable.height) / 2) { alpha = 1f }
            } else {
                nextPlaceable.placeWithLayer(offscreenX, 0, zIndex = -1f) { alpha = 0f }
            }
            if (isReminders && isHandled) {
                finishPlaceable.placeRelativeWithLayer(primaryX, (height - finishPlaceable.height) / 2) { alpha = 1f }
            } else {
                finishPlaceable.placeWithLayer(offscreenX, 0, zIndex = -1f) { alpha = 0f }
            }
            if (isReminders && !isHandled) {
                maybeLaterPlaceable.placeRelativeWithLayer(primaryX, (height - maybeLaterPlaceable.height) / 2) { alpha = 1f }
            } else {
                maybeLaterPlaceable.placeWithLayer(offscreenX, 0, zIndex = -1f) { alpha = 0f }
            }
        }
    }
}

@Composable
private fun BindBottomBarSemantics(
    onWelcome: Boolean,
    onRemindersState: State<Boolean>,
    remindersHandledState: State<Boolean>,
    primarySemantics: StepSemanticsHolder,
    finishSemantics: StepSemanticsHolder,
    maybeLaterSemantics: StepSemanticsHolder,
    getStartedSemantics: StepSemanticsHolder,
    nextSemantics: StepSemanticsHolder,
) {
    val onReminders = onRemindersState.value
    val remindersHandled = remindersHandledState.value
    val primaryTag = if (onWelcome) OnboardingTags.GetStarted else OnboardingTags.Next
    SideEffect {
        primarySemantics.update(newTag = primaryTag, newActive = !onReminders)
        finishSemantics.update(newTag = OnboardingTags.Finish, newActive = onReminders && remindersHandled)
        maybeLaterSemantics.update(newTag = OnboardingTags.MaybeLater, newActive = onReminders && !remindersHandled)
        getStartedSemantics.setActive(onWelcome)
        nextSemantics.setActive(!onWelcome)
    }
}

private enum class BarSlot { Back, Primary, Finish, MaybeLater }

private fun PaddingValues.horizontal(layoutDirection: LayoutDirection) =
    calculateLeftPadding(layoutDirection) + calculateRightPadding(layoutDirection)

/** One of the morphing button's two labels; the one not in use is silent for TalkBack. */
@Composable
private fun MorphLabel(
    text: String,
    semanticsHolder: StepSemanticsHolder,
    alpha: () -> Float,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    val modifier = remember(alpha, semanticsHolder) {
        Modifier
            .graphicsLayer { this.alpha = alpha() }
            .then(StepSemanticsElement(semanticsHolder))
    }
    Text(
        text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier,
    )
}

@Composable
internal fun SyncStepSemantics(activeState: State<Boolean>, holder: StepSemanticsHolder) {
    val active = activeState.value
    SideEffect { holder.setActive(active) }
}

/**
 * When [inert] is true, clears descendant semantics (via [semanticsHolder], whose `active` is `!inert()`), disables
 * focus, and consumes pointer events in the initial pass. Stays attached so toggling never attaches/detaches nodes.
 */
internal fun Modifier.inertWhen(
    semanticsHolder: StepSemanticsHolder,
    inert: () -> Boolean,
): Modifier = this
    .then(StepSemanticsElement(semanticsHolder))
    .focusProperties { if (inert()) canFocus = false }
    .pointerInput(inert) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (inert()) event.changes.forEach { it.consume() }
            }
        }
    }

@Stable
internal class StepSemanticsHolder(
    initialTag: String = "",
    initialActive: Boolean = true,
    initialContentDescription: String? = null,
    val keepTagWhenInactive: Boolean = false,
) {
    var tag: String = initialTag
        private set
    var active: Boolean = initialActive
        private set
    var contentDescription: String? = initialContentDescription
        private set
    var node: StepSemanticsNode? = null

    fun setActive(value: Boolean) {
        if (active != value) {
            active = value
            node?.invalidateSemantics()
        }
    }

    fun update(
        newTag: String = tag,
        newActive: Boolean = active,
        newContentDescription: String? = contentDescription,
    ) {
        if (tag != newTag || active != newActive || contentDescription != newContentDescription) {
            tag = newTag
            active = newActive
            contentDescription = newContentDescription
            node?.invalidateSemantics()
        }
    }
}

internal data class StepSemanticsElement(
    val holder: StepSemanticsHolder,
) : ModifierNodeElement<StepSemanticsNode>() {
    override fun create(): StepSemanticsNode = StepSemanticsNode(holder)
    override fun update(node: StepSemanticsNode) {
        node.holder = holder
        holder.node = node
    }
}

internal class StepSemanticsNode(
    var holder: StepSemanticsHolder,
) : Modifier.Node(), SemanticsModifierNode {
    override val shouldClearDescendantSemantics: Boolean
        get() = !holder.active

    override fun onAttach() {
        holder.node = this
    }

    override fun onDetach() {
        if (holder.node === this) holder.node = null
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        if (holder.active || holder.keepTagWhenInactive) {
            if (holder.tag.isNotEmpty()) {
                testTag = holder.tag
            }
        }
        if (holder.active) {
            holder.contentDescription?.let {
                contentDescription = it
            }
        }
    }
}

/**
 * The outlined Back of the bottom bar (every step after Welcome). Disabled, it keeps its enabled look: it only ever is
 * while hidden behind Get started, and fading out of Welcome's step must not grey it first.
 */
@Composable
private fun OnboardingBackButton(onBack: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val colors = ButtonDefaults.outlinedButtonColors()
    OutlinedButton(
        onClick = onBack,
        enabled = enabled,
        colors = colors.copy(disabledContainerColor = colors.containerColor, disabledContentColor = colors.contentColor),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
        modifier = modifier.heightIn(min = 56.dp).testTag(OnboardingTags.Back),
    ) {
        // Auto-mirrored: it points to the start edge in RTL by itself.
        Icon(painterResource(R.drawable.onboarding_ic_arrow_back), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.onboarding_back))
    }
}

/** The bottom bar's padding above and below its buttons. */
internal fun bottomBarVerticalPadding(short: Boolean): Dp = if (short) 8.dp else 16.dp

/** The least room between Back and the primary action. */
private val BarGap = 8.dp

/** Next (and Finish) is at least this wide, room permitting. */
private val NextMinWidth = 140.dp

/** On wide windows Get started is centred and at least this wide instead of spanning the window. */
private val GetStartedMinWideWidth = 280.dp
