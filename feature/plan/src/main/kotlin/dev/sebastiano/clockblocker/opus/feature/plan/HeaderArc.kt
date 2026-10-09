package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.runtime.Immutable
import dev.sebastiano.clockblocker.opus.core.designsystem.component.celestialPosition
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyPalette
import dev.sebastiano.clockblocker.opus.core.model.Sun
import dev.sebastiano.clockblocker.opus.core.model.SunDay
import dev.sebastiano.clockblocker.opus.core.model.Trip
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * The sun's day where the plan's local time is (issue #21): sunrise and sunset as hours of the local day. The header
 * paints its sky, its sun / moon and their path for this day, so the body-clock sun and the local-time ghost share
 * one arc: the same hour lands on the same spot.
 */
@Immutable
internal data class HeaderDaylight(val sunriseHour: Float, val sunsetHour: Float) {
    companion object {
        /** The sky's schematic day, for plans without a place (and polar days, see [of]). */
        val Default = HeaderDaylight(SkyPalette.DefaultSunrise, SkyPalette.DefaultSunset)

        /**
         * From the real sun ([Sun.on]) in [zone]. The header's sky and arc need a sunrise before the sunset on the same
         * local day, so the midnight sun, polar night and a sunset past midnight keep the [Default] day (the dial's
         * sky rings show those as they are).
         */
        fun of(sun: SunDay?, zone: ZoneId): HeaderDaylight {
            if (sun !is SunDay.RisesAndSets) return Default
            val sunrise = hourOf(sun.sunrise, zone)
            val sunset = hourOf(sun.sunset, zone)
            return if (sunset > sunrise) HeaderDaylight(sunrise, sunset) else Default
        }

        private fun hourOf(instant: Instant, zone: ZoneId): Float =
            instant.atZone(zone).toLocalTime().toSecondOfDay() / 3600f
    }
}

/** The daylight of the [trip]'s place in the [moment]'s zone, on the moment's local date ([HeaderDaylight.Default] without one). */
internal fun headerDaylight(trip: Trip?, moment: PlanMoment): HeaderDaylight {
    val place = trip?.placeIn(moment.zone, moment.instant) ?: return HeaderDaylight.Default
    val date = moment.instant.atZone(moment.zone).toLocalDate()
    return HeaderDaylight.of(Sun.on(date, moment.zone, place.latitude, place.longitude), moment.zone)
}

/** A point on the header's arc: [t] from 0 (rising, at the start) to 1 (setting, at the end), on the day or night half. */
internal data class ArcPoint(val t: Float, val isSun: Boolean)

/** Where [hour] (of the day) sits on the arc: the sun rides it from sunrise to sunset, the moon from sunset to sunrise. */
internal fun HeaderDaylight.arcPoint(hour: Float): ArcPoint {
    val position = celestialPosition(hour, sunriseHour, sunsetHour)
    return ArcPoint(((position.x - 0.1f) / 0.8f).coerceIn(0f, 1f), position.isSun)
}

/** The ghost and the body's sun / moon are joined from this far apart (hours), so a few minutes' drift draws nothing. */
internal const val GhostJoinHours = 0.5f

/**
 * Whether the header joins the body's sun / moon (at [bodyHour]) to the local-time ghost (at [localHour]) with a faint
 * arc: both on the same half of the day, at least [GhostJoinHours] apart. Across sunrise or sunset they ride
 * different halves of the same path, so a join would cut across the day.
 */
internal fun HeaderDaylight.joins(bodyHour: Float, localHour: Float): Boolean {
    if (arcPoint(bodyHour).isSun != arcPoint(localHour).isSun) return false
    val apart = abs(bodyHour - localHour).mod(24f)
    return minOf(apart, 24f - apart) >= GhostJoinHours
}

/**
 * Where the ghost ring sits on the arc ([ArcPoint.t]). The arc shows the half of the day the body's sun / moon is on,
 * so a wall clock on the same half sits at its own spot. On the other half it waits at the nearer end, past which
 * it is (before sunrise, at the start of the day; after sunset, at its end), rather than at its own spot on the
 * other half, which would put a wall clock minutes before sunrise at the far end of the day.
 */
internal fun HeaderDaylight.ghostT(bodyHour: Float, localHour: Float): Float {
    val body = arcPoint(bodyHour)
    val local = arcPoint(localHour)
    if (body.isSun == local.isSun) return local.t
    val (start, end) = if (body.isSun) sunriseHour to sunsetHour else sunsetHour to sunriseHour
    val beforeStart = (start - localHour).mod(24f)
    val afterEnd = (localHour - end).mod(24f)
    return if (afterEnd < beforeStart) 1f else 0f
}
