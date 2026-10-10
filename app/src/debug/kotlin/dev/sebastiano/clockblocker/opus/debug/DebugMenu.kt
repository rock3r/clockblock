package dev.sebastiano.clockblocker.opus.debug

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.sebastiano.clockblocker.opus.R
import dev.sebastiano.clockblocker.opus.feature.settings.SettingsLinkSection

/**
 * Debug builds: Settings ends with a Debug section whose row opens [DebugActivity]. The release source set has its
 * own `DebugMenu` with no section, so release builds carry neither the section nor the debug tools.
 */
object DebugMenu {
    const val SettingsRowTag = "settings_debug"

    val settingsSection: (@Composable () -> Unit)? = {
        val context = LocalContext.current
        SettingsLinkSection(
            header = stringResource(R.string.debug_settings_section),
            title = stringResource(R.string.debug_settings_row),
            supporting = stringResource(R.string.debug_settings_row_description),
            tag = SettingsRowTag,
            onClick = { context.startActivity(Intent(context, DebugActivity::class.java)) },
        )
    }
}
