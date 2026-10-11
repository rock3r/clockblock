package dev.sebastiano.clockblocker.opus.feature.trips.editor

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import dev.sebastiano.clockblocker.opus.feature.trips.ui.cityLabel
import dev.sebastiano.clockblocker.opus.feature.trips.ui.formatDuration
import dev.sebastiano.clockblocker.opus.feature.trips.ui.rememberDateFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Stateful editor entry: collects the ViewModel, forwards saves to [onDone] and guards unsaved changes
 * (close button, system back and predictive back all ask before discarding).
 */
@Composable
internal fun TripEditorRoute(
    viewModel: TripEditorViewModel,
    onDone: (String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    val view = LocalView.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is TripEditorEvent.Saved -> {
                    // A saved trip is a rare, completed task: one confirm tick (the plan then opens with first light).
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    currentOnDone(event.tripId)
                }
            }
        }
    }
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            val current = Instant.now()
            delay(MillisPerMinute - current.toEpochMilli() % MillisPerMinute)
            value = Instant.now()
        }
    }
    TripEditorContent(
        state = state,
        actions = viewModel,
        onClose = onBack,
        onDiscard = { onDone(null) },
        modifier = modifier,
        now = now,
    )
}

private const val MillisPerMinute = 60_000L

/** Stateless full-screen editor. [now] drives the UTC offsets shown next to airports. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripEditorContent(
    state: TripEditorUiState,
    actions: TripEditorActions,
    onClose: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = remember { Instant.now() },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    // The ticket the user is on (touched or focused last): the globe frames and highlights it.
    var activeLeg by rememberSaveable { mutableIntStateOf(0) }
    var globeExpanded by rememberSaveable { mutableStateOf(false) }
    val tickets = remember(state.legs.map { it.id }) { state.legs.map { TicketHandle() } }
    val scope = rememberCoroutineScope()
    val route = remember(state.legs) { EditorRoute.of(state.legs) }
    val routeDescription = routeDescription(route, state.preview?.let(ShiftSummary::of))
    val active = activeLeg.coerceIn(0, (state.legs.size - 1).coerceAtLeast(0))
    // A tap on a flight (on the globe, small or full size): go to its ticket and make it the active one.
    val goToLeg: (Int) -> Unit = { index ->
        activeLeg = index
        globeExpanded = false
        tickets.getOrNull(index)?.let { ticket ->
            scope.launch {
                ticket.bringIntoView.bringIntoView()
                runCatching { ticket.focus.requestFocus() }
            }
        }
    }
    val requestClose = { if (state.dirty) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.dirty && !confirmDiscard) { confirmDiscard = true }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (state.mode) {
                                EditorMode.New -> R.string.editor_title_new
                                EditorMode.Edit -> R.string.editor_title_edit
                                EditorMode.Return -> R.string.editor_title_return
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = requestClose, modifier = Modifier.testTag(TripsTestTags.EditorClose)) {
                        Icon(painterResource(R.drawable.ic_trips_close), contentDescription = stringResource(R.string.editor_close))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (!state.loading && !state.notFound) PrimaryActionBar(state, onSave = actions::save)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding))
            state.notFound -> NotFound(onClose, Modifier.fillMaxSize().padding(padding))
            else -> EditorBody(
                state = state,
                actions = actions,
                snackbarHostState = snackbarHostState,
                onPick = { picker = it },
                contentPadding = padding,
                tickets = tickets,
                activeLeg = active,
                onActiveLeg = { activeLeg = it },
                globe = if (route.isEmpty) {
                    null
                } else {
                    {
                        EditorGlobe(
                            route = route,
                            description = routeDescription,
                            now = now,
                            activeLeg = active,
                            onLegClick = goToLeg,
                            onExpand = { globeExpanded = true },
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    picker?.let { target -> PickerDialog(target, state, actions, onDismiss = { picker = null }) }

    if (globeExpanded && !route.isEmpty) {
        GlobeSheet(
            route = route,
            description = routeDescription,
            now = now,
            activeLeg = active,
            onLegClick = goToLeg,
            onDismiss = { globeExpanded = false },
        )
    }

    state.search?.let { search ->
        PlaceSearchSheet(
            search = search,
            legCount = state.legs.size,
            now = now,
            onQueryChange = { actions.onPlaceQueryChange(search.field, it) },
            onPick = { place -> pickPlace(state, actions, search.field) { actions.onPlaceSelected(search.field, place); true } },
            onPickTop = { pickPlace(state, actions, search.field) { actions.pickTopResult(search.field) } },
            onDismiss = actions::dismissSearch,
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmDiscard = false; onDiscard() },
                    modifier = Modifier.testTag(TripsTestTags.DiscardConfirm),
                ) { Text(stringResource(R.string.editor_discard_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDiscard = false },
                    modifier = Modifier.testTag(TripsTestTags.DiscardKeep),
                ) { Text(stringResource(R.string.editor_discard_keep)) }
            },
        )
    }
}

/**
 * Picks an airport with [pick] (false: nothing was picked) and moves on: after From, an empty To opens next in the
 * same sheet, so a new trip's route is two picks in a row.
 */
