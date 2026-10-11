package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeLeg
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeStop
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.Pinch
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.RouteGlobe
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.pinchGesture
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Sun
import dev.sebastiano.clockblocker.opus.core.model.Trip
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/** Below this sun height (degrees) the place counts as dark: sunset, with refraction. */
private const val DarkBelowDegrees = -0.833

/**
 * The world behind the dial (pinch out on it): the trip's route on the globe, the terminator at [at] (the dial's
 * time), a dot where the traveller is then, and the leg they're on bright. Says where they are in words ("It's 03:10
 * and dark in Tokyo"), politely announced to TalkBack when it appears. Pinch in, Back or the "Back to the dial"
 * action return. Works with Reduce motion (it simply cross-fades in) and in sleep windows: it isn't an easter egg.
 */
@Composable
internal fun WorldView(trip: Trip, shiftHours: Double, at: Instant, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val legs = remember(trip) { trip.legs.map { GlobeLeg(it.origin.stop(), it.destination.stop()) } }
    val position = remember(trip, at) { trip.positionAt(at) }
    val formatter = rememberTimeFormatter()
    val time = formatter.formatFull(at.atZone(position.place.zone).toLocalTime())
    val dark = remember(position, at) { Sun.elevation(Sun.subsolarPoint(at), position.place.latitude, position.place.longitude) < DarkBelowDegrees }
    val city = position.place.city.ifBlank { position.place.name }
    val whereYouAre = stringResource(
        when {
            position.inAir && dark -> R.string.plan_world_in_air_dark
            position.inAir -> R.string.plan_world_in_air_light
            dark -> R.string.plan_world_dark
            else -> R.string.plan_world_light
        },
        time,
        city,
    )
    val description = tripDescription(trip, shiftHours)
    val back = stringResource(R.string.plan_world_back)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .testTag(PlanTags.World)
            .pinchGesture(enabled = true) { if (it == Pinch.In) onBack() }
            .semantics { customActions = listOf(CustomAccessibilityAction(back) { onBack(); true }) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        RouteGlobe(
            legs = legs,
            instant = at,
            contentDescription = description,
            activeLeg = position.legIndex.takeIf { legs.size > 1 },
            showLabels = true,
            marker = position.point,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            whereYouAre,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .background(Color(0xB3000000), MaterialTheme.shapes.small)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(PlanTags.WorldCaption),
        )
    }
}

private fun Place.stop() = GlobeStop(point, displayCode)

/** "Lisbon to Tokyo via Doha, 9 hours east": the trip in words for TalkBack. */
@Composable
internal fun tripDescription(trip: Trip, shiftHours: Double): String {
    val cities = buildList {
        trip.legs.forEach { leg ->
            if (lastOrNull() != leg.origin) add(leg.origin)
            add(leg.destination)
        }
    }.map { it.city.ifBlank { it.name } }
    val route = when {
        cities.size <= 2 -> stringResource(R.string.plan_world_route, cities.first(), cities.last())
        else -> stringResource(R.string.plan_world_route_via, cities.first(), cities.last(), cities.subList(1, cities.size - 1).joinToString(", "))
    }
    val hours = timeDifferenceHours(shiftHours)
    if (abs(hours) < 1.0) return route
    val whole = abs(hours).roundToInt()
    val shift = if (abs(abs(hours) - whole) < 0.01) {
        pluralStringResource(if (hours > 0) R.plurals.plan_world_hours_east else R.plurals.plan_world_hours_west, whole, whole)
    } else {
        stringResource(if (hours > 0) R.string.plan_world_hours_east_fraction else R.string.plan_world_hours_west_fraction, abs(hours))
    }
    return "$route, $shift"
}
