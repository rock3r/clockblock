package dev.sebastiano.clockblocker.opus.feature.plan

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.AdviceGlyph
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.label
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.drawHatch
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.drawRoundDots
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.drawStarDots
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/** A row of the rail's lazy list. */
@Immutable
internal sealed interface RailRow {
    val key: String

    data class Earlier(val count: Int, val expanded: Boolean) : RailRow {
        override val key: String get() = "rail-earlier"
    }

    data class Header(val day: RailDay, val isToday: Boolean) : RailRow {
        override val key: String get() = "rail-day-${day.day.index}-${day.day.date}"
    }

    /**
     * The zone the rail's times are shown in changes here (the day before was in [from], the next one is in [to]),
     * usually at landing. [at] is the first instant shown in [to].
     */
    data class ZoneSwitch(val day: RailDay, val from: ZoneId, val to: ZoneId, val at: Instant) : RailRow {
        override val key: String get() = "rail-zone-${day.day.index}-${day.day.date}"
    }

    /**
     * One block. [nowFraction]: where "now" falls inside this block (0 top … 1 bottom), or null. [first]/[last]:
     * this block opens/closes its day's body-sky band (the band's ends are rounded).
     *
     * [children] are shorter, lower-priority blocks that sit entirely inside this one ("Caffeine OK" during
     * "See bright light"): they ride along in this row instead of repeating its start time in the time column.
     * [showTime] is false when this block starts at the same time as the row above, so the time column only ever
     * moves forward. The row's slice of the body-clock sky runs from the block's start to [bandEnd] (where the next
     * row starts), so the strip down the rail is one continuous gradient.
     */
    data class Block(
        val day: RailDay,
        val item: RailItem,
        val first: Boolean,
        val nowFraction: Float?,
        val last: Boolean = false,
        val children: List<RailItem> = emptyList(),
        val showTime: Boolean = true,
        val bandEnd: Instant = item.advice.end,
    ) : RailRow {
        override val key: String get() = "rail-block-${day.day.index}-${item.advice.id}"
    }

    /** [bandAt]: the instant whose body-clock sky the marker's slice of the strip shows (null = no strip here). */
    data class NowMarker(val day: RailDay, val instant: Instant, val bandAt: Instant? = null) : RailRow {
        override val key: String get() = "rail-now"
    }
}

/**
 * Whether [child] rides inside [parent]'s row: it starts inside [parent], ends no later (moments just need to start
 * inside), and is less important (higher [AdviceType] ordinal), so an important block is never demoted into a
 * chip. Nothing nests under a flight (blocks on board get their own rows and an "In flight" badge), and a flight
 * never becomes a chip either: its row carries the route and flight number.
 */
internal fun nestsInside(child: Advice, parent: Advice): Boolean {
    if (AdviceType.Flight in setOf(parent.type, child.type) || child.type.ordinal <= parent.type.ordinal) return false
    if (child.start.isBefore(parent.start) || !child.start.isBefore(parent.end)) return false
    return child.type.isMoment || child.start == child.end || !child.end.isAfter(parent.end)
}

/**
 * Flattens [days] into rows. Days entirely in the past fold behind an "earlier days" toggle unless [showEarlier]
 * (or every day is past, i.e. the trip is over and the whole plan is the summary).
 */