private fun pickPlace(state: TripEditorUiState, actions: TripEditorActions, field: PlaceFieldRef, pick: () -> Boolean) {
    val leg = state.legs.getOrNull(field.legIndex) ?: return
    if (!pick()) return
    if (field.end == LegEnd.Origin && leg.destination.place == null) {
        actions.openPlaceSearch(PlaceFieldRef(field.legIndex, LegEnd.Destination))
    }
}

/**
 * The editor's main action, at the bottom in thumb reach: full-width "Build my plan" ("Save changes" when editing),
 * disabled until the trip is valid, with the reason right above it. It rides above the keyboard and the navigation
 * bar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PrimaryActionBar(state: TripEditorUiState, onSave: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val blocker = state.saveBlocker.takeIf { !state.saving }
    Column(
        Modifier
            .fillMaxWidth()
            .background(surface)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (blocker != null) {
            Text(
                stringResource(
                    when (blocker) {
                        SaveBlocker.Errors -> R.string.editor_save_blocked
                        SaveBlocker.Incomplete -> R.string.editor_save_hint
                        SaveBlocker.ReturnDateMissing -> R.string.editor_save_return_missing
                        SaveBlocker.ReturnBeforeArrival -> R.string.editor_save_return_before
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (blocker == SaveBlocker.Incomplete) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = PrimaryActionMaxWidth).padding(bottom = 8.dp).testTag(TripsTestTags.EditorSaveHint),
            )
        }
        Button(
            onClick = onSave,
            enabled = state.canSave,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .widthIn(max = PrimaryActionMaxWidth)
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(TripsTestTags.EditorSave),
        ) {
            Text(
                stringResource(if (state.mode == EditorMode.Edit) R.string.editor_save_changes else R.string.editor_build_plan),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private val PrimaryActionMaxWidth = 560.dp

@Composable
private fun EditorBody(
    state: TripEditorUiState,
    actions: TripEditorActions,
    snackbarHostState: SnackbarHostState,
    onPick: (PickerTarget) -> Unit,
    contentPadding: PaddingValues,
    tickets: List<TicketHandle>,
    activeLeg: Int,
    onActiveLeg: (Int) -> Unit,
    globe: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val flights: @Composable ColumnScope.() -> Unit = {
        if (state.canReportDelay) {
            DelayCard(state, actions, snackbarHostState)
            Spacer(Modifier.height(16.dp))
        }
        state.legs.forEachIndexed { index, leg ->
            if (index > 0) LayoverConnector(state, index)
            key(leg.id) {
                LegTicket(
                    index = index,
                    leg = leg,
                    legCount = state.legs.size,
                    issues = state.issuesFor(index),
                    actions = actions,
                    onPick = onPick,
                    onPickPlace = actions::openPlaceSearch,
                    active = index == activeLeg && state.legs.size > 1,
                    handle = tickets.getOrNull(index),
                    onActive = { onActiveLeg(index) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = actions::addLeg,
            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).testTag(TripsTestTags.EditorAddLeg),
        ) {
            Icon(painterResource(R.drawable.ic_trips_add), contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.editor_add_leg))
        }
        if (state.offersReturnFlight) {
            Spacer(Modifier.height(8.dp))
            ReturnFlightCard(state, onToggle = actions::onReturnFlightChange, onPick = onPick)
        }
    }
    val details: @Composable ColumnScope.() -> Unit = {
        YourShiftCard(state.preview, blocked = state.hasErrors, pending = state.complete && !state.hasErrors, globe = globe)
        Spacer(Modifier.height(16.dp))
        TripDetails(state, actions)
    }
    val layoutDirection = LocalLayoutDirection.current
    val top = contentPadding.calculateTopPadding()
    val bottom = contentPadding.calculateBottomPadding()
    BoxWithConstraints(
        modifier.padding(
            start = contentPadding.calculateStartPadding(layoutDirection),
            end = contentPadding.calculateEndPadding(layoutDirection),
            top = top,
            // Outside the scroll, not inside it: the viewport then ends at the main action, so focus, TalkBack and
            // scroll-into-view never leave a field hidden behind the bar.
            bottom = bottom,
        ),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (maxWidth >= TwoPaneMinWidth) {
            Row(
                Modifier.widthIn(max = 1200.dp).fillMaxSize().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.weight(3f).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp)) {
                    flights()
                }
                Column(Modifier.weight(2f).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp)) {
                    details()
                }
            }
        } else {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            ) {
                flights()
                Spacer(Modifier.height(16.dp))
                details()
            }
        }
    }
}

/** Window width from which the editor shows flights and trip details side by side. */
private val TwoPaneMinWidth = 840.dp

