package dev.sebastiano.clockblocker.opus.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Lightweight stand-ins for the feature screens: each fills its pane with a tinted, tagged column (so
 * screenshots show pane bounds) and exposes its callbacks as tagged buttons.
 */
object FakeDestinations : AppDestinations {
    /** New trips saved by the fake editor get this id. */
    const val NewTripId = "new-trip"

    @Composable
    override fun Onboarding(onFinished: () -> Unit) {
        FakeScreen("screen_onboarding", "Onboarding", MaterialTheme.colorScheme.primaryContainer) {
            Button(onClick = onFinished, modifier = Modifier.testTag("onboarding_finish")) { Text("Finish") }
        }
    }

    @Composable
    override fun Plan(
        tripId: String?,
        onBack: (() -> Unit)?,
        onEditTrip: (tripId: String) -> Unit,
        onCreateReturnTrip: (outboundTripId: String) -> Unit,
        onNewTrip: () -> Unit,
    ) {
        val id = tripId ?: "now"
        FakeScreen("screen_plan_$id", if (tripId == null) "Now" else "Plan $tripId", MaterialTheme.colorScheme.tertiaryContainer) {
            if (onBack != null) Button(onClick = onBack, modifier = Modifier.testTag("plan_up")) { Text("Up") }
            Button(onClick = { onEditTrip(tripId ?: "current") }, modifier = Modifier.testTag("plan_edit")) { Text("Edit") }
            Counter("plan_counter_$id")
        }
    }

    @Composable
    override fun PlanPlaceholder(onNewTrip: () -> Unit) {
        FakeScreen("screen_placeholder", "Pick a trip", MaterialTheme.colorScheme.surfaceContainerHigh) {
            Button(onClick = onNewTrip, modifier = Modifier.testTag("placeholder_new")) { Text("New trip") }
        }
    }

    @Composable
    override fun Trips(
        selectedTripId: String?,
        onOpenTrip: (tripId: String) -> Unit,
        onNewTrip: () -> Unit,
        onOpenSettings: () -> Unit,
        onEditTrip: (tripId: String) -> Unit,
        onCreateReturnTrip: (outboundTripId: String) -> Unit,
    ) {
        FakeScreen("screen_trips", "Trips", MaterialTheme.colorScheme.secondaryContainer) {
            Text("selected=${selectedTripId ?: "none"}", modifier = Modifier.testTag("trips_selected"))
            Button(onClick = { onOpenTrip("a") }, modifier = Modifier.testTag("trips_open_a")) { Text("Trip A") }
            Button(onClick = { onOpenTrip("b") }, modifier = Modifier.testTag("trips_open_b")) { Text("Trip B") }
            Button(onClick = onNewTrip, modifier = Modifier.testTag("trips_new")) { Text("New trip") }
        }
    }

    @Composable
    override fun TripEditor(
        tripId: String?,
        returnOfTripId: String?,
        onDone: (savedTripId: String?) -> Unit,
        onBack: () -> Unit,
    ) {
        FakeScreen("screen_editor", "Editor ${tripId ?: "new"}", MaterialTheme.colorScheme.surfaceContainerHighest) {
            Button(onClick = { onDone(tripId ?: NewTripId) }, modifier = Modifier.testTag("editor_save")) { Text("Save") }
            Button(onClick = onBack, modifier = Modifier.testTag("editor_close")) { Text("Close") }
        }
    }

    @Composable
    override fun Settings(onOpenAbout: () -> Unit, onReplayOnboarding: () -> Unit) {
        FakeScreen("screen_settings", "Settings", MaterialTheme.colorScheme.surfaceContainer) {
            Button(onClick = onOpenAbout, modifier = Modifier.testTag("settings_about")) { Text("About") }
            Button(onClick = onReplayOnboarding, modifier = Modifier.testTag("settings_replay")) { Text("Replay") }
        }
    }

    @Composable
    override fun About(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
        FakeScreen("screen_about", "About", MaterialTheme.colorScheme.surfaceContainer) {
            Button(onClick = onOpenLicenses, modifier = Modifier.testTag("about_licenses")) { Text("Licenses") }
        }
    }

    @Composable
    override fun Licenses(onBack: () -> Unit) {
        FakeScreen("screen_licenses", "Licenses", MaterialTheme.colorScheme.surfaceContainer) {}
    }
}

/** A saveable counter: proves per-entry saved state survives navigation and restoration. */
@Composable
private fun Counter(tag: String) {
    var count by rememberSaveable { mutableIntStateOf(0) }
    Button(onClick = { count++ }, modifier = Modifier.testTag(tag)) { Text("count=$count") }
}

@Composable
private fun FakeScreen(tag: String, title: String, color: Color, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .safeDrawingPadding()
            .padding(16.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        content()
    }
}
