package dev.sebastiano.clockblocker.opus.debug

import androidx.compose.runtime.Composable

/** Release builds have no debug tools: Settings gets no Debug section. See the debug source set's `DebugMenu`. */
object DebugMenu {
    val settingsSection: (@Composable () -> Unit)? = null
}
