package dev.sebastiano.clockblocker.opus.feature.trips.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.component.WavyAdaptationIndicator
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.formatJetLagHours
import dev.sebastiano.clockblocker.opus.core.designsystem.component.RouteArcBanner
import dev.sebastiano.clockblocker.opus.core.designsystem.component.RouteArcDefaults
import dev.sebastiano.clockblocker.opus.core.designsystem.component.routeArcDescription
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.LocalReduceMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import dev.sebastiano.clockblocker.opus.feature.trips.ui.cityLabel
import dev.sebastiano.clockblocker.opus.feature.trips.ui.rememberDateFormatter
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** Card actions. Null callbacks hide their menu entry (e.g. when the host hasn't wired the editor). */
class TripCardActions(
    val onOpen: () -> Unit,
    val onEdit: (() -> Unit)?,
    val onDuplicate: () -> Unit,
    val onCreateReturn: (() -> Unit)?,
    val onDelete: () -> Unit,
)

/**
 * A trip as a rich card: status, time shift (and, for upcoming trips, the planner's days to adapt), route title,
 * a dot-matrix route banner, both ends in their own local time and, while under way, the wavy adaptation line. A 100+/day surface, so it only gets the platform
 * state layer (no bespoke motion).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TripCard(
    summary: TripSummary,
    selected: Boolean,
    actions: TripCardActions,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (summary.phase) {
        TripPhase.InProgress -> colors.primaryContainer to colors.onPrimaryContainer
        TripPhase.Upcoming -> colors.surfaceContainerHigh to colors.onSurface
        TripPhase.Past -> colors.surfaceContainerLow to colors.onSurface
    }
    val labels = rememberActionLabels()
    val customActions = buildList {
        actions.onEdit?.let { add(CustomAccessibilityAction(labels.edit) { it(); true }) }
        add(CustomAccessibilityAction(labels.duplicate) { actions.onDuplicate(); true })
        actions.onCreateReturn?.let { add(CustomAccessibilityAction(labels.createReturn) { it(); true }) }
        add(CustomAccessibilityAction(labels.delete) { actions.onDelete(); true })
    }

    Surface(
        modifier = modifier
            .testTag(TripsTestTags.tripCard(summary.id))
            .semantics {
                this.selected = selected
                this.customActions = customActions
            },
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
        border = if (selected) BorderStroke(3.dp, colors.primary) else null,
    ) {
        Column(
            Modifier
                .combinedClickable(
                    onClickLabel = labels.open,
                    onLongClickLabel = stringResource(R.string.trip_more_actions, summary.title),
                    onLongClick = { menuOpen = true },
                    onClick = actions.onOpen,
                )
                .padding(start = 20.dp, top = 12.dp, end = 8.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusChip(summary)
                    ShiftPill(summary.shiftHours, content)
                    summary.daysToAdapt?.let { days -> DaysToAdaptPill(days, content) }
                }
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.testTag(TripsTestTags.tripMenu(summary.id)),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_trips_more),
                            contentDescription = stringResource(R.string.trip_more_actions, summary.title),
                        )
                    }
                    TripMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, actions = actions)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                summary.title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp),
            )
            Spacer(Modifier.height(12.dp))
            RouteBanner(summary, container, content, Modifier.padding(end = 12.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                stopsLine(summary),
                style = MaterialTheme.typography.labelLarge,
                color = LocalContentColor.current.copy(alpha = SecondaryContentAlpha),
            )
            Spacer(Modifier.height(12.dp))
            val trip = summary.trip
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EndpointLine(
                    icon = R.drawable.ic_trips_takeoff,
                    local = trip.legs.first().departureLocal,
                    place = trip.origin,
                    departs = true,
                )
                EndpointLine(
                    icon = R.drawable.ic_trips_land,
                    local = trip.legs.last().arrivalLocal,
                    place = trip.destination,
                    departs = false,
                )
            }
            summary.adaptation?.let { adaptation ->
                Spacer(Modifier.height(16.dp))
                AdaptationRow(adaptation, Modifier.padding(end = 12.dp))
            }
        }
    }
}

@Composable
private fun TripMenu(expanded: Boolean, onDismiss: () -> Unit, actions: TripCardActions) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        actions.onEdit?.let { onEdit ->
            MenuItem(R.string.trip_action_edit, R.drawable.ic_trips_edit, TripsTestTags.MenuEdit) { onDismiss(); onEdit() }
        }
        MenuItem(R.string.trip_action_duplicate, R.drawable.ic_trips_copy, TripsTestTags.MenuDuplicate) {
            onDismiss(); actions.onDuplicate()
        }
        actions.onCreateReturn?.let { onReturn ->
            MenuItem(R.string.trip_action_return, R.drawable.ic_trips_return, TripsTestTags.MenuReturn) { onDismiss(); onReturn() }
        }
        MenuItem(R.string.trip_action_delete, R.drawable.ic_trips_delete, TripsTestTags.MenuDelete) {
            onDismiss(); actions.onDelete()
        }
    }
}

@Composable
private fun MenuItem(label: Int, icon: Int, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun StatusChip(summary: TripSummary) {
    val colors = MaterialTheme.colorScheme
    val (bg, fg) = when (summary.phase) {
        TripPhase.InProgress -> colors.primary to colors.onPrimary
        TripPhase.Upcoming -> colors.secondaryContainer to colors.onSecondaryContainer
        TripPhase.Past -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.small) {
        Text(
            statusLabel(summary.status),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** The chip text for a [TripStatus]. */
@Composable
internal fun statusLabel(status: TripStatus): String = when (status) {
    is TripStatus.StartsIn -> when (status.days) {
        0L -> stringResource(R.string.trip_status_today)
        1L -> stringResource(R.string.trip_status_tomorrow)
        else -> pluralStringResource(R.plurals.trip_status_starts_in, status.days.toInt(), status.days.toInt())
    }
    is TripStatus.Adjusting -> when (status.days) {
        0L -> stringResource(R.string.trip_status_adjusting_today)
        1L -> stringResource(R.string.trip_status_adjusting_tomorrow)
        else -> pluralStringResource(R.plurals.trip_status_adjusting, status.days.toInt(), status.days.toInt())
    }
    TripStatus.InTheAir -> stringResource(R.string.trip_status_in_the_air)
    is TripStatus.Day -> if (status.total != null) {
        stringResource(R.string.trip_status_day_of, status.number, status.total)
    } else {
        stringResource(R.string.trip_status_day, status.number)
    }
    TripStatus.Adapted -> stringResource(R.string.trip_status_adapted)
    TripStatus.StayedOnHomeTime -> stringResource(R.string.trip_status_home_time)
    TripStatus.Finished -> stringResource(R.string.trip_status_finished)
}

/**
 * **From ──✈── To** in dot-matrix codes over the card colour. While the trip is in the air the plane sits at the
 * flight's live progress (and TalkBack hears how far along it is); otherwise it rests at the start, or at the end
 * once landed. The arc's height follows the total travel time. Static: it only moves when progress changes.
 */
@Composable
private fun RouteBanner(summary: TripSummary, container: Color, content: Color, modifier: Modifier = Modifier) {
    val trip = summary.trip
    val origin = trip.origin.displayCode
    val destination = trip.destination.displayCode
    val route = routeArcDescription(origin, destination, trip.origin.cityLabel, trip.destination.cityLabel)
    val description = if (summary.status == TripStatus.InTheAir) {
        stringResource(R.string.trip_route_in_flight, route, (summary.flightProgress * 100).roundToInt())
    } else {
        route
    }
    // The list is a high-frequency surface (frequency gate): no bespoke motion, so the banner snaps to each minute's
    // progress instead of animating the plane. Scoped to the banner only.
    CompositionLocalProvider(LocalReduceMotion provides true) {
        RouteArcBanner(
            origin = origin,
            destination = destination,
            progress = summary.flightProgress,
            apex = RouteArcDefaults.apexForDuration(Duration.between(trip.departure, trip.arrival)),
            colors = RouteArcDefaults.colors(
                background = container,
                route = content.copy(alpha = 0.5f),
                code = content,
                caption = content.copy(alpha = SecondaryContentAlpha),
            ),
            contentDescription = description,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/** "Nonstop", or "1 stop · via SIN" (every connection, in order). */
@Composable
private fun stopsLine(summary: TripSummary): String {
    val legs = summary.trip.legs
    val stops = legs.size - 1
    if (stops == 0) return stringResource(R.string.trip_nonstop)
    val via = legs.dropLast(1).joinToString(", ") { it.destination.displayCode }
    return "${pluralStringResource(R.plurals.trip_stops, stops, stops)} · ${stringResource(R.string.trip_via, via)}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EndpointLine(icon: Int, local: LocalDateTime, place: Place, departs: Boolean) {
    val dates = rememberDateFormatter()
    val times = rememberTimeFormatter()
    val date = dates.format(local.toLocalDate())
    val time = times.formatFull(local.toLocalTime())
    val description = stringResource(
        if (departs) R.string.trip_departs_description else R.string.trip_arrives_description,
        date,
        time,
        place.cityLabel,
    )
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.trip_time_at, date, time),
                style = ClockblockTheme.textStyles.timeLabel,
            )
            Text(
                place.cityLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = SecondaryContentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShiftPill(hours: Float, content: Color) {
    val label = when {
        abs(hours) < 0.25f -> stringResource(R.string.trip_shift_none)
        hours > 0 -> stringResource(R.string.trip_shift_east, formatJetLagHours(hours))
        else -> stringResource(R.string.trip_shift_west, formatJetLagHours(hours))
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, content.copy(alpha = 0.32f)),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

/** The planner's estimate for an upcoming trip, styled like [ShiftPill] so the two read as a pair of facts. */
@Composable
private fun DaysToAdaptPill(days: Double, content: Color) {
    val rounded = days.roundToInt().coerceAtLeast(1)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, content.copy(alpha = 0.32f)),
    ) {
        Text(
            pluralStringResource(R.plurals.trip_days_to_adapt, rounded, rounded),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun AdaptationRow(adaptation: AdaptationSnapshot, modifier: Modifier = Modifier) {
    val percent = (adaptation.progress * 100).roundToInt()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer) {
            Text(stringResource(R.string.trip_adapted_percent, percent), style = MaterialTheme.typography.labelLarge)
        }
        WavyAdaptationIndicator(
            progress = adaptation.progress,
            misalignment = adaptation.misalignment,
            modifier = Modifier.fillMaxWidth(),
            remaining = formatJetLagHours(adaptation.remainingHours).removePrefix("+"),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        )
    }
}

private class ActionLabels(
    val open: String,
    val edit: String,
    val duplicate: String,
    val createReturn: String,
    val delete: String,
)

@Composable
private fun rememberActionLabels(): ActionLabels = ActionLabels(
    open = stringResource(R.string.trip_action_open),
    edit = stringResource(R.string.trip_action_edit),
    duplicate = stringResource(R.string.trip_action_duplicate),
    createReturn = stringResource(R.string.trip_action_return),
    delete = stringResource(R.string.trip_action_delete),
)

/**
 * Secondary lines (route codes, endpoint cities) on every card colour. 0.86 keeps them at WCAG AA (4.5:1) on the
 * in-progress `primaryContainer`, where 0.72 fell to 4:1 (see `ContrastAuditTest` in :core:designsystem).
 */
private const val SecondaryContentAlpha = 0.86f
