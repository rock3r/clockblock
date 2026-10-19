package dev.sebastiano.clockblocker.opus.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import dev.sebastiano.clockblocker.opus.debug.DebugMenu
import dev.sebastiano.clockblocker.opus.feature.onboarding.OnboardingScreen
import dev.sebastiano.clockblocker.opus.feature.plan.PlanScreen
import dev.sebastiano.clockblocker.opus.feature.settings.AboutScreen
import dev.sebastiano.clockblocker.opus.feature.settings.LicensesScreen
import dev.sebastiano.clockblocker.opus.R
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import androidx.compose.runtime.getValue
import dev.sebastiano.clockblocker.opus.feature.settings.SettingsScreen
import dev.sebastiano.clockblocker.opus.feature.trips.TripEditorScreen
import dev.sebastiano.clockblocker.opus.feature.trips.TripsScreen

/**
 * The screens the shell can show. Production uses [FeatureDestinations] (the feature modules' entry
 * composables); shell tests substitute lightweight fakes so navigation is tested independently of features.
 */
@Stable
interface AppDestinations {
    /** [onTakeoff]: onboarding's finish is taking off; the shell's sweep will follow [onFinished]. */
    @Composable
    fun Onboarding(onFinished: () -> Unit, onTakeoff: () -> Unit)

    /** [tripId] null = the current plan (Now). [onBack] null = no up affordance. */
    @Composable
    fun Plan(
        tripId: String?,
        onBack: (() -> Unit)?,
        onEditTrip: (tripId: String) -> Unit,
        onCreateReturnTrip: (outboundTripId: String) -> Unit,
        onNewTrip: () -> Unit,
        onOpenTrips: (() -> Unit)? = null,
    )

    @Composable
    fun Trips(
        selectedTripId: String?,
        onOpenTrip: (tripId: String) -> Unit,
        onNewTrip: () -> Unit,
        onOpenSettings: () -> Unit,
        onEditTrip: (tripId: String) -> Unit,
        onCreateReturnTrip: (outboundTripId: String) -> Unit,
        onOpenNow: () -> Unit = {},
    )

    @Composable
    fun TripEditor(
        tripId: String?,
        returnOfTripId: String?,
        onDone: (savedTripId: String?) -> Unit,
        onBack: () -> Unit,
    )

    @Composable
    fun Settings(onOpenAbout: () -> Unit, onReplayOnboarding: () -> Unit)

    @Composable
    fun About(onBack: () -> Unit, onOpenLicenses: () -> Unit)

    @Composable
    fun Licenses(onBack: () -> Unit)
}

/** The real screens from `:feature:*`. */
object FeatureDestinations : AppDestinations {
    @Composable
    override fun Onboarding(onFinished: () -> Unit, onTakeoff: () -> Unit) {
        OnboardingScreen(onFinished = onFinished, onTakeoff = onTakeoff)
    }

    @Composable
    override fun Plan(
        tripId: String?,
        onBack: (() -> Unit)?,
        onEditTrip: (tripId: String) -> Unit,
        onCreateReturnTrip: (outboundTripId: String) -> Unit,
        onNewTrip: () -> Unit,
        onOpenTrips: (() -> Unit)?,
    ) {
        PlanScreen(
            tripId = tripId,
            onBack = onBack,
            onEditTrip = onEditTrip,
            onCreateReturnTrip = onCreateReturnTrip,
            onNewTrip = onNewTrip,
            onOpenTrips = onOpenTrips,
        )
    }

    @Composable
    override fun Trips(
        selectedTripId: String?,
        onOpenTrip: (tripId: String) -> Unit,
        onNewTrip: () -> Unit,
        onOpenSettings: () -> Unit,
        onEditTrip: (tripId: String) -> Unit,
        onCreateReturnTrip: (outboundTripId: String) -> Unit,
        onOpenNow: () -> Unit,
    ) {
        TripsScreen(
            onOpenTrip = onOpenTrip,
            onNewTrip = onNewTrip,
            onOpenSettings = onOpenSettings,
            selectedTripId = selectedTripId,
            onEditTrip = onEditTrip,
            onCreateReturnTrip = onCreateReturnTrip,
            onOpenNow = onOpenNow,
        )
    }

    @Composable
    override fun TripEditor(
        tripId: String?,
        returnOfTripId: String?,
        onDone: (savedTripId: String?) -> Unit,
        onBack: () -> Unit,
    ) {
        TripEditorScreen(tripId = tripId, onDone = onDone, onBack = onBack, returnOfTripId = returnOfTripId)
    }

    @Composable
    override fun Settings(onOpenAbout: () -> Unit, onReplayOnboarding: () -> Unit) {
        SettingsScreen(
            onBack = null,
            onOpenAbout = onOpenAbout,
            onReplayOnboarding = onReplayOnboarding,
            // Debug builds only: the release DebugMenu has no section, so release Settings has no Debug section.
            extraSection = DebugMenu.settingsSection,
        )
    }

    @Composable
    override fun About(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
        AboutScreen(onBack = onBack, onOpenLicenses = onOpenLicenses)
    }

    @Composable
    override fun Licenses(onBack: () -> Unit) {
        // Collected at build time by the AboutLibraries plugin (app/build.gradle.kts), read off the main thread.
        val libraries by produceLibraries(R.raw.aboutlibraries)
        LicensesScreen(libraries = libraries, onBack = onBack)
    }
}
