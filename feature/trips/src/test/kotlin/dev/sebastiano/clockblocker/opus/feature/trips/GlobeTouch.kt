package dev.sebastiano.clockblocker.opus.feature.trips

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GeoPoint
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeGeo
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.RouteGlobeDefaults
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg

/**
 * Clicks a globe, framed on [legs] (on leg [active], or the whole route), where it draws leg [index]'s arc: the first
 * visible point of [fractions] along it. The globe's own projection, so the click goes through the real hit-testing.
 */
fun TouchInjectionScope.clickOnLeg(legs: List<FlightLeg>, index: Int, active: Int? = null, fractions: List<Double> = listOf(0.5, 0.4, 0.6, 0.3, 0.2)) {
    fun FlightLeg.from() = GeoPoint(origin.latitude, origin.longitude)
    fun FlightLeg.to() = GeoPoint(destination.latitude, destination.longitude)
    val view = GlobeGeo.routeView(legs.map { GlobeGeo.greatCircle(it.from(), it.to()) }, active)
    val point = fractions
        .map { GlobeGeo.interpolate(legs[index].from(), legs[index].to(), it) }
        .first { GlobeGeo.orthographic(it, view).depth > 0.15 }
    val p = GlobeGeo.orthographic(point, view)
    val radius = RouteGlobeDefaults.radius(width.toFloat(), height.toFloat())
    click(Offset(centerX + p.x.toFloat() * radius, centerY - p.y.toFloat() * radius))
}
