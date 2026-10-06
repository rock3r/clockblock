package dev.sebastiano.clockblocker.opus.core.circadian

import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import java.time.Instant
import java.time.ZoneId

/**
 * The zones a plan's times are shown in at one instant: [local] is "local time", [secondary] the other end of the
 * trip shown next to it (product rule: times show local time plus a secondary zone).
 */
data class PlanDisplayZones(val local: ZoneId, val secondary: ZoneId)

/**
 * The zone the plan expects the traveller to be in at [instant]: the zone of the plan day containing it (see
 * [daySpans]), switching to the next day's zone (the destination) at the travel day's landing. A morning arrival
 * ends the travel day there anyway; an evening arrival keeps the travel day until midnight, but the traveller is
 * on destination time from the landing. Before the plan it is the first day's zone, after it the last day's, and
 * the destination for a plan without days.
 *
 * This is what "local time" means on every surface: the plan screen, the widgets and the notifications all read
 * it from here, never from the device's zone, so they agree even when the phone's zone is somewhere else (a demo
 * trip, a phone on a manual zone, a pre-trip day away from home).
 */
fun JetLagPlan.localZoneAt(instant: Instant): ZoneId {
    val spans = daySpans()
    val index = spans.indexOfFirst { instant in it }
    val day = when {
        index >= 0 -> {
            val span = spans[index]
            val landed = span.day.takeIf { it.kind == DayKind.Travel }
                ?.advice?.filter { it.type == AdviceType.Flight }?.maxOfOrNull { it.end }
            if (landed != null && !instant.isBefore(landed)) {
                return ZoneId.of(spans.getOrNull(index + 1)?.day?.zoneId ?: destinationZoneId)
            }
            span.day
        }
        else -> spans.firstOrNull()?.takeIf { instant.isBefore(it.start) }?.day ?: spans.lastOrNull()?.day
    }
    return ZoneId.of(day?.zoneId ?: destinationZoneId)
}

/**
 * The zone shown next to times in [local]: the destination, or home once local time is the destination's. Equal
 * to [local] only when the trip starts and ends in the same zone; surfaces may then leave it out.
 */
fun JetLagPlan.secondaryZoneFor(local: ZoneId): ZoneId =
    ZoneId.of(if (local.id == destinationZoneId) originZoneId else destinationZoneId)

/** [localZoneAt] and its [secondaryZoneFor] at [instant]. */
fun JetLagPlan.displayZonesAt(instant: Instant): PlanDisplayZones {
    val local = localZoneAt(instant)
    return PlanDisplayZones(local, secondaryZoneFor(local))
}
