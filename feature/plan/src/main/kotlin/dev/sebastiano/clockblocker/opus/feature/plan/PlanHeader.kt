package dev.sebastiano.clockblocker.opus.feature.plan

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.clickable
import kotlin.math.sin
import kotlin.math.abs
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.drawPolygon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.component.BodyClockSky
import dev.sebastiano.clockblocker.opus.core.designsystem.component.contentColor
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.ShapeMorph
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockThemeVariant
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.toHourFloat
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Trip
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Title for a plan: the trip's name, or "Lisbon → Tokyo" when the trip is gone or unnamed. */
internal fun planTitle(plan: JetLagPlan, trip: Trip?): String =
    trip?.title?.takeIf { it.isNotBlank() }
        ?: (ZoneId.of(plan.originZoneId).cityName() + " \u2192 " + ZoneId.of(plan.destinationZoneId).cityName())

/**
 * The header (design.md §2.4 C): a large flexible top app bar laid transparently over the body-clock sky, so the
 * header literally shows what time it is inside you. Title = the trip, subtitle = "Day 2 · Adapting · body 8 h behind".
 * The sky follows [moment] (so it moves while the dial is scrubbed).
 *
 * [skyKey] is the day picked in the day strip (`null` = live). Picking another day jumps the body clock by hours,
 * so the sky cross-fades to it; anything else (scrubbing, the minute tick) repaints the same sky in place.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlanHeader(
    title: String,
    moment: PlanMoment,
    firstDay: java.time.LocalDate?,
    nightSafe: Boolean,
    canEdit: Boolean,
    easterEggs: Boolean,
    actions: PlanActions,
    onMoonTip: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    shortWindow: Boolean = false,
    firstLight: Boolean = false,
    skyKey: Int? = null,
    daylight: HeaderDaylight = HeaderDaylight.Default,
) {
    val live = HeaderFrame(moment.bodyTime, moment.instant.atZone(moment.zone).toLocalTime(), daylight)
    val fade = rememberSkyFade(skyKey, live)
    // Ink and the sun / moon follow the sky that is mostly showing (the old day's until a day-pick fade is halfway):
    // its body time, wall clock and daylight together, so nothing on top mixes two days.
    val foreground = fade.foreground(live)
    val gradient = ClockblockTheme.sky.gradientAt(foreground.bodyTime, foreground.daylight.sunriseHour, foreground.daylight.sunsetHour)
    val ink = gradient.contentColor()
    var atStartEdge by remember { mutableStateOf(false) }
    SkyStatusBarIcons(ink, ownsStatusBar = atStartEdge)
    Box(
        modifier
            .testTag(PlanTags.Header)
            .onGloballyPositioned { atStartEdge = it.positionInWindow().x < 1f && it.positionInWindow().y < 1f },
    ) {
        // The sky paints the gradient; the sun / moon ride the navigation row (HeaderCelestial) so they never sit
        // behind the title.
        HeaderSky(fade, live, Modifier.matchParentSize())
        val subtitle: @Composable () -> Unit = {
            PriorityLine(optional = stageLabel(moment, firstDay), essential = bodyShiftLabel(moment.bodyAheadHours))
        }
        val navigationIcon: @Composable () -> Unit = {
            val onBack = actions.onBack
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.testTag(PlanTags.Back)) {
                    Icon(PlanIcons.ArrowBack, contentDescription = stringResource(R.string.plan_navigate_up))
                }
            }
        }
        val barActions: @Composable RowScope.() -> Unit = {
            if (nightSafe) NightSafeChip()
            OverflowMenu(canEdit, actions)
        }
        val colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = ink,
            titleContentColor = ink,
            actionIconContentColor = ink,
            subtitleContentColor = ink.copy(alpha = 0.82f),
        )
        if (shortWindow) {
            // Short windows (landscape phones): the large sky header took a third of the height and left the
            // plan a sliver, so it is a single row there (pinned; nothing to collapse).
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = subtitle,
                navigationIcon = navigationIcon,
                actions = barActions,
                colors = colors,
            )
        } else {
            LargeFlexibleTopAppBar(
                title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                subtitle = subtitle,
                navigationIcon = navigationIcon,
                actions = barActions,
                colors = colors,
                scrollBehavior = scrollBehavior,
            )
        }
        // Above the app bar: its Surface would swallow the moon's taps otherwise.
        HeaderCelestial(
            bodyTime = foreground.bodyTime,
            localTime = foreground.localTime,
            daylight = foreground.daylight,
            ink = ink,
            eggEnabled = easterEggs,
            reserveEnd = if (nightSafe) 200.dp else 72.dp,
            collapsedFraction = { if (shortWindow) 1f else scrollBehavior.state.collapsedFraction },
            onTip = onMoonTip,
            firstLight = firstLight,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** What the header shows at one moment: the body's time, the wall clock's and the day's sunrise and sunset. */
