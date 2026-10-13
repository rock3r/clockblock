package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The full-screen globe covers what's under it: touches anywhere on it never reach the screen beneath. It offers
 * TalkBack only the zoom steps that do something, and owns the status bar's icons only from the window's top-start.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w411dp-h891dp-xhdpi")
class GlobeOverlayTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var state: RouteGlobeState

    @Composable
    private fun Overlay(modifier: Modifier = Modifier, details: (@Composable (Modifier) -> Unit)? = null) {
        state = rememberRouteGlobeState()
        GlobeOverlay(
            visible = true,
            onDismiss = {},
            title = "Your route",
            subtitle = "Lisbon to Tokyo",
            state = state,
            modifier = modifier,
            globe = { globeModifier ->
                RouteGlobe(
                    legs = listOf(GlobeLeg(GlobeStop(GeoPoint(38.78, -9.14), "LIS"), GlobeStop(GeoPoint(35.55, 139.79), "HND"))),
                    instant = Instant.parse("2026-06-21T12:00:00Z"),
                    contentDescription = "",
                    state = state,
                    interaction = GlobeInteraction.RotateAndZoom,
                    modifier = globeModifier.testTag(GlobeTag),
                )
            },
            details = details,
        )
    }

    @Test
    fun `taps on the overlay's background, title and details never reach the screen under it`() {
        var underneath = 0
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().clickable { underneath++ }.testTag("under"))
                    Overlay(details = { modifier -> Box(modifier.size(200.dp)) })
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().performTouchInput {
            click(Offset(width * 0.5f, height * 0.04f)) // the title row
            click(Offset(width * 0.9f, height * 0.06f)) // beside the title
            click(Offset(width * 0.1f, height * 0.97f)) // the details, at the bottom
            click(Offset(width * 0.02f, height * 0.5f)) // the night sky at the edge, beside the globe
        }
        compose.waitForIdle()
        underneath shouldBe 0
    }

    private fun actionLabels(): List<String> =
        compose.onNodeWithTag(GlobeTag).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
            .map(CustomAccessibilityAction::label)

    @Test
    fun `TalkBack is only offered the zoom steps that change the zoom`() {
        compose.setContent { ClockblockTheme(dynamicColor = false, reduceMotion = true) { Overlay() } }
        compose.waitForIdle()
        actionLabels() shouldContain "Zoom in"
        actionLabels() shouldNotContain "Zoom out"

        compose.runOnIdle { runBlocking { state.zoomTo(RouteGlobeState.MaxZoom, null) } }
        compose.waitForIdle()
        actionLabels() shouldNotContain "Zoom in"
        actionLabels() shouldContain "Zoom out"
    }

    @Test
    fun `TalkBack is only offered the tilts that move the globe`() {
        compose.setContent { ClockblockTheme(dynamicColor = false, reduceMotion = true) { Overlay() } }
        compose.waitForIdle()
        actionLabels() shouldContain "Tilt the globe north"
        actionLabels() shouldContain "Tilt the globe south"

        compose.runOnIdle { runBlocking { state.tiltBy(180f, null) } }
        compose.waitForIdle()
        actionLabels() shouldNotContain "Tilt the globe north"
        actionLabels() shouldContain "Tilt the globe south"

        compose.runOnIdle { runBlocking { state.tiltBy(-360f, null) } }
        compose.waitForIdle()
        actionLabels() shouldContain "Tilt the globe north"
        actionLabels() shouldNotContain "Tilt the globe south"
    }

    private val lightStatusBars: Boolean
        get() {
            val window = compose.activity.window
            return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
        }

    private fun startInLightTheme() = compose.runOnUiThread {
        val window = compose.activity.window
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
    }

    @Test
    fun `over the whole window it turns the status bar's icons light`() {
        startInLightTheme()
        compose.setContent { ClockblockTheme(dynamicColor = false, reduceMotion = true) { Overlay(Modifier.fillMaxSize()) } }
        compose.waitForIdle()
        lightStatusBars shouldBe false
    }

    @Test
    fun `in a trailing pane it leaves the status bar's icons to the leading pane`() {
        startInLightTheme()
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight())
                    Overlay(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        compose.waitForIdle()
        lightStatusBars shouldBe true
    }

    @Test
    fun `once the overlay covers the screen, what is under it stops drawing, and draws again as it leaves`() {
        val cover = GlobeOverlayCover()
        var visible by mutableStateOf(false)
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().hiddenUnder(cover).testTag("under"))
                    val state = rememberRouteGlobeState()
                    GlobeOverlay(visible = visible, onDismiss = {}, title = "Your route", state = state, globe = {}, cover = cover)
                }
            }
        }
        compose.waitForIdle()
        cover.covering shouldBe false
        visible = true
        compose.waitForIdle()
        cover.covering shouldBe true
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            visible = false
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        // From the first frame of its exit, the screen under it is drawn again (it shows through the fade).
        cover.covering shouldBe false
    }

    private companion object {
        const val GlobeTag = "globe"
    }
}
