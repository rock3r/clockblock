package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockThemeVariant
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.DuskPalette
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyPalette
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyPhase

/**
 * Colours of the route globe, in the app and on the home screen. The globe is a night-sky object in light and dark
 * themes alike: Clockblock's night sky ramp for the ocean and its day-side glow, the dawn sky's warm horizon for city
 * lights, Marigold for the route. [NightSafe] swaps to warm, dim amber on near-black, so a globe on a night-safe
 * screen never emits blue or bright white light.
 */
@Immutable
data class GlobeColors(
    val ocean: Color,
    val oceanDay: Color,
    val rim: Color,
    val land: Color,
    val sea: Color,
    val cityLight: Color,
    val route: Color,
    val routeDim: Color,
    val stop: Color,
    val label: Color,
    /** Behind text drawn on the globe or over it (airport codes, captions, a picked flight). */
    val scrim: Color,
) {
    companion object {
        val Standard: GlobeColors = GlobeColors(
            ocean = SkyPalette.Default[SkyPhase.Night].top,
            oceanDay = SkyPalette.Default[SkyPhase.PreDawn].top,
            rim = DuskPalette.GlobeRim,
            land = DuskPalette.GlobeLand,
            sea = DuskPalette.GlobeSea,
            cityLight = SkyPalette.Default[SkyPhase.Dawn].bottom,
            route = DuskPalette.MarigoldSeed,
            routeDim = DuskPalette.GlobeRouteDim,
            stop = Color.White,
            label = Color.White,
            scrim = GlobeScrim,
        )

        val NightSafe: GlobeColors = GlobeColors(
            ocean = DuskPalette.NightGlobeOcean,
            oceanDay = DuskPalette.NightGlobeOceanDay,
            rim = DuskPalette.NightGlobeRim,
            land = DuskPalette.NightInk,
            sea = DuskPalette.NightGlobeSea,
            cityLight = DuskPalette.NightGlobeCityLight,
            route = DuskPalette.NightGlobeRoute,
            routeDim = DuskPalette.NightGlobeRouteDim,
            stop = DuskPalette.NightGlobeStop,
            label = DuskPalette.NightGlobeStop,
            scrim = DuskPalette.NightGlobeScrim,
        )

        /** The colours for the current theme (night-safe or not). */
        val current: GlobeColors
            @Composable get() = if (ClockblockTheme.variant == ClockblockThemeVariant.NightSafe) NightSafe else Standard
    }
}

/** The scrim behind text on the globe (70 % black): airport codes, the world caption, a picked flight. */
val GlobeScrim: Color = Color(0xB3000000)
