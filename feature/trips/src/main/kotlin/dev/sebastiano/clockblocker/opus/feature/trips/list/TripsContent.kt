package dev.sebastiano.clockblocker.opus.feature.trips.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.SuitcaseOClockArt
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags

/** Everything the trips list can ask its host or ViewModel to do. Null = not offered by the host. */
class TripsCallbacks(
    val onOpenTrip: (String) -> Unit,
    val onNewTrip: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onEditTrip: ((String) -> Unit)?,
    val onCreateReturnTrip: ((String) -> Unit)?,
    val onTryDemo: () -> Unit,
    val onDelete: (String) -> Unit,
    val onDuplicate: (String) -> Unit,
)

/** Width from which trips are laid out as a two-column grid of cards. */
internal val TwoColumnMinWidth: Dp = 720.dp
private val SingleColumnMaxWidth: Dp = 640.dp

/**
 * Stateless trips list: large flexible app bar, sectioned card grid (or the empty state) and the FAB menu.
 *
 * @param showSettingsAction show a settings gear in the app bar. Off by default: the shell's navigation suite
 *   always carries a Settings destination, and a second route to it read as two different places.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TripsContent(
    state: TripsUiState,
    callbacks: TripsCallbacks,
    modifier: Modifier = Modifier,
    selectedTripId: String? = null,
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    showSettingsAction: Boolean = false,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    val menuBack = rememberFabMenuBack(expanded = fabExpanded, onCollapse = { fabExpanded = false })

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.trips_app_title)) },
                subtitle = summaryLine(state)?.let { line -> { Text(line) } },
                actions = {
                    if (showSettingsAction) {
                        IconButton(onClick = callbacks.onOpenSettings, modifier = Modifier.testTag(TripsTestTags.Settings)) {
                            Icon(painterResource(R.drawable.ic_trips_settings), contentDescription = stringResource(R.string.trips_settings))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.loading && !state.isEmpty) {
                TripsFabMenu(
                    expanded = fabExpanded,
                    onExpandedChange = { open ->
                        if (open) menuBack.reset()
                        fabExpanded = open
                    },
                    collapseProgress = menuBack::collapseProgress,
                    returnCandidate = state.returnCandidate.takeIf { callbacks.onCreateReturnTrip != null },
                    callbacks = callbacks,
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding))
            state.isEmpty -> TripsEmptyState(
                onPlanTrip = callbacks.onNewTrip,
                onTryDemo = callbacks.onTryDemo,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> TripsGrid(state, callbacks, selectedTripId, padding)
        }
        FabMenuScrim(
            visible = fabExpanded,
            collapseProgress = menuBack::collapseProgress,
            onDismiss = { fabExpanded = false },
        )
    }
}

/**
 * Dims the list behind an open FAB menu so the menu items read over busy cards. Purely visual for
 * accessibility: the menu closes via its own toggle or back, so the scrim itself stays out of the tree.
 *
 * Both the fade and the predictive-back [collapseProgress] are read in the draw phase only: the scrim is always
 * in the tree and simply draws nothing at alpha 0, so the animation never recomposes (F-001).
 */
@Composable
private fun FabMenuScrim(visible: Boolean, collapseProgress: () -> Float, onDismiss: () -> Unit) {
    val alpha = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = OpusTheme.motion.colour(),
        label = "fabScrim",
    )
    val scrim = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val a = alpha.value * (1f - collapseProgress())
                if (a > 0f) drawRect(scrim, alpha = a)
            }
            .then(if (visible) Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } } else Modifier)
            .clearAndSetSemantics {},
    )
}

