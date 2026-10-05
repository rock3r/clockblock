package dev.sebastiano.clockblocker.opus.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import dev.sebastiano.clockblocker.opus.R
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import dev.sebastiano.clockblocker.opus.navigation.AboutRoute
import dev.sebastiano.clockblocker.opus.navigation.AppNavigator
import dev.sebastiano.clockblocker.opus.navigation.AppRoute
import dev.sebastiano.clockblocker.opus.navigation.LicensesRoute
import dev.sebastiano.clockblocker.opus.navigation.NowRoute
import dev.sebastiano.clockblocker.opus.navigation.PlanRoute
import dev.sebastiano.clockblocker.opus.navigation.SettingsRoute
import dev.sebastiano.clockblocker.opus.navigation.TripsRoute
import dev.sebastiano.clockblocker.opus.navigation.DeepLinkTarget
import dev.sebastiano.clockblocker.opus.navigation.OnboardingRoute
import dev.sebastiano.clockblocker.opus.navigation.TopLevelDestination
import dev.sebastiano.clockblocker.opus.navigation.TripEditorRoute
import dev.sebastiano.clockblocker.opus.navigation.rememberAppNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Clock

/**
 * The whole app UI once [uiState] is ready (nothing is drawn while loading; the splash screen covers it):
 * applies the theme from settings, gates on onboarding, routes [deepLinks] and hosts [OpusShell].
 *
 * Motion turns Calm app-wide during body night ([isBodyNight], re-checked every minute on [clock]), whatever
 * the colours are doing: Night-safe colours remain a separate, plan-screen concern.
 *
 * @param clock wall clock whose zone is the device's local zone (used when there is no current plan).
 * @param onDarkThemeChanged called with the effective dark-theme flag so the activity can restyle system bars.
 */
@Composable
fun OpusAppRoot(
    uiState: ShellUiState,
    modifier: Modifier = Modifier,
    deepLinks: Flow<DeepLinkTarget> = emptyFlow(),
    destinations: AppDestinations = FeatureDestinations,
    clock: Clock = Clock.systemDefaultZone(),
    onDarkThemeChanged: (Boolean) -> Unit = {},
) {
    val ready = uiState as? ShellUiState.Ready ?: return
    val darkTheme = ready.settings.isDarkTheme()
    val currentOnDarkThemeChanged by rememberUpdatedState(onDarkThemeChanged)
    SideEffect { currentOnDarkThemeChanged(darkTheme) }
    val bodyNight = rememberBodyNight(plan = ready.currentPlan, sleep = ready.sleep, clock = clock)

    OpusTheme(
        darkTheme = darkTheme,
        dynamicColor = ready.settings.dynamicColor,
        opusMode = ready.settings.opusModeUnlocked && ready.settings.opusModeEnabled,
        reduceMotion = ready.settings.reduceMotion,
        calmMotion = bodyNight,
    ) {
        val navigator = rememberAppNavigator(AppNavigator.startDestination(ready.hasProfile, ready.hasTrips))
        LaunchedEffect(navigator, ready.hasProfile) {
            if (!ready.hasProfile) navigator.requireOnboarding()
        }
        LaunchedEffect(navigator, deepLinks) {
            deepLinks.collect { navigator.open(it) }
        }
        OpusShell(navigator = navigator, destinations = destinations, hasTrips = ready.hasTrips, modifier = modifier)
    }
}

/**
 * Adaptive chrome around [AppNavDisplay]: a short navigation bar on compact windows and a navigation rail on
 * wider ones (M3 Expressive `NavigationSuiteScaffold`), hidden during onboarding and in the trip editor.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun OpusShell(
    navigator: AppNavigator,
    destinations: AppDestinations,
    hasTrips: Boolean,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = calculatePaneScaffoldDirective(adaptiveInfo)
    val isTwoPane = directive.maxHorizontalPartitions > 1
    val suiteType = suiteTypeFor(
        default = NavigationSuiteScaffoldDefaults.navigationSuiteType(adaptiveInfo),
        minWidthDp = adaptiveInfo.windowSizeClass.minWidthDp,
        minHeightDp = adaptiveInfo.windowSizeClass.minHeightDp,
        tabletop = adaptiveInfo.windowPosture.isTabletop,
    )

    val route = navigator.currentRoute
    val showSuite = route !is OnboardingRoute && route !is TripEditorRoute
    val suiteState = rememberNavigationSuiteScaffoldState(
        if (showSuite) NavigationSuiteScaffoldValue.Visible else NavigationSuiteScaffoldValue.Hidden,
    )
    LaunchedEffect(showSuite) {
        // Entering the editor or onboarding is already one motion (the screen transition); collapsing the suite
        // at the same time would be a second event, so it disappears instantly. Coming back, it may slide in.
        if (showSuite) suiteState.show() else suiteState.snapTo(NavigationSuiteScaffoldValue.Hidden)
    }

    NavigationSuiteScaffold(
        navigationItems = {
            TopLevelDestination.NavigationSuite.forEach { destination ->
                val item = destination.suiteItem()
                val selected = navigator.selected == destination
                NavigationSuiteItem(
                    selected = selected,
                    onClick = { navigator.select(destination) },
                    icon = {
                        Icon(
                            painter = painterResource(if (selected) item.selectedIcon else item.icon),
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(item.label)) },
                    navigationSuiteType = suiteType,
                    modifier = Modifier
                        .testTag(item.testTag)
                        .then(if (showSuite) Modifier else Modifier.semantics { hideFromAccessibility() }),
                )
            }
        },
        modifier = modifier,
        navigationSuiteType = suiteType,
        state = suiteState,
    ) {
        AppNavDisplay(
            navigator = navigator,
            destinations = destinations,
            hasTrips = hasTrips,
            directive = directive,
            isTwoPane = isTwoPane,
        )
    }
}

/** Effective dark theme for the user's [AppSettings.themeMode]. */
@Composable
private fun AppSettings.isDarkTheme(): Boolean = when (themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** How a navigation-suite destination is presented. */
private data class SuiteItem(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    val testTag: String,
)

private fun TopLevelDestination.suiteItem(): SuiteItem = when (this) {
    TopLevelDestination.Now -> SuiteItem(R.string.nav_now, R.drawable.ic_nav_now, R.drawable.ic_nav_now_filled, ShellTestTags.NavNow)
    TopLevelDestination.Trips ->
        SuiteItem(R.string.nav_trips, R.drawable.ic_nav_trips, R.drawable.ic_nav_trips_filled, ShellTestTags.NavTrips)
    TopLevelDestination.Settings ->
        SuiteItem(R.string.nav_settings, R.drawable.ic_nav_settings, R.drawable.ic_nav_settings_filled, ShellTestTags.NavSettings)
    TopLevelDestination.Onboarding -> error("Onboarding is not a navigation-suite destination")
}

/** Test tags of shell chrome, shared with unit and e2e tests. */
object ShellTestTags {
    const val NavNow = "nav_now"
    const val NavTrips = "nav_trips"
    const val NavSettings = "nav_settings"

    /** Tag wrapping every screen: `route_onboarding`, `route_now`, `route_trips`, `route_plan`, … */
    fun route(route: AppRoute): String = "route_" + when (route) {
        OnboardingRoute -> "onboarding"
        NowRoute -> "now"
        TripsRoute -> "trips"
        is PlanRoute -> "plan"
        is TripEditorRoute -> "trip_editor"
        SettingsRoute -> "settings"
        AboutRoute -> "about"
        LicensesRoute -> "licenses"
    }
}
