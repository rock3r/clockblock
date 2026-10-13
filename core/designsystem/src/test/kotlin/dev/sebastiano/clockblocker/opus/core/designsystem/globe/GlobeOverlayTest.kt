package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** The full-screen globe covers what's under it: touches anywhere on it never reach the screen beneath. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w411dp-h891dp-xhdpi")
class GlobeOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `taps on the overlay's background, title and details never reach the screen under it`() {
        var underneath = 0
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().clickable { underneath++ }.testTag("under"))
                    val state = rememberRouteGlobeState()
                    GlobeOverlay(
                        visible = true,
                        onDismiss = {},
                        title = "Your route",
                        subtitle = "Lisbon to Tokyo",
                        state = state,
                        globe = { modifier ->
                            RouteGlobe(
                                legs = listOf(GlobeLeg(GlobeStop(GeoPoint(38.78, -9.14), "LIS"), GlobeStop(GeoPoint(35.55, 139.79), "HND"))),
                                instant = Instant.parse("2026-06-21T12:00:00Z"),
                                contentDescription = "",
                                state = state,
                                modifier = modifier,
                            )
                        },
                        details = { modifier -> Box(modifier.size(200.dp)) },
                    )
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
}
