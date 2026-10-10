package dev.sebastiano.clockblocker.opus.core.notifications.schedule

import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import java.time.Duration
import java.time.Instant

/** What happens at a [Transition]. */
enum class TransitionKind(
    /** Whether this transition makes a sound (posts a reminder) rather than only refreshing surfaces. */
    val alerts: Boolean,
) {
    /** `reminderLeadMinutes` before a window starts (or at its start when the lead is 0). */
    Upcoming(alerts = true),

    /** A window starts: surfaces switch over silently. */
    Start(alerts = false),

    /** A window ends: surfaces switch over silently. */
    End(alerts = false),

    /** A Sleep or Nap window ends: the one transition allowed to alert at the edge of a sleep block. */
    WakeUp(alerts = true),

    /** An instantaneous advice (melatonin) is due. */
    Moment(alerts = true),
}

/** One plan boundary that matters to notifications and widgets. Instants are absolute; zones only matter for text. */
data class Transition(val at: Instant, val kind: TransitionKind, val advice: Advice) {
    /** Stable identity used for de-duplication: the same advice can't have the same kind of transition twice. */
    val key: String get() = "${advice.id}:${kind.name}"
}

/** All transitions sharing one instant; scheduled as a single alarm. */
data class AlarmPoint(val at: Instant, val transitions: List<Transition>) {
    val alerts: Boolean get() = transitions.any { it.kind.alerts }
}

/**
 * Pure computation of the alarms the scheduler should hold. This is the single source of truth for *when*
 * anything outside the app updates, so every rule is here and unit tested:
 *
 * - Windows get an [TransitionKind.Upcoming] reminder `leadMinutes` before their start, and silent
 *   [TransitionKind.Start] / [TransitionKind.End] refreshes. Sleep and Nap ends are [TransitionKind.WakeUp].
 * - Melatonin (and any future moment type) gets a [TransitionKind.Moment] at its time.
 * - Flights only refresh (take-off/landing drive the travel-day Live Update); they never remind.
 * - Nothing fires strictly inside a Sleep or Nap window, and no reminder is sent for a window that starts
 *   while the user is asleep. The window's own start and its wake-up are kept, so the sleeper is woken exactly
 *   once and every surface is re-rendered at wake-up.
 * - With reminders disabled only silent refreshes remain (widgets must still update).
 * - Duplicate advice (e.g. a night split across two plan days) and coinciding instants are merged.
 */
object TransitionPlanner {

    /** How many alarms to hold at once; the chain is re-armed at every alarm, so a small number is enough. */
    const val DEFAULT_LIMIT: Int = 8

    /** Every transition of [plan], suppression applied, sorted by time. Not filtered by "now". */
    fun transitions(plan: JetLagPlan, settings: AppSettings): List<Transition> {
        val advice = plan.allAdvice
            .distinctBy { it.id }
            .distinctBy { Triple(it.type, it.start, it.end) }
        val lead = Duration.ofMinutes(settings.reminderLeadMinutes.coerceAtLeast(0).toLong())
        val sleepers = advice.filter { it.type.isSleeper }

        return advice
            .flatMap { rawTransitions(it, lead) }
            .filterNot { it.isSuppressedBy(sleepers) }
            .mapNotNull { if (settings.remindersEnabled) it else it.silenced() }
            .distinctBy { it.key }
            .sortedWith(compareBy<Transition> { it.at }.thenBy { it.kind.ordinal }.thenBy { it.advice.type.ordinal })
    }

    /** The next [limit] alarm instants strictly after [now], each with the transitions it carries. */
    fun upcoming(
        plan: JetLagPlan,
        settings: AppSettings,
        now: Instant,
        limit: Int = DEFAULT_LIMIT,
    ): List<AlarmPoint> = transitions(plan, settings)
        .filter { it.at.isAfter(now) }
        .groupBy { it.at }
        .map { (at, transitions) -> AlarmPoint(at, transitions) }
        .sortedBy { it.at }
        .take(limit)

