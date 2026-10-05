package dev.sebastiano.clockblocker.opus.core.notifications.now

import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import java.time.Duration
import java.time.Instant

/** The advice that takes over at [from] (it may have started earlier, overlapping the current one). */
data class NextUp(val advice: Advice, val from: Instant) {
    val until: Instant get() = advice.end
}

/**
 * What the ongoing "Now" notification (and any other glanceable surface) should say at an instant.
 * Pure data; rendering to text happens in [NowTextFormatter].
 */
data class NowState(
    val tripId: String,
    /** The window to act on right now; `null` in a gap between windows. */
    val headline: Advice?,
    /**
     * When the headline stops being the headline: its end, or earlier if a higher-priority window starts
     * (e.g. Sleep beginning during Avoid light). `null` in a gap.
     */
    val until: Instant?,
    /** What takes over at [until] (or the next window, in a gap). */
    val next: NextUp?,
    /** What the user logged for [headline], if anything. */
    val outcome: AdviceOutcome?,
)

/**
 * Pure selection of the current headline advice. Rules, in order:
 * 1. Sleep beats everything (if you should be asleep, that's the only instruction that matters), then Nap,
 *    then Optional nap.
 * 2. Otherwise the highest-priority window by [AdviceType] order (bright light before caffeine…).
 * 3. A Flight marker is only the headline when nothing else is active.
 * Moments (melatonin) are never the headline; they get their own reminder.
 */
object NowStateCalculator {

    private val sleepOrder = listOf(AdviceType.Sleep, AdviceType.Nap, AdviceType.OptionalNap)

    /** The headline advice active at [instant], or `null`. */
    fun headline(plan: JetLagPlan, instant: Instant): Advice? = headline(plan.windows(), instant)

    /**
     * Whether the plan is "live" at [now]: from [lead] before its first window until its last window ends.
     * Outside this range the ongoing notification is removed (an upcoming trip in five days is not "now").
     */
    fun isInProgress(plan: JetLagPlan, now: Instant, lead: Duration = Duration.ZERO): Boolean {
        val windows = plan.windows()
        val first = windows.minOfOrNull { it.start } ?: return false
        val last = windows.maxOf { it.end }
        return !now.isBefore(first.minus(lead)) && now.isBefore(last)
    }

    /** The full state at [now], or `null` when the plan isn't in progress. */
    fun compute(
        plan: JetLagPlan,
        now: Instant,
        logs: List<AdviceLog> = emptyList(),
        lead: Duration = Duration.ZERO,
    ): NowState? {
        if (!isInProgress(plan, now, lead)) return null
        val windows = plan.windows()
        val headline = headline(windows, now)
        val boundaries = windows.flatMap { listOf(it.start, it.end) }.filter { it.isAfter(now) }.distinct().sorted()
        // The headline changes at the first boundary where a different advice (or nothing) takes over.
        val changeAt = boundaries.firstOrNull { headline(windows, it)?.id != headline?.id }
        // Skip over gaps so "then …" always names the next thing to do.
        val next = changeAt?.let { from ->
            boundaries.asSequence()
                .filter { !it.isBefore(from) }
                .firstNotNullOfOrNull { b -> headline(windows, b)?.let { NextUp(it, b) } }
        }
        return NowState(
            tripId = plan.tripId,
            headline = headline,
            until = headline?.let { changeAt ?: it.end },
            next = next,
            outcome = headline?.let { h -> logs.lastOrNull { it.adviceId == h.id }?.outcome },
        )
    }

    private fun headline(windows: List<Advice>, instant: Instant): Advice? {
        val active = windows.filter { instant in it }
        return active.filter { it.type in sleepOrder }.minByOrNull { sleepOrder.indexOf(it.type) }
            ?: active.filter { it.type != AdviceType.Flight }.minByOrNull { it.type.ordinal }
            ?: active.firstOrNull { it.type == AdviceType.Flight }
    }

    private fun JetLagPlan.windows(): List<Advice> = allAdvice.filter { !it.type.isMoment }.distinctBy { it.id }
}
