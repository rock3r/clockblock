package dev.sebastiano.clockblocker.opus.feature.onboarding

import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Immutable
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
import dev.sebastiano.clockblocker.opus.core.notifications.NowNotificationContent
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ChronotypeEstimate
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/**
 * The onboarding screens, in order (design.md §1.8: ≤ 6 screens, no account). Five steps to answer: the
 * [Chronotype] reveal is the Sleep step's answer, not a question of its own, so it shares the Sleep step's place in
 * the count ("Step 3 of 5").
 */
enum class OnboardingStep {
    Welcome,
    HomeZone,
    Sleep,

    /** The chronotype worked out from the Sleep step's night, with a way to pick another. */
    Chronotype,
    Tools,
    Reminders,
    ;

    /** 0-based position in the progress count; the [Chronotype] reveal shares the Sleep step's. */
    val index: Int get() = if (ordinal <= Sleep.ordinal) ordinal else ordinal - 1

    companion object {
        /** Steps in the progress count. */
        val Count: Int = entries.size - 1
    }
}

/**
 * Everything the onboarding UI renders.
 *
 * @property profile the draft profile; saved only on [OnboardingViewModel.finish]. Its sleep is the usual night on work
 *   days (the planner's habitual sleep); days off stay linked to it until set on their own.
 * @property homePlace the place picked in search, or `null` when the zone came from the device or a saved profile.
 * @property usingDeviceZone the draft zone is the phone's own.
 * @property melatoninAcknowledged the user has read and accepted the melatonin safety note.
 * @property chronotypePicked the user chose the chronotype (in the reveal's sheet, or in a profile saved before this
 *   flow): the nights no longer set it. Choosing the suggested one hands it back to the nights.
 * @property notificationsAsked the notification permission prompt has answered at least once in this flow.
 * @property notificationPreview the real Now notification's content for a sample plan, when this device can play it;
 *   `null` draws the standard notification's look.
 * @property easterEggs the sleep dial's 24.2 egg may run ([EasterEggGate]: Reduce motion off, plan not saying sleep).
 */
@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val profile: UserProfile,
    val deviceZoneId: String,
    val homePlace: Place? = null,
    val query: String = "",
    val results: ImmutableList<Place> = persistentListOf(),
    val searchMatchingQuery: String = "",
    val melatoninAcknowledged: Boolean = false,
    val permissions: NotificationPermissionState,
    val chronotypePicked: Boolean = false,
    val notificationsAsked: Boolean = false,
    val exactTimingOffered: Boolean = false,
    val notificationPreview: RemoteViews? = null,
    /** When the flow opened; used to show "now" in candidate home zones and in the notification preview. */
    val now: Instant = Instant.EPOCH,
    val easterEggs: Boolean = false,
) {
    val usingDeviceZone: Boolean get() = profile.homeZoneId == deviceZoneId && homePlace == null

    /** What the two usual nights say about the chronotype (MCTQ mid-sleep on days off, corrected for catch-up sleep). */
    val inferred: ChronotypeEstimate.Result get() = profile.inferredChronotype()

    /** The reveal says "Your pick": a chosen chronotype that differs from what the nights say. */
    val showsPick: Boolean get() = chronotypePicked && profile.chronotype != inferred.chronotype

    /** Reminders is done with: notifications are allowed, or the prompt has answered. Next becomes Finish. */
    val remindersHandled: Boolean get() = permissions.notificationsGranted || notificationsAsked

    /**
     * Exact timing is an optional follow-up: offered only once notifications are allowed, and only where the user
     * could grant it (not where it's already allowed, like builds outside Google Play). Once offered it stays, so
     * granting it shows "Allowed" instead of the row disappearing under the finger.
     */
    val showExactTiming: Boolean
        get() = permissions.notificationsGranted && permissions.exactAlarmsRequestable &&
            (!permissions.exactAlarmsAllowed || exactTimingOffered)
}

/**
 * Drives the onboarding flow: a draft [UserProfile] edited step by step and saved once at the end. When onboarding
 * is replayed from Settings, the draft starts from the saved profile.
 *
 * The chronotype follows the two nights set on the Sleep step ([ChronotypeEstimate.estimate]) until the user picks
 * another one on the reveal; a picked chronotype stays when the nights change.
 */
