package dev.sebastiano.clockblocker.opus.feature.plan

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.sp
import dev.sebastiano.clockblocker.opus.core.designsystem.component.IataCode
import dev.sebastiano.clockblocker.opus.core.designsystem.component.SkyStars
import dev.sebastiano.clockblocker.opus.core.designsystem.component.drawSkyStars
import dev.sebastiano.clockblocker.opus.core.designsystem.component.drawSunGlow
import dev.sebastiano.clockblocker.opus.core.designsystem.component.spellOut
import dev.sebastiano.clockblocker.opus.core.designsystem.component.starVisibility
import dev.sebastiano.clockblocker.opus.core.designsystem.component.sunGlowStrength
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.DotMatrixStyle
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
import androidx.compose.ui.unit.LayoutDirection
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
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.RowScope
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
import dev.sebastiano.clockblocker.opus.core.designsystem.component.SkyStatusBarIcons
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

/** The route's airport codes for the header: where it leaves from and where it ends. */
@Immutable
internal data class HeaderRoute(val origin: String, val destination: String)

/** The header's next action: [headline] ("Next: Avoid caffeine in 1 h 20 min") and [detail] ("at 14:00 · 22:00 London"). */
@Immutable
internal data class HeaderNext(val headline: String, val detail: String)

/**
 * The header (design.md §2.4 C): an immersive sky painted for **body** time (the real sunrise and sunset where the
 * traveller is), edge to edge under the status bar, that melts into the page at its foot. Over it: the navigation row
 * with the sun or moon on its path, then the trip as dot-matrix airport codes and a city line, where the plan is
 * ("Day 2 · Adapting", "2 days before your flight") and the next action. The sky follows [moment] (so it moves while
 * the dial is scrubbed).
 *
 * It collapses with [scrollBehavior] to the navigation row with the trip's name; the expanded block fades out as it
 * goes (read in the layer phase). Short windows keep it collapsed.
 *
 * [showBodyShift]: the subtitle also says how far off the body is ("body 2½ h ahead"), for when the dial doesn't
 * (compact phones draw it without its offset pill); otherwise the dial's pill is the one place that says it.
 *
 * [skyKey] is the day picked in the day strip (`null` = live). Picking another day jumps the body clock by hours,
 * so the sky cross-fades to it; anything else (scrubbing, the minute tick) repaints the same sky in place.
 *
 * [ambient]: the sky may come alive now and then ([HeaderAmbient]); off under Reduce motion and in sleep windows.
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
    route: HeaderRoute? = null,
    next: HeaderNext? = null,
    showBodyShift: Boolean = false,
    ambient: Boolean = false,
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
    val collapsedFraction: () -> Float = { if (shortWindow) 1f else scrollBehavior.state.collapsedFraction }
    val celestialFrame by rememberUpdatedState(foreground)
    val frame = remember { { celestialFrame } }
    val headerAmbient = rememberHeaderAmbient(ambient && !nightSafe)
    val stage = stageLabel(moment, firstDay)
    val subtitle = if (showBodyShift) stage + " \u00B7 " + bodyShiftLabel(moment.bodyAheadHours) else stage
    Box(
        modifier
            .testTag(PlanTags.Header)
            .onGloballyPositioned { atStartEdge = it.positionInWindow().x < 1f && it.positionInWindow().y < 1f }
            .clipToBounds(),
    ) {
        HeaderSky(fade, live, Modifier.matchParentSize())
        // Stars and the ambient plane, behind everything else on the sky.
        HeaderAtmosphere(frame, headerAmbient, ink, collapsedFraction, Modifier.matchParentSize())
        CompositionLocalProvider(LocalContentColor provides ink) {
            HeaderLayout(
                scrollBehavior = scrollBehavior,
                shortWindow = shortWindow,
                navRow = {
                    HeaderNavRow(
                        title = title,
                        subtitle = subtitle.takeIf { shortWindow },
                        next = next.takeIf { shortWindow },
                        collapsedFraction = collapsedFraction,
                        nightSafe = nightSafe,
                        canEdit = canEdit,
                        actions = actions,
                    )
                },
                expanded = { HeaderExpanded(title, route, subtitle, next, ink, collapsedFraction) },
                fade = { HeaderFoot(collapsedFraction) },
            )
        }
        // Above the rest: the moon takes taps. The frame changes per scrub frame, so it goes in as a provider read in
        // layout and draw.
        HeaderCelestial(
            frame = frame,
            ink = ink,
            eggEnabled = easterEggs,
            reserveEnd = if (nightSafe) 200.dp else 72.dp,
            collapsedFraction = collapsedFraction,
            onTip = onMoonTip,
            firstLight = firstLight,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * The header's three parts: the [navRow] (status bar and navigation row, always there), the [expanded] block under it
 * and the [fade] at its foot. Expanded, it is all three; collapsing (the scroll behaviour's height offset, read in
 * layout, so scrolling never recomposes) it shrinks to the navigation row, clipping the block from the bottom.
 * The expanded height comes from the content (it grows with the font size), and is what the scroll behaviour may
 * take away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeaderLayout(
    scrollBehavior: TopAppBarScrollBehavior,
    shortWindow: Boolean,
    navRow: @Composable () -> Unit,
    expanded: @Composable () -> Unit,
    fade: @Composable () -> Unit,
) {
    val state = scrollBehavior.state
    Layout(contents = listOf(navRow, if (shortWindow) ({}) else expanded, if (shortWindow) ({}) else fade)) { (navs, blocks, fades), constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val nav = navs.first().measure(loose)
        val block = blocks.firstOrNull()?.measure(loose)
        val foot = fades.firstOrNull()?.measure(Constraints.fixedWidth(width))
        val collapsedHeight = nav.height
        val expandedHeight = collapsedHeight + (block?.height ?: 0)
        val limit = (collapsedHeight - expandedHeight).toFloat()
        if (!shortWindow && state.heightOffsetLimit != limit) state.heightOffsetLimit = limit
        val height = if (shortWindow) collapsedHeight else (expandedHeight + state.heightOffset).roundToInt().coerceIn(collapsedHeight, expandedHeight)
        layout(width, height) {
            block?.placeRelative(0, collapsedHeight)
            foot?.placeRelative(0, height - foot.height)
            nav.placeRelative(0, 0)
        }
    }
}

/**
 * The status bar's room and the navigation row: back, the trip's name (fading in as the header collapses; always
 * there on short windows, with the [subtitle] and [next] action under it), the Night-safe chip and the menu.
 */
