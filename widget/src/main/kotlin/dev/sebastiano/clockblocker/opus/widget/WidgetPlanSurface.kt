package dev.sebastiano.clockblocker.opus.widget

import dev.sebastiano.clockblocker.opus.core.data.PlanSurface
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/**
 * Widgets as a [PlanSurface]: the alarm scheduler in `:core:notifications` calls [refresh] at every advice
 * boundary and whenever the plan changes, so the widgets always show the same thing as the Now notification.
 * The route globe's own tick calls [refreshGlobe], which re-renders only the Route globe widgets.
 */
@ContributesIntoSet(AppScope::class)
@Inject
class WidgetPlanSurface(private val updater: WidgetUpdater) : PlanSurface {
    override val hasPlacedGlobe: Boolean
        get() = updater.ids(WidgetKind.RouteGlobe).isNotEmpty()

    override suspend fun refresh() = updater.updateAll()

    override suspend fun refreshGlobe() = updater.update(WidgetKind.RouteGlobe, updater.ids(WidgetKind.RouteGlobe))
}
