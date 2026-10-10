package dev.sebastiano.clockblocker.opus.debug

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.widget.debug.RemoteComposeNotificationProbe
import dev.sebastiano.clockblocker.opus.widget.debug.WidgetGalleryActivity
import kotlinx.coroutines.launch

/**
 * Debug builds only (declared in the debug manifest): Settings → Debug → Debug tools. Runs the Remote Compose
 * notification probe (#49) and opens the widget gallery's pages, so they can be tried on a phone without adb.
 */
class DebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val probe = RemoteComposeNotificationProbe(this)
        setContent {
            ClockblockTheme {
                val scope = rememberCoroutineScope()
                var state by remember { mutableStateOf<ProbeState>(ProbeState.Idle) }
                var allowed by remember { mutableStateOf(notificationsAllowed()) }
                LifecycleResumeEffect(Unit) {
                    allowed = notificationsAllowed()
                    onPauseOrDispose { }
                }
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    allowed = notificationsAllowed()
                }
                DebugScreen(
                    probe = state,
                    notificationsAllowed = allowed,
                    onRunProbe = {
                        state = ProbeState.Running
                        scope.launch { state = ProbeState.Done(probe.runOnDemoPlan()) }
                    },
                    onClearProbe = {
                        probe.cancelAll()
                        if (state is ProbeState.Done) state = ProbeState.Idle
                    },
                    onAllowNotifications = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
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
