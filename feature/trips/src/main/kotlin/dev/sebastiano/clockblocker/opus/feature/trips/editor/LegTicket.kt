package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import dev.sebastiano.clockblocker.opus.core.data.trip.IssueSeverity
import dev.sebastiano.clockblocker.opus.core.data.trip.TripIssue
import dev.sebastiano.clockblocker.opus.core.data.trip.TripValidator
import dev.sebastiano.clockblocker.opus.core.designsystem.component.IataCode
import dev.sebastiano.clockblocker.opus.core.designsystem.component.RouteArc
import dev.sebastiano.clockblocker.opus.core.designsystem.component.RouteArcDefaults
import dev.sebastiano.clockblocker.opus.core.designsystem.component.TicketShape
import dev.sebastiano.clockblocker.opus.core.designsystem.component.iataCodeWidthPx
import dev.sebastiano.clockblocker.opus.core.designsystem.component.spellOut
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.DotMatrixStyle
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import dev.sebastiano.clockblocker.opus.feature.trips.ui.cityLabel
import dev.sebastiano.clockblocker.opus.feature.trips.ui.formatDuration
import dev.sebastiano.clockblocker.opus.feature.trips.ui.rememberDateFormatter
import dev.sebastiano.clockblocker.opus.feature.trips.ui.utcOffsetLabel
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import kotlin.math.abs

/**
 * One flight as a ticket (the editor's boarding pass): FROM and TO as big dot-matrix codes with the city, the airport's
 * name and its UTC offset under each (at that end's flight time, once a date is known: offsets change with DST),
 * joined by the route arc and a swap button; departure and arrival as compact date and time chips (local time at
 * each airport), with "+1 day" in the attention colour when the flight lands on another calendar day; inline issues
 * with their fixes; and, past the perforation, the stub: time in the air and an optional flight number.
 *
 * Tapping a code opens the airport search sheet ([onPickPlace]); a chip opens its picker ([onPick]). The "Flight 1"
 * heading (with the remove button) only shows once the trip has a connection.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LegTicket(
    index: Int,
    leg: LegDraft,
    legCount: Int,
    issues: List<TripIssue>,
    actions: TripEditorActions,
    onPick: (PickerTarget) -> Unit,
    onPickPlace: (PlaceFieldRef) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    handle: TicketHandle? = null,
    onActive: () -> Unit = {},
) {
    val currentOnActive by rememberUpdatedState(onActive)
    val dates = rememberDateFormatter()
    val times = rememberTimeFormatter()
    val hasArrivalError = issues.any { it.severity == IssueSeverity.Error && it is TripIssue.ArrivalNotAfterDeparture }
    val density = LocalDensity.current
    var stubHeight by remember { mutableStateOf(0.dp) }

    Column(
        modifier
            .fillMaxWidth()
            .then(if (handle != null) Modifier.bringIntoViewRequester(handle.bringIntoView) else Modifier)
            // Any touch on the ticket, or focus inside it, makes it the active one (the globe follows). Observed on
            // the way in, never consumed, so the ticket's own controls work as before.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    currentOnActive()
                }
            }
            .onFocusChanged { if (it.hasFocus) currentOnActive() },
    ) {
        if (legCount > 1) {
            Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.editor_leg_title, index + 1),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = { actions.removeLeg(index) }, modifier = Modifier.testTag(TripsTestTags.editorRemoveLeg(index))) {
                    Icon(painterResource(R.drawable.ic_trips_delete), contentDescription = stringResource(R.string.editor_leg_remove, index + 1))
                }
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TripsTestTags.editorLeg(index))
                // With a connection, TalkBack hears which ticket the globe is showing.
                .then(if (legCount > 1) Modifier.semantics { selected = active } else Modifier),
            shape = TicketShape(stubHeight = stubHeight, corner = TicketCorner, notch = TicketNotch),
            color = MaterialTheme.colorScheme.surfaceContainer,
            // With a connection, the ticket the globe highlights is outlined too, so the two read as one choice.
            border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        ) {
            Column {
                Column(Modifier.padding(start = TicketPadding, end = TicketPadding, top = 16.dp, bottom = 16.dp)) {
                    RouteRow(index, leg, onPickPlace, onSwap = { actions.swapPlaces(index) }, fromFocus = handle?.focus)
                    Spacer(Modifier.height(16.dp))
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
                    )
                    Spacer(Modifier.height(12.dp))
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
                        estimated = leg.arrivalEstimated,
                        dayOffset = leg.arrivalDayOffset?.takeIf { it != 0 && leg.arrivalTime != null },
                        dayOffsetTag = TripsTestTags.editorDayOffset(index),
                    )
                    if (leg.arrivalEstimated) {
                        Text(
                            stringResource(R.string.editor_arrival_estimated),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp).testTag(TripsTestTags.editorArrivalEstimate(index)),
                        )
                    }
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
                // The stub: its measured height places the notches on the perforation, whatever the font scale.
                Column(Modifier.onSizeChanged { stubHeight = with(density) { it.height.toDp() } }) {
                    Perforation(Modifier.fillMaxWidth().padding(horizontal = TicketNotch + 8.dp))
                    TicketStub(index, leg, actions)
                }
            }
        }
    }
}

/** What the editor needs to reach a ticket from the globe: scroll it into view and focus its first control. */
internal class TicketHandle {
    val bringIntoView = BringIntoViewRequester()
    val focus = FocusRequester()
}

