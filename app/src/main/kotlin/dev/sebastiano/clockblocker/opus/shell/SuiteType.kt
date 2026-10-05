package dev.sebastiano.clockblocker.opus.shell

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType

private const val MediumWidthDp = 600
private const val MediumHeightDp = 480

/**
 * The M3 default puts a bottom bar on any window shorter than medium height, so a landscape phone spent 80 dp
 * of its ~400 dp height on navigation. Wide-but-short windows get a collapsed rail instead (width is the
 * plentiful axis there). Tabletop keeps the default: the bar belongs on the lower half.
 */
internal fun suiteTypeFor(
    default: NavigationSuiteType,
    minWidthDp: Int,
    minHeightDp: Int,
    tabletop: Boolean,
): NavigationSuiteType =
    if (!tabletop && minWidthDp >= MediumWidthDp && minHeightDp < MediumHeightDp) {
        NavigationSuiteType.WideNavigationRailCollapsed
    } else {
        default
    }
