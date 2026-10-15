package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.ChronotypeScene
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.drawChronotypeSceneWarmup
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.skyPose
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.warmChronotypeSceneShapes
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.feature.onboarding.R
import dev.sebastiano.clockblocker.opus.feature.onboarding.rememberTouchExplorationEnabled
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Test tags for the e2e suite. */
object ChronotypeRevealTags {
    const val Name = "chronotype_reveal_name"
    const val Other = "chronotype_reveal_other"
    const val Sheet = "chronotype_sheet"

    /** The illustration card. */
    const val Art = "chronotype_reveal_art"

    /** The reveal while its first-time spectacle plays (gone once it rests). */
    const val Spectacle = "chronotype_reveal_spectacle"
}

/**
 * The chronotype worked out from the ideal night, revealed: a sky at the hour that person's day peaks, the sun (or,
 * for evening types, the moon) rising along its path, then our name and description for the chronotype, what the
 * night is centred on, and a "Doesn't sound like me" link to a sheet with all five.
 *
 * Motion (a rare moment, so Expressive is allowed; MOTION.md), once per visit (saveable, so coming back from Tools
 * shows it at rest), in [RevealEntrance]'s beats:
 * - The first time from the sleep dial ([spectacleFrom]: the dial's inner face, in root coordinates), the spectacle:
 *   a `MaterialShapes` shape grows out of the dial's face, rises to the card spinning through two morphs, and pops
 *   into the card's rounded rectangle, where the illustration fades in. A tap (or TalkBack's labelled click) skips to
 *   the end; a tap on the link once it shows also opens the sheet. [onSpectacleFinished] follows either.
 * - The chronotype's landscape enters layer by layer, each chronotype its own way (`ChronotypeScene`), the sky warming
 *   from three hours earlier; then the words stagger down into place.
 *
 * The sun's rays turn and the stars twinkle on `ambientLoop`. Picking another chronotype moves the sun along the path
 * to its new place. Reduce motion, or TalkBack exploring by touch: everything sits at rest; the words carry the
 * meaning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronotypeReveal(
    chronotype: Chronotype,
    picked: Boolean,
    suggested: Chronotype,
    midSleep: LocalTime,
    onPick: (Chronotype) -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
    daysOffLinked: Boolean = false,
    spectacleFrom: Rect? = null,
    onSpectacleFinished: () -> Unit = {},
) {
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    val touchExploring = rememberTouchExplorationEnabled()
    var played by rememberSaveable { mutableStateOf(false) }
    val entrance = remember {
        RevealEntrance(
            when {
                reduce || touchExploring -> RevealStart.Rest
                spectacleFrom != null -> RevealStart.Spectacle
                played -> RevealStart.Rest
                else -> RevealStart.Entrance
            },
        )
    }
    val currentOnSpectacleFinished by rememberUpdatedState(onSpectacleFinished)
    val scope = rememberCoroutineScope()
    var choreography by remember { mutableStateOf<Job?>(null) }
    // Counted as played as soon as it's shown: leaving mid-entrance (Next during the rise) and coming back, or a
    // rotation, shows the reveal at rest instead of replaying it. Marked as the composition applies, not in the effect
    // below, so even a recreation before the effect's first run can't save it unplayed.
    SideEffect { played = true }
    LaunchedEffect(entrance) {
        choreography = coroutineContext[Job]
        entrance.play(motion)
        if (entrance.start == RevealStart.Spectacle) currentOnSpectacleFinished()
    }
    val toRest: () -> Unit = {
        val wasSpectacle = entrance.spectacleRunning
        choreography?.cancel()
        scope.launch {
            entrance.skip()
            if (wasSpectacle) currentOnSpectacleFinished()
        }
    }
    val skip: () -> Unit = { if (entrance.spectacleRunning) toRest() }
    // Turning on reduce motion or TalkBack mid-entrance lands it at rest too.
    LaunchedEffect(reduce || touchExploring) { if (reduce || touchExploring) toRest() }

    var showSheet by rememberSaveable { mutableStateOf(false) }
    val origin = remember { mutableStateOf(Offset.Zero) }
    val card = remember { mutableStateOf(Rect.Zero) }
    val artBlock: @Composable (Dp) -> Unit = { size ->
        Box(
            Modifier
                .size(size)
                .onGloballyPositioned { card.value = Rect(it.positionInRoot(), it.size.toSize()) }
                .testTag(ChronotypeRevealTags.Art),
        ) {
            ChronotypeRevealArt(
                chronotype,
                entrance = { entrance.art.value },
                drawn = { entrance.artAlpha > 0f },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val scale = entrance.popScale
                    alpha = entrance.artAlpha
                    scaleX = scale
                    scaleY = scale
                    compositingStrategy = if (scale != 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                },
            )
        }
    }
    val words: @Composable ColumnScope.(TextAlign, Alignment.Horizontal, Boolean) -> Unit = { align, horizontal, compact ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = horizontal) {
            RevealWords(chronotype, picked, { entrance.words.value }, midSleep, daysOffLinked, align, compact)
            Spacer(Modifier.size(8.dp))
            TextButton(
                // Until it shows, the link is only part of the spectacle (a keyboard or Switch Access click skips it).
                onClick = { if (entrance.spectacleRunning && !entrance.linkShown) skip() else showSheet = true },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .staggeredWord({ entrance.words.value }, LinkStagger)
                    .testTag(ChronotypeRevealTags.Other),
            ) { Text(stringResource(R.string.chronotype_reveal_other)) }
        }
    }
    val spectacle = Modifier.spectacle(entrance, spectacleFrom, origin, card, chronotype, skip)
    if (wide) BoxWithConstraints(modifier.fillMaxSize().then(spectacle)) {
        val artSize = minOf(260.dp, maxHeight * 0.6f)
        // Phones in landscape: a smaller name, so the link stays in view without scrolling.
        val short = maxHeight < ShortPaneHeight
        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(0.42f), contentAlignment = Alignment.Center) { artBlock(artSize) }
            Spacer(Modifier.width(40.dp))
            Column(Modifier.weight(0.58f).verticalScroll(rememberScrollState()).padding(vertical = if (short) 8.dp else 24.dp)) {
                words(TextAlign.Start, Alignment.Start, short)
            }
        }
    } else {
        Column(
            modifier.fillMaxSize().then(spectacle).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            artBlock(RevealArtSize)
            Spacer(Modifier.size(28.dp))
            words(TextAlign.Center, Alignment.CenterHorizontally, false)
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scenesReady = remember(sheetState) {
            derivedStateOf { sheetState.targetValue != SheetValue.Hidden || sheetState.currentValue != SheetValue.Hidden }
        }
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState, modifier = Modifier.testTag(ChronotypeRevealTags.Sheet)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                Text(
                    stringResource(R.string.chronotype_sheet_title),
                    style = ClockblockTheme.textStyles.editorialTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.size(16.dp))
                ChronotypePicker(
                    selected = chronotype,
                    suggested = suggested,
                    drawScenes = { scenesReady.value },
                    modifier = Modifier.graphicsLayer {
                        val moving = sheetState.isAnimationRunning || sheetState.currentValue != SheetValue.Expanded
                        compositingStrategy = if (moving) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                    },
                    onSelect = { pick ->
                        onPick(pick)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) showSheet = false }
                    },
                )
            }
        }
    }
}

/**
 * The spectacle on the reveal's root, outside its scrolling (so the shape is never clipped): tracks the root's place,
 * draws the shape behind the content, and, while it plays, takes a tap anywhere as a skip (letting it through to the
 * link once the link shows) and offers TalkBack a labelled skip.
 */