private val TicketCorner = 28.dp
private val TicketNotch = 12.dp
private val TicketPadding = 20.dp

/** The big codes on the ticket: larger than the cards' [ClockblockTheme.textStyles] `iataDisplay`. */
private val TicketCodeStyle = DotMatrixStyle(glyphHeight = 34.sp)

/** Where the editor's preview plane sits once both airports are set (mid-route: it's a preview, not progress). */
private const val TicketPlanePosition = 0.5f

/**
 * FROM ──✈── TO: each end is a button (label, code, city) that opens the airport search; the arc runs between the
 * two codes, with the swap button centred under it, between the city names.
 */
@Composable
private fun RouteRow(
    index: Int,
    leg: LegDraft,
    onPickPlace: (PlaceFieldRef) -> Unit,
    onSwap: () -> Unit,
    fromFocus: FocusRequester? = null,
) {
    val density = LocalDensity.current
    val from = leg.origin.place
    val to = leg.destination.place
    val labelStyle = MaterialTheme.typography.labelMedium
    val codeHeight = with(density) { TicketCodeStyle.glyphHeight.toDp() }
    val labelHeight = with(density) { labelStyle.lineHeight.toDp() }
    val arcHeight = max(RouteArcDefaults.ArcHeight, codeHeight + 20.dp)
    val apex = if (from != null && to != null) RouteArcDefaults.apexForDistance(TripValidator.distanceKm(from, to)) else 1f
    val container = MaterialTheme.colorScheme.surfaceContainer
    val fromCodeWidth = iataCodeWidthPx(from?.displayCode, TicketCodeStyle, density)
    val toCodeWidth = iataCodeWidthPx(to?.displayCode, TicketCodeStyle, density)

    Layout(
        contents = listOf(
            {
                RouteArc(
                    hasOrigin = from != null,
                    hasDestination = to != null,
                    codeHeight = codeHeight,
                    progress = TicketPlanePosition,
                    apex = apex,
                    colors = RouteArcDefaults.colors(background = container),
                )
            },
            {
                RouteEnd(
                    label = stringResource(R.string.editor_from),
                    place = from,
                    at = from?.let { leg.departureAt(it) },
                    alignEnd = false,
                    tag = TripsTestTags.editorFrom(index),
                    onClick = { onPickPlace(PlaceFieldRef(index, LegEnd.Origin)) },
                    focusRequester = fromFocus,
                )
            },
            {
                RouteEnd(
                    label = stringResource(R.string.editor_to),
                    place = to,
                    at = to?.let { leg.arrivalAt(it) },
                    alignEnd = true,
                    tag = TripsTestTags.editorTo(index),
                    onClick = { onPickPlace(PlaceFieldRef(index, LegEnd.Destination)) },
                )
            },
            { SwapButton(index, enabled = from != null || to != null, onSwap = onSwap) },
        ),
    ) { (arcs, froms, tos, swaps), constraints ->
        val width = constraints.maxWidth
        val swapGap = SwapClearance.roundToPx()
        // Each end may take up to the middle minus room for the swap button, so long city names wrap rather than
        // run under it.
        val endMax = ((width - swapGap) / 2).coerceAtLeast(0)
        val endConstraints = Constraints(maxWidth = endMax)
        val fromPlaceable = froms.single().measure(endConstraints)
        val toPlaceable = tos.single().measure(endConstraints)
        val swapPlaceable = swaps.single().measure(Constraints())
        val inset = EndPadding.roundToPx()
        val codeBottom = inset + labelHeight.roundToPx() + LabelGap.roundToPx() + codeHeight.roundToPx()
        val gap = ArcGap.roundToPx()
        val arcStart = inset + fromCodeWidth + gap
        val arcEnd = width - inset - toCodeWidth - gap
        val arcWidth = (arcEnd - arcStart).coerceAtLeast(0)
        val arcPlaceable = arcs.single().measure(Constraints.fixed(arcWidth, arcHeight.roundToPx()))
        val swapTop = codeBottom + SwapTopGap.roundToPx()
        val height = maxOf(fromPlaceable.height, toPlaceable.height, swapTop + swapPlaceable.height)
        layout(width, height) {
            // The arc first, so the two end buttons sit above it and take the touches.
            arcPlaceable.placeRelative(arcStart, codeBottom - arcPlaceable.height)
            fromPlaceable.placeRelative(0, 0)
            toPlaceable.placeRelative(width - toPlaceable.width, 0)
            swapPlaceable.placeRelative((width - swapPlaceable.width) / 2, swapTop)
        }
    }
}

