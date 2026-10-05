package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import dev.sebastiano.clockblocker.opus.core.circadian.Chronotypes
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import java.time.LocalTime

/**
 * The "Not sure?" helper: estimates a chronotype from sleep on free days (no alarm), using MCTQ mid-sleep on free
 * days corrected for oversleep (MSFsc, Roenneberg 2004; `docs/science.md` §8.1).
 *
 * The five bands nest inside the planner's three classes (`Chronotypes.fromMsfSc`: early < 03:00 ≤ neutral ≤
 * 05:00 < late), so the estimate never disagrees with the engine about advance-vs-delay thresholds.
 */
object ChronotypeEstimate {
    /** Result of the estimate: the suggested [chronotype] and the corrected mid-sleep it came from. */
    data class Result(val chronotype: Chronotype, val midSleep: LocalTime)

    /**
     * @param free usual sleep on free days.
     * @param work usual sleep on work days (the window set on the Sleep step), for the oversleep correction.
     */
    fun estimate(free: SleepWindow, work: SleepWindow): Result {
        val msfSc = Chronotypes.mctqMsfSc(
            sleepOnsetFree = free.bedtime,
            sleepDurationFreeHours = free.duration.toMinutes() / 60.0,
            sleepDurationWorkHours = work.duration.toMinutes() / 60.0,
        )
        val minute = (msfSc * 60).toInt().mod(SleepDialMath.MinutesPerDay)
        return Result(fromMidSleepHours(msfSc), SleepDialMath.timeOf(minute))
    }

    /** Maps corrected mid-sleep (clock hours) to one of five chronotypes. */
    fun fromMidSleepHours(msfScHours: Double): Chronotype {
        // Evening mid-sleeps (very early sleepers) count as before midnight: map to [-6, 18).
        val h = (msfScHours + 6.0).mod(24.0) - 6.0
        return when {
            h < 2.0 -> Chronotype.DefiniteMorning
            h < 3.0 -> Chronotype.ModerateMorning
            h <= 5.0 -> Chronotype.Intermediate
            h <= 6.0 -> Chronotype.ModerateEvening
            else -> Chronotype.DefiniteEvening
        }
    }
}
