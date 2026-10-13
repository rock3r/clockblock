import androidx.compose.ui.semantics.clearAndSetSemantics
package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.ChronotypeScene
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.skyPose
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.ShapeMorph
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.drawMorph
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.feature.onboarding.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChronotypeSpectacleOverlay(
    chronotype: Chronotype,
    dialBounds: Rect,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = ClockblockTheme.motion
    val reduce = ClockblockTheme.reduceMotion
    
    // Core timeline
    // 0..1: Morph spins and pop
    // 1..2: Layers animate in
    // 2..3: Text fades in
    val phase = remember { Animatable(0f) }
    
    LaunchedEffect(reduce) {
        if (reduce) {
            onFinished()
        } else {
            // Stage 1: Rise and morph spin
            phase.animateTo(1f, motion.containerSpatial()) // Expressive spatial bounce
            // Stage 2: Layers
            phase.animateTo(2f, motion.artEntrance())
            // Stage 3: Words
            phase.animateTo(3f, motion.containerSpatial())
            
            onFinished()
        }
    }
    
    import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
    BoxWithConstraints(modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFinished() }.clearAndSetSemantics {}) {
        val wide = maxWidth >= 840.dp || (maxWidth >= 600.dp && maxWidth > maxHeight)
        val compact = maxHeight < 580.dp
        val artSize = minOf(260.dp, maxHeight * 0.6f)
        
        // We capture the target bounds of the art to interpolate
        var artBounds by remember { mutableStateOf<Rect?>(null) }
        
        // Draw the animating shape
        val density = LocalDensity.current
        val artSizePx = with(density) { artSize.toPx() }
        val sky = ClockblockTheme.sky
        
        val shape1 = MaterialShapes.SoftBurst
        val shape2 = MaterialShapes.Cookie7Sided
        import androidx.graphics.shapes.CornerRounding
        import androidx.graphics.shapes.CornerRounding
        val targetRect = RoundedPolygon(
            vertices = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f),
            rounding = CornerRounding(radius = with(density) { 14.dp.toPx() } / (artSizePx / 2f))
        )
        
        val morph1 = remember { ShapeMorph(shape1, shape2) }
        val morph2 = remember { ShapeMorph(shape2, targetRect) }
        
        Box(Modifier.fillMaxSize().drawBehind {
            if (artBounds == null) return@drawBehind
            
            val p = phase.value
            if (p < 1f) {
                // Moving from dial to artBounds
                val center = Offset(
                    dialBounds.center.x + (artBounds!!.center.x - dialBounds.center.x) * p,
                    dialBounds.center.y + (artBounds!!.center.y - dialBounds.center.y) * p
                )
                // scale overshoot pop
                val scale = if (p < 0.5f) p * 2f else 1f + kotlin.math.sin((p - 0.5f) * 2f * kotlin.math.PI.toFloat()) * 0.1f
                val size = Size(artSizePx, artSizePx) * scale
                
                // spins
                val rotation = p * 720f
                
                // morph
                val mProgress = (p * 2f).coerceIn(0f, 1f)
                val m2Progress = ((p - 0.5f) * 2f).coerceIn(0f, 1f)
                
                androidx.compose.ui.graphics.drawscope.translate(left = center.x - size.width/2f, top = center.y - size.height/2f) {
                    if (p < 0.5f) {
                        drawMorph(morph1, mProgress, sky.night, rotation, scale = 1f)
                    } else {
                        drawMorph(morph2, m2Progress, sky.night, rotation, scale = 1f)
                    }
                }
            }
        }) {
            // Invisible anchor to get bounds
            val artBlock: @Composable (Dp) -> Unit = { size ->
                Box(Modifier.size(size).onGloballyPositioned { artBounds = it.boundsInRoot() }) {
                    if (phase.value >= 1f) {
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
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            
            val words: @Composable ColumnScope.(TextAlign, Alignment.Horizontal, Boolean) -> Unit = { align, horizontal, compact ->
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = horizontal,
                ) {
                    RevealWords(chronotype, false, { (phase.value - 2f).coerceIn(0f, 1f) }, LocalTime.MIDNIGHT, false, align, compact)
                }
            }
            
            if (wide) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        artBlock(artSize)
                    }
                    Column(Modifier.weight(1f).padding(end = 24.dp), horizontalAlignment = Alignment.Start) {
                        words(TextAlign.Start, Alignment.Start, compact)
                    }
                }
            } else {
                Column {
                    Spacer(Modifier.weight(0.5f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        artBlock(artSize)
                    }
                    Spacer(Modifier.height(32.dp))
                    words(TextAlign.Center, Alignment.CenterHorizontally, compact)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