@Composable
private fun HeaderNavRow(
    title: String,
    subtitle: String?,
    next: HeaderNext?,
    collapsedFraction: () -> Float,
    nightSafe: Boolean,
    canEdit: Boolean,
    actions: PlanActions,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(NavRowHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val onBack = actions.onBack
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.testTag(PlanTags.Back)) {
                Icon(PlanIcons.ArrowBack, contentDescription = stringResource(R.string.plan_navigate_up))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                // Fades in over the second half of the collapse, once the expanded block has faded out.
                .graphicsLayer { alpha = ((collapsedFraction() - 0.5f) * 2f).coerceIn(0f, 1f) },
        ) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                val line = if (next != null) "$subtitle \u00B7 ${next.headline} \u00B7 ${next.detail}" else subtitle
                Text(
                    line,
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (next != null) Modifier.testTag(PlanTags.NextAction) else Modifier,
                )
            }
        }
        if (nightSafe) NightSafeChip()
        OverflowMenu(canEdit, actions)
    }
}

/**
 * The expanded block: the route in dot-matrix codes ("SFO ✈ LHR", no codes for a trip without airports) with where the
 * plan is beside it, the city line, and the next action. It fades out over the first half of the collapse.
 */
@Composable
private fun HeaderExpanded(title: String, route: HeaderRoute?, subtitle: String, next: HeaderNext?, ink: Color, collapsedFraction: () -> Float) {
    val quiet = ink.copy(alpha = 0.82f)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 16.dp)
            .graphicsLayer { alpha = (1f - collapsedFraction() * 2f).coerceIn(0f, 1f) },
    ) {
        if (route != null) {
            val described = stringResource(R.string.plan_route_codes, spellOut(route.origin), spellOut(route.destination))
            FlowRow(
                Modifier.padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.clearAndSetSemantics { contentDescription = described },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IataCode(route.origin, style = HeaderCodeStyle, color = ink, animateChanges = false)
                    Icon(
                        PlanIcons.Flight,
                        contentDescription = null,
                        tint = quiet,
                        modifier = Modifier.padding(horizontal = 10.dp).size(18.dp).graphicsLayer { rotationZ = 90f },
                    )
                    IataCode(route.destination, style = HeaderCodeStyle, color = ink, animateChanges = false)
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = quiet)
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (route == null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = quiet)
        }
        if (next != null) {
            val description = stringResource(R.string.plan_next_description, next.headline, next.detail)
            Column(
                Modifier
                    .padding(top = 6.dp)
                    .testTag(PlanTags.NextAction)
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                Text(next.headline, style = MaterialTheme.typography.labelLargeEmphasized)
                Text(next.detail, style = ClockblockTheme.textStyles.timeLabel, color = quiet)
            }
        }
    }
}

