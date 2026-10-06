package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.circadian.PlannerConfig
import dev.sebastiano.clockblocker.opus.core.circadian.daySpans
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.formatJetLagHours
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** One sample of the journey chart: [day] = days since landing (negative before), [hours] = hours off destination time. */
internal data class JourneyPoint(val day: Float, val hours: Float)

/**
 * The body clock's journey for the adaptation card's chart: hours away from destination time, by day relative to
 * landing (day 0). [withPlan] is the plan's own trajectory; [withoutPlan] is a rough guide (home time until
 * landing, then a straight line closing at the model's no-plan estimate), labelled as such in the UI.
 */
@Immutable
internal data class AdaptationJourney(
    val withPlan: List<JourneyPoint>,
    val withoutPlan: List<JourneyPoint>,
    val startDay: Float,
    val endDay: Float,
    val maxHours: Float,
) {
    /** The plan line's value at [day] (clamped to the ends). */
    fun withPlanAt(day: Float): Float {
        val after = withPlan.indexOfFirst { it.day >= day }
        return when (after) {
            -1 -> withPlan.last().hours
            0 -> withPlan.first().hours
            else -> {
                val a = withPlan[after - 1]
                val b = withPlan[after]
                val t = if (b.day == a.day) 1f else (day - a.day) / (b.day - a.day)
                a.hours + (b.hours - a.hours) * t
            }
        }
    }
}

private const val MinutesPerDay = 24f * 60f

/** The chart never runs longer than this after landing, even when the no-plan estimate does. */
private const val MaxChartDays = 10f

/** Days since this plan's landing at [instant] (null without a flight). */
internal fun JetLagPlan.dayOfJourney(instant: Instant): Float? =
    landing?.let { Duration.between(it, instant).toMinutes() / MinutesPerDay }

/**
 * Destination minus origin clock offset, in hours normalised to (−12, 12] (+ = east). The origin is read at the
 * first departure and the destination at landing, like the planner's home offset: the body clock doesn't follow
 * a DST change at home while airborne.
 */
internal fun JetLagPlan.geographicShiftHours(): Float {
    val flights = allAdvice.filter { it.type == AdviceType.Flight }
    val departed = flights.minOfOrNull { it.start } ?: generatedAt
    val landed = flights.maxOfOrNull { it.end } ?: generatedAt
    val origin = ZoneId.of(originZoneId).rules.getOffset(departed).totalSeconds
    val destination = ZoneId.of(destinationZoneId).rules.getOffset(landed).totalSeconds
    return normalisedOffsetHours(destination - origin)
}

/** How far [to]'s clocks are ahead of [from]'s at [at], in hours normalised to (−12, 12] (date line aware). */
internal fun zoneDeltaHours(from: ZoneId, to: ZoneId, at: Instant): Float =
    normalisedOffsetHours(to.rules.getOffset(at).totalSeconds - from.rules.getOffset(at).totalSeconds)

/**
 * A zone delta as "+8 h", "−3½ h" or "+5¾ h": [formatJetLagHours]'s style, but at quarter-hour precision so
 * Kathmandu or Chatham don't contradict the UTC offset printed next to them.
 */
internal fun formatZoneDelta(hours: Float): String {
    val quarters = Math.round(hours * QuartersPerHour)
    if (quarters == 0) return formatQuarterHours(0f)
    return (if (quarters > 0) "+" else "\u2212") + formatQuarterHours(hours)
}

/** "5¾ h", "½ h", "0 h": the unsigned magnitude of [hours], rounded to the nearest quarter hour. */
internal fun formatQuarterHours(hours: Float): String {
    val quarters = abs(Math.round(hours * QuartersPerHour))
    val whole = quarters / QuartersPerHour
    val fraction = QuarterFractions[quarters % QuartersPerHour]
    val number = if (whole == 0 && fraction.isNotEmpty()) fraction else "$whole$fraction"
    return "$number h"
}

private val QuarterFractions = listOf("", "\u00BC", "\u00BD", "\u00BE")

/** An offset difference in seconds as hours in (−12, 12]: a 24 h difference is the same clock time. */
private fun normalisedOffsetHours(diffSeconds: Int): Float {
    val diff = Math.floorMod(diffSeconds + HalfDaySeconds - 1, DaySeconds) - HalfDaySeconds + 1
    return diff / SecondsPerHour
}