@Composable
private fun Modifier.spectacle(
    entrance: RevealEntrance,
    from: Rect?,
    origin: MutableState<Offset>,
    card: MutableState<Rect>,
    chronotype: Chronotype,
    skip: () -> Unit,
): Modifier {
    if (entrance.start != RevealStart.Spectacle) return this
    val startFrom = remember { from }
    val fill = ClockblockTheme.sky.gradientAt(((chronotype.skyPose().hour - EntranceHours) % 24f + 24f) % 24f)
    val rim = ClockblockTheme.artColors.tertiary
    val skipLabel = stringResource(R.string.chronotype_spectacle_skip)
    val path = remember { Path() }
    val running = entrance.spectacleRunning
    return this
        .onGloballyPositioned { origin.value = it.positionInRoot() }
        .drawWithCache {
            val local = card.value.translate(-origin.value)
            val morphs = spectacleMorphs(if (local.isEmpty) 0f else RevealCorner.toPx() / local.width)
            val face = startFrom?.translate(-origin.value) ?: Rect.Zero
            onDrawBehind {
                if (entrance.spectacleRunning && !local.isEmpty) drawSpectacleShape(entrance, morphs, face, local, fill, rim, path)
            }
        }
        .then(
            if (!running) {
                Modifier
            } else {
                Modifier
                    .testTag(ChronotypeRevealTags.Spectacle)
                    .semantics { onClick(label = skipLabel) { skip(); true } }
                    .pointerInput(entrance) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val toLink = entrance.linkShown
                            if (!toLink) down.consume()
                            val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                            if (!toLink) up.consume()
                            skip()
                        }
                    }
            },
        )
}

