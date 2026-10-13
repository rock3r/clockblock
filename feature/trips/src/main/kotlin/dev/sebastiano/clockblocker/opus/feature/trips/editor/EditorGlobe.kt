package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GeoPoint
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeCallout
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeColors
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeInteraction
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeLeg
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeOverlay
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeSelection
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeStop
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.RouteGlobe
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.RouteGlobeDefaults
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.rememberRouteGlobeState
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import dev.sebastiano.clockblocker.opus.feature.trips.ui.cityLabel
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeOverlayCover

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

        private fun Place.toStop() = GlobeStop(GeoPoint(latitude, longitude), displayCode, cityLabel)
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
 * The editor's globe full screen, over the editor (or its pane): the airport codes beside their dots, on a scrim.
 * Drag to turn it, pinch or the buttons to zoom, double-tap to frame the route again. Tapping a flight makes it the
 * active one (the globe turns to it and the ticket behind follows) and names it, with Go to ticket to close the globe
 * and bring that ticket into view ([onGoToTicket]; the overlay plays its exit). TalkBack's per-flight actions go
 * straight to the ticket.
 */
@Composable
internal fun EditorGlobeOverlay(
    route: EditorRoute,
    description: String,
    now: Instant,
    activeLeg: Int,
    visible: Boolean,
    onActivateLeg: (Int) -> Unit,
    onGoToTicket: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cover: GlobeOverlayCover? = null,
) {
    val labels = legLabels(route)
    val legs = rememberNamedLegs(route)
    val state = rememberRouteGlobeState()
    LaunchedEffect(visible) {
        if (visible) {
            state.selection = null
            state.recentre(null)
        }
    }
    GlobeOverlay(
        visible = visible,
        onDismiss = onDismiss,
        title = stringResource(R.string.globe_title),
        subtitle = description,
        state = state,
        cover = cover,
        modifier = modifier.testTag(TripsTestTags.EditorGlobeOverlay),
        globe = { globeModifier ->
            RouteGlobe(
                legs = legs,
                instant = now,
                contentDescription = description,
                activeLeg = route.globeIndexOf(activeLeg).takeIf { route.legs.size > 1 },
                showLabels = true,
                onLegClick = { onGoToTicket(route.legIndices[it]) },
                onLegTap = { onActivateLeg(route.legIndices[it]) },
                legActionLabel = { labels[it] },
                state = state,
                interaction = GlobeInteraction.RotateAndZoom,
                modifier = globeModifier.testTag(TripsTestTags.EditorGlobeFull),
            )
        },
        callout = { calloutModifier ->
            GlobeCallout(state, legs, calloutModifier) { selection ->
                if (selection is GlobeSelection.Leg) {
                    TextButton(
                        onClick = { onGoToTicket(route.legIndices[selection.index]) },
                        modifier = Modifier.testTag(TripsTestTags.EditorGlobeGoToTicket),
                    ) {
                        Text(stringResource(R.string.globe_go_to_ticket), color = GlobeColors.current.route)
                    }
                }
            }
        },
        details = if (route.legs.size > 1) {
            { detailsModifier ->
                Text(
                    stringResource(R.string.globe_tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = GlobeColors.current.label.copy(alpha = 0.86f),
                    modifier = detailsModifier,
                )
            }
        } else {
            null
        },
    )
}

/** The drawn legs, each named for when it's tapped ("Flight 2 · Doha to Tokyo"). */
@Composable
private fun rememberNamedLegs(route: EditorRoute): List<GlobeLeg> {
    val template = stringResource(R.string.globe_leg_name)
    return remember(route, template) {
        route.legs.mapIndexed { i, leg ->
            val (from, to) = route.legPlaces[i]
            leg.copy(name = template.format(route.legIndices[i] + 1, from.cityLabel, to.cityLabel))
        }
    }
}
