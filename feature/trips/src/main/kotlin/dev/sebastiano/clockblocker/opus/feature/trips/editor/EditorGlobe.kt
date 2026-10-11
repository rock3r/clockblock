package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeLeg
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeStop
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.RouteGlobe
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.RouteGlobeDefaults
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import dev.sebastiano.clockblocker.opus.feature.trips.ui.cityLabel
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The route as the globe sees it: the legs that have both airports, with the index of each in the editor's list
 * (a leg still missing an airport isn't drawn, so the two can differ).
 */
internal data class EditorRoute(
    val legs: List<GlobeLeg>,
    val legIndices: List<Int>,
    /** The airports of each drawn leg, from and to. */
    val legPlaces: List<Pair<Place, Place>>,
    /** The stops in route order, a connection once. */
    val places: List<Place>,
) {
    val isEmpty: Boolean get() = legs.isEmpty()

    /** The globe's index for editor leg [legIndex], or null when that leg isn't drawn. */
    fun globeIndexOf(legIndex: Int): Int? = legIndices.indexOf(legIndex).takeIf { it >= 0 }

    companion object {
        fun of(legs: List<LegDraft>): EditorRoute {
            val drawn = legs.withIndex().mapNotNull { (i, leg) ->
                val from = leg.origin.place ?: return@mapNotNull null
                val to = leg.destination.place ?: return@mapNotNull null
                if (from == to) null else Triple(i, from, to)
            }
            val places = buildList {
                drawn.forEach { (_, from, to) ->
                    if (lastOrNull() != from) add(from)
                    add(to)
                }
            }
            return EditorRoute(
                legs = drawn.map { (_, from, to) -> GlobeLeg(from.toStop(), to.toStop()) },
                legIndices = drawn.map { it.first },
                legPlaces = drawn.map { (_, from, to) -> from to to },
                places = places,
            )
        }

        private fun Place.toStop() = GlobeStop(GeoPoint(latitude, longitude), displayCode)
    }
}

/**
 * The trip in words for TalkBack: "Lisbon to Tokyo via Doha, 9 hours east" (the shift once the planner has it).
 */
@Composable
internal fun routeDescription(route: EditorRoute, summary: ShiftSummary?): String {
    val cities = route.places.map { it.cityLabel }
    val trip = when {
        cities.size < 2 -> cities.firstOrNull().orEmpty()
        cities.size == 2 -> stringResource(R.string.globe_route, cities.first(), cities.last())
        else -> stringResource(R.string.globe_route_via, cities.first(), cities.last(), cities.subList(1, cities.size - 1).joinToString(", "))
    }
    val shift = (summary as? ShiftSummary.Shift)?.let { s ->
        val hours = abs(s.shiftHours)
        val whole = hours.roundToInt()
        val text = if (abs(hours - whole) < 0.01f) {
            pluralStringResource(if (s.east) R.plurals.globe_hours_east else R.plurals.globe_hours_west, whole, whole)
        } else {
            stringResource(if (s.east) R.string.globe_hours_east_fraction else R.string.globe_hours_west_fraction, hours)
        }
        text
    }
    return listOfNotNull(trip.takeIf { it.isNotBlank() }, shift).joinToString(", ")
}

/**
 * The globe in the Your shift card: small, no labels. A tap on another flight's arc picks that flight's ticket
 * ([onLegClick] with the editor's leg index); a tap anywhere else, including on the active flight (whose arc runs
 * through the middle of the framed view, where people tap to open it), opens the full-size view ([onExpand]).
 * TalkBack's per-flight actions always go to the ticket.
 */
@Composable
internal fun EditorGlobe(
    route: EditorRoute,
    description: String,
    now: Instant,
    activeLeg: Int,
    onLegClick: (Int) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = route.globeIndexOf(activeLeg).takeIf { route.legs.size > 1 }
    RouteGlobe(
        legs = route.legs,
        instant = now,
        contentDescription = description,
        activeLeg = active,
        onLegClick = { onLegClick(route.legIndices[it]) },
        onLegTap = { if (it == active || route.legs.size == 1) onExpand() else onLegClick(route.legIndices[it]) },
        onClick = onExpand,
        onClickLabel = stringResource(R.string.globe_expand),
        legActionLabel = legLabels(route)::get,
        modifier = modifier.size(RouteGlobeDefaults.SmallSize).testTag(TripsTestTags.EditorGlobe),
    )
}

/** "Go to flight 2: Doha to Tokyo" for each drawn leg, for TalkBack's custom actions on the globe. */
@Composable
private fun legLabels(route: EditorRoute): List<String> {
    val template = stringResource(R.string.globe_leg_action)
    return remember(route, template) {
        route.legs.mapIndexed { i, _ ->
            val (from, to) = route.legPlaces[i]
            template.format(route.legIndices[i] + 1, from.cityLabel, to.cityLabel)
        }
    }
}

/**
 * The full-size globe in a sheet (predictive back comes with it): the airport codes beside their dots, on a scrim.
 * Tapping a flight closes the sheet and goes to its ticket.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobeSheet(
    route: EditorRoute,
    description: String,
    now: Instant,
    activeLeg: Int,
    onLegClick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = Modifier.testTag(TripsTestTags.EditorGlobeSheet)) {
        GlobeSheetContent(route, description, now, activeLeg, onLegClick)
    }
}

/** The sheet's content, on its own for screenshots. */
@Composable
internal fun GlobeSheetContent(
    route: EditorRoute,
    description: String,
    now: Instant,
    activeLeg: Int,
    onLegClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = legLabels(route)
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.globe_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        RouteGlobe(
            legs = route.legs,
            instant = now,
            contentDescription = description,
            activeLeg = route.globeIndexOf(activeLeg).takeIf { route.legs.size > 1 },
            showLabels = true,
            onLegClick = { onLegClick(route.legIndices[it]) },
            legActionLabel = { labels[it] },
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().aspectRatio(1f).testTag(TripsTestTags.EditorGlobeFull),
        )
        if (route.legs.size > 1) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.globe_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
