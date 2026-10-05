package dev.sebastiano.clockblocker.opus.core.notifications.now

import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import java.time.Duration
import java.time.Instant

/** The span of a travel day the Live Update tracks: getting to the airport → flights → arriving at the hotel. */
data class TravelWindow(val start: Instant, val end: Instant, val flights: List<Advice>) {
    operator fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)
    val minutes: Int get() = Duration.between(start, end).toMinutes().toInt()
}

/** A coloured stretch of the progress bar; `type == null` is a neutral gap with no advice. */
data class ProgressBlock(val type: AdviceType?, val minutes: Int)

enum class MarkKind { TakeOff, Landing }

/** A point on the progress bar, in minutes from the window start. */
data class ProgressMark(val kind: MarkKind, val minute: Int)

/** Everything a `ProgressStyle` needs, in whole minutes (sum of [blocks] == [totalMinutes]). */
data class TravelProgress(
    val totalMinutes: Int,
    val progressMinutes: Int,
    val blocks: List<ProgressBlock>,
    val marks: List<ProgressMark>,
)

/** The status-bar chip of a Live Update. */
sealed interface LiveChip {
    /** A ticking countdown to [until] (chronometer, so it costs no updates). */
    data class Countdown(val until: Instant) : LiveChip

    /** A static end time ("18:00") when a countdown would be noise. */
    data class EndsAt(val until: Instant) : LiveChip
}

/**
 * Pure travel-day Live Update logic.
 *
 * Policy (Live Updates must be ongoing, user-initiated and time-sensitive; upcoming events don't qualify): a
 * Live Update is shown only inside the [TravelWindow] **and** while a protocol window (anything but the bare
 * flight marker) is active. Otherwise the standard ongoing notification is used.
 */
object TravelPlanner {

    /** Before the first take-off: getting to and through the airport. */
    val PRE_DEPARTURE: Duration = Duration.ofHours(3)

    /** After the last landing: immigration, bags, getting to the hotel. */
    val POST_ARRIVAL: Duration = Duration.ofHours(2)

    /** Platform limits of `Notification.ProgressStyle`. */
    const val MAX_SEGMENTS: Int = 15
    const val MAX_POINTS: Int = 4

    /** A countdown chip is used for the last hour of a window; longer remaining times show the end time. */
    val COUNTDOWN_THRESHOLD: Duration = Duration.ofHours(1)

    fun window(plan: JetLagPlan): TravelWindow? {
        val flights = plan.allAdvice.filter { it.type == AdviceType.Flight }.distinctBy { it.id }.sortedBy { it.start }
        if (flights.isEmpty()) return null
        return TravelWindow(
            start = flights.first().start.minus(PRE_DEPARTURE),
            end = flights.maxOf { it.end }.plus(POST_ARRIVAL),
            flights = flights,
        )
    }

    /** Whether the travel-day Live Update should be shown at [now] (see the class docs for the policy). */
    fun isLiveUpdateActive(plan: JetLagPlan, now: Instant): Boolean {
        val window = window(plan) ?: return false
        if (now !in window) return false
        val headline = NowStateCalculator.headline(plan, now) ?: return false
        return headline.type != AdviceType.Flight
    }

    /** The progress bar at [now], or `null` outside the travel window. */
    fun progress(plan: JetLagPlan, now: Instant): TravelProgress? {
        val window = window(plan) ?: return null
        if (now !in window) return null
        val total = window.minutes
        return TravelProgress(
            totalMinutes = total,
            progressMinutes = minutesFrom(window.start, now).coerceIn(0, total),
            blocks = capSegments(blocks(plan, window)),
            marks = marks(window),
        )
    }

    /** Countdown in the last [COUNTDOWN_THRESHOLD] of a window, otherwise a static end time. */
    fun chip(until: Instant, now: Instant): LiveChip =
        if (Duration.between(now, until) <= COUNTDOWN_THRESHOLD) LiveChip.Countdown(until) else LiveChip.EndsAt(until)

    /** Partition the window by headline advice (same priority rules as the Now notification). */
    internal fun blocks(plan: JetLagPlan, window: TravelWindow): List<ProgressBlock> {
        val cuts = (
            plan.allAdvice.filter { !it.type.isMoment }.flatMap { listOf(it.start, it.end) }
                .filter { it.isAfter(window.start) && it.isBefore(window.end) } + window.start + window.end
            ).distinct().sorted()
        return cuts.zipWithNext { from, to ->
            // Measured cumulatively from the window start, so lengths always add up to the window exactly.
            val minutes = minutesFrom(window.start, to) - minutesFrom(window.start, from)
            ProgressBlock(NowStateCalculator.headline(plan, from)?.type, minutes)
        }
            .filter { it.minutes > 0 }
            .fold(mutableListOf<ProgressBlock>()) { acc, block ->
                val last = acc.lastOrNull()
                if (last != null && last.type == block.type) acc[acc.lastIndex] = last.copy(minutes = last.minutes + block.minutes)
                else acc += block
                acc
            }
    }

    /** Take-off and landing of every leg, capped at [MAX_POINTS] keeping the first take-off and last landing. */
    internal fun marks(window: TravelWindow): List<ProgressMark> {
        val all = window.flights.flatMap {
            listOf(
                ProgressMark(MarkKind.TakeOff, minutesFrom(window.start, it.start)),
                ProgressMark(MarkKind.Landing, minutesFrom(window.start, it.end)),
            )
        }
        if (all.size <= MAX_POINTS) return all
        return listOf(all.first()) + all.subList(1, all.lastIndex).take(MAX_POINTS - 2) + all.last()
    }

    /** Merge the shortest block into its longer neighbour until the platform segment limit is met. */
    internal fun capSegments(blocks: List<ProgressBlock>): List<ProgressBlock> {
        val result = blocks.toMutableList()
        while (result.size > MAX_SEGMENTS) {
            val i = result.indices.minBy { result[it].minutes }
            val neighbour = listOfNotNull(
                (i - 1).takeIf { it >= 0 },
                (i + 1).takeIf { it <= result.lastIndex },
            ).maxBy { result[it].minutes }
            result[neighbour] = result[neighbour].copy(minutes = result[neighbour].minutes + result[i].minutes)
            result.removeAt(i)
            // Re-merge equal neighbours created by the removal.
            var j = 0
            while (j < result.lastIndex) {
                if (result[j].type == result[j + 1].type) {
                    result[j] = result[j].copy(minutes = result[j].minutes + result[j + 1].minutes)
                    result.removeAt(j + 1)
                } else j++
            }
        }
        return result
    }

    private fun minutesFrom(from: Instant, to: Instant): Int = Duration.between(from, to).toMinutes().toInt()
}
