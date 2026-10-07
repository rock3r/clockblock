package dev.sebastiano.clockblocker.opus.core.data

import dev.sebastiano.clockblocker.opus.core.data.time.Ticker
import dev.sebastiano.clockblocker.opus.core.model.easterEggsAllowed
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [easterEggsAllowed] for screens without a plan of their own (About, the sleep dials): Reduce motion is off and
 * the current plan doesn't say sleep. The plan screen applies the same rule to the plan it shows.
 */
@Inject
class EasterEggGate(settings: SettingsRepository, plans: PlanRepository, ticker: Ticker) {
    /** Re-evaluated when the settings or the current plan change, and as time passes. */
    val allowed: Flow<Boolean> = combine(settings.settings, plans.currentPlan, ticker.ticks()) { s, plan, now ->
        easterEggsAllowed(s.reduceMotion, plan, now)
    }.distinctUntilChanged()
}
