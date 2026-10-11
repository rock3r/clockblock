package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.component.PlaceResultRow
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import java.time.Instant

/**
 * Matches for the airport search, or popular airports while its query is still empty. Each row shows the
 * place's flag, its live local time and UTC offset and a tiny day/night sky, so the time difference is felt while
 * picking.
 */
@Composable
internal fun PlaceResults(search: PlaceSearchState, now: Instant, onSelect: (Place) -> Unit, modifier: Modifier = Modifier) {
    if (search.results.isEmpty() && !search.searching && !search.popular) {
        Text(
            stringResource(R.string.editor_place_no_results, search.query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        return
    }
    if (search.results.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            if (search.popular) {
                Text(
                    stringResource(R.string.editor_place_popular),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp).semantics { heading() },
                )
            }
            search.results.forEachIndexed { index, place ->
                // Inset to the text column: row padding 16 + code chip 48 + gap 12.
                if (index > 0) HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                PlaceResultRow(place, now, onClick = { onSelect(place) }, modifier = Modifier.testTag(TripsTestTags.placeResult(place.displayCode)))
            }
        }
    }
}
