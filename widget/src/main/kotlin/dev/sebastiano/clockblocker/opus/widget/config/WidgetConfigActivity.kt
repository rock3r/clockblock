package dev.sebastiano.clockblocker.opus.widget.config

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.WidgetConfigRepository
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import dev.sebastiano.clockblocker.opus.widget.WidgetUpdater
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.ActivityKey

/**
 * The widget configuration screen (#52), started by the launcher when a widget is placed (it can be skipped:
 * `configuration_optional`) or from the widget's Reconfigure action. Choices are saved and drawn on the widget as they
 * are made, so the result is always OK and leaving by back or Done both keep them.
 */
@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey
@Inject
class WidgetConfigActivity(
    private val updater: WidgetUpdater,
    private val configs: WidgetConfigRepository,
    private val settingsRepository: SettingsRepository,
) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false

        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Exported for the launcher: show nothing for an id that isn't one of our widgets.
        val kind = appWidgetId.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }?.let(updater::kindOf)
        if (kind == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        val factory = viewModelFactory {
            initializer {
                WidgetConfigViewModel(appWidgetId, kind, updater, configs, settingsRepository, landscape = isLandscape())
            }
        }
        val viewModel = ViewModelProvider(this, factory)[WidgetConfigViewModel::class.java]
        // The ViewModel survives a rotation; the recreated activity tells it the new orientation.
        viewModel.setLandscape(isLandscape())

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val state by viewModel.state.collectAsStateWithLifecycle()
            // Blank for the moment it takes to read the settings, rather than a flash of the wrong theme.
            val appSettings = settings ?: return@setContent
            val darkTheme = when (appSettings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            LaunchedEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ClockblockTheme(
                darkTheme = darkTheme,
                dynamicColor = appSettings.dynamicColor,
                opusMode = appSettings.opusModeUnlocked && appSettings.opusModeEnabled,
                reduceMotion = appSettings.reduceMotion,
            ) {
                val current = state ?: return@ClockblockTheme
                WidgetConfigScreen(
                    state = current,
                    onBodyRingChange = viewModel::setBodyRing,
                    onDone = ::finish,
                ) { modifier ->
                    // The widget exactly as the launcher hosts it: the same RemoteViews in a host view at its size.
                    AndroidView(
                        factory = ::PreviewHostView,
                        modifier = modifier,
                        update = { view ->
                            view.updateAppWidgetSize(Bundle(), listOf(SizeF(current.widthDp, current.heightDp)))
                            current.preview?.let(view::updateAppWidget)
                        },
                    )
                }
            }
        }
    }
}

private fun Activity.isLandscape(): Boolean = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * The preview's host view. It plays the widget's real RemoteViews, whose buttons fire real actions (Done would log
 * the advice), so it takes no touches or focus. TalkBack skips its content and reads the preview's description.
 */
private class PreviewHostView(context: Context) : AppWidgetHostView(context) {
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        isFocusable = false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = true

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = true
}
