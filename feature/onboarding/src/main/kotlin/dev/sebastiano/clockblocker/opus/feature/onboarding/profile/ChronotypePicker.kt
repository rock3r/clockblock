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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.ChronotypeSky
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.feature.onboarding.R

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
    drawScenes: () -> Boolean = { true },
) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
        Chronotype.entries.forEachIndexed { index, chronotype ->
            ChronotypeCard(
                chronotype = chronotype,
                selected = chronotype == selected,
                suggested = chronotype == suggested,
                onClick = { onSelect(chronotype) },
                shape = segmentedShape(index, Chronotype.entries.size),
                drawScene = drawScenes,
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
    drawScene: () -> Boolean,
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
            ChronotypeSky(chronotype, Modifier.size(52.dp), drawn = drawScene)
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
