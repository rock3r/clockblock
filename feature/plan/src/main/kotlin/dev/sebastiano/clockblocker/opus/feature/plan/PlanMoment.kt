package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.runtime.Immutable
import dev.sebastiano.clockblocker.opus.core.circadian.PlanDaySpan
import dev.sebastiano.clockblocker.opus.core.circadian.adaptationProgressAt
import dev.sebastiano.clockblocker.opus.core.circadian.bodyClockTimeAt
import dev.sebastiano.clockblocker.opus.core.circadian.daySpans
import dev.sebastiano.clockblocker.opus.core.designsystem.component.misalignmentFraction
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/** Where in its life a plan is at a given instant. */
enum class PlanStage {
    /** Before the first plan day starts. */
    Upcoming,
    PreTrip,
    Travel,

    /** Arrival days: the body clock is still moving. */
    Adapting,
    Adapted,

    /** After the last plan day ended. */
    Complete,
}

/** What kind of plan this is, which decides the screen's supporting copy. */
enum class PlanKind {
    /** The normal case: move the body clock to the destination. */
    Adapt,

    /** Less than an hour to shift: nothing to do but sleep well. */
    NoShift,

    /** Short trip: keep the body clock on home time. */
    StayOnHomeTime,
}

/** How long a moment (melatonin) stays "now" after its time, so the card doesn't vanish within the minute. */
internal val MomentWindow: Duration = Duration.ofMinutes(30)

/** How long after the plan ends adapting can still be celebrated (a trip opened weeks later shouldn't confetti). */
internal val CelebrationGrace: Duration = Duration.ofDays(2)

/**
 * Everything the plan screen shows about one instant: the day and zone the user is in, what to do now and next,
 * and where the body clock is. Pure function of the plan and the instant ([momentAt]); the screen computes it at
 * "now" and, while the dial is being scrubbed, at the previewed instant.
 *
 * @property zone the zone the user is (expected to be) in: the current [PlanDay]'s zone; before the plan the
 *   origin, after it the destination.
 * @property secondaryZone the other end of the trip, shown small next to every time.
 * @property active the most important advice right now (highest priority), or null for free time.
 * @property concurrent other advice active at the same time (e.g. the flight while sleeping on board).
 * @property bodyAheadHours body clock minus local clock, in hours, in [-12, 12): negative = body behind.
 * @property daysToGo model estimate of days left until adapted (0 once adapted).
 * @property wantsNightSafe the screen should dim itself: avoid-light or sleep now, or biological night with no
 *   light advice running.
 */
@Immutable
data class PlanMoment(
    val instant: Instant,
    val stage: PlanStage,
    val day: PlanDay?,
    val zone: ZoneId,
    val secondaryZone: ZoneId,
    val active: Advice?,
    val concurrent: ImmutableList<Advice>,
    val upNext: ImmutableList<Advice>,
    val bodyTime: LocalTime,
    val bodyAheadHours: Float,
    val progress: Float,
    val misalignment: Float,
    val daysToGo: Double,
    val isBodyNight: Boolean,
    val wantsNightSafe: Boolean,
) {
    /** Time left in the [active] window (zero for moments and when nothing is active). */
    val remaining: Duration
        get() = active?.takeUnless { it.type.isMoment }?.let { Duration.between(instant, it.end) }?.takeIf { !it.isNegative }
            ?: Duration.ZERO
}

/** [PlanKind] of this plan. */
val JetLagPlan.kind: PlanKind
    get() = when {
        strategy == AdaptationStrategy.StayOnHomeTime -> PlanKind.StayOnHomeTime
        direction == ShiftDirection.None || abs(shiftHours) < 1.0 -> PlanKind.NoShift
        else -> PlanKind.Adapt
    }

/** When the last flight lands (the arrival), or null for a plan without flights. */
val JetLagPlan.landing: Instant?
    get() = allAdvice.filter { it.type == AdviceType.Flight }.maxOfOrNull { it.end }

