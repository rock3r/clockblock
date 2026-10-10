package dev.sebastiano.clockblocker.opus.core.designsystem.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import java.util.WeakHashMap

/**
 * While a sky header sits under the status bar's start (single pane, nothing to its left), the status bar icons
 * follow the sky's ink instead of the theme: a night sky in light theme would otherwise leave dark icons on navy.
 *
 * Headers claim the icons through one [StatusBarIconsOwner] per window, so overlapping headers stay in order. During
 * a screen transition both screens are composed: the newer claim wins, and when the outgoing header leaves, the
 * icons go to the claim still standing, or back to the window's own appearance when none is left.
 */
@Composable
fun SkyStatusBarIcons(ink: Color, ownsStatusBar: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findActivity()?.window ?: return
    val darkIcons = ink.luminance() < 0.5f
    val owner = remember(window) { StatusBarIconsOwner.of(window) }
    val claim = remember(owner) { owner.newClaim() }
    DisposableEffect(claim, ownsStatusBar) {
        if (ownsStatusBar) claim.acquire(darkIcons)
        onDispose { claim.release() }
    }
    SideEffect { claim.update(darkIcons) }
}

/**
 * Hands the window's light-status-bar appearance to the most recent active claim. The window's own value (set by
 * the theme through edge-to-edge) is saved when the first claim arrives and comes back when the last one leaves. If
 * something else changes the appearance meanwhile (a theme switch), that value becomes the one to come back to.
 */
class StatusBarIconsOwner(
    private val read: () -> Boolean,
    private val write: (Boolean) -> Unit,
) {
    private val active = mutableListOf<Claim>()
    private var base: Boolean? = null
    private var applied: Boolean? = null

    fun newClaim(): Claim = Claim()

    private fun apply(lightStatusBars: Boolean) {
        if (applied != null && read() != applied) base = read()
        write(lightStatusBars)
        applied = lightStatusBars
    }

    private fun restore() {
        val current = read()
        val target = if (applied != null && current != applied) current else base
        if (target != null) write(target)
        base = null
        applied = null
    }

    inner class Claim internal constructor() {
        private var darkIcons = false

        /** Takes the icons, ahead of any older claim. */
        fun acquire(darkIcons: Boolean) {
            this.darkIcons = darkIcons
            active.remove(this)
            if (active.isEmpty()) base = read()
            active += this
            apply(darkIcons)
        }

        /** New ink for this claim. Shows only while it's the newest active claim. */
        fun update(darkIcons: Boolean) {
            this.darkIcons = darkIcons
            if (active.lastOrNull() === this && applied != darkIcons) apply(darkIcons)
        }

        /** Gives the icons back: to the newest claim left, or to the window's own appearance. */
        fun release() {
            val wasOnTop = active.lastOrNull() === this
            if (!active.remove(this)) return
            val next = active.lastOrNull()
            when {
                next == null -> restore()
                wasOnTop -> apply(next.darkIcons)
            }
        }
    }

    companion object {
        private val owners = WeakHashMap<Window, StatusBarIconsOwner>()

        /** The owner for [window], shared by every header in it. */
        fun of(window: Window): StatusBarIconsOwner = owners.getOrPut(window) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            StatusBarIconsOwner(
                read = { controller.isAppearanceLightStatusBars },
                write = { controller.isAppearanceLightStatusBars = it },
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
