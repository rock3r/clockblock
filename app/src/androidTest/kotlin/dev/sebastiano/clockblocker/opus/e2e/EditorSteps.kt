package dev.sebastiano.clockblocker.opus.e2e

import android.icu.text.DateFormat
import android.icu.util.TimeZone
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

/** Editor steps shared by the trip-creation and rotation tests. */

/**
 * Opens the editor from Trips: the empty state's "Plan a trip" button when there are no trips (the FAB is hidden
 * there), otherwise the FAB menu's "New trip".
 */
fun OpusE2eTest.openNewTrip() {
    when (awaitAnyTag(TripsTestTags.EmptyPlanTrip, TripsTestTags.Fab)) {
        TripsTestTags.EmptyPlanTrip -> awaitTag(TripsTestTags.EmptyPlanTrip).scrollToIfScrollable().performClick()
        else -> {
            awaitTag(TripsTestTags.Fab).performClick()
            awaitTag(TripsTestTags.NewTrip).performClick()
        }
    }
    awaitTag("route_trip_editor").assertIsDisplayed()
}

/** Types [query] into a place field and picks the result with IATA [code]. */
fun OpusE2eTest.pickPlace(fieldTag: String, query: String, code: String) {
    awaitTag(fieldTag).performTextInput(query)
    awaitTag(TripsTestTags.placeResult(code)).performClick()
    awaitGone(TripsTestTags.placeResult(code))
}

/**
 * The day the trip departs: tomorrow, unless that's next month (the date picker opens on the current month and
 * the test shouldn't depend on month navigation), in which case today.
 */
fun departureDay(): LocalDate {
    val today = LocalDate.now(ZoneId.systemDefault())
    return today.plusDays(1).takeIf { it.month == today.month } ?: today
}

/**
 * Picks [date] in the open Material date picker. Day cells are announced with the full date (ICU skeleton
 * `yMMMMEEEEd`, in UTC like the picker itself), optionally prefixed with "Today".
 */
fun OpusE2eTest.pickDateInOpenPicker(date: LocalDate) {
    val millis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val label = DateFormat.getInstanceForSkeleton("yMMMMEEEEd", Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))
    await(hasText(label, substring = true) and hasClickAction()).performClick()
    awaitTag(TripsTestTags.PickerConfirm).assertIsEnabled().performClick()
    awaitGone(TripsTestTags.PickerConfirm)
}

/** Opens a time picker field and confirms the time it offers (09:00 for an empty field). */
fun OpusE2eTest.confirmTimePicker(fieldTag: String) {
    awaitTag(fieldTag).scrollToIfScrollable().performClick()
    awaitTag(TripsTestTags.PickerConfirm).performClick()
    awaitGone(TripsTestTags.PickerConfirm)
}