@Composable
private fun summaryLine(state: TripsUiState): String? {
    if (state.loading || state.isEmpty) return null
    val parts = buildList {
        if (state.inProgress.isNotEmpty()) add(stringResource(R.string.trips_summary_in_progress, state.inProgress.size))
        if (state.upcoming.isNotEmpty()) add(stringResource(R.string.trips_summary_upcoming, state.upcoming.size))
        if (state.past.isNotEmpty()) add(stringResource(R.string.trips_summary_past, state.past.size))
    }
    return parts.joinToString(stringResource(R.string.trips_summary_separator))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TripsFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    collapseProgress: () -> Float,
    returnCandidate: TripSummary?,
    callbacks: TripsCallbacks,
) {
    // Predictive back: the items shrink toward the FAB and fade with the gesture (read in the layer phase).
    val followBack = Modifier.graphicsLayer {
        val p = collapseProgress()
        alpha = 1f - p
        val scale = 1f - p * (1f - FabMenuBackScale)
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(1f, 1f)
    }
    val openLabel = stringResource(R.string.trips_fab_open)
    val closeLabel = stringResource(R.string.trips_fab_close)
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                modifier = Modifier
                    .testTag(TripsTestTags.Fab)
                    .semantics {
                        traversalIndex = -1f
                        contentDescription = if (expanded) closeLabel else openLabel
                    },
            ) {
                val icon = if (checkedProgress > 0.5f) R.drawable.ic_trips_close else R.drawable.ic_trips_add
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = { onExpandedChange(false); callbacks.onNewTrip() },
            text = { Text(stringResource(R.string.trips_new_trip)) },
            icon = { Icon(painterResource(R.drawable.ic_trips_flight), contentDescription = null) },
            modifier = followBack.testTag(TripsTestTags.NewTrip),
        )
        if (returnCandidate != null) {
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    callbacks.onCreateReturnTrip?.invoke(returnCandidate.id)
                },
                text = { Text(stringResource(R.string.trips_return_trip, returnCandidate.trip.destination.city.ifBlank { returnCandidate.trip.destination.displayCode })) },
                icon = { Icon(painterResource(R.drawable.ic_trips_return), contentDescription = null) },
                modifier = followBack.testTag(TripsTestTags.ReturnTrip),
            )
        }
        FloatingActionButtonMenuItem(
            onClick = { onExpandedChange(false); callbacks.onTryDemo() },
            text = { Text(stringResource(R.string.trips_demo_trip)) },
            icon = { Icon(painterResource(R.drawable.ic_trips_sparkle), contentDescription = null) },
            modifier = followBack.testTag(TripsTestTags.DemoTrip),
        )
    }
}

@Composable
private fun TripsGrid(
    state: TripsUiState,
    callbacks: TripsCallbacks,
    selectedTripId: String?,
    padding: PaddingValues,
) {
    val othersPresent = state.inProgress.isNotEmpty() || state.upcoming.isNotEmpty()
    var collapsed by rememberSaveable {
        mutableStateOf(if (othersPresent && state.past.size > PastCollapsedThreshold) setOf(TripPhase.Past.name) else emptySet())
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= TwoColumnMinWidth) 2 else 1
        val side = if (columns == 1 && maxWidth > SingleColumnMaxWidth) (maxWidth - SingleColumnMaxWidth) / 2 else 16.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize().testTag(TripsTestTags.List),
            contentPadding = PaddingValues(
                start = side,
                end = side,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + FabClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TripPhase.entries.forEach { phase ->
                val trips = state.section(phase)
                if (trips.isNotEmpty()) {
                    val isCollapsed = phase.name in collapsed
                    section(
                        phase = phase,
                        trips = trips,
                        collapsed = isCollapsed,
                        onToggle = { collapsed = if (isCollapsed) collapsed - phase.name else collapsed + phase.name },
                        selectedTripId = selectedTripId,
                        callbacks = callbacks,
                    )
                }
            }
        }
    }
}

private fun LazyGridScope.section(
    phase: TripPhase,
    trips: List<TripSummary>,
    collapsed: Boolean,
    onToggle: () -> Unit,
    selectedTripId: String?,
    callbacks: TripsCallbacks,
) {
    item(key = "header-${phase.name}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
        SectionHeader(phase, trips.size, collapsed, onToggle, animateTripItem())
    }
    if (!collapsed) {
        items(trips, key = { it.id }, contentType = { "trip" }) { summary ->
            SwipeToDeleteTripCard(
                summary = summary,
                selected = summary.id == selectedTripId,
                callbacks = callbacks,
                modifier = animateTripItem(),
            )
        }
    }
}

