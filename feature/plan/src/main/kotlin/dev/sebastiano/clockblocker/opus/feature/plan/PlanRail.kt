package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.formatJetLagHours
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.drawHatch
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.drawRoundDots
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.drawStarDots
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import java.time.Duration
import java.time.Instant

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

    /** [nowFraction]: where "now" falls inside this block (0 top … 1 bottom), or null. */
    data class Block(val day: RailDay, val item: RailItem, val first: Boolean, val nowFraction: Float?) : RailRow {
        override val key: String get() = "rail-block-${day.day.index}-${item.advice.id}"
    }

    data class NowMarker(val day: RailDay, val instant: Instant) : RailRow {
        override val key: String get() = "rail-now"
    }
}

/**
 * Flattens [days] into rows. Days entirely in the past fold behind an "earlier days" toggle unless [showEarlier]
 * (or every day is past, i.e. the trip is over and the whole plan is the summary).
 */
internal fun buildRailRows(days: List<RailDay>, now: Instant, showEarlier: Boolean): List<RailRow> = buildList {
    val firstCurrent = days.indexOfFirst { !it.isPast }.let { if (it == -1) 0 else it }
    if (firstCurrent > 0) add(RailRow.Earlier(firstCurrent, showEarlier))
    days.forEachIndexed { index, day ->
        if (index < firstCurrent && !showEarlier) return@forEachIndexed
        add(RailRow.Header(day, isToday = day.nowIndex != null))
        day.items.forEachIndexed { i, item ->
            if (day.nowIndex == i && !day.nowInsideBlock) add(RailRow.NowMarker(day, now))
            val fraction = if (day.nowIndex == i && day.nowInsideBlock) {
                val a = item.advice
                val span = Duration.between(a.start, a.end).toMillis().toFloat()
                if (span <= 0f) 0f else (Duration.between(a.start, now).toMillis() / span).coerceIn(0f, 1f)
            } else {
                null
            }
            add(RailRow.Block(day, item, first = i == 0, nowFraction = fraction))
        }
        if (day.nowIndex == day.items.size) add(RailRow.NowMarker(day, now))
    }
}

/** Index (within [rows]) of the "now" position: the marker or the block containing now. */
internal fun List<RailRow>.nowRowIndex(): Int =
    indexOfFirst { it is RailRow.NowMarker || (it is RailRow.Block && it.nowFraction != null) }

/** Everything a rail row needs besides its own data. */
@Immutable
internal class RailRenderer(
    val highlighted: Set<String>,
    val flightRoute: (adviceId: String) -> String?,
    val bodyHour: (Instant) -> Float,
    val onBlockClick: (adviceId: String) -> Unit,
    val onToggleEarlier: () -> Unit,
)

/** Emits the rail: sticky day headers, blocks and the now marker. */
internal fun LazyListScope.railRows(rows: List<RailRow>, renderer: RailRenderer, timeColumn: Dp) {
    rows.forEach { row ->
        when (row) {
            is RailRow.Header -> stickyHeader(row.key, contentType = "header") { DayHeader(row) }
            is RailRow.Earlier -> item(row.key, contentType = "earlier") { EarlierToggle(row, renderer.onToggleEarlier) }
            is RailRow.NowMarker -> item(row.key, contentType = "now") { NowMarkerRow(row, timeColumn) }
            is RailRow.Block -> item(row.key, contentType = "block") {
                RailBlockRow(row, renderer, timeColumn, highlighted = row.item.advice.id in renderer.highlighted)
            }
        }
    }
}

/** Width of the leading time column: the widest "00:00" in the time style, so all rows align. */
@Composable
internal fun rememberTimeColumnWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = OpusTheme.textStyles.timeTitle
    val density = LocalDensity.current
    val formatter = rememberTimeFormatter()
    return remember(style, density, formatter) {
        val sample = formatter.format(java.time.LocalTime.of(22, 58))
        with(density) { measurer.measure(sample, style).size.width.toDp() } + 12.dp
    }
}

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
        DayKind.Travel -> OpusTheme.adviceColors[AdviceType.Flight].let { it.container to it.onContainer }
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
    val offsetHours = (day.secondaryZone.rules.getOffset(day.start).totalSeconds - day.zone.rules.getOffset(day.start).totalSeconds) / 3600f
    val secondary = (day.secondaryZone.cityName() + " " + formatJetLagHours(offsetHours)).replace(' ', '\u00A0')
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

@Composable
private fun NowMarkerRow(row: RailRow.NowMarker, timeColumn: Dp) {
    val formatter = rememberTimeFormatter()
    val color = MaterialTheme.colorScheme.primary
    val time = formatter.formatFull(row.instant.atZone(row.day.zone).toLocalTime())
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag(PlanTags.NowMarker),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            time,
            style = OpusTheme.textStyles.timeLabel,
            color = color,
            modifier = Modifier.width(timeColumn),
        )
        Box(
            Modifier
                .weight(1f)
                .height(18.dp)
                .drawBehind {
                    val y = size.height / 2f
                    val r = 5.dp.toPx()
                    drawCircle(color, r, Offset(CapsuleWidth.toPx() / 2f, y))
                    drawLine(color, Offset(CapsuleWidth.toPx() / 2f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
                },
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.plan_now), style = MaterialTheme.typography.labelLargeEmphasized, color = color)
    }
}