private const val DaySeconds = 24 * 3600
private const val HalfDaySeconds = 12 * 3600
private const val SecondsPerHour = 3600f

/** The chart's data, or null for plans that don't adapt or have no flight. */
internal fun JetLagPlan.journey(sampleHours: Long = 2): AdaptationJourney? {
    if (kind != PlanKind.Adapt) return null
    val landed = landing ?: return null
    val spans = daySpans()
    if (spans.isEmpty()) return null
    fun dayOf(instant: Instant) = Duration.between(landed, instant).toMinutes() / MinutesPerDay
    val startDay = dayOf(spans.first().start)
    val withoutDays = estimatedDaysWithoutPlan.toFloat()
    val endDay = max(min(dayOf(spans.last().end), MaxChartDays), min(withoutDays, MaxChartDays))

    val withPlan = buildList {
        var t = spans.first().start
        val end = landed.plus(Duration.ofMinutes((endDay * MinutesPerDay).toLong()))
        while (t.isBefore(end)) {
            add(JourneyPoint(dayOf(t), abs(misalignmentHoursAt(t)).toFloat()))
            t = t.plus(Duration.ofHours(sampleHours))
        }
        add(JourneyPoint(endDay, abs(misalignmentHoursAt(end)).toFloat()))
    }

    val home = abs(geographicShiftHours())
    val withoutPlan = buildList {
        add(JourneyPoint(startDay, home))
        add(JourneyPoint(0f, home))
        if (withoutDays <= endDay) {
            add(JourneyPoint(withoutDays, 0f))
        } else {
            add(JourneyPoint(endDay, home * (1f - endDay / withoutDays)))
        }
    }
    return AdaptationJourney(
        withPlan = withPlan,
        withoutPlan = withoutPlan,
        startDay = startDay,
        endDay = endDay,
        maxHours = max(home, withPlan.maxOf { it.hours }),
    )
}

/** Whole days the plan saves over the no-plan estimate, or null when it doesn't (never claim a tie as a win). */
internal fun daysSaved(withPlan: Double, withoutPlan: Double): DaysSaved? {
    if (withPlan >= EstimateHorizonDays) return null
    val days = (roundDays(withoutPlan) - roundDays(withPlan)).takeIf { it > 0 } ?: return null
    return DaysSaved(days, atLeast = withoutPlan >= EstimateHorizonDays)
}

/**
 * How much sooner the plan gets there. [atLeast] when the no-plan estimate is the horizon sentinel: the model only
 * knows it takes longer than that, so the difference is a lower bound.
 */
internal data class DaysSaved(val days: Int, val atLeast: Boolean)

/** The model's "didn't adapt within the simulated window" value, for either estimate. */
internal val EstimateHorizonDays: Double = PlannerConfig().estimateHorizonDays.toDouble()

/** When the plan takes the long way round the clock face. */
internal enum class LongWayRound { EastByDelaying, WestByAdvancing }

/**
 * A shift in hours (either sign) as a duration snapped to the nearest quarter hour, so half- and quarter-hour
 * zones read as they are ("10 h 30 min") without float noise surfacing as odd minutes.
 */
internal fun shiftDuration(hours: Double): Duration =
    Duration.ofMinutes((abs(hours) * QuartersPerHour).roundToLong() * MinutesPerQuarter)

private const val QuartersPerHour = 4
private const val MinutesPerQuarter = 15L

/**
 * The long way round: the plan moves the body clock the opposite way to the map (an eastward trip delayed, a
 * westward one advanced), or null when it goes the direct way.
 */
internal fun JetLagPlan.longWayRound(): LongWayRound? {
    val geographic = geographicShiftHours()
    return when {
        kind != PlanKind.Adapt -> null
        geographic > 0f && direction == ShiftDirection.Delay -> LongWayRound.EastByDelaying
        geographic < 0f && direction == ShiftDirection.Advance -> LongWayRound.WestByAdvancing
        else -> null
    }
}

/**
 * The journey chart: hours out of sync with destination time by day. Solid line + soft fill = the plan's
 * trajectory; dashed = a rough no-plan guide; a vertical "Arrive" marker at landing; a dot on the plan line at
 * [activeDay] (the moment on screen) that glides with the data token when the day strip picks another day.
 * Values are data, so the dot never bounces. The legend labels both lines in words.
 */
