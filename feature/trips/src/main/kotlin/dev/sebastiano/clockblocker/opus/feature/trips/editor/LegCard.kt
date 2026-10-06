package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
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
import dev.sebastiano.clockblocker.opus.core.data.trip.TripValidator
import dev.sebastiano.clockblocker.opus.core.designsystem.component.RouteArcBanner
import dev.sebastiano.clockblocker.opus.core.designsystem.component.RouteArcDefaults
import dev.sebastiano.clockblocker.opus.core.designsystem.component.celestialPosition
import dev.sebastiano.clockblocker.opus.core.designsystem.component.contentColor
import dev.sebastiano.clockblocker.opus.core.designsystem.component.routeArcDescription
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.toHourFloat
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
 * One flight as a boarding-pass card: a sky banner with the route in dot-matrix codes (painted for the departure
 * time of day), the From/To pair with a swap button, departure (local time at that airport) → a flight strip with the
 * duration → arrival (local time there, estimated from the distance until the user sets it), optional flight number,
 * and inline issues with fixes.
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

            SkyRouteBanner(leg)
            Spacer(Modifier.height(12.dp))
            RoutePair(index, leg, search, now, focus, actions)
            Spacer(Modifier.height(12.dp))
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
            )
            if (leg.arrivalEstimated) {
                Text(
                    stringResource(R.string.editor_arrival_estimated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp).testTag(TripsTestTags.editorArrivalEstimate(index)),
                )
            }
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

/**
 * The boarding-pass header: **From ──✈── To** in dot-matrix codes ([RouteArcBanner]) on a sky painted for the
 * departure time of day, so a 09:00 and a 21:00 flight look different at a glance (a check on AM/PM). Without a
 * departure time the banner is a plain tonal surface. The sky cross-fades with the colour token, read in the draw
 * phase; the ink snaps to stay legible. The times themselves are in the fields below, so the sky adds no meaning
 * that motion or colour alone would carry.
 */
