package dev.sebastiano.clockblocker.opus.core.circadian

import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import java.time.LocalTime

/**
 * The three chronotype classes the science report parameterises (`docs/science.md` §8.2): they set the
 * CBTmin-to-mid-sleep offset and the advance-or-delay threshold.
 */
enum class ChronotypeClass { Early, Neutral, Late }

/**
 * Maps the app's 5-level self-report onto the report's 3 classes, consistent with the MEQ mapping of §8.2
 * (MEQ ≥ 59 = moderate/definite morning → early; ≤ 41 = moderate/definite evening → late).
 */
fun Chronotype.toClass(): ChronotypeClass = when (this) {
    Chronotype.DefiniteMorning, Chronotype.ModerateMorning -> ChronotypeClass.Early
    Chronotype.Intermediate -> ChronotypeClass.Neutral
    Chronotype.ModerateEvening, Chronotype.DefiniteEvening -> ChronotypeClass.Late
}

/** Questionnaire helpers (MEQ, MCTQ) from `docs/science.md` §8.1. */
object Chronotypes {
    /** Horne & Östberg MEQ score (16–86) → category. */
    fun fromMeq(score: Int): Chronotype = when {
        score >= 70 -> Chronotype.DefiniteMorning
        score >= 59 -> Chronotype.ModerateMorning
        score >= 42 -> Chronotype.Intermediate
        score >= 31 -> Chronotype.ModerateEvening
        else -> Chronotype.DefiniteEvening
    }

    /** MCTQ mid-sleep on free days, MSF = SO_f + SD_f / 2, as a clock hour in [0, 24). */
    fun mctqMidSleepFreeDays(sleepOnsetFree: LocalTime, sleepDurationFreeHours: Double): Double =
        CircadianMath.mod24(sleepOnsetFree.toSecondOfDay() / 3600.0 + sleepDurationFreeHours / 2)

    /**
     * MCTQ sleep-debt corrected mid-sleep MSF_sc (5 work days, 2 free days). Not valid if the person uses an
     * alarm on free days.
     */
    fun mctqMsfSc(
        sleepOnsetFree: LocalTime,
        sleepDurationFreeHours: Double,
        sleepDurationWorkHours: Double,
        workDays: Int = 5,
    ): Double {
        val msf = mctqMidSleepFreeDays(sleepOnsetFree, sleepDurationFreeHours)
        if (sleepDurationFreeHours <= sleepDurationWorkHours) return msf
        val freeDays = 7 - workDays
        val week = (workDays * sleepDurationWorkHours + freeDays * sleepDurationFreeHours) / 7.0
        return CircadianMath.mod24(msf - (sleepDurationFreeHours - week) / 2)
    }

    /** MSF_sc tertiles (§8.2): before 03:00 → early, after 05:00 → late. */
    fun fromMsfSc(msfScHours: Double, earlyBefore: Double = 3.0, lateAfter: Double = 5.0): ChronotypeClass {
        // Mid-sleep in the evening (18:00–24:00, e.g. 23:00 for very early sleepers) counts as before midnight:
        // map to [-6, 18), matching fromMidSleep.
        val h = CircadianMath.mod24(msfScHours + 6.0) - 6.0
        return when {
            h < earlyBefore -> ChronotypeClass.Early
            h > lateAfter -> ChronotypeClass.Late
            else -> ChronotypeClass.Neutral
        }
    }

    /**
     * Mid-sleep (clock hours) → one of the app's five chronotypes. The bands nest inside [fromMsfSc]'s three classes
     * (early < 03:00 ≤ neutral ≤ 05:00 < late), so the five-level answer never disagrees with the planner about
     * advance-vs-delay thresholds: before 02:00 Early bird, before 03:00 Morning person, up to 05:00 Somewhere in
     * between, up to 06:00 Evening person, later Night owl. Evening mid-sleeps count as before midnight.
     */
    fun fromMidSleep(midSleepHours: Double): Chronotype {
        val h = CircadianMath.mod24(midSleepHours + 6.0) - 6.0
        return when {
            h < 2.0 -> Chronotype.DefiniteMorning
            h < 3.0 -> Chronotype.ModerateMorning
            h <= 5.0 -> Chronotype.Intermediate
            h <= 6.0 -> Chronotype.ModerateEvening
            else -> Chronotype.DefiniteEvening
        }
    }

    /**
     * The chronotype of someone whose ideal night on a free day starts at [sleepOnsetFree] and lasts
     * [sleepDurationFreeHours]: [fromMidSleep] of the plain MCTQ mid-sleep on free days ([mctqMidSleepFreeDays]).
     *
     * Onboarding asks only for that night, so there is no work-day sleep to correct against: this is MSF, not
     * MSF_sc. Free-day sleep that catches up on a week's sleep debt runs long, which moves its midpoint later, so the
     * answer can lean a little late for people who sleep in on days off; the user can pick another chronotype.
     */
    fun fromFreeDaySleep(sleepOnsetFree: LocalTime, sleepDurationFreeHours: Double): Chronotype =
        fromMidSleep(mctqMidSleepFreeDays(sleepOnsetFree, sleepDurationFreeHours))
}
