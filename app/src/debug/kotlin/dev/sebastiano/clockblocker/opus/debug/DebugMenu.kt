package dev.sebastiano.clockblocker.opus.debug

import android.content.Context
import android.content.Intent

/**
 * Debug builds: Settings shows a Debug section whose row opens [DebugActivity]. The release source set has its
 * own `DebugMenu` that returns null, so release builds carry neither the entry's target nor the debug tools.
 */
object DebugMenu {
    fun opener(context: Context): (() -> Unit)? = {
        context.startActivity(Intent(context, DebugActivity::class.java))
    }
}
