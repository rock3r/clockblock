package dev.sebastiano.clockblocker.opus.core.model

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Every kind of card the plan can show. Order = visual priority when windows overlap. */
@Serializable
enum class AdviceType {
    /** Phase-shifting light: as bright as possible (outdoors ideally). */
    SeeBrightLight,

    /** Lower-priority light window: avoid dim/dark. */
    SeeLight,

    /** Minimise light: dark sunglasses, stay indoors, dim screens. */
    AvoidLight,
    Sleep,
    Nap,
    OptionalNap,
    Melatonin,
    Caffeine,
    AvoidCaffeine,

    /** Circadian low point while awake: be careful (driving etc.). */
    PeakFatigue,

    /** Travel marker spanning a flight. */
    Flight,
    ;

    /** Instantaneous advice (taken at a time) vs. windows (an interval). */
    val isMoment: Boolean get() = this == Melatonin
}

/** Why a piece of advice exists. Rendered in the "Why?" sheet; keeps the science transparent. */
@Serializable
enum class AdviceReason {
    /** Light here falls on the advancing side of the light PRC (after CBTmin). */
    LightAdvancesClock,

    /** Light here falls on the delaying side of the light PRC (before CBTmin). */
    LightDelaysClock,

    /** Avoiding light that would push the clock the wrong way. */
    AvoidCounterShift,

    /** Melatonin in the afternoon/evening advances (before DLMO). */
    MelatoninAdvances,

    /** Melatonin in the morning delays (after CBTmin). */
    MelatoninDelays,

    /** Sleep timed on the shifting schedule. */
    ShiftedSleep,

    /** Sleep at the destination's local night. */
    DestinationSleep,

    /** Short trip: keep the body clock on home time. */
    StayOnHomeTime,

    /** Alertness support without disturbing the following sleep. */
    AlertnessSupport,

    /** Caffeine would disturb upcoming sleep. */
    ProtectSleep,

    /** Circadian low while awake. */
    CircadianLow,

    /** Nap to pay down sleep pressure without anchoring the old time zone. */
    SleepPressure,
    TravelMarker,

    /** In-flight body-night for someone who can't sleep on planes: rest in the dark (eye mask) instead. */
    RestInFlight,
}

@Serializable
data class Advice(
    /** Stable id (deterministic for a given plan) so UI keys, alarms and completion logs survive re-planning. */
    val id: String,
    val type: AdviceType,
    @Serializable(with = InstantIsoSerializer::class) val start: Instant,
    @Serializable(with = InstantIsoSerializer::class) val end: Instant,
    val reason: AdviceReason,
    /** Optional extra like a melatonin dose label ("0.5 mg") or flight number. */
    val detail: String? = null,
) {
    val duration: Duration get() = Duration.between(start, end)
    operator fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)
}

@Serializable
enum class DayKind { PreTrip, Travel, Arrival, Adapted }

@Serializable
data class PlanDay(
    /** Relative to the departure day: -2, -1, 0 (travel), 1, 2… */
    val index: Int,
    val kind: DayKind,
    /** Calendar date in [zoneId]. */
    @Serializable(with = LocalDateIsoSerializer::class) val date: LocalDate,
    /** The zone the user is (expected to be) in that day; the day's cards are displayed in this zone. */
    val zoneId: String,
    val advice: List<Advice>,
)

/**
 * The estimated state of the body clock at an instant. `bodyUtcOffset` expresses the body clock as if it
 * were a time zone: at home and fully entrained it equals the home zone's offset; after arriving it drifts
 * day by day towards the destination offset. "Your body thinks it's 03:12" = instant at that offset.
 */
@Serializable
data class PhasePoint(
    @Serializable(with = InstantIsoSerializer::class) val instant: Instant,
    /** Body clock expressed as an offset from UTC, in minutes (may exceed ±14h transiently is never expected). */
    val bodyUtcOffsetMinutes: Int,
    /** Estimated core-body-temperature minimum nearest to [instant]. */
    @Serializable(with = InstantIsoSerializer::class) val cbtMin: Instant,
)

