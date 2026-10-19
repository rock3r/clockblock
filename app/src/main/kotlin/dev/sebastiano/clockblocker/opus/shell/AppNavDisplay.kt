package dev.sebastiano.clockblocker.opus.shell

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.sebastiano.clockblocker.opus.R
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.navigation.AboutRoute
import dev.sebastiano.clockblocker.opus.navigation.AppNavigator
import dev.sebastiano.clockblocker.opus.navigation.AppRoute
import dev.sebastiano.clockblocker.opus.navigation.LicensesRoute
import dev.sebastiano.clockblocker.opus.navigation.NowRoute
import dev.sebastiano.clockblocker.opus.navigation.OnboardingRoute
import dev.sebastiano.clockblocker.opus.navigation.PlanRoute
import dev.sebastiano.clockblocker.opus.navigation.SettingsRoute
import dev.sebastiano.clockblocker.opus.navigation.TopLevelDestination
import dev.sebastiano.clockblocker.opus.navigation.TripEditorRoute
import dev.sebastiano.clockblocker.opus.navigation.TripsRoute
import dev.sebastiano.clockblocker.opus.navigation.rememberAppTransitions

/** Scene key shared by the trips list and plan detail entries of the list-detail layout. */
private const val TripsSceneKey = "trips"

/** The list pane's default width, and the snap points of the handle between the panes. */
private val ListPaneWidth = 360.dp

