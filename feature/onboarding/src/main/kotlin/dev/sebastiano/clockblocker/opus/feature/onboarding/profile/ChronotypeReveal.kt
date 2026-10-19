package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import android.graphics.PorterDuff
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.AdviceShapes
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.ChronotypeScene
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.SkyPose
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.drawChronotypeSceneWarmup
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.skyPose
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.warmChronotypeSceneShapes
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.notifications.R as NotificationR
import dev.sebastiano.clockblocker.opus.feature.onboarding.R
import dev.sebastiano.clockblocker.opus.feature.onboarding.rememberTouchExplorationEnabled
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
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

    /** The single-frame Sleep-step prewarm surface (gone once disarmed). */
    const val Prewarm = "chronotype_reveal_prewarm"
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
 *   the end; a tap on the link once it shows also opens the sheet. [onSpectacleFinished] follows either, and
 *   [onSpectacleName] as the chronotype's name starts to appear (or at the skip).
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
    active: Boolean = true,
    activeState: State<Boolean> = rememberUpdatedState(active),
    daysOffLinked: Boolean = false,
    spectacleFrom: Rect? = null,
    spectacleFromProvider: () -> Rect? = { spectacleFrom },
    onSpectacleFinished: () -> Unit = {},
    onSpectacleName: () -> Unit = {},
) {
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    val touchExploring = rememberTouchExplorationEnabled()
    val played = rememberSaveable { mutableStateOf(false) }
    val entrance = remember {
        val initialActive = Snapshot.withoutReadObservation { activeState.value }
        val initialFrom = Snapshot.withoutReadObservation { spectacleFromProvider() }
        RevealEntrance(
            start = when {
                !initialActive -> RevealStart.Rest
                reduce || touchExploring -> RevealStart.Rest
                initialFrom != null -> RevealStart.Spectacle
                played.value -> RevealStart.Rest
                else -> RevealStart.Entrance
            },
            spectacleFrom = initialFrom,
        )
    }
    val currentOnSpectacleFinished by rememberUpdatedState(onSpectacleFinished)
    val scope = rememberCoroutineScope()
    val choreography = remember { mutableStateOf<Job?>(null) }
    val toRest: () -> Unit = remember(entrance, choreography, scope) {
        {
            val wasSpectacle = entrance.spectacleRunning
            choreography.value?.cancel()
            scope.launch {
                entrance.skip()
                if (wasSpectacle) currentOnSpectacleFinished()
            }
        }
    }
    val skip: () -> Unit = remember(entrance, toRest) {
        { if (entrance.spectacleRunning) toRest() }
    }
    val skipLabel = stringResource(R.string.chronotype_spectacle_skip)
    val spectacleSemantics = remember {
        val initialRunning = Snapshot.withoutReadObservation { entrance.spectacleRunning }
        SpectacleSemanticsHolder(initialRunning = initialRunning, initialSkipLabel = skipLabel)
    }
    BindChronotypeRevealEntrance(
        entrance = entrance,
        played = played,
        activeState = activeState,
        reduce = reduce,
        touchExploring = touchExploring,
        spectacleFromProvider = spectacleFromProvider,
        choreography = choreography,
        spectacleSemantics = spectacleSemantics,
        skipLabel = skipLabel,
        toRest = toRest,
        onSpectacleFinished = onSpectacleFinished,
        onSpectacleName = onSpectacleName,
        motion = motion,
    )

    var showSheet by rememberSaveable { mutableStateOf(false) }
    val coords = remember { SpectacleCoords() }
    val artEntrance = remember(entrance) { { entrance.art.value } }
    val artDrawn = remember(entrance) { { entrance.artAlpha > 0f } }
    val artModifier = remember(entrance) {
        Modifier.fillMaxSize().graphicsLayer {
            val scale = entrance.popScale
            val a = entrance.artAlpha
            alpha = if (scale != 1f && a >= 1f) PopLayerOpaqueAlpha else a
            scaleX = scale
            scaleY = scale
            compositingStrategy = if (scale != 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
    }
    val artBlock: @Composable (Dp) -> Unit = { size ->
        val boxModifier = remember(size, coords) {
            Modifier
                .size(size)
                .onPlaced { coords.card = it }
                .testTag(ChronotypeRevealTags.Art)
        }
        Box(boxModifier) {
            ChronotypeRevealArt(
                chronotype,
                entrance = artEntrance,
                drawn = artDrawn,
                activeState = activeState,
                modifier = artModifier,
            )
        }
    }
    val wordsProgress = remember(entrance) { { entrance.words.value } }
    val otherModifier = remember(wordsProgress) {
        Modifier
            .heightIn(min = 48.dp)
            .staggeredWord(wordsProgress, LinkStagger)
            .testTag(ChronotypeRevealTags.Other)
    }
    val words: @Composable ColumnScope.(TextAlign, Alignment.Horizontal, Boolean) -> Unit = { align, horizontal, compact ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = horizontal) {
            RevealWords(chronotype, picked, wordsProgress, midSleep, daysOffLinked, align, compact)
            Spacer(Modifier.size(8.dp))
            TextButton(
                // Until it shows, the link is only part of the spectacle (a keyboard or Switch Access click skips it).
                onClick = { if (entrance.spectacleRunning && !entrance.linkShown) skip() else showSheet = true },
                modifier = otherModifier,
            ) { Text(stringResource(R.string.chronotype_reveal_other)) }
        }
    }
    val spectacle = Modifier.spectacle(entrance, coords, chronotype, spectacleSemantics, skip)
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

@Composable
private fun BindChronotypeRevealEntrance(
    entrance: RevealEntrance,
    played: MutableState<Boolean>,
    activeState: State<Boolean>,
    reduce: Boolean,
    touchExploring: Boolean,
    spectacleFromProvider: () -> Rect?,
    choreography: MutableState<Job?>,
    spectacleSemantics: SpectacleSemanticsHolder,
    skipLabel: String,
    toRest: () -> Unit,
    onSpectacleFinished: () -> Unit,
    onSpectacleName: () -> Unit,
    motion: ClockblockMotion,
) {
    val active = activeState.value
    val spectacleFrom = if (active) spectacleFromProvider() else null
    var wasActive by remember { mutableStateOf(active) }
    if (active && !wasActive) {
        wasActive = true
        entrance.reset(
            nextStart = when {
                reduce || touchExploring -> RevealStart.Rest
                spectacleFrom != null -> RevealStart.Spectacle
                played.value -> RevealStart.Rest
                else -> RevealStart.Entrance
            },
            from = spectacleFrom,
        )
    } else if (!active && wasActive) {
        wasActive = false
        entrance.reset(RevealStart.Rest)
    }
    val running = entrance.spectacleRunning
    SideEffect { spectacleSemantics.update(running = running, skipLabel = skipLabel) }
    val currentOnSpectacleFinished by rememberUpdatedState(onSpectacleFinished)
    val currentOnSpectacleName by rememberUpdatedState(onSpectacleName)
    if (active) {
        if (entrance.start == RevealStart.Spectacle) {
            LaunchedEffect(entrance) {
                // The name's first frame on screen, a skip landing everything at once included.
                snapshotFlow { entrance.words.value > NameStagger.start }.first { it }
                currentOnSpectacleName()
            }
        }
        // Counted as played as soon as it's shown: leaving mid-entrance (Next during the rise) and coming back, or a
        // rotation, shows the reveal at rest instead of replaying it. Marked as the composition applies, not in the effect
        // below, so even a recreation before the effect's first run can't save it unplayed.
        SideEffect { played.value = true }
        LaunchedEffect(entrance) {
            choreography.value = coroutineContext[Job]
            entrance.play(motion)
            if (entrance.start == RevealStart.Spectacle) currentOnSpectacleFinished()
        }
        // Turning on reduce motion or TalkBack mid-entrance lands it at rest too.
        LaunchedEffect(reduce || touchExploring) { if (reduce || touchExploring) toRest() }
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
    coords: SpectacleCoords,
    chronotype: Chronotype,
    semanticsHolder: SpectacleSemanticsHolder,
    skip: () -> Unit,
): Modifier {
    val fill = ClockblockTheme.sky.gradientAt(((chronotype.skyPose().hour - EntranceHours) % 24f + 24f) % 24f)
    val rim = ClockblockTheme.artColors.tertiary
    val path = remember { Path() }
    val currentSkip by rememberUpdatedState(skip)
    SideEffect { semanticsHolder.onSkip = currentSkip }
    return remember(this, entrance, coords, fill, rim, path, semanticsHolder) {
        this
            .onPlaced { coords.root = it }
            .drawWithCache {
                if (!entrance.spectacleRunning) return@drawWithCache onDrawBehind {}
                val origin = coords.originInRoot()
                val local = coords.cardInRoot().translate(-origin)
                val morphs = spectacleMorphs(if (local.isEmpty) 0f else RevealCorner.toPx() / local.width)
                val startFrom = entrance.spectacleFrom
                val face = if (startFrom.isEmpty) Rect.Zero else startFrom.translate(-origin)
                onDrawBehind {
                    if (entrance.spectacleRunning && !local.isEmpty) drawSpectacleShape(entrance, morphs, face, local, fill, rim, path)
                }
            }
            .then(SpectacleSemanticsElement(semanticsHolder))
            .pointerInput(entrance) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (!entrance.spectacleRunning) return@awaitEachGesture
                    val toLink = entrance.linkShown
                    if (!toLink) down.consume()
                    val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                    if (!entrance.spectacleRunning) return@awaitEachGesture
                    if (!toLink) up.consume()
                    semanticsHolder.onSkip()
                }
            }
    }
}

@Stable
private class SpectacleCoords {
    var root: LayoutCoordinates? = null
    var card: LayoutCoordinates? = null

    fun originInRoot(): Offset {
        val r = root?.takeIf { it.isAttached } ?: return Offset.Zero
        return r.positionInRoot()
    }

    fun cardInRoot(): Rect {
        val c = card?.takeIf { it.isAttached } ?: return Rect.Zero
        return Rect(c.positionInRoot(), c.size.toSize())
    }
}

@Stable
private class SpectacleSemanticsHolder(
    initialRunning: Boolean = false,
    initialSkipLabel: String = "",
) {
    var running: Boolean = initialRunning
        private set
    var skipLabel: String = initialSkipLabel
        private set
    var onSkip: () -> Unit = {}
    var node: SpectacleSemanticsNode? = null

    fun update(running: Boolean, skipLabel: String) {
        if (this.running != running || this.skipLabel != skipLabel) {
            this.running = running
            this.skipLabel = skipLabel
            node?.invalidateSemantics()
        }
    }
}

private data class SpectacleSemanticsElement(
    val holder: SpectacleSemanticsHolder,
) : ModifierNodeElement<SpectacleSemanticsNode>() {
    override fun create(): SpectacleSemanticsNode = SpectacleSemanticsNode(holder)
    override fun update(node: SpectacleSemanticsNode) {
        node.holder = holder
        holder.node = node
    }
}

private class SpectacleSemanticsNode(
    var holder: SpectacleSemanticsHolder,
) : Modifier.Node(), SemanticsModifierNode {
    override fun onAttach() {
        holder.node = this
    }

    override fun onDetach() {
        if (holder.node === this) holder.node = null
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        if (holder.running) {
            testTag = ChronotypeRevealTags.Spectacle
            onClick(label = holder.skipLabel) {
                holder.onSkip()
                true
            }
        }
    }
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
        modifier = Modifier.fillMaxWidth().staggeredWord(wordsProgress, NameStagger).semantics { heading() }.testTag(ChronotypeRevealTags.Name),
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
    activeState: State<Boolean> = remember { mutableStateOf(true) },
) {
    val target = chronotype.skyPose()
    val hour = remember { Animatable(target.hour) }
    val position = remember { Animatable(target.position) }
    val restingMoon = if (target.evening) 1f else 0f
    val moonFrom = remember { mutableFloatStateOf(restingMoon) }
    val moonTo = remember { mutableFloatStateOf(restingMoon) }
    val swap = remember { Animatable(1f) }
    val sunRotation = remember { AmbientPhaseState() }
    val twinkle = remember { AmbientPhaseState() }
    BindChronotypeArtMotion(
        target = target,
        activeState = activeState,
        hour = hour,
        position = position,
        moonFrom = moonFrom,
        moonTo = moonTo,
        swap = swap,
        sunRotation = sunRotation,
        twinkle = twinkle,
    )
    val hourLambda = remember(hour, entrance) { { hour.value - (1f - entrance()) * EntranceHours } }
    val positionLambda = remember(position) { { position.value } }
    val moonFractionLambda = remember(moonFrom, moonTo, swap) {
        { BodySwap.moonFraction(moonFrom.floatValue, moonTo.floatValue, swap.value) }
    }
    val sunRotationLambda = remember(sunRotation) { { sunRotation.value } }
    val twinkleLambda = remember(twinkle) { { twinkle.value } }
    val bodyScaleLambda = remember(swap) { { 1f + BodySwap.PopScale * BodySwap.pop(swap.value) } }
    ChronotypeScene(
        chronotype = chronotype,
        hour = hourLambda,
        position = positionLambda,
        moonFraction = moonFractionLambda,
        entrance = entrance,
        sunRotation = sunRotationLambda,
        twinkle = twinkleLambda,
        drawn = drawn,
        modifier = modifier,
        cornerRadius = RevealCorner,
        bodyScale = bodyScaleLambda,
    )
}

@Composable
private fun BindChronotypeArtMotion(
    target: SkyPose,
    activeState: State<Boolean>,
    hour: Animatable<Float, AnimationVector1D>,
    position: Animatable<Float, AnimationVector1D>,
    moonFrom: MutableFloatState,
    moonTo: MutableFloatState,
    swap: Animatable<Float, AnimationVector1D>,
    sunRotation: AmbientPhaseState,
    twinkle: AmbientPhaseState,
) {
    val active = activeState.value
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    // Sun ⇄ moon: one clock for the cross-fade and pop (BodySwap), from wherever the last swap had got to, with a
    // CONFIRM haptic at the top of the pop (straight away when motion is reduced and the swap snaps).
    val view = LocalView.current
    LaunchedEffect(target, active) {
        if (!active) {
            if (hour.value != target.hour) hour.snapTo(target.hour)
            if (position.value != target.position) position.snapTo(target.position)
            val targetMoon = if (target.evening) 1f else 0f
            moonFrom.floatValue = targetMoon
            moonTo.floatValue = targetMoon
            if (swap.value != 1f) swap.snapTo(1f)
            return@LaunchedEffect
        }
        if (hour.value != target.hour) launch { hour.animateTo(target.hour, motion.artEntrance()) }
        if (position.value != target.position) launch { position.animateTo(target.position, motion.artEntrance()) }
        val targetMoon = if (target.evening) 1f else 0f
        if (moonTo.floatValue != targetMoon) {
            moonFrom.floatValue = BodySwap.moonFraction(moonFrom.floatValue, moonTo.floatValue, swap.value)
            moonTo.floatValue = targetMoon
            swap.snapTo(0f)
            launch {
                snapshotFlow { swap.value >= BodySwap.PopPeak }.first { it }
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            swap.animateTo(1f, motion.bodySwap())
        }
    }
    // The rays only turn while a sun shows (an evening type's sky has the moon): no loop runs for nothing. A sun fading
    // out under a swap keeps turning, so it doesn't snap back to its first angle.
    val swapping by remember { derivedStateOf { swap.value < 1f } }
    BindAmbientPhase(sunRotation, active && !reduce && (!target.evening || swapping), ClockblockMotion.SunRotationMillis, reverse = false)
    BindAmbientPhase(twinkle, active && !reduce, ClockblockMotion.TwinkleMillis, reverse = true)
}

/**
 * Prewarms the chronotype reveal's and picker sheet's shapes, morphs, text measurements and Vulkan graphics pipelines
 * on a single frame while the Sleep step sits on screen, then disarms itself (`armed = false`) via `view.post` so it
 * leaves the composition tree after that frame.
 *
 * Why this exists: on a cold first run on a Pixel 7 Pro, Skia/Vulkan compiled the reveal and sheet graphics pipelines
 * lazily on the RenderThread (`138.4 ms` across 9 `CreateGraphicsPipeline-CompileAfterCacheMiss` slices during the
 * reveal entrance, with a worst `DrawFrames` of `78.8 ms`; and `123.4 ms` across 9 slices when opening the picker
 * sheet, with a worst `DrawFrames` of `100.9 ms`), plus first-time `ShapeMorph` construction and Minikin text shaping
 * on the main thread.
 *
 * Maintenance contract: the draw list here must mirror the primitives drawn by [ChronotypeScene]
 * ([drawChronotypeSceneWarmup]), [drawSpectacleShape] (2-stop vertical gradient path fill + rim stroke, both clipped
 * and unclipped), and [ChronotypePicker] (card rounded rect + radio-button circle stroke), in both an offscreen
 * `graphicsLayer` (`flush layers`, at [PrewarmTranslucentAlpha] and [PrewarmNonIdentityScale]) and the direct window
 * buffer (`flush commands`, unscaled and at [PrewarmNonIdentityScale]). Its output is covered by an opaque
 * `drawRect(surface)` of the same `200.dp` bounds so no visible pixel on the Sleep step ever changes.
 */
@Composable
internal fun ChronotypePrewarm(
    modifier: Modifier = Modifier,
    disarmAfterFrame: Boolean = true,
    onPrewarmed: () -> Unit = {},
) {
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
    val midpointLinked = stringResource(R.string.chronotype_reveal_midpoint_linked, "03:30")
    val midpointUnlinked = stringResource(R.string.chronotype_reveal_midpoint, "03:30")
    val labels = Chronotype.entries.map { it.label() to it.description() }
    // Warm Tools/Reminders vector drawables into LocalImageVectorCache so step transitions don't parse XML vectors synchronously.
    painterResource(NotificationR.drawable.ic_notif_caffeine)
    painterResource(R.drawable.onboarding_ic_flight)
    painterResource(NotificationR.drawable.ic_notif_nap)
    painterResource(NotificationR.drawable.ic_notif_melatonin)
    painterResource(NotificationR.drawable.ic_notif_light)
    painterResource(R.drawable.onboarding_ic_info)
    painterResource(R.drawable.onboarding_ic_expand_more)
    painterResource(R.drawable.onboarding_ic_check)
    painterResource(NotificationR.drawable.ic_notif_app)
    remember(measurer, typography, textStyles) {
        AdviceShapes.warmUp()
        warmChronotypeSceneShapes()
        val morphs = spectacleMorphs(RevealCorner.value / RevealArtSize.value)
        val warmPath = Path()
        val warmSize = Size(100f, 100f)
        morphs.forEach { it.toPath(PrewarmTranslucentAlpha, warmSize, out = warmPath) }
        val display = textStyles.editorialDisplay.copy(fontSize = 40.sp, lineHeight = 46.sp)
        measurer.measure(kicker, typography.labelLarge)
        measurer.measure(other, typography.labelLarge)
        measurer.measure(sheetTitle, textStyles.editorialTitle)
        measurer.measure(suggested, typography.labelMedium)
        measurer.measure(plan, typography.bodyMedium)
        measurer.measure(midpointLinked, typography.bodyMedium)
        measurer.measure(midpointUnlinked, typography.bodyMedium)
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
    var armed by remember { mutableStateOf(true) }
    val currentOnPrewarmed by rememberUpdatedState(onPrewarmed)
    DisposableEffect(view, disarmAfterFrame) {
        if (!disarmAfterFrame) return@DisposableEffect onDispose {}
        val clear = Runnable {
            armed = false
            currentOnPrewarmed()
        }
        view.post(clear)
        onDispose { view.removeCallbacks(clear) }
    }
    if (!armed) return
    val fill = sky.gradientAt(10f)
    Box(modifier.size(RevealArtSize).testTag(ChronotypeRevealTags.Prewarm).clearAndSetSemantics { }) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = PrewarmTranslucentAlpha }
                .drawWithCache {
                    onDrawBehind {
                        drawRect(outline)
                        drawRect(Color.Transparent, topLeft = Offset(2f, 2f), size = Size(10f, 10f), blendMode = BlendMode.Clear)
                        drawIntoCanvas { canvas ->
                            canvas.save()
                            canvas.clipRect(2f, 2f, 12f, 12f)
                            canvas.nativeCanvas.drawColor(0, PorterDuff.Mode.CLEAR)
                            canvas.restore()
                        }
                        drawLine(outline, Offset.Zero, Offset(size.width, 0f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(outline, Offset.Zero, Offset(size.width, 0f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Butt)
                        drawRoundRect(outline, cornerRadius = CornerRadius(14.dp.toPx()))
                        drawRoundRect(outline, cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(2.dp.toPx()))
                        drawCircle(outline, radius = 11.dp.toPx())
                        drawCircle(outline, radius = 11.dp.toPx(), style = Stroke(2.dp.toPx()))
                        drawArc(
                            color = outline,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                        )
                        drawChronotypeSceneWarmup(sky, tertiary)
                    }
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = PrewarmTranslucentAlpha
                    scaleY = PrewarmNonIdentityScale
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
                .graphicsLayer {
                    scaleX = PrewarmNonIdentityScale
                    scaleY = PrewarmNonIdentityScale
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .clip(RoundedCornerShape(RevealCorner))
                .drawWithCache {
                    onDrawBehind {
                        drawRect(surface)
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
                        val shape = morphs[0].toPath(PrewarmTranslucentAlpha, size, out = shapePath)
                        drawPath(
                            shape,
                            Brush.verticalGradient(
                                0f to fill.top.copy(alpha = PrewarmTranslucentAlpha),
                                1f to fill.bottom.copy(alpha = PrewarmTranslucentAlpha),
                                endY = size.height,
                            ),
                        )
                        drawPath(
                            shape,
                            Brush.verticalGradient(0f to fill.top, 1f to fill.bottom, endY = size.height),
                        )
                        drawPath(shape, tertiary.copy(alpha = PrewarmTranslucentAlpha), style = Stroke(2.dp.toPx()))
                        drawRoundRect(outline, cornerRadius = CornerRadius(14.dp.toPx()))
                        drawCircle(outline, radius = 11.dp.toPx(), style = Stroke(2.dp.toPx()))
                        drawChronotypeSceneWarmup(sky, tertiary)
                        withTransform({ scale(PrewarmNonIdentityScale, PrewarmNonIdentityScale) }) {
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
                        val shape = morphs[0].toPath(PrewarmTranslucentAlpha, size, out = shapePath)
                        drawPath(
                            shape,
                            Brush.verticalGradient(
                                0f to fill.top.copy(alpha = PrewarmTranslucentAlpha),
                                1f to fill.bottom.copy(alpha = PrewarmTranslucentAlpha),
                                endY = size.height,
                            ),
                        )
                        drawPath(
                            shape,
                            Brush.verticalGradient(0f to fill.top, 1f to fill.bottom, endY = size.height),
                        )
                        drawPath(shape, tertiary.copy(alpha = PrewarmTranslucentAlpha), style = Stroke(2.dp.toPx()))
                        drawRect(surface)
                    }
                },
        )
    }
}

/** Non-opaque alpha so Skia compiles the translucent (`SrcOver`) pipeline variants and HWUI flushes an offscreen layer. */
private const val PrewarmTranslucentAlpha = 0.5f

/**
 * Keeps the offscreen layer on the already-compiled modulated `TextureOp` pipeline during the ~200 ms pop settle after
 * [RevealEntrance.artAlpha] reaches 1, before [RevealEntrance.popScale] returns to 1 and [CompositingStrategy.Auto] takes over.
 */
private const val PopLayerOpaqueAlpha = 0.998f

/** Non-identity scale so Skia compiles the `kScale_Mask` scene and `TextureOp` pipeline variants. */
private const val PrewarmNonIdentityScale = 0.99f

@Stable
private class AmbientPhaseState : State<Float> {
    var delegate: State<Float>? by mutableStateOf(null)
    override val value: Float
        get() = delegate?.value ?: 0f
}

/** A 0 → 1 ambient phase on [ClockblockMotion.ambientLoop] when [enabled]; a still 0 otherwise. Read it in draw. */
@Composable
private fun BindAmbientPhase(holder: AmbientPhaseState, enabled: Boolean, periodMillis: Int, reverse: Boolean) {
    if (!enabled) {
        if (holder.delegate != null) holder.delegate = null
        return
    }
    val transition = rememberInfiniteTransition(label = "revealAmbient")
    val anim = transition.animateFloat(0f, 1f, ClockblockTheme.motion.ambientLoop(periodMillis, reverse), label = "revealPhase")
    if (holder.delegate !== anim) holder.delegate = anim
}

private val RevealArtSize = 200.dp
private val ShortPaneHeight = 480.dp
private val RevealCorner = 32.dp

/** The chronotype name's share of the staggered words: second in, after the kicker. */
internal val NameStagger = 0.16f..0.62f

/** The link's share of the staggered words: the last line in. */
internal val LinkStagger = 0.68f..1.00f

/** The sky warms through the last few hours before the peak during the entrance. */
private const val EntranceHours = 3f

