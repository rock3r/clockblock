package dev.sebastiano.clockblocker.opus.core.data

/**
 * Something outside the app's UI that renders the current plan: home/lock-screen widgets, the ongoing
 * "Now" notification. Implementations are contributed into a Metro multibinding
 * (`@ContributesIntoSet(AppScope::class)`) and refreshed by the alarm scheduler at every advice boundary
 * and whenever a plan changes, so every surface always shows the same thing.
 */
interface PlanSurface {
    /** Whether this surface currently has a placed route globe that needs periodic daylight/marker refreshes. */
    val hasPlacedGlobe: Boolean get() = false

    suspend fun refresh()
}