internal fun buildRailRows(days: List<RailDay>, now: Instant, showEarlier: Boolean): List<RailRow> = buildList {
    val firstCurrent = days.indexOfFirst { !it.isPast }.let { if (it == -1) 0 else it }
    if (firstCurrent > 0) add(RailRow.Earlier(firstCurrent, showEarlier))
    var previousShown: RailDay? = null
    days.forEachIndexed { index, day ->
        if (index < firstCurrent && !showEarlier) return@forEachIndexed
        val previous = previousShown
        if (previous != null && previous.zone.id != day.zone.id) add(RailRow.ZoneSwitch(day, previous.zone, day.zone, day.start))
        previousShown = day
        add(RailRow.Header(day, isToday = day.nowIndex != null))

        // Group each block with the lower-priority blocks that sit inside it.
        val groups = mutableListOf<Pair<Int, MutableList<RailItem>>>()
        day.items.forEachIndexed { i, item ->
            val parent = groups.lastOrNull()
            if (parent != null && nestsInside(item.advice, day.items[parent.first].advice)) parent.second += item else groups += i to mutableListOf()
        }
        val nowGroup = day.nowIndex?.let { n -> groups.indexOfLast { it.first <= n } }
        groups.forEachIndexed { g, (i, children) ->
            val item = day.items[i]
            if (nowGroup == g && !day.nowInsideBlock) add(RailRow.NowMarker(day, now, bandAt = item.advice.start.takeIf { g > 0 }))
            val fraction = if (nowGroup == g && day.nowInsideBlock) {
                val a = item.advice
                val span = Duration.between(a.start, a.end).toMillis().toFloat()
                if (span <= 0f) 0f else (Duration.between(a.start, now).toMillis() / span).coerceIn(0f, 1f)
            } else {
                null
            }
            val next = groups.getOrNull(g + 1)?.let { day.items[it.first].advice.start }
            val end = (children.map { it.advice.end } + item.advice.end).max()
            add(
                RailRow.Block(
                    day = day,
                    item = item,
                    first = g == 0,
                    nowFraction = fraction,
                    last = g == groups.lastIndex,
                    children = children.toList(),
                    showTime = g == 0 || day.items[groups[g - 1].first].advice.start != item.advice.start,
                    bandEnd = next ?: end,
                ),
            )
        }
        if (day.nowIndex == day.items.size) add(RailRow.NowMarker(day, now))
    }
}

/** Index (within [rows]) of the "now" position: the marker or the block containing now. */
internal fun List<RailRow>.nowRowIndex(): Int =
    indexOfFirst { it is RailRow.NowMarker || (it is RailRow.Block && it.nowFraction != null) }

/** Index (within [rows]) of the first block row [highlighted] lights up (itself or one of its chips), or -1. */
internal fun List<RailRow>.highlightedRowIndex(highlighted: Set<String>): Int =
    if (highlighted.isEmpty()) {
        -1
    } else {
        indexOfFirst { row -> row is RailRow.Block && (row.item.advice.id in highlighted || row.children.any { it.advice.id in highlighted }) }
    }

/**
 * Like [LazyListState.scrollToItem] ([index]'s top ends [scrollOffset] px above the viewport's top), but moving on
 * [spec]. An item that isn't laid out is approached by its estimated distance (jumping to a screenful away first
 * when it is far), then settled exactly.
 */
internal suspend fun LazyListState.animateScrollToItem(index: Int, scrollOffset: Int, spec: AnimationSpec<Float>) {
    fun laidOut() = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (laidOut() == null) {
        val visible = layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return scrollToItem(index, scrollOffset)
        val screenful = visible.size
        val first = firstVisibleItemIndex
        if (index > first + 3 * screenful) scrollToItem(index - screenful) else if (index < first - 3 * screenful) scrollToItem(index + screenful)
        val average = visible.sumOf { it.size } / visible.size
        val estimate = (index - firstVisibleItemIndex) * average - firstVisibleItemScrollOffset + scrollOffset
        animateScrollBy(estimate.toFloat(), spec)
    }
    val item = laidOut() ?: return scrollToItem(index, scrollOffset)
    val delta = item.offset + scrollOffset
    if (delta != 0) animateScrollBy(delta.toFloat(), spec)
}

/** The day (`PlanDay.index`) this row belongs to; null for the "earlier days" toggle. */
internal val RailRow.dayIndex: Int?
    get() = when (this) {
        is RailRow.Earlier -> null
        is RailRow.Header -> day.day.index
        is RailRow.ZoneSwitch -> day.day.index
        is RailRow.Block -> day.day.index
        is RailRow.NowMarker -> day.day.index
    }