/** Advice active at [instant], highest priority first. Moments stay active for [MomentWindow]. */
fun JetLagPlan.activeAdviceAt(instant: Instant): List<Advice> = allAdvice
    .filter { advice ->
        if (advice.type.isMoment || advice.start == advice.end) {
            !instant.isBefore(advice.start) && instant.isBefore(advice.start.plus(MomentWindow))
        } else {
            instant in advice
        }
    }
    .sortedWith(compareBy<Advice> { it.type.ordinal }.thenBy { it.start })

/** See [PlanMoment]. [upNextCount] caps [PlanMoment.upNext]. */
fun JetLagPlan.momentAt(instant: Instant, upNextCount: Int = 3): PlanMoment {
    val spans = daySpans()
    val span = spans.firstOrNull { instant in it }
    val origin = ZoneId.of(originZoneId)
    val destination = ZoneId.of(destinationZoneId)
    val stage = stageAt(instant, spans, span)
    val zone = span?.day?.zoneId?.let(ZoneId::of) ?: if (stage == PlanStage.Upcoming) origin else destination
    val secondary = if (zone.id == origin.id) destination else origin

    val active = activeAdviceAt(instant)
    val upNext = allAdvice.filter { it.start.isAfter(instant) }.sortedBy { it.start }.take(upNextCount)

    val localOffset = zone.rules.getOffset(instant).totalSeconds
    val bodyOffset = bodyOffsetAt(instant).totalSeconds
    val bodyAhead = wrapHours((bodyOffset - localOffset) / 3600f)

    val progress = adaptationProgressAt(instant)
    val misalignment = misalignmentFraction(misalignmentHoursAt(instant).toFloat(), shiftHours.toFloat())
    val bodyNight = isBodyNightAt(instant)
    val lightNow = active.any { it.type == AdviceType.SeeBrightLight || it.type == AdviceType.SeeLight }
    val restNow = active.any { it.type == AdviceType.AvoidLight || it.type == AdviceType.Sleep }

    return PlanMoment(
        instant = instant,
        stage = stage,
        day = span?.day,
        zone = zone,
        secondaryZone = secondary,
        active = active.firstOrNull(),
        concurrent = active.drop(1).toImmutableList(),
        upNext = upNext.toImmutableList(),
        bodyTime = bodyClockTimeAt(instant),
        bodyAheadHours = bodyAhead,
        progress = progress,
        misalignment = misalignment,
        daysToGo = daysToGoAt(instant, progress),
        isBodyNight = bodyNight,
        wantsNightSafe = restNow || (bodyNight && !lightNow),
    )
}

/** Whether to show the once-per-trip "Clockblocked." moment at [instant] (persistence is the caller's job). */
fun JetLagPlan.isCelebrationDue(instant: Instant): Boolean {
    if (kind != PlanKind.Adapt) return false
    val spans = daySpans()
    val last = spans.lastOrNull() ?: return false
    if (!instant.isBefore(last.end.plus(CelebrationGrace))) return false
    val landed = landing?.let { !instant.isBefore(it) } ?: true
    if (!landed) return false
    val day = spans.firstOrNull { instant in it }?.day
    return day?.kind == DayKind.Adapted || !instant.isBefore(last.end) || adaptationProgressAt(instant) >= 0.95f
}

private fun JetLagPlan.stageAt(instant: Instant, spans: List<PlanDaySpan>, span: PlanDaySpan?): PlanStage {
    val first = spans.firstOrNull() ?: return PlanStage.Complete
    if (instant.isBefore(first.start)) return PlanStage.Upcoming
    if (!instant.isBefore(spans.last().end)) return PlanStage.Complete
    return when (span?.day?.kind) {
        DayKind.PreTrip -> PlanStage.PreTrip
        DayKind.Travel -> PlanStage.Travel
        DayKind.Arrival -> if (kind == PlanKind.Adapt && adaptationProgressAt(instant) >= 0.95f) PlanStage.Adapted else PlanStage.Adapting
        DayKind.Adapted -> PlanStage.Adapted
        null -> PlanStage.Complete
    }
}

/**
 * Biological night ≈ DLMO → habitual wake, i.e. from 6 h before to 2.5 h after the core-body-temperature
 * minimum (the same band the dial's inner ring paints).
 */
