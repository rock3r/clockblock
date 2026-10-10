package dev.sebastiano.clockblocker.opus.icon

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import dev.sebastiano.clockblocker.opus.core.notifications.R as NotificationsR

/*
 * Building blocks for reviewing the icon family (#53) the way the system shows it: adaptive layers under launcher
 * masks, themed icons, the status bar and the small icon rasterised at real pixel sizes.
 */

@DrawableRes val LauncherBackground = R.drawable.ic_launcher_background
@DrawableRes val LauncherForeground = R.drawable.ic_launcher_foreground
@DrawableRes val LauncherMonochrome = R.drawable.ic_launcher_monochrome
@DrawableRes val NotificationSmallIcon = NotificationsR.drawable.ic_notif_app

/** A superellipse, like the squircle masks of several launchers. */
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

/** The rounded square some launchers use (corner about a quarter of the side). */
val RoundedSquare: Shape = RoundedCornerShape(23)

/** A Material You themed-icon pair: the tonal disc and the tint of the monochrome layer. */
data class ThemedColors(val background: Color, val icon: Color)

/** Themed-icon colours for a few wallpaper seeds, light and dark. */
val ThemedLight = listOf(
    ThemedColors(Color(0xFFD8E2FF), Color(0xFF0B2A5E)),
    ThemedColors(Color(0xFFCDEBC6), Color(0xFF0E3317)),
    ThemedColors(Color(0xFFFFD9E6), Color(0xFF4A0F2C)),
)
val ThemedDark = listOf(
    ThemedColors(Color(0xFF233A63), Color(0xFFC7D7FF)),
    ThemedColors(Color(0xFF26432A), Color(0xFFB4DDAE)),
    ThemedColors(Color(0xFF55233D), Color(0xFFFFB8D3)),
)

val LightWallpaper = Brush.linearGradient(
    listOf(Color(0xFFF1E6F6), Color(0xFFDDE6F8), Color(0xFFF8E9D8)),
    start = Offset(0f, 0f),
    end = Offset(900f, 700f),
)
val DarkWallpaper = Brush.linearGradient(
    listOf(Color(0xFF2B1F4A), Color(0xFF111A35), Color(0xFF0A0E1C)),
    start = Offset(0f, 0f),
    end = Offset(900f, 700f),
)

/** The adaptive layers cropped by [shape]: launchers show the central 72 of the 108 dp layers, so they draw at 1.5×. */
@Composable
fun AdaptiveIcon(size: Dp, shape: Shape = CircleShape) {
    Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        Image(painterResource(LauncherBackground), null, Modifier.requiredSize(size * 1.5f))
        Image(painterResource(LauncherForeground), null, Modifier.requiredSize(size * 1.5f))
    }
}

/** Android 13+ themed icon: the monochrome layer tinted on a tonal shape. */
@Composable
fun ThemedIcon(size: Dp, colors: ThemedColors, shape: Shape = CircleShape) {
    Box(Modifier.size(size).clip(shape).background(colors.background), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(LauncherMonochrome),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f),
            colorFilter = ColorFilter.tint(colors.icon),
        )
    }
}

/** The whole 108 dp canvas with the 72 dp visible area (dashed) and the 66 dp safe zone (green) drawn over it. */
@Composable
fun FullBleed(size: Dp, guides: Boolean = true) {
    Box(Modifier.size(size)) {
        Image(painterResource(LauncherBackground), null, Modifier.size(size))
        Image(painterResource(LauncherForeground), null, Modifier.size(size))
        if (guides) {
            Canvas(Modifier.size(size)) {
                val u = this.size.width / 108f
                val dash = PathEffect.dashPathEffect(floatArrayOf(4f * u, 3f * u))
                drawCircle(Color.White.copy(alpha = 0.6f), 36f * u, style = Stroke(0.6f * u, pathEffect = dash))
                drawCircle(Color(0xFF7CFFB2), 33f * u, style = Stroke(0.6f * u))
            }
        }
    }
}

/** The small icon on its 24 dp grid with the 20 dp live area, enlarged. */
@Composable
fun SmallIconGrid(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1B27)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val u = this.size.width / 24f
            val grid = Color.White.copy(alpha = 0.08f)
            for (i in 1 until 24) {
                drawLine(grid, Offset(i * u, 0f), Offset(i * u, this.size.height), 1f)
                drawLine(grid, Offset(0f, i * u), Offset(this.size.width, i * u), 1f)
            }
            drawRect(Color(0xFF7CFFB2).copy(alpha = 0.6f), Offset(2 * u, 2 * u), Size(20 * u, 20 * u), style = Stroke(1.5f))
        }
        Image(painterResource(NotificationSmallIcon), null, Modifier.size(size), colorFilter = ColorFilter.tint(Color.White))
    }
}

