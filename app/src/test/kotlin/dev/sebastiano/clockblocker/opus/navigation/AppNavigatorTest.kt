package dev.sebastiano.clockblocker.opus.navigation

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AppNavigatorTest {

    @Test
    fun `start destination follows profile and trips`() {
        AppNavigator.startDestination(hasProfile = false, hasTrips = true) shouldBe TopLevelDestination.Onboarding
        AppNavigator.startDestination(hasProfile = true, hasTrips = true) shouldBe TopLevelDestination.Now
        AppNavigator.startDestination(hasProfile = true, hasTrips = false) shouldBe TopLevelDestination.Trips
    }

    @Test
    fun `each destination keeps its own back stack`() {
        val navigator = AppNavigator(TopLevelDestination.Now)
        navigator.select(TopLevelDestination.Trips)
        navigator.openPlan("a")
        navigator.select(TopLevelDestination.Settings)
        navigator.navigate(AboutRoute)

        navigator.select(TopLevelDestination.Trips)

        navigator.currentBackStack shouldContainExactly listOf(TripsRoute, PlanRoute("a"))
        navigator.backStacks.getValue(TopLevelDestination.Settings) shouldContainExactly listOf(SettingsRoute, AboutRoute)
        navigator.lastDirection shouldBe NavigationDirection.TopLevel
    }

    @Test
    fun `home stays underneath other destinations so back exits through it`() {
        val navigator = AppNavigator(TopLevelDestination.Now)
        navigator.destinationsInUse shouldContainExactly listOf(TopLevelDestination.Now)

        navigator.select(TopLevelDestination.Settings)
        navigator.destinationsInUse shouldContainExactly listOf(TopLevelDestination.Now, TopLevelDestination.Settings)

        navigator.goBack().shouldBeTrue()
        navigator.selected shouldBe TopLevelDestination.Now
        navigator.lastDirection shouldBe NavigationDirection.TopLevel
        navigator.goBack().shouldBeFalse()
    }

    @Test
    fun `back pops the hierarchy before leaving the destination`() {
        val navigator = AppNavigator(TopLevelDestination.Now)
        navigator.select(TopLevelDestination.Settings)
        navigator.navigate(AboutRoute)
        navigator.navigate(LicensesRoute)

        navigator.goBack().shouldBeTrue()
        navigator.currentRoute shouldBe AboutRoute
        navigator.lastDirection shouldBe NavigationDirection.Backward
    }

    @Test
    fun `reselecting the current destination pops to its root`() {
        val navigator = AppNavigator(TopLevelDestination.Trips)
        navigator.openPlan("a")

        navigator.select(TopLevelDestination.Trips)

        navigator.currentBackStack shouldContainExactly listOf(TripsRoute)
        navigator.lastDirection shouldBe NavigationDirection.Backward
    }

    @Test
    fun `opening a plan replaces the plan already open`() {
        val navigator = AppNavigator(TopLevelDestination.Trips)
        navigator.openPlan("a")
        navigator.openPlan("b")

        navigator.currentBackStack shouldContainExactly listOf(TripsRoute, PlanRoute("b"))
        navigator.lastDirection shouldBe NavigationDirection.Forward
    }

    @Test
    fun `a new trip saved from Trips opens its plan in place of the editor`() {
        val navigator = AppNavigator(TopLevelDestination.Trips)
        navigator.openTripEditor()

        navigator.finishTripEditor(savedTripId = "new")

        navigator.currentBackStack shouldContainExactly listOf(TripsRoute, PlanRoute("new"))
        navigator.lastDirection shouldBe NavigationDirection.Backward
    }

    @Test
    fun `editing an existing trip returns to its plan`() {
        val navigator = AppNavigator(TopLevelDestination.Trips)
        navigator.openPlan("a")
        navigator.openTripEditor(tripId = "a")

        navigator.finishTripEditor(savedTripId = "a")

        navigator.currentBackStack shouldContainExactly listOf(TripsRoute, PlanRoute("a"))
    }

    @Test
    fun `a trip created from Now returns to Now`() {
        val navigator = AppNavigator(TopLevelDestination.Now)
        navigator.openTripEditor()

        navigator.finishTripEditor(savedTripId = "new")

        navigator.currentBackStack shouldContainExactly listOf(NowRoute)
    }

    @Test
    fun `a discarded editor just closes`() {
        val navigator = AppNavigator(TopLevelDestination.Trips)
        navigator.openTripEditor()

        navigator.finishTripEditor(savedTripId = null)

        navigator.currentBackStack shouldContainExactly listOf(TripsRoute)
    }

    @Test
    fun `first-run onboarding lands on Now with trips, Trips without`() {
        AppNavigator(TopLevelDestination.Onboarding).apply {
            finishOnboarding(hasTrips = true)
            selected shouldBe TopLevelDestination.Now
            home shouldBe TopLevelDestination.Now
        }
        AppNavigator(TopLevelDestination.Onboarding).apply {
            finishOnboarding(hasTrips = false)
            selected shouldBe TopLevelDestination.Trips
            home shouldBe TopLevelDestination.Trips
            goBack().shouldBeFalse()
        }
    }

    @Test
    fun `replayed onboarding pops back to settings`() {
        val navigator = AppNavigator(TopLevelDestination.Now)
        navigator.select(TopLevelDestination.Settings)
        navigator.replayOnboarding()
        navigator.currentRoute shouldBe OnboardingRoute
        navigator.isOnboarding.shouldBeFalse()

        navigator.finishOnboarding(hasTrips = true)

        navigator.currentBackStack shouldContainExactly listOf(SettingsRoute)
        navigator.selected shouldBe TopLevelDestination.Settings
    }

    @Test
    fun `losing the profile resets to onboarding with fresh stacks`() {
        val navigator = AppNavigator(TopLevelDestination.Trips)
        navigator.openPlan("a")

        navigator.requireOnboarding()

        navigator.selected shouldBe TopLevelDestination.Onboarding
        navigator.destinationsInUse shouldContainExactly listOf(TopLevelDestination.Onboarding)
        navigator.backStacks.getValue(TopLevelDestination.Trips) shouldContainExactly listOf(TripsRoute)
    }

    @Test
    fun `a deep link replaces the target stack with its synthetic back stack`() {
        val navigator = AppNavigator(TopLevelDestination.Now)
        navigator.select(TopLevelDestination.Trips)
        navigator.openTripEditor()
        navigator.select(TopLevelDestination.Settings)

        navigator.open(DeepLinkParser.parse("clockblock://plan/x")!!)

        navigator.selected shouldBe TopLevelDestination.Trips
        navigator.currentBackStack shouldContainExactly listOf(TripsRoute, PlanRoute("x"))
        navigator.lastDirection shouldBe NavigationDirection.TopLevel
        navigator.goBack().shouldBeTrue()
        navigator.currentRoute shouldBe TripsRoute
    }

    @Test
    fun `a deep link during first-run onboarding waits for it to finish`() {
        val navigator = AppNavigator(TopLevelDestination.Onboarding)

        navigator.open(DeepLinkParser.parse("clockblock://trips/new")!!)
        navigator.selected shouldBe TopLevelDestination.Onboarding

        navigator.finishOnboarding(hasTrips = false)
        navigator.selected shouldBe TopLevelDestination.Trips
        navigator.currentBackStack shouldContainExactly listOf(TripsRoute, TripEditorRoute())
    }
}