@Composable
private fun RailBlockRow(row: RailRow.Block, renderer: RailRenderer, timeColumn: Dp, highlighted: Boolean) {
    val advice = row.item.advice
    val day = row.day
    val role = OpusTheme.adviceColors[advice.type]
    val scheme = MaterialTheme.colorScheme
    val formatter = rememberTimeFormatter()
    val resources = LocalContext.current.resources
    val sky = OpusTheme.sky
    val isNow = row.item.status == RailStatus.Now
    val past = row.item.status == RailStatus.Past
    val emphasised = isNow || highlighted
    val height = capsuleHeight(advice.duration, advice.type.isMoment || advice.start == advice.end)
    val label = advice.type.label()
    val range = formatter.range(advice.start, advice.end, day.zone, resources)
    val secondaryRange = formatter.range(advice.start, advice.end, day.secondaryZone, resources) + " " + day.secondaryZone.cityName()
    val detail = when {
        advice.type == AdviceType.Flight -> listOfNotNull(renderer.flightRoute(advice.id), advice.detail, formatDuration(advice.duration)).joinToString(" · ")
        advice.type.isMoment -> advice.detail
        else -> formatDuration(advice.duration) + (advice.detail?.let { " · $it" } ?: "")
    }
    val description = if (advice.type.isMoment) {
        stringResource(R.string.plan_moment_description, label, range)
    } else {
        stringResource(
            R.string.plan_block_description,
            label,
            formatter.formatFull(advice.start.atZone(day.zone).toLocalTime()),
            formatter.formatFull(advice.end.atZone(day.zone).toLocalTime()),
        )
    }
    val outcomeText = row.item.outcome?.let {
        stringResource(
            when (it) {
                AdviceOutcome.Done -> R.string.plan_outcome_done
                AdviceOutcome.Skipped -> R.string.plan_outcome_skipped
                AdviceOutcome.CantDo -> R.string.plan_outcome_cant
            },
        )
    }
    val nowText = stringResource(R.string.plan_now)
    val bodyTop = remember(advice.start) { sky.gradientAt(renderer.bodyHour(advice.start)).mid }
    val bodyBottom = remember(advice.end) { sky.gradientAt(renderer.bodyHour(advice.end)).mid }
    val rowShape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(rowShape)
            .then(if (emphasised) Modifier.background(scheme.surfaceContainerHigh, rowShape) else Modifier)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.plan_open_why)) { renderer.onBlockClick(advice.id) }
            .padding(horizontal = 8.dp)
            .height(IntrinsicSize.Min)
            .testTag(PlanTags.block(advice.id))
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(description, detail, secondaryRange).joinToString(". ")
                if (isNow) stateDescription = nowText else if (outcomeText != null) stateDescription = outcomeText
            },
        verticalAlignment = Alignment.Top,
    ) {
        // Time column: start time, upright = local (dial type rule).
        Column(Modifier.width(timeColumn).padding(top = RowGap + 6.dp)) {
            Text(
                formatter.format(advice.start.atZone(day.zone).toLocalTime()),
                style = OpusTheme.textStyles.timeTitle,
                color = if (emphasised) scheme.onSurface else scheme.onSurfaceVariant,
            )
            formatter.marker(advice.start.atZone(day.zone).toLocalTime())?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        // Rail column: faint body-clock sky band, connector line, the capsule (height = duration).
        val connector = scheme.outlineVariant
        Box(
            Modifier
                .width(CapsuleWidth + 12.dp)
                .fillMaxHeight()
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(bodyTop, bodyBottom)), alpha = 0.16f)
                    if (!row.first) {
                        drawLine(connector, Offset(size.width / 2f, 0f), Offset(size.width / 2f, RowGap.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    }
                },
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
        Spacer(Modifier.width(12.dp))
        // Text column.
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = height + RowGap * 2)
                .padding(top = RowGap + 6.dp, bottom = RowGap + 6.dp)
                .alpha(if (past && !emphasised) 0.7f else 1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = if (emphasised) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isNow) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = CircleShape, color = scheme.primary, contentColor = scheme.onPrimary) {
                        Text(nowText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                row.item.outcome?.let {
                    Spacer(Modifier.width(8.dp))
                    OutcomeBadge(it)
                }
            }
            Text(range, style = OpusTheme.textStyles.timeLabel, color = scheme.onSurface)
            if (!detail.isNullOrBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(secondaryRange, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant.copy(alpha = 0.85f))
        }
    }
}
