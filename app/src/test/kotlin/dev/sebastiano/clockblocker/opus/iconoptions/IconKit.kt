package dev.sebastiano.clockblocker.opus.iconoptions

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.sebastiano.clockblocker.opus.R
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import dev.sebastiano.clockblocker.opus.core.notifications.R as NotifR

/** One icon family: adaptive layers, themed layer and the status-bar small icon. */
data class IconOption(
    val key: String,
    val name: String,
    val idea: String,
    @param:DrawableRes val background: Int,
    @param:DrawableRes val foreground: Int,
    @param:DrawableRes val monochrome: Int,
    @param:DrawableRes val notif: Int,
    /** An alternative small icon to compare against [notif], if any. */
    @param:DrawableRes val notifAlt: Int? = null,
    val notifAltLabel: String = "",
) {
    companion object {
        val Today = IconOption(
            "today", "Today", "Marigold ring and hand with a lilac moon badge; the small icon changes with the advice.",
            R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground, R.drawable.ic_launcher_monochrome,
            NotifR.drawable.ic_notif_avoid_light,
        )
        val Skies = IconOption(
            "skies", "A · Two skies",
            "Two concentric rings, each split into a bright day arc and a dim night arc: the sky where you are " +
                "(marigold, day at the top) and your body's sky (blue), the split turned by the jet lag. The Two skies dial as a mark.",
            R.drawable.iconopt_skies_background, R.drawable.iconopt_skies_foreground,
            R.drawable.iconopt_skies_monochrome, R.drawable.iconopt_skies_notif,
            R.drawable.iconopt_skies_notif_solid, "solid-only alternative (day arcs)",
        )
        val MoonClock = IconOption(
            "moonclock", "B · Moon clock",
            "A clock ring with the sun riding on it (the time where you are) and a crescent moon inside: your body " +
                "is still in its night. Ring and sun also read as a C.",
            R.drawable.iconopt_moonclock_background, R.drawable.iconopt_moonclock_foreground,
            R.drawable.iconopt_moonclock_monochrome, R.drawable.iconopt_moonclock_notif,
        )
        val Horizon = IconOption(
            "horizon", "C · Horizon",
            "A sun cut along its horizon into day (above) and night (below), the halves slid out of line: the day " +
                "you are in and the day your body expects. Jet lag as one solid shape.",
            R.drawable.iconopt_horizon_background, R.drawable.iconopt_horizon_foreground,
            R.drawable.iconopt_horizon_monochrome, R.drawable.iconopt_horizon_notif,
        )
        val Options = listOf(Skies, MoonClock, Horizon)
        val All = listOf(Today) + Options
    }
}

/** Rendered platform notifications (collapsed + expanded) per option, light and dark. */
class NotificationShots(val light: Map<String, List<Bitmap>>, val dark: Map<String, List<Bitmap>>)

// ------------------------------------------------------------------------------------------ shapes and colours

