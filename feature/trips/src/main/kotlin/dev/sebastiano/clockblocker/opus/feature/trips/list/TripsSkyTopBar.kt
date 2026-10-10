package dev.sebastiano.clockblocker.opus.feature.trips.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.component.BodyClockSky
import dev.sebastiano.clockblocker.opus.core.designsystem.component.SkyStatusBarIcons
import dev.sebastiano.clockblocker.opus.core.designsystem.component.celestialPosition
import dev.sebastiano.clockblocker.opus.core.designsystem.component.contentColor
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockThemeVariant
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.toHourFloat
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.sin

/**
 * The trips list's app bar over a sky (design.md §2.5): painted for the body clock of a trip under way, or for
 * local time at rest. The sun or moon rides the navigation row (never behind the title) and fades as the bar
 * collapses. The sky only repaints when [sky] changes (once a minute at most), and its meaning is spelled out in
 * the subtitle ("Body clock 04:10"), so nothing depends on the colours or on motion.
 *
 * [shortWindow] (landscape phones): a single pinned row, like the plan header, so the list keeps its height.
 * Without a [sky] (still loading) the bar is a plain surface bar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TripsSkyTopBar(
    title: String,
    subtitle: String?,
    sky: TripsSky?,
    shortWindow: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val time = sky?.time
    val ink = time?.let { ClockblockTheme.sky.gradientAt(it).contentColor() }
    var atTopStart by remember { mutableStateOf(false) }
    if (ink != null) SkyStatusBarIcons(ink, ownsStatusBar = atTopStart)
    val colors = if (ink != null) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = ink,
            titleContentColor = ink,
            actionIconContentColor = ink,
            subtitleContentColor = ink.copy(alpha = 0.86f),
        )
    } else {
        TopAppBarDefaults.topAppBarColors()
    }
    val subtitleSlot: (@Composable () -> Unit)? = subtitle?.let { line -> { Text(line) } }
    Box(
        modifier.onGloballyPositioned {
            val position = it.positionInWindow()
            atTopStart = position.x < 1f && position.y < 1f
        },
    ) {
        if (time != null) BodyClockSky(bodyTime = time, modifier = Modifier.matchParentSize(), showCelestial = false)
        if (shortWindow) {
            val titleSlot: @Composable () -> Unit = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (subtitle != null) {
                TopAppBar(
                    title = titleSlot,
                    // One line here: the body clock and the counts share it ("Body clock 10:03 · 1 upcoming").
                    subtitle = { Text(subtitle.lines().joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = actions,
                    colors = colors,
                    scrollBehavior = scrollBehavior,
                )
            } else {
                TopAppBar(title = titleSlot, actions = actions, colors = colors, scrollBehavior = scrollBehavior)
            }
        } else {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = subtitleSlot,
                actions = actions,
                colors = colors,
                scrollBehavior = scrollBehavior,
            )
            if (time != null) {
                NavRowCelestial(
                    time = time,
                    collapsedFraction = { scrollBehavior.state.collapsedFraction },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

/**
 * The sun or moon at [time]'s solar position, on a shallow arc across the navigation row (between the start edge
 * and the actions), plus a few stars at night. Decorative: the subtitle carries the time. Faded with the bar's
 * [collapsedFraction], read in the draw phase only.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NavRowCelestial(time: LocalTime, collapsedFraction: () -> Float, modifier: Modifier = Modifier) {
    val hour = time.toHourFloat()
    val position = celestialPosition(hour)
    val nightSafe = ClockblockTheme.variant == ClockblockThemeVariant.NightSafe
    val art = ClockblockTheme.artColors
    val sunColor = if (nightSafe) art.tertiary.copy(alpha = 0.7f) else ClockblockTheme.adviceColors[AdviceType.SeeBrightLight].color
    val moonColor = if (nightSafe) art.onInverse.copy(alpha = 0.6f) else Moonlight
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sunny = remember { MaterialShapes.Sunny.toPath() }
    Box(
        modifier
            .clearAndSetSemantics {}
            .drawBehind {
                val alpha = (1f - collapsedFraction() * 2f).coerceIn(0f, 1f)
                if (alpha <= 0f) return@drawBehind
                val top = topInset.toPx()
                val band = NavRowHeight.toPx()
                val t = ((position.x - 0.1f) / 0.8f).coerceIn(0f, 1f)
                val start = CelestialInsetStart.toPx()
                val end = (size.width - CelestialReserveEnd.toPx()).coerceAtLeast(start + 1f)
                val center = Offset(start + (end - start) * t, top + band * (0.72f - 0.44f * sin(PI.toFloat() * t)))
                val r = CelestialRadius.toPx()
                if (!position.isSun) drawStars(moonColor.copy(alpha = alpha * if (nightSafe) 0.35f else 0.7f), top, band)
                if (position.isSun) {
                    translate(center.x, center.y) {
                        rotate(t * 90f, pivot = Offset.Zero) {
                            scale(r * 2.2f, r * 2.2f, pivot = Offset.Zero) { drawPath(sunny, sunColor, alpha = alpha) }
                        }
                    }
                } else {
                    val moon = Path().apply {
                        addOval(Rect(center, r))
                        op(this, Path().apply { addOval(Rect(center + Offset(r * 0.45f, -r * 0.35f), r * 0.85f)) }, PathOperation.Difference)
                    }
                    drawPath(moon, moonColor, alpha = alpha)
                }
            },
    )
}

private fun DrawScope.drawStars(color: Color, top: Float, band: Float) {
    val r = 1.3.dp.toPx()
    NavRowStars.forEachIndexed { i, (x, y) ->
        drawCircle(color, if (i % 3 == 0) r * 1.5f else r, Offset(size.width * x, top + band * y))
    }
}

/** The polygon's outline normalised to a unit square centred on the origin, built from its cubics. */
private fun androidx.graphics.shapes.RoundedPolygon.toPath(): Path {
    val b = calculateBounds()
    val extent = maxOf(b[2] - b[0], b[3] - b[1]).coerceAtLeast(1e-6f)
    val cx = (b[0] + b[2]) / 2f
    val cy = (b[1] + b[3]) / 2f
    fun nx(v: Float) = (v - cx) / extent
    fun ny(v: Float) = (v - cy) / extent
    return Path().apply {
        cubics.forEachIndexed { i, c ->
            if (i == 0) moveTo(nx(c.anchor0X), ny(c.anchor0Y))
            cubicTo(nx(c.control0X), ny(c.control0Y), nx(c.control1X), ny(c.control1Y), nx(c.anchor1X), ny(c.anchor1Y))
        }
        close()
    }
}

private val Moonlight = Color(0xFFF4F1FF)
private val NavRowHeight: Dp = 64.dp
private val CelestialRadius: Dp = 12.dp
private val CelestialInsetStart: Dp = 28.dp
private val CelestialReserveEnd: Dp = 72.dp

private val NavRowStars = listOf(
    0.30f to 0.18f, 0.42f to 0.40f, 0.55f to 0.12f, 0.64f to 0.52f, 0.12f to 0.60f,
    0.78f to 0.22f, 0.88f to 0.58f, 0.50f to 0.70f, 0.22f to 0.30f, 0.70f to 0.80f,
)