private fun JetLagPlan.isBodyNightAt(instant: Instant): Boolean {
    val nearest = phase.minByOrNull { Duration.between(it.instant, instant).abs() } ?: return false
    var cbt = nearest.cbtMin
    // Use the CBTmin occurrence nearest to the instant (the stored one is nearest to its phase point).
    while (Duration.between(cbt, instant).toHours() >= 12) cbt = cbt.plus(Duration.ofDays(1))
    while (Duration.between(instant, cbt).toHours() >= 12) cbt = cbt.minus(Duration.ofDays(1))
    return !instant.isBefore(cbt.minus(Duration.ofHours(6))) && instant.isBefore(cbt.plus(Duration.ofMinutes(150)))
}

private fun JetLagPlan.daysToGoAt(instant: Instant, progress: Float): Double {
    if (kind != PlanKind.Adapt || progress >= 0.95f) return 0.0
    val landed = landing ?: return estimatedDaysToAdapt
    if (instant.isBefore(landed)) return estimatedDaysToAdapt
    val elapsed = Duration.between(landed, instant).toMinutes() / (24.0 * 60.0)
    return (estimatedDaysToAdapt - elapsed).coerceAtLeast(0.0)
}

private fun wrapHours(hours: Float): Float = ((hours + 12f).mod(24f)) - 12f

/** Status of an advice block on the rail relative to an instant. */
enum class RailStatus { Past, Now, Future }

/** One advice block on the rail. [inFlight]: it starts while airborne (inside a flight block, not the flight itself). */
@Immutable
data class RailItem(val advice: Advice, val status: RailStatus, val outcome: AdviceOutcome?, val inFlight: Boolean = false)

/**
 * One day on the rail: its span, the zone its times are shown in (+ the secondary zone) and its blocks in start
 * order. [nowIndex] is where the "now" marker goes when the instant falls inside this day: the index of the
 * block that contains it, or of the first block after it (`items.size` = after the last block).
 */
@Immutable
data class RailDay(
    val day: PlanDay,
    val start: Instant,
    val end: Instant,
    val zone: ZoneId,
    val secondaryZone: ZoneId,
    val items: ImmutableList<RailItem>,
    val nowIndex: Int?,
    val nowInsideBlock: Boolean,
) {
    val isPast: Boolean get() = items.isNotEmpty() && items.all { it.status == RailStatus.Past } && nowIndex == null
}

/** The rail timeline at [instant] with logged [outcomes] (advice id → outcome). */
fun JetLagPlan.railDays(instant: Instant, outcomes: Map<String, AdviceOutcome>): List<RailDay> {
    val origin = ZoneId.of(originZoneId)
    val destination = ZoneId.of(destinationZoneId)
    val activeIds = activeAdviceAt(instant).mapTo(HashSet()) { it.id }
    val flights = allAdvice.filter { it.type == AdviceType.Flight }
    return daySpans().map { span ->
        val zone = ZoneId.of(span.day.zoneId)
        val items = span.day.advice.sortedWith(compareBy<Advice> { it.start }.thenBy { it.type.ordinal }).map { advice ->
            val status = when {
                advice.id in activeIds -> RailStatus.Now
                !instant.isBefore(if (advice.type.isMoment) advice.start.plus(MomentWindow) else advice.end) -> RailStatus.Past
                else -> RailStatus.Future
            }
            val inFlight = advice.type != AdviceType.Flight && flights.any { advice.start in it }
            RailItem(advice, status, outcomes[advice.id], inFlight)
        }
        val inside = instant in span
        val nowIndex = if (!inside) {
            null
        } else {
            items.indexOfFirst { it.status == RailStatus.Now }.takeIf { it >= 0 }
                ?: items.indexOfFirst { it.status == RailStatus.Future }.takeIf { it >= 0 }
                ?: items.size
        }
        RailDay(
            day = span.day,
            start = span.start,
            end = span.end,
            zone = zone,
            secondaryZone = if (zone.id == origin.id) destination else origin,
            items = items.toImmutableList(),
            nowIndex = nowIndex,
            nowInsideBlock = nowIndex != null && items.getOrNull(nowIndex)?.status == RailStatus.Now,
        )
    }
}
