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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
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
 * A trip as a rich, compact card: dot-matrix route banner (`LHR ──✈── SYD`) and overflow menu at the top,
 * route title and single-line dates + stops in the middle, and status, time-zone shift and adaptation summary
 * at the bottom. Past trips read as past through colour alone ([tripCardColors]): a lower surface, variant ink, a
 * faint route and a quiet chip, while every line of text keeps WCAG AA contrast (no layer alpha over the card).
 * The Trips list only shows upcoming and past trips (the trip under way is on Now).
 * A 100+/day surface, so it only gets the platform state layer (no bespoke motion).
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
    val isPast = summary.phase == TripPhase.Past
    val cardColors = tripCardColors(isPast, colors)
    val container = cardColors.container
    val content = cardColors.content
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
                .padding(start = 20.dp, top = 12.dp, end = 8.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RouteBanner(
                    summary = summary,
                    colors = cardColors,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
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
            val routeCities = stringResource(
                R.string.trip_route_cities,
                summary.trip.origin.cityLabel,
                summary.trip.destination.cityLabel,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                summary.title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp),
            )
            if (summary.title != routeCities) {
                Spacer(Modifier.height(2.dp))
                Text(
                    routeCities,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cardColors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            ScheduleAndStopsLine(summary = summary, color = cardColors.secondary, modifier = Modifier.padding(end = 12.dp))
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(summary, cardColors)
                ShiftPill(summary.shiftHours, content)
                summary.daysToAdapt?.let { days -> DaysToAdaptPill(days, content) }
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
        MenuItem(R.string.trip_action_delete, R.drawable.ic_trips_delete, TripsTestTags.MenuDelete, destructive = true) {
            onDismiss(); actions.onDelete()
        }
    }
}

/** A menu entry; [destructive] ones (Delete) take the error colour for both label and icon. */
@Composable
private fun MenuItem(label: Int, icon: Int, tag: String, destructive: Boolean = false, onClick: () -> Unit) {
    val error = MaterialTheme.colorScheme.error
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
        colors = if (destructive) MenuDefaults.itemColors(textColor = error, leadingIconColor = error) else MenuDefaults.itemColors(),
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun StatusChip(summary: TripSummary, colors: TripCardColors) {
    Surface(color = colors.chip, contentColor = colors.onChip, shape = MaterialTheme.shapes.small) {
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
    is TripStatus.PartlyAdapted -> stringResource(R.string.trip_status_partly_adapted, status.percent)
    TripStatus.StayedOnHomeTime -> stringResource(R.string.trip_status_home_time)
    TripStatus.Finished -> stringResource(R.string.trip_status_finished)
}

/**
 * **From ──✈── To** in dot-matrix codes over the card colour. While the trip is in the air the plane sits at the
 * flight's live progress (and TalkBack hears how far along it is); otherwise it rests at the start, or at the end
 * once landed. The arc's height follows the total travel time. Static: it only moves when progress changes.
 */
@Composable
private fun RouteBanner(summary: TripSummary, colors: TripCardColors, modifier: Modifier = Modifier) {
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
            colors = colors.arcAccent?.let { accent ->
                RouteArcDefaults.colors(
                    background = colors.container,
                    route = colors.route,
                    progress = accent,
                    origin = accent,
                    destination = accent,
                    plane = accent,
                    code = colors.content,
                    caption = colors.secondary,
                )
            } ?: RouteArcDefaults.colors(
                background = colors.container,
                route = colors.route,
                code = colors.content,
                caption = colors.secondary,
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

/**
 * "Thu, Apr 2, 10:05 LIS → Fri, Apr 3, 08:30 HND · nonstop": each time is local to its airport, and the airport's
 * code beside it says which zone that is (the card's banner names the cities).
 */
@Composable
private fun ScheduleAndStopsLine(summary: TripSummary, color: Color, modifier: Modifier = Modifier) {
    val trip = summary.trip
    val dates = rememberDateFormatter()
    val times = rememberTimeFormatter()
    val departLocal = trip.legs.first().departureLocal
    val arriveLocal = trip.legs.last().arrivalLocal
    val departDate = dates.format(departLocal.toLocalDate())
    val departTime = times.formatFull(departLocal.toLocalTime())
    val arriveDate = dates.format(arriveLocal.toLocalDate())
    val arriveTime = times.formatFull(arriveLocal.toLocalTime())
    val stops = stopsLine(summary)
    val departPart = stringResource(R.string.trip_time_at_place, departDate, departTime, trip.origin.displayCode)
    val arrivePart = if (departLocal.toLocalDate() == arriveLocal.toLocalDate()) {
        stringResource(R.string.trip_time_place, arriveTime, trip.destination.displayCode)
    } else {
        stringResource(R.string.trip_time_at_place, arriveDate, arriveTime, trip.destination.displayCode)
    }
    val line = stringResource(R.string.trip_schedule_line, departPart, arrivePart, stops)
    val description = listOf(
        stringResource(R.string.trip_departs_description, departDate, departTime, trip.origin.cityLabel),
        stringResource(R.string.trip_arrives_description, arriveDate, arriveTime, trip.destination.cityLabel),
        stops,
    ).joinToString(", ")

    Text(
        text = line,
        style = ClockblockTheme.textStyles.timeLabel,
        color = color,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    )
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
 * A trip card's colours. Upcoming: `surfaceContainerHigh` with `onSurface` ink, secondary lines at 86 % (still
 * WCAG AA, see `ContrastAuditTest` in :core:designsystem). Past: one step lower (`surfaceContainerLow`) with
 * `onSurfaceVariant` ink at full strength for every line of text, so the card reads as past by colour without its
 * text dropping below 4.5:1; the decorative route arc turns grey (`outline`) instead of the theme's accents.
 */
@Immutable
internal data class TripCardColors(
    val container: Color,
    val content: Color,
    val secondary: Color,
    val route: Color,
    /** The flown part of the arc, the end dots and the plane: the theme's accents, or greys on a past card. */
    val arcAccent: Color?,
    val chip: Color,
    val onChip: Color,
)

internal fun tripCardColors(isPast: Boolean, scheme: ColorScheme): TripCardColors = if (isPast) {
    TripCardColors(
        container = scheme.surfaceContainerLow,
        content = scheme.onSurfaceVariant,
        secondary = scheme.onSurfaceVariant,
        route = scheme.onSurfaceVariant.copy(alpha = PastRouteAlpha),
        arcAccent = scheme.outline,
        chip = scheme.surfaceContainerHighest,
        onChip = scheme.onSurfaceVariant,
    )
} else {
    TripCardColors(
        container = scheme.surfaceContainerHigh,
        content = scheme.onSurface,
        secondary = scheme.onSurface.copy(alpha = SecondaryContentAlpha),
        route = scheme.onSurface.copy(alpha = RouteAlpha),
        arcAccent = null,
        chip = scheme.secondaryContainer,
        onChip = scheme.onSecondaryContainer,
    )
}

/** Secondary lines on upcoming cards: 0.86 keeps them at WCAG AA (4.5:1); 0.72 fell to 4:1. */
internal const val SecondaryContentAlpha = 0.86f
private const val RouteAlpha = 0.5f
private const val PastRouteAlpha = 0.32f
