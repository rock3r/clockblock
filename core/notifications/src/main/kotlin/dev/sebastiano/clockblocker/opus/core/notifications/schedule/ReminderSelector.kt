package dev.sebastiano.clockblocker.opus.core.notifications.schedule

import dev.sebastiano.clockblocker.opus.core.model.Advice
import java.time.Duration
import java.time.Instant

/** Which kind of alerting notification to post. */
enum class ReminderKind { Upcoming, WakeUp, Moment, Snoozed }

/**
 * One alerting reminder. Only one is ever visible (it reuses a single notification id), so if several
 * transitions alert at the same instant the most important one is the [advice] and the rest are listed in
 * [alsoStarting].
 */
data class ReminderSpec(
    val kind: ReminderKind,
    val advice: Advice,
    /** Reminders becoming due at the same instant, shown as an extra line. */
    val alsoStarting: List<Advice> = emptyList(),
    /** When the reminder is no longer accurate and should disappear on its own (`setTimeoutAfter`). */
    val expiresAt: Instant,
)

/**
 * Pure choice of the reminder to post when an alarm fires. Re-evaluated against the *current* plan at fire
 * time so a reminder can never contradict what the app shows; late alarms (Doze, device off, inexact fallback)
 * drop reminders that are no longer true.
 */
object ReminderSelector {

    /** How long an upcoming-window reminder stays up after the window started. */
    val UPCOMING_GRACE: Duration = Duration.ofMinutes(30)
    val WAKE_UP_TTL: Duration = Duration.ofHours(1)
    val MOMENT_TTL: Duration = Duration.ofHours(2)

    fun select(transitions: List<Transition>, now: Instant): ReminderSpec? {
        val fresh = transitions
            .filter { it.kind.alerts && !it.isStaleAt(now) }
            .sortedWith(compareBy<Transition> { priority(it.kind) }.thenBy { it.advice.type.ordinal })
        val primary = fresh.firstOrNull() ?: return null
        val kind = when (primary.kind) {
            TransitionKind.WakeUp -> ReminderKind.WakeUp
            TransitionKind.Moment -> ReminderKind.Moment
            else -> ReminderKind.Upcoming
        }
        return ReminderSpec(
            kind = kind,
            advice = primary.advice,
            alsoStarting = fresh.drop(1).map { it.advice }.distinctBy { it.id },
            expiresAt = primary.expiry(),
        )
    }

    /** A reminder for advice the user snoozed, or `null` if it's over by now. */
    fun snoozed(advice: Advice, now: Instant): ReminderSpec? {
        val expiresAt = if (advice.type.isMoment) advice.start.plus(MOMENT_TTL) else advice.end
        if (!now.isBefore(expiresAt)) return null
        return ReminderSpec(ReminderKind.Snoozed, advice, expiresAt = expiresAt)
    }

    private fun priority(kind: TransitionKind): Int = when (kind) {
        TransitionKind.WakeUp -> 0
        TransitionKind.Moment -> 1
        TransitionKind.Upcoming -> 2
        TransitionKind.Start, TransitionKind.End -> 3
    }

    private fun Transition.isStaleAt(now: Instant): Boolean = !now.isBefore(expiry())

    private fun Transition.expiry(): Instant = when (kind) {
        TransitionKind.Upcoming -> minOf(advice.start.plus(UPCOMING_GRACE), advice.end)
        TransitionKind.WakeUp -> at.plus(WAKE_UP_TTL)
        TransitionKind.Moment -> at.plus(MOMENT_TTL)
        TransitionKind.Start, TransitionKind.End -> at
    }
}
