package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** What happens to a pick, and to the framing, when the route or the active leg changes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class RouteGlobeSelectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val lis = GlobeStop(GeoPoint(38.78, -9.14), "LIS")
    private val doh = GlobeStop(GeoPoint(25.27, 51.61), "DOH")
    private val hnd = GlobeStop(GeoPoint(35.55, 139.79), "HND")
    private val jfk = GlobeStop(GeoPoint(40.64, -73.78), "JFK")

    private fun show(state: RouteGlobeState, legs: () -> List<GlobeLeg>) {
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                RouteGlobe(
                    legs = legs(),
                    instant = Instant.parse("2026-06-21T12:00:00Z"),
                    contentDescription = "",
                    state = state,
                    interaction = GlobeInteraction.Rotate,
                    modifier = Modifier.size(300.dp),
                )
            }
        }
    }

    @Test
    fun `a pick made before the globe appears is kept`() {
        val state = RouteGlobeState(GlobeView(20.0, 0.0)).apply { selection = GlobeSelection.Leg(1) }
        show(state) { listOf(GlobeLeg(lis, doh), GlobeLeg(doh, hnd)) }
        compose.waitForIdle()
        state.selection shouldBe GlobeSelection.Leg(1)
    }

    @Test
    fun `a new route with as many flights clears the pick, the same route again keeps it`() {
        val state = RouteGlobeState(GlobeView(20.0, 0.0))
        var legs by mutableStateOf(listOf(GlobeLeg(lis, doh), GlobeLeg(doh, hnd)))
        show(state) { legs }
        compose.waitForIdle()
        state.selection = GlobeSelection.Leg(1)

        // An equal route (a new list with the same flights) means the same picks.
        legs = listOf(GlobeLeg(lis, doh), GlobeLeg(doh, hnd))
        compose.waitForIdle()
        state.selection shouldBe GlobeSelection.Leg(1)

        // Another route of two flights: index 1 would now name JFK → HND, so the pick goes.
        legs = listOf(GlobeLeg(lis, jfk), GlobeLeg(jfk, hnd))
        compose.waitForIdle()
        state.selection shouldBe null
    }

    @Test
    fun `a tap while the globe turns to a newly active leg lets the turn finish`() {
        val state = RouteGlobeState(GlobeView(20.0, 0.0))
        val legs = listOf(GlobeLeg(lis, doh), GlobeLeg(doh, hnd))
        var active by mutableStateOf(0)
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = false) {
                RouteGlobe(
                    legs = legs,
                    instant = Instant.parse("2026-06-21T12:00:00Z"),
                    contentDescription = "",
                    activeLeg = active,
                    state = state,
                    interaction = GlobeInteraction.Rotate,
                    modifier = Modifier.size(300.dp).testTag("globe"),
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        active = 1
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("globe").performTouchInput { click(center) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val target = GlobeGeo.routeView(legs.map { GlobeGeo.greatCircle(it.from.point, it.to.point) }, 1)
        state.view.latitude shouldBe (target.latitude plusOrMinus 0.01)
        GlobeGeo.longitudeDelta(state.view.longitude, target.longitude) shouldBe (0.0 plusOrMinus 0.01)
        state.moved shouldBe false
    }
}
