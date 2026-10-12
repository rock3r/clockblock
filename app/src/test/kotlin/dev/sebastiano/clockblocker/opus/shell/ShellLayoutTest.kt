package dev.sebastiano.clockblocker.opus.shell

import dev.sebastiano.clockblocker.opus.navigation.AboutRoute
import dev.sebastiano.clockblocker.opus.navigation.NowRoute
import dev.sebastiano.clockblocker.opus.navigation.OnboardingRoute
import dev.sebastiano.clockblocker.opus.navigation.PlanRoute
import dev.sebastiano.clockblocker.opus.navigation.SettingsRoute
import dev.sebastiano.clockblocker.opus.navigation.TripEditorRoute
import dev.sebastiano.clockblocker.opus.navigation.TripsRoute
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ShellLayoutTest {
    private val phonePortrait = shellLayoutFor(minWidthDp = 0, minHeightDp = 840)
    private val phoneLandscape = shellLayoutFor(minWidthDp = 840, minHeightDp = 0)
    private val tabletPortrait = shellLayoutFor(minWidthDp = 600, minHeightDp = 900)
    private val tabletLandscape = shellLayoutFor(minWidthDp = 840, minHeightDp = 480)
    private val desktop = shellLayoutFor(minWidthDp = 1600, minHeightDp = 900)

    @Test
    fun `trips and a plan sit side by side only on wide windows with room to spare in height`() {
        tabletLandscape.listDetail shouldBe true
        desktop.listDetail shouldBe true
        shellLayoutFor(minWidthDp = 1200, minHeightDp = 480).listDetail shouldBe true

        phonePortrait.listDetail shouldBe false
        tabletPortrait.listDetail shouldBe false
        // A landscape phone is wide but short: the plan needs the whole width, so it stays one pane.
        phoneLandscape.listDetail shouldBe false
    }

    @Test
    fun `a trip plan opened from Trips hides the navigation on phones, in either orientation`() {
        phonePortrait.showsNavigation(PlanRoute("a")) shouldBe false
        phoneLandscape.showsNavigation(PlanRoute("a")) shouldBe false
    }

    @Test
    fun `larger windows keep the navigation next to a trip plan, as a pane or pushed`() {
        tabletPortrait.showsNavigation(PlanRoute("a")) shouldBe true
        tabletLandscape.showsNavigation(PlanRoute("a")) shouldBe true
        desktop.showsNavigation(PlanRoute("a")) shouldBe true
    }

    @Test
    fun `onboarding and the trip editor are full screen on every window`() {
        listOf(phonePortrait, phoneLandscape, tabletPortrait, tabletLandscape, desktop).forEach { layout ->
            layout.showsNavigation(OnboardingRoute) shouldBe false
            layout.showsNavigation(TripEditorRoute()) shouldBe false
            layout.showsNavigation(TripEditorRoute(tripId = "a")) shouldBe false
        }
    }

    @Test
    fun `top-level screens and Settings pages always show the navigation`() {
        listOf(phonePortrait, phoneLandscape, tabletPortrait, tabletLandscape, desktop).forEach { layout ->
            listOf(NowRoute, TripsRoute, SettingsRoute, AboutRoute).forEach { route ->
                layout.showsNavigation(route) shouldBe true
            }
        }
    }
}
