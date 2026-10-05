package dev.sebastiano.clockblocker.opus.core.circadian

import dev.sebastiano.clockblocker.opus.core.circadian.engine.PlanBuilder
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import java.time.Instant

/**
 * The production [JetLagPlanner]: a rule-based cycle planner (light, sleep, melatonin, caffeine and naps per
 * circadian cycle) whose output is checked against a mathematical model of the human clock (Hannay 2019 by
 * default) to estimate how many days adaptation takes with and without the plan.
 *
 * The planner is pure and deterministic: the same trip, profile and [PlannerConfig] always produce the same
 * plan, including the advice ids. `now` only stamps [JetLagPlan.generatedAt]; days that are already in the past
 * are still emitted so the timeline stays stable while the trip is under way.
 *
 * See `docs/algorithm.md` for the decision procedure, parameters and evidence levels.
 *
 * @param config tuning parameters; the user profile's intensity and preferences are applied on top of it via
 * [PlannerConfig.forProfile].
 */
class DefaultJetLagPlanner(private val config: PlannerConfig = PlannerConfig()) : JetLagPlanner {
    override fun plan(trip: Trip, profile: UserProfile, now: Instant): JetLagPlan =
        PlanBuilder(config.forProfile(profile), trip, profile, now).build()
}