private data class HeaderFrame(val bodyTime: LocalTime, val localTime: LocalTime, val daylight: HeaderDaylight)

/** What the header showed: [frame] for the day keyed by [key] (the picked day, `null` = live). */
private data class SkyFrame(val key: Int?, val frame: HeaderFrame)

/** One frozen sky under a fade: the body time it paints under [daylight], at [alpha]. */
private data class SkyLayer(val bodyTime: LocalTime, val alpha: Float, val daylight: HeaderDaylight)

/**
 * At most this many frozen skies under a fade. Skies hidden under an opaque one are dropped first, so this only bites
 * on a burst of picks a frame or two apart; then the bottom one is dropped and the next made opaque.
 */
private const val MaxSkyLayers = 6

/** A frozen sky at least this opaque hides everything under it. */
private const val OpaqueAlpha = 0.999f

/**
 * The moment (body time, or the whole frame) the header's foreground follows (ink of the title, icons and status bar,
 * and the sun / moon with their path) while the sky cross-fades from [from] to [to]: the old sky's until the new one
 * is half faded in ([progress] 0 → 1), so everything on top matches the sky that is mostly showing. With no fade
 * ([from] `null`) it is simply [to].
 */
internal fun <T : Any> headerInkTime(from: T?, to: T, progress: Float): T =
    if (from == null || progress >= 0.5f) to else from

/**
 * A day-pick cross-fade of the header sky: [layers] (frozen, bottom first, the bottom one opaque) under the live sky,
 * which fades in as [progress] → 1. [from] is what the foreground followed when the fade started.
 */
@Stable
private class SkyFade(val layers: List<SkyLayer>, private val from: HeaderFrame?) {
    val progress = Animatable(if (layers.isEmpty()) 1f else 0f)

    /** The frozen skies are still (partly) visible. Flips once per fade, so it is safe to read in composition. */
    val fading by derivedStateOf { layers.isNotEmpty() && progress.value < 1f }

    /** The new sky is the one mostly showing (see [headerInkTime]). Flips once per fade. */
    private val pastMidpoint by derivedStateOf { progress.value >= 0.5f }

    fun foreground(live: HeaderFrame): HeaderFrame = headerInkTime(from, live, if (pastMidpoint) 1f else 0f)

    /**
     * What this fade shows right now, with the live sky at [live], as frozen layers for the next fade to start over:
     * an interrupted fade keeps its blend instead of jumping to its target. Reads without observing (a one-off).
     */
    fun freeze(live: HeaderFrame): List<SkyLayer> = Snapshot.withoutReadObservation {
        if (!fading) {
            listOf(SkyLayer(live.bodyTime, 1f, live.daylight))
        } else {
            val stack = layers + SkyLayer(live.bodyTime, progress.value, live.daylight)
            // Anything under an (all but) opaque layer can't be seen: only that much of the stack is kept.
            val base = stack.indexOfLast { it.alpha >= OpaqueAlpha }.coerceAtLeast(0)
            stack.drop(base).takeLast(MaxSkyLayers).mapIndexed { i, layer -> if (i == 0) layer.copy(alpha = 1f) else layer }
        }
    }

