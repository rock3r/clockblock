package dev.sebastiano.clockblocker.opus.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.clockblocker.opus.core.data.EasterEggGate
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Feedback for taps on the version row. */
sealed interface AboutEvent {
    /** "You are [remaining] taps away from a concert." */
    data class TapsAway(val remaining: Int) : AboutEvent

    /** Opus mode just unlocked (and switched on); the title card is showing. */
    data object Unlocked : AboutEvent

    /** Already unlocked: point at the switch in Settings. */
    data object AlreadyUnlocked : AboutEvent
}

/**
 * About screen logic: the developer-options-style easter egg. Tapping the version [TapsToUnlock] times unlocks
 * Opus mode ("Opus No. 1 in Jet-Lag Minor"), switches it on and raises the title card. The last
 * [CountdownFrom] taps before that count down. Like every egg, taps do nothing while [EasterEggGate] says no
 * (Reduce motion on, or the current plan says sleep).
 */
@Inject
@ViewModelKey(AboutViewModel::class)
@ContributesIntoMap(AppScope::class)
class AboutViewModel(private val settings: SettingsRepository, private val eggs: EasterEggGate) : ViewModel() {
    private var taps = 0
    private val _events = Channel<AboutEvent>(Channel.BUFFERED)
    private val _showTitleCard = MutableStateFlow(false)

    /** One-off feedback for snackbars. */
    val events: Flow<AboutEvent> = _events.receiveAsFlow()

    /** The concert title card is up. */
    val showTitleCard: StateFlow<Boolean> = _showTitleCard.asStateFlow()

    fun onVersionTapped() {
        viewModelScope.launch {
            if (settings.settings.first().opusModeUnlocked) {
                _events.send(AboutEvent.AlreadyUnlocked)
                return@launch
            }
            if (!eggs.allowed.first()) return@launch
            taps++
            val remaining = TapsToUnlock - taps
            when {
                remaining <= 0 -> {
                    taps = 0
                    settings.update { it.copy(opusModeUnlocked = true, opusModeEnabled = true) }
                    _showTitleCard.value = true
                    _events.send(AboutEvent.Unlocked)
                }
                remaining <= CountdownFrom -> _events.send(AboutEvent.TapsAway(remaining))
            }
        }
    }

    fun dismissTitleCard() {
        _showTitleCard.value = false
    }

    companion object {
        const val TapsToUnlock: Int = 7
        const val CountdownFrom: Int = 3
    }
}
