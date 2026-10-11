package dev.sebastiano.clockblocker.opus.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sebastiano.clockblocker.opus.widget.debug.RemoteComposeNotificationProbe
import dev.sebastiano.clockblocker.opus.widget.debug.RemoteComposeNotificationProbe.Result
import dev.sebastiano.clockblocker.opus.widget.debug.RemoteComposeNotificationProbe.Variant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the Debug screen needs from the notification probe (the real one posts; tests fake it). */
interface NotificationProbe {
    suspend fun run(): List<Result>
    fun activeVariants(): List<Variant>
    fun cancelAll()
}

/**
 * Holds the notification probe's state for [DebugActivity], so it survives the activity being recreated: flipping
 * dark theme to see the probe's notifications follow it recreates the activity, which lost its results before. A run
 * keeps going through a recreation too. When the process itself was restarted, the probe's notifications still in the
 * shade are picked up instead ([ProbeState.Showing]).
 */
class ProbeViewModel(private val probe: NotificationProbe) : ViewModel() {
    private val _state = MutableStateFlow(fromShade())
    val state: StateFlow<ProbeState> = _state.asStateFlow()

    fun run() {
        if (_state.value == ProbeState.Running) return
        _state.value = ProbeState.Running
        viewModelScope.launch { _state.value = ProbeState.Done(probe.run()) }
    }

    fun clear() {
        // Clearing mid-run would cancel the probe's own posts and read as "dropped" (the screen disables it too).
        if (_state.value == ProbeState.Running) return
        probe.cancelAll()
        _state.value = ProbeState.Idle
    }

    private fun fromShade(): ProbeState = probe.activeVariants().takeIf { it.isNotEmpty() }?.let(ProbeState::Showing) ?: ProbeState.Idle

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val app = context.applicationContext
            return viewModelFactory { initializer { ProbeViewModel(RealProbe(RemoteComposeNotificationProbe(app))) } }
        }
    }

    private class RealProbe(private val probe: RemoteComposeNotificationProbe) : NotificationProbe {
        override suspend fun run(): List<Result> = probe.runOnDemoPlan()
        override fun activeVariants(): List<Variant> = probe.activeVariants()
        override fun cancelAll() = probe.cancelAll()
    }
}