val Squircle: Shape = GenericShape { size, _ ->
    val n = 4.2
    val a = size.width / 2f
    val b = size.height / 2f
    for (i in 0..180) {
        val t = i / 180.0 * 2 * Math.PI
        val c = cos(t)
        val s = sin(t)
        val x = a + a * (abs(c).pow(2 / n) * sign(c)).toFloat()
        val y = b + b * (abs(s).pow(2 / n) * sign(s)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
val RoundedSquare: Shape = RoundedCornerShape(23)

/** Material You themed-icon pairs (background, icon) for three wallpaper seeds. */
data class Themed(val name: String, val lightBg: Color, val lightFg: Color, val darkBg: Color, val darkFg: Color)

val ThemedSeeds = listOf(
    Themed("blue", Color(0xFFD8E2FF), Color(0xFF0B2A5E), Color(0xFF233A63), Color(0xFFC7D7FF)),
    Themed("green", Color(0xFFCDEBC6), Color(0xFF0E3317), Color(0xFF26432A), Color(0xFFB4DDAE)),
    Themed("rose", Color(0xFFFFD9E6), Color(0xFF4A0F2C), Color(0xFF55233D), Color(0xFFFFB8D3)),
)

private val LightWallpaper = Brush.linearGradient(
    listOf(Color(0xFFF1E6F6), Color(0xFFDDE6F8), Color(0xFFF8E9D8)),
    start = Offset(0f, 0f), end = Offset(900f, 700f),
)
private val DarkWallpaper = Brush.linearGradient(
    listOf(Color(0xFF2B1F4A), Color(0xFF111A35), Color(0xFF0A0E1C)),
    start = Offset(0f, 0f), end = Offset(900f, 700f),
)

// ------------------------------------------------------------------------------------------ icon renderings

/** The adaptive layers cropped by [shape]; launchers show the central 72 of 108 dp, so layers draw at 1.5×. */
@Composable
fun AdaptiveIcon(option: IconOption, size: Dp, shape: Shape = CircleShape) {
    Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        Image(painterResource(option.background), null, Modifier.requiredSize(size * 1.5f))
        Image(painterResource(option.foreground), null, Modifier.requiredSize(size * 1.5f))
    }
}

@Composable
fun ThemedIcon(option: IconOption, size: Dp, bg: Color, fg: Color, shape: Shape = CircleShape) {
    Box(Modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
        Image(painterResource(option.monochrome), null, Modifier.requiredSize(size * 1.5f), colorFilter = ColorFilter.tint(fg))
    }
}

/** The whole 108 dp canvas with the 72 dp mask and the 66 dp safe zone drawn over it. */
@Composable
fun FullBleed(option: IconOption, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(6.dp))) {
        Image(painterResource(option.background), null, Modifier.size(size))
        Image(painterResource(option.foreground), null, Modifier.size(size))
        Canvas(Modifier.size(size)) {
            val u = this.size.width / 108f
            val dash = PathEffect.dashPathEffect(floatArrayOf(4f * u, 3f * u))
            drawCircle(Color.White.copy(alpha = 0.55f), 36f * u, style = Stroke(0.7f * u, pathEffect = dash))
            drawCircle(Color(0xFF7CFFB2).copy(alpha = 0.9f), 33f * u, style = Stroke(0.7f * u))
        }
    }
}

/** The small icon on its 24 dp grid (2 dp padding keyline), enlarged. */
@Composable
fun SmallIconGrid(option: IconOption, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1B27)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val u = this.size.width / 24f
            val grid = Color.White.copy(alpha = 0.07f)
            for (i in 1 until 24) {
                drawLine(grid, Offset(i * u, 0f), Offset(i * u, this.size.height), 1f)
                drawLine(grid, Offset(0f, i * u), Offset(this.size.width, i * u), 1f)
            }
            drawRect(Color(0xFF7CFFB2).copy(alpha = 0.5f), Offset(2 * u, 2 * u), Size(20 * u, 20 * u), style = Stroke(1.2f))
        }
        Image(painterResource(option.notif), null, Modifier.size(size), colorFilter = ColorFilter.tint(Color.White))
    }
}

/** The vector rasterised at exactly [px] pixels, then enlarged without smoothing: the worst-case legibility check. */
@Composable
fun PixelProof(option: IconOption, px: Int, shown: Dp, dark: Boolean, @DrawableRes res: Int = option.notif) {
    val context = LocalContext.current
    val ink = if (dark) Color.White else Color(0xFF1D1B20)
    val bitmap = remember(res, px, dark) {
        val d = ContextCompat.getDrawable(context, res)!!.mutate()
        d.setTint(ink.toArgb())
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, px, px)
        d.draw(android.graphics.Canvas(b))
        b.asImageBitmap()
    }
    Box(
        Modifier.size(shown).clip(RoundedCornerShape(8.dp)).background(if (dark) Color(0xFF101014) else Color(0xFFF4F1F8)),
        contentAlignment = Alignment.Center,
    ) {
        Image(bitmap, null, Modifier.size(shown * 0.8f), filterQuality = FilterQuality.None)
    }
}

/** A launcher splash screen: the adaptive icon in its circle on the app's splash background. */
@Composable
fun SplashPhone(option: IconOption, dark: Boolean, width: Dp = 92.dp) {
    val height = width * 2f
    Box(
        Modifier.size(width, height).clip(RoundedCornerShape(16.dp))
            .background(if (dark) Color(0xFF0B1026) else Color(0xFFF7F5FF))
            .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AdaptiveIcon(option, width * 0.56f)
    }
}

// ------------------------------------------------------------------------------------------ system chrome