    /** Transitions of [plan] happening exactly at [at] (used when an alarm fires, against the *current* plan). */
    fun transitionsAt(plan: JetLagPlan, settings: AppSettings, at: Instant): List<Transition> =
        transitions(plan, settings).filter { it.at == at }

    /**
     * How long before a block starts the widget dial must have it: its 16 h of future, less an hour, because the dial
     * measures the wall clock and a clock change on the way can stretch real time by one.
     */
    val DIAL_ENTRY_LEAD: Duration = Duration.ofHours(15)

    /**
     * The next instant strictly after [now] at which a block of [plan] comes into the widget dial's view
     * ([DIAL_ENTRY_LEAD] before it starts). Widgets capture the dial's blocks and only move the hand, so without a
     * refresh then a block further away than the dial's future would stay off it until it starts.
     */
    fun nextDialEntry(plan: JetLagPlan, now: Instant): Instant? = plan.allAdvice
        .map { it.start.minus(DIAL_ENTRY_LEAD) }
        .filter { it.isAfter(now) }
        .minOrNull()

    /** How long a moment stays due on the widget dial (the design system's `MomentDueMinutes`). */
    val MOMENT_DUE: Duration = Duration.ofMinutes(1)

    /**
     * The next instant strictly after [now] at which a moment of [plan] (melatonin, or any advice with no length)
     * stops being due ([MOMENT_DUE] after it). A widget captured at the moment features it, and its only
     * [TransitionKind.Moment] is at the start, so without a refresh then it would stay featured until a later alarm.
     */
    fun nextMomentEnd(plan: JetLagPlan, now: Instant): Instant? = plan.allAdvice
        .filter { it.type.isMoment || it.start == it.end }
        .map { it.start.plus(MOMENT_DUE) }
        .filter { it.isAfter(now) }
        .minOrNull()

    /** The next refresh the widget dial needs on its own: the earlier of [nextDialEntry] and [nextMomentEnd]. */
    fun nextDialRefresh(plan: JetLagPlan, now: Instant): Instant? =
        listOfNotNull(nextDialEntry(plan, now), nextMomentEnd(plan, now)).minOrNull()

    private fun rawTransitions(advice: Advice, lead: Duration): List<Transition> = when {
        advice.type.isMoment -> listOf(Transition(advice.start, TransitionKind.Moment, advice))
        advice.type == AdviceType.Flight -> listOf(
            Transition(advice.start, TransitionKind.Start, advice),
            Transition(advice.end, TransitionKind.End, advice),
        )
        else -> listOf(
            Transition(advice.start.minus(lead), TransitionKind.Upcoming, advice),
            Transition(advice.start, TransitionKind.Start, advice),
            Transition(
                advice.end,
                if (advice.type.isSleeper) TransitionKind.WakeUp else TransitionKind.End,
                advice,
            ),
        )
    }

    private fun Transition.isSuppressedBy(sleepers: List<Advice>): Boolean = sleepers.any { sleep ->
        if (sleep.id == advice.id) return@any false
        val firesWhileAsleep = at.isStrictlyInside(sleep)
        // Don't remind about something that will begin while the user sleeps; the wake-up covers it.
        val remindsAboutSomethingAsleep = kind == TransitionKind.Upcoming && advice.start.isStrictlyInside(sleep)
        firesWhileAsleep || remindsAboutSomethingAsleep
    }

    /** With reminders off: keep state changes, drop pure reminders. */
    private fun Transition.silenced(): Transition? = when (kind) {
        TransitionKind.Upcoming, TransitionKind.Moment -> null
        TransitionKind.WakeUp -> copy(kind = TransitionKind.End)
        TransitionKind.Start, TransitionKind.End -> this
    }

    private fun Instant.isStrictlyInside(window: Advice): Boolean = isAfter(window.start) && isBefore(window.end)
}

/** Sleep blocks that must not be interrupted. Optional naps are skippable, so they don't silence anything. */
internal val AdviceType.isSleeper: Boolean
    get() = this == AdviceType.Sleep || this == AdviceType.Nap