@Composable
internal fun RevealWords(
    chronotype: Chronotype,
    picked: Boolean,
    wordsProgress: () -> Float = { 1f },
    midSleep: LocalTime,
    daysOffLinked: Boolean,
    align: TextAlign,
    compact: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val formatter = rememberTimeFormatter()
    Text(
        stringResource(if (picked) R.string.chronotype_reveal_kicker_picked else R.string.chronotype_reveal_kicker),
        style = MaterialTheme.typography.labelLarge,
        color = colors.primary,
        textAlign = align,
        modifier = Modifier.fillMaxWidth().staggeredWord(wordsProgress, 0.00f..0.45f),
    )
    Spacer(Modifier.size(if (compact) 4.dp else 8.dp))
    Text(
        chronotype.label(),
        style = if (compact) ClockblockTheme.textStyles.editorialTitle else ClockblockTheme.textStyles.editorialDisplay.copy(fontSize = 40.sp, lineHeight = 46.sp),
        color = colors.onSurface,
        textAlign = align,
        modifier = Modifier.fillMaxWidth().staggeredWord(wordsProgress, 0.16f..0.62f).semantics { heading() }.testTag(ChronotypeRevealTags.Name),
    )
    Spacer(Modifier.size(if (compact) 4.dp else 12.dp))
    Text(
        chronotype.description(),
        style = MaterialTheme.typography.titleMedium,
        color = colors.onSurface,
        textAlign = align,
        modifier = Modifier.fillMaxWidth().staggeredWord(wordsProgress, 0.32f..0.78f),
    )
    Spacer(Modifier.size(if (compact) 8.dp else 16.dp))
    val small: TextStyle = MaterialTheme.typography.bodyMedium
    Text(
        // While days off are still linked to work days, the estimate comes from alarm-clock nights: say so, and how to
        // sharpen it, instead of calling them nights off.
        stringResource(
            if (daysOffLinked) R.string.chronotype_reveal_midpoint_linked else R.string.chronotype_reveal_midpoint,
            formatter.format(midSleep),
        ),
        style = small,
        color = colors.onSurfaceVariant,
        textAlign = align,
        modifier = Modifier.fillMaxWidth().staggeredWord(wordsProgress, 0.48f..0.92f),
    )
    Text(
        stringResource(R.string.chronotype_reveal_plan),
        style = small,
        color = colors.onSurfaceVariant,
        textAlign = align,
        modifier = Modifier.fillMaxWidth().staggeredWord(wordsProgress, 0.58f..1.00f),
    )
}

/** One line of the staggered words: fades in while dropping into place over its own [share] of [progress]. */
internal fun Modifier.staggeredWord(progress: () -> Float, share: ClosedFloatingPointRange<Float>): Modifier = graphicsLayer {
    val p = staggerProgress(progress(), share)
    alpha = p
    translationY = -(1f - p) * WordStaggerDrop.toPx()
}