/** A status bar row: time, the notification icons, then signal, wifi and battery, all in the bar's ink. */
@Composable
fun StatusBar(notifs: List<Int>, dark: Boolean, scale: Float = 1f, width: Dp = 300.dp) {
    val ink = if (dark) Color.White else Color(0xFF1D1B20)
    val bg = if (dark) Color(0xFF0E0E13) else Color(0xFFF2EEF7)
    val icon = 17.dp * scale
    Row(
        Modifier.width(width * scale).height(28.dp * scale).clip(RoundedCornerShape(6.dp * scale)).background(bg)
            .padding(horizontal = 12.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("15:20", color = ink, fontSize = (14f * scale).sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp * scale))
        notifs.forEach { res ->
            Image(painterResource(res), null, Modifier.size(icon), colorFilter = ColorFilter.tint(ink))
            Spacer(Modifier.width(5.dp * scale))
        }
        Spacer(Modifier.weight(1f))
        SystemIcons(ink, scale)
    }
}

@Composable
private fun SystemIcons(ink: Color, scale: Float) {
    Canvas(Modifier.size(62.dp * scale, 17.dp * scale)) {
        val u = size.height / 17f
        // Signal: a right triangle.
        val sig = Path().apply {
            moveTo(1f * u, 15f * u); lineTo(14f * u, 15f * u); lineTo(14f * u, 2f * u); close()
        }
        drawPath(sig, ink)
        // Wifi: a filled fan.
        val cx = 26.5f * u
        val wifi = Path().apply {
            moveTo(cx, 15f * u)
            arcTo(androidx.compose.ui.geometry.Rect(cx - 9f * u, 1.5f * u, cx + 9f * u, 19.5f * u), 225f, 90f, false)
            close()
        }
        drawPath(wifi, ink)
        // Battery: a horizontal pill with a nub, mostly full.
        val left = 39f * u
        drawRoundRect(ink, Offset(left, 4f * u), Size(19f * u, 10f * u), CornerRadius(3f * u), style = Stroke(1.4f * u))
        drawRoundRect(ink, Offset(left + 2f * u, 6f * u), Size(12.5f * u, 6f * u), CornerRadius(1.5f * u))
        drawRoundRect(ink, Offset(left + 19.6f * u, 7f * u), Size(1.8f * u, 4f * u), CornerRadius(0.9f * u))
    }
}

// ------------------------------------------------------------------------------------------ sheet building blocks

@Composable
fun SheetTheme(content: @Composable () -> Unit) = ClockblockTheme(darkTheme = false, dynamicColor = false, content = content)

@Composable
fun Caption(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
fun Panel(title: String, modifier: Modifier = Modifier, dark: Boolean = false, brush: Brush? = null, content: @Composable () -> Unit) {
    val bgMod = when {
        brush != null -> Modifier.background(brush, RoundedCornerShape(24.dp))
        dark -> Modifier.background(Color(0xFF15141C), RoundedCornerShape(24.dp))
        else -> Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
    }
    Column(modifier.clip(RoundedCornerShape(24.dp)).then(bgMod).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (title.isNotEmpty()) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (dark || brush == DarkWallpaperBrush) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
        content()
    }
}

val LightWallpaperBrush: Brush get() = LightWallpaper
val DarkWallpaperBrush: Brush get() = DarkWallpaper

@Composable
fun Labelled(label: String, dark: Boolean = false, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        content()
        Caption(label, if (dark) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Launcher cell: icon plus app label, as on a home screen. */
@Composable
fun LauncherCell(option: IconOption, size: Dp, dark: Boolean, shape: Shape = CircleShape) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        AdaptiveIcon(option, size, shape)
        Text(
            "Clockblock",
            fontSize = if (size < 60.dp) 11.sp else 12.sp,
            color = if (dark) Color.White else Color(0xFF1D1B20),
        )
    }
}

/** A rendered notification at [width]; [cropFraction] keeps only its left part (the header), enlarged. */
@Composable
fun NotificationShot(bitmap: Bitmap, width: Dp, cropFraction: Float = 1f) {
    val shown = remember(bitmap, cropFraction) {
        if (cropFraction >= 1f) bitmap else Bitmap.createBitmap(bitmap, 0, 0, (bitmap.width * cropFraction).toInt(), bitmap.height)
    }
    val ratio = shown.height.toFloat() / shown.width
    Image(shown.asImageBitmap(), null, Modifier.size(width, width * ratio))
}
