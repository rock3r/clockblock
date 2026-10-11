package dev.sebastiano.clockblocker.opus.debug

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.widget.debug.WidgetGalleryActivity

/**
 * Debug builds only (declared in the debug manifest): Settings → Debug → Debug tools. Runs the Remote Compose
 * notification probe (#49) and opens the widget gallery's pages, so they can be tried on a phone without adb. The
 * probe's state lives in [ProbeViewModel], so its results stay on screen when a theme switch recreates the activity.
 */
class DebugActivity : ComponentActivity() {
    private val probe: ProbeViewModel by viewModels { ProbeViewModel.factory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClockblockTheme {
                val state by probe.state.collectAsStateWithLifecycle()
                var allowed by remember { mutableStateOf(notificationsAllowed()) }
                LifecycleResumeEffect(Unit) {
                    allowed = notificationsAllowed()
                    onPauseOrDispose { }
                }
                DebugScreen(
                    probe = state,
                    notificationsAllowed = allowed,
                    onRunProbe = probe::run,
                    onClearProbe = probe::clear,
                    // Always the app's notification settings: they work whether notifications were never granted,
                    // denied for good (no more prompts) or turned off later, and the resume effect picks up the change.
                    onAllowNotifications = {
                        startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                        )
                    },
                    onOpenGallery = { page ->
                        startActivity(Intent(this, WidgetGalleryActivity::class.java).putExtra("page", page.extra))
                    },
                    onBack = ::finish,
                )
            }
        }
    }

    private fun notificationsAllowed() = NotificationManagerCompat.from(this).areNotificationsEnabled()
}