    fun frozenForeground(live: HeaderFrame): HeaderFrame = Snapshot.withoutReadObservation { foreground(live) }
}

/**
 * A new [key] (a day picked in the strip) starts a fade, on `colour()`, from whatever the header showed until then
 * (the previous day, or a fade still running). The same key with a new [bodyTime] (scrubbing, the minute tick) is no
 * fade at all: the live sky repaints in place.
 */
@Composable
private fun rememberSkyFade(key: Int?, frame: HeaderFrame): SkyFade {
    val motion = ClockblockTheme.motion
    // The frame and fade the last composition drew. Plain (not state): they are only read when the key changes.
    val last = remember { arrayOfNulls<SkyFrame>(1) }
    val previous = remember { arrayOfNulls<SkyFade>(1) }
    val fade = remember(key) {
        val before = last[0]?.takeIf { it.key != key }
        val prev = previous[0]
        when {
            before == null -> SkyFade(emptyList(), from = null)
            prev == null -> SkyFade(listOf(SkyLayer(before.frame.bodyTime, 1f, before.frame.daylight)), from = before.frame)
            else -> SkyFade(prev.freeze(before.frame), from = prev.frozenForeground(before.frame))
        }
    }
    LaunchedEffect(fade) { if (fade.layers.isNotEmpty()) fade.progress.animateTo(1f, motion.colour()) }
    SideEffect {
        last[0] = SkyFrame(key, frame)
        previous[0] = fade
    }
    return fade
}

/**
 * The body-clock sky behind the header. During a [fade] the frozen skies stay put underneath while the live one fades
 * in over them (alphas read in the layer, so the fade never recomposes); the bottom one is opaque, so the header never
 * shows through.
 */
@Composable
private fun HeaderSky(fade: SkyFade, live: HeaderFrame, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (fade.fading) {
            fade.layers.forEach { layer ->
                BodyClockSky(
                    bodyTime = layer.bodyTime,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = layer.alpha }.testTag(PlanTags.HeaderSky),
                    sunriseHour = layer.daylight.sunriseHour,
                    sunsetHour = layer.daylight.sunsetHour,
                    showCelestial = false,
                )
            }
        }
        BodyClockSky(
            bodyTime = live.bodyTime,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.progress.value }.testTag(PlanTags.HeaderSky),
            sunriseHour = live.daylight.sunriseHour,
            sunsetHour = live.daylight.sunsetHour,
            showCelestial = false,
        )
    }
}

/** "Night-safe" label so the dimmed look reads as intentional, not broken. */
@Composable
private fun NightSafeChip() {
    val description = stringResource(R.string.plan_night_safe_description)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(PlanTags.NightSafeChip).semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PlanIcons.NightsStay, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.plan_night_safe_chip), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun OverflowMenu(canEdit: Boolean, actions: PlanActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag(PlanTags.Overflow)) {
            Icon(PlanIcons.MoreVert, contentDescription = stringResource(R.string.plan_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (canEdit) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plan_menu_edit)) },
                    onClick = { open = false; actions.onEditTrip() },
                    modifier = Modifier.testTag(PlanTags.MenuEdit),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plan_menu_return)) },
                    onClick = { open = false; actions.onCreateReturnTrip() },
                    modifier = Modifier.testTag(PlanTags.MenuReturn),
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plan_menu_export)) },
                leadingIcon = { Icon(PlanIcons.CalendarToday, contentDescription = null) },
                onClick = { open = false; actions.onExportCalendar() },
                modifier = Modifier.testTag(PlanTags.MenuExport),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plan_menu_share)) },
                onClick = { open = false; actions.onShareSummary() },
                modifier = Modifier.testTag(PlanTags.MenuShare),
            )
        }
    }
}

/** Taps on the moon before the egg hatches. */
internal const val MoonTaps = 7

