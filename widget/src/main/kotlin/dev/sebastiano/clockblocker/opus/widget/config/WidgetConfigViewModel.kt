package dev.sebastiano.clockblocker.opus.widget.config

import android.util.Log
import android.widget.RemoteViews
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.WidgetConfigRepository
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.BodyRingMode
import dev.sebastiano.clockblocker.opus.core.model.WidgetConfig
import dev.sebastiano.clockblocker.opus.widget.WidgetKind
import dev.sebastiano.clockblocker.opus.widget.WidgetUpdater
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the configuration screen shows for one widget.
 *
 * @property bodyRing the widget's saved body ring.
 * @property dial the dial the body ring cards draw: the widget's own now, or the sample trip's.
 * @property sample whether there is no trip yet, so the preview shows a sample trip.
 * @property previewDescription what the preview shows, for TalkBack.
 * @property preview the widget as the home screen draws it; null when it couldn't be rendered.
 * @property widthDp the widget's size on the home screen, which the preview copies.
 */
internal data class WidgetConfigUiState(
    val kind: WidgetKind,
    val bodyRing: BodyRingMode,
    val dial: DialState?,
    val sample: Boolean,
    val previewDescription: String,
    val preview: RemoteViews?,
    val widthDp: Float,
    val heightDp: Float,
)

/**
 * Drives [WidgetConfigScreen] for one placed widget ([appWidgetId], a [kind] widget). A choice is saved and drawn on
 * the widget at once ([WidgetUpdater.configure]); the screen's state follows the saved options, so it never shows a
 * choice that wasn't saved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class WidgetConfigViewModel(
    private val appWidgetId: Int,
    val kind: WidgetKind,
    private val updater: WidgetUpdater,
    configs: WidgetConfigRepository,
    settingsRepository: SettingsRepository,
    background: CoroutineDispatcher = Dispatchers.Default,
    private val saveScope: CoroutineScope = updater.scope,
    landscape: Boolean = false,
) : ViewModel() {

    /** The app's appearance settings, which the screen's theme follows; null until read. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whether the screen is in landscape: the preview copies the widget's size in that orientation (#125). */
    private val orientation = MutableStateFlow(landscape)

    /** The activity's orientation, reported on every (re)creation: this ViewModel outlives a rotation. */
    fun setLandscape(value: Boolean) {
        orientation.value = value
    }

    val state: StateFlow<WidgetConfigUiState?> = combine(
        configs.configs.map { it[appWidgetId] ?: WidgetConfig() }.distinctUntilChanged(),
        // The preview follows the app's theme too.
        settingsRepository.settings,
        orientation,
    ) { config: WidgetConfig, _: Any?, isLandscape: Boolean -> config to isLandscape }
        .mapLatest { (config, isLandscape) -> build(config, isLandscape) }
        .flowOn(background)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Serialises the saves so quick taps land in the order they were made. */
    private val saves = Mutex()

    /**
     * Saves [mode] as this widget's body ring and redraws the widget with it. The save runs in [saveScope], which
     * outlives the screen: Done or Back right after a tap must not cancel it.
     */
    fun setBodyRing(mode: BodyRingMode) {
        // Undispatched, so the lock is queued for in tap order before anything suspends.
        saveScope.launch(start = CoroutineStart.UNDISPATCHED) {
            saves.withLock {
                try {
                    updater.configure(appWidgetId) { it.copy(bodyRing = mode) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The app-wide scope has no handler: a failed write must not take the process down.
                    Log.e(TAG, "Couldn't save widget $appWidgetId's body ring", e)
                }
            }
        }
    }

    private suspend fun build(config: WidgetConfig, landscape: Boolean): WidgetConfigUiState {
        val preview = updater.previewModel(appWidgetId, config)
        val size = updater.sizeDp(appWidgetId, landscape)
        return WidgetConfigUiState(
            kind = kind,
            bodyRing = config.bodyRing,
            dial = (preview.model.state as? WidgetState.Active)?.dial,
            sample = preview.sample,
            previewDescription = preview.model.texts.contentDescription,
            preview = runCatching { updater.renderPreview(kind, preview.model) }.getOrNull(),
            widthDp = size.width,
            heightDp = size.height,
        )
    }
}

private const val TAG = "ClockblockWidgetConfig"
