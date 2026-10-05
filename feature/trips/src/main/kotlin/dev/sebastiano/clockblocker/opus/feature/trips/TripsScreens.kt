package dev.sebastiano.clockblocker.opus.feature.trips

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.clockblocker.opus.feature.trips.editor.TripEditorArgs
import dev.sebastiano.clockblocker.opus.feature.trips.editor.TripEditorRoute
import dev.sebastiano.clockblocker.opus.feature.trips.editor.TripEditorViewModel
import dev.sebastiano.clockblocker.opus.feature.trips.list.TripsCallbacks
import dev.sebastiano.clockblocker.opus.feature.trips.list.TripsContent
import dev.sebastiano.clockblocker.opus.feature.trips.list.TripsEvent
import dev.sebastiano.clockblocker.opus.feature.trips.list.TripsViewModel
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch

/**
 * Trips list (navigation contract used by `:app`; keep these signatures, additive defaulted params are OK).
 * [selectedTripId] highlights the trip shown in the detail pane on large screens.
 *
 * @param onEditTrip opens the editor for a trip; when null, "Edit" isn't offered on cards.
 * @param onCreateReturnTrip opens the editor prefilled with the return of the given outbound trip
 *   (`TripEditorScreen(tripId = null, returnOfTripId = it)`); when null, return-trip shortcuts are hidden.
 */
@Composable
fun TripsScreen(
    onOpenTrip: (tripId: String) -> Unit,
    onNewTrip: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    selectedTripId: String? = null,
    onEditTrip: ((tripId: String) -> Unit)? = null,
    onCreateReturnTrip: ((outboundTripId: String) -> Unit)? = null,
) {
    val viewModel = metroViewModel<TripsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnOpenTrip by rememberUpdatedState(onOpenTrip)
    val currentOnEditTrip by rememberUpdatedState(onEditTrip)
    val deletedLabel = stringResource(R.string.trip_deleted)
    val undoLabel = stringResource(R.string.trip_undo)
    val duplicatedLabel = stringResource(R.string.trip_duplicated)
    val editLabel = stringResource(R.string.trip_action_edit)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is TripsEvent.OpenTrip -> currentOnOpenTrip(event.tripId)
                is TripsEvent.Deleted -> launch {
                    val result = snackbarHostState.showSnackbar(deletedLabel, undoLabel, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.trip)
                }
                is TripsEvent.Duplicated -> launch {
                    val edit = currentOnEditTrip
                    val result = snackbarHostState.showSnackbar(duplicatedLabel, if (edit != null) editLabel else null)
                    if (result == SnackbarResult.ActionPerformed) edit?.invoke(event.tripId)
                }
            }
        }
    }

    TripsContent(
        state = state,
        callbacks = TripsCallbacks(
            onOpenTrip = onOpenTrip,
            onNewTrip = onNewTrip,
            onOpenSettings = onOpenSettings,
            onEditTrip = onEditTrip,
            onCreateReturnTrip = onCreateReturnTrip,
            onTryDemo = viewModel::createDemoTrip,
            onDelete = viewModel::delete,
            onDuplicate = viewModel::duplicate,
        ),
        modifier = modifier,
        selectedTripId = selectedTripId,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Trip editor. [tripId] null = new trip; [returnOfTripId] prefills a return trip of that outbound trip.
 * [onDone] receives the saved trip id (null if discarded).
 *
 * [onBack] is called for the close button and system back when there's nothing to lose; with unsaved changes
 * the editor asks first, then calls [onDone] with null when the user discards.
 */
@Composable
fun TripEditorScreen(
    tripId: String?,
    onDone: (savedTripId: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    returnOfTripId: String? = null,
) {
    val args = TripEditorArgs(tripId = tripId, returnOfTripId = returnOfTripId)
    val viewModel = assistedMetroViewModel<TripEditorViewModel, TripEditorViewModel.Factory>(
        key = "trip-editor:${args.tripId}:${args.returnOfTripId}",
    ) { create(args) }
    TripEditorRoute(viewModel = viewModel, onDone = onDone, onBack = onBack, modifier = modifier)
}