private val EndPadding = 8.dp
private val LabelGap = 2.dp
private val ArcGap = 6.dp
private val SwapTopGap = 2.dp
private val SwapClearance = 56.dp

/**
 * One end of the route: "FROM", the code (or `· · ·`), the city (or "Choose airport"), and under it the airport's name
 * (when it isn't just the city, so LHR and LGW tell apart) with the zone's UTC offset at [at]: "Heathrow · GMT+1". A
 * button that opens the airport search; TalkBack reads "From, London, L H R, London Heathrow Airport, GMT+1", with
 * "Change airport" as the action.
 */
@Composable
private fun RouteEnd(
    label: String,
    place: Place?,
    at: Instant?,
    alignEnd: Boolean,
    tag: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val motion = ClockblockTheme.motion
    val colors = MaterialTheme.colorScheme
    val codeColor by animateColorAsState(if (place != null) colors.onSurface else colors.onSurfaceVariant, motion.colour(), label = "code")
    val city = place?.cityLabel
    val airport = place?.airportLabel
    val offset = at?.let { place?.utcOffsetLabel(it) }
    val details = listOfNotNull(airport, offset).takeIf { it.isNotEmpty() }?.joinToString(DetailsSeparator)
    val actionLabel = stringResource(if (place != null) R.string.editor_place_change else R.string.editor_place_choose)
    val description = listOfNotNull(label, city, place?.displayCode?.let(::spellOut), airport, offset).joinToString(", ")
    Column(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
            .testTag(tag)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(EndPadding),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(LabelGap))
        IataCode(
            place?.displayCode,
            style = TicketCodeStyle,
            color = codeColor,
            contentDescription = null,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            city ?: actionLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = if (city != null) colors.onSurface else colors.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
        if (details != null) {
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                modifier = Modifier.testTag(TripsTestTags.EditorPlaceDetails),
            )
        }
    }
}

private const val DetailsSeparator = " · "

/** When the leg leaves [origin], as far as it's known (noon until a time is picked); null without a date. */
private fun LegDraft.departureAt(origin: Place): Instant? =
    departureDate?.atTime(departureTime ?: LocalTime.NOON)?.atZone(origin.zone)?.toInstant()

/** When the leg lands at [destination] (its departure date until an arrival date is known); null without a date. */
private fun LegDraft.arrivalAt(destination: Place): Instant? =
    (arrivalDate ?: departureDate)?.atTime(arrivalTime ?: LocalTime.NOON)?.atZone(destination.zone)?.toInstant()

/**
 * The airport's name for the ticket, without what the ticket already says: null for a plain city or when the name is
 * just the city; "Heathrow" for "London Heathrow Airport" in London, "Humberto Delgado" for "Lisbon Humberto Delgado
 * Airport".
 */
internal val Place.airportLabel: String?
    get() {
        if (code.isBlank()) return null
        val trimmed = name.trim()
            .removeSuffix(" International Airport").removeSuffix(" Airport")
            .let { n -> if (city.isNotBlank() && n.startsWith("$city ", ignoreCase = true)) n.substring(city.length + 1) else n }
            .trim()
        return trimmed.takeIf { it.isNotBlank() && it.folded() != city.folded() }
    }

/** Case- and accent-insensitive form, so "Zürich" (the airport) matches "Zurich" (the city). */
private fun String.folded(): String =
    java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/**
 * Swaps From and To. Each tap turns the arrows half a turn (a state change, so the non-bouncy data token; snaps
 * under reduce motion, and the swapped codes carry the result anyway).
 */
@Composable
private fun SwapButton(index: Int, enabled: Boolean, onSwap: () -> Unit) {
    var turns by rememberSaveable { mutableIntStateOf(0) }
    val rotation = animateFloatAsState(turns * 180f, ClockblockTheme.motion.dataSpatial(), label = "swap")
    IconButton(
        onClick = {
            turns++
            onSwap()
        },
        enabled = enabled,
        modifier = Modifier.testTag(TripsTestTags.editorSwap(index)),
    ) {
        Icon(
            painterResource(R.drawable.ic_trips_swap),
            contentDescription = stringResource(R.string.editor_swap),
            modifier = Modifier.graphicsLayer { rotationZ = 90f + rotation.value },
        )
    }
}

