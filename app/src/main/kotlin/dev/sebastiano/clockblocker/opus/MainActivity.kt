package dev.sebastiano.clockblocker.opus

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.clockblocker.opus.core.designsystem.component.StatusBarIconsOwner
import dev.sebastiano.clockblocker.opus.navigation.DeepLinkParser
import dev.sebastiano.clockblocker.opus.navigation.DeepLinkTarget
import dev.sebastiano.clockblocker.opus.shell.DeviceZoneClock
import dev.sebastiano.clockblocker.opus.shell.ClockblockAppRoot
import dev.sebastiano.clockblocker.opus.shell.ShellUiState
import dev.sebastiano.clockblocker.opus.shell.ShellViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.ActivityKey
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Clock

/**
 * The single activity. Keeps the splash screen up until the shell state is known, draws edge to edge (system bar
 * icons follow the in-app theme, not just the system one) and forwards `clockblock://` deep links to the
 * navigator — on launch and, being `singleTop`, via [onNewIntent] from notifications and widgets.
 */
@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey
@Inject
class MainActivity(
    private val viewModelFactory: MetroViewModelFactory,
    clock: Clock,
) : ComponentActivity() {

    /** The injected clock in the device's current zone, for the shell's body-night (Calm motion) check. */
    private val localClock: Clock = DeviceZoneClock(clock)

    /** Deep links waiting for the navigator; buffered so links arriving before the UI is ready aren't lost. */
    private val deepLinks = Channel<DeepLinkTarget>(Channel.BUFFERED)
    private val deepLinkFlow = deepLinks.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        applySystemBars(darkTheme = null)
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false

        val shellViewModel = ViewModelProvider(this, viewModelFactory)[ShellViewModel::class.java]
        splashScreen.setKeepOnScreenCondition { shellViewModel.uiState.value is ShellUiState.Loading }

        // On recreation the restored back stacks already reflect the launch intent.
        if (savedInstanceState == null) handleDeepLink(intent)

        setContent {
            CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
                val uiState by shellViewModel.uiState.collectAsStateWithLifecycle()
                ClockblockAppRoot(
                    uiState = uiState,
                    deepLinks = deepLinkFlow,
                    clock = localClock,
                    onDarkThemeChanged = ::applySystemBars,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        DeepLinkParser.parse(intent.dataString)?.let { deepLinks.trySend(it) }
    }

    private var appliedDarkTheme: Boolean? = null

    /** Transparent bars whose icon colour follows [darkTheme] (the system setting while it's still unknown). */
    private fun applySystemBars(darkTheme: Boolean?) {
        if (darkTheme != null && darkTheme == appliedDarkTheme) return
        appliedDarkTheme = darkTheme
        val style = if (darkTheme == null) {
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        // A sky header may hold the status bar icons right now: it keeps them, and this theme's value is the one
        // to come back to when it leaves (see SkyStatusBarIcons).
        if (darkTheme != null) StatusBarIconsOwner.of(window).baseChanged()
    }
}
