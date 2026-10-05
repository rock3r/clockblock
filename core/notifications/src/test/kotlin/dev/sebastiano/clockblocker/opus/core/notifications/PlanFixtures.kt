package dev.sebastiano.clockblocker.opus.core.notifications

import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import java.time.Instant
import java.time.LocalDate

/** `"2026-10-10T18:00"` → instant at UTC. Keeps test plans readable. */
fun utc(iso: String): Instant = Instant.parse(if (iso.endsWith("Z")) iso else "${iso}:00Z")

fun advice(
    type: AdviceType,
    start: String,
    end: String = start,
    id: String = "${type.name}@$start",
    detail: String? = null,
): Advice = Advice(
    id = id,
    type = type,
    start = utc(start),
    end = utc(end),
    reason = AdviceReason.ShiftedSleep,
    detail = detail,
)

/** A plan with all [advice] on one travel day (days don't matter to notifications, only instants do). */
fun planOf(
    vararg advice: Advice,
    tripId: String = "trip-1",
    origin: String = "Europe/London",
    destination: String = "Asia/Tokyo",
): JetLagPlan = planOfDays(listOf(advice.toList()), tripId, origin, destination)

fun planOfDays(
    days: List<List<Advice>>,
    tripId: String = "trip-1",
    origin: String = "Europe/London",
    destination: String = "Asia/Tokyo",
): JetLagPlan = JetLagPlan(
    tripId = tripId,
    generatedAt = Instant.EPOCH,
    strategy = AdaptationStrategy.Adapt,
    direction = ShiftDirection.Advance,
    shiftHours = 8.0,
    originZoneId = origin,
    destinationZoneId = destination,
    days = days.mapIndexed { index, list ->
        PlanDay(index, DayKind.Travel, LocalDate.of(2026, 10, 10).plusDays(index.toLong()), destination, list)
    },
    phase = emptyList(),
    estimatedDaysToAdapt = 3.0,
    estimatedDaysWithoutPlan = 7.0,
)