/** "Departs · local time in Lisbon", then the date and time chips (and, on arrival, the day offset). */
@OptIn(ExperimentalLayoutApi::class)
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
    isError: Boolean = false,
    estimated: Boolean = false,
    dayOffset: Int? = null,
    dayOffsetTag: String? = null,
) {
    val estimatedLabel = if (estimated) ", ${stringResource(R.string.editor_arrival_estimated_short)}" else ""
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
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            TicketChip(
                value = dateText,
                placeholder = stringResource(R.string.editor_pick_date),
                icon = R.drawable.ic_trips_event,
                accessibilityLabel = "$caption, ${stringResource(R.string.editor_date)}$estimatedLabel",
                onClick = onPickDate,
                tag = dateTag,
                isError = isError,
            )
            TicketChip(
                value = timeText,
                placeholder = stringResource(R.string.editor_pick_time),
                icon = R.drawable.ic_trips_schedule,
                accessibilityLabel = "$caption, ${stringResource(R.string.editor_time)}, $zoneLabel$estimatedLabel",
                onClick = onPickTime,
                tag = timeTag,
                isError = isError,
            )
            if (dayOffset != null) DayOffsetBadge(dayOffset, dayOffsetTag)
        }
    }
}

/**
 * A compact tonal chip that opens a date or time picker: filled once set, outlined while empty ("Pick date"),
 * error-tinted when the value is part of an issue. 40 dp tall in a 48 dp touch target; TalkBack reads
 * "Departs, Date: Wed, Oct 7" with "Pick date" as the action.
 */
@Composable
internal fun TicketChip(
    value: String?,
    placeholder: String,
    icon: Int,
    accessibilityLabel: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.editor_field_value, accessibilityLabel, value ?: placeholder)
    val (container, content) = when {
        isError -> colors.errorContainer to colors.onErrorContainer
        value != null -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> Color.Transparent to colors.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 40.dp)
            .testTag(tag)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = placeholder) { onClick(); true }
            },
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border = when {
            isError -> BorderStroke(1.dp, colors.error)
            value == null -> BorderStroke(1.dp, colors.outline)
            else -> null
        },
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                value ?: placeholder,
                style = if (value != null) ClockblockTheme.textStyles.timeLabel else MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

/**
 * "+1 day" (or "−1 day", "+2 days") beside the arrival, in the attention colour: the arrival is on another calendar
 * day than the departure, local to each airport. TalkBack reads it in words ("Lands the next day").
 */
@Composable
private fun DayOffsetBadge(days: Int, tag: String?) {
    val magnitude = abs(days)
    val text = if (days > 0) {
        pluralStringResource(R.plurals.editor_day_offset_later, magnitude, magnitude)
    } else {
        pluralStringResource(R.plurals.editor_day_offset_earlier, magnitude, magnitude)
    }
    val spoken = when {
        days == 1 -> stringResource(R.string.editor_day_next)
        days == -1 -> stringResource(R.string.editor_day_previous)
        days > 1 -> pluralStringResource(R.plurals.editor_day_later, days, days)
        else -> pluralStringResource(R.plurals.editor_day_earlier, magnitude, magnitude)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clearAndSetSemantics { contentDescription = spoken },
    )
}

/** The dashed tear line between the notches. */
@Composable
private fun Perforation(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
    }
}

/** The ticket's stub: "10 h 50 m in the air" (once the times are in) and the optional flight number. */
@Composable
private fun TicketStub(index: Int, leg: LegDraft, actions: TripEditorActions) {
    val flight = leg.toFlightLeg()
    Row(
        Modifier.fillMaxWidth().padding(start = TicketPadding, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f)) {
            if (flight != null && flight.duration > Duration.ZERO) {
                Text(
                    stringResource(R.string.editor_flight_duration, formatDuration(flight.duration)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TripsTestTags.editorFlightDuration(index)),
                )
            }
        }
        FlightNumberField(
            value = leg.flightNumber,
            onValueChange = { actions.onFlightNumberChange(index, it) },
            tag = TripsTestTags.editorFlightNumber(index),
        )
    }
}

/** A chip-sized text field for the optional flight number ("TP 80"), shown as a tag on the stub. */
@Composable
private fun FlightNumberField(value: String, onValueChange: (String) -> Unit, tag: String) {
    val focusManager = LocalFocusManager.current
    val label = stringResource(R.string.editor_flight_number)
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = ClockblockTheme.textStyles.timeLabel.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier
            .widthIn(min = FlightNumberMinWidth, max = FlightNumberMaxWidth)
            .heightIn(min = 48.dp)
            .testTag(tag)
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerHighest) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_trips_flight),
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Box(Modifier.weight(1f, fill = false)) {
                        if (value.isEmpty()) {
                            Text(
                                stringResource(R.string.editor_flight_number_short),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                }
            }
        },
    )
}

private val FlightNumberMinWidth = 112.dp
private val FlightNumberMaxWidth = 168.dp
