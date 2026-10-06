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
        // map to [-6, 18), matching ChronotypeEstimate.fromMidSleepHours.
        val h = CircadianMath.mod24(msfScHours + 6.0) - 6.0
        return when {
            h < earlyBefore -> ChronotypeClass.Early
            h > lateAfter -> ChronotypeClass.Late
            else -> ChronotypeClass.Neutral
        }
    }
}
