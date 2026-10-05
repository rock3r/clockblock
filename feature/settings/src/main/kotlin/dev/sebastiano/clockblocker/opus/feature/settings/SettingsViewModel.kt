package dev.sebastiano.clockblocker.opus.feature.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.clockblocker.opus.core.data.PlaceSearch
import dev.sebastiano.clockblocker.opus.core.data.ProfileRepository
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.backup.Backup
import dev.sebastiano.clockblocker.opus.core.data.backup.BackupCodec
import dev.sebastiano.clockblocker.opus.core.data.backup.BackupException
import dev.sebastiano.clockblocker.opus.core.data.backup.BackupManager
import dev.sebastiano.clockblocker.opus.core.data.backup.ImportMode
import dev.sebastiano.clockblocker.opus.core.data.backup.ImportResult
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/** The lead times offered for reminders, in minutes before an advice window starts. */
val ReminderLeadOptions: List<Int> = listOf(0, 5, 10, 15, 30)

/**
 * Everything the Settings screen renders.
 *
 * @property profile the saved profile, or `null` before onboarding (the profile section is hidden then).
 * @property melatoninAcknowledged the melatonin safety note has been accepted (true when melatonin is already on).
 * @property pendingImport a decoded backup waiting for the Replace/Merge confirmation.
 * @property isWorking an export or import is in progress.
 * @property homeQuery the text in the home time zone search.
 * @property homeResults places matching [homeQuery].
 */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val profile: UserProfile? = null,
    val permissions: NotificationPermissionState,
    val melatoninAcknowledged: Boolean = false,
    val pendingImport: PendingImport? = null,
    val isWorking: Boolean = false,
    val homeQuery: String = "",
    val homeResults: ImmutableList<Place> = persistentListOf(),
)

/** A backup that decoded fine and is waiting for the user to pick [ImportMode.Replace] or [ImportMode.Merge]. */
data class PendingImport(val tripCount: Int, val exportedAt: Instant, val hasProfile: Boolean)

/** Why an import was refused. */
enum class ImportFailure {
    /** Not JSON, or someone else's JSON. */
    NotABackup,

    /** Written by a newer version of the app. */
    NewerVersion,

    /** The file couldn't be read at all. */
    Unreadable,
}

/** One-off outcomes, shown as snackbars. */
sealed interface SettingsEvent {
    data object Exported : SettingsEvent
    data object ExportFailed : SettingsEvent
    data class Imported(val result: ImportResult) : SettingsEvent
    data class ImportFailed(val reason: ImportFailure) : SettingsEvent
    data object TestReminderSent : SettingsEvent

    /** Notifications are off, so the test couldn't show. */
    data object TestReminderBlocked : SettingsEvent
}

/**
 * Settings: profile edits (saved immediately), appearance, reminders and permission status, backup export/import.
 * File access stays in the UI (Storage Access Framework); this ViewModel only sees the text.
 */
