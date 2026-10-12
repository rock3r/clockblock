package dev.sebastiano.clockblocker.opus.shell

import androidx.compose.runtime.Immutable
import dev.sebastiano.clockblocker.opus.navigation.AppRoute
import dev.sebastiano.clockblocker.opus.navigation.OnboardingRoute
import dev.sebastiano.clockblocker.opus.navigation.PlanRoute
import dev.sebastiano.clockblocker.opus.navigation.TripEditorRoute

private const val MediumWidthDp = 600
private const val ExpandedWidthDp = 840
private const val MediumHeightDp = 480

/**
 * How the shell uses a window, from its size-class breakpoints (`WindowSizeClass.minWidthDp` / `minHeightDp`).
 *
 * - **[listDetail]**: Trips and a trip's plan side by side. Expanded width and up (tablets in landscape, unfolded
 *   foldables, desktop windows), but not on short windows: a landscape phone is expanded-wide yet only ~400 dp
 *   tall, and a plan squeezed next to the list there lost its own short-window two-pane layout.
 * - **[immersiveDetail]**: a trip's plan pushed from Trips takes the whole window, without the navigation (the owner's
 *   "opens without the primary nav"). Phones only, in either orientation. On medium windows (tablet portrait, an
 *   unfolded foldable held upright) the rail stays, as M3 asks of larger screens: hiding it there would only make
 *   the plan wider, which its single column doesn't use.
 */
@Immutable
internal data class ShellLayout(val listDetail: Boolean, val immersiveDetail: Boolean) {
    /** Whether the navigation suite (bar or rail) shows over [route]. */
    fun showsNavigation(route: AppRoute): Boolean = when (route) {
        OnboardingRoute, is TripEditorRoute -> false
        is PlanRoute -> !immersiveDetail
        else -> true
    }
}

internal fun shellLayoutFor(minWidthDp: Int, minHeightDp: Int): ShellLayout {
    val short = minHeightDp < MediumHeightDp
    val listDetail = minWidthDp >= ExpandedWidthDp && !short
    return ShellLayout(
        listDetail = listDetail,
        immersiveDetail = !listDetail && (minWidthDp < MediumWidthDp || short),
    )
}
