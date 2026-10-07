package dev.sebastiano.clockblocker.opus.core.notifications.now

import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import java.time.Duration
import java.time.Instant

/**
 * What the ongoing "Now" notification (and any other glanceable surface) should say at an instant.
 * Pure data; rendering to text happens in [NotificationTextFormatter][dev.sebastiano.clockblocker.opus.core.notifications.text.NotificationTextFormatter].
 */
data class NowState(
    val tripId: String,
    /** The window to act on right now; `null` in a gap between windows. */
    val headline: Advice?,
    /**
     * The headline's own end, the same "until" as the plan screen's Now card and the widgets (#43). A block that
     * outranks it and starts earlier doesn't cut it short: that block is [next], then [alongside]. `null` in a gap.
     */
    val until: Instant?,
    /**
     * The next window to start after now (it may start before [until], overlapping the headline), or the next
     * window at all in a gap. Ties go to the one that will be the headline.
     */
    val next: Advice?,
    /** What the user logged for [headline], if anything. */
    val outcome: AdviceOutcome?,
    /**
     * Other windows running right now besides the headline, each with its own end (never the flight marker, which
     * the travel-day header covers), soonest end first.
     */
    val alongside: List<Advice> = emptyList(),
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
        val next = windows.filter { it.start.isAfter(now) }.minWithOrNull(compareBy<Advice> { it.start }.then(priority))
        val alongside = windows
            .filter { now in it && it.id != headline?.id && it.type != AdviceType.Flight }
            .sortedWith(compareBy<Advice> { it.end }.then(priority))
        return NowState(
            tripId = plan.tripId,
            headline = headline,
            until = headline?.end,
            next = next,
            outcome = headline?.let { h -> logs.lastOrNull { it.adviceId == h.id }?.outcome },
            alongside = if (headline == null) emptyList() else alongside,
        )
    }

    /** Headline priority as a comparator: sleep kinds first, then [AdviceType] order, the flight marker last. */
    private val priority: Comparator<Advice> = compareBy { advice ->
        val sleepRank = sleepOrder.indexOf(advice.type)
        when {
            sleepRank >= 0 -> sleepRank
            advice.type == AdviceType.Flight -> Int.MAX_VALUE
            else -> sleepOrder.size + advice.type.ordinal
        }
    }

    private fun headline(windows: List<Advice>, instant: Instant): Advice? =
        windows.filter { instant in it }.minWithOrNull(priority)

    private fun JetLagPlan.windows(): List<Advice> = allAdvice.filter { !it.type.isMoment }.distinctBy { it.id }
}