@Inject
@ViewModelKey(SettingsViewModel::class)
@ContributesIntoMap(AppScope::class)
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val profiles: ProfileRepository,
    private val notificationPermissions: NotificationPermissions,
    private val places: PlaceSearch,
    private val backupManager: BackupManager,
    private val codec: BackupCodec,
    private val clock: Clock,
) : ViewModel() {
    private val permissions = MutableStateFlow(notificationPermissions.state())

    /** `null` until the user touches the note; then their answer. Before that, "melatonin already on" counts. */
    private val acknowledged = MutableStateFlow<Boolean?>(null)
    private val transient = MutableStateFlow(Transient())
    private var pendingBackup: Backup? = null
    private var searchJob: Job? = null

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)

    /** One-off results for snackbars. */
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        profiles.profile,
        permissions,
        acknowledged,
        transient,
    ) { settings, profile, permissions, acknowledged, transient ->
        SettingsUiState(
            settings = settings,
            profile = profile,
            permissions = permissions,
            melatoninAcknowledged = acknowledged ?: (profile?.useMelatonin == true),
            pendingImport = transient.pendingImport,
            isWorking = transient.isWorking,
            homeQuery = transient.homeQuery,
            homeResults = transient.homeResults,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), SettingsUiState(permissions = permissions.value))

    /** The runtime permission to request for notifications (`null` below API 33). */
    val runtimePermission: String? get() = notificationPermissions.runtimePermission

    fun notificationSettingsIntent(): Intent = notificationPermissions.notificationSettingsIntent()
    fun exactAlarmSettingsIntent(): Intent = notificationPermissions.exactAlarmSettingsIntent()
    fun promotedSettingsIntent(): Intent = notificationPermissions.promotedSettingsIntent()
    fun batteryOptimizationSettingsIntent(): Intent = notificationPermissions.batteryOptimizationSettingsIntent()

    /** Re-reads permission state; call on resume, since it changes in system settings. */
    fun refreshPermissions() {
        permissions.value = notificationPermissions.state()
    }

    fun setThemeMode(mode: ThemeMode) = updateSettings { it.copy(themeMode = mode) }
    fun setDynamicColor(enabled: Boolean) = updateSettings { it.copy(dynamicColor = enabled) }
    fun setReduceMotion(enabled: Boolean) = updateSettings { it.copy(reduceMotion = enabled) }
    fun setNightSafeAuto(enabled: Boolean) = updateSettings { it.copy(nightSafeAuto = enabled) }
    fun setRemindersEnabled(enabled: Boolean) = updateSettings { it.copy(remindersEnabled = enabled) }
    fun setReminderLead(minutes: Int) = updateSettings { it.copy(reminderLeadMinutes = minutes.coerceAtLeast(0)) }

    /** Turns the concert theme on or off; ignored until it has been unlocked from About. */
    fun setOpusModeEnabled(enabled: Boolean) = updateSettings { if (it.opusModeUnlocked) it.copy(opusModeEnabled = enabled) else it }

    /** Applies [transform] to the saved profile and saves it straight away. No-op before onboarding. */
    fun updateProfile(transform: (UserProfile) -> UserProfile) {
        viewModelScope.launch {
            val current = profiles.profile.first() ?: return@launch
            val updated = transform(current)
            if (updated != current) profiles.save(updated)
        }
    }

    /** Searches places for a new home time zone. */
    fun searchHome(query: String) {
        transient.update { it.copy(homeQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            transient.update { it.copy(homeResults = persistentListOf()) }
            return
        }
        searchJob = viewModelScope.launch {
            val results = places.search(query.trim(), HomeSearchLimit).toImmutableList()
            transient.update { it.copy(homeResults = results) }
        }
    }

    /** Saves [place]'s IANA zone as home and clears the search. */
    fun setHomePlace(place: Place) {
        updateProfile { it.copy(homeZoneId = place.zoneId) }
        clearHomeSearch()
    }

    fun clearHomeSearch() {
        searchJob?.cancel()
        transient.update { it.copy(homeQuery = "", homeResults = persistentListOf()) }
    }

    fun acknowledgeMelatoninNote(acknowledged: Boolean) {
        this.acknowledged.value = acknowledged
        if (!acknowledged) updateProfile { it.copy(useMelatonin = false) }
    }

    /** Switching melatonin on requires the safety note to be acknowledged first; switching off always works. */
    fun setMelatonin(enabled: Boolean) {
        viewModelScope.launch {
            val profile = profiles.profile.first() ?: return@launch
            val noteAccepted = acknowledged.value ?: profile.useMelatonin
            if (enabled && !noteAccepted) return@launch
            if (profile.useMelatonin != enabled) profiles.save(profile.copy(useMelatonin = enabled))
        }
    }

    /** Suggested file name for the export document. */
    fun exportFileName(): String = BackupCodec.fileName(clock.instant())

    /**
     * Builds the backup JSON and hands it to [write] (which saves it to the document the user picked). Reports
     * [SettingsEvent.Exported] or [SettingsEvent.ExportFailed].
     */
    fun export(write: suspend (String) -> Unit) {
        viewModelScope.launch {
            transient.update { it.copy(isWorking = true) }
            val event = try {
                write(backupManager.export())
                SettingsEvent.Exported
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SettingsEvent.ExportFailed
            } finally {
                transient.update { it.copy(isWorking = false) }
            }
            _events.send(event)
        }
    }

    /**
     * Reads a backup through [read] and decodes it without touching any data. On success the state gets a
     * [PendingImport] to confirm; otherwise [SettingsEvent.ImportFailed] says why.
     */
    fun prepareImport(read: suspend () -> String) {
        viewModelScope.launch {
            transient.update { it.copy(isWorking = true) }
            val failure: ImportFailure? = try {
                val decoded = codec.decode(read())
                pendingBackup = decoded
                transient.update {
                    it.copy(pendingImport = PendingImport(decoded.trips.size, decoded.exportedAt, decoded.profile != null))
                }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackupException.UnsupportedVersion) {
                ImportFailure.NewerVersion
            } catch (_: BackupException) {
                ImportFailure.NotABackup
            } catch (_: Exception) {
                ImportFailure.Unreadable
            } finally {
                transient.update { it.copy(isWorking = false) }
            }
            if (failure != null) _events.send(SettingsEvent.ImportFailed(failure))
        }
    }

    /** Restores the pending backup with [mode] and reports [SettingsEvent.Imported]. */
    fun confirmImport(mode: ImportMode) {
        val backup = pendingBackup ?: return
        viewModelScope.launch {
            transient.update { it.copy(isWorking = true) }
            val event = try {
                SettingsEvent.Imported(backupManager.restore(backup, mode))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SettingsEvent.ImportFailed(ImportFailure.Unreadable)
            } finally {
                pendingBackup = null
                transient.update { it.copy(pendingImport = null, isWorking = false) }
            }
            _events.send(event)
        }
    }

    fun cancelImport() {
        pendingBackup = null
        transient.update { it.copy(pendingImport = null) }
    }

    /** Posts a sample reminder now, so the user can see what they'll get (and whether notifications work). */
    fun sendTestReminder() {
        val sent = notificationPermissions.sendTestReminder()
        refreshPermissions()
        viewModelScope.launch { _events.send(if (sent) SettingsEvent.TestReminderSent else SettingsEvent.TestReminderBlocked) }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    private data class Transient(
        val pendingImport: PendingImport? = null,
        val isWorking: Boolean = false,
        val homeQuery: String = "",
        val homeResults: ImmutableList<Place> = persistentListOf(),
    )

    private companion object {
        const val StopTimeoutMillis = 5_000L
        const val HomeSearchLimit = 8
    }
}
