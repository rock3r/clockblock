package dev.sebastiano.clockblocker.opus.feature.settings

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

/**
 * Base for Roborazzi screenshot tests. Content renders with reduce motion on (deterministic goldens that also
 * prove each static carrier) and the 24-hour clock. Subclasses carry the Robolectric annotations and pick the
 * device size with `@Config(qualifiers = ...)`.
 */
abstract class ClockblockScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    fun snap(
        name: String,
        darkTheme: Boolean = false,
        fontScale: Float? = null,
        reduceMotion: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        setContent(darkTheme, fontScale, reduceMotion, content)
        capture(name)
    }

    fun setContent(
        darkTheme: Boolean = false,
        fontScale: Float? = null,
        reduceMotion: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        Settings.System.putString(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
            Settings.System.TIME_12_24,
            "24",
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides if (fontScale != null) Density(density.density, fontScale) else density,
            ) {
                ClockblockTheme(darkTheme = darkTheme, dynamicColor = false, reduceMotion = reduceMotion) {
                    Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
                }
            }
        }
    }

    fun capture(name: String) {
        // Let any spring (ToggleButton shape morphs, autosized labels) fully settle so goldens are stable.
        compose.mainClock.advanceTimeBy(SettleMillis)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

private const val SettleMillis = 2_000L
