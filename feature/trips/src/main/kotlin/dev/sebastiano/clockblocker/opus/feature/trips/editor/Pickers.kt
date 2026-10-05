package dev.sebastiano.clockblocker.opus.feature.trips.editor

import android.text.format.DateFormat
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Which value a picker dialog edits. */
internal sealed interface PickerTarget {
    data class DepartureDate(val legIndex: Int) : PickerTarget
    data class DepartureTime(val legIndex: Int) : PickerTarget
    data class ArrivalDate(val legIndex: Int) : PickerTarget
    data class ArrivalTime(val legIndex: Int) : PickerTarget
    data object ReturnDate : PickerTarget
    data object ReturnTime : PickerTarget
}

/** M3 date picker in a dialog. Material's picker works in UTC millis; we only ever hand it calendar dates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalDatePickerDialog(
    title: String,
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
                enabled = state.selectedDateMillis != null,
                modifier = Modifier.testTag(TripsTestTags.PickerConfirm),
            ) { Text(stringResource(R.string.editor_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    ) {
        DatePicker(
            state = state,
            title = { Text(title, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
        )
    }
}

/**
 * M3 time picker in a dialog; 12/24 h follows the system setting. The mode toggle switches to keyboard
 * input (TimeInput), the fastest way to type "19:30".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalTimePickerDialog(
    title: String,
    initial: LocalTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val context = LocalContext.current
    val start = initial ?: DefaultPickerTime
    val state = rememberTimePickerState(start.hour, start.minute, is24Hour = DateFormat.is24HourFormat(context))
    var keyboard by rememberSaveable { mutableStateOf(false) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(LocalTime.of(state.hour, state.minute))
                    onDismiss()
                },
                modifier = Modifier.testTag(TripsTestTags.PickerConfirm),
            ) { Text(stringResource(R.string.editor_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
        modeToggleButton = {
            IconButton(onClick = { keyboard = !keyboard }) {
                Icon(
                    painterResource(if (keyboard) R.drawable.ic_trips_schedule else R.drawable.ic_trips_edit),
                    contentDescription = stringResource(R.string.editor_pick_time),
                )
            }
        },
    ) {
        if (keyboard) TimeInput(state = state) else TimePicker(state = state)
    }
}

private val DefaultPickerTime: LocalTime = LocalTime.of(9, 0)
