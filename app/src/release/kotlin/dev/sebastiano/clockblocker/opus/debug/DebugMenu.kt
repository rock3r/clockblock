package dev.sebastiano.clockblocker.opus.debug

import android.content.Context

/** Release builds have no debug tools: Settings gets no Debug section. See the debug source set's `DebugMenu`. */
object DebugMenu {
    @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
    fun opener(context: Context): (() -> Unit)? = null
}
