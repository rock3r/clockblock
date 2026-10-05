package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import dev.sebastiano.clockblocker.opus.feature.trips.ui.cityLabel
import dev.sebastiano.clockblocker.opus.feature.trips.ui.formatDuration
import dev.sebastiano.clockblocker.opus.feature.trips.ui.rememberDateFormatter
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

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
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is TripEditorEvent.Saved -> currentOnDone(event.tripId)
            }
        }
    }
    TripEditorContent(
        state = state,
        actions = viewModel,
        onClose = onBack,
        onDiscard = { onDone(null) },
        modifier = modifier,
    )
}

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
                actions = {
                    Button(
                        onClick = actions::save,
                        enabled = state.canSave,
                        modifier = Modifier.padding(end = 12.dp).testTag(TripsTestTags.EditorSave),
                    ) { Text(stringResource(R.string.editor_save)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding))
            state.notFound -> NotFound(onClose, Modifier.fillMaxSize().padding(padding))
            else -> EditorBody(
                state = state,
                actions = actions,
                now = now,
                snackbarHostState = snackbarHostState,
                onPick = { picker = it },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    picker?.let { target -> PickerDialog(target, state, actions, onDismiss = { picker = null }) }

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

@Composable
private fun EditorBody(
    state: TripEditorUiState,
    actions: TripEditorActions,
    now: Instant,
    snackbarHostState: SnackbarHostState,
    onPick: (PickerTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember(state.legs.map { it.id }) { state.legs.associate { it.id to LegFocus() } }
    val flights: @Composable () -> Unit = {
        if (state.canReportDelay) {
            DelayCard(state, actions, snackbarHostState)
            Spacer(Modifier.height(16.dp))
        }
        state.legs.forEachIndexed { index, leg ->
            if (index > 0) LayoverConnector(state, index)
            LegCard(
                index = index,
                leg = leg,
                legCount = state.legs.size,
                issues = state.issuesFor(index),
                search = state.search,
                now = now,
                focus = focus.getValue(leg.id),
                actions = actions,
                onPick = onPick,
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = actions::addLeg,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(TripsTestTags.EditorAddLeg),
        ) {
            Icon(painterResource(R.drawable.ic_trips_add), contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.editor_add_leg))
        }
    }
    val details: @Composable () -> Unit = {
        TripDetails(state, actions, onPick)
        if (!state.canSave && !state.saving) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(if (state.hasErrors) R.string.editor_save_blocked else R.string.editor_save_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.hasErrors) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
    BoxWithConstraints(modifier.imePadding(), contentAlignment = Alignment.TopCenter) {
        if (maxWidth >= TwoPaneMinWidth) {
            Row(
                Modifier.widthIn(max = 1200.dp).fillMaxSize().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.weight(3f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    flights()
                    Spacer(Modifier.height(32.dp))
                }
                Column(Modifier.weight(2f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    details()
                    Spacer(Modifier.height(32.dp))
                }
            }
        } else {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                flights()
                Spacer(Modifier.height(24.dp))
                details()
                Spacer(Modifier.height(32.dp))
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
private fun TripDetails(state: TripEditorUiState, actions: TripEditorActions, onPick: (PickerTarget) -> Unit) {
    val dates = rememberDateFormatter()
    val times = rememberTimeFormatter()
    val destination = state.legs.lastOrNull()?.destination?.place
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                stringResource(R.string.editor_details),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
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
                modifier = Modifier.fillMaxWidth().testTag(TripsTestTags.EditorTitle),
            )

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.editor_return), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            val zoneLabel = destination?.let { stringResource(R.string.editor_local_time_in, it.cityLabel) }
                ?: stringResource(R.string.editor_local_time_destination)
            Row(
                Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PickerField(
                    label = stringResource(R.string.editor_date),
                    accessibilityLabel = stringResource(R.string.editor_return),
                    value = state.form.returnDate?.let(dates::format),
                    icon = R.drawable.ic_trips_event,
                    onClick = { onPick(PickerTarget.ReturnDate) },
                    pickLabel = stringResource(R.string.editor_pick_date),
                    tag = TripsTestTags.EditorReturnDate,
                    modifier = Modifier.weight(1.35f).fillMaxHeight(),
                )
                PickerField(
                    label = stringResource(R.string.editor_time),
                    accessibilityLabel = "${stringResource(R.string.editor_return)}, $zoneLabel",
                    value = state.form.returnTime?.takeIf { state.form.returnDate != null }?.let(times::formatFull),
                    icon = R.drawable.ic_trips_schedule,
                    onClick = { onPick(PickerTarget.ReturnTime) },
                    pickLabel = stringResource(R.string.editor_pick_time),
                    tag = TripsTestTags.EditorReturnTime,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                if (state.form.returnDate != null) {
                    IconButton(onClick = actions::clearReturn) {
                        Icon(painterResource(R.drawable.ic_trips_close), contentDescription = stringResource(R.string.editor_return_clear))
                    }
                }
            }
            Text(
                stringResource(R.string.editor_return_hint, zoneLabel.replaceFirstChar { it.uppercase() }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )

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
        }
    }
}

/** Auto / Adapt / Home time as a connected M3 Expressive button group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StrategyToggle(selected: AdaptationStrategy?, onSelect: (AdaptationStrategy?) -> Unit) {
    val options = listOf(
        Triple<AdaptationStrategy?, Int, String>(null, R.string.editor_strategy_auto, TripsTestTags.EditorStrategyAuto),
        Triple(AdaptationStrategy.Adapt, R.string.editor_strategy_adapt, TripsTestTags.EditorStrategyAdapt),
        Triple(AdaptationStrategy.StayOnHomeTime, R.string.editor_strategy_home, TripsTestTags.EditorStrategyHome),
    )
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (value, label, tag) ->
            ToggleButton(
                checked = selected == value,
                onCheckedChange = { onSelect(value) },
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).testTag(tag),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(stringResource(label), maxLines = 2, textAlign = TextAlign.Center)
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
            title = stringResource(R.string.editor_return),
            initial = state.form.returnDate ?: state.legs.lastOrNull()?.arrivalDate,
            onDismiss = onDismiss,
            onConfirm = actions::onReturnDateChange,
        )
        PickerTarget.ReturnTime -> LocalTimePickerDialog(
            title = "${stringResource(R.string.editor_return)} · ${zoneOf(state.legs.lastIndex, origin = false)}",
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
