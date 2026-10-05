package dev.sebastiano.clockblocker.opus.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/** How the last navigation should animate; read by the shell's transition specs. */
enum class NavigationDirection {
    /** Deeper into a hierarchy (push): shared-axis forward. */
    Forward,

    /** Back up a hierarchy (pop): shared-axis backward. */
    Backward,

    /** Between top-level destinations: fade-through. */
    TopLevel,
}

/**
 * Navigation state of the app: one back stack per [TopLevelDestination] (multiple back stacks), the selected
 * destination and the "home" destination users exit the app through.
 *
 * All navigation goes through this class so it can record the [lastDirection] the transition specs animate.
 * State is snapshot-backed: create it with [rememberAppNavigator] (saved across configuration changes and process
 * death) or directly in tests.
 */
@Stable
class AppNavigator(
    selected: MutableState<TopLevelDestination>,
    home: MutableState<TopLevelDestination>,
    /** One back stack per destination; each always starts with its [TopLevelDestination.root]. */
    val backStacks: Map<TopLevelDestination, NavBackStack<NavKey>>,
) {
    /** Creates unsaved state (tests, previews). */
    constructor(start: TopLevelDestination) : this(
        selected = mutableStateOf(start),
        home = mutableStateOf(homeFor(start)),
        backStacks = TopLevelDestination.entries.associateWith { NavBackStack(it.root) },
    )

    init {
        require(backStacks.keys == TopLevelDestination.entries.toSet()) { "One back stack per destination" }
    }

    /** The destination whose back stack is on screen. */
    var selected: TopLevelDestination by selected
        private set

    /** The destination users exit through: back from another destination's root returns here. */
    var home: TopLevelDestination by home
        private set

    /** How the most recent change should animate (not snapshot state: read when a transition starts). */
    var lastDirection: NavigationDirection = NavigationDirection.Forward
        private set

    /** A deep link received during first-run onboarding, applied once it completes. */
    private var pendingDeepLink: DeepLinkTarget? = null

    val currentBackStack: NavBackStack<NavKey> get() = backStacks.getValue(selected)

    /** The route on top of the visible back stack. */
    val currentRoute: AppRoute get() = currentBackStack.last() as AppRoute

    /**
     * Destinations whose entries are handed to `NavDisplay`, bottom to top. Home is always underneath, so
     * predictive back from another destination's root previews home ("exit through home").
     */
    val destinationsInUse: List<TopLevelDestination>
        get() = if (selected == home || selected == TopLevelDestination.Onboarding) listOf(selected) else listOf(home, selected)

    /** True while the first-run onboarding flow is showing (not a replay from Settings). */
    val isOnboarding: Boolean get() = selected == TopLevelDestination.Onboarding

    /**
     * Selects a navigation-suite destination. Re-selecting the current one pops it back to its root (the
     * platform convention for tapping the active tab).
     */
    fun select(destination: TopLevelDestination) {
        require(destination != TopLevelDestination.Onboarding) { "Onboarding is not a navigation-suite destination" }
        if (destination == selected) {
            val stack = currentBackStack
            if (stack.size > 1) {
                lastDirection = NavigationDirection.Backward
                stack.retainOnly(1)
            }
        } else {
            lastDirection = NavigationDirection.TopLevel
            selected = destination
        }
    }

    /** Pushes [route] on the visible back stack. */
    fun navigate(route: AppRoute) {
        lastDirection = NavigationDirection.Forward
        currentBackStack.add(route)
    }

    /**
     * Opens a trip's plan inside the Trips destination. Any plan already open there is replaced, so the detail
     * pane of the list-detail layout never accumulates history.
     */
    fun openPlan(tripId: String) {
        val trips = backStacks.getValue(TopLevelDestination.Trips)
        val switching = selected != TopLevelDestination.Trips
        while (trips.size > 1 && trips.last() is PlanRoute) trips.removeAt(trips.lastIndex)
        trips.add(PlanRoute(tripId))
        lastDirection = if (switching) NavigationDirection.TopLevel else NavigationDirection.Forward
        selected = TopLevelDestination.Trips
    }

    /** Opens the trip editor on the visible back stack. */
    fun openTripEditor(tripId: String? = null, returnOfTripId: String? = null) {
        navigate(TripEditorRoute(tripId = tripId, returnOfTripId = returnOfTripId))
    }

    /**
     * Closes the editor on top of the visible back stack. A newly created trip ([savedTripId] for an editor that
     * had no trip id) opens its plan when the editor was launched from Trips; elsewhere (e.g. Now) the screen
     * underneath already reflects the new trip.
     */
    fun finishTripEditor(savedTripId: String?) {
        val stack = currentBackStack
        val editor = stack.lastOrNull() as? TripEditorRoute ?: return
        lastDirection = NavigationDirection.Backward
        stack.removeAt(stack.lastIndex)
        if (savedTripId != null && editor.tripId == null && selected == TopLevelDestination.Trips) {
            while (stack.size > 1 && stack.last() is PlanRoute) stack.removeAt(stack.lastIndex)
            stack.add(PlanRoute(savedTripId))
        }
    }

    /**
     * Handles back. Pops the visible stack; at a non-home root returns to home. Returns false when there is
     * nothing left to pop (the system then leaves the app).
     */
    fun goBack(): Boolean {
        val stack = currentBackStack
        return when {
            stack.size > 1 -> {
                lastDirection = NavigationDirection.Backward
                stack.removeAt(stack.lastIndex)
                true
            }

            selected != home && selected != TopLevelDestination.Onboarding -> {
                lastDirection = NavigationDirection.TopLevel
                selected = home
                true
            }

            else -> false
        }
    }

    /** Replays onboarding on top of the visible back stack (from Settings). */
    fun replayOnboarding() {
        navigate(OnboardingRoute)
    }

    /**
     * Onboarding finished. After first-run onboarding, lands on Now when the user already has trips, otherwise on
     * Trips (and applies a deep link received meanwhile); after a replay, simply pops it.
     */
    fun finishOnboarding(hasTrips: Boolean) {
        if (isOnboarding) {
            home = if (hasTrips) TopLevelDestination.Now else TopLevelDestination.Trips
            lastDirection = NavigationDirection.TopLevel
            selected = home
            pendingDeepLink?.let { pendingDeepLink = null; open(it) }
        } else {
            val stack = currentBackStack
            if (stack.last() == OnboardingRoute) {
                lastDirection = NavigationDirection.Backward
                stack.removeAt(stack.lastIndex)
            }
        }
    }

    /** The profile is gone (e.g. all data deleted): back to first-run onboarding with fresh back stacks. */
    fun requireOnboarding() {
        if (isOnboarding) return
        lastDirection = NavigationDirection.TopLevel
        selected = TopLevelDestination.Onboarding
        backStacks.forEach { (destination, stack) ->
            if (destination != TopLevelDestination.Onboarding) stack.retainOnly(1)
        }
    }

    /**
     * Shows a deep link [target], replacing the target destination's back stack with the synthetic one. Deferred
     * until first-run onboarding completes.
     */
    fun open(target: DeepLinkTarget) {
        if (isOnboarding) {
            pendingDeepLink = target
            return
        }
        val stack = backStacks.getValue(target.destination)
        lastDirection = if (target.destination == selected) NavigationDirection.Forward else NavigationDirection.TopLevel
        stack.retainOnly(1)
        stack.addAll(target.backStack.drop(1))
        selected = target.destination
    }

    companion object {
        /** Home for a fresh start: the start destination, or Now while onboarding is pending. */
        fun homeFor(start: TopLevelDestination): TopLevelDestination =
            if (start == TopLevelDestination.Onboarding) TopLevelDestination.Now else start

        /** The first destination: onboarding without a profile, then Now if there are trips, else Trips. */
        fun startDestination(hasProfile: Boolean, hasTrips: Boolean): TopLevelDestination = when {
            !hasProfile -> TopLevelDestination.Onboarding
            hasTrips -> TopLevelDestination.Now
            else -> TopLevelDestination.Trips
        }
    }
}

/**
 * Remembers an [AppNavigator] whose selection and back stacks survive configuration changes and process death.
 * [start] is only used the first time; restored state wins afterwards.
 */
@Composable
fun rememberAppNavigator(start: TopLevelDestination): AppNavigator {
    val selected = rememberSaveable { mutableStateOf(start) }
    val home = rememberSaveable { mutableStateOf(AppNavigator.homeFor(start)) }
    val backStacks = TopLevelDestination.entries.associateWith { destination ->
        key(destination) { rememberNavBackStack(AppRouteSavedStateConfiguration, destination.root) }
    }
    return remember { AppNavigator(selected, home, backStacks) }
}

private fun NavBackStack<NavKey>.retainOnly(count: Int) {
    while (size > count) removeAt(lastIndex)
}