/**
 * The day (`PlanDay.index`) the rail is showing: the one whose row crosses a reading line a third of the way down
 * [layout]'s viewport, or the last day's once the list can't scroll further ([atEnd]: a short last day never
 * reaches the line). [firstRow] is the list index of `this[0]`. Null with nothing laid out, or over the "earlier
 * days" toggle or the title.
 */
internal fun List<RailRow>.dayInView(layout: LazyListLayoutInfo, firstRow: Int, atEnd: Boolean): Int? {
    val visible = layout.visibleItemsInfo
    if (visible.isEmpty()) return null
    val item = if (atEnd) {
        visible.maxBy { it.index }
    } else {
        val line = layout.viewportStartOffset + (layout.viewportEndOffset - layout.viewportStartOffset) / 3
        // Highest index starting above the line: a sticky header pinned at the top (a lower index) never wins over
        // the row actually under the line.
        visible.filter { it.offset <= line }.maxByOrNull { it.index } ?: return null
    }
    return getOrNull(item.index - firstRow)?.dayIndex
}

/**
 * Everything a rail row needs besides its own data. [onCheckOff]: a row's check-off circle was ticked (`true`: log
 * it as done) or unticked (`false`: forget the log).
 */
@Immutable
internal class RailRenderer(
    val highlighted: Set<String>,
    val flightRoute: (adviceId: String) -> String?,
    val bodyHour: (Instant) -> Float,
    val onBlockClick: (adviceId: String) -> Unit,
    val onToggleEarlier: () -> Unit,
    val onCheckOff: (adviceId: String, done: Boolean) -> Unit = { _, _ -> },
)

/** Emits the rail: sticky day headers, blocks and the now marker. */
internal fun LazyListScope.railRows(rows: List<RailRow>, renderer: RailRenderer, timeColumn: Dp) {
    rows.forEach { row ->
        when (row) {
            is RailRow.Header -> stickyHeader(row.key, contentType = "header") { DayHeader(row) }
            is RailRow.Earlier -> item(row.key, contentType = "earlier") { EarlierToggle(row, renderer.onToggleEarlier) }
            is RailRow.NowMarker -> item(row.key, contentType = "now") { NowMarkerRow(row, renderer, timeColumn) }
            is RailRow.ZoneSwitch -> item(row.key, contentType = "zone") { ZoneSwitchRow(row) }
            is RailRow.Block -> item(row.key, contentType = "block") {
                RailBlockRow(row, renderer, timeColumn, highlighted = row.item.advice.id in renderer.highlighted)
            }
        }
    }
}

/**
 * Width of the leading time column: the widest "00:00" in the time style, so all rows align, or the locale's wider
 * AM/PM marker under it (see [timeColumnWidth]).
 */
@Composable
internal fun rememberTimeColumnWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = ClockblockTheme.textStyles.timeTitle
    val markerStyle = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val formatter = rememberTimeFormatter()
    return remember(style, markerStyle, density, formatter) {
        with(density) {
            val digits = measurer.measure(formatter.format(java.time.LocalTime.of(22, 58)), style).size.width.toDp()
            val marker = listOf(java.time.LocalTime.of(10, 0), java.time.LocalTime.of(22, 0))
                .mapNotNull { formatter.marker(it) }
                .maxOfOrNull { measurer.measure(it, markerStyle).size.width.toDp() } ?: 0.dp
            timeColumnWidth(digits, marker)
        }
    }
}

/**
 * The time column for [digits] wide times with an AM/PM marker up to [widestMarker] wide under them: room for the
 * wider of the two, but a marker gets at most [MaxMarkerToDigits] times the digits' width. A phrase-sized one (Kölsch's
 * "Uhr vörmiddaachs") is cut short on one line, rather than wrapping and growing every row.
 */
internal fun timeColumnWidth(digits: Dp, widestMarker: Dp): Dp =
    maxOf(digits, minOf(widestMarker, digits * MaxMarkerToDigits)) + TimeColumnGap

private const val MaxMarkerToDigits = 1.5f
private val TimeColumnGap = 12.dp

