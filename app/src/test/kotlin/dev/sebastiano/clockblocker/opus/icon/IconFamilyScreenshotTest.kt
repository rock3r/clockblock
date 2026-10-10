package dev.sebastiano.clockblocker.opus.icon

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.TestApplication
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.testing.captureRoboImageInvalidated
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The contact sheet of the icon family (#53): the launcher icon under three masks and on light and dark
 * wallpapers, themed icons light and dark, the splash, the small icon on its grid, the status bar at real size and
 * 3×, the small icon rasterised at its real pixel sizes, and the platform's notification template with it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], application = TestApplication::class, qualifiers = "w900dp-h1700dp-hdpi")
class IconFamilyScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun iconFamily() {
        val light = notifications(night = false)
        RuntimeEnvironment.setQualifiers("+night")
        val dark = notifications(night = true)
        RuntimeEnvironment.setQualifiers("+notnight")
        compose.setContent {
            ClockblockTheme(darkTheme = false, dynamicColor = false) { Sheet(light, dark) }
        }
        compose.captureRoboImageInvalidated("src/test/screenshots/icon_family.png")
    }

    /** The platform's own template (collapsed, expanded) with the small icon, on a shade card. */
    private fun notifications(night: Boolean): List<Bitmap> {
        val application = RuntimeEnvironment.getApplication()
        application.applicationInfo.nonLocalizedLabel = "Clockblock"
        val context = ContextThemeWrapper(application, android.R.style.Theme_DeviceDefault_DayNight)
        val notification = Notification.Builder(context, "now")
            .setSmallIcon(NotificationSmallIcon)
            .setColor(0xFF4F46E5.toInt())
            .setContentTitle("Avoid light")
            .setContentText("until 18:00 · 02:00 Tokyo")
            .setSubText("Body clock 7 h behind")
            .setShowWhen(false)
            .setStyle(Notification.BigTextStyle().bigText("until 18:00 · 02:00 Tokyo\nSunglasses on, even if it feels silly."))
            .build()
        val builder = Notification.Builder.recoverBuilder(context, notification)
        val density = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).roundToInt()
        return listOf(builder.createContentView(), builder.createBigContentView()).map { views ->
            val card = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = px(24).toFloat()
                    setColor(if (night) 0xFF2B2930.toInt() else 0xFFFFFFFF.toInt())
                }
                addView(views.apply(context, this))
            }
            card.measure(
                View.MeasureSpec.makeMeasureSpec(px(380), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(px(600), View.MeasureSpec.AT_MOST),
            )
            card.layout(0, 0, card.measuredWidth, card.measuredHeight)
            Bitmap.createBitmap(card.measuredWidth, card.measuredHeight, Bitmap.Config.ARGB_8888).also { card.draw(Canvas(it)) }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Sheet(light: List<Bitmap>, dark: List<Bitmap>) {
    Column(
        Modifier.width(900.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Horizon", style = ClockblockTheme.textStyles.editorialHeadline, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "A sun cut along its horizon into day and night, the halves slid out of line: the day you're in and the " +
                    "day your body expects.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Panel("Adaptive icon: 108 dp canvas with the 72 dp mask (dashed) and 66 dp safe zone, three masks, themed, splash") {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
                Labelled("canvas · safe zone") { FullBleed(128.dp) }
                Labelled("circle") { AdaptiveIcon(84.dp, CircleShape) }
                Labelled("squircle") { AdaptiveIcon(84.dp, Squircle) }
                Labelled("rounded square") { AdaptiveIcon(84.dp, RoundedSquare) }
                Labelled("themed") { ThemedIcon(84.dp, ThemedLight[0]) }
                Labelled("themed") { ThemedIcon(84.dp, ThemedDark[0]) }
                Labelled("splash") { SplashPhone(dark = false, width = 56.dp) }
                Labelled("splash") { SplashPhone(dark = true, width = 56.dp) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(false, true).forEach { night ->
                Panel(
                    if (night) "Dark wallpaper · 48 / 64 / 96 dp · themed" else "Light wallpaper · 48 / 64 / 96 dp · themed",
                    Modifier.width(420.dp),
                    background = if (night) DarkWallpaper else LightWallpaper,
                    dark = night,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                        LauncherCell(48.dp, night)
                        LauncherCell(64.dp, night, Squircle)
                        LauncherCell(96.dp, night)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                        val themed = if (night) ThemedDark else ThemedLight
                        themed.forEach { ThemedCell(56.dp, it, night) }
                        ThemedCell(56.dp, themed[0], night, Squircle)
                    }
                }
            }
        }

        Panel("Status bar · real size and 3× · the small icon on its 24 dp grid") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusBar(dark = false, icons = 2)
                    StatusBar(dark = true, icons = 2)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusBar(dark = false, scale = 3f, width = 132.dp)
                    StatusBar(dark = true, scale = 3f, width = 132.dp)
                }
                Labelled("24 dp grid") { SmallIconGrid(96.dp) }
            }
            Text(
                "Rasterised at its real pixel sizes, then enlarged without smoothing: 16 and 24 dp at 1× (mdpi) and 3× (xxhdpi)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                listOf(16 to "16 px", 24 to "24 px", 48 to "48 px", 72 to "72 px").forEach { (px, label) ->
                    Labelled(label) { PixelProof(px, 72.dp, dark = false) }
                    Labelled(label) { PixelProof(px, 72.dp, dark = true) }
                }
            }
        }

        Panel("Notification · the platform template with the small icon, and its header enlarged") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.background(Color(0xFFF4F0F8), RoundedCornerShape(20.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { light.forEach { BitmapShot(it, 380.dp) } }
                Column(
                    Modifier.background(Color.Black, RoundedCornerShape(20.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { dark.forEach { BitmapShot(it, 380.dp) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.background(Color(0xFFF4F0F8), RoundedCornerShape(20.dp)).padding(8.dp)) {
                    BitmapShot(light[1], 380.dp, cropFraction = 0.6f, cropHeightFraction = 0.48f)
                }
                Column(Modifier.background(Color.Black, RoundedCornerShape(20.dp)).padding(8.dp)) {
                    BitmapShot(dark[1], 380.dp, cropFraction = 0.6f, cropHeightFraction = 0.48f)
                }
            }
        }
    }
}
