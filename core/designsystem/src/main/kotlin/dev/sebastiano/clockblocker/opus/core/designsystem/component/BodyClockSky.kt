package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.sharedUnitPath
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ColorMath
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.DuskPalette
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockThemeVariant
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyGradient
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyPalette
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.toHourFloat
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.sin

/**
 * Where the sun (by day) or moon (by night) sits on a sky painted for [hour], as fractions of the box:
 * x runs 0.1 → 0.9 from rising to setting, y follows a half-sine arc from [horizon] up to [horizon] − [arc].
 * Exposed for tests and for widgets that paint the same sky.
 */
data class CelestialPosition(val x: Float, val y: Float, val isSun: Boolean)

fun celestialPosition(
    hour: Float,
    sunriseHour: Float = SkyPalette.DefaultSunrise,
    sunsetHour: Float = SkyPalette.DefaultSunset,
    horizon: Float = 0.92f,
    arc: Float = 0.62f,
): CelestialPosition {
    val h = ((hour % 24f) + 24f) % 24f
    // Measured round the clock from sunrise, so a sunset after midnight (past 24 h, or an hour before sunrise) works.
    val dayLength = (sunsetHour - sunriseHour).mod(24f).coerceIn(1f, 23f)
    val sinceSunrise = (h - sunriseHour).mod(24f)
    val isSun = sinceSunrise <= dayLength
    // Clamped: a day under 1 h or over 23 h is drawn as 1 h or 23 h, so the far end of the other half stays on the arc.
    val t = if (isSun) {
        sinceSunrise / dayLength
    } else {
        (((h - sunsetHour) % 24f + 24f) % 24f) / (24f - dayLength)
    }.coerceIn(0f, 1f)
    val x = 0.1f + 0.8f * t
    val y = horizon - arc * sin(PI.toFloat() * t)
    return CelestialPosition(x, y, isSun)
}

/** Readable content colour over a sky gradient (light ink on dark skies and vice versa). */
fun SkyGradient.contentColor(): Color = if (mid.luminance() < 0.36f) Color.White else DuskPalette.Dusk10

/**
 * The body-clock sky (design.md §2.4 C): a full-bleed two-stop sky painted for **body** time, with the sun or
 * moon at the body's solar position and a few stars at night. [content] is drawn on top with
 * [LocalContentColor] set to a colour that reads on the sky. In Night-safe the sky and the sun are dimmed.
 *
 * The sky is a data surface (it says "inside you it's 04:00"), so it never bounces; it repaints when
 * [bodyTime] changes, which callers drive at most once a minute (or per scrub frame).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BodyClockSky(
    bodyTime: LocalTime,
    modifier: Modifier = Modifier,
    sunriseHour: Float = SkyPalette.DefaultSunrise,
    sunsetHour: Float = SkyPalette.DefaultSunset,
    showCelestial: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val sky = ClockblockTheme.sky
    val nightSafe = ClockblockTheme.variant == ClockblockThemeVariant.NightSafe
    val art = ClockblockTheme.artColors
    val hour = bodyTime.toHourFloat()
    val gradient = sky.gradientAt(hour, sunriseHour, sunsetHour)
    val position = celestialPosition(hour, sunriseHour, sunsetHour)
    val sunColor = if (nightSafe) art.tertiary.copy(alpha = 0.7f) else DuskPalette.MarigoldSeed
    val moonColor = if (nightSafe) art.onInverse.copy(alpha = 0.6f) else Color(0xFFF4F1FF)
    val sunny = MaterialShapes.Sunny.sharedUnitPath()
    val pixelMode = ClockblockTheme.pixelMode
    val pixelSun = MaterialShapes.PixelCircle.sharedUnitPath()
    Box(
        modifier.drawBehind {
            if (pixelMode) {
                // 8-bit easter egg: flat bands sampled from the same gradient.
                val bands = gradient.bands(SkyPixelBands)
                val bandHeight = size.height / bands.size
                bands.forEachIndexed { i, color ->
                    drawRect(color, topLeft = Offset(0f, i * bandHeight), size = Size(size.width, bandHeight + 1f))
                }
            } else {
                drawRect(gradient.verticalBrush(0f, size.height))
            }
            if (!showCelestial) return@drawBehind
            val night = !position.isSun
            if (night) drawStars(moonColor.copy(alpha = if (nightSafe) 0.35f else 0.7f))
            val r = minOf(size.height * 0.16f, 28.dp.toPx())
            val c = Offset(size.width * position.x, size.height * position.y)
            if (position.isSun) {
                translate(c.x, c.y) {
                    if (pixelMode) {
                        scale(r * 2f, r * 2f, pivot = Offset.Zero) { drawPath(pixelSun, sunColor) }
                    } else {
                        rotate(position.x * 90f, pivot = Offset.Zero) {
                            scale(r * 2.2f, r * 2.2f, pivot = Offset.Zero) { drawPath(sunny, sunColor) }
                        }
                    }
                }
            } else {
                val moon = Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(c, r))
                    val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c + Offset(r * 0.45f, -r * 0.35f), r * 0.85f)) }
                    op(this, bite, PathOperation.Difference)
                }
                drawPath(moon, moonColor)
            }
        },
    ) {
        CompositionLocalProvider(LocalContentColor provides gradient.contentColor()) { content() }
    }
}

/** How many flat bands the 8-bit sky uses. */
internal const val SkyPixelBands = 6

/** [count] flat colours sampled from this gradient at the centre of each equal-height band, top to bottom. */
internal fun SkyGradient.bands(count: Int): List<Color> {
    require(count > 0) { "count must be positive" }
    return List(count) { i -> ColorMath.mix(top, bottom, (i + 0.5f) / count) }
}

private val StarField = listOf(
    0.08f to 0.18f, 0.22f to 0.42f, 0.31f to 0.12f, 0.47f to 0.30f, 0.58f to 0.09f,
    0.66f to 0.38f, 0.78f to 0.16f, 0.88f to 0.34f, 0.94f to 0.08f, 0.14f to 0.62f,
)

private fun DrawScope.drawStars(color: Color) {
    val r = 1.4.dp.toPx()
    StarField.forEachIndexed { i, (x, y) ->
        drawCircle(color, if (i % 3 == 0) r * 1.5f else r, Offset(size.width * x, size.height * y))
    }
}
