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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
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
@Composable
internal fun OnboardingFrame(
    searchProgress: () -> Float,
    keyboard: Density.() -> Int,
    topBar: @Composable () -> Unit,
    disclaimer: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    steps: @Composable (topBarHeight: Int, disclaimerHeight: Int) -> Unit,
    modifier: Modifier = Modifier,
    disclaimerOverlap: Dp = 0.dp,
) {
    SubcomposeLayout(modifier.clipToBounds()) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = Constraints(minWidth = 0, maxWidth = width, minHeight = 0, maxHeight = height)
        val full = loose.copy(minWidth = width)
        val bar = subcompose(FrameSlot.TopBar, topBar).map { it.measure(full) }
        val barHeight = bar.maxOfOrNull { it.height } ?: 0
        val note = subcompose(FrameSlot.Disclaimer, disclaimer).map { it.measure(loose) }
        val noteOverlap = disclaimerOverlap.roundToPx()
        val noteHeight = ((note.maxOfOrNull { it.height } ?: 0) - noteOverlap).coerceAtLeast(0)
        val bottom = subcompose(FrameSlot.BottomBar, bottomBar).map { it.measure(full) }
        val bottomHeight = bottom.maxOfOrNull { it.height } ?: 0
        val shownBottom = (bottomHeight * (1f - searchProgress().coerceIn(0f, 1f))).roundToInt()
        val stepsHeight = (height - shownBottom).coerceAtLeast(0)
        val content = subcompose(FrameSlot.Steps) { steps(barHeight, noteHeight) }
            .map { it.measure(Constraints.fixed(width, stepsHeight)) }
        layout(width, height) {
            bar.forEach { it.place(0, 0) }
            note.forEach { it.place((width - it.width) / 2, stepsHeight - it.height + noteOverlap) }
            content.forEach { it.place(0, 0) }
            bottom.forEach { it.place(0, stepsHeight - keyboard()) }
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
 * Step N of 5, the wavy progress line and Skip. Always composed and measured (the steps reserve its height), shown
 * through [labelAlpha], [progressAlpha] and [skipAlpha] (scaled by [barAlpha]), read in the draw phase. With [slide]
 * on, each item sits 12 dp above its place until its drop progress ([labelDrop], [progressDrop], [skipDrop]; by
 * default its alpha) reaches 1, so entering from Welcome they drop in, staggered. Under reduced motion [slide] is off
 * and they only fade. While hidden ([visible] false: Welcome, the home
 * search, the spectacle) it is silent for TalkBack and Skip is off. The wave keeps moving while [waveMoving], which
 * stays true through the fade out toward Welcome (a speed change mid-fade makes the wave jump).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OnboardingTopBar(
    step: OnboardingStep,
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
    modifier: Modifier = Modifier,
) {
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    val target = (step.index + 1f) / OnboardingStep.Count
    val progress by animateFloatAsState(target, motion.dataSpatial(), label = "onboardingProgress")
    val label = stringResource(R.string.onboarding_progress, step.index + 1, OnboardingStep.Count)
    Row(
        modifier
            .fillMaxWidth()
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics { })
            .padding(start = 24.dp, end = 8.dp, top = 8.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.topBarItem(labelAlpha, barAlpha, slide, drop = labelDrop),
            )
            Spacer(Modifier.size(6.dp))
            // "Wavy = jet-lagged, flat = adapted": the line calms down as setup completes.
            LinearWavyProgressIndicator(
                progress = { progress },
                amplitude = { p -> (1f - p).coerceIn(0f, 1f) },
                wavelength = 28.dp,
                waveSpeed = if (reduce || !waveMoving) 0.dp else 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .topBarItem(progressAlpha, barAlpha, slide, drop = progressDrop)
                    .semantics { contentDescription = label }
                    .testTag(OnboardingTags.Progress),
            )
        }
        Spacer(Modifier.width(8.dp))
        val skippable = step != OnboardingStep.Reminders
        TextButton(
            onClick = onSkip,
            enabled = visible && skippable,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(OnboardingTags.Skip)
                .topBarItem(skipAlpha, barAlpha, slide, enabled = skippable, drop = skipDrop),
        ) { Text(stringResource(R.string.onboarding_skip)) }
    }
}

