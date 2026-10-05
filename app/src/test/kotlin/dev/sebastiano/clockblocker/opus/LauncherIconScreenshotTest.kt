package dev.sebastiano.clockblocker.opus

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the adaptive launcher icon layers the way launchers do (full bleed, circle and squircle masks, themed
 * monochrome) plus the splash-screen icon, so the artwork can be reviewed and guarded by goldens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestApplication::class, qualifiers = "w640dp-h400dp")
class LauncherIconScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun launcherIcon() {
        compose.setContent {
            Row(
                modifier = Modifier.background(Color(0xFFE8E6F0)).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Full 108dp canvas, then masks that crop to the 72dp visible area.
                AdaptiveIcon(shape = RoundedCornerShape(0.dp), size = 108, fullBleed = true)
                AdaptiveIcon(shape = CircleShape, size = 72)
                AdaptiveIcon(shape = RoundedCornerShape(24.dp), size = 72)
                ThemedIcon()
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/launcher_icon.png")
    }

    /** Legibility check: the circle-masked icon at 48, 72 and 192 px (this config is mdpi, so 1 dp == 1 px). */
    @Test
    fun launcherIconSizes() {
        compose.setContent {
            Row(
                modifier = Modifier.background(Color(0xFFE8E6F0)).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdaptiveIcon(shape = CircleShape, size = 48)
                AdaptiveIcon(shape = CircleShape, size = 72)
                AdaptiveIcon(shape = CircleShape, size = 192)
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/launcher_icon_sizes.png")
    }

    @Test
    fun splashIcon() {
        compose.setContent {
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                SplashIcon(background = Color(0xFFF7F5FF))
                SplashIcon(background = Color(0xFF0B1026))
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/launcher_splash.png")
    }
}

/**
 * The icon layers cropped by [shape] to a [size]dp mask. Launchers show the central 72dp of the 108dp layers, so
 * layers are drawn at 1.5× the mask; [fullBleed] shows the whole canvas instead (to check the safe zone).
 */
@Composable
private fun AdaptiveIcon(shape: Shape, size: Int, fullBleed: Boolean = false) {
    val layer = if (fullBleed) size.dp else (size * 1.5f).dp
    Box(Modifier.size(size.dp).clip(shape), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(layer))
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(layer))
    }
}

/** Android 13+ themed icon: monochrome layer tinted on a tonal disc. */
@Composable
private fun ThemedIcon() {
    Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF2B2A3D)), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_monochrome),
            contentDescription = null,
            modifier = Modifier.requiredSize(108.dp),
            colorFilter = ColorFilter.tint(Color(0xFFC9C5F5)),
        )
    }
}

/** Splash screen: 160dp icon-background disc showing the foreground (scaled like the platform: 240dp layer). */
@Composable
private fun SplashIcon(background: Color) {
    Box(Modifier.size(288.dp).background(background), contentAlignment = Alignment.Center) {
        Box(Modifier.size(160.dp).clip(CircleShape).background(Color(0xFF4F46E5)), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(240.dp))
        }
    }
}
