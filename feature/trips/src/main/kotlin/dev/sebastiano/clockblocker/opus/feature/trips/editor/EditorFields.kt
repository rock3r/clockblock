package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import dev.sebastiano.clockblocker.opus.feature.trips.ui.countryName
import dev.sebastiano.clockblocker.opus.feature.trips.ui.utcOffsetLabel
import java.time.Instant

/**
 * Airport/city field with search-as-you-type. Results (already ranked by [dev.sebastiano.clockblocker.opus.core.data.PlaceSearch])
 * appear under the field while it has focus; the IME action picks the top match so typing "lis⏎" is enough.
 */
@Composable
internal fun PlaceField(
    label: String,
    input: PlaceInput,
    search: PlaceSearchState?,
    now: Instant,
    onQueryChange: (String) -> Unit,
    onSelect: (Place) -> Unit,
    onImeAction: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    var focused by remember { mutableStateOf(false) }
    val place = input.place
    Column(modifier) {
        OutlinedTextField(
            value = input.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .testTag(tag),
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.editor_place_placeholder)) },
            leadingIcon = if (place != null) {
                { CodeChip(place.displayCode, Modifier.padding(start = 8.dp)) }
            } else {
                { Icon(painterResource(R.drawable.ic_trips_search), contentDescription = null) }
            },
            supportingText = when {
                place != null -> {
                    { Text(placeDetails(place, now), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                focused && input.query.isNotBlank() -> {
                    { Text(stringResource(R.string.editor_place_pick_hint)) }
                }
                else -> null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }),
        )
        if (focused && place == null && search != null && search.query == input.query) {
            PlaceResults(search, now, onSelect, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PlaceResults(search: PlaceSearchState, now: Instant, onSelect: (Place) -> Unit, modifier: Modifier = Modifier) {
    if (search.results.isEmpty() && !search.searching) {
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
            search.results.forEachIndexed { index, place ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                PlaceResultRow(place, now, onClick = { onSelect(place) })
            }
        }
    }
}

@Composable
private fun PlaceResultRow(place: Place, now: Instant, onClick: () -> Unit) {
    val country = countryName(place.countryCode)
    val offset = place.utcOffsetLabel(now)
    val description = stringResource(
        R.string.editor_place_result_description,
        place.displayCode,
        place.city,
        place.name,
        country,
        offset,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(TripsTestTags.placeResult(place.displayCode))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CodeChip(place.displayCode, Modifier.widthIn(min = 48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOf(place.city, country).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                place.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(offset, style = OpusTheme.textStyles.timeLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The IATA code in a small tonal box (tabular figures, so codes line up in result lists). */
@Composable
internal fun CodeChip(code: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            code,
            style = OpusTheme.textStyles.timeLabel,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun placeDetails(place: Place, now: Instant): String =
    stringResource(R.string.editor_place_supporting, place.name, countryName(place.countryCode), place.utcOffsetLabel(now))

/**
 * A field-shaped button that opens a date or time picker. Focusable and Enter-activatable, so keyboard users
 * tab through it like a text field.
 */
@Composable
internal fun PickerField(
    label: String,
    value: String?,
    icon: Int,
    onClick: () -> Unit,
    pickLabel: String,
    tag: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    accessibilityLabel: String = label,
) {
    val colors = MaterialTheme.colorScheme
    val borderColor = if (isError) colors.error else colors.outline
    val description = stringResource(R.string.editor_field_value, accessibilityLabel, value ?: pickLabel)
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 56.dp)
            .testTag(tag)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = pickLabel) { onClick(); true }
            },
        shape = OutlinedTextFieldDefaults.shape,
        color = colors.surface.copy(alpha = 0f),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(
                    value ?: pickLabel,
                    style = if (value != null) OpusTheme.textStyles.timeTitle else MaterialTheme.typography.bodyLarge,
                    color = if (value != null) LocalContentColor.current else colors.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
