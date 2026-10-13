package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.ChronotypeScene
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.skyPose
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.ShapeMorph
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.feature.onboarding.R
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.sin

/**
 * First-time transition from the sleep dial to the chronotype reveal: a `MaterialShapes` polygon rises from the dial's
 * centre, spins through two morphs, pops with a brief overshoot into the rounded rectangle card, animates the
 * chronotype's landscape in layer by layer, and staggers the text down underneath.
 *
 * Uses the exact layout geometry of [ChronotypeReveal] so the hand-off at completion has zero positional jump.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChronotypeSpectacleOverlay(
    chronotype: Chronotype,
    picked: Boolean,
    midSleep: LocalTime,
    daysOffLinked: Boolean,
    wide: Boolean,
    dialBounds: Rect,
    onFinished: (openPicker: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    val phase = remember { Animatable(0f) }
    var skipped by remember { mutableStateOf(false) }

    LaunchedEffect(reduce) {
        if (reduce) {
            onFinished(false)
        } else {
            phase.animateTo(1f, motion.artEntrance())
            phase.animateTo(2f, motion.artEntrance())
            phase.animateTo(3f, motion.artEntrance())
            onFinished(false)
        }
    }

    val currentPhase: () -> Float = { if (skipped) 3f else phase.value }
    val density = LocalDensity.current
    val sky = ClockblockTheme.sky
    val pose = chronotype.skyPose()
    val startGradient = sky.gradientAt(((pose.hour - SpectacleEntranceHours) + 24f) % 24f)
    val rimColor = ClockblockTheme.artColors.tertiary

    var overlayPos by remember { mutableStateOf(Offset.Zero) }
    var artBounds by remember { mutableStateOf<Rect?>(null) }
    val scratchPath = remember { Path() }

    val morph1 = remember { ShapeMorph(MaterialShapes.SoftBurst, MaterialShapes.Sunny) }
    val morph2 = remember { ShapeMorph(MaterialShapes.Sunny, MaterialShapes.Cookie9Sided) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                skipped = true
                onFinished(false)
            }
            .clearAndSetSemantics {},
    ) {
        val artSize = if (wide) minOf(260.dp, maxHeight * 0.6f) else SpectacleArtSize
        val short = maxHeight < SpectacleShortPaneHeight
        val artSizePx = with(density) { artSize.toPx() }
        val cornerFraction = (with(density) { SpectacleCorner.toPx() } / (artSizePx / 2f)).coerceIn(0.05f, 0.45f)
        val morph3 = remember(cornerFraction) {
            val targetRect = RoundedPolygon(
                vertices = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f),
                rounding = CornerRounding(radius = cornerFraction),
            )
            ShapeMorph(MaterialShapes.Cookie9Sided, targetRect)
        }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { overlayPos = it.boundsInRoot().topLeft }
                .drawBehind {
                    val targetBounds = artBounds ?: return@drawBehind
                    val p = currentPhase().coerceIn(0f, 3f)
                    if (p < 1.05f) {
                        val morphP = p.coerceIn(0f, 1f)
                        val startCenter = if (!dialBounds.isEmpty) {
                            dialBounds.center - overlayPos
                        } else {
                            Offset(size.width / 2f, size.height * 0.52f)
                        }
                        val endCenter = targetBounds.center - overlayPos
                        // Smooth ease-out rise from the dial's centre to the card's centre.
                        val rise = 1f - (1f - morphP) * (1f - morphP)
                        val center = Offset(
                            startCenter.x + (endCenter.x - startCenter.x) * rise,
                            startCenter.y + (endCenter.y - startCenter.y) * rise,
                        )
                        // Rises from 34% scale during the two morph spins, then pops (1.12x overshoot) as it expands into the card.
                        val scale = when {
                            morphP < 0.72f -> 0.34f + (morphP / 0.72f) * 0.56f
                            else -> {
                                val popT = ((morphP - 0.72f) / 0.28f).coerceIn(0f, 1f)
                                0.90f + 0.10f * popT + sin(popT * PI.toFloat()) * 0.14f
                            }
                        }
                        val currentSize = Size(artSizePx * scale, artSizePx * scale)
                        val spinProgress = (morphP / 0.72f).coerceIn(0f, 1f)
                        val rotation = if (morphP < 0.72f) spinProgress * 720f else 0f
                        val path = when {
                            morphP < 0.36f -> morph1.toPath((morphP / 0.36f).coerceIn(0f, 1f), currentSize, rotation, out = scratchPath)
                            morphP < 0.72f -> morph2.toPath(((morphP - 0.36f) / 0.36f).coerceIn(0f, 1f), currentSize, rotation, out = scratchPath)
                            else -> morph3.toPath(((morphP - 0.72f) / 0.28f).coerceIn(0f, 1f), currentSize, 0f, out = scratchPath)
                        }
                        val shapeAlpha = if (p <= 1f) 1f else (1f - (p - 1f) / 0.05f).coerceIn(0f, 1f)
                        translate(left = center.x - currentSize.width / 2f, top = center.y - currentSize.height / 2f) {
                            drawPath(
                                path,
                                Brush.verticalGradient(
                                    0f to startGradient.top.copy(alpha = shapeAlpha),
                                    1f to startGradient.bottom.copy(alpha = shapeAlpha),
                                ),
                            )
                            drawPath(
                                path,
                                rimColor.copy(alpha = 0.38f * shapeAlpha * (1f - morphP * 0.6f)),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                    }
                },
        ) {
            val artBlock: @Composable (Dp) -> Unit = { boxSize ->
                Box(Modifier.size(boxSize).onGloballyPositioned { artBounds = it.boundsInRoot() }) {
                    ChronotypeScene(
                        chronotype = chronotype,
                        hour = {
                            val e = (currentPhase() - 1f).coerceIn(0f, 1f)
                            pose.hour - (1f - e) * SpectacleEntranceHours
                        },
                        position = { pose.position },
                        moonFraction = { if (pose.evening) 1f else 0f },
                        entrance = { (currentPhase() - 1f).coerceIn(0f, 1f) },
                        sunRotation = { 0f },
                        twinkle = { 0.5f },
                        cornerRadius = SpectacleCorner,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val fadeIn = ((currentPhase() - 0.96f) / 0.12f).coerceIn(0f, 1f)
                                alpha = fadeIn
                            },
                    )
                }
            }

            val wordsProgress: () -> Float = { (currentPhase() - 1.85f).coerceIn(0f, 1f) }
            val words: @Composable ColumnScope.(TextAlign, Alignment.Horizontal, Boolean) -> Unit = { align, horizontal, compact ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = horizontal) {
                    RevealWords(chronotype, picked, wordsProgress, midSleep, daysOffLinked, align, compact)
                    Spacer(Modifier.size(8.dp))
                    TextButton(
                        onClick = {
                            val openPicker = wordsProgress() > 0.68f
                            skipped = true
                            onFinished(openPicker)
                        },
                        modifier = Modifier.heightIn(min = 48.dp).staggeredWord(wordsProgress, 0.68f, 1.00f),
                    ) { Text(stringResource(R.string.chronotype_reveal_other)) }
                }
            }

            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(0.42f), contentAlignment = Alignment.Center) { artBlock(artSize) }
                    Spacer(Modifier.width(40.dp))
                    Column(Modifier.weight(0.58f).verticalScroll(rememberScrollState()).padding(vertical = if (short) 8.dp else 24.dp)) {
                        words(TextAlign.Start, Alignment.Start, short)
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    artBlock(SpectacleArtSize)
                    Spacer(Modifier.size(28.dp))
                    words(TextAlign.Center, Alignment.CenterHorizontally, false)
                }
            }
        }
    }
}

private val SpectacleArtSize = 200.dp
private val SpectacleShortPaneHeight = 480.dp
private val SpectacleCorner = 32.dp
private const val SpectacleEntranceHours = 3f
