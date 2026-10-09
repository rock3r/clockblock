package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.data.trip.IssueSeverity
import dev.sebastiano.clockblocker.opus.core.data.trip.TripIssue
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import dev.sebastiano.clockblocker.opus.feature.trips.ui.formatDuration
import dev.sebastiano.clockblocker.opus.feature.trips.ui.rememberDateFormatter
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** A one-tap correction offered next to an issue. */
internal sealed interface IssueFix {
    data class Arrival(val suggested: LocalDateTime) : IssueFix
    data class ConnectFrom(val code: String) : IssueFix
}

internal fun TripIssue.fix(): IssueFix? = when (this) {
    is TripIssue.ArrivalNotAfterDeparture -> suggestedArrivalLocal?.let(IssueFix::Arrival)
    is TripIssue.ImplausiblyShort -> suggestedArrivalLocal?.let(IssueFix::Arrival)
    is TripIssue.ImplausiblyLong -> suggestedArrivalLocal?.let(IssueFix::Arrival)
    is TripIssue.LegsNotConnected -> IssueFix.ConnectFrom(arrivedAt.displayCode)
    else -> null
}

/**
 * One validation finding, inline under its leg. Severity is carried by colour, icon *and* a text label
 * ("Fix this" / "Check this" / "Note"), never colour alone. Errors are announced politely as they appear.
 */
@Composable
internal fun IssueBanner(
    issue: TripIssue,
    currentArrival: LocalDateTime?,
    onFix: (IssueFix) -> Unit,
    tagIndex: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content, icon, label) = when (issue.severity) {
        IssueSeverity.Error -> Quad(colors.errorContainer, colors.onErrorContainer, R.drawable.ic_trips_error, R.string.issue_error)
        IssueSeverity.Warning -> Quad(colors.tertiaryContainer, colors.onTertiaryContainer, R.drawable.ic_trips_warning, R.string.issue_warning)
        IssueSeverity.Info -> Quad(colors.secondaryContainer, colors.onSecondaryContainer, R.drawable.ic_trips_info, R.string.issue_info)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TripsTestTags.editorIssue(tagIndex))
            .semantics { if (issue.severity == IssueSeverity.Error) liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
                Column {
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                    Text(issueMessage(issue), style = MaterialTheme.typography.bodyMedium)
                }
            }
            issue.fix()?.let { fix ->
                AssistChip(
                    onClick = { onFix(fix) },
                    label = { Text(fixLabel(fix, currentArrival)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_trips_check), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.padding(start = 30.dp).testTag(TripsTestTags.editorFix(tagIndex)),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = colors.surface,
                        labelColor = colors.onSurface,
                        leadingIconContentColor = colors.primary,
                    ),
                    border = null,
                )
            }
        }
    }
}

private data class Quad(val container: Color, val content: Color, val icon: Int, val label: Int)

@Composable
private fun issueMessage(issue: TripIssue): String {
    val numbers = NumberFormat.getIntegerInstance()
    return when (issue) {
        TripIssue.NoFlights -> stringResource(R.string.issue_no_flights)
        is TripIssue.SameOriginAndDestination -> stringResource(R.string.issue_same_place)
        is TripIssue.ArrivalNotAfterDeparture -> stringResource(R.string.issue_arrival_before_departure)
        is TripIssue.ImplausiblyShort ->
            stringResource(R.string.issue_too_short, formatDuration(issue.duration), numbers.format(issue.distanceKm))
        is TripIssue.ImplausiblyLong ->
            stringResource(R.string.issue_too_long, formatDuration(issue.duration), numbers.format(issue.distanceKm))
        is TripIssue.LegsOverlap -> stringResource(R.string.issue_overlap, formatDuration(issue.overlap))
        is TripIssue.LegsNotConnected ->
            stringResource(R.string.issue_not_connected, issue.arrivedAt.displayCode, issue.departsFrom.displayCode)
        is TripIssue.LayoverTooShort -> stringResource(R.string.issue_layover_short, formatDuration(issue.layover))
        is TripIssue.LayoverTooLong -> stringResource(R.string.issue_layover_long, formatDuration(issue.layover))
        is TripIssue.CrossesDateLine -> stringResource(
            when {
                issue.calendarDayShift < 0 -> R.string.issue_date_line_back
                issue.calendarDayShift > 0 -> R.string.issue_date_line_forward
                else -> R.string.issue_date_line_same
            },
        )
    }
}

@Composable
private fun fixLabel(fix: IssueFix, currentArrival: LocalDateTime?): String = when (fix) {
    is IssueFix.ConnectFrom -> stringResource(R.string.fix_depart_from, fix.code)
    is IssueFix.Arrival -> {
        val days = currentArrival?.let { ChronoUnit.DAYS.between(it.toLocalDate(), fix.suggested.toLocalDate()) }
        when (days) {
            1L -> stringResource(R.string.fix_next_day)
            -1L -> stringResource(R.string.fix_previous_day)
            else -> {
                val dates = rememberDateFormatter()
                val times = rememberTimeFormatter()
                stringResource(
                    R.string.fix_arrival_at,
                    "${dates.format(fix.suggested.toLocalDate())} ${times.formatFull(fix.suggested.toLocalTime())}",
                )
            }
        }
    }
}