/** Between two legs: the dotted rail continues, with the connection time (or an overlap) on it. */
@Composable
private fun LayoverConnector(state: TripEditorUiState, index: Int) {
    val layover = state.layoverBefore(index)
    val place = state.legs[index - 1].destination.place
    val color = MaterialTheme.colorScheme.outline
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 2.dp, height = 56.dp)) {
            drawLine(
                color = color,
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height),
                strokeWidth = size.width,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
            )
        }
        Spacer(Modifier.size(16.dp))
        if (layover != null) {
            val overlap = layover.isNegative
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (overlap) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (overlap) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    if (overlap) {
                        stringResource(R.string.editor_layover_overlap)
                    } else {
                        stringResource(R.string.editor_layover, formatDuration(layover), place?.cityLabel.orEmpty())
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TripDetails(state: TripEditorUiState, actions: TripEditorActions) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                stringResource(R.string.editor_details),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            // The title is generated from the route, so it reads as text with a Rename button; the field only opens
            // on Rename (and stays open once the title is the traveller's own).
            var renaming by rememberSaveable { mutableStateOf(false) }
            if (renaming || state.form.titleEdited) {
                val focus = remember { FocusRequester() }
                OutlinedTextField(
                    value = if (state.form.titleEdited) state.form.title else state.suggestedTitle,
                    onValueChange = actions::onTitleChange,
                    label = { Text(stringResource(R.string.editor_trip_title)) },
                    supportingText = if (!state.form.titleEdited && state.suggestedTitle.isNotBlank()) {
                        { Text(stringResource(R.string.editor_trip_title_hint)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(TripsTestTags.EditorTitle),
                )
                // Opened by Rename: straight into the field (not on a restored or already-edited title).
                if (renaming && !state.form.titleEdited) LaunchedEffect(Unit) { focus.requestFocus() }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(
                            stringResource(R.string.editor_trip_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            state.suggestedTitle.ifBlank { stringResource(R.string.editor_trip_title_pending) },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state.suggestedTitle.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    TextButton(onClick = { renaming = true }, modifier = Modifier.testTag(TripsTestTags.EditorRename)) {
                        Text(stringResource(R.string.editor_trip_rename))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.editor_strategy), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            StrategyToggle(state.form.strategy, actions::onStrategyChange)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    when (state.form.strategy) {
                        null -> R.string.editor_strategy_auto_explanation
                        AdaptationStrategy.Adapt -> R.string.editor_strategy_adapt_explanation
                        AdaptationStrategy.StayOnHomeTime -> R.string.editor_strategy_home_explanation
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            state.bodyClockStart?.let { choice -> BodyClockStart(choice, actions::onBodyClockStartChange) }
        }
    }
}

/**
 * "Body clock when you leave" (issue #9): departure city or home. Only offered when the two are on different
 * times at departure, so it never shows for the common case of flying out from home.
 */
@Composable
private fun BodyClockStart(choice: BodyClockStartChoice, onSelect: (Boolean) -> Unit) {
    val homeCity = remember(choice.homeZoneId) { ZoneId.of(choice.homeZoneId).cityName() }
    val departureCity = choice.departure.cityLabel
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.editor_body_clock_start), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    ConnectedToggleGroup(
        options = listOf(
            ToggleOption(false, stringResource(R.string.editor_body_clock_start_departure), TripsTestTags.EditorBodyClockStartDeparture),
            ToggleOption(true, stringResource(R.string.editor_body_clock_start_home, homeCity), TripsTestTags.EditorBodyClockStartHome),
        ),
        selected = choice.fromHome,
        onSelect = onSelect,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        if (choice.fromHome) {
            stringResource(R.string.editor_body_clock_start_home_explanation, homeCity, departureCity)
        } else {
            stringResource(R.string.editor_body_clock_start_departure_explanation, departureCity)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** Auto / Adapt / Home time as a connected M3 Expressive button group. */
@Composable
private fun StrategyToggle(selected: AdaptationStrategy?, onSelect: (AdaptationStrategy?) -> Unit) {
    ConnectedToggleGroup(
        options = listOf(
            ToggleOption(null, stringResource(R.string.editor_strategy_auto), TripsTestTags.EditorStrategyAuto),
            ToggleOption(AdaptationStrategy.Adapt, stringResource(R.string.editor_strategy_adapt), TripsTestTags.EditorStrategyAdapt),
            ToggleOption(AdaptationStrategy.StayOnHomeTime, stringResource(R.string.editor_strategy_home), TripsTestTags.EditorStrategyHome),
        ),
        selected = selected,
        onSelect = onSelect,
    )
}

private data class ToggleOption<T>(val value: T, val label: String, val tag: String)

/** One-of-n choice as a connected M3 Expressive button group: equal widths, labels wrap to two lines. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedToggleGroup(options: List<ToggleOption<T>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = selected == option.value,
                onCheckedChange = { onSelect(option.value) },
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).testTag(option.tag),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(option.label, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
    }
}

/** "I'm delayed": quick shifts for the next flight, offered while a trip is under way or departs soon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DelayCard(state: TripEditorUiState, actions: TripEditorActions, snackbarHostState: SnackbarHostState) {
    var legIndex by rememberSaveable(state.delayLegIndex) { mutableStateOf(state.delayLegIndex) }
    var customOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    val leg = state.legs.getOrNull(legIndex)
    val flightLabel = leg?.flightNumber?.takeIf { it.isNotBlank() } ?: stringResource(R.string.editor_delay_flight)
    val durations = listOf(30L, 60L, 120L, 180L).map(Duration::ofMinutes)
    val appliedTemplate = stringResource(R.string.editor_delay_applied)
    val durationLabels = durations.associateWith { formatDuration(it) }

    fun apply(delay: Duration, label: String) {
        actions.delayLeg(legIndex, delay)
        scope.launch { snackbarHostState.showSnackbar(appliedTemplate.format(flightLabel, label)) }
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.testTag(TripsTestTags.EditorDelay),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_trips_delay), contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(R.string.editor_delay_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(stringResource(R.string.editor_delay_body, flightLabel), style = MaterialTheme.typography.bodyMedium)
            if (state.legs.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.legs.forEachIndexed { index, draft ->
                        FilterChip(
                            selected = index == legIndex,
                            onClick = { legIndex = index },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.editor_delay_leg_label,
                                        draft.origin.place?.displayCode ?: "?",
                                        draft.destination.place?.displayCode ?: "?",
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                durations.forEach { delay ->
                    val label = durationLabels.getValue(delay)
                    FilledTonalButton(
                        onClick = { apply(delay, label) },
                        enabled = leg?.toFlightLeg() != null,
                        modifier = Modifier.testTag(TripsTestTags.editorDelayMinutes(delay.toMinutes().toInt())),
                    ) { Text(stringResource(R.string.editor_delay_plus, label)) }
                }
                OutlinedButton(
                    onClick = { customOpen = true },
                    enabled = leg?.toFlightLeg() != null,
                    modifier = Modifier.testTag(TripsTestTags.EditorDelayCustom),
                ) { Text(stringResource(R.string.editor_delay_custom)) }
            }
        }
    }

    if (customOpen) {
        CustomDelayDialog(
            onDismiss = { customOpen = false },
            onConfirm = { minutes ->
                customOpen = false
                val delay = Duration.ofMinutes(minutes)
                apply(delay, formatDuration(resources, delay))
            },
        )
    }
}

@Composable
private fun CustomDelayDialog(onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val minutes = text.toLongOrNull()?.takeIf { it in 1..MaxCustomDelayMinutes }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_delay_custom_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.editor_delay_minutes)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { minutes?.let(onConfirm) }, enabled = minutes != null) { Text(stringResource(R.string.editor_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}

@Composable
private fun PickerDialog(target: PickerTarget, state: TripEditorUiState, actions: TripEditorActions, onDismiss: () -> Unit) {
    fun legOf(index: Int) = state.legs.getOrNull(index)
    val departs = stringResource(R.string.editor_departs)
    val arrives = stringResource(R.string.editor_arrives)
    @Composable
    fun zoneOf(index: Int, origin: Boolean): String {
        val place = legOf(index)?.let { if (origin) it.origin.place else it.destination.place }
        return place?.let { stringResource(R.string.editor_local_time_in, it.cityLabel) }
            ?: stringResource(if (origin) R.string.editor_local_time_origin else R.string.editor_local_time_destination)
    }
    when (target) {
        is PickerTarget.DepartureDate -> LocalDatePickerDialog(
            title = departs,
            initial = legOf(target.legIndex)?.departureDate ?: legOf(target.legIndex - 1)?.arrivalDate,
            onDismiss = onDismiss,
            onConfirm = { actions.onDepartureDateChange(target.legIndex, it) },
        )
        is PickerTarget.DepartureTime -> LocalTimePickerDialog(
            title = "$departs · ${zoneOf(target.legIndex, origin = true)}",
            initial = legOf(target.legIndex)?.departureTime,
            onDismiss = onDismiss,
            onConfirm = { actions.onDepartureTimeChange(target.legIndex, it) },
        )
        is PickerTarget.ArrivalDate -> LocalDatePickerDialog(
            title = arrives,
            initial = legOf(target.legIndex)?.let { it.arrivalDate ?: it.departureDate },
            onDismiss = onDismiss,
            onConfirm = { actions.onArrivalDateChange(target.legIndex, it) },
        )
        is PickerTarget.ArrivalTime -> LocalTimePickerDialog(
            title = "$arrives · ${zoneOf(target.legIndex, origin = false)}",
            initial = legOf(target.legIndex)?.arrivalTime,
            onDismiss = onDismiss,
            onConfirm = { actions.onArrivalTimeChange(target.legIndex, it) },
        )
        PickerTarget.ReturnDate -> LocalDatePickerDialog(
            title = stringResource(R.string.editor_return_title),
            initial = state.form.returnDate ?: state.legs.lastOrNull()?.arrivalDate,
            onDismiss = onDismiss,
            onConfirm = actions::onReturnDateChange,
        )
        PickerTarget.ReturnTime -> LocalTimePickerDialog(
            title = "${stringResource(R.string.editor_return_title)} · ${zoneOf(state.legs.lastIndex, origin = false)}",
            initial = state.form.returnTime,
            onDismiss = onDismiss,
            onConfirm = { time ->
                if (state.form.returnDate == null) {
                    state.legs.lastOrNull()?.arrivalDate?.let(actions::onReturnDateChange)
                }
                actions.onReturnTimeChange(time)
            },
        )
    }
}

@Composable
private fun NotFound(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.editor_not_found), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack) { Text(stringResource(R.string.editor_back)) }
    }
}

private const val MaxCustomDelayMinutes = 24 * 60L