/** Where the sky melts into the page: a gradient into the surface, gone once collapsed (read in the layer). */
@Composable
private fun HeaderFoot(collapsedFraction: () -> Float) {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .fillMaxWidth()
            .height(FootHeight)
            .graphicsLayer { alpha = 1f - collapsedFraction() }
            .drawBehind { drawRect(Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface))) },
    )
}

private val HeaderCodeStyle = DotMatrixStyle(glyphHeight = 18.sp)
private val FootHeight = 20.dp

/**
 * The sky's atmosphere behind the header's text: its stars at body night (they come out through dusk), breathing in
 * and out during an ambient session, and now and then the ambient plane. Everything is read in the draw phase from
 * [frame] and [ambient], and fades with the collapsing header.
 */
@Composable
private fun HeaderAtmosphere(frame: () -> HeaderFrame, ambient: HeaderAmbient, ink: Color, collapsedFraction: () -> Float, modifier: Modifier = Modifier) {
    val nightSafe = ClockblockTheme.variant == ClockblockThemeVariant.NightSafe
    val art = ClockblockTheme.artColors
    val starColor = if (nightSafe) art.onInverse.copy(alpha = 0.45f) else Moonlight
    val stars = remember { SkyStars.generate(seed = 7, count = 42) }
    val plane = rememberVectorPainter(PlanIcons.Flight)
    val trail = remember { Path() }
    Box(
        modifier.drawBehind {
            val alpha = (1f - collapsedFraction() * 2f).coerceIn(0f, 1f)
            if (alpha <= 0f) return@drawBehind
            val now = frame()
            val hour = now.bodyTime.toHourFloat()
            val visibility = starVisibility(hour, now.daylight.sunriseHour, now.daylight.sunsetHour)
            val session = ambient.sessionMillis
            drawSkyStars(
                stars,
                Rect(0f, 0f, size.width, size.height - FootHeight.toPx()),
                starColor.copy(alpha = starColor.alpha * alpha),
                visibility,
                session,
            )
            val flight = planeAt(session) ?: return@drawBehind
            drawAmbientPlane(flight, plane, trail, ink, visibility, alpha, session)
        },
    )
}

/**
 * The ambient plane: it climbs gently across the upper right of the sky, its contrail streaming behind and fading out
 * once it has gone. By night there is no contrail to see, only its blinking light.
 */
