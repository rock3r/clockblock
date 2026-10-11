package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
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
import androidx.graphics.shapes.toPath
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.feature.onboarding.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
) {
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    var played by rememberSaveable { mutableStateOf(false) }
    val start = !reduce && !played
    val art = remember { Animatable(if (start) 0f else 1f) }
    val wordsAlpha = remember { Animatable(if (start) 0f else 1f) }
    val wordsRise = remember { Animatable(if (start) 1f else 0f) }
    LaunchedEffect(reduce) {
        if (reduce || played) {
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
        played = true
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
            RevealWords(chronotype, picked, midSleep, align, compact)
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
private fun RevealWords(chronotype: Chronotype, picked: Boolean, midSleep: LocalTime, align: TextAlign, compact: Boolean) {
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
        stringResource(R.string.chronotype_reveal_midpoint, formatter.format(midSleep)),
        style = small,
        color = colors.onSurfaceVariant,
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.chronotype_reveal_plan), style = small, color = colors.onSurfaceVariant, textAlign = align, modifier = Modifier.fillMaxWidth())
}

/** Where a chronotype's day peaks: the sky's hour, the sun's place along its path (0 = rising, 1 = set), and moon or sun. */
private data class SkyPose(val hour: Float, val position: Float, val evening: Boolean)

private fun Chronotype.pose(): SkyPose = when (this) {
    Chronotype.DefiniteMorning -> SkyPose(6.4f, 0.16f, evening = false)
    Chronotype.ModerateMorning -> SkyPose(8.5f, 0.32f, evening = false)
    Chronotype.Intermediate -> SkyPose(13f, 0.5f, evening = false)
    Chronotype.ModerateEvening -> SkyPose(19.3f, 0.72f, evening = true)
    Chronotype.DefiniteEvening -> SkyPose(23.5f, 0.86f, evening = true)
}

/**
 * [ChronotypeSky] grown into the reveal's illustration: the sky of the hour the day peaks, the dashed sun path over a
 * horizon, and the sun (or the moon, with a few stars) at its place. [entrance] (0 → 1, read in the draw phase) rises
 * the sun from the horizon and warms the sky from three hours earlier. A new [chronotype] moves it to its new place.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChronotypeRevealArt(chronotype: Chronotype, entrance: () -> Float, modifier: Modifier = Modifier) {
    val sky = ClockblockTheme.sky
    val art = ClockblockTheme.artColors
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    val target = chronotype.pose()
    val hour = remember { Animatable(target.hour) }
    val position = remember { Animatable(target.position) }
    val moon = remember { Animatable(if (target.evening) 1f else 0f) }
    LaunchedEffect(target) {
        launch { hour.animateTo(target.hour, motion.artEntrance()) }
        launch { position.animateTo(target.position, motion.artEntrance()) }
        moon.animateTo(if (target.evening) 1f else 0f, motion.colour())
    }
    val sunRotation = rememberAmbient(!reduce, ClockblockMotion.SunRotationMillis, reverse = false)
    val twinkle = rememberAmbient(!reduce, ClockblockMotion.TwinkleMillis, reverse = true)
    val sunPath = remember { MaterialShapes.Sunny.toPath().asComposePath() }
    val sunBounds = remember(sunPath) { sunPath.getBounds() }

    Canvas(modifier.clip(RoundedCornerShape(RevealCorner))) {
        val e = entrance()
        val h = hour.value - (1f - e) * EntranceHours
        val gradient = sky.gradientAt(((h % 24f) + 24f) % 24f)
        drawRect(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
        val w = size.width
        val ht = size.height
        val horizon = ht * 0.78f

        // Stars, for evening types (they fade with the moon).
        val m = moon.value
        if (m > 0f) {
            Stars.forEachIndexed { i, (sx, sy, r) ->
                val phase = (twinkle.value + i * 0.33f) % 1f
                val alpha = (0.55f + 0.45f * phase) * m * e
                drawCircle(StarColor.copy(alpha = alpha), r * w, Offset(sx * w, sy * ht))
            }
        }

        val arc = Path().apply { arcTo(Rect(w * 0.1f, ht * 0.22f, w * 0.9f, horizon * 2f - ht * 0.22f), 180f, 180f, true) }
        drawPath(arc, Color.White.copy(alpha = 0.55f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
        drawRect(Color.Black.copy(alpha = 0.10f), topLeft = Offset(0f, horizon), size = androidx.compose.ui.geometry.Size(w, ht - horizon))

        val pos = position.value * e
        val angle = PI * (1.0 - pos)
        val rx = w * 0.4f
        val ry = horizon - ht * 0.22f
        val p = Offset(w / 2f + (rx * cos(angle)).toFloat(), horizon - (ry * sin(angle)).toFloat())
        val r = w * 0.12f
        // Below the horizon line the sun hides behind the ground (it rises out of it).
        withTransform({ clipRect(bottom = horizon) }) {
            if (m < 1f) {
                val s = 2.4f * r / maxOf(sunBounds.width, sunBounds.height)
                withTransform({
                    translate(p.x - sunBounds.center.x * s, p.y - sunBounds.center.y * s)
                    scale(s, s, pivot = Offset.Zero)
                }) {
                    rotate(sunRotation.value * 45f, pivot = sunBounds.center) { drawPath(sunPath, art.tertiary.copy(alpha = 1f - m)) }
                }
            }
            if (m > 0f) {
                val disc = Path().apply { addOval(Rect(p, r)) }
                val bite = Path().apply { addOval(Rect(Offset(p.x + r * 0.55f, p.y - r * 0.45f), r * 0.85f)) }
                drawPath(Path.combine(PathOperation.Difference, disc, bite), MoonColor.copy(alpha = m))
            }
        }
    }
}

/** A 0 → 1 ambient phase on [ClockblockMotion.ambientLoop] when [enabled]; a still 0 otherwise. Read it in draw. */
@Composable
private fun rememberAmbient(enabled: Boolean, periodMillis: Int, reverse: Boolean): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "revealAmbient")
    return transition.animateFloat(0f, 1f, ClockblockTheme.motion.ambientLoop(periodMillis, reverse), label = "revealPhase")
}

/** Star positions (fractions of the art) and radii, kept clear of the moon's path. */
private val Stars = listOf(
    Triple(0.18f, 0.16f, 0.012f),
    Triple(0.34f, 0.08f, 0.009f),
    Triple(0.52f, 0.14f, 0.011f),
    Triple(0.70f, 0.07f, 0.008f),
    Triple(0.86f, 0.20f, 0.010f),
)

private val StarColor = Color(0xFFFFF4D6)
private val MoonColor = Color(0xFFF3EBD3)
private val RevealArtSize = 200.dp
private val ShortPaneHeight = 480.dp
private val RevealCorner = 32.dp
private val WordsRise = 24.dp
private const val WordsStartAt = 0.7f

/** The sky warms through the last few hours before the peak during the entrance. */
private const val EntranceHours = 3f
