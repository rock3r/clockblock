package dev.sebastiano.clockblocker.opus.core.designsystem

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.LocalReduceMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule

/**
 * Base for Roborazzi screenshot tests. Subclasses carry the Robolectric annotations. Content renders with
 * [LocalReduceMotion] = true by default, which both makes goldens deterministic and proves every component
 * has a readable static end state (MOTION.md "Reduced motion").
 */
abstract class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    fun snap(
        name: String,
        darkTheme: Boolean = false,
        nightSafe: Boolean = false,
        opusMode: Boolean = false,
        fontScale: Float? = null,
        background: Boolean = true,
        use24Hour: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        setClockFormat(use24Hour)
        compose.setContent {
            Themed(darkTheme, nightSafe, opusMode, fontScale, background, reduceMotion = true, content)
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    /**
     * A frame in the middle of a transition: renders [content] with `changed = false` and motion **on**, flips it
     * to `true` after the first frame, advances the paused clock by [advanceMillis] and captures. Proves the
     * moving frames (rolls, reveals, springs) are drawn correctly, not just the static end state.
     */
    fun snapMidChange(
        name: String,
        advanceMillis: Long,
        darkTheme: Boolean = false,
        content: @Composable (changed: Boolean) -> Unit,
    ) {
        setClockFormat(true)
        var changed by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Themed(darkTheme, nightSafe = false, opusMode = false, fontScale = null, background = true, reduceMotion = false) {
                content(changed)
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread {
            changed = true
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(advanceMillis)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun setClockFormat(use24Hour: Boolean) {
        // Goldens use the 24-hour clock (the design's reference); 12-hour has its own coverage.
        Settings.System.putString(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
            Settings.System.TIME_12_24,
            if (use24Hour) "24" else "12",
        )
    }

    @Composable
    private fun Themed(
        darkTheme: Boolean,
        nightSafe: Boolean,
        opusMode: Boolean,
        fontScale: Float?,
        background: Boolean,
        reduceMotion: Boolean,
        content: @Composable () -> Unit,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides if (fontScale != null) Density(density.density, fontScale) else density,
        ) {
            OpusTheme(
                darkTheme = darkTheme,
                dynamicColor = false,
                nightSafe = nightSafe,
                opusMode = opusMode,
                reduceMotion = reduceMotion,
            ) {
                val bg = if (background) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier
                Box(bg.padding(16.dp)) { content() }
            }
        }
    }
}
