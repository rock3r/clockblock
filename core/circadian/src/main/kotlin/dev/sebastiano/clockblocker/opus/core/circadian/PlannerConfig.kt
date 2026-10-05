package dev.sebastiano.clockblocker.opus.core.circadian

import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.UserProfile

/** Which ODE the planner uses for its days-to-adapt estimates and for the Tier-2 direction choice. */
enum class EstimateModel { Hannay19, Forger99 }

/**
 * Every tunable of the planner, with the values of `docs/science.md` §12.2 as defaults (evidence levels in
 * `docs/algorithm.md`). All durations are hours unless the name says otherwise.
 *
 * [forProfile] layers the user's [Intensity] and toggles on top; the defaults themselves are "Balanced".
 */
data class PlannerConfig(
    /** `PRE_ADV_CAP`: pre-flight advance per cycle (B: Eastman 2005, Burgess 2003). */
    val preAdvanceCap: Double = 1.0,
    /** `PRE_DEL_CAP`: pre-flight delay per cycle (B/C: Eastman & Burgess 2009). */
    val preDelayCap: Double = 1.5,
    /** Pre-flight delay per cycle with an evening light box. */
    val preDelayCapLightBox: Double = 2.0,
    /** `POST_ADV_CAP`: post-arrival advance per cycle (B/C). */
    val postAdvanceCap: Double = 1.5,
    /** `POST_DEL_CAP`: post-arrival delay per cycle (B/C). */
    val postDelayCap: Double = 2.0,
    /** `PRE_MAX_SHIFT`: total pre-flight shift (C, practicality). */
    val preMaxShift: Double = 3.0,
    /** Pre-flight adjustment days (0–4). */
    val preFlightDays: Int = 3,
    /** `ADV_THRESHOLD`: advance if the eastward shift A ≤ threshold, else delay (C; configurable 7–12). */
    val advanceThresholdEarly: Double = 10.0,
    val advanceThresholdNeutral: Double = 9.0,
    val advanceThresholdLate: Double = 8.0,
    /** `CBT_FROM_MID`: T0 = mid-sleep + offset (B: Baehr 2000; E&B 2009). */
    val cbtFromMidEarly: Double = 0.5,
    val cbtFromMidNeutral: Double = 1.0,
    val cbtFromMidLate: Double = 1.5,
    /** `SEEK_LEN`: seek window length from CBTmin (B: Khalsa 2003). */
    val seekLength: Double = 6.0,
    /** `AVOID_LEN`: avoid window length from CBTmin (B/C). */
    val avoidLength: Double = 8.0,
    /**
     * Length of the lower-priority "see light" window beyond the core seek window (light 6–10 h from CBTmin
     * still shifts a little in the same direction, §2.1). Display only; the validator treats it as neutral.
     */
    val seeLightLength: Double = 4.0,
    /** `MAX_SLEEP_DEV`: post-arrival sleep onset within ± this of the habitual local onset (C). */
    val maxSleepDeviation: Double = 3.0,
    /** `MEL_ADV_OFFSET`: 0.5 mg melatonin relative to CBTmin (B: Burgess 2010). */
    val melatoninAdvanceOffset: Double = -9.0,
    /** `MEL_ADV_WINDOW`: acceptable melatonin window relative to CBTmin (B/C). */
    val melatoninWindowStart: Double = -13.0,
    val melatoninWindowEnd: Double = -7.0,
    /** Dose label shown on melatonin cards. */
    val melatoninDose: String = "0.5 mg",
    /**
     * Opt-in morning melatonin for delay plans (low evidence, §3.1; off by default): taken at
     * CBTmin + [melatoninDelayOffset] or at wake if that falls in sleep and wake is within CBTmin + 2…5 h.
     */
    val melatoninForDelay: Boolean = false,
    val melatoninDelayOffset: Double = 3.5,
    /** `CAF_CUTOFF`: no caffeine within this time of planned sleep (B: Drake 2013). */
    val caffeineCutoff: Double = 6.0,
    /** Caffeine cutoff on advance plans (B: Burke 2015). */
    val caffeineCutoffAdvance: Double = 8.0,
    /** `NAP_MAX`: nap length (B: Brooks & Lack 2006). */
    val napLength: Double = 0.5,
    /** `NAP_MIN_BEFORE_SLEEP` (C). */
    val napMinBeforeSleep: Double = 6.0,
    /** A nap is shown as recommended (not optional) when the waking period exceeds habitual wake + this. */
    val napRecommendedExtraWake: Double = 1.0,
    /** Peak-fatigue window around CBTmin while awake (circadian alertness nadir). */
    val peakFatigueBefore: Double = 2.0,
    val peakFatigueAfter: Double = 2.0,
    /** `SHORT_TRIP`: stays shorter than this stay on home time (C: Waterhouse 2007; Sack 2010). */
    val shortTripHours: Double = 72.0,
    /** `PRE_DEP_WAKE`: no sleep from departure − this to departure + 0.5 h (C). */
    val preDepartureWake: Double = 3.0,
    val postDepartureNoSleep: Double = 0.5,
    /** `ARR_SLEEP_END`: no sleep from arrival − this to arrival + 1.5 h (C). */
    val arrivalSleepEnd: Double = 0.75,
    val postArrivalNoSleep: Double = 1.5,
    /** `MIN_SHIFT`: below this |Δ| there is nothing to adapt (C). */
    val minShift: Double = 2.0,
    /** `DONE_TOL`: plan finished when |φ* − φ| ≤ tol (C). */
    val doneTolerance: Double = 0.5,
    /** Safety bound on the number of body-clock cycles per segment (the reference uses 30). */
    val maxCycles: Int = 30,
    /** Light box available for evening light on delay plans. */
    val lightBox: Boolean = false,
    /**
     * Also block sleep across intermediate layovers (arrival − [arrivalSleepEnd] … next departure +
     * [postDepartureNoSleep]). The reference planner does not; real travellers cannot sleep through a connection.
     */
    val blockLayoverSleep: Boolean = true,
    /** Tier-2: choose advance vs delay by simulating both with [estimateModel] when A ∈ [tier2Min, tier2Max]. */
    val tier2DirectionChoice: Boolean = false,
    val tier2Min: Double = 8.0,
    val tier2Max: Double = 12.0,
    /** Tier-2 ties (within this many days) go to the literature/threshold default. */
    val tier2TieDays: Double = 1.0,
    /** ODE used for estimates (see `docs/algorithm.md` for why Hannay19 is the default). */
    val estimateModel: EstimateModel = EstimateModel.Hannay19,
    /** Simulated days after arrival; "not adapted" estimates report this horizon. */
    val estimateHorizonDays: Int = 21,
    /** Display rounding of advice boundaries (practicality filter). */
    val roundingMinutes: Int = 15,
    /** Same-type windows closer than this are merged for display. */
    val mergeGapMinutes: Int = 15,
    /** Seek windows shorter than this are not shown. */
    val minSeekMinutes: Int = 30,
    /** Rate at which the body clock is assumed to drift without a plan (|Δ| < [minShift]), hours/day. */
    val naturalDriftPerDay: Double = 1.0,
    /** Destination nights suggested when there is no plan (|Δ| < [minShift]). */
    val noPlanSleepNights: Int = 3,
) {
    /** `ADV_THRESHOLD` for a chronotype: advance when the eastward shift A ≤ this, else delay. */
    fun advanceThreshold(c: ChronotypeClass): Double = when (c) {
        ChronotypeClass.Early -> advanceThresholdEarly
        ChronotypeClass.Neutral -> advanceThresholdNeutral
        ChronotypeClass.Late -> advanceThresholdLate
    }

    /** `CBT_FROM_MID` for a chronotype: hours from habitual mid-sleep to the estimated CBTmin. */
    fun cbtFromMidSleep(c: ChronotypeClass): Double = when (c) {
        ChronotypeClass.Early -> cbtFromMidEarly
        ChronotypeClass.Neutral -> cbtFromMidNeutral
        ChronotypeClass.Late -> cbtFromMidLate
    }

    /**
     * Applies the user's preferences:
     * - [Intensity.Gentle]: at most 2 pre-flight days and 2 h total, pre-delay 1 h/day, post-arrival
     *   1 h/day advance / 1.5 h/day delay (E&B 2009's "realistic adherence" rate).
     * - [Intensity.Balanced]: the report's defaults.
     * - [Intensity.Max]: defaults plus Tier-2 model-based direction choice for 8–12 h eastward shifts.
     * - `adjustBeforeDeparture = false` → no pre-flight days.
     */
    fun forProfile(profile: UserProfile): PlannerConfig {
        val byIntensity = when (profile.intensity) {
            Intensity.Gentle -> copy(
                preFlightDays = minOf(preFlightDays, 2),
                preMaxShift = minOf(preMaxShift, 2.0),
                preDelayCap = minOf(preDelayCap, 1.0),
                preDelayCapLightBox = minOf(preDelayCapLightBox, 1.5),
                postAdvanceCap = minOf(postAdvanceCap, 1.0),
                postDelayCap = minOf(postDelayCap, 1.5),
            )
            Intensity.Balanced -> this
            Intensity.Max -> copy(tier2DirectionChoice = true)
        }
        return if (profile.adjustBeforeDeparture) byIntensity else byIntensity.copy(preFlightDays = 0)
    }
}