/** A staggered line's own eased 0 → 1 over its [share] of the words' [progress]. */
internal fun staggerProgress(progress: Float, share: ClosedFloatingPointRange<Float>): Float =
    ClockblockMotion.StaggerEasing.transform(((progress - share.start) / (share.endInclusive - share.start)).coerceIn(0f, 1f))

private val WordStaggerDrop = 12.dp

/**
 * [ChronotypeSky] grown into the reveal's illustration: the sky of the hour the day peaks, the dashed sun path over a
 * layered landscape, and the sun (or the moon, with stars and constellations) at its place. [entrance] (0 → 1, read in
 * the draw phase) rises the sun from the horizon and warms the sky from three hours earlier. A new [chronotype] moves
 * it to its new place.
 */
@Composable
private fun ChronotypeRevealArt(
    chronotype: Chronotype,
    entrance: () -> Float,
    modifier: Modifier = Modifier,
    drawn: () -> Boolean = { true },
) {
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    val target = chronotype.skyPose()
    val hour = remember { Animatable(target.hour) }
    val position = remember { Animatable(target.position) }
    val moon = remember { Animatable(if (target.evening) 1f else 0f) }
    LaunchedEffect(target) {
        if (hour.value != target.hour) launch { hour.animateTo(target.hour, motion.artEntrance()) }
        if (position.value != target.position) launch { position.animateTo(target.position, motion.artEntrance()) }
        val targetMoon = if (target.evening) 1f else 0f
        if (moon.value != targetMoon) moon.animateTo(targetMoon, motion.colour())
    }
    // The rays only turn while a sun shows (an evening type's sky has the moon): no loop runs for nothing.
    val sunRotation = rememberAmbient(!reduce && !target.evening, ClockblockMotion.SunRotationMillis, reverse = false)
    val twinkle = rememberAmbient(!reduce, ClockblockMotion.TwinkleMillis, reverse = true)
    ChronotypeScene(
        chronotype = chronotype,
        hour = { hour.value - (1f - entrance()) * EntranceHours },
        position = { position.value },
        moonFraction = { moon.value },
        entrance = entrance,
        sunRotation = { sunRotation.value },
        twinkle = { twinkle.value },
        drawn = drawn,
        modifier = modifier,
        cornerRadius = RevealCorner,
    )
}

/**
 * Prewarms the chronotype reveal's and picker sheet's shapes, morphs, text measurements and GPU pipelines on a single
 * frame while the Sleep step sits on screen, using the [MaterialTheme.colorScheme] surface colour so no visible pixel
 * changes.
 */
