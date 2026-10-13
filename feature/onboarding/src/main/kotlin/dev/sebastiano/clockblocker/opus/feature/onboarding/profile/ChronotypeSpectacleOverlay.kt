package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.ChronotypeScene
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.skyPose
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.ShapeMorph
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.drawMorph
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import java.time.LocalTime

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChronotypeSpectacleOverlay(
    chronotype: Chronotype,
    midSleep: LocalTime,
    daysOffLinked: Boolean,
    dialBounds: Rect,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    val phase = remember { Animatable(0f) }
    
    LaunchedEffect(reduce) {
        if (reduce) {
            onFinished()
        } else {
            phase.animateTo(1f, motion.containerSpatial())
            phase.animateTo(2f, motion.artEntrance())
            phase.animateTo(3f, motion.containerSpatial())
            onFinished()
        }
    }
    
    BoxWithConstraints(modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFinished() }.clearAndSetSemantics {}) {
        val wide = maxWidth >= 840.dp || (maxWidth >= 600.dp && maxWidth > maxHeight)
        val compact = maxHeight < 580.dp
        val artSize = minOf(260.dp, maxHeight * 0.6f)
        var artBounds by remember { mutableStateOf<Rect?>(null) }
        
        var overlayPos by remember { mutableStateOf(Offset.Zero) }
        val density = LocalDensity.current
        val artSizePx = with(density) { artSize.toPx() }
        val sky = ClockblockTheme.sky
        
        val shape1 = MaterialShapes.SoftBurst
        val shape2 = MaterialShapes.Cookie7Sided
        val targetRect = RoundedPolygon(
            vertices = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f),
            rounding = CornerRounding(radius = with(density) { 14.dp.toPx() } / (artSizePx / 2f))
        )
        
        val morph1 = remember { ShapeMorph(shape1, shape2) }
        val morph2 = remember { ShapeMorph(shape2, targetRect) }
        val shapeColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
        
        Box(Modifier.fillMaxSize().onGloballyPositioned { overlayPos = it.boundsInRoot().topLeft }.drawBehind {
            if (artBounds == null) return@drawBehind
            val p = phase.value
            if (p < 1f) {
                val center = Offset(
                    (dialBounds.center.x - overlayPos.x) + ((artBounds!!.center.x - overlayPos.x) - (dialBounds.center.x - overlayPos.x)) * p,
                    (dialBounds.center.y - overlayPos.y) + ((artBounds!!.center.y - overlayPos.y) - (dialBounds.center.y - overlayPos.y)) * p
                )
                val scale = if (p < 0.5f) p * 2f else 1f + kotlin.math.sin((p - 0.5f) * 2f * kotlin.math.PI.toFloat()) * 0.1f
                val size = Size(artSizePx, artSizePx) * scale
                val rotation = p * 720f
                val mProgress = (p * 2f).coerceIn(0f, 1f)
                val m2Progress = ((p - 0.5f) * 2f).coerceIn(0f, 1f)
                
                translate(left = center.x - size.width/2f, top = center.y - size.height/2f) {
                    if (p < 0.5f) {
                        drawMorph(morph1, mProgress, shapeColor, rotation, scale = 1f)
                    } else {
                        drawMorph(morph2, m2Progress, shapeColor, rotation, scale = 1f)
                    }
                }
            }
        }) {
            val artBlock: @Composable (Dp) -> Unit = { size ->
                Box(Modifier.size(size).onGloballyPositioned { artBounds = it.boundsInRoot() }) {
                    val entrance = (phase.value - 1f).coerceIn(0f, 1f)
                        val target = chronotype.skyPose()
                        ChronotypeScene(
                            chronotype = chronotype,
                            hour = { target.hour - (1f - entrance) * 6f },
                            position = { target.position },
                            moonFraction = { if (target.evening) 1f else 0f },
                            entrance = { entrance },
                            sunRotation = { 0f },
                            twinkle = { 0f },
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (phase.value >= 1f) 1f else 0f }
                        )
                }
            }
            
            val words: @Composable ColumnScope.(TextAlign, Alignment.Horizontal, Boolean) -> Unit = { align, horizontal, compact ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = horizontal) {
                    RevealWords(chronotype, false, { (phase.value - 2f).coerceIn(0f, 1f) }, midSleep, daysOffLinked, align, compact)
                }
            }
            
            if (wide) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { artBlock(artSize) }
                    Column(Modifier.weight(1f).padding(end = 24.dp), horizontalAlignment = Alignment.Start) { words(TextAlign.Start, Alignment.Start, compact) }
                }
            } else {
                Column {
                    Spacer(Modifier.weight(0.5f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { artBlock(artSize) }
                    Spacer(Modifier.height(32.dp))
                    words(TextAlign.Center, Alignment.CenterHorizontally, compact)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
