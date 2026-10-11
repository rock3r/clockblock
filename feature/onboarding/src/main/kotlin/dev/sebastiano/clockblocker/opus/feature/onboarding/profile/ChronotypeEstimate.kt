package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import dev.sebastiano.clockblocker.opus.core.circadian.Chronotypes
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import java.time.LocalTime

/**
 * Chronotype estimates from sleep, on the MCTQ mid-sleep helpers in `:core:circadian` (Roenneberg 2004;
 * `docs/science.md` §8.1). The five bands ([Chronotypes.fromMidSleep]) nest inside the planner's three classes
 * (`Chronotypes.fromMsfSc`: early < 03:00 ≤ neutral ≤ 05:00 < late), so an estimate never disagrees with the engine
 * about advance-vs-delay thresholds.
 */
object ChronotypeEstimate {
    /** Result of an estimate: the suggested [chronotype] and the mid-sleep it came from. */
    data class Result(val chronotype: Chronotype, val midSleep: LocalTime)

    /**
     * Onboarding's inference: the chronotype of someone whose ideal night on a day off is [ideal]. This is plain
     * mid-sleep on free days (MSF): with no work-day sleep there is nothing to correct oversleep against.
     */
    fun fromIdealNight(ideal: SleepWindow): Result {
        val msf = Chronotypes.mctqMidSleepFreeDays(ideal.bedtime, ideal.duration.toMinutes() / 60.0)
        return Result(Chronotypes.fromMidSleep(msf), midSleepTime(msf))
    }

    /**
     * Settings' "Not sure?" helper: free-day sleep corrected for oversleep (MSF_sc) against the usual sleep.
     *
     * @param free usual sleep on free days.
     * @param work usual sleep on work days, for the oversleep correction.
     */
    fun estimate(free: SleepWindow, work: SleepWindow): Result {
        val msfSc = Chronotypes.mctqMsfSc(
            sleepOnsetFree = free.bedtime,
            sleepDurationFreeHours = free.duration.toMinutes() / 60.0,
            sleepDurationWorkHours = work.duration.toMinutes() / 60.0,
        )
        return Result(fromMidSleepHours(msfSc), midSleepTime(msfSc))
    }

    /** Maps mid-sleep (clock hours) to one of five chronotypes ([Chronotypes.fromMidSleep]). */
    fun fromMidSleepHours(msfScHours: Double): Chronotype = Chronotypes.fromMidSleep(msfScHours)

    private fun midSleepTime(hours: Double): LocalTime =
        SleepDialMath.timeOf((hours * 60).toInt().mod(SleepDialMath.MinutesPerDay))
}
