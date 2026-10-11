package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.formatJetLagHours
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.TwoClocksArt
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags

/**
 * "Your shift": what the planner makes of the trip as entered, live, before the main action. Every number comes from
 * the [ShiftPreview] the ViewModel computed with the real planner, summarised by [ShiftSummary] ("+9 h east", "About
 * 4 days with the plan, 7 without"); nothing is guessed here. Until the draft can be planned it says what it needs.
 * Static (no motion): it simply updates when the draft changes.
 *
 * @param globe **Globe slot (UX plan, PR 4).** The route globe goes here, above the summary, about 180 dp tall: the
 *   legs as arcs, the active leg highlighted. Null (today) draws nothing in its place, and the card shows the Two
 *   Clocks art beside the summary instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun YourShiftCard(
    preview: ShiftPreview?,
    modifier: Modifier = Modifier,
    globe: (@Composable () -> Unit)? = null,
) {
    val summary = preview?.let(ShiftSummary::of)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth().testTag(TripsTestTags.EditorPreview),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.editor_shift_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (globe != null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { globe() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShiftSummaryText(summary)
                }
                if (globe == null && summary is ShiftSummary.Shift) {
                    Spacer(Modifier.width(12.dp))
                    TwoClocksArt(modifier = Modifier.size(72.dp), progress = 0f, animated = false)
                }
            }
            if (summary != null) {
                Text(
                    stringResource(R.string.editor_preview_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShiftSummaryText(summary: ShiftSummary?) {
    val headline = MaterialTheme.typography.headlineSmallEmphasized
    when (summary) {
        null -> Text(stringResource(R.string.editor_shift_empty), style = MaterialTheme.typography.bodyLarge)
        ShiftSummary.HomeTime -> {
            Text(stringResource(R.string.editor_shift_home_title), style = headline)
            Text(stringResource(R.string.editor_preview_home), style = MaterialTheme.typography.bodyMedium)
        }
        ShiftSummary.NoShift -> {
            Text(stringResource(R.string.editor_shift_none_title), style = headline)
            Text(stringResource(R.string.editor_preview_no_shift), style = MaterialTheme.typography.bodyMedium)
        }
        is ShiftSummary.Shift -> {
            val hours = formatJetLagHours(summary.shiftHours)
            Text(
                stringResource(if (summary.east) R.string.trip_shift_east else R.string.trip_shift_west, hours),
                style = ClockblockTheme.textStyles.timeHeadline,
            )
            Text(
                pluralStringResource(R.plurals.editor_shift_with, summary.daysWithPlan, summary.daysWithPlan) + ", " +
                    pluralStringResource(R.plurals.editor_shift_without, summary.daysWithoutPlan, summary.daysWithoutPlan),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (summary.longWay) {
                Text(
                    stringResource(if (summary.east) R.string.editor_shift_long_way_later else R.string.editor_shift_long_way_earlier),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
