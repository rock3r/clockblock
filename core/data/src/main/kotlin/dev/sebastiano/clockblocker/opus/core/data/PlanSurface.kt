package dev.sebastiano.clockblocker.opus.core.data

/**
 * Something outside the app's UI that renders the current plan: home/lock-screen widgets, the ongoing
 * "Now" notification. Implementations are contributed into a Metro multibinding
 * (`@ContributesIntoSet(AppScope::class)`) and refreshed by the alarm scheduler at every advice boundary
 * and whenever a plan changes, so every surface always shows the same thing.
 */
interface PlanSurface {
    suspend fun refresh()
}
