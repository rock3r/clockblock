package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.ChronotypeScene
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.skyPose
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.feature.onboarding.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Test tags for the e2e suite. */
object ChronotypeRevealTags {
    const val Name = "chronotype_reveal_name"
    const val Other = "chronotype_reveal_other"
    const val Sheet = "chronotype_sheet"
}

/**
 * The chronotype worked out from the ideal night, revealed: a sky at the hour that person's day peaks, the sun (or,
 * for evening types, the moon) rising along its path, then our name and description for the chronotype, what the
 * night is centred on, and a "Doesn't sound like me" link to a sheet with all five.
 *
 * Motion (a rare moment, so Expressive is allowed; MOTION.md): the sky warms and the sun rises on `artEntrance()`, then
 * the words fade in on `colour()` while rising 24 dp on `containerSpatial()`, once per visit (saveable, so coming back
 * from Tools shows it at rest). The sun's rays turn and the stars twinkle on `ambientLoop`. Picking another chronotype
 * moves the sun along the path to its new place. Reduce motion: everything sits at rest; the words carry the meaning.
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
    alreadyPlayed: Boolean = false,
) {
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    var played by rememberSaveable(alreadyPlayed) { mutableStateOf(alreadyPlayed) }
    val start = !reduce && !played
    val art = remember { Animatable(if (start) 0f else 1f) }
    val wordsAlpha = remember { Animatable(if (start) 0f else 1f) }
    val wordsRise = remember { Animatable(if (start) 1f else 0f) }
    LaunchedEffect(reduce) {
        val play = !reduce && !played
        // Counted as played as soon as it starts: leaving mid-entrance (Next during the rise) and coming back
        // shows the reveal at rest instead of replaying it.
        played = true
        if (!play) {
            art.snapTo(1f)
            wordsAlpha.snapTo(1f)
            wordsRise.snapTo(0f)
        } else {
            launch { art.animateTo(1f, motion.artEntrance()) }
            // One mover at a time: the words follow once the sun is mostly up.
            snapshotFlow { art.value }.first { it >= WordsStartAt }
            launch { wordsAlpha.animateTo(1f, motion.colour()) }
            wordsRise.animateTo(0f, motion.containerSpatial())
        }
    }

    var showSheet by rememberSaveable { mutableStateOf(false) }
    val artBlock: @Composable (Dp) -> Unit = { size ->
        ChronotypeRevealArt(chronotype, entrance = { art.value }, modifier = Modifier.size(size))
    }
    val words: @Composable ColumnScope.(TextAlign, Alignment.Horizontal, Boolean) -> Unit = { align, horizontal, compact ->
        Column(
            Modifier.fillMaxWidth().graphicsLayer {
                alpha = wordsAlpha.value
                translationY = wordsRise.value * WordsRise.toPx()
            },
            horizontalAlignment = horizontal,
        ) {
            RevealWords(chronotype, picked, { 1f }, midSleep, daysOffLinked, align, compact)
            Spacer(Modifier.size(8.dp))
            TextButton(
                onClick = { showSheet = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag(ChronotypeRevealTags.Other),
            ) { Text(stringResource(R.string.chronotype_reveal_other)) }
        }
    }
    if (wide) BoxWithConstraints(modifier.fillMaxSize()) {
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
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            artBlock(RevealArtSize)
            Spacer(Modifier.size(28.dp))
            words(TextAlign.Center, Alignment.CenterHorizontally, false)
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
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
internal fun RevealWords(chronotype: Chronotype, picked: Boolean, wordsProgress: () -> Float = { 1f }, midSleep: LocalTime, daysOffLinked: Boolean, align: TextAlign, compact: Boolean) {
    val colors = MaterialTheme.colorScheme
    val formatter = rememberTimeFormatter()
    Text(
        stringResource(if (picked) R.string.chronotype_reveal_kicker_picked else R.string.chronotype_reveal_kicker),
        style = MaterialTheme.typography.labelLarge,
        color = colors.primary,
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(if (compact) 4.dp else 8.dp))
    Text(
        chronotype.label(),
        style = if (compact) ClockblockTheme.textStyles.editorialTitle else ClockblockTheme.textStyles.editorialDisplay.copy(fontSize = 40.sp, lineHeight = 46.sp),
        color = colors.onSurface,
        textAlign = align,
        modifier = Modifier.fillMaxWidth().semantics { heading() }.testTag(ChronotypeRevealTags.Name),
    )
    Spacer(Modifier.size(if (compact) 4.dp else 12.dp))
    Text(chronotype.description(), style = MaterialTheme.typography.titleMedium, color = colors.onSurface, textAlign = align, modifier = Modifier.fillMaxWidth())
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
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.chronotype_reveal_plan), style = small, color = colors.onSurfaceVariant, textAlign = align, modifier = Modifier.fillMaxWidth())
}

/**
 * [ChronotypeSky] grown into the reveal's illustration: the sky of the hour the day peaks, the dashed sun path over a
 * layered landscape, and the sun (or the moon, with stars and constellations) at its place. [entrance] (0 → 1, read in
 * the draw phase) rises the sun from the horizon and warms the sky from three hours earlier. A new [chronotype] moves
 * it to its new place.
 */
@Composable
private fun ChronotypeRevealArt(chronotype: Chronotype, entrance: () -> Float, modifier: Modifier = Modifier) {
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    val target = chronotype.skyPose()
    val hour = remember { Animatable(target.hour) }
    val position = remember { Animatable(target.position) }
    val moon = remember { Animatable(if (target.evening) 1f else 0f) }
    LaunchedEffect(target) {
        launch { hour.animateTo(target.hour, motion.artEntrance()) }
        launch { position.animateTo(target.position, motion.artEntrance()) }
        moon.animateTo(if (target.evening) 1f else 0f, motion.colour())
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
        modifier = modifier,
        cornerRadius = RevealCorner,
    )
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
private val WordsRise = 24.dp
private const val WordsStartAt = 0.7f

/** The sky warms through the last few hours before the peak during the entrance. */
private const val EntranceHours = 3f

