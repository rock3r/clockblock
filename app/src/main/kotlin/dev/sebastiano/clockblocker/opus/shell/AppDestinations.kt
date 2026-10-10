package dev.sebastiano.clockblocker.opus.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.sebastiano.clockblocker.opus.debug.DebugMenu
import dev.sebastiano.clockblocker.opus.feature.onboarding.OnboardingScreen
import dev.sebastiano.clockblocker.opus.feature.plan.PlanEmptyPane
import dev.sebastiano.clockblocker.opus.feature.plan.PlanScreen
import dev.sebastiano.clockblocker.opus.feature.settings.AboutScreen
import dev.sebastiano.clockblocker.opus.feature.settings.LicensesScreen
import dev.sebastiano.clockblocker.opus.feature.settings.SettingsScreen
import dev.sebastiano.clockblocker.opus.feature.trips.TripEditorScreen
import dev.sebastiano.clockblocker.opus.feature.trips.TripsScreen

/**
 * The screens the shell can show. Production uses [FeatureDestinations] (the feature modules' entry
 * composables); shell tests substitute lightweight fakes so navigation is tested independently of features.
 */
@Stable
interface AppDestinations {
    @Composable
    fun Onboarding(onFinished: () -> Unit)

    /** [tripId] null = the current plan (Now). [onBack] null = no up affordance. */
    @Composable
    fun Plan(
        tripId: String?,
        onBack: (() -> Unit)?,
        onEditTrip: (tripId: String) -> Unit,
        onCreateReturnTrip: (outboundTripId: String) -> Unit,
        onNewTrip: () -> Unit,
    )

    /** Detail-pane placeholder next to the trips list when no trip is open. */
    @Composable
    fun PlanPlaceholder(onNewTrip: () -> Unit)

    @Composable
    fun Trips(
        selectedTripId: String?,
        onOpenTrip: (tripId: String) -> Unit,
        onNewTrip: () -> Unit,
        onOpenSettings: () -> Unit,
        onEditTrip: (tripId: String) -> Unit,
        onCreateReturnTrip: (outboundTripId: String) -> Unit,
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
    override fun Onboarding(onFinished: () -> Unit) {
        OnboardingScreen(onFinished = onFinished)
    }

    @Composable
    override fun Plan(
        tripId: String?,
        onBack: (() -> Unit)?,
        onEditTrip: (tripId: String) -> Unit,
        onCreateReturnTrip: (outboundTripId: String) -> Unit,
        onNewTrip: () -> Unit,
    ) {
        PlanScreen(
            tripId = tripId,
            onBack = onBack,
            onEditTrip = onEditTrip,
            onCreateReturnTrip = onCreateReturnTrip,
            onNewTrip = onNewTrip,
        )
    }

    @Composable
    override fun PlanPlaceholder(onNewTrip: () -> Unit) {
        PlanEmptyPane(onNewTrip = onNewTrip)
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
        TripsScreen(
            onOpenTrip = onOpenTrip,
            onNewTrip = onNewTrip,
            onOpenSettings = onOpenSettings,
            selectedTripId = selectedTripId,
            onEditTrip = onEditTrip,
            onCreateReturnTrip = onCreateReturnTrip,
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
        val context = LocalContext.current
        SettingsScreen(
            onBack = null,
            onOpenAbout = onOpenAbout,
            onReplayOnboarding = onReplayOnboarding,
            // Debug builds only: the release DebugMenu returns null, so release Settings has no Debug section.
            onOpenDebug = remember(context) { DebugMenu.opener(context) },
        )
    }

    @Composable
    override fun About(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
        AboutScreen(onBack = onBack, onOpenLicenses = onOpenLicenses)
    }

    @Composable
    override fun Licenses(onBack: () -> Unit) {
        LicensesScreen(onBack = onBack)
    }
}
