package dev.sebastiano.clockblocker.opus.feature.plan

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * While the sky header sits under the status bar's start (single pane, nothing to its left), the status bar
 * icons follow the sky's ink instead of the theme: a night sky in light theme left dark icons on navy.
 * The previous appearance comes back when the header leaves or stops owning the bar.
 */
@Composable
internal fun SkyStatusBarIcons(ink: Color, ownsStatusBar: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findActivity()?.window ?: return
    val darkIcons = ink.luminance() < 0.5f
    DisposableEffect(window, darkIcons, ownsStatusBar) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        if (ownsStatusBar) controller.isAppearanceLightStatusBars = darkIcons
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