private fun DrawScope.drawAmbientPlane(
    flight: PlaneFrame,
    plane: VectorPainter,
    trail: Path,
    ink: Color,
    starVisibility: Float,
    alpha: Float,
    sessionMillis: Long,
) {
    val from = Offset(size.width * 0.18f, size.height * 0.62f)
    val to = Offset(size.width * 1.08f, size.height * 0.22f)
    val at = from + (to - from) * flight.progress
    val direction = (to - from) / (to - from).getDistance()
    val dayness = 1f - starVisibility
    // The contrail: a fading streak from where the plane was a while ago up to the plane.
    if (dayness > 0f) {
        val tail = at - direction * (size.width * 0.42f).coerceAtMost((at - from).getDistance())
        trail.rewind()
        trail.moveTo(tail.x, tail.y)
        trail.lineTo(at.x, at.y)
        val streak = Color.White.copy(alpha = 0.7f * dayness * flight.trailAlpha * alpha)
        drawPath(
            trail,
            Brush.linearGradient(listOf(streak.copy(alpha = 0f), streak), start = tail, end = at),
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
    if (flight.progress >= 1f) return
    val planeSize = 14.dp.toPx()
    val heading = Math.toDegrees(kotlin.math.atan2(direction.y, direction.x).toDouble()).toFloat() + 90f
    translate(at.x - planeSize / 2f, at.y - planeSize / 2f) {
        rotate(heading, pivot = Offset(planeSize / 2f, planeSize / 2f)) {
            with(plane) { draw(Size(planeSize, planeSize), alpha = 0.6f * alpha, colorFilter = ColorFilter.tint(ink)) }
        }
    }
    if (starVisibility > 0f) {
        // Its navigation light, blinking about once a second.
        val on = (sessionMillis / 600L) % 2L == 0L
        drawCircle(Color(0xFFFF5A4E).copy(alpha = (if (on) 0.9f else 0.25f) * starVisibility * alpha), 1.6.dp.toPx(), at)
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

/** The warm haze round the sun (its alpha follows [sunGlowStrength]). */
private val SunGlow = Color(0xFFFFE6B0)

/** Line segments the sun path is drawn with: smooth at any header width. */
private const val ArcSteps = 48

/**
 * Where the sun / moon sit at [t] along their path ([ArcPoint.t]): a half sine across the navigation row, between the
 * up arrow and the actions.
 */
private fun Density.celestialCenter(width: Float, top: Float, t: Float, reserveEnd: Dp, rtl: Boolean): Offset {
    val start = CelestialInsetStart.toPx()
    val end = (width - reserveEnd.toPx()).coerceAtLeast(start + 1f)
    val band = NavRowHeight.toPx()
    val x = start + (end - start) * t
    // Right to left, the up arrow and the actions swap sides, so the path runs (and rises) the other way.
    return Offset(if (rtl) width - x else x, top + band * (0.74f - 0.48f * sin(PI.toFloat() * t)))
}

/** The path from [from] to [to] (both [ArcPoint.t]) along [celestialCenter]'s half sine, into [out]. */
private fun Density.arcPath(width: Float, top: Float, from: Float, to: Float, reserveEnd: Dp, rtl: Boolean, out: Path): Path {
    out.rewind()
    val steps = (ArcSteps * abs(to - from)).roundToInt().coerceAtLeast(2)
    for (i in 0..steps) {
        val p = celestialCenter(width, top, from + (to - from) * i / steps, reserveEnd, rtl)
        if (i == 0) out.moveTo(p.x, p.y) else out.lineTo(p.x, p.y)
    }
    return out
}

/**
 * The header's sun or moon at the **body's** solar time, riding the navigation row so it never hides the title, with
 * the sun's glow round it (strongest at sunrise and sunset). It fades out as the header collapses (read in the draw
 * phase). The stars are [HeaderAtmosphere]'s.
 *
 * Sun path (issue #21): the faint dashed half sine the sun / moon ride, and a ghost ring where they would be by the
 * wall clock ([HeaderFrame.localTime]; [HeaderDaylight.ghostT]: at the nearer end when the wall clock is on the other
 * half of the day). When the body is ½ h or more off the wall clock on the same half, a faint arc joins the two along
 * the path ([HeaderDaylight.joins]); in step, the ring circles the sun. All of it is laid out for the frame's
 * daylight, the real day where the traveller is, mirrored right to left, and is decoration only: the subtitle says
 * how far off the body is, in words. Nothing here moves on its own; it repaints when the times do. [frame] changes per
 * scrub frame, so it is read in layout and draw; only the sun / moon switch reaches composition.
 *
 * Moon-phase easter egg: tap the moon [MoonTaps] times and it morphs through "phases" (circle, cookie, clover,
 * ghost, heart) into a cookie that gets a bite taken out of it, then [onTip] fires ("Midnight snack? Your gut
 * has a clock too."). Rare, earned delight: absent when ![eggEnabled] (reduce motion, or the plan says sleep)
 * and in the daytime, when there is no moon. Closing the gate also resets a hatched or half-tapped moon.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeaderCelestial(
    frame: () -> HeaderFrame,
    ink: Color,
    eggEnabled: Boolean,
    reserveEnd: Dp,
    collapsedFraction: () -> Float,
    onTip: () -> Unit,
    modifier: Modifier = Modifier,
    firstLight: Boolean = false,
) {
    // Only the sun / moon switch reaches composition (it decides the moon's tap target); positions are read in layout
    // and draw, since the frame changes per scrub frame.
    val isSun by remember { derivedStateOf { frame().let { it.daylight.arcPoint(it.bodyTime.toHourFloat()).isSun } } }
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
            val now = frame()
            val daylight = now.daylight
            val hour = now.bodyTime.toHourFloat()
            val localHour = now.localTime.toHourFloat()
            val body = daylight.arcPoint(hour)
            val rtl = layoutDirection == LayoutDirection.Rtl
            val rest = celestialCenter(size.width, top, body.t, reserveEnd, rtl)
            // Rising: from just below the header's bottom edge (the horizon) up to its resting place.
            val c = rest.copy(y = rest.y + (1f - risen) * (size.height - rest.y + r * 1.2f))
            // The sun's glow: wide and warm around sunrise and sunset, soft by day, gone at night (and in Night-safe).
            if (!nightSafe) {
                val glow = sunGlowStrength(hour, daylight.sunriseHour, daylight.sunsetHour)
                drawSunGlow(c, size.width * 0.32f, glow * alpha, SunGlow)
            }
            // The sun path, the join to the wall clock's spot on it, and the ghost ring there.
            val pathInk = ink.copy(alpha = ink.alpha * alpha * if (nightSafe) 0.6f else 1f)
            drawPath(
                arcPath(size.width, top, 0f, 1f, reserveEnd, rtl, trail),
                pathInk.copy(alpha = pathInk.alpha * PathAlpha),
                style = Stroke(1.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))),
            )
            // In step, or so close that the ring would still circle the sun's middle: drawn exactly around it.
            val arcWidth = (size.width - reserveEnd.toPx() - CelestialInsetStart.toPx()).coerceAtLeast(1f)
            val ghostT = daylight.ghostT(hour, localHour, snapT = (r + GhostGap.toPx()) / arcWidth)
            if (daylight.joins(hour, localHour) && ghostT != body.t) {
                drawPath(
                    arcPath(size.width, top, body.t, ghostT, reserveEnd, rtl, trail),
                    pathInk.copy(alpha = pathInk.alpha * JoinAlpha),
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            drawCircle(
                pathInk.copy(alpha = pathInk.alpha * GhostAlpha),
                r + GhostGap.toPx(),
                celestialCenter(size.width, top, ghostT, reserveEnd, rtl),
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
                val now = frame()
                val t = now.daylight.arcPoint(now.bodyTime.toHourFloat()).t
                val c = celestialCenter(w.toFloat(), topInset.toPx(), t, reserveEnd, layoutDirection == LayoutDirection.Rtl)
                placeable.place((c.x - target / 2f).roundToInt(), (c.y - target / 2f).roundToInt())
            }
        }
    }
}
