package dev.sebastiano.clockblocker.opus.feature.trips.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GeoPoint
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeCallout
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeColors
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeGeo
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeInteraction
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeLeg
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeOverlay
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeSelection
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeStop
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.RouteGlobe
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.rememberRouteGlobeState
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import dev.sebastiano.clockblocker.opus.feature.trips.ui.cityLabel
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Aggregated route and travel statistics across all completed trips, for the past-trips globe.
 *
 * @property hoursOfTimeDifference the time difference of every flight added up (each flight's change of clock,
 *   however it nets out over the trip: a round trip to a place 8 hours ahead counts 16).
 */
internal data class PastTripsStats(
    val tripsCount: Int,
    val flightsCount: Int,
    val airportsCount: Int,
    val totalDistanceKm: Int,
    val hoursOfTimeDifference: Float,
    val daysSaved: Int,
    val legs: List<GlobeLeg>,
    val legTripIds: List<String>,
    val legPlaces: List<Pair<Place, Place>>,
    /** The local date each drawn flight left on. */
    val legDates: List<LocalDate>,
) {
    val isEmpty: Boolean get() = legs.isEmpty()

    companion object {
        fun of(past: List<TripSummary>): PastTripsStats {
            val chronological = past.sortedBy { it.trip.departure }
            val drawn = chronological.flatMap { summary ->
                summary.trip.legs.filter { it.origin != it.destination }.map { summary.id to it }
            }
            val airports = chronological
                .flatMap { it.trip.legs }
                .flatMap { listOf(it.origin.displayCode, it.destination.displayCode) }
                .toSet()
            val distanceKm = drawn.sumOf { (_, leg) -> GlobeGeo.distanceKm(leg.origin.geo(), leg.destination.geo()) }.roundToInt()
            val saved = chronological.sumOf { it.daysSaved }.roundToInt().coerceAtLeast(0)

            return PastTripsStats(
                tripsCount = chronological.size,
                flightsCount = drawn.size,
                airportsCount = airports.size,
                totalDistanceKm = distanceKm,
                hoursOfTimeDifference = chronological.sumOf { hoursOfTimeDifference(it.trip).toDouble() }.toFloat(),
                daysSaved = saved,
                legs = drawn.map { (_, leg) -> GlobeLeg(leg.origin.toStop(), leg.destination.toStop()) },
                legTripIds = drawn.map { it.first },
                legPlaces = drawn.map { (_, leg) -> leg.origin to leg.destination },
                legDates = drawn.map { (_, leg) -> leg.departureLocal.toLocalDate() },
            )
        }

        private fun Place.geo() = GeoPoint(latitude, longitude)

        private fun Place.toStop() = GlobeStop(geo(), displayCode, cityLabel)
    }
}

/**
 * The time difference [trip]'s flights crossed, in hours: for each flight, how far the destination's clock was from
 * the origin's when it flew (from the IANA zones, so DST and half-hour zones count), added up regardless of direction.
 */
internal fun hoursOfTimeDifference(trip: Trip): Float = trip.legs.sumOf { it.timeDifferenceHours() }.toFloat()

private fun FlightLeg.timeDifferenceHours(): Double {
    val from = origin.zone.rules.getOffset(departure).totalSeconds
    val to = destination.zone.rules.getOffset(arrival).totalSeconds
    return abs(to - from) / 3600.0
}

/**
 * The past-trips easter egg (tap the Past trips heading): every past flight crisscrossed on one big globe, edge to
 * edge over the Trips screen (or its pane), with the stats under it (beside it in a wide window). The globe turns,
 * zooms and names a tapped flight or airport; a picked flight offers Open trip. TalkBack: one action per flight opens
 * its trip, plus the globe's own turn and zoom actions.
 */
