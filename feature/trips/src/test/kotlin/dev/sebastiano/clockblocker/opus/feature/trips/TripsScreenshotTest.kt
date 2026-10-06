package dev.sebastiano.clockblocker.opus.feature.trips

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import org.junit.Rule
import org.robolectric.RuntimeEnvironment

/**
 * Base for Roborazzi screenshots of whole screens. Renders with reduce motion on (deterministic goldens, and
 * proof every state reads without animation), static colours and the 24 h clock.
 */
abstract class TripsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /** Phone portrait by default; pass e.g. `"w960dp-h720dp"` for an expanded window. */
    fun setContent(
        darkTheme: Boolean = false,
        fontScale: Float? = null,
        qualifiers: String? = null,
        content: @Composable () -> Unit,
    ) {
        qualifiers?.let { RuntimeEnvironment.setQualifiers("+$it") }
        Settings.System.putString(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.System.TIME_12_24,
            "24",
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides if (fontScale != null) Density(density.density, fontScale) else density,
            ) {
                ClockblockTheme(darkTheme = darkTheme, dynamicColor = false, reduceMotion = true) {
                    Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
                }
            }
        }
    }

    fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    fun snap(
        name: String,
        darkTheme: Boolean = false,
        fontScale: Float? = null,
        qualifiers: String? = null,
        content: @Composable () -> Unit,
    ) {
        setContent(darkTheme, fontScale, qualifiers, content)
        capture(name)
    }
}
