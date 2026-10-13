package dev.sebastiano.clockblocker.opus.feature.trips.list

import dev.sebastiano.clockblocker.opus.core.circadian.adaptationProgressAt
import dev.sebastiano.clockblocker.opus.core.circadian.daySpans
import dev.sebastiano.clockblocker.opus.core.data.plan.NowTrip
import dev.sebastiano.clockblocker.opus.core.data.plan.TripWindow
import dev.sebastiano.clockblocker.opus.core.designsystem.component.misalignmentFraction
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.core.model.Trip
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which list section a trip belongs to. Declaration order = display order. */
enum class TripPhase { InProgress, Upcoming, Past }

/** The one-line status shown in a trip card's chip. */
sealed interface TripStatus {
    /** Departs in [days] calendar days (0 = today, 1 = tomorrow). */
    data class StartsIn(val days: Long) : TripStatus

    /** Pre-trip plan days are running: the body clock is already shifting, departure in [days]. */
    data class Adjusting(val days: Long) : TripStatus

    /** Between the first take-off and the last landing. */
    data object InTheAir : TripStatus

    /** Day [number] after departure (1 = arrival day) of [total], or of an unknown total without a plan. */
    data class Day(val number: Int, val total: Int?) : TripStatus

    /** The plan has finished: the body clock is on destination time. */
    data object Adapted : TripStatus

    /**
     * The next trip took over before this one's plan finished (a return trip's pre-travel days began): the body
     * clock got [percent] of the way to destination time.
     */
    data class PartlyAdapted(val percent: Int) : TripStatus

    /** A short trip whose plan kept the body on home time. */
    data object StayedOnHomeTime : TripStatus

    /** Over, without a plan to say more. */
    data object Finished : TripStatus
}

/**
 * How far along adaptation is, for the wavy indicator.
 *
 * @property progress share of the planned shift completed, 0..1.
 * @property misalignment wave amplitude, remaining misalignment / initial (0 = flat).
 * @property remainingHours absolute hours the body clock is still off destination time.
 */
data class AdaptationSnapshot(val progress: Float, val misalignment: Float, val remainingHours: Float)

/**
 * The sky painted behind the trips list's app bar: the body clock of a trip under way (what time it is inside
 * you), otherwise the local time.
 */
data class TripsSky(val time: LocalTime, val bodyClock: Boolean)

/**
 * Everything a trip card shows, derived from a [Trip], its (optional) [JetLagPlan] and the current time.
 *
 * @property shiftHours destination minus origin UTC offset around the trip, normalised to (−12, 12]:
 *   positive = eastward.
 * @property flightProgress 0 before the first take-off, 1 after the last landing (drives the route banner).
 * @property daysToAdapt the planner's estimate of days to adapt, for upcoming trips whose plan shifts the body
 *   clock (null otherwise: trips under way show live progress instead).
 * @property bodyTime body-clock time now, for trips under way with a plan.
 * @property window when the trip is under way ([NowTrip.window]): what decides which trip is "now".
 * @property homeTime the plan keeps the body on home time (the planner's choice or the user's).
 * @property plan the trip's plan, if any (to say how far a trip got when a later one took over).
 */
data class TripSummary(
    val trip: Trip,
    val title: String,
    val phase: TripPhase,
    val status: TripStatus,
    val shiftHours: Float,
    val flightProgress: Float,
    val adaptation: AdaptationSnapshot?,
    val daysToAdapt: Double? = null,
    val daysSaved: Double = 0.0,
    val bodyTime: LocalTime? = null,
    val window: TripWindow = NowTrip.window(trip, plan = null),
    val homeTime: Boolean = false,
    val plan: JetLagPlan? = null,
) {
    val id: String get() = trip.id
}

/**
 * How trips split between the **Now** tab ([nowTrip]: the trip in progress, or else the first upcoming one) and
 * the **Trips** tab ([upcoming]: all following upcoming trips after [nowTrip], plus [past] below).
 */
data class PartitionedTrips(
    val nowTrip: TripSummary?,
    val upcoming: List<TripSummary>,
    val past: List<TripSummary>,
)

/** Pure derivation of [TripSummary]s, kept free of Android so it is unit-tested on the JVM. */
object TripSummaries {