/**
 * The app's `NavDisplay`: decorates each back stack separately (saveable state + its own `ViewModelStore` per
 * entry), shows home + the selected destination, and animates everything with [rememberAppTransitions].
 *
 * When [directive] allows two panes, Trips and a trip's plan share the window as list and detail (M3
 * `ListDetailSceneStrategy`): the plan has no up arrow, the list highlights the open trip, and a placeholder fills
 * the detail pane until a trip is picked. A drag handle between the panes resizes them. With one pane the
 * strategy steps aside (`shouldHandleSinglePaneLayout = false`), so pushing a plan is the same shared-axis
 * hierarchy transition, predictive back included, as every other push.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppNavDisplay(
    navigator: AppNavigator,
    destinations: AppDestinations,
    hasTrips: Boolean,
    directive: PaneScaffoldDirective,
    modifier: Modifier = Modifier,
) {
    val transitions = rememberAppTransitions()
    val currentHasTrips by rememberUpdatedState(hasTrips)
    val isTwoPane = directive.maxHorizontalPartitions > 1
    val currentIsTwoPane by rememberUpdatedState(isTwoPane)
    val handleDescription = stringResource(R.string.pane_handle_description)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(
        shouldHandleSinglePaneLayout = false,
        backNavigationBehavior = BackNavigationBehavior.PopLatest,
        directive = directive,
        // Snap points for the handle: the default list width, half and half, and a wide list (two columns of
        // trip cards) beside a narrower plan.
        paneExpansionState = rememberPaneExpansionState(
            anchors = listOf(
                PaneExpansionAnchor.Offset.fromStart(ListPaneWidth),
                PaneExpansionAnchor.Proportion(0.5f),
                PaneExpansionAnchor.Offset.fromEnd(ListPaneWidth),
            ),
        ),
        paneExpansionDragHandle = { state ->
            val interactionSource = remember { MutableInteractionSource() }
            VerticalDragHandle(
                modifier = Modifier
                    .paneExpansionDraggable(
                        state = state,
                        minTouchTargetSize = 48.dp,
                        interactionSource = interactionSource,
                        semanticsProperties = { contentDescription = handleDescription },
                    )
                    .testTag(ShellTestTags.PaneHandle),
                interactionSource = interactionSource,
            )
        },
    )
    val paneMotion = ListDetailSceneStrategy.paneAnimation(
        enterTransition = transitions.paneEnter(),
        exitTransition = transitions.paneExit(),
        boundsAnimationSpec = ClockblockTheme.motion.navigationSpatial(),
    )

    val provider: (NavKey) -> NavEntry<NavKey> = entryProvider {
        entry<OnboardingRoute> {
            val guard = rememberNavigationGuard()
            destinations.Onboarding(
                onFinished = guard { navigator.finishOnboarding(currentHasTrips) },
                onTakeoff = navigator::armTakeoff,
            )
        }
        entry<NowRoute> {
            val guard = rememberNavigationGuard()
            destinations.Plan(
                tripId = null,
                onBack = null,
                onEditTrip = guard.of { id: String -> navigator.openTripEditor(tripId = id) },
                onCreateReturnTrip = guard.of { id: String -> navigator.openTripEditor(returnOfTripId = id) },
                onNewTrip = guard { navigator.openTripEditor() },
                onOpenTrips = guard { navigator.select(TopLevelDestination.Trips) },
            )
        }
        entry<TripsRoute>(
            metadata = ListDetailSceneStrategy.listPane(
                sceneKey = TripsSceneKey,
                detailPlaceholder = { TripPlanPlaceholder(hasTrips = currentHasTrips) },
            ) + paneMotion,
        ) {
            val guard = rememberNavigationGuard()
            val openTripId = (navigator.backStacks.getValue(TopLevelDestination.Trips).last() as? PlanRoute)?.tripId
            destinations.Trips(
                selectedTripId = if (currentIsTwoPane) openTripId else null,
                onOpenTrip = guard.of { id: String -> navigator.openPlan(id) },
                onNewTrip = guard { navigator.openTripEditor() },
                onOpenSettings = guard { navigator.select(TopLevelDestination.Settings) },
                onEditTrip = guard.of { id: String -> navigator.openTripEditor(tripId = id) },
                onCreateReturnTrip = guard.of { id: String -> navigator.openTripEditor(returnOfTripId = id) },
                onOpenNow = guard { navigator.select(TopLevelDestination.Now) },
            )
        }
        entry<PlanRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = TripsSceneKey) + paneMotion) { route ->
            val guard = rememberNavigationGuard()
            PaneBack(
                enabled = currentIsTwoPane,
                transitions = transitions,
                onBack = { navigator.goBack() },
                placeholder = { TripPlanPlaceholder(hasTrips = currentHasTrips) },
            ) {
                destinations.Plan(
                    tripId = route.tripId,
                    // Beside the list, the plan is a pane, not a pushed screen: no up arrow (back still closes it).
                    onBack = if (currentIsTwoPane) null else guard { navigator.goBack() },
                    onEditTrip = guard.of { id: String -> navigator.openTripEditor(tripId = id) },
                    onCreateReturnTrip = guard.of { id: String -> navigator.openTripEditor(returnOfTripId = id) },
                    onNewTrip = guard { navigator.openTripEditor() },
                )
            }
        }
        entry<TripEditorRoute> { route ->
            val guard = rememberNavigationGuard()
            destinations.TripEditor(
                tripId = route.tripId,
                returnOfTripId = route.returnOfTripId,
                onDone = guard.of { saved: String? -> navigator.finishTripEditor(saved) },
                onBack = guard { navigator.goBack() },
            )
        }
        entry<SettingsRoute> {
            val guard = rememberNavigationGuard()
            destinations.Settings(
                onOpenAbout = guard { navigator.navigate(AboutRoute) },
                onReplayOnboarding = guard { navigator.replayOnboarding() },
            )
        }
        entry<AboutRoute> {
            val guard = rememberNavigationGuard()
            destinations.About(
                onBack = guard { navigator.goBack() },
                onOpenLicenses = guard { navigator.navigate(LicensesRoute) },
            )
        }
        entry<LicensesRoute> {
            val guard = rememberNavigationGuard()
            destinations.Licenses(onBack = guard { navigator.goBack() })
        }
    }

    // One set of decorators per back stack, so hidden destinations keep their state and ViewModels.
    val entriesByDestination = TopLevelDestination.entries.associateWith { destination ->
        key(destination) {
            rememberDecoratedNavEntries(
                backStack = navigator.backStacks.getValue(destination),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = { route -> provider(route).scopedTo(route, destination) },
            )
        }
    }

    NavDisplay(
        entries = navigator.destinationsInUse.flatMap { entriesByDestination.getValue(it) },
        modifier = modifier,
        sceneStrategies = listOf(listDetailStrategy),
        transitionSpec = { transitions.forNavigation(navigator.lastDirection, isPop = false) },
        popTransitionSpec = { transitions.forNavigation(navigator.lastDirection, isPop = true) },
        predictivePopTransitionSpec = { swipeEdge -> transitions.predictiveBack(swipeEdge) },
        onBack = { navigator.goBack() },
    )
}

/**
 * The same route can live in two back stacks (e.g. a new-trip editor under Now and under Trips); prefixing the
 * content key keeps saved state and ViewModel stores apart. The content is tagged with
 * [ShellTestTags.route] so e2e tests can find screens independently of the feature UIs.
 */
private fun NavEntry<NavKey>.scopedTo(route: NavKey, destination: TopLevelDestination): NavEntry<NavKey> {
    val base = this
    return NavEntry(
        key = route,
        contentKey = "${destination.name}/${base.contentKey}",
        metadata = base.metadata,
    ) {
        Box(Modifier.fillMaxSize().testTag(ShellTestTags.route(route as AppRoute)), propagateMinConstraints = true) {
            base.Content()
        }
    }
}

/**
 * Drops navigation requests while the entry is not resumed (e.g. mid-transition), so a double tap can't push
 * twice. Mirrors `dropUnlessResumed` for callbacks with arguments.
 */
private class NavigationGuard(private val lifecycle: Lifecycle) {
    private val resumed: Boolean get() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    operator fun invoke(block: () -> Unit): () -> Unit = { if (resumed) block() }

    fun <A> of(block: (A) -> Unit): (A) -> Unit = { arg -> if (resumed) block(arg) }
}

@Composable
private fun rememberNavigationGuard(): NavigationGuard = NavigationGuard(LocalLifecycleOwner.current.lifecycle)