/** The small icon rasterised at exactly [px] pixels, then enlarged without smoothing: what a screen really gets. */
@Composable
fun PixelProof(px: Int, shown: Dp, dark: Boolean) {
    val context = LocalContext.current
    val ink = if (dark) Color.White else Color(0xFF1D1B20)
    val bitmap = remember(px, dark) {
        val drawable = ContextCompat.getDrawable(context, NotificationSmallIcon)!!.mutate()
        drawable.setTint(ink.toArgb())
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(android.graphics.Canvas(b))
        b.asImageBitmap()
    }
    Box(
        Modifier.size(shown).clip(RoundedCornerShape(8.dp))
            .background(if (dark) Color(0xFF101014) else Color(0xFFF4F1F8)),
        contentAlignment = Alignment.Center,
    ) {
        Image(bitmap, null, Modifier.size(shown * 0.8f), filterQuality = FilterQuality.None)
    }
}

/** The splash screen: the foreground on the indigo icon-background disc, as the theme sets it up. */
@Composable
fun SplashPhone(dark: Boolean, width: Dp) {
    Box(
        Modifier.size(width, width * 2f).clip(RoundedCornerShape(14.dp))
            .background(if (dark) Color(0xFF0B1026) else Color(0xFFF7F5FF)),
        contentAlignment = Alignment.Center,
    ) {
        SplashIcon(width * 0.5f)
    }
}

/** The platform splash icon: a [disc] wide icon-background circle showing the foreground at 1.5× (as launchers do). */
@Composable
fun SplashIcon(disc: Dp) {
    Box(Modifier.size(disc).clip(CircleShape).background(SplashIconBackground), contentAlignment = Alignment.Center) {
        Image(painterResource(LauncherForeground), null, Modifier.requiredSize(disc * 1.5f))
    }
}

/** `windowSplashScreenIconBackgroundColor` in the app theme (the background layer's mid-tone). */
val SplashIconBackground = Color(0xFF453EC7)

/** A status bar: the time, the notification icons, then signal, wifi and battery, all in the bar's ink. */
@Composable
fun StatusBar(dark: Boolean, scale: Float = 1f, width: Dp = 300.dp, icons: Int = 1) {
    val ink = if (dark) Color.White else Color(0xFF1D1B20)
    val bg = if (dark) Color(0xFF0E0E13) else Color(0xFFF2EEF7)
    Row(
        Modifier.width(width * scale).height(28.dp * scale).clip(RoundedCornerShape(6.dp * scale)).background(bg)
            .padding(horizontal = 12.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("15:20", color = ink, fontSize = (14f * scale).sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp * scale))
        repeat(icons) {
            // AOSP draws notification icons at about 17 dp in an 18 dp slot.
            Image(painterResource(NotificationSmallIcon), null, Modifier.size(17.dp * scale), colorFilter = ColorFilter.tint(ink))
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
        val signal = Path().apply {
            moveTo(1f * u, 15f * u)
            lineTo(14f * u, 15f * u)
            lineTo(14f * u, 2f * u)
            close()
        }
        drawPath(signal, ink)
        val cx = 26.5f * u
        val wifi = Path().apply {
            moveTo(cx, 15f * u)
            arcTo(Rect(cx - 9f * u, 1.5f * u, cx + 9f * u, 19.5f * u), 225f, 90f, false)
            close()
        }
        drawPath(wifi, ink)
        val left = 39f * u
        drawRoundRect(ink, Offset(left, 4f * u), Size(19f * u, 10f * u), CornerRadius(3f * u), style = Stroke(1.4f * u))
        drawRoundRect(ink, Offset(left + 2f * u, 6f * u), Size(12.5f * u, 6f * u), CornerRadius(1.5f * u))
        drawRoundRect(ink, Offset(left + 19.6f * u, 7f * u), Size(1.8f * u, 4f * u), CornerRadius(0.9f * u))
    }
}

@Composable
fun Caption(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    background: Brush? = null,
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val fill = if (background != null) Modifier.background(background, shape) else Modifier.background(MaterialTheme.colorScheme.surface, shape)
    Column(modifier.clip(shape).then(fill).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = if (dark) Color.White else MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
fun Labelled(label: String, dark: Boolean = false, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        content()
        Caption(label, if (dark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A home-screen cell: the icon and the app label. */
@Composable
fun LauncherCell(size: Dp, dark: Boolean, shape: Shape = CircleShape) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        AdaptiveIcon(size, shape)
        Text("Clockblock", fontSize = if (size < 60.dp) 11.sp else 12.sp, color = if (dark) Color.White else Color(0xFF1D1B20))
    }
}

/** A themed home-screen cell. */
@Composable
fun ThemedCell(size: Dp, colors: ThemedColors, dark: Boolean, shape: Shape = CircleShape) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        ThemedIcon(size, colors, shape)
        Text("Clockblock", fontSize = 11.sp, color = if (dark) Color.White else Color(0xFF1D1B20))
    }
}

/** A rendered view bitmap shown [width] wide; [cropFraction] keeps only its start (the header), enlarged. */
@Composable
fun BitmapShot(bitmap: Bitmap, width: Dp, cropFraction: Float = 1f, cropHeightFraction: Float = 1f) {
    val shown = remember(bitmap, cropFraction, cropHeightFraction) {
        if (cropFraction >= 1f && cropHeightFraction >= 1f) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, (bitmap.width * cropFraction).toInt(), (bitmap.height * cropHeightFraction).toInt())
        }
    }
    val ratio = shown.height.toFloat() / shown.width
    Image(shown.asImageBitmap(), null, Modifier.size(width, width * ratio), filterQuality = FilterQuality.None)
}