@Composable
internal fun JourneyChart(journey: AdaptationJourney, activeDay: Float?, description: String, modifier: Modifier = Modifier) {
    val motion = OpusTheme.motion
    val colors = MaterialTheme.colorScheme
    val planColour = colors.primary
    val noPlanColour = colors.onSurfaceVariant
    val markerColour = colors.outline
    val dotRing = colors.surfaceContainerLow
    val measurer = rememberTextMeasurer()
    val arriveLabel = stringResource(R.string.plan_journey_arrive)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val topLabel = stringResource(R.string.plan_journey_hours, formatQuarterHours(journey.maxHours))
    val zeroLabel = stringResource(R.string.plan_journey_in_sync)
    val target = (activeDay ?: journey.startDay).coerceIn(journey.startDay, journey.endDay)
    val dotDay by animateFloatAsState(target, motion.dataSpatial(), label = "journeyDot")

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .semantics { contentDescription = description }
                .testTag(PlanTags.JourneyChart)
                .drawWithCache {
                    val left = 4.dp.toPx()
                    val right = size.width - 4.dp.toPx()
                    val top = 18.dp.toPx()
                    val bottom = size.height - 14.dp.toPx()
                    val span = (journey.endDay - journey.startDay).coerceAtLeast(0.01f)
                    val scaleY = journey.maxHours.coerceAtLeast(1f)
                    fun x(day: Float) = left + (day - journey.startDay) / span * (right - left)
                    fun y(hours: Float) = bottom - hours / scaleY * (bottom - top)
                    val line = Path().apply {
                        journey.withPlan.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.day), y(p.hours)) else lineTo(x(p.day), y(p.hours)) }
                    }
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(x(journey.withPlan.last().day), bottom)
                        lineTo(x(journey.withPlan.first().day), bottom)
                        close()
                    }
                    val noPlan = Path().apply {
                        journey.withoutPlan.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.day), y(p.hours)) else lineTo(x(p.day), y(p.hours)) }
                    }
                    val stroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val dashed = Stroke(
                        1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                    )
                    val fillBrush = Brush.verticalGradient(listOf(planColour.copy(alpha = 0.22f), planColour.copy(alpha = 0f)), startY = top, endY = bottom)
                    val arriveX = x(0f)
                    val arrive = measurer.measure(arriveLabel, labelStyle)
                    val topText = measurer.measure(topLabel, labelStyle)
                    val zeroText = measurer.measure(zeroLabel, labelStyle)
                    onDrawBehind {
                        // Baseline (in sync) and the arrival marker.
                        drawLine(markerColour.copy(alpha = 0.4f), Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
                        if (arriveX in left..right) {
                            drawLine(markerColour, Offset(arriveX, top - 2.dp.toPx()), Offset(arriveX, bottom), 1.dp.toPx())
                            val labelX = (arriveX - arrive.size.width / 2f).coerceIn(left, right - arrive.size.width)
                            drawText(arrive, topLeft = Offset(labelX, 0f))
                        }
                        // The scale label sits top-left unless the Arrive label would collide with it.
                        val arriveStart = arriveX - arrive.size.width / 2f
                        val fitsLeft = left + topText.size.width + 8.dp.toPx() <= arriveStart
                        drawText(topText, topLeft = if (fitsLeft) Offset(left, 0f) else Offset(right - topText.size.width, 0f))
                        drawText(zeroText, topLeft = Offset(right - zeroText.size.width, bottom + 1.dp.toPx()))
                        drawPath(noPlan, noPlanColour, style = dashed)
                        drawPath(fill, fillBrush)
                        drawPath(line, planColour, style = stroke)
                        // The dot is read here, in the draw phase, so it glides without recomposing the card.
                        val dot = Offset(x(dotDay), y(journey.withPlanAt(dotDay)))
                        drawCircle(dotRing, 6.dp.toPx(), dot)
                        drawCircle(planColour, 4.dp.toPx(), dot)
                    }
                },
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            LegendItem(planColour, dashed = false, label = stringResource(R.string.plan_journey_with))
            LegendItem(noPlanColour, dashed = true, label = stringResource(R.string.plan_journey_without))
        }
    }
}

@Composable
private fun LegendItem(colour: Color, dashed: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 18.dp, height = 8.dp)) {
            val y = size.height / 2f
            drawLine(
                colour,
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = if (dashed) 1.5.dp.toPx() else 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


