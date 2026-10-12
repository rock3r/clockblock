package dev.sebastiano.clockblocker.opus.core.data

/**
 * Something outside the app's UI that renders the current plan: home/lock-screen widgets, the ongoing
 * "Now" notification. Implementations are contributed into a Metro multibinding
 * (`@ContributesIntoSet(AppScope::class)`) and refreshed by the alarm scheduler at every advice boundary
 * and whenever a plan changes, so every surface always shows the same thing.
 */
interface PlanSurface {
    /**
     * Whether this surface currently has a placed route globe. While one is, the scheduler arms the globe's own
     * cosmetic tick around the trip's flights (day-night line, traveller marker) and calls [refreshGlobe] on it.
     */
    val hasPlacedGlobe: Boolean get() = false

    suspend fun refresh()

    /** Re-renders only this surface's route globe(s), for the globe tick. Surfaces without a globe do nothing. */
    suspend fun refreshGlobe() = Unit
}
