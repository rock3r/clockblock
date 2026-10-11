package dev.sebastiano.clockblocker.opus.feature.trips.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GeoPoint
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeGeo
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeLeg
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeStop
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.RouteGlobe
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import dev.sebastiano.clockblocker.opus.feature.trips.ui.cityLabel
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Aggregated route and travel statistics across all completed trips, for the past-trips globe sheet.
 */
internal data class PastTripsStats(
    val tripsCount: Int,
    val flightsCount: Int,
    val airportsCount: Int,
    val totalDistanceKm: Int,
    val totalShiftHours: Float,
    val daysSaved: Int,
    val legs: List<GlobeLeg>,
    val legTripIds: List<String>,
    val legPlaces: List<Pair<Place, Place>>,
) {
    val isEmpty: Boolean get() = legs.isEmpty()

    companion object {
        fun of(past: List<TripSummary>): PastTripsStats {
            if (past.isEmpty()) {
                return PastTripsStats(
                    tripsCount = 0,
                    flightsCount = 0,
                    airportsCount = 0,
                    totalDistanceKm = 0,
                    totalShiftHours = 0f,
                    daysSaved = 0,
                    legs = emptyList(),
                    legTripIds = emptyList(),
                    legPlaces = emptyList(),
                )
            }
            val chronological = past.sortedBy { it.trip.departure }
            val drawn = chronological.flatMap { summary ->
                summary.trip.legs.mapNotNull { leg ->
                    if (leg.origin == leg.destination) null else Triple(summary.id, leg.origin, leg.destination)
                }
            }
            val airports = chronological
                .flatMap { it.trip.legs }
                .flatMap { listOf(it.origin.displayCode, it.destination.displayCode) }
                .toSet()
            val distanceKm = drawn.sumOf { (_, from, to) ->
                GlobeGeo.distanceKm(
                    GeoPoint(from.latitude, from.longitude),
                    GeoPoint(to.latitude, to.longitude),
                )
            }.roundToInt()
            val shiftHours = chronological.sumOf { abs(it.shiftHours).toDouble() }.toFloat()
            val saved = chronological.sumOf { it.daysSaved }.roundToInt().coerceAtLeast(0)

            return PastTripsStats(
                tripsCount = chronological.size,
                flightsCount = drawn.size,
                airportsCount = airports.size,
                totalDistanceKm = distanceKm,
                totalShiftHours = shiftHours,
                daysSaved = saved,
                legs = drawn.map { (_, from, to) -> GlobeLeg(from.toStop(), to.toStop()) },
                legTripIds = drawn.map { it.first },
                legPlaces = drawn.map { (_, from, to) -> from to to },
            )
        }

        private fun Place.toStop() = GlobeStop(GeoPoint(latitude, longitude), displayCode)
    }
}

/**
 * Modal bottom sheet launched from the "Past trips" section header: shows every past trip's flight arcs
 * crisscrossed across a single [RouteGlobe] plus summary travel statistics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PastTripsGlobeSheet(
    stats: PastTripsStats,
    now: Instant,
    onOpenTrip: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(TripsTestTags.PastGlobeSheet),
    ) {
        PastTripsGlobeSheetContent(
            stats = stats,
            now = now,
            onOpenTrip = { tripId ->
                onDismiss()
                onOpenTrip(tripId)
            },
        )
    }
}

/** Content of [PastTripsGlobeSheet], separated so screenshot and Compose tests can render it directly. */
@Composable
internal fun PastTripsGlobeSheetContent(
    stats: PastTripsStats,
    now: Instant,
    onOpenTrip: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val tripsLabel = pluralStringResource(R.plurals.past_globe_trips, stats.tripsCount, stats.tripsCount)
    val flightsLabel = pluralStringResource(R.plurals.past_globe_flights, stats.flightsCount, stats.flightsCount)
    val subtitle = stringResource(R.string.past_globe_subtitle, tripsLabel, flightsLabel)
    val distanceFormatted = numberFormat.format(stats.totalDistanceKm)
    val airportsLabel = pluralStringResource(R.plurals.past_globe_stat_airports_label, stats.airportsCount)
    val description = stringResource(
        R.string.past_globe_description,
        tripsLabel,
        "${stats.airportsCount} ${airportsLabel.lowercase(Locale.getDefault())}",
        distanceFormatted,
    )
    val actionTemplate = stringResource(R.string.past_globe_leg_action)
    val legLabels = remember(stats, actionTemplate) {
        stats.legPlaces.map { (from, to) -> actionTemplate.format(from.cityLabel, to.cityLabel) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.past_globe_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        RouteGlobe(
            legs = stats.legs,
            instant = now,
            contentDescription = description,
            showLabels = true,
            onLegClick = { index -> onOpenTrip(stats.legTripIds[index]) },
            legActionLabel = { legLabels[it] },
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .testTag(TripsTestTags.PastGlobe),
        )
        Spacer(Modifier.height(14.dp))
        PastTripsStatsGrid(stats = stats, numberFormat = numberFormat)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.past_globe_tap_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PastTripsStatsGrid(stats: PastTripsStats, numberFormat: NumberFormat) {
    Column(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile(
                value = stringResource(R.string.past_globe_stat_shift_value, formatTotalShiftHours(stats.totalShiftHours)),
                label = stringResource(R.string.past_globe_stat_shift_label),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                value = pluralStringResource(R.plurals.past_globe_stat_saved_value, stats.daysSaved, stats.daysSaved),
                label = stringResource(R.string.past_globe_stat_saved_label),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = ClockblockTheme.textStyles.timeTitle,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTotalShiftHours(hours: Float): String {
    val rounded = hours.roundToInt()
    return if (abs(hours - rounded) < 0.05f) {
        rounded.toString()
    } else {
        "%.1f".format(Locale.getDefault(), hours)
    }
}
