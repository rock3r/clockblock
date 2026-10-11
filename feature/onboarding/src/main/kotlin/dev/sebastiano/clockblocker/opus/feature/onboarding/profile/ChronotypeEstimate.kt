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
     * The chronotype from both usual nights: mid-sleep on days off corrected for catch-up sleep against work days
     * (MCTQ MSF_sc). With the two nights the same (days off linked) there is nothing to correct: plain MSF.
     *
     * @param free usual sleep on days off (no alarm).
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
