package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.data.trip.IssueSeverity
import dev.sebastiano.clockblocker.opus.core.data.trip.TripIssue
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import dev.sebastiano.clockblocker.opus.feature.trips.ui.cityLabel
import dev.sebastiano.clockblocker.opus.feature.trips.ui.formatDuration
import dev.sebastiano.clockblocker.opus.feature.trips.ui.rememberDateFormatter
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Focus targets of one leg, so IME actions and picks can move the user along. */
internal class LegFocus {
    val from = FocusRequester()
    val to = FocusRequester()
    val departureDate = FocusRequester()
    val flightNumber = FocusRequester()
}

/**
 * One flight as a card: From → departure (local time at that airport) → a flight strip with the duration →
 * To → arrival (local time there) → optional flight number → inline issues with fixes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LegCard(
    index: Int,
    leg: LegDraft,
    legCount: Int,
    issues: List<TripIssue>,
    search: PlaceSearchState?,
    now: Instant,
    focus: LegFocus,
    actions: TripEditorActions,
    onPick: (PickerTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val dates = rememberDateFormatter()
    val times = rememberTimeFormatter()
    val hasArrivalError = issues.any {
        it.severity == IssueSeverity.Error && (it is TripIssue.ArrivalNotAfterDeparture)
    }
    val fromRef = PlaceFieldRef(index, LegEnd.Origin)
    val toRef = PlaceFieldRef(index, LegEnd.Destination)

    Surface(
        modifier = modifier.fillMaxWidth().testTag(TripsTestTags.editorLeg(index)),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.editor_leg_title, index + 1),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (legCount > 1) {
                    IconButton(
                        onClick = { actions.removeLeg(index) },
                        modifier = Modifier.testTag(TripsTestTags.editorRemoveLeg(index)),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_trips_delete),
                            contentDescription = stringResource(R.string.editor_leg_remove, index + 1),
                        )
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }

            PlaceField(
                label = stringResource(R.string.editor_from),
                input = leg.origin,
                search = search?.takeIf { it.field == fromRef },
                now = now,
                onQueryChange = { actions.onPlaceQueryChange(fromRef, it) },
                onSelect = { place ->
                    actions.onPlaceSelected(fromRef, place)
                    focus.to.requestFocus()
                },
                onImeAction = {
                    actions.pickTopResult(fromRef)
                    focus.to.requestFocus()
                },
                tag = TripsTestTags.editorFrom(index),
                focusRequester = focus.from,
            )
            Spacer(Modifier.height(8.dp))
            TimeRow(
                caption = stringResource(R.string.editor_departs),
                place = leg.origin.place,
                originSide = true,
                dateText = leg.departureDate?.let(dates::format),
                timeText = leg.departureTime?.let(times::formatFull),
                dateTag = TripsTestTags.editorDepartureDate(index),
                timeTag = TripsTestTags.editorDepartureTime(index),
                onPickDate = { onPick(PickerTarget.DepartureDate(index)) },
                onPickTime = { onPick(PickerTarget.DepartureTime(index)) },
                dateModifier = Modifier.focusRequester(focus.departureDate),
            )

            FlightStrip(leg)

            PlaceField(
                label = stringResource(R.string.editor_to),
                input = leg.destination,
                search = search?.takeIf { it.field == toRef },
                now = now,
                onQueryChange = { actions.onPlaceQueryChange(toRef, it) },
                onSelect = { place ->
                    actions.onPlaceSelected(toRef, place)
                    focus.departureDate.requestFocus()
                },
                onImeAction = {
                    actions.pickTopResult(toRef)
                    focus.departureDate.requestFocus()
                },
                tag = TripsTestTags.editorTo(index),
                focusRequester = focus.to,
            )
            Spacer(Modifier.height(8.dp))
            TimeRow(
                caption = stringResource(R.string.editor_arrives),
                place = leg.destination.place,
                originSide = false,
                dateText = leg.arrivalDate?.let(dates::format),
                timeText = leg.arrivalTime?.let(times::formatFull),
                dateTag = TripsTestTags.editorArrivalDate(index),
                timeTag = TripsTestTags.editorArrivalTime(index),
                onPickDate = { onPick(PickerTarget.ArrivalDate(index)) },
                onPickTime = { onPick(PickerTarget.ArrivalTime(index)) },
                isError = hasArrivalError,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = leg.flightNumber,
                onValueChange = { actions.onFlightNumberChange(index, it) },
                label = { Text(stringResource(R.string.editor_flight_number)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_trips_flight), contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus.flightNumber)
                    .testTag(TripsTestTags.editorFlightNumber(index)),
            )
            if (issues.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    issues.forEach { issue ->
                        IssueBanner(
                            issue = issue,
                            currentArrival = leg.arrivalLocal,
                            onFix = { fix ->
                                when (fix) {
                                    is IssueFix.Arrival -> actions.applySuggestedArrival(index, fix.suggested)
                                    is IssueFix.ConnectFrom -> actions.useConnectingOrigin(index)
                                }
                            },
                            tagIndex = index,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRow(
    caption: String,
    place: Place?,
    originSide: Boolean,
    dateText: String?,
    timeText: String?,
    dateTag: String,
    timeTag: String,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    dateModifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val zoneLabel = when {
        place != null -> stringResource(R.string.editor_local_time_in, place.cityLabel)
        originSide -> stringResource(R.string.editor_local_time_origin)
        else -> stringResource(R.string.editor_local_time_destination)
    }
    Column {
        Text(
            "$caption · $zoneLabel",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        // Same height for both fields: their values measure slightly differently (dates vs digits).
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerField(
                label = stringResource(R.string.editor_date),
                accessibilityLabel = "$caption, ${stringResource(R.string.editor_date)}",
                value = dateText,
                icon = R.drawable.ic_trips_event,
                onClick = onPickDate,
                pickLabel = stringResource(R.string.editor_pick_date),
                tag = dateTag,
                modifier = dateModifier.weight(1.35f).fillMaxHeight(),
                isError = isError,
            )
            PickerField(
                label = stringResource(R.string.editor_time),
                accessibilityLabel = "$caption, ${stringResource(R.string.editor_time)}, $zoneLabel",
                value = timeText,
                icon = R.drawable.ic_trips_schedule,
                onClick = onPickTime,
                pickLabel = stringResource(R.string.editor_pick_time),
                tag = timeTag,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                isError = isError,
            )
        }
    }
}

/** The bit between the two airports: a dashed flight path with the block time and any day change. */
@Composable
private fun FlightStrip(leg: LegDraft) {
    val flight = leg.toFlightLeg()
    val color = MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(width = 2.dp, height = 36.dp)) {
                drawLine(
                    color = color,
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = size.width,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
                )
            }
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
                Icon(
                    painterResource(R.drawable.ic_trips_flight),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).padding(1.dp),
                )
            }
        }
        if (flight != null && flight.duration > Duration.ZERO) {
            val dayShift = ChronoUnit.DAYS.between(flight.departureLocal.toLocalDate(), flight.arrivalLocal.toLocalDate())
            val parts = buildList {
                add(stringResource(R.string.editor_flight_duration, formatDuration(flight.duration)))
                when {
                    dayShift == 1L -> add(stringResource(R.string.editor_day_next))
                    dayShift == -1L -> add(stringResource(R.string.editor_day_previous))
                    dayShift > 1 -> add(pluralStringResource(R.plurals.editor_day_later, dayShift.toInt(), dayShift.toInt()))
                }
            }
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
