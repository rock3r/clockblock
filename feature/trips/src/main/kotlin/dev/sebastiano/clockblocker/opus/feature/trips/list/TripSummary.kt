package dev.sebastiano.clockblocker.opus.feature.trips.list

import dev.sebastiano.clockblocker.opus.core.circadian.adaptationProgressAt
import dev.sebastiano.clockblocker.opus.core.circadian.daySpans
import dev.sebastiano.clockblocker.opus.core.designsystem.component.misalignmentFraction
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Trip
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

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
 * Everything a trip card shows, derived from a [Trip], its (optional) [JetLagPlan] and the current time.
 *
 * @property shiftHours destination minus origin UTC offset around the trip, normalised to (−12, 12]:
 *   positive = eastward.
 * @property flightProgress 0 before the first take-off, 1 after the last landing (drives the great-circle art).
 */
data class TripSummary(
    val trip: Trip,
    val title: String,
    val phase: TripPhase,
    val status: TripStatus,
    val shiftHours: Float,
    val flightProgress: Float,
    val adaptation: AdaptationSnapshot?,
) {
    val id: String get() = trip.id
}

/** Pure derivation of [TripSummary]s, kept free of Android so it is unit-tested on the JVM. */
object TripSummaries {

    /** Without a plan, a trip counts as in progress for this long after landing. */
    val UnplannedSettlingTime: Duration = Duration.ofDays(2)

    /**
     * @param today the user's current calendar date (device zone), for "Starts in N days".
     * @param fallbackTitle used when the trip has no title of its own (e.g. from `TripTitleSuggester`).
     */
    fun summarize(trip: Trip, plan: JetLagPlan?, now: Instant, today: LocalDate, fallbackTitle: String): TripSummary {
        val spans = plan?.daySpans().orEmpty()
        val planStart = spans.firstOrNull()?.start
        val planEnd = spans.lastOrNull()?.end
        val departureDate = trip.legs.first().departureLocal.toLocalDate()
        val daysToDeparture = ChronoUnit.DAYS.between(today, departureDate).coerceAtLeast(0)
        val homeTime = plan?.strategy == AdaptationStrategy.StayOnHomeTime

        val (phase, status) = when {
            now.isBefore(trip.departure) -> when {
                planStart != null && !now.isBefore(planStart) ->
                    TripPhase.InProgress to TripStatus.Adjusting(daysToDeparture)
                else -> TripPhase.Upcoming to TripStatus.StartsIn(daysToDeparture)
            }

            now.isBefore(trip.arrival) -> TripPhase.InProgress to TripStatus.InTheAir

            plan != null && planEnd != null -> {
                if (now.isBefore(planEnd)) {
                    val day = spans.firstOrNull { now in it }?.day
                    val total = spans.last().day.index.coerceAtLeast(1)
                    TripPhase.InProgress to TripStatus.Day((day?.index ?: 1).coerceAtLeast(1), total)
                } else {
                    TripPhase.Past to if (homeTime) TripStatus.StayedOnHomeTime else TripStatus.Adapted
                }
            }

            now.isBefore(trip.arrival.plus(UnplannedSettlingTime)) -> {
                val arrivalDate = trip.legs.last().arrivalLocal.toLocalDate()
                val number = ChronoUnit.DAYS.between(arrivalDate, today).toInt() + 1
                TripPhase.InProgress to TripStatus.Day(number.coerceAtLeast(1), null)
            }

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

        return TripSummary(
            trip = trip,
            title = trip.title.ifBlank { fallbackTitle },
            phase = phase,
            status = status,
            shiftHours = shiftHours(trip),
            flightProgress = flightProgress(trip, now),
            adaptation = adaptation,
        )
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