/**
 * Item appearance, disappearance and reflow when a section folds: one tier for the whole block. Cards are
 * containers moving in space, so their fades ride the same container spatial spec as their placement and
 * arrive together (and all of it snaps under reduced motion), instead of a fast fade finishing long before the
 * reflow settles.
 */
@Composable
private fun LazyGridItemScope.animateTripItem(): Modifier {
    val motion = OpusTheme.motion
    return Modifier.animateItem(
        fadeInSpec = motion.containerSpatial(),
        placementSpec = motion.containerSpatial(),
        fadeOutSpec = motion.containerSpatial(),
    )
}

@Composable
private fun SectionHeader(phase: TripPhase, count: Int, collapsed: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(
        when (phase) {
            TripPhase.InProgress -> R.string.trips_section_in_progress
            TripPhase.Upcoming -> R.string.trips_section_upcoming
            TripPhase.Past -> R.string.trips_section_past
        },
    )
    val description = pluralStringResource(R.plurals.trips_section_count, count, title, count)
    val stateLabel = stringResource(if (collapsed) R.string.trips_section_collapsed else R.string.trips_section_expanded)
    // State indicator, not a container: no bounce (dataSpatial), and the value is only read in the layer phase.
    val rotation = animateFloatAsState(if (collapsed) -90f else 0f, OpusTheme.motion.dataSpatial(), label = "chevron")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(TripsTestTags.section(phase.name))
            .toggleable(value = !collapsed, role = Role.Button, onValueChange = { onToggle() })
            .clearAndSetSemantics {
                heading()
                contentDescription = description
                stateDescription = stateLabel
            }
            .padding(start = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Text(
                count.toString(),
                style = OpusTheme.textStyles.timeLabel,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            painterResource(R.drawable.ic_trips_expand),
            contentDescription = null,
            modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteTripCard(
    summary: TripSummary,
    selected: Boolean,
    callbacks: TripsCallbacks,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) callbacks.onDelete(summary.id) },
        backgroundContent = {
            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Row(
                        Modifier.padding(horizontal = 28.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.trip_swipe_delete), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.size(8.dp))
                        Icon(painterResource(R.drawable.ic_trips_delete), contentDescription = null)
                    }
                }
            }
        },
    ) {
        TripCard(
            summary = summary,
            selected = selected,
            actions = TripCardActions(
                onOpen = { callbacks.onOpenTrip(summary.id) },
                onEdit = callbacks.onEditTrip?.let { edit -> { edit(summary.id) } },
                onDuplicate = { callbacks.onDuplicate(summary.id) },
                onCreateReturn = callbacks.onCreateReturnTrip?.let { create -> { create(summary.id) } },
                onDelete = { callbacks.onDelete(summary.id) },
            ),
        )
    }
}

/** "No trips yet": an editorial moment (rare surface), so the suitcase clock may tick. */
@Composable
internal fun TripsEmptyState(onPlanTrip: () -> Unit, onTryDemo: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.testTag(TripsTestTags.EmptyState), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SuitcaseOClockArt(
                modifier = Modifier.size(176.dp),
                contentDescription = stringResource(R.string.trips_empty_art),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.trips_empty_headline),
                style = OpusTheme.textStyles.editorialHeadline,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.trips_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onPlanTrip,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(TripsTestTags.EmptyPlanTrip),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Icon(painterResource(R.drawable.ic_trips_flight), contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.trips_empty_plan), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onTryDemo,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(TripsTestTags.EmptyDemoTrip),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Icon(painterResource(R.drawable.ic_trips_sparkle), contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.trips_empty_demo), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private const val PastCollapsedThreshold = 3
private const val FabMenuBackScale = 0.9f
private val FabClearance = 104.dp