    /**
     * @param today the user's current calendar date (device zone), for "Starts in N days".
     * @param fallbackTitle used when the trip has no title of its own (e.g. from `TripTitleSuggester`).
     */
    fun summarize(trip: Trip, plan: JetLagPlan?, now: Instant, today: LocalDate, fallbackTitle: String): TripSummary {
        val spans = plan?.daySpans().orEmpty()
        val window = NowTrip.window(trip, plan)
        val departureDate = trip.legs.first().departureLocal.toLocalDate()
        val daysToDeparture = ChronoUnit.DAYS.between(today, departureDate).coerceAtLeast(0)
        val homeTime = plan?.strategy == AdaptationStrategy.StayOnHomeTime

        // Phases follow the trip's window, the same one that decides the Now tab's trip (NowTrip).
        val (phase, status) = when {
            now.isBefore(window.start) -> TripPhase.Upcoming to TripStatus.StartsIn(daysToDeparture)
            now.isBefore(trip.departure) -> TripPhase.InProgress to TripStatus.Adjusting(daysToDeparture)
            now.isBefore(trip.arrival) -> TripPhase.InProgress to TripStatus.InTheAir
            now.isBefore(window.end) -> TripPhase.InProgress to if (plan != null && spans.isNotEmpty()) {
                // After the last day's midnight, the last night's sleep still belongs to the last day.
                val day = (spans.firstOrNull { now in it } ?: spans.last().takeIf { !now.isBefore(it.end) })?.day
                val total = spans.last().day.index.coerceAtLeast(1)
                TripStatus.Day((day?.index ?: 1).coerceAtLeast(1), total)
            } else {
                val arrivalDate = trip.legs.last().arrivalLocal.toLocalDate()
                TripStatus.Day((ChronoUnit.DAYS.between(arrivalDate, today).toInt() + 1).coerceAtLeast(1), null)
            }
            plan != null -> TripPhase.Past to if (homeTime) TripStatus.StayedOnHomeTime else TripStatus.Adapted
            else -> TripPhase.Past to TripStatus.Finished
        }

        val adaptation = if (phase == TripPhase.InProgress && plan != null && !homeTime) {
            val misalignmentHours = plan.misalignmentHoursAt(now).toFloat()
            AdaptationSnapshot(
                progress = plan.adaptationProgressAt(now),
                misalignment = misalignmentFraction(misalignmentHours, plan.shiftHours.toFloat()),
                remainingHours = abs(misalignmentHours),
            )
        } else {
            null
        }

        val shifts = plan != null && !homeTime && plan.direction != ShiftDirection.None
        val daysSaved = if (shifts) (plan.estimatedDaysWithoutPlan - plan.estimatedDaysToAdapt).coerceAtLeast(0.0) else 0.0
        return TripSummary(
            trip = trip,
            title = trip.title.ifBlank { fallbackTitle },
            phase = phase,
            status = status,
            shiftHours = shiftHours(trip),
            flightProgress = flightProgress(trip, now),
            adaptation = adaptation,
            daysToAdapt = plan?.estimatedDaysToAdapt?.takeIf { shifts && phase == TripPhase.Upcoming },
            daysSaved = daysSaved,
            bodyTime = plan?.takeIf { phase == TripPhase.InProgress }?.let { now.atOffset(it.bodyOffsetAt(now)).toLocalTime() },
            window = window,
            homeTime = homeTime,
            plan = plan,
        )
    }