/** Circle → Cookie12 → Clover8 → Ghostish → Heart → Cookie9 (then bitten). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val EggShapes by lazy {
    listOf(
        MaterialShapes.Circle,
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Clover8Leaf,
        MaterialShapes.Ghostish,
        MaterialShapes.Heart,
        MaterialShapes.Cookie9Sided,
    )
}

private val Moonlight = Color(0xFFF4F1FF)
private val NavRowHeight = 64.dp
private val CelestialRadius = 12.dp
private val CelestialInsetStart = 72.dp

/** The ghost ring sits this far outside the sun / moon, so in step it circles them. */
private val GhostGap = 4.dp
private const val PathAlpha = 0.45f
private const val JoinAlpha = 0.6f
private const val GhostAlpha = 0.85f

private val HeaderStars = listOf(
    0.30f to 0.10f, 0.42f to 0.22f, 0.55f to 0.08f, 0.64f to 0.30f, 0.12f to 0.52f,
    0.78f to 0.14f, 0.88f to 0.40f, 0.50f to 0.46f, 0.22f to 0.28f, 0.70f to 0.56f,
)

/** Line segments the sun path is drawn with: smooth at any header width. */
private const val ArcSteps = 48

/**
 * Where the sun / moon sit at [t] along their path ([ArcPoint.t]): a half sine across the navigation row, between the
 * up arrow and the actions.
 */
private fun Density.celestialCenter(width: Float, top: Float, t: Float, reserveEnd: Dp): Offset {
    val start = CelestialInsetStart.toPx()
    val end = (width - reserveEnd.toPx()).coerceAtLeast(start + 1f)
    val band = NavRowHeight.toPx()
    return Offset(start + (end - start) * t, top + band * (0.74f - 0.48f * sin(PI.toFloat() * t)))
}

/** The path from [from] to [to] (both [ArcPoint.t]) along [celestialCenter]'s half sine, into [out]. */
private fun Density.arcPath(width: Float, top: Float, from: Float, to: Float, reserveEnd: Dp, out: Path): Path {
    out.rewind()
    val steps = (ArcSteps * abs(to - from)).roundToInt().coerceAtLeast(2)
    for (i in 0..steps) {
        val p = celestialCenter(width, top, from + (to - from) * i / steps, reserveEnd)
        if (i == 0) out.moveTo(p.x, p.y) else out.lineTo(p.x, p.y)
    }
    return out
}

