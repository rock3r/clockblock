package dev.sebastiano.clockblocker.opus.feature.plan

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.clickable
import kotlin.math.sin
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
import dev.sebastiano.clockblocker.opus.core.designsystem.component.celestialPosition
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
) {
    val gradient = ClockblockTheme.sky.gradientAt(moment.bodyTime)
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
        BodyClockSky(bodyTime = moment.bodyTime, modifier = Modifier.matchParentSize(), showCelestial = false)
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
            bodyTime = moment.bodyTime,
            eggEnabled = easterEggs,
            reserveEnd = if (nightSafe) 184.dp else 72.dp,
            collapsedFraction = { if (shortWindow) 1f else scrollBehavior.state.collapsedFraction },
            onTip = onMoonTip,
            firstLight = firstLight,
            modifier = Modifier.matchParentSize(),
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

private val HeaderStars = listOf(
    0.30f to 0.10f, 0.42f to 0.22f, 0.55f to 0.08f, 0.64f to 0.30f, 0.12f to 0.52f,
    0.78f to 0.14f, 0.88f to 0.40f, 0.50f to 0.46f, 0.22f to 0.28f, 0.70f to 0.56f,
)

/** Where the sun / moon sit: an arc across the navigation row, between the up arrow and the actions. */
private fun Density.celestialCenter(width: Float, top: Float, hour: Float, reserveEnd: Dp): Offset {
    val t = ((celestialPosition(hour).x - 0.1f) / 0.8f).coerceIn(0f, 1f)
    val start = CelestialInsetStart.toPx()
    val end = (width - reserveEnd.toPx()).coerceAtLeast(start + 1f)
    val band = NavRowHeight.toPx()
    return Offset(start + (end - start) * t, top + band * (0.74f - 0.48f * sin(PI.toFloat() * t)))
}

/**
 * The header's sun or moon at the **body's** solar time, riding the navigation row so it never hides the title,
 * plus a few stars at body night. It fades out as the header collapses (read in the draw phase).
 *
 * Moon-phase easter egg: tap the moon [MoonTaps] times and it morphs through "phases" (circle, cookie, clover,
 * ghost, heart) into a cookie that gets a bite taken out of it, then [onTip] fires ("Midnight snack? Your gut
 * has a clock too."). Rare, earned delight: absent when ![eggEnabled] (reduce motion, or the plan says sleep)
 * and in the daytime, when there is no moon.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeaderCelestial(
    bodyTime: LocalTime,
    eggEnabled: Boolean,
    reserveEnd: Dp,
    collapsedFraction: () -> Float,
    onTip: () -> Unit,
    modifier: Modifier = Modifier,
    firstLight: Boolean = false,
) {
    val hour = bodyTime.toHourFloat()
    val isSun = celestialPosition(hour).isSun
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
            val rest = celestialCenter(size.width, top, hour, reserveEnd)
            // Rising: from just below the header's bottom edge (the horizon) up to its resting place.
            val c = rest.copy(y = rest.y + (1f - risen) * (size.height - rest.y + r * 1.2f))
            if (!isSun) {
                val star = 1.3.dp.toPx()
                HeaderStars.forEachIndexed { i, (x, y) ->
                    drawCircle(moonColor.copy(alpha = moonColor.alpha * 0.6f * alpha), if (i % 3 == 0) star * 1.4f else star, Offset(size.width * x, top + (size.height - top) * y))
                }
            }
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
                val c = celestialCenter(w.toFloat(), topInset.toPx(), hour, reserveEnd)
                placeable.place((c.x - target / 2f).roundToInt(), (c.y - target / 2f).roundToInt())
            }
        }
    }
}
