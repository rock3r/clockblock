package dev.sebastiano.clockblocker.opus.core.model

import java.time.Instant

/**
 * The one gate for playful extras (easter eggs) on every screen: they run only when Reduce motion is off and
 * [plan] (the plan on screen, or the current one) doesn't say sleep at [now]. No plan means no sleep to protect.
 */
fun easterEggsAllowed(reduceMotion: Boolean, plan: JetLagPlan?, now: Instant): Boolean =
    !reduceMotion && plan?.saysSleepAt(now) != true

/** A Sleep block covers [instant], whatever else overlaps it. */
fun JetLagPlan.saysSleepAt(instant: Instant): Boolean =
    allAdvice.any { it.type == AdviceType.Sleep && instant in it }
