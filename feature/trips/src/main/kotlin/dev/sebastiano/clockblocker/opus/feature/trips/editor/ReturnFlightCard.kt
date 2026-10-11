package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import dev.sebastiano.clockblocker.opus.feature.trips.ui.cityLabel
import dev.sebastiano.clockblocker.opus.feature.trips.ui.rememberDateFormatter

/**
 * "Return flight · One way / not sure yet": a switch right on the editor. On, it asks when you fly back (local time
 * where the trip ends, a week after landing suggested) and Save also adds the return trip, the way Create return trip
 * does. Editing a trip that already flies back only moves the date. The date row grows in on the container token
 * (a rare setup choice); under reduce motion it simply appears, and the switch carries the state either way.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReturnFlightCard(
    state: TripEditorUiState,
    onToggle: (Boolean) -> Unit,
    onPick: (PickerTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val on = state.form.returnFlight
    val origin = state.legs.firstOrNull()?.origin?.place
    val destination = state.legs.lastOrNull()?.destination?.place
    val motion = ClockblockTheme.motion
    val dates = rememberDateFormatter()
    val times = rememberTimeFormatter()
    val route = if (origin != null && destination != null) {
        stringResource(R.string.editor_return_route, destination.cityLabel, origin.cityLabel)
    } else {
        null
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
                    .testTag(TripsTestTags.EditorReturnSwitch)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.editor_return_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !on -> stringResource(R.string.editor_return_off)
                            route != null -> route
                            else -> stringResource(R.string.editor_return_on)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = on, onCheckedChange = null)
            }
            AnimatedVisibility(
                visible = on,
                enter = expandVertically(motion.containerSpatial()) + fadeIn(motion.fade()),
                exit = shrinkVertically(motion.containerSpatial()) + fadeOut(motion.fade()),
            ) {
                val zone = destination?.let { stringResource(R.string.editor_local_time_in, it.cityLabel) }
                    ?: stringResource(R.string.editor_local_time_destination)
                val caption = stringResource(R.string.editor_return_flies)
                val error = state.returnLeavesBeforeArrival
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                    Text(
                        "$caption · $zone",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TicketChip(
                            value = state.form.returnDate?.let(dates::format),
                            placeholder = stringResource(R.string.editor_pick_date),
                            icon = R.drawable.ic_trips_event,
                            accessibilityLabel = "$caption, ${stringResource(R.string.editor_date)}",
                            onClick = { onPick(PickerTarget.ReturnDate) },
                            tag = TripsTestTags.EditorReturnDate,
                            isError = error,
                        )
                        TicketChip(
                            value = state.form.returnTime?.takeIf { state.form.returnDate != null }?.let(times::formatFull),
                            placeholder = stringResource(R.string.editor_pick_time),
                            icon = R.drawable.ic_trips_schedule,
                            accessibilityLabel = "$caption, ${stringResource(R.string.editor_time)}, $zone",
                            onClick = { onPick(PickerTarget.ReturnTime) },
                            tag = TripsTestTags.EditorReturnTime,
                            isError = error,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            error -> stringResource(R.string.editor_return_before_landing, destination?.cityLabel.orEmpty())
                            state.createsReturnTrip -> stringResource(R.string.editor_return_creates)
                            else -> stringResource(R.string.editor_return_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