@Composable
private fun SkyRouteBanner(leg: LegDraft, modifier: Modifier = Modifier) {
    val motion = OpusTheme.motion
    val time = leg.departureTime
    val gradient = time?.let { OpusTheme.sky.gradientAt(it) }
    val neutral = MaterialTheme.colorScheme.surfaceContainerHigh
    val top = animateColorAsState(gradient?.top ?: neutral, motion.colour(), label = "skyTop")
    val bottom = animateColorAsState(gradient?.bottom ?: neutral, motion.colour(), label = "skyBottom")
    val night = time != null && !celestialPosition(time.toHourFloat()).isSun
    val stars = animateFloatAsState(if (night) 0.7f else 0f, motion.colour(), label = "stars")
    val ink = gradient?.contentColor() ?: MaterialTheme.colorScheme.onSurface
    val from = leg.origin.place
    val to = leg.destination.place
    val apex = if (from != null && to != null) RouteArcDefaults.apexForDistance(TripValidator.distanceKm(from, to)) else 1f
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(top.value, bottom.value)))
                val a = stars.value
                if (a > 0f) {
                    val r = 1.2.dp.toPx()
                    BannerStars.forEachIndexed { i, (x, y) ->
                        drawCircle(Color.White.copy(alpha = a), if (i % 3 == 0) r * 1.5f else r, Offset(size.width * x, size.height * y))
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        RouteArcBanner(
            origin = from?.displayCode,
            destination = to?.displayCode,
            progress = BannerPlanePosition,
            apex = apex,
            colors = RouteArcDefaults.colors(
                background = gradient?.mid ?: neutral,
                route = ink.copy(alpha = 0.5f),
                progress = ink.copy(alpha = 0.7f),
                origin = ink,
                destination = ink,
                plane = ink,
                code = ink,
                emptyCode = ink.copy(alpha = 0.6f),
                caption = ink.copy(alpha = 0.86f),
            ),
            contentDescription = routeArcDescription(from?.displayCode, to?.displayCode, from?.cityLabel, to?.cityLabel),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Where the editor's preview plane sits once both airports are set (mid-route: it's a preview, not progress). */
private const val BannerPlanePosition = 0.5f

private val BannerStars = listOf(
    0.06f to 0.16f, 0.18f to 0.08f, 0.33f to 0.22f, 0.47f to 0.06f, 0.61f to 0.18f,
    0.74f to 0.09f, 0.86f to 0.24f, 0.95f to 0.12f,
)

/**
 * From and To as a connected pair joined by a swap button: stacked with the button at the end on phones,
 * side by side with the button between them on wide cards. Results (or popular airports) for the focused field span
 * the pair's full width underneath, so they stay readable either way.
 */
@Composable
private fun RoutePair(
    index: Int,
    leg: LegDraft,
    search: PlaceSearchState?,
    now: Instant,
    focus: LegFocus,
    actions: TripEditorActions,
) {
    var focusedEnd by remember { mutableStateOf<LegEnd?>(null) }
    val fromRef = PlaceFieldRef(index, LegEnd.Origin)
    val toRef = PlaceFieldRef(index, LegEnd.Destination)
    fun onFocus(end: LegEnd, ref: PlaceFieldRef, focused: Boolean) {
        if (focused) {
            focusedEnd = end
            actions.onPlaceFieldFocused(ref)
        } else if (focusedEnd == end) {
            focusedEnd = null
        }
    }
    val from: @Composable (Modifier) -> Unit = { fieldModifier ->
        PlaceField(
            label = stringResource(R.string.editor_from),
            input = leg.origin,
            focused = focusedEnd == LegEnd.Origin,
            now = now,
            onQueryChange = { actions.onPlaceQueryChange(fromRef, it) },
            onFocusChange = { onFocus(LegEnd.Origin, fromRef, it) },
            onImeAction = {
                actions.pickTopResult(fromRef)
                focus.to.requestFocus()
            },
            tag = TripsTestTags.editorFrom(index),
            focusRequester = focus.from,
            modifier = fieldModifier,
        )
    }
    val to: @Composable (Modifier) -> Unit = { fieldModifier ->
        PlaceField(
            label = stringResource(R.string.editor_to),
            input = leg.destination,
            focused = focusedEnd == LegEnd.Destination,
            now = now,
            onQueryChange = { actions.onPlaceQueryChange(toRef, it) },
            onFocusChange = { onFocus(LegEnd.Destination, toRef, it) },
            onImeAction = {
                actions.pickTopResult(toRef)
                focus.departureDate.requestFocus()
            },
            tag = TripsTestTags.editorTo(index),
            focusRequester = focus.to,
            modifier = fieldModifier,
        )
    }
    val canSwap = leg.origin.place != null || leg.destination.place != null
    BoxWithConstraints {
        val sideBySide = maxWidth >= SideBySideMinWidth
        Column {
            if (sideBySide) {
                Row(verticalAlignment = Alignment.Top) {
                    from(Modifier.weight(1f))
                    SwapButton(index, canSwap, vertical = false, onSwap = { actions.swapPlaces(index) }, modifier = Modifier.padding(top = 8.dp))
                    to(Modifier.weight(1f))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        from(Modifier)
                        to(Modifier)
                    }
                    SwapButton(index, canSwap, vertical = true, onSwap = { actions.swapPlaces(index) }, modifier = Modifier.padding(start = 4.dp))
                }
            }
            val end = focusedEnd
            val visible = search?.takeIf { s ->
                end != null && s.field == PlaceFieldRef(index, end) && leg.place(end).let { it.place == null && it.query == s.query }
            }
            if (visible != null) {
                PlaceResults(
                    search = visible,
                    now = now,
                    onSelect = { place ->
                        actions.onPlaceSelected(visible.field, place)
                        if (visible.field.end == LegEnd.Origin) focus.to.requestFocus() else focus.departureDate.requestFocus()
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Swaps From and To. Each tap turns the arrows half a turn (a state change, so the non-bouncy data token; snaps
 * under reduce motion, and the swapped codes and fields carry the result anyway).
 */
@Composable
private fun SwapButton(index: Int, enabled: Boolean, vertical: Boolean, onSwap: () -> Unit, modifier: Modifier = Modifier) {
    var turns by rememberSaveable { mutableIntStateOf(0) }
    val rotation = animateFloatAsState(turns * 180f, OpusTheme.motion.dataSpatial(), label = "swap")
    val base = if (vertical) 0f else 90f
    IconButton(
        onClick = {
            turns++
            onSwap()
        },
        enabled = enabled,
        modifier = modifier.testTag(TripsTestTags.editorSwap(index)),
    ) {
        Icon(
            painterResource(R.drawable.ic_trips_swap),
            contentDescription = stringResource(R.string.editor_swap),
            modifier = Modifier.graphicsLayer { rotationZ = base + rotation.value },
        )
    }
}

/** Card width from which From and To sit side by side. */
private val SideBySideMinWidth = 560.dp

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
    estimated: Boolean = false,
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
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        // Same height for both fields: their values measure slightly differently (dates vs digits).
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerField(
                label = stringResource(R.string.editor_date),
                accessibilityLabel = "$caption, ${stringResource(R.string.editor_date)}$estimatedLabel",
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
                accessibilityLabel = "$caption, ${stringResource(R.string.editor_time)}, $zoneLabel$estimatedLabel",
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