@Serializable
enum class ShiftDirection { Advance, Delay, None }

@Serializable
data class JetLagPlan(
    val tripId: String,
    @Serializable(with = InstantIsoSerializer::class) val generatedAt: Instant,
    val strategy: AdaptationStrategy,
    val direction: ShiftDirection,
    /** Signed hours the body clock must move: + = advance (eastward-like), - = delay. */
    val shiftHours: Double,
    val originZoneId: String,
    val destinationZoneId: String,
    val days: List<PlanDay>,
    /** Body-clock trajectory, typically hourly from the first plan day to the end. */
    val phase: List<PhasePoint>,
    /** Model estimate of days until within ±1h of destination time, following the plan. */
    val estimatedDaysToAdapt: Double,
    /** Model estimate without any intervention (for the "you saved X days" message). */
    val estimatedDaysWithoutPlan: Double,
    /**
     * The zone the body clock starts on when it isn't [originZoneId] ([Trip.bodyClockStartZoneId], e.g. still on
     * home time after just arriving in the departure city); null = [originZoneId]. See [startZoneId].
     */
    val bodyClockStartZoneId: String? = null,
) {
    val allAdvice: List<Advice> get() = days.flatMap { it.advice }

    /** The zone the body clock starts on: "home time" for this plan (home-time mode keeps the clock there). */
    val startZoneId: String get() = bodyClockStartZoneId ?: originZoneId

    /** Advice active at [instant], highest priority (lowest ordinal) first. */
    fun activeAt(instant: Instant): List<Advice> =
        allAdvice.filter { instant in it || (it.type.isMoment && it.start == instant) }.sortedBy { it.type.ordinal }

    /** Next advice starting strictly after [instant]. */
    fun nextAfter(instant: Instant): Advice? =
        allAdvice.filter { it.start.isAfter(instant) }.minByOrNull { it.start }

    /**
     * Interpolated body clock offset at [instant] (clamped to the trajectory ends). Offsets are clock values, so
     * interpolation takes the short way round the 24 h dial (stored offsets may wrap, e.g. −11 h → +12 h).
     */
    fun bodyOffsetAt(instant: Instant): ZoneOffset {
        if (phase.isEmpty()) return ZoneId.of(startZoneId).rules.getOffset(instant)
        val after = phase.indexOfFirst { !it.instant.isBefore(instant) }
        val minutes = when {
            after == -1 -> phase.last().bodyUtcOffsetMinutes.toDouble()
            after == 0 -> phase.first().bodyUtcOffsetMinutes.toDouble()
            else -> {
                val a = phase[after - 1]
                val b = phase[after]
                val span = Duration.between(a.instant, b.instant).toMillis().toDouble()
                val t = if (span == 0.0) 1.0 else Duration.between(a.instant, instant).toMillis() / span
                val diff = Math.floorMod(b.bodyUtcOffsetMinutes - a.bodyUtcOffsetMinutes + 720, 1440) - 720
                a.bodyUtcOffsetMinutes + diff * t
            }
        }
        var seconds = (minutes * 60).toInt()
        if (seconds > 18 * 3600) seconds -= 24 * 3600
        if (seconds < -18 * 3600) seconds += 24 * 3600
        return ZoneOffset.ofTotalSeconds(seconds.coerceIn(-18 * 3600, 18 * 3600))
    }

    /** Misalignment between body clock and destination local time at [instant], in hours, normalised to (−12, 12]. */
    fun misalignmentHoursAt(instant: Instant): Double {
        val dest = ZoneId.of(destinationZoneId).rules.getOffset(instant).totalSeconds
        val body = bodyOffsetAt(instant).totalSeconds
        val diff = Math.floorMod(dest - body + 12 * 3600 - 1, 24 * 3600) - 12 * 3600 + 1
        return diff / 3600.0
    }
}
