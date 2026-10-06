package dev.sebastiano.clockblocker.opus.feature.plan

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.LocalReduceMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * The instant a day pill previews: [now]'s wall-clock time (as read in [nowZone], the zone the user is living by)
 * on this day, in this day's zone, kept inside the day's span. Today previews [now] itself.
 */
internal fun RailDay.previewInstant(now: Instant, nowZone: ZoneId): Instant {
    if (!now.isBefore(start) && now.isBefore(end)) return now
    return at(now.atZone(nowZone).toLocalTime())
}

/** [time] on this day in its zone, kept inside the day's span (a minute before its end at the latest). */
internal fun RailDay.at(time: LocalTime): Instant {
    val candidate = ZonedDateTime.of(day.date, time, zone).toInstant()
    val last = maxOf(start, end.minus(Duration.ofMinutes(1)))
    return candidate.coerceIn(start, last)
}

/**
 * The instant the screen is anchored to while the day strip has [selectedDay] (a `PlanDay.index`) picked, or
 * null for live: nothing picked, today picked, or a day the plan no longer has.
 */
internal fun selectedDayBase(days: List<RailDay>, selectedDay: Int?, now: Instant, nowZone: ZoneId): Instant? {
    val day = days.firstOrNull { it.day.index == selectedDay } ?: return null
    return day.previewInstant(now, nowZone).takeUnless { it == now }
}

/** Body clock vs local time (hours, + = ahead) at each day's local noon, keyed by `PlanDay.index`. */
internal fun dayStripOffsets(plan: JetLagPlan, days: List<RailDay>): Map<Int, Float> =
    days.associate { it.day.index to plan.momentAt(it.at(LocalTime.NOON), upNextCount = 0).bodyAheadHours }

private val PillDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d")
private val PillHeight = 68.dp
private val PillCorner = 16.dp

/**
 * A row of day pills above the dial (design: "see how any day of the plan looks"). Picking a day anchors the
 * dial, the Now card and the header to that day at the current time of day; picking today (or the toolbar's
 * Now) goes back to live. Each pill carries a tiny two-dot alignment mark: the dots sit apart by how far the
 * body clock is from local time that day and meet once it has caught up.
 *
 * Frequency gate: the plan screen is opened many times a day, so the strip has no entrance motion. Selection
 * moves with the container tokens; the pill's look (colour + outline) is the static carrier under reduce motion.
 */
@Composable
internal fun PlanDayStrip(
    days: List<RailDay>,
    offsets: Map<Int, Float>,
    todayIndex: Int?,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val reduce = LocalReduceMotion.current
    val listState = rememberLazyListState()
    val shownIndex = selectedIndex ?: todayIndex
    var positioned by remember { mutableStateOf(false) }
    LaunchedEffect(shownIndex, days.size) {
        val position = days.indexOfFirst { it.day.index == shownIndex }.takeIf { it >= 0 } ?: return@LaunchedEffect
        val target = (position - 1).coerceAtLeast(0)
        if (!positioned || reduce) {
            listState.scrollToItem(target)
            positioned = true
        } else {
            val visible = listState.layoutInfo.visibleItemsInfo
            val fullyVisible = visible.any { it.index == position && it.offset >= 0 && it.offset + it.size <= listState.layoutInfo.viewportEndOffset }
            if (!fullyVisible) listState.animateScrollToItem(target)
        }
    }
    LazyRow(
        state = listState,
        modifier = modifier.selectableGroup().testTag(PlanTags.DayStrip),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(days, key = { _, day -> day.day.index }) { _, day ->
            val index = day.day.index
            DayPill(
                day = day,
                offset = offsets[index] ?: 0f,
                isToday = index == todayIndex,
                selected = index == shownIndex,
                onClick = {
                    if (index != shownIndex) {
                        view.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK,
                        )
                    }
                    onSelect(if (index == todayIndex) null else index)
                },
            )
        }
    }
}

@Composable
private fun DayPill(day: RailDay, offset: Float, isToday: Boolean, selected: Boolean, onClick: () -> Unit) {
    val motion = OpusTheme.motion
    val colors = MaterialTheme.colorScheme
    val resources = LocalContext.current.resources
    val corner by animateDpAsState(if (selected) PillHeight / 2 else PillCorner, motion.containerSpatial(), label = "dayPillCorner")
    val container by animateColorAsState(if (selected) colors.secondaryContainer else colors.surfaceContainer, motion.colour(), label = "dayPillColour")
    val content = if (selected) colors.onSecondaryContainer else colors.onSurface
    val outline = colors.primary
    val label = resources.dayShortTitle(day.day, pill = true)
    val date = PillDate.withLocale(Locale.getDefault()).format(day.day.date)
    val description = stringResource(
        if (isToday) R.string.plan_strip_pill_description_today else R.string.plan_strip_pill_description,
        resources.dayTitle(day.day),
        formatDayDate(day.day.date),
        resources.bodyShiftLabel(offset),
    )
    Box(
        Modifier
            .heightIn(min = PillHeight)
            .widthIn(min = 72.dp)
            // Corner and colour are read here, in the draw phase (the selection moves without recomposing).
            .drawBehind {
                val radius = CornerRadius(corner.toPx())
                drawRoundRect(container, cornerRadius = radius)
                if (isToday) {
                    val stroke = 2.dp.toPx()
                    drawRoundRect(
                        outline,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = size.copy(width = size.width - stroke, height = size.height - stroke),
                        cornerRadius = CornerRadius((corner.toPx() - stroke / 2).coerceAtLeast(0f)),
                        style = Stroke(stroke),
                    )
                }
            }
            .clip(RoundedCornerShape(PillCorner))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(PlanTags.dayPill(day.day.index))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics {}) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (day.day.kind == DayKind.Adapted) {
                        Icon(PlanIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                    }
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                Text(
                    date,
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.72f),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                AlignmentDots(offset, local = content.copy(alpha = 0.6f), body = colors.primary)
            }
        }
    }
}

/**
 * Two dots: hollow = local time, filled = body clock, apart by how far the body is from local time (behind =
 * left, ahead = right, 12 h = the full width). Aligned days show the filled dot inside the hollow one.
 */
@Composable
private fun AlignmentDots(offsetHours: Float, local: Color, body: Color) {
    Canvas(Modifier.size(width = 32.dp, height = 10.dp)) {
        val r = 3.5.dp.toPx()
        val stroke = 1.5.dp.toPx()
        val travel = (size.width - 2 * r - stroke) / 2f
        val apart = min(abs(offsetHours), 12f) / 12f * travel
        val centre = Offset(size.width / 2f, size.height / 2f)
        val direction = sign(offsetHours)
        drawCircle(local, r, centre.copy(x = centre.x - direction * apart / 2f), style = Stroke(stroke))
        drawCircle(body, r - stroke / 2f, centre.copy(x = centre.x + direction * apart / 2f))
    }
}
