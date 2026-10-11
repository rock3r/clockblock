package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import java.time.Instant

/**
 * The airport search, in a bottom sheet over the ticket: opened by tapping a code, it searches offline as you type
 * (popular airports while the query is empty), and each result shows the place's local time and UTC offset. Enter
 * picks the top match. [onPick] decides what happens next (the editor moves on from From to an empty To).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceSearchSheet(
    search: PlaceSearchState,
    legCount: Int,
    now: Instant,
    onQueryChange: (String) -> Unit,
    onPick: (Place) -> Unit,
    onPickTop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(TripsTestTags.EditorPlaceSheet),
    ) {
        PlaceSearchPanel(search, legCount, now, onQueryChange, onPick, onPickTop)
    }
}

/** The sheet's content, on its own so it can be screenshot-tested without a window. */
@Composable
internal fun PlaceSearchPanel(
    search: PlaceSearchState,
    legCount: Int,
    now: Instant,
    onQueryChange: (String) -> Unit,
    onPick: (Place) -> Unit,
    onPickTop: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    // Focus follows the field: after picking From, the sheet asks for To with the keyboard still up.
    if (autoFocus) LaunchedEffect(search.field) { focus.requestFocus() }
    val title = stringResource(
        if (search.field.end == LegEnd.Origin) R.string.editor_place_sheet_from else R.string.editor_place_sheet_to,
    )
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Text(
            if (legCount > 1) stringResource(R.string.editor_place_sheet_title_leg, title, search.field.legIndex + 1) else title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = search.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(TripsTestTags.EditorPlaceSearch),
            placeholder = { Text(stringResource(R.string.editor_place_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_trips_search), contentDescription = null) },
            supportingText = if (search.query.isNotBlank()) {
                { Text(stringResource(R.string.editor_place_pick_hint)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { onPickTop() }, onDone = { onPickTop() }),
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            PlaceResults(search = search, now = now, onSelect = onPick)
        }
    }
}
