package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.formatHoursMagnitude
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.TwoClocksArt
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "Your shift preview": what the planner makes of the trip as entered, before Save. Every number comes from the
 * [ShiftPreview] the ViewModel computed with the real planner (shift, direction, strategy, and its with/without-plan
 * estimates); nothing is guessed here. Static (no motion): it simply updates when the draft changes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShiftPreviewCard(preview: ShiftPreview, modifier: Modifier = Modifier) {
    // A home-time plan reports no shift (direction None, 0 h) by design, so it's checked first: otherwise a long-haul
    // short stay would read as "no jet lag".
    val homeTime = preview.strategy == AdaptationStrategy.StayOnHomeTime
    val noShift = !homeTime && (preview.direction == ShiftDirection.None || abs(preview.shiftHours) < MinShiftHours)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth().testTag(TripsTestTags.EditorPreview),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.editor_preview_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.semantics { heading() },
                )
                if (!noShift && !homeTime) {
                    val hours = formatHoursMagnitude(preview.shiftHours.toFloat())
                    val shift = stringResource(
                        if (preview.shiftHours > 0) R.string.editor_preview_east else R.string.editor_preview_west,
                        hours,
                    )
                    val direction = when (preview.direction) {
                        ShiftDirection.Advance -> stringResource(R.string.editor_preview_earlier)
                        ShiftDirection.Delay -> stringResource(R.string.editor_preview_later)
                        ShiftDirection.None -> null
                    }
                    Text(
                        listOfNotNull(shift, direction).joinToString(" · "),
                        style = OpusTheme.textStyles.timeTitle,
                    )
                }
                Text(
                    when {
                        noShift -> stringResource(R.string.editor_preview_no_shift)
                        homeTime -> stringResource(R.string.editor_preview_home)
                        else -> {
                            val with = roundDays(preview.daysToAdapt)
                            val without = roundDays(preview.daysWithoutPlan)
                            pluralStringResource(R.plurals.editor_preview_days, with, with) + " · " +
                                pluralStringResource(R.plurals.editor_preview_without, without, without)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.editor_preview_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            if (!noShift && !homeTime) {
                Spacer(Modifier.width(12.dp))
                TwoClocksArt(modifier = Modifier.size(72.dp), progress = 0f, animated = false)
            }
        }
    }
}

/** Below this the planner treats the trip as no shift (matches the plan screen's "no jet lag" threshold). */
private const val MinShiftHours = 1.0

private fun roundDays(days: Double): Int = days.roundToInt().coerceAtLeast(1)