@Inject
@ViewModelKey(OnboardingViewModel::class)
@ContributesIntoMap(AppScope::class)
class OnboardingViewModel(
    private val profiles: ProfileRepository,
    private val places: PlaceSearch,
    private val notificationPermissions: NotificationPermissions,
    private val clock: Clock,
    private val deviceZones: DeviceZone,
    eggs: EasterEggGate,
    private val notificationContent: NowNotificationContent,
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

    private val _finished = MutableStateFlow(false)

    /**
     * The profile has been saved; the route calls `onFinished()`. Apart from [state]: saving happens while the finish
     * takes off, and a new UI state then would recompose the whole screen under it.
     */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private var saving = false

    private var touched = false
    private var searchJob: Job? = null
    private var previewWidthDp: Int? = null

    init {
        viewModelScope.launch {
            val saved = profiles.profile.first() ?: return@launch
            if (!touched) {
                // A saved chronotype was chosen by the user (the old question step, the sheet, or Settings):
                // changing the nights while replaying never overwrites it.
                _state.update { it.copy(profile = saved, melatoninAcknowledged = saved.useMelatonin, chronotypePicked = true) }
            }
        }
        viewModelScope.launch {
            eggs.allowed.collect { allowed -> _state.update { it.copy(easterEggs = allowed) } }
        }
    }

    /** The runtime permission to request on the Reminders step. */
    val runtimePermission: String get() = notificationPermissions.runtimePermission

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

    /**
     * "Skip": keep the defaults for everything in between and go straight to Reminders. The chronotype still
     * follows whatever night is set (the default night gives the default chronotype).
     */
    fun skipToEnd() {
        _state.update { it.copy(step = OnboardingStep.Reminders) }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(results = persistentListOf(), searchMatchingQuery = "") }
            return
        }
        searchJob = viewModelScope.launch {
            val found = places.search(query.trim(), limit = 8).toImmutableList()
            _state.update { if (it.query == query) it.copy(results = found, searchMatchingQuery = query) else it }
        }
    }

    fun selectPlace(place: Place) {
        searchJob?.cancel()
        edit { it.copy(homeZoneId = place.zoneId) }
        _state.update { it.copy(homePlace = place, query = "", results = persistentListOf(), searchMatchingQuery = "") }
    }

    fun useDeviceZone() {
        val zone = deviceZone
        edit { it.copy(homeZoneId = zone) }
        _state.update { it.copy(homePlace = null, deviceZoneId = zone) }
    }

    /** Usual sleep on work days (days off follow while linked). */
    fun setSleep(window: SleepWindow) = editSleep { it.copy(sleep = window) }

    /** Usual sleep on days off; the first change unlinks it from work days. */
    fun setDaysOffSleep(window: SleepWindow) = editSleep { it.copy(daysOffSleep = window) }

    /** A sleep edit; the chronotype follows the nights unless the user picked one ([OnboardingUiState.chronotypePicked]). */
    private fun editSleep(change: (UserProfile) -> UserProfile) {
        val picked = _state.value.chronotypePicked
        edit { p -> change(p).let { next -> if (picked) next else next.copy(chronotype = next.inferredChronotype().chronotype) } }
    }

    /** A chronotype picked on the reveal's "Doesn't sound like me" sheet (picking the inferred one follows the nights again). */
    fun setChronotype(chronotype: Chronotype) {
        edit { it.copy(chronotype = chronotype) }
        _state.update { it.copy(chronotypePicked = chronotype != it.profile.inferredChronotype().chronotype) }
    }

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
        val permissions = notificationPermissions.state()
        _state.update {
            val next = it.copy(permissions = permissions)
            if (next.showExactTiming) next.copy(exactTimingOffered = true) else next
        }
    }

    /** The notification permission prompt answered (granted or not): Reminders can now finish. */
    fun onNotificationsResult() {
        _state.update { it.copy(notificationsAsked = true) }
        refreshPermissions()
    }

    /**
     * Renders the Now notification preview [widthDp] wide (once per width). Failures leave it null: the step then
     * draws the standard notification's look.
     */
    fun loadNotificationPreview(widthDp: Int) {
        if (widthDp <= 0 || previewWidthDp == widthDp) return
        previewWidthDp = widthDp
        viewModelScope.launch {
            val views = try {
                notificationContent.preview(_state.value.now, widthDp)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } catch (_: LinkageError) {
                null
            }
            if (previewWidthDp == widthDp) _state.update { it.copy(notificationPreview = views) }
        }
    }

    /** Saves the profile, then flags [finished]. Idempotent. */
    fun finish() {
        if (saving || _finished.value) return
        saving = true
        viewModelScope.launch {
            profiles.save(_state.value.profile)
            saving = false
            _finished.value = true
        }
    }

    private fun edit(transform: (UserProfile) -> UserProfile) {
        touched = true
        _state.update { it.copy(profile = transform(it.profile)) }
    }
}

/** What this profile's two usual nights say about its chronotype. */
private fun UserProfile.inferredChronotype(): ChronotypeEstimate.Result = ChronotypeEstimate.estimate(free = daysOff, work = sleep)