    /**
     * Splits [summaries] between the **Now** tab and the **Trips** tab at [now]:
     * - [PartitionedTrips.nowTrip] is picked by [NowTrip], the rule `PlanRepository.currentPlan` uses: the trip under
     *   way (latest departure if windows overlap), or else the soonest upcoming trip.
     * - [PartitionedTrips.upcoming] holds all remaining upcoming trips in departure order (so the trip shown on
     *   Now is not duplicated on Trips).
     * - [PartitionedTrips.past] holds all past trips, plus any earlier trip under way that [PartitionedTrips.nowTrip]
     *   took over from, most recent first, with an honest status ([TripStatus.PartlyAdapted] if its plan hadn't
     *   finished).
     */
    fun partition(summaries: List<TripSummary>, now: Instant): PartitionedTrips {
        val nowTrip = NowTrip.pick(summaries, now, { it.trip }, { it.window })
        val others = summaries.filter { it.id != nowTrip?.id }
        val upcoming = others.mapNotNull { s ->
            when {
                s.phase == TripPhase.Upcoming -> s
                // Pre-travel days of a trip that isn't "now": it still lies ahead.
                s.status is TripStatus.Adjusting -> s.copy(
                    phase = TripPhase.Upcoming,
                    status = TripStatus.StartsIn(s.status.days),
                    adaptation = null,
                    bodyTime = null,
                )
                else -> null
            }
        }
        val past = others.mapNotNull { s ->
            when {
                s.phase == TripPhase.Past -> s
                s.phase == TripPhase.InProgress && s.status !is TripStatus.Adjusting -> s.copy(
                    phase = TripPhase.Past,
                    status = supersededStatus(s, takeover = nowTrip!!.window.start),
                    adaptation = null,
                    bodyTime = null,
                )
                else -> null
            }
        }
        return PartitionedTrips(
            nowTrip = nowTrip,
            upcoming = upcoming.sortedBy { it.trip.departure },
            past = past.sortedByDescending { it.trip.arrival },
        )
    }

    /**
     * Where a trip under way stood when the next trip took over from it at [takeover] (that trip's window start):
     * frozen there, so the card doesn't keep following a plan that no longer applies.
     */
    private fun supersededStatus(s: TripSummary, takeover: Instant): TripStatus {
        if (s.homeTime) return TripStatus.StayedOnHomeTime
        // Without a plan there's nothing to say about the body clock.
        val plan = s.plan ?: return TripStatus.Finished
        val progress = plan.adaptationProgressAt(takeover)
        val percent = (progress * 100).roundToInt()
        return if (percent >= 100) TripStatus.Adapted else TripStatus.PartlyAdapted(percent.coerceAtLeast(0))
    }

    /**
     * The list's app bar sky: the body clock of the trip under way, else local time in [zone]. When active plans
     * overlap (an outbound tail and a return's pre-travel days), the latest departure wins, as in the plan repository.
     */
    fun sky(summaries: List<TripSummary>, now: Instant, zone: ZoneId): TripsSky {
        val body = summaries
            .filter { it.phase == TripPhase.InProgress && it.bodyTime != null }
            .maxByOrNull { it.trip.departure }
            ?.bodyTime
        return if (body != null) TripsSky(body, bodyClock = true) else TripsSky(now.atZone(zone).toLocalTime(), bodyClock = false)
    }

    /** Destination minus origin offset, each taken at its own end of the trip, normalised to (−12, 12]. */
    fun shiftHours(trip: Trip): Float {
        val from = trip.origin.zone.rules.getOffset(trip.departure).totalSeconds
        val to = trip.destination.zone.rules.getOffset(trip.arrival).totalSeconds
        val day = 24 * 3600
        val diff = Math.floorMod(to - from + day / 2 - 1, day) - day / 2 + 1
        return diff / 3600f
    }

    /** Linear position between first take-off and last landing, clamped to 0..1. */
    fun flightProgress(trip: Trip, now: Instant): Float {
        val total = Duration.between(trip.departure, trip.arrival).toMillis()
        if (total <= 0) return if (now.isBefore(trip.departure)) 0f else 1f
        val done = Duration.between(trip.departure, now).toMillis()
        return (done.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Groups and orders summaries for the list: in progress and upcoming soonest first, past most recent first.
     */
    fun sections(summaries: List<TripSummary>): Map<TripPhase, List<TripSummary>> {
        val grouped = summaries.groupBy { it.phase }
        return TripPhase.entries.associateWith { phase ->
            val list = grouped[phase].orEmpty()
            if (phase == TripPhase.Past) list.sortedByDescending { it.trip.departure } else list.sortedBy { it.trip.departure }
        }
    }

    /** The trip a "Return trip" shortcut should reverse: the most recent one already under way or done. */
    fun returnCandidate(summaries: List<TripSummary>): TripSummary? =
        summaries.filter { it.phase != TripPhase.Upcoming }.maxByOrNull { it.trip.departure }
}