private val CapsuleWidth = 44.dp
private val RowGap = 6.dp

/** Capsule height: "height = duration", compressed so a night's sleep doesn't take a whole screen. */
private fun capsuleHeight(duration: Duration, moment: Boolean): Dp {
    if (moment) return 56.dp
    val hours = duration.toMinutes() / 60f
    return (44f + hours * 16f).coerceIn(64f, 184f).dp
}

@Composable
private fun DayHeader(row: RailRow.Header) {
    val day = row.day
    val scheme = MaterialTheme.colorScheme
    val (badgeColor, badgeContent) = when (day.day.kind) {
        DayKind.PreTrip -> scheme.secondaryContainer to scheme.onSecondaryContainer
        DayKind.Travel -> ClockblockTheme.adviceColors[AdviceType.Flight].let { it.container to it.onContainer }
        DayKind.Arrival -> scheme.primaryContainer to scheme.onPrimaryContainer
        DayKind.Adapted -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    val kindLabel = stringResource(
        when (day.day.kind) {
            DayKind.PreTrip -> R.string.plan_day_kind_pre
            DayKind.Travel -> R.string.plan_day_kind_travel
            DayKind.Arrival -> R.string.plan_day_kind_arrival
            DayKind.Adapted -> R.string.plan_day_kind_adapted
        },
    )
    val offsetHours = zoneDeltaHours(day.zone, day.secondaryZone, day.start)
    val secondary = (day.secondaryZone.cityName() + " " + formatZoneDelta(offsetHours)).replace(' ', '\u00A0')
    Surface(color = scheme.surface, modifier = Modifier.fillMaxWidth().testTag(PlanTags.day(day.day.index))) {
        Row(
            Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(dayTitle(day.day), style = MaterialTheme.typography.titleLargeEmphasized)
                Text(
                    formatDayDate(day.day.date) + " · " + day.zone.cityName() + " · " + secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (row.isToday) {
                Surface(shape = CircleShape, color = scheme.primary, contentColor = scheme.onPrimary, modifier = Modifier.padding(end = 6.dp)) {
                    Text(stringResource(R.string.plan_today), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Surface(shape = CircleShape, color = badgeColor, contentColor = badgeContent) {
                Text(kindLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun EarlierToggle(row: RailRow.Earlier, onToggle: () -> Unit) {
    val label = if (row.expanded) {
        stringResource(R.string.plan_hide_earlier)
    } else {
        pluralStringResource(R.plurals.plan_earlier_days, row.count, row.count)
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Surface(
            onClick = onToggle,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.align(Alignment.CenterStart).heightIn(min = 48.dp).testTag(PlanTags.EarlierDays),
        ) {
            Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Centre of the rail column (the capsules' axis), from the start of the column. */
private val RailColumnWidth = CapsuleWidth + 12.dp

/**
 * This row's slice of the body-clock sky strip behind the capsules: [top] at the row's top edge to [bottom] at its
 * bottom edge. Neighbouring rows share their edge colour, so the strip reads as one ribbon; only a day's two ends
 * are pill-rounded (square ends read as stray grey rectangles behind the first and last capsules in dark theme).
 */
private fun Modifier.skyBand(top: Color, bottom: Color, roundTop: Boolean, roundBottom: Boolean): Modifier = drawBehind {
    val end = CornerRadius(size.width / 2f)
    val band = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(Offset.Zero, size),
                topLeft = if (roundTop) end else CornerRadius.Zero,
                topRight = if (roundTop) end else CornerRadius.Zero,
                bottomRight = if (roundBottom) end else CornerRadius.Zero,
                bottomLeft = if (roundBottom) end else CornerRadius.Zero,
            ),
        )
    }
    drawPath(band, Brush.verticalGradient(listOf(top, bottom)), alpha = SkyBandAlpha)
}

private const val SkyBandAlpha = 0.16f

@Composable
private fun NowMarkerRow(row: RailRow.NowMarker, renderer: RailRenderer, timeColumn: Dp) {
    val formatter = rememberTimeFormatter()
    val color = MaterialTheme.colorScheme.primary
    val sky = ClockblockTheme.sky
    val time = formatter.formatFull(row.instant.atZone(row.day.zone).toLocalTime())
    val band = row.bandAt?.let { at -> remember(at, sky) { sky.gradientAt(renderer.bodyHour(at)).mid } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(IntrinsicSize.Min).testTag(PlanTags.NowMarker),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            time,
            style = ClockblockTheme.textStyles.timeLabel,
            color = color,
            modifier = Modifier.width(timeColumn).padding(vertical = 4.dp),
        )
        // The marker sits on the capsules' axis, over the same strip the blocks around it paint.
        Box(
            Modifier
                .width(RailColumnWidth)
                .fillMaxHeight()
                .then(if (band != null) Modifier.skyBand(band, band, roundTop = false, roundBottom = false) else Modifier)
                .drawBehind {
                    val y = size.height / 2f
                    drawCircle(color, 5.dp.toPx(), Offset(size.width / 2f, y))
                    drawLine(color, Offset(size.width / 2f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
                },
        )
        Box(
            Modifier
                .weight(1f)
                .height(18.dp)
                .drawBehind {
                    val y = size.height / 2f
                    drawLine(color, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
                },
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.plan_now), style = MaterialTheme.typography.labelLargeEmphasized, color = color)
    }
}

/**
 * Where the rail's times change zone (usually at landing): "Switching to London time · UTC+1 · 8 h ahead". The day
 * headers name each day's zone too, but a block-by-block reader needs to see the clocks change under them.
 */
@Composable
private fun ZoneSwitchRow(row: RailRow.ZoneSwitch) {
    val scheme = MaterialTheme.colorScheme
    val role = ClockblockTheme.adviceColors[AdviceType.Flight]
    val title = stringResource(R.string.plan_zone_switch, row.to.cityName())
    val hours = zoneDeltaHours(row.from, row.to, row.at)
    val detail = stringResource(
        R.string.plan_zone_switch_detail,
        utcLabel(row.to.rules.getOffset(row.at)),
        formatZoneDelta(hours),
        row.from.cityName(),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(PlanTags.zoneSwitch(row.day.day.index))
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(scheme.outlineVariant))
        Surface(
            shape = CircleShape,
            color = role.container,
            contentColor = role.onContainer,
            modifier = Modifier.padding(horizontal = 8.dp).widthIn(max = 360.dp),
        ) {
            Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(PlanIcons.Flight, contentDescription = null, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 90f })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    Text(detail, style = ClockblockTheme.textStyles.timeLabel, color = role.onContainer.copy(alpha = 0.85f))
                }
            }
        }
        Box(Modifier.weight(1f).height(1.dp).background(scheme.outlineVariant))
    }
}

/** "UTC+1", "UTC−7", "UTC+5:30" (true minus sign). */
internal fun utcLabel(offset: ZoneOffset): String {
    val total = offset.totalSeconds / 60
    val sign = if (total < 0) "\u2212" else "+"
    val h = abs(total) / 60
    val m = abs(total) % 60
    return if (m == 0) "UTC$sign$h" else "UTC$sign$h:" + m.toString().padStart(2, '0')
}

@Composable
private fun RailBlockRow(row: RailRow.Block, renderer: RailRenderer, timeColumn: Dp, highlighted: Boolean) {
    val advice = row.item.advice
    val day = row.day
    val role = ClockblockTheme.adviceColors[advice.type]
    val scheme = MaterialTheme.colorScheme
    val formatter = rememberTimeFormatter()
    val resources = LocalContext.current.resources
    val sky = ClockblockTheme.sky
    val isNow = row.item.status == RailStatus.Now
    val past = row.item.status == RailStatus.Past
    val emphasised = isNow || highlighted
    val height = capsuleHeight(advice.duration, advice.type.isMoment || advice.start == advice.end)
    val label = advice.type.label()
    val range = formatter.range(advice.start, advice.end, day.zone, resources)
    val secondaryRange = formatter.range(advice.start, advice.end, day.secondaryZone, resources) + " " + day.secondaryZone.cityName()
    val detail = when {
        advice.type == AdviceType.Flight -> (flightDetails(renderer.flightRoute(advice.id), advice.detail) + formatDuration(advice.duration)).joinToString(" · ")
        advice.type.isMoment -> advice.detail
        else -> formatDuration(advice.duration) + (advice.detail?.let { " · $it" } ?: "")
    }
    val description = blockDescription(advice, day)
    val inFlightText = stringResource(R.string.plan_in_flight)
    val outcomeText = row.item.outcome?.let { outcomeLabel(it) }
    val nowText = stringResource(R.string.plan_now)
    // Keyed on the palette too, so Night-safe's sky cross-fade reaches rows already on screen.
    val bodyTop = remember(advice.start, sky) { sky.gradientAt(renderer.bodyHour(advice.start)).mid }
    val bodyBottom = remember(row.bandEnd, sky) { sky.gradientAt(renderer.bodyHour(row.bandEnd)).mid }
    val rowShape = RoundedCornerShape(24.dp)
    RailRowLayout(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(rowShape)
            .then(if (emphasised) Modifier.background(scheme.surfaceContainerHigh, rowShape) else Modifier)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.plan_open_why)) { renderer.onBlockClick(advice.id) }
            .padding(horizontal = 8.dp)
            .testTag(PlanTags.block(advice.id))
            // The row speaks as one button; the blocks riding inside it (children) stay their own buttons.
            .semantics {
                contentDescription = listOfNotNull(description, inFlightText.takeIf { row.item.inFlight }, detail, secondaryRange).joinToString(". ")
                if (isNow) stateDescription = nowText else if (outcomeText != null) stateDescription = outcomeText
            },
    ) {
        // Time column: start time, upright = local (dial type rule). Blank when the row above starts at the same
        // time, so the column only ever moves forward.
        Column(Modifier.width(timeColumn).padding(top = RowGap + 6.dp).clearAndSetSemantics {}) {
            if (row.showTime) {
                Text(
                    formatter.format(advice.start.atZone(day.zone).toLocalTime()),
                    style = ClockblockTheme.textStyles.timeTitle,
                    color = if (emphasised) scheme.onSurface else scheme.onSurfaceVariant,
                )
                formatter.marker(advice.start.atZone(day.zone).toLocalTime())?.let {
                    // One line whatever the locale: a marker wider than the column is cut short, the row never grows.
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // Rail column: the body-clock sky strip, connector line, the capsule (height = duration).
        val connector = scheme.outlineVariant
        Box(
            Modifier
                .width(RailColumnWidth)
                .fillMaxHeight()
                .skyBand(bodyTop, bodyBottom, roundTop = row.first, roundBottom = row.last)
                .drawBehind {
                    if (!row.first) {
                        drawLine(connector, Offset(size.width / 2f, 0f), Offset(size.width / 2f, RowGap.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    }
                }
                .clearAndSetSemantics {},
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = RowGap, bottom = RowGap)
                    .width(CapsuleWidth)
                    .heightIn(min = height)
                    .fillMaxHeight()
                    .alpha(if (past && !emphasised) 0.55f else 1f)
                    .clip(RoundedCornerShape(CapsuleWidth / 2))
                    .background(if (isNow) role.color else role.container)
                    .drawBehind {
                        val ink = (if (isNow) role.onColor else role.onContainer).copy(alpha = 0.22f)
                        when (advice.type) {
                            AdviceType.AvoidLight -> drawHatch(ink)
                            AdviceType.Sleep -> drawStarDots(ink.copy(alpha = 0.5f))
                            AdviceType.Nap, AdviceType.OptionalNap -> drawRoundDots(ink)
                            // Avoid caffeine: the struck-through glyph carries it; a strike across a tall capsule
                            // read as a stray line.
                            else -> Unit
                        }
                    },
            ) {
                AdviceGlyph(
                    advice.type,
                    active = isNow,
                    size = 36.dp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
                )
                if (row.nowFraction != null) {
                    // Where now falls inside the block: a marker line across the capsule.
                    Box(
                        Modifier
                            .align(BiasAlignment(0f, row.nowFraction * 2f - 1f))
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(scheme.onSurface),
                    )
                }
            }
        }
        // Text column (its height sets the row's; the rail column stretches to match).
        Column(
            Modifier
                .heightIn(min = height + RowGap * 2)
                .padding(top = RowGap + 6.dp, bottom = RowGap + 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // The text lines share their width with the check-off slot; the chips below keep the column's full width.
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    Modifier.weight(1f).alpha(if (past && !emphasised) 0.7f else 1f).clearAndSetSemantics {},
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    FlowRow(
                        itemVerticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            label,
                            style = if (emphasised) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isNow) {
                            Surface(shape = CircleShape, color = scheme.primary, contentColor = scheme.onPrimary) {
                                Text(nowText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                        if (row.item.inFlight) InFlightBadge(inFlightText)
                        row.item.outcome?.let { OutcomeBadge(it) }
                    }
                    Text(range, style = ClockblockTheme.textStyles.timeLabel, color = scheme.onSurface)
                    if (!detail.isNullOrBlank()) {
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(secondaryRange, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant.copy(alpha = 0.85f))
                }
                // Check-off slot: on every row but flights (which just happen), so titles wrap at the same width down
                // the rail; the circle itself only shows once the block has started (you can't log the future). It
                // sits level with the title, its 48 dp target reaching up into the row's top padding.
                if (advice.type != AdviceType.Flight) {
                    Box(Modifier.offset(y = -CheckOffLift).size(CheckOffTarget), contentAlignment = Alignment.Center) {
                        if (row.item.status != RailStatus.Future) {
                            CheckOffCircle(
                                done = row.item.outcome == AdviceOutcome.Done,
                                label = label,
                                onChange = { renderer.onCheckOff(advice.id, it) },
                                modifier = Modifier.testTag(PlanTags.checkOff(advice.id)),
                            )
                        }
                    }
                }
            }
            if (row.children.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.children.forEach { child ->
                        ChildBlockChip(child, day, highlighted = child.advice.id in renderer.highlighted, onClick = { renderer.onBlockClick(child.advice.id) })
                    }
                }
            }
        }
    }
}

private val RailTextGap = 12.dp

/**
 * A rail row's three columns, in order: time, rail (sky band and capsule) and text. The text column's own height sets
 * the row's, then the rail column is stretched to it. (A Row with `height(IntrinsicSize.Min)` did this before, but a
 * FlowRow's intrinsic height comes up a line short when a badge or chip only just doesn't fit, and the row then
 * dropped the chips that wrapped.)
 */
@Composable
private fun RailRowLayout(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val (time, rail, text) = measurables
        val railWidth = RailColumnWidth.roundToPx()
        val gap = RailTextGap.roundToPx()
        val timePlaceable = time.measure(Constraints(maxWidth = constraints.maxWidth))
        val textWidth = (constraints.maxWidth - timePlaceable.width - railWidth - gap).coerceAtLeast(0)
        val textPlaceable = text.measure(Constraints(minWidth = textWidth, maxWidth = textWidth))
        val height = maxOf(timePlaceable.height, textPlaceable.height, constraints.minHeight)
        val railPlaceable = rail.measure(Constraints.fixed(railWidth, height))
        layout(constraints.maxWidth, height) {
            timePlaceable.placeRelative(0, 0)
            railPlaceable.placeRelative(timePlaceable.width, 0)
            textPlaceable.placeRelative(timePlaceable.width + railWidth + gap, 0)
        }
    }
}

private val CheckOffTarget = 48.dp

/** How far the check-off target reaches above the title line, so the circle sits level with the title. */
private val CheckOffLift = 12.dp

/**
 * A row's quick check-off: an outlined circle that fills with a check once the block is logged as done. Ticking it
 * gives the same `CONFIRM` click as the Now card's Done (the caller offers Undo); unticking forgets the log. The rail
 * is a 100+/day surface, so the circle simply swaps state: the state layer is its only motion.
 */
@Composable
private fun CheckOffCircle(done: Boolean, label: String, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.plan_check_off, label)
    Box(
        modifier
            .size(CheckOffTarget)
            .clip(CircleShape)
            .toggleable(value = done, role = Role.Checkbox) { checked ->
                if (checked) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onChange(checked)
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .then(if (done) Modifier.background(scheme.primary, CircleShape) else Modifier.border(2.dp, scheme.outline, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(PlanIcons.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

/** "See bright light, 10:00 to 13:30" / "Take melatonin at 22:00": what TalkBack reads for a block. */
@Composable
private fun blockDescription(advice: Advice, day: RailDay): String {
    val formatter = rememberTimeFormatter()
    val resources = LocalContext.current.resources
    val label = advice.type.label()
    return if (advice.type.isMoment) {
        stringResource(R.string.plan_moment_description, label, formatter.range(advice.start, advice.end, day.zone, resources))
    } else {
        stringResource(
            R.string.plan_block_description,
            label,
            formatter.formatFull(advice.start.atZone(day.zone).toLocalTime()),
            formatter.formatFull(advice.end.atZone(day.zone).toLocalTime()),
        )
    }
}

@Composable
private fun outcomeLabel(outcome: AdviceOutcome): String = stringResource(
    when (outcome) {
        AdviceOutcome.Done -> R.string.plan_outcome_done
        AdviceOutcome.Skipped -> R.string.plan_outcome_skipped
        AdviceOutcome.CantDo -> R.string.plan_outcome_cant
    },
)

/**
 * A shorter block that sits inside its row's block ("Caffeine OK" during "See bright light"): its own glyph,
 * label and times, and its own Why? (48 dp target). Labelled, never an icon alone.
 */
@Composable
private fun ChildBlockChip(item: RailItem, day: RailDay, highlighted: Boolean, onClick: () -> Unit) {
    val advice = item.advice
    val role = ClockblockTheme.adviceColors[advice.type]
    val formatter = rememberTimeFormatter()
    val resources = LocalContext.current.resources
    val isNow = item.status == RailStatus.Now
    val past = item.status == RailStatus.Past
    val description = blockDescription(advice, day)
    val nowText = stringResource(R.string.plan_now)
    val outcomeText = item.outcome?.let { outcomeLabel(it) }
    val secondary = formatter.range(advice.start, advice.end, day.secondaryZone, resources) + " " + day.secondaryZone.cityName()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isNow || highlighted) role.container else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (isNow || highlighted) role.onContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .alpha(if (past && !highlighted) 0.7f else 1f)
            .testTag(PlanTags.block(advice.id))
            .semantics {
                contentDescription = listOfNotNull(description, advice.detail, secondary).joinToString(". ")
                if (isNow) stateDescription = nowText else if (outcomeText != null) stateDescription = outcomeText
            },
    ) {
        Row(
            Modifier.heightIn(min = 40.dp).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp).clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdviceGlyph(advice.type, active = isNow, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(advice.type.label(), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    formatter.range(advice.start, advice.end, day.zone, resources) + (advice.detail?.let { " · $it" } ?: ""),
                    style = ClockblockTheme.textStyles.timeLabel,
                )
                Text(secondary, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.8f))
            }
            item.outcome?.let {
                Spacer(Modifier.width(8.dp))
                OutcomeBadge(it)
            }
        }
    }
}

/** Tonal "In flight" label on blocks that happen on board (text, never the plane alone). */
@Composable
private fun InFlightBadge(text: String) {
    val role = ClockblockTheme.adviceColors[AdviceType.Flight]
    Surface(shape = CircleShape, color = role.container, contentColor = role.onContainer) {
        Row(Modifier.padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PlanIcons.Flight, contentDescription = null, modifier = Modifier.size(12.dp).graphicsLayer { rotationZ = 90f })
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}
