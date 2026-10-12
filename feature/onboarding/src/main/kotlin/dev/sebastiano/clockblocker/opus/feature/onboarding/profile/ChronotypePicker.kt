package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toPath
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.feature.onboarding.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Test tags for the e2e suite. */
object ChronotypeTags {
    /** `chronotype_DefiniteMorning` … `chronotype_DefiniteEvening`. */
    fun option(chronotype: Chronotype): String = "chronotype_${chronotype.name}"
}

/**
 * Five selectable chronotype cards, Early bird → Night owl, each with a little sky showing when that person's day
 * naturally peaks. Radio-group semantics; selection changes colour only (a 100+/day-safe state change).
 *
 * @param suggested the chronotype the user's night suggests, labelled as such.
 */
@Composable
fun ChronotypePicker(
    selected: Chronotype,
    onSelect: (Chronotype) -> Unit,
    modifier: Modifier = Modifier,
    suggested: Chronotype? = null,
) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
        Chronotype.entries.forEachIndexed { index, chronotype ->
            ChronotypeCard(
                chronotype = chronotype,
                selected = chronotype == selected,
                suggested = chronotype == suggested,
                onClick = { onSelect(chronotype) },
                shape = segmentedShape(index, Chronotype.entries.size),
            )
        }
    }
}

@Composable
private fun ChronotypeCard(
    chronotype: Chronotype,
    selected: Boolean,
    suggested: Boolean,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
) {
    val colors = MaterialTheme.colorScheme
    val motion = ClockblockTheme.motion
    val container by animateColorAsState(if (selected) colors.secondaryContainer else colors.surfaceContainer, motion.colour(), label = "chronoContainer")
    Surface(
        shape = shape,
        color = container,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .testTag(ChronotypeTags.option(chronotype)),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChronotypeSky(chronotype, Modifier.size(52.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(chronotype.label(), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(chronotype.description(), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (suggested) {
                    Text(
                        stringResource(R.string.chronotype_suggested),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (selected) {
                    Surface(shape = CircleShape, color = colors.primary, modifier = Modifier.size(28.dp)) {
                        Icon(
                            painterResource(R.drawable.onboarding_ic_check),
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                } else {
                    Canvas(Modifier.size(24.dp)) {
                        drawCircle(colors.outline, size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }
    }
}

/**
 * A tiny sky for a chronotype: the gradient of the hour that person's day peaks, a dashed sun path, and the sun (or
 * the moon, for evening types) sitting at its point on the path.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChronotypeSky(chronotype: Chronotype, modifier: Modifier = Modifier, cornerRadius: Dp = 14.dp) {
    val sky = ClockblockTheme.sky
    val art = ClockblockTheme.artColors
    val (hour, position) = when (chronotype) {
        Chronotype.DefiniteMorning -> 6.4f to 0.16f
        Chronotype.ModerateMorning -> 8.5f to 0.32f
        Chronotype.Intermediate -> 13f to 0.5f
        Chronotype.ModerateEvening -> 19.3f to 0.72f
        Chronotype.DefiniteEvening -> 23.5f to 0.86f
    }
    val gradient = sky.gradientAt(hour)
    val evening = chronotype == Chronotype.ModerateEvening || chronotype == Chronotype.DefiniteEvening
    val sunPath = remember { MaterialShapes.Sunny.toPath().asComposePath() }
    Canvas(modifier.clip(RoundedCornerShape(cornerRadius))) {
        drawRect(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
        val w = size.width
        val h = size.height
        val horizon = h * 0.78f
        // Sun path: a dashed half-ellipse over the horizon.
        val arc = Path().apply { arcTo(Rect(w * 0.1f, h * 0.22f, w * 0.9f, horizon * 2f - h * 0.22f), 180f, 180f, true) }
        drawPath(arc, Color.White.copy(alpha = 0.55f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
        drawRect(Color.Black.copy(alpha = 0.10f), topLeft = Offset(0f, horizon), size = androidx.compose.ui.geometry.Size(w, h - horizon))
        val angle = PI * (1.0 - position)
        val rx = w * 0.4f
        val ry = horizon - h * 0.22f
        val p = Offset(w / 2f + (rx * cos(angle)).toFloat(), horizon - (ry * sin(angle)).toFloat())
        val r = w * 0.15f
        if (evening) {
            val disc = Path().apply { addOval(Rect(p, r)) }
            val bite = Path().apply { addOval(Rect(Offset(p.x + r * 0.55f, p.y - r * 0.45f), r * 0.85f)) }
            drawPath(Path.combine(PathOperation.Difference, disc, bite), MoonColor)
        } else {
            val b = sunPath.getBounds()
            val s = 2.4f * r / maxOf(b.width, b.height)
            withTransform({
                translate(p.x - b.center.x * s, p.y - b.center.y * s)
                scale(s, s, pivot = Offset.Zero)
            }) { drawPath(sunPath, art.tertiary) }
        }
    }
}

private val MoonColor = Color(0xFFF3EBD3)
