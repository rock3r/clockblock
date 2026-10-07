package dev.sebastiano.clockblocker.opus.iconoptions

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.clockblocker.opus.TestApplication
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * Contact sheets for the #53 icon family options (exploration; not goldens of shipped art). Each option is shown as
 * adaptive layers with the safe zone, under three masks, on light/dark wallpapers at 48/64/96 dp, as Material You
 * themed icons, as a splash, and as the status-bar small icon at real size, enlarged and rasterised at 16/24 px,
 * plus the platform's own notification template with that small icon.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestApplication::class, qualifiers = "w900dp-h2000dp-xhdpi")
class IconOptionsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun skies() = sheet(IconOption.Skies)

    @Test
    fun moonClock() = sheet(IconOption.MoonClock)

    @Test
    fun horizon() = sheet(IconOption.Horizon)

    @Test
    @Config(qualifiers = "w1180dp-h1400dp-hdpi")
    fun comparison() {
        val shots = renderNotifications()
        compose.setContent { SheetTheme { ComparisonSheet(shots) } }
        compose.onNodeWithTag("sheet").captureRoboImage("src/test/screenshots/icon_options/icon_options_comparison.png")
    }

    private fun sheet(option: IconOption) {
        val shots = renderNotifications()
        compose.setContent { SheetTheme { OptionSheet(option, shots) } }
        compose.onNodeWithTag("sheet").captureRoboImage("src/test/screenshots/icon_options/icon_option_${option.key}.png")
    }

    /** The platform's own notification template (collapsed, expanded) with each option's small icon. */
    private fun renderNotifications(): NotificationShots {
        val light = IconOption.All.associate { it.key to notif(it, night = false) }
        RuntimeEnvironment.setQualifiers("+night")
        val dark = IconOption.All.associate { it.key to notif(it, night = true) }
        RuntimeEnvironment.setQualifiers("+notnight")
        return NotificationShots(light, dark)
    }

    private fun notif(option: IconOption, night: Boolean): List<Bitmap> {
        val application = RuntimeEnvironment.getApplication()
        application.applicationInfo.nonLocalizedLabel = "Clockblock"
        val context = ContextThemeWrapper(application, android.R.style.Theme_DeviceDefault_DayNight)
        val notification = Notification.Builder(context, "now")
            .setSmallIcon(option.notif)
            .setColor(0xFF4F46E5.toInt())
            .setContentTitle("Avoid light")
            .setContentText("until 18:00 · 02:00 Tokyo")
            .setSubText("Body clock 7 h behind")
            .setShowWhen(false)
            .setStyle(Notification.BigTextStyle().bigText("until 18:00 · 02:00 Tokyo\nSunglasses on, even if it feels silly."))
            .build()
        val template = Notification.Builder.recoverBuilder(context, notification)
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).roundToInt()
        return listOf(template.createContentView(), template.createBigContentView()).map { views ->
            val card = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(android.graphics.Color.parseColor(if (night) "#2B2930" else "#FFFFFF"))
                }
                addView(views.apply(context, this))
            }
            val holder = LinearLayout(context).apply { addView(card, LinearLayout.LayoutParams(-1, -2)) }
            holder.measure(
                View.MeasureSpec.makeMeasureSpec(dp(380), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(600), View.MeasureSpec.AT_MOST),
            )
            holder.layout(0, 0, holder.measuredWidth, holder.measuredHeight)
            Bitmap.createBitmap(holder.measuredWidth, holder.measuredHeight, Bitmap.Config.ARGB_8888).also {
                holder.draw(Canvas(it))
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = ClockblockTheme.textStyles.editorialHeadline, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OptionSheet(option: IconOption, shots: NotificationShots) {
    Column(
        Modifier.testTag("sheet").width(900.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(option.name, option.idea)

        // The family at a glance.
        Panel("The family: launcher layers and safe zone, three masks, the small icon, splash", Modifier.width(852.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
                Labelled("108 dp canvas · safe zone") { FullBleed(option, 120.dp) }
                Labelled("circle") { AdaptiveIcon(option, 80.dp, androidx.compose.foundation.shape.CircleShape) }
                Labelled("squircle") { AdaptiveIcon(option, 80.dp, Squircle) }
                Labelled("rounded square") { AdaptiveIcon(option, 80.dp, RoundedSquare) }
                Labelled("small icon · 24 dp grid") { SmallIconGrid(option, 100.dp) }
                Labelled("splash") { SplashPhone(option, dark = false, width = 70.dp) }
                Labelled("splash") { SplashPhone(option, dark = true, width = 70.dp) }
            }
        }

        // Home screens.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(false, true).forEach { dark ->
                Panel(
                    if (dark) "Dark wallpaper · 48 / 64 / 96 dp · themed" else "Light wallpaper · 48 / 64 / 96 dp · themed",
                    Modifier.width(420.dp),
                    brush = if (dark) DarkWallpaperBrush else LightWallpaperBrush,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
                        LauncherCell(option, 48.dp, dark)
                        LauncherCell(option, 64.dp, dark)
                        LauncherCell(option, 96.dp, dark)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
                        ThemedSeeds.forEach { t ->
                            ThemedIcon(option, 64.dp, if (dark) t.darkBg else t.lightBg, if (dark) t.darkFg else t.lightFg)
                        }
                        ThemedIcon(option, 64.dp, if (dark) ThemedSeeds[0].darkBg else ThemedSeeds[0].lightBg, if (dark) ThemedSeeds[0].darkFg else ThemedSeeds[0].lightFg, Squircle)
                    }
                }
            }
        }

        // Status bar.
        Panel("Status bar · real size, then 2.5× · rasterised: 24 px (worst case), 32 px (16 dp @ 2×), 48 px (24 dp @ 2×)", Modifier.width(852.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusBar(listOf(option.notif), dark = false)
                    StatusBar(listOf(option.notif), dark = true)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusBar(listOf(option.notif), dark = false, scale = 2.5f, width = 196.dp)
                    StatusBar(listOf(option.notif), dark = true, scale = 2.5f, width = 196.dp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                listOf(24, 32, 48).forEach { px ->
                    Labelled("$px px") { PixelProof(option, px, 72.dp, dark = false) }
                    Labelled("$px px") { PixelProof(option, px, 72.dp, dark = true) }
                }
                Labelled("themed layer") { ThemedIcon(option, 72.dp, Color(0xFF2B2A3D), Color(0xFFC9C5F5)) }
            }
            option.notifAlt?.let { alt ->
                Text(option.notifAltLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusBar(listOf(alt), dark = false)
                        StatusBar(listOf(alt), dark = true)
                    }
                    listOf(24, 32, 48).forEach { px ->
                        PixelProof(option, px, 56.dp, dark = false, res = alt)
                        PixelProof(option, px, 56.dp, dark = true, res = alt)
                    }
                }
            }
        }

        // Notification.
        Panel("Notification · the platform template with the new small icon", Modifier.width(852.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.background(Color(0xFFF4F0F8), androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { shots.light.getValue(option.key).forEach { NotificationShot(it, 380.dp) } }
                Column(
                    Modifier.background(Color.Black, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { shots.dark.getValue(option.key).forEach { NotificationShot(it, 380.dp) } }
            }
        }
    }
}

@Composable
fun ComparisonSheet(shots: NotificationShots) {
    Column(
        Modifier.testTag("sheet").width(1180.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header("Icon family · today vs. three options", "Same layout per row: home screens, themed, status bar (2×) and the notification header.")
        IconOption.All.forEach { option ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(option.name, Modifier.width(110.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Panel("", Modifier.width(196.dp), brush = LightWallpaperBrush) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                        LauncherCell(option, 64.dp, false)
                        LauncherCell(option, 48.dp, false, Squircle)
                    }
                }
                Panel("", Modifier.width(196.dp), brush = DarkWallpaperBrush) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                        LauncherCell(option, 64.dp, true)
                        LauncherCell(option, 48.dp, true, RoundedSquare)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemedIcon(option, 48.dp, ThemedSeeds[0].lightBg, ThemedSeeds[0].lightFg)
                    ThemedIcon(option, 48.dp, ThemedSeeds[0].darkBg, ThemedSeeds[0].darkFg)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBar(listOf(option.notif), dark = false, scale = 2f, width = 150.dp)
                    StatusBar(listOf(option.notif), dark = true, scale = 2f, width = 150.dp)
                }
                Column(
                    Modifier.background(Color(0xFFF4F0F8), androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(6.dp),
                ) { NotificationShot(shots.light.getValue(option.key)[0], 250.dp, cropFraction = 0.62f) }
            }
        }
    }
}
