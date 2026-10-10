package dev.sebastiano.clockblocker.opus

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.clockblocker.opus.core.testing.captureRoboImageInvalidated
import dev.sebastiano.clockblocker.opus.icon.AdaptiveIcon
import dev.sebastiano.clockblocker.opus.icon.FullBleed
import dev.sebastiano.clockblocker.opus.icon.RoundedSquare
import dev.sebastiano.clockblocker.opus.icon.SplashIcon
import dev.sebastiano.clockblocker.opus.icon.Squircle
import dev.sebastiano.clockblocker.opus.icon.ThemedDark
import dev.sebastiano.clockblocker.opus.icon.ThemedIcon
import dev.sebastiano.clockblocker.opus.icon.ThemedLight
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the adaptive launcher icon layers the way launchers do (the full canvas with its safe zone, then circle,
 * squircle and rounded-square masks, then themed icons light and dark) plus the splash-screen icon, so the artwork
 * is guarded by goldens. The whole family, status bar and notification included, is on the contact sheet in
 * [dev.sebastiano.clockblocker.opus.icon.IconFamilyScreenshotTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], application = TestApplication::class, qualifiers = "w720dp-h400dp")
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
                // The full 108 dp canvas with the safe zone, then masks that crop to the 72 dp visible area.
                FullBleed(108.dp)
                AdaptiveIcon(72.dp, CircleShape)
                AdaptiveIcon(72.dp, Squircle)
                AdaptiveIcon(72.dp, RoundedSquare)
                ThemedIcon(72.dp, ThemedLight[0])
                ThemedIcon(72.dp, ThemedDark[0])
            }
        }
        compose.captureRoboImageInvalidated("src/test/screenshots/launcher_icon.png")
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
                AdaptiveIcon(48.dp)
                AdaptiveIcon(72.dp)
                AdaptiveIcon(192.dp)
            }
        }
        compose.captureRoboImageInvalidated("src/test/screenshots/launcher_icon_sizes.png")
    }

    /** The splash screen: a 160 dp icon-background disc showing the foreground, on the light and dark backgrounds. */
    @Test
    fun splashIcon() {
        compose.setContent {
            Row {
                listOf(Color(0xFFF7F5FF), Color(0xFF0B1026)).forEach { background ->
                    Box(Modifier.size(288.dp).background(background), contentAlignment = Alignment.Center) {
                        SplashIcon(160.dp)
                    }
                }
            }
        }
        compose.captureRoboImageInvalidated("src/test/screenshots/launcher_splash.png")
    }

    /**
     * The README's icon: the circle-masked icon at 192 px on a transparent background, drawn straight from the
     * vectors so it can't drift from the real icon.
     */
    @Test
    fun readmeIcon() {
        val context = RuntimeEnvironment.getApplication()
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.clipPath(Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) })
        // Launchers show the central 72 of the 108 dp layers: draw the layers at 1.5× the mask, centred.
        val inset = size / 4
        listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground).forEach { res ->
            context.getDrawable(res)!!.apply {
                setBounds(-inset, -inset, size + inset, size + inset)
                draw(canvas)
            }
        }
        bitmap.captureRoboImage("../docs/screenshots/launcher-icon.png")
    }
}