@Composable
internal fun PastTripsGlobeOverlay(
    stats: PastTripsStats,
    now: Instant,
    visible: Boolean,
    onOpenTrip: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberRouteGlobeState()
    LaunchedEffect(visible) {
        if (visible) {
            state.selection = null
            state.recentre(null)
        }
    }
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val tripsLabel = pluralStringResource(R.plurals.past_globe_trips, stats.tripsCount, stats.tripsCount)
    val flightsLabel = pluralStringResource(R.plurals.past_globe_flights, stats.flightsCount, stats.flightsCount)
    val airportsLabel = pluralStringResource(R.plurals.past_globe_stat_airports_label, stats.airportsCount)
    val description = stringResource(
        R.string.past_globe_description,
        tripsLabel,
        "${stats.airportsCount} ${airportsLabel.lowercase(Locale.getDefault())}",
        numberFormat.format(stats.totalDistanceKm),
    )
    val actionTemplate = stringResource(R.string.past_globe_leg_action)
    val legLabels = remember(stats, actionTemplate) { stats.legPlaces.map { (from, to) -> actionTemplate.format(from.cityLabel, to.cityLabel) } }
    val nameTemplate = stringResource(R.string.past_globe_leg_name)
    val month = remember { DateTimeFormatter.ofPattern("LLL yyyy", Locale.getDefault()) }
    val legs = remember(stats, nameTemplate) {
        stats.legs.mapIndexed { i, leg ->
            val (from, to) = stats.legPlaces[i]
            leg.copy(name = nameTemplate.format(from.cityLabel, to.cityLabel, month.format(stats.legDates[i])))
        }
    }
    val open: (String) -> Unit = { tripId ->
        onDismiss()
        onOpenTrip(tripId)
    }

    GlobeOverlay(
        visible = visible,
        onDismiss = onDismiss,
        title = stringResource(R.string.past_globe_title),
        subtitle = stringResource(R.string.past_globe_subtitle, tripsLabel, flightsLabel),
        state = state,
        modifier = modifier.testTag(TripsTestTags.PastGlobeOverlay),
        globe = { globeModifier ->
            RouteGlobe(
                legs = legs,
                instant = now,
                contentDescription = description,
                showLabels = true,
                onLegClick = { index -> open(stats.legTripIds[index]) },
                onLegTap = {},
                legActionLabel = { legLabels[it] },
                state = state,
                interaction = GlobeInteraction.RotateAndZoom,
                modifier = globeModifier.testTag(TripsTestTags.PastGlobe),
            )
        },
        callout = { calloutModifier ->
            GlobeCallout(state, legs, calloutModifier) { selection ->
                if (selection is GlobeSelection.Leg) {
                    TextButton(onClick = { open(stats.legTripIds[selection.index]) }, modifier = Modifier.testTag(TripsTestTags.PastGlobeOpenTrip)) {
                        Text(stringResource(R.string.past_globe_open_trip), color = GlobeColors.current.route)
                    }
                }
            }
        },
        details = { detailsModifier -> PastTripsStatsPanel(stats, numberFormat, detailsModifier) },
    )
}

/** The stats on the globe's night sky: four tiles on the globe's scrim, and how to use the globe. */
@Composable
internal fun PastTripsStatsPanel(stats: PastTripsStats, numberFormat: NumberFormat, modifier: Modifier = Modifier) {
    val colors = GlobeColors.current
    Column(modifier.testTag(TripsTestTags.PastGlobeStats), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                value = numberFormat.format(stats.airportsCount),
                label = pluralStringResource(R.plurals.past_globe_stat_airports_label, stats.airportsCount),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                value = stringResource(R.string.past_globe_stat_distance_value, numberFormat.format(stats.totalDistanceKm)),
                label = stringResource(R.string.past_globe_stat_distance_label),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                value = stringResource(R.string.past_globe_stat_shift_value, formatHours(stats.hoursOfTimeDifference)),
                label = stringResource(R.string.past_globe_stat_shift_label),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                value = pluralStringResource(R.plurals.past_globe_stat_saved_value, stats.daysSaved, stats.daysSaved),
                label = stringResource(R.string.past_globe_stat_saved_label),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Text(
            stringResource(R.string.past_globe_tap_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.label.copy(alpha = 0.86f),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = GlobeColors.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.scrim,
        contentColor = colors.label,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = ClockblockTheme.textStyles.timeTitle, color = colors.route)
            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.label.copy(alpha = 0.86f))
        }
    }
}

private fun formatHours(hours: Float): String {
    val rounded = hours.roundToInt()
    return if (abs(hours - rounded) < 0.05f) rounded.toString() else "%.1f".format(Locale.getDefault(), hours)
}