@Composable
internal fun ChronotypePrewarm(modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val sky = ClockblockTheme.sky
    val tertiary = ClockblockTheme.artColors.tertiary
    val typography = MaterialTheme.typography
    val textStyles = ClockblockTheme.textStyles
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val kicker = stringResource(R.string.chronotype_reveal_kicker)
    val other = stringResource(R.string.chronotype_reveal_other)
    val sheetTitle = stringResource(R.string.chronotype_sheet_title)
    val suggested = stringResource(R.string.chronotype_suggested)
    val plan = stringResource(R.string.chronotype_reveal_plan)
    val labels = Chronotype.entries.map { it.label() to it.description() }
    remember(measurer, typography, textStyles) {
        warmChronotypeSceneShapes()
        val morphs = spectacleMorphs(RevealCorner.value / RevealArtSize.value)
        val warmPath = Path()
        val warmSize = Size(100f, 100f)
        morphs.forEach { it.toPath(0.5f, warmSize, out = warmPath) }
        val display = textStyles.editorialDisplay.copy(fontSize = 40.sp, lineHeight = 46.sp)
        measurer.measure(kicker, typography.labelLarge)
        measurer.measure(other, typography.labelLarge)
        measurer.measure(sheetTitle, textStyles.editorialTitle)
        measurer.measure(suggested, typography.labelMedium)
        measurer.measure(plan, typography.bodyMedium)
        labels.forEach { (title, desc) ->
            measurer.measure(title, display)
            measurer.measure(title, textStyles.editorialTitle)
            measurer.measure(title, typography.titleMedium)
            measurer.measure(desc, typography.titleMedium)
            measurer.measure(desc, typography.bodyMedium)
        }
        true
    }
    val view = LocalView.current
    var armed by rememberSaveable { mutableStateOf(true) }
    DisposableEffect(view) {
        val clear = Runnable { armed = false }
        view.post(clear)
        onDispose { view.removeCallbacks(clear) }
    }
    if (!armed) return
    val fill = sky.gradientAt(10f)
    Box(modifier.size(RevealArtSize).clearAndSetSemantics { }) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 0.5f
                    scaleY = 0.99f
                }
                .clip(RoundedCornerShape(RevealCorner))
                .drawWithCache {
                    onDrawBehind {
                        drawRoundRect(outline, cornerRadius = CornerRadius(14.dp.toPx()))
                        drawCircle(outline, radius = 11.dp.toPx(), style = Stroke(2.dp.toPx()))
                        drawChronotypeSceneWarmup(sky, tertiary)
                    }
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(RevealCorner))
                .drawWithCache {
                    val morphs = spectacleMorphs(RevealCorner.toPx() / size.width.coerceAtLeast(1f))
                    val shapePath = Path()
                    onDrawBehind {
                        val shape = morphs[0].toPath(0.5f, size, out = shapePath)
                        drawPath(
                            shape,
                            Brush.verticalGradient(0f to fill.top.copy(alpha = 0.5f), 1f to fill.bottom.copy(alpha = 0.5f), endY = size.height),
                        )
                        drawPath(
                            shape,
                            Brush.verticalGradient(0f to fill.top, 1f to fill.bottom, endY = size.height),
                        )
                        drawPath(shape, tertiary.copy(alpha = 0.5f), style = Stroke(2.dp.toPx()))
                        drawRoundRect(outline, cornerRadius = CornerRadius(14.dp.toPx()))
                        drawCircle(outline, radius = 11.dp.toPx(), style = Stroke(2.dp.toPx()))
                        drawChronotypeSceneWarmup(sky, tertiary)
                        withTransform({ scale(0.99f, 0.99f) }) {
                            drawChronotypeSceneWarmup(sky, tertiary)
                        }
                        drawRect(surface)
                    }
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val morphs = spectacleMorphs(RevealCorner.toPx() / size.width.coerceAtLeast(1f))
                    val shapePath = Path()
                    onDrawBehind {
                        val shape = morphs[0].toPath(0.5f, size, out = shapePath)
                        drawPath(
                            shape,
                            Brush.verticalGradient(0f to fill.top.copy(alpha = 0.5f), 1f to fill.bottom.copy(alpha = 0.5f), endY = size.height),
                        )
                        drawPath(
                            shape,
                            Brush.verticalGradient(0f to fill.top, 1f to fill.bottom, endY = size.height),
                        )
                        drawPath(shape, tertiary.copy(alpha = 0.5f), style = Stroke(2.dp.toPx()))
                        drawRect(surface)
                    }
                },
        )
    }
}

/** A 0 → 1 ambient phase on [ClockblockMotion.ambientLoop] when [enabled]; a still 0 otherwise. Read it in draw. */
@Composable
private fun rememberAmbient(enabled: Boolean, periodMillis: Int, reverse: Boolean): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "revealAmbient")
    return transition.animateFloat(0f, 1f, ClockblockTheme.motion.ambientLoop(periodMillis, reverse), label = "revealPhase")
}

private val RevealArtSize = 200.dp
private val ShortPaneHeight = 480.dp
private val RevealCorner = 32.dp

/** The link's share of the staggered words: the last line in. */
internal val LinkStagger = 0.68f..1.00f

/** The sky warms through the last few hours before the peak during the entrance. */
private const val EntranceHours = 3f

