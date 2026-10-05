package dev.sebastiano.clockblocker.opus.core.circadian

import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import java.time.Instant

/**
 * Computes a jet lag plan for a trip. Pure and deterministic: same inputs, same plan (ids included), which
 * makes plans cacheable, diffable and unit-testable.
 */
fun interface JetLagPlanner {
    /**
     * @param now used only to decide whether pre-trip days that are already in the past should still be
     * emitted (they are, but the planner may start the shifting schedule from the current estimate).
     */
    fun plan(trip: Trip, profile: UserProfile, now: Instant): JetLagPlan
}