private fun Modifier.topBarItem(
    itemAlpha: () -> Float,
    barAlpha: () -> Float,
    slide: Boolean,
    enabled: Boolean = true,
    drop: () -> Float = itemAlpha,
): Modifier = graphicsLayer {
    val p = itemAlpha().coerceIn(0f, 1f)
    alpha = if (enabled) p * barAlpha() else 0f
    translationY = if (slide) -(1f - drop().coerceIn(0f, 1f)) * TopBarDrop.toPx() else 0f
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
    state: OnboardingUiState,
    actions: OnboardingActions,
    wide: Boolean,
    short: Boolean,
    welcome: () -> Float,
    welcomeChrome: () -> Float,
    chrome: () -> Float,
    modifier: Modifier = Modifier,
) {
    val step = state.step
    val onWelcome = step == OnboardingStep.Welcome
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.titleMedium
    val getStarted = stringResource(R.string.welcome_get_started)
    val next = stringResource(R.string.onboarding_next)
    val padding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
    Layout(
        content = {
            OnboardingBackButton(
                onBack = actions::back,
                // Hidden behind Get started on Welcome: off for touch, keyboard focus and TalkBack.
                enabled = !onWelcome,
                modifier = Modifier
                    .layoutId(BarSlot.Back)
                    .graphicsLayer { alpha = chrome() }
                    .then(if (onWelcome) Modifier.semantics { hideFromAccessibility() } else Modifier),
            )
            if (step == OnboardingStep.Reminders) {
                Box(Modifier.layoutId(BarSlot.Primary)) { RemindersPrimary(state, actions) }
            } else {
                Button(
                    onClick = actions::next,
                    contentPadding = padding,
                    modifier = Modifier
                        .layoutId(BarSlot.Primary)
                        .heightIn(min = ButtonDefaults.MediumContainerHeight)
                        .testTag(if (onWelcome) OnboardingTags.GetStarted else OnboardingTags.Next),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MorphLabel(getStarted, visible = onWelcome, alpha = welcomeChrome)
                        MorphLabel(next, visible = !onWelcome, alpha = chrome)
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = bottomBarVerticalPadding(short)),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = BarGap.roundToPx()
        val backMeasurable = measurables.first { it.layoutId == BarSlot.Back }
        val backWidth = backMeasurable.maxIntrinsicWidth(constraints.maxHeight)
        val primaryMeasurable = measurables.first { it.layoutId == BarSlot.Primary }
        val primary: Placeable
        val primaryX: Int
        // The width the primary ends up with at rest: Back gets the rest of the row.
        val primarySlot: Int
        val f = welcome().coerceIn(0f, 1f)
        if (step == OnboardingStep.Reminders) {
            primary = primaryMeasurable.measure(loose.copy(maxWidth = (width - backWidth - gap).coerceAtLeast(width / 2)))
            primaryX = width - primary.width
            primarySlot = primary.width
        } else {
            val chromeWidth = (padding.horizontal(layoutDirection)).roundToPx()
            val nextLabelWidth = measurer.measure(next, labelStyle).size.width + chromeWidth
            // Next's minimum width is the first thing to give when Back needs the room.
            val nextWidth = maxOf(NextMinWidth.roundToPx(), nextLabelWidth)
                .coerceAtMost(maxOf(nextLabelWidth, width - backWidth - gap))
                .coerceAtMost(width)
            val startedWidth = if (wide) {
                maxOf(GetStartedMinWideWidth.roundToPx(), measurer.measure(getStarted, labelStyle).size.width + chromeWidth).coerceAtMost(width)
            } else {
                width
            }
            val primaryWidth = lerp(nextWidth.toFloat(), startedWidth.toFloat(), f).roundToInt()
            primary = primaryMeasurable.measure(
                Constraints(minWidth = primaryWidth, maxWidth = primaryWidth, minHeight = 0, maxHeight = constraints.maxHeight),
            )
            primaryX = lerp((width - nextWidth).toFloat(), ((width - startedWidth) / 2).toFloat(), f).roundToInt()
            primarySlot = nextWidth
        }
        // Back gets what Next leaves. The same height on every step: Back is always measured, even while it hides
        // behind Get started.
        val back = backMeasurable.measure(loose.copy(maxWidth = (width - primarySlot - gap).coerceAtLeast(0)))
        val height = maxOf(back.height, primary.height)
        val backX = if (step == OnboardingStep.Reminders) 0 else lerp(0f, (-gap - back.width).toFloat(), f).roundToInt()
        layout(width, height) {
            back.placeRelative(backX, (height - back.height) / 2)
            primary.placeRelative(primaryX, (height - primary.height) / 2)
        }
    }
}

private enum class BarSlot { Back, Primary }

private fun PaddingValues.horizontal(layoutDirection: LayoutDirection) =
    calculateLeftPadding(layoutDirection) + calculateRightPadding(layoutDirection)

/** One of the morphing button's two labels; the one not in use is silent for TalkBack. */
@Composable
private fun MorphLabel(text: String, visible: Boolean, alpha: () -> Float) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha() }
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics { }),
    )
}

/** The Reminders step's primary: Maybe later until notifications have been asked, then Finish. */
@Composable
private fun RemindersPrimary(state: OnboardingUiState, actions: OnboardingActions) {
    // The permission is asked from the card (one Allow, not two). Until it has been asked, the step's secondary
    // action finishes without it; once handled, a normal Finish.
    if (state.remindersHandled) {
        Button(
            onClick = actions::finish,
            enabled = !state.isSaving,
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            modifier = Modifier
                .heightIn(min = ButtonDefaults.MediumContainerHeight)
                .widthIn(min = NextMinWidth)
                .testTag(OnboardingTags.Finish),
        ) { Text(stringResource(R.string.onboarding_finish), style = MaterialTheme.typography.titleMedium) }
    } else {
        TextButton(
            onClick = actions::finish,
            enabled = !state.isSaving,
            modifier = Modifier.heightIn(min = 56.dp).testTag(OnboardingTags.MaybeLater),
        ) { Text(stringResource(R.string.reminders_maybe_later), style = MaterialTheme.typography.titleMedium) }
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
