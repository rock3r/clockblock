package dev.sebastiano.clockblocker.opus.feature.onboarding

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.clockblocker.opus.core.data.EasterEggGate
import dev.sebastiano.clockblocker.opus.core.data.PlaceSearch
import dev.sebastiano.clockblocker.opus.core.data.ProfileRepository
import dev.sebastiano.clockblocker.opus.core.data.time.DeviceZone
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationPermissionState
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationPermissions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/** The onboarding steps, in order (design.md §1.8: ≤ 6 screens, no account). */
enum class OnboardingStep {
    Welcome,
    HomeZone,
    Sleep,
    Chronotype,
    Tools,
    Reminders,
    ;

    /** 0-based position, for the progress indicator. */
    val index: Int get() = ordinal

    companion object {
        val Count: Int = entries.size
    }
}

/**
 * Everything the onboarding UI renders.
 *
 * @property profile the draft profile; saved only on [OnboardingViewModel.finish].
 * @property homePlace the place picked in search, or `null` when the zone came from the device or a saved profile.
 * @property usingDeviceZone the draft zone is the phone's own.
 * @property melatoninAcknowledged the user has read and accepted the melatonin safety note.
 * @property isFinished the profile has been saved; the route calls `onFinished()`.
 * @property easterEggs the sleep dial's 24.2 egg may run ([EasterEggGate]: Reduce motion off, plan not saying sleep).
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val profile: UserProfile,
    val deviceZoneId: String,
    val homePlace: Place? = null,
    val query: String = "",
    val results: ImmutableList<Place> = persistentListOf(),
    val melatoninAcknowledged: Boolean = false,
    val permissions: NotificationPermissionState,
    /** When the flow opened; used to show "now" in candidate home zones. */
    val now: Instant = Instant.EPOCH,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
    val easterEggs: Boolean = false,
) {
    val usingDeviceZone: Boolean get() = profile.homeZoneId == deviceZoneId && homePlace == null
}

/**
 * Drives the onboarding flow: a draft [UserProfile] edited step by step and saved once at the end. When onboarding
 * is replayed from Settings, the draft starts from the saved profile.
 */
@Inject
@ViewModelKey(OnboardingViewModel::class)
@ContributesIntoMap(AppScope::class)
class OnboardingViewModel(
    private val profiles: ProfileRepository,
    private val places: PlaceSearch,
    private val notificationPermissions: NotificationPermissions,
    clock: Clock,
    private val deviceZones: DeviceZone,
    eggs: EasterEggGate,
) : ViewModel() {
    // Not clock.zone: the app Clock is UTC by contract, which made a fresh install's home zone "Z".
    private val deviceZone get() = deviceZones.current().id
    private val _state = MutableStateFlow(
        deviceZone.let { zone ->
            OnboardingUiState(
                profile = UserProfile(homeZoneId = zone),
                deviceZoneId = zone,
                permissions = notificationPermissions.state(),
                now = clock.instant(),
            )
        },
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var touched = false
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = profiles.profile.first() ?: return@launch
            if (!touched) {
                _state.update { it.copy(profile = saved, melatoninAcknowledged = saved.useMelatonin) }
            }
        }
        viewModelScope.launch {
            eggs.allowed.collect { allowed -> _state.update { it.copy(easterEggs = allowed) } }
        }
    }

    /** The runtime permission to request on the Reminders step (`null` below API 33). */
    val runtimePermission: String? get() = notificationPermissions.runtimePermission

    fun exactAlarmSettingsIntent(): Intent = notificationPermissions.exactAlarmSettingsIntent()

    fun notificationSettingsIntent(): Intent = notificationPermissions.notificationSettingsIntent()

    fun next() {
        _state.update { s -> s.copy(step = OnboardingStep.entries.getOrElse(s.step.ordinal + 1) { s.step }) }
    }

    /** Goes to the previous step. Returns `false` on the first step, so the system handles back. */
    fun back(): Boolean {
        val current = _state.value.step
        if (current == OnboardingStep.Welcome) return false
        _state.update { it.copy(step = OnboardingStep.entries[current.ordinal - 1]) }
        return true
    }

    /** "Skip": keep the defaults for everything in between and go straight to Reminders. */
    fun skipToEnd() {
        _state.update { it.copy(step = OnboardingStep.Reminders) }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(results = persistentListOf()) }
            return
        }
        searchJob = viewModelScope.launch {
            val found = places.search(query.trim(), limit = 8).toImmutableList()
            _state.update { if (it.query == query) it.copy(results = found) else it }
        }
    }

    fun selectPlace(place: Place) {
        searchJob?.cancel()
        edit { it.copy(homeZoneId = place.zoneId) }
        _state.update { it.copy(homePlace = place, query = "", results = persistentListOf()) }
    }

    fun useDeviceZone() {
        val zone = deviceZone
        edit { it.copy(homeZoneId = zone) }
        _state.update { it.copy(homePlace = null, deviceZoneId = zone) }
    }

    fun setSleep(window: SleepWindow) = edit { it.copy(sleep = window) }

    fun setChronotype(chronotype: Chronotype) = edit { it.copy(chronotype = chronotype) }

    fun setIntensity(intensity: Intensity) = edit { it.copy(intensity = intensity) }

    /** Generic edit for the tool toggles (caffeine, planes, pre-departure). Melatonin goes through [setMelatonin]. */
    fun updateProfile(transform: (UserProfile) -> UserProfile) = edit { p ->
        transform(p).copy(useMelatonin = p.useMelatonin)
    }

    /** Melatonin can only be switched on once the safety note has been acknowledged. */
    fun setMelatonin(enabled: Boolean) {
        if (enabled && !_state.value.melatoninAcknowledged) return
        edit { it.copy(useMelatonin = enabled) }
    }

    fun acknowledgeMelatoninNote(acknowledged: Boolean) {
        touched = true
        _state.update {
            it.copy(
                melatoninAcknowledged = acknowledged,
                profile = if (acknowledged) it.profile else it.profile.copy(useMelatonin = false),
            )
        }
    }

    /** Re-reads notification permissions (call on resume and after a permission result). */
    fun refreshPermissions() {
        _state.update { it.copy(permissions = notificationPermissions.state()) }
    }

    /** Saves the profile, then flags [OnboardingUiState.isFinished]. Idempotent. */
    fun finish() {
        val current = _state.value
        if (current.isSaving || current.isFinished) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            profiles.save(_state.value.profile)
            _state.update { it.copy(isSaving = false, isFinished = true) }
        }
    }

    private fun edit(transform: (UserProfile) -> UserProfile) {
        touched = true
        _state.update { it.copy(profile = transform(it.profile)) }
    }
}
