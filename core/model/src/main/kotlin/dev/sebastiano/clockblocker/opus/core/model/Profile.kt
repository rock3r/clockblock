package dev.sebastiano.clockblocker.opus.core.model

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalTime

/**
 * Self-reported chronotype. Maps to a shift of the estimated circadian phase relative to the habitual sleep
 * window (owls tend to have a later phase angle; see the circadian science notes in `docs/science.md`).
 */
@Serializable
enum class Chronotype {
    DefiniteMorning,
    ModerateMorning,
    Intermediate,
    ModerateEvening,
    DefiniteEvening,
}

/** How hard the plan is allowed to push. Trades daily effort against days-to-adapt. */
@Serializable
enum class Intensity { Gentle, Balanced, Max }

/** The user's habitual sleep, in their home time zone. Bedtime may be before or after midnight. */
@Serializable
data class SleepWindow(
    @Serializable(with = LocalTimeIsoSerializer::class) val bedtime: LocalTime,
    @Serializable(with = LocalTimeIsoSerializer::class) val wake: LocalTime,
) {
    /** Sleep duration, handling windows that wrap around midnight. */
    val duration: Duration
        get() {
            val raw = Duration.between(bedtime, wake)
            return if (raw.isNegative || raw.isZero) raw.plusHours(24) else raw
        }

    /** Mid-sleep clock time (used for chronotype-style phase estimates, cf. MCTQ MSF). */
    val midSleep: LocalTime get() = bedtime.plus(duration.dividedBy(2))

    companion object {
        val Default = SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 0))
    }
}

@Serializable
data class UserProfile(
    val homeZoneId: String,
    /** Usual sleep on work days: the planner's habitual sleep. */
    val sleep: SleepWindow = SleepWindow.Default,
    val chronotype: Chronotype = Chronotype.Intermediate,
    val useMelatonin: Boolean = false,
    val useCaffeine: Boolean = true,
    val canSleepOnPlanes: Boolean = true,
    /** Start shifting the body clock before departure (pre-flight adjustment days). */
    val adjustBeforeDeparture: Boolean = true,
    val intensity: Intensity = Intensity.Balanced,
    /**
     * Usual sleep on days off (the nights you wake up without an alarm), or `null` while it's linked to [sleep]
     * (the same nights). It only feeds the chronotype estimate (MCTQ); plans start from [sleep]. Profiles saved
     * before this field existed load with it `null`.
     */
    val daysOffSleep: SleepWindow? = null,
) {
    /** Days-off sleep, falling back to work-day sleep while the two are linked. */
    val daysOff: SleepWindow get() = daysOffSleep ?: sleep
}
