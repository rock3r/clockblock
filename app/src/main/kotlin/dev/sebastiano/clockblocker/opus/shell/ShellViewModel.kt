package dev.sebastiano.clockblocker.opus.shell

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.ProfileRepository
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the shell needs before it can draw anything: onboarding state, whether trips exist, and settings. */
sealed interface ShellUiState {
    /** Repositories haven't emitted yet; the splash screen stays up. */
    data object Loading : ShellUiState

    /**
     * @property hasProfile false until onboarding completes (and again if the profile is cleared).
     * @property hasTrips whether at least one trip exists (decides Now vs Trips as home).
     * @property sleep the profile's habitual sleep window (null before onboarding); with [currentPlan] it decides
     *   "body night", when the whole app switches to Calm motion (see [isBodyNight]).
     * @property currentPlan the plan in progress or next up, whose body-clock estimate defines body night.
     */
    @Immutable
    data class Ready(
        val hasProfile: Boolean,
        val hasTrips: Boolean,
        val settings: AppSettings,
        val sleep: SleepWindow? = null,
        val currentPlan: JetLagPlan? = null,
    ) : ShellUiState
}

/** Feeds the app shell (theme, gating, start destination). Shared by the activity's splash screen and UI. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class ShellViewModel(
    profileRepository: ProfileRepository,
    tripRepository: TripRepository,
    settingsRepository: SettingsRepository,
    planRepository: PlanRepository,
) : ViewModel() {

    /** Starts eagerly so the splash screen can be dismissed as soon as the first values arrive. */
    val uiState: StateFlow<ShellUiState> = combine(
        // Only the parts the shell uses: profile edits other than the sleep window don't re-emit.
        profileRepository.profile.map { ProfileFacts(hasProfile = it != null, sleep = it?.sleep) }.distinctUntilChanged(),
        tripRepository.trips.map { it.isNotEmpty() }.distinctUntilChanged(),
        settingsRepository.settings,
        planRepository.currentPlan.distinctUntilChanged(),
    ) { profile, hasTrips, settings, plan ->
        ShellUiState.Ready(profile.hasProfile, hasTrips, settings, profile.sleep, plan)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ShellUiState.Loading)

    private data class ProfileFacts(val hasProfile: Boolean, val sleep: SleepWindow?)
}