/**
 * The header's sun or moon at the **body's** solar time, riding the navigation row so it never hides the title,
 * plus a few stars at body night. It fades out as the header collapses (read in the draw phase).
 *
 * Sun path (issue #21): the faint dashed half sine the sun / moon ride, and a ghost ring where they would be at
 * [localTime], the wall clock ([HeaderDaylight.ghostT]: at the nearer end when the wall clock is on the other half of
 * the day). When the body is ½ h or more off the wall clock on the same half, a faint arc joins the two along the
 * path ([HeaderDaylight.joins]); in step, the ring circles the sun. All of it is laid
 * out for [daylight], the real day where the traveller is, and is decoration only: the subtitle says how far off the
 * body is, in words. Nothing here moves on its own; it repaints when the times do.
 *
 * Moon-phase easter egg: tap the moon [MoonTaps] times and it morphs through "phases" (circle, cookie, clover,
 * ghost, heart) into a cookie that gets a bite taken out of it, then [onTip] fires ("Midnight snack? Your gut
 * has a clock too."). Rare, earned delight: absent when ![eggEnabled] (reduce motion, or the plan says sleep)
 * and in the daytime, when there is no moon. Closing the gate also resets a hatched or half-tapped moon.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeaderCelestial(
    bodyTime: LocalTime,
    localTime: LocalTime,
    daylight: HeaderDaylight,
    ink: Color,
    eggEnabled: Boolean,
    reserveEnd: Dp,
    collapsedFraction: () -> Float,
    onTip: () -> Unit,
    modifier: Modifier = Modifier,
    firstLight: Boolean = false,
) {
    val hour = bodyTime.toHourFloat()
    val body = daylight.arcPoint(hour)
    val isSun = body.isSun
    val localHour = localTime.toHourFloat()
    val joins = daylight.joins(hour, localHour)
    val nightSafe = ClockblockTheme.variant == ClockblockThemeVariant.NightSafe
    val art = ClockblockTheme.artColors
    val sunColor = if (nightSafe) art.tertiary.copy(alpha = 0.7f) else ClockblockTheme.adviceColors[AdviceType.SeeBrightLight].color
    val moonColor = if (nightSafe) art.onInverse.copy(alpha = 0.6f) else Moonlight
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val motion = ClockblockTheme.motion
    val view = LocalView.current
    var taps by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val morphs = remember { EggShapes.zipWithNext { a, b -> ShapeMorph(a, b) } }
    val path = remember { Path() }
    val bite = remember { Path() }
    val bitten = remember { Path() }
    val trail = remember { Path() }
    val currentOnTip by rememberUpdatedState(onTip)
    val hatched = taps >= MoonTaps
    val expanded by remember { derivedStateOf { collapsedFraction() < 0.5f } }
    // "First light": the plan of a trip saved moments ago opens with its sun or moon rising into place over the
    // horizon, once (rare: a few times a month). Under reduce motion it is simply there (the static carrier;
    // the rise adds no meaning).
    val reduce = ClockblockTheme.reduceMotion
    var risen by rememberSaveable { mutableStateOf(!firstLight) }
    val rise = remember { Animatable(if (risen || reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!risen && !reduce) rise.animateTo(1f, motion.artEntrance())
        risen = true
    }
    // Phases: crescent waxes to full (1) → the shape chain (morphs) → the bite. One continuous progress.
    val totalSteps = morphs.size + 2
    LaunchedEffect(hatched) {
        if (!hatched) return@LaunchedEffect
        progress.animateTo(totalSteps.toFloat(), motion.eggChain(totalSteps))
        currentOnTip()
    }
    // The gate closing (Reduce motion turned on, or a sleep block starting) cancels the egg, hatched or half-tapped:
    // the moon goes back to its plain phase (un-hatching also cancels the morph above, so no tip), and once the gate
    // reopens the count starts over.
    LaunchedEffect(eggEnabled) {
        if (!eggEnabled && (taps > 0 || progress.value > 0f)) {
            taps = 0
            progress.snapTo(0f)
        }
    }
    val label = stringResource(R.string.plan_moon_target)
    Layout(
        content = {
            if (eggEnabled && !isSun && expanded) {
                Box(
                    Modifier
                        .testTag(PlanTags.Moon)
                        .semantics {
                            contentDescription = label
                            role = Role.Button
                        }
                        .clip(CircleShape)
                        .clickable {
                            if (taps < MoonTaps) {
                                taps++
                                // A light tick on the counting taps; the hatch itself speaks through the morph.
                                if (taps < MoonTaps) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        },
                )
            }
        },
        modifier = modifier.clipToBounds().drawBehind {
            val risen = rise.value
            val alpha = (1f - collapsedFraction() * 2f).coerceIn(0f, 1f) * risen.coerceIn(0f, 1f)
            if (alpha <= 0f) return@drawBehind
            val top = topInset.toPx()
            val r = CelestialRadius.toPx()
            val rest = celestialCenter(size.width, top, body.t, reserveEnd)
            // Rising: from just below the header's bottom edge (the horizon) up to its resting place.
            val c = rest.copy(y = rest.y + (1f - risen) * (size.height - rest.y + r * 1.2f))
            if (!isSun) {
                val star = 1.3.dp.toPx()
                HeaderStars.forEachIndexed { i, (x, y) ->
                    drawCircle(moonColor.copy(alpha = moonColor.alpha * 0.6f * alpha), if (i % 3 == 0) star * 1.4f else star, Offset(size.width * x, top + (size.height - top) * y))
                }
            }
            // The sun path, the join to the wall clock's spot on it, and the ghost ring there.
            val pathInk = ink.copy(alpha = ink.alpha * alpha * if (nightSafe) 0.6f else 1f)
            drawPath(
                arcPath(size.width, top, 0f, 1f, reserveEnd, trail),
                pathInk.copy(alpha = pathInk.alpha * PathAlpha),
                style = Stroke(1.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))),
            )
            // In step, or so close that the ring would still circle the sun's middle: drawn exactly around it.
            val arcWidth = (size.width - reserveEnd.toPx() - CelestialInsetStart.toPx()).coerceAtLeast(1f)
            val ghostT = daylight.ghostT(hour, localHour, snapT = (r + GhostGap.toPx()) / arcWidth)
            if (joins && ghostT != body.t) {
                drawPath(
                    arcPath(size.width, top, body.t, ghostT, reserveEnd, trail),
                    pathInk.copy(alpha = pathInk.alpha * JoinAlpha),
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            drawCircle(
                pathInk.copy(alpha = pathInk.alpha * GhostAlpha),
                r + GhostGap.toPx(),
                celestialCenter(size.width, top, ghostT, reserveEnd),
                style = Stroke(1.5.dp.toPx()),
            )
            val box = Size(r * 2.2f, r * 2.2f)
            translate(c.x - box.width / 2f, c.y - box.height / 2f) {
                when {
                    isSun -> drawPolygon(MaterialShapes.Sunny, sunColor.copy(alpha = sunColor.alpha * alpha), box, rotationDegrees = hour * 15f)
                    hatched -> {
                        val v = progress.value
                        val color = moonColor.copy(alpha = moonColor.alpha * alpha)
                        if (v < 1f) {
                            // Crescent → full moon: the shadow slides off and shrinks, and the disc grows to the
                            // chain's Circle (which fills the box) so the hand-off is seamless.
                            val centre = Offset(box.width / 2f, box.height / 2f)
                            path.rewind()
                            path.addOval(Rect(centre, r + (box.width / 2f - r) * v))
                            val wax = 1f - v
                            bite.rewind()
                            bite.addOval(Rect(centre + Offset(r * (0.45f + 0.9f * v), -r * 0.35f * wax), r * 0.85f * wax))
                            bitten.rewind()
                            bitten.op(path, bite, PathOperation.Difference)
                            drawPath(bitten, color)
                        } else {
                            val chain = v - 1f
                            val segment = min(floor(chain).toInt(), morphs.size - 1).coerceAtLeast(0)
                            val fraction = (chain - segment).coerceAtMost(1f)
                            val biteAmount = (chain - morphs.size).coerceIn(0f, 1f)
                            morphs[segment].toPath(fraction, box, out = path)
                            val shape = if (biteAmount > 0f) {
                                bite.rewind()
                                bite.addOval(Rect(Offset(box.width * 0.9f, box.height * 0.16f), box.width * 0.34f * biteAmount))
                                bitten.rewind()
                                bitten.op(path, bite, PathOperation.Difference)
                                bitten
                            } else {
                                path
                            }
                            drawPath(shape, color)
                        }
                    }
                    else -> {
                        val centre = Offset(box.width / 2f, box.height / 2f)
                        path.rewind()
                        path.addOval(Rect(centre, r))
                        bite.rewind()
                        bite.addOval(Rect(centre + Offset(r * 0.45f, -r * 0.35f), r * 0.85f))
                        bitten.rewind()
                        bitten.op(path, bite, PathOperation.Difference)
                        drawPath(bitten, moonColor.copy(alpha = moonColor.alpha * alpha))
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val target = 48.dp.roundToPx()
        val placeable = measurables.firstOrNull()?.measure(Constraints.fixed(target, target))
        layout(w, h) {
            if (placeable != null) {
                val c = celestialCenter(w.toFloat(), topInset.toPx(), body.t, reserveEnd)
                placeable.place((c.x - target / 2f).roundToInt(), (c.y - target / 2f).roundToInt())
            }
        }
    }
}
