# Opus Clockblock — jet lag planning algorithm

This document describes what `:core:circadian` computes and why. The science, the evidence review and the
original pseudo-code are in [`science.md`](science.md) (§ references below point there); this file documents
the **implemented** behaviour, including every place where the code deliberately goes beyond or differs from the
reference scripts in `docs/research/reference/`.

> Not medical advice. The planner produces a schedule of light, sleep, caffeine, nap and (opt-in) melatonin
> suggestions from published human phase-response data and two published mathematical models. It does not
> measure anything about the user.

## 1. Public API

| Symbol | Purpose |
|---|---|
| `DefaultJetLagPlanner(config: PlannerConfig = PlannerConfig()) : JetLagPlanner` | `plan(trip, profile, now): JetLagPlan`. Pure and deterministic. |
| `PlannerConfig` | Every tunable parameter (§12.2 plus the extras in §5 below). `forProfile(profile)` applies intensity and pre-flight preferences. |
| `EstimateModel { Hannay19, Forger99 }` | Model used for the days-to-adapt estimates and Tier-2 direction choice. |
| `ChronotypeClass`, `Chronotype.toClass()`, `Chronotypes` | Five self-report options → early / neutral / late; MEQ and MCTQ (MSF<sub>sc</sub>) scoring helpers. |
| `CircadianMath.norm12`, `mod24` | Hour arithmetic on the 24 h dial. |
| `JetLagPlan.bodyClockTimeAt`, `adaptationProgressAt`, `daySpans`, `currentDay`, `PlanDaySpan` | Read-side helpers for UI, widgets and notifications (`BodyClock.kt`). |
| `ode.CircadianModel`, `ode.Forger99`, `ode.Hannay19`, `ode.Rk4`, `ode.Trajectory` | The two ODE models (§4) and a fixed-step RK4 integrator with CBTmin read-out. |

## 2. Inputs

| Input | Source | Notes |
|---|---|---|
| Legs | `Trip.legs` | Local date-times + IANA zones; converted to UTC instants with the tz database (DST and historical offsets included). Sorted by departure. |
| Return | `Trip.returnDeparture` | Optional. Detects short trips; advice is clipped at the return departure. |
| Strategy override | `Trip.strategyOverride` | `StayOnHomeTime` forces home-time mode; `Adapt` disables the short-trip rule. |
| Habitual sleep | `UserProfile.sleep` | Home-clock bedtime and wake; duration coerced to 3–14 h. |
| Chronotype | `UserProfile.chronotype` | Definite/Moderate morning → early, Intermediate → neutral, Moderate/Definite evening → late. |
| Toggles | `useMelatonin`, `useCaffeine`, `canSleepOnPlanes`, `adjustBeforeDeparture`, `intensity` | See §6. |

The **home body clock** is the UTC offset of the first leg's origin at departure (not `profile.homeZoneId`):
a traveller starting from somewhere other than home is assumed to be entrained to where they start.

All computation runs in *planner hours*: `Double` hours since the UTC midnight of the first departure. Every
modern UTC offset is a multiple of 15 min, so rounding to 15 min on this grid is also exact on the local grid
(Kathmandu +5:45 and Chatham +12:45 included).

## 3. Mode selection

```text
segments   ← legs split at stops ≥ SHORT_TRIP (72 h): each such stop is a destination in its own right
Δ_j        ← norm12(arrivalOffset(segment j) − offset at the start of segment j)      // (−12, 12]
if strategyOverride == StayOnHomeTime                 → HOME-TIME
else if all |Δ_j| < MIN_SHIFT (2 h)                   → NO PLAN
else if override == null and return − arrival < 72 h  → HOME-TIME
else                                                  → ADAPT
```

* **No plan** (`direction = None`, `shiftHours = Δ`): flight markers plus `noPlanSleepNights` (3) destination
  nights at the habitual local bedtime (the first one at least 1.5 h after landing). The phase track drifts to
  the destination at `naturalDriftPerDay` (1 h/day) from arrival, with one CBTmin per day.
* **Home time** (`strategy = StayOnHomeTime`, `direction = None`, `shiftHours = 0`, `estimatedDaysToAdapt = 0`):
  sleep at the habitual home-clock time (minus airport-transit blocks), avoid light from CBTmin − 8 h to +3 h,
  see light from +3 h to +16 h, caffeine windows and cut-off on the home clock. The body clock stays on the
  origin offset. `estimatedDaysWithoutPlan` is still simulated so the UI can explain the choice. Home-time mode
  never suggests melatonin (§9: a sleep-aid dose at destination bedtime would push the clock).
* **Adapt**: the cycle planner below.

## 4. Decision procedure (adapt mode)

The cycle planner is a bit-for-bit port of `reference/planner.py` (§12.3); `CyclePlannerGoldenTest` compares
its output with the committed reference printouts byte for byte. Per segment:

1. **Direction.** `A = Δ mod 24` (eastward hours). Advance if `A ≤ ADV_THRESHOLD[chronotype]`
   (early 10 / neutral 9 / late 8 h), else delay. With **Max** intensity and `A ∈ [8, 12]` (first segment
   only), both directions are planned and simulated with Hannay19 (§13.7 Tier 2); the faster one wins, and
   results within 1 day of each other fall back to the threshold rule.
2. **Initial phase.** `T0 = habitual mid-sleep + CBT_FROM_MID[chronotype]` (0.5 / 1.0 / 1.5 h) on the home clock.
3. **Cycles.** Starting `preFlightDays` before departure, each circadian cycle (one CBTmin `T`) gets:
   * **sleep** `[T − ψon, T − ψon + duration]`; after arrival the onset is clamped to ±3 h of the habitual
     *local* onset; sleep is cut out of `[dep − 3 h, dep + 0.5 h]`, `[arr − 0.75 h, arr + 1.5 h]` and (default
     `blockLayoverSleep`) `[layover start − 0.75 h, next departure + 0.5 h]` for layovers < 24 h;
   * **seek bright light** `[T, T + 6 h]` (advance) or `[T − 6 h, T]` (delay), minus sleep;
   * **avoid light** `[T − 8 h, T]` (advance) or `[T, T + 8 h]` (delay), minus sleep;
   * **see light** (lower priority, `seeLightLength` 4 h) right after/before the seek window;
   * **melatonin** (advance plans, opt-in): 0.5 mg at `T − 9 h`; if that falls in sleep, at bedtime − 15 min when
     bedtime ∈ `[T − 13, T − 7]`, otherwise skipped; stops once adapted. Delay melatonin exists only behind
     `PlannerConfig.melatoninForDelay` (default off; low evidence);
   * **peak fatigue** `[T − 2 h, T + 2 h]` minus sleep;
   * **caffeine** in awake body-night `[T − 7, T + 3]`, never later than sleep − 6 h (− 8 h on advance plans),
     plus an *avoid caffeine* window up to the next sleep;
   * **nap** ≤ 30 min, ≥ 6 h before the next sleep, inside an avoid window (delay plans: siesta at wake + 7 h).
     It is labelled `Nap` when the waking period exceeds the habitual one by ≥ 1 h, else `OptionalNap`.
4. **Step.** `φ ← φ + sgn·min(cap, |φ* − φ|)`, `T ← T + 24 − step`, with caps pre-flight 1.0 h advance /
   1.5 h delay (2.0 with a light box), ≤ 3 h in total and never making the departure-day wake later than
   dep − 3 h; post-departure 1.5 h advance / 2.0 h delay. Stops at the destination once `|φ* − φ| ≤ 0.5 h`.

**Multi-leg itineraries (§10).** Layovers < 24 h are pass-through. Stops of 24–72 h that lie *against* the
direction of travel hold the clock (no shift while there). Stops ≥ 72 h start a new segment that continues from
the body clock the previous segment reached, with `floor(stay / 24) − 2` (capped at `preFlightDays`) pre-flight
days before its first departure; the previous segment stops shifting when those begin.

**Can't sleep on planes.** In-flight sleep is replaced by *avoid light* with reason `RestInFlight` ("rest in the
dark with an eye mask"); the ODE treats it as dim (5 lux) instead of dark.

### 4.1 Card assembly and the practicality filter

1. **Resolve** cross-cycle overlaps with the validator's precedence: sleep > rest-in-flight > avoid light >
   naps > seek bright light > see light; avoid caffeine > caffeine; everything loses to sleep. After this step
   no two conflicting cards overlap (property-tested).
2. **Clip** at the return departure (if any); flight markers are kept whole.
3. **Practicality filter** (§12.3 UI mapping): merge same-type windows < 15 min apart (unless a conflicting card
   sits in the gap), round to 15 min, drop light windows < 30 min. The ODE always sees the raw windows.
4. **Calendar days** (§10.5): pre-trip days are home-zone calendar days; a *Travel* day runs from local midnight
   of the departure date to the arrival (or to the next local midnight when landing at/after 18:00); later days
   are calendar days in the destination zone, the first one starting at the arrival. Groups of legs separated by
   ≥ 24 h get their own travel day. Day 0 is the first travel day; post-arrival days are `Adapted` once the phase
   track is within 0.5 h of the segment target at mid-day, otherwise `Arrival`. `JetLagPlan.daySpans()`
   reconstructs exactly the same spans from a stored plan (property-tested against the builder).
5. **Ids** are the first 8 bytes (hex) of SHA-256(`tripId|type|dayIndex|n`), `n` = occurrence of that type within
   the day. They are deterministic and independent of `now`, and survive re-plans that keep a card's day.

### 4.2 Phase trajectory

`JetLagPlan.phase` holds one point per hour from the first day's start to the last day's end. The body offset is
linearly interpolated between the planned CBTmins (each cycle's `home + φ`); the nearest CBTmin comes from the
cycle series, continued every 24 h before, after and between segments. Offsets are stored continuously when they
fit `ZoneOffset`'s ±18 h, otherwise wrapped per point into (−12, 12] — consumers interpolate along the shortest
arc (`JetLagPlan.bodyOffsetAt`, `adaptationProgressAt`).

## 5. Parameters

Defaults are those of [`science.md` §12.2](science.md#122-parameters-all-configurable-values-used-for-the-fixtures).
Evidence: **A** meta-analysis/multiple RCTs, **B** controlled lab studies/single RCTs, **C** consensus/protocol
reviews/modelling, **D** opinion; "eng." = engineering choice.

| `PlannerConfig` | Default | Meaning | Evidence |
|---|---|---|---|
| `preAdvanceCap` | 1.0 h | pre-flight advance per cycle | B |
| `preDelayCap` / `preDelayCapLightBox` | 1.5 / 2.0 h | pre-flight delay per cycle | B/C |
| `postAdvanceCap` / `postDelayCap` | 1.5 / 2.0 h | post-departure shift per cycle | B/C |
| `preMaxShift`, `preFlightDays` | 3 h, 3 days | total pre-flight shift / days | C |
| `advanceThresholdEarly/Neutral/Late` | 10 / 9 / 8 h | advance if A ≤ threshold | C |
| `cbtFromMidEarly/Neutral/Late` | 0.5 / 1.0 / 1.5 h | CBTmin after mid-sleep | B |
| `seekLength`, `avoidLength` | 6 h, 8 h | light windows from T | B, B/C |
| `seeLightLength` | 4 h | lower-priority light window | eng. |
| `maxSleepDeviation` | 3 h | post-arrival onset clamp | C |
| `melatoninAdvanceOffset`, window | −9 h, [−13, −7] h | 0.5 mg advance dose | B (clinical A) |
| `melatoninForDelay`, `melatoninDelayOffset` | false, +3.5 h | delay dose at ≈ wake | C (low) |
| `caffeineCutoff` / `caffeineCutoffAdvance` | 6 h / 8 h | no caffeine before sleep | B |
| `napLength`, `napMinBeforeSleep` | 0.5 h, 6 h | nap rules | B / C |
| `napRecommendedExtraWake` | 1 h | `Nap` vs `OptionalNap` | eng. |
| `peakFatigueBefore/After` | 2 h / 2 h | circadian low around T while awake | B/C |
| `shortTripHours` | 72 h | home-time mode, segment split | C |
| `preDepartureWake`, `postDepartureNoSleep` | 3 h, 0.5 h | transit sleep blocks | C |
| `arrivalSleepEnd`, `postArrivalNoSleep` | 0.75 h, 1.5 h | transit sleep blocks | C |
| `blockLayoverSleep` | true | also block sleep across < 24 h layovers | eng. (reference: false) |
| `minShift`, `doneTolerance`, `maxCycles` | 2 h, 0.5 h, 30 | no-plan threshold, completion, safety stop | C |
| `lightBox` | false | night seek at 5000 lux, faster pre-delay | B/C |
| `tier2DirectionChoice`, `tier2Min/Max`, `tier2TieDays` | false (Max: true), 8–12 h, 1 day | model-chosen direction | C (modelling) |
| `estimateModel`, `estimateHorizonDays` | Hannay19, 21 days | estimates | C (modelling) |
| `roundingMinutes`, `mergeGapMinutes`, `minSeekMinutes` | 15, 15, 30 | practicality filter | C / eng. |
| `naturalDriftPerDay`, `noPlanSleepNights` | 1 h/day, 3 | no-plan mode | C |

### 5.1 Intensity and preferences (`PlannerConfig.forProfile`)

| Profile | Effect |
|---|---|
| `Gentle` | ≤ 2 pre-flight days and ≤ 2 h in total, pre-flight delay ≤ 1 h/day (1.5 with box), post-departure ≤ 1.0 h/day advance and ≤ 1.5 h/day delay. |
| `Balanced` | Defaults. |
| `Max` | Defaults + Tier-2 direction choice for 8–12 h eastward shifts. |
| `adjustBeforeDeparture = false` | No pre-flight days. |
| `useMelatonin = false` (default) | No melatonin cards. Melatonin is opt-in behind a safety note in the UI. |
| `useCaffeine = false` | No caffeine or avoid-caffeine cards. |
| `canSleepOnPlanes = false` | In-flight sleep becomes rest-in-the-dark. |

## 6. Estimates and validation (§13)

The validator turns a plan into lux(t) with the §4.4 table (sleep 0, avoid 10 / in-flight 5, seek 3000 by day,
500 at night or 5000 with a box, in-flight seek 1000, flight 100, neutral 250, outdoor 3000 between 12:00 and
14:00 local), integrates the model with RK4 (dt = 0.1 h on an integer step grid) after a shared 40-day warm-up on
the habitual "typical day", reads out CBTmins, and reports **days to adapt** = time from arrival to the first
CBTmin that starts a run of three within ±1 h of the model's own entrained CBTmin on destination time.
`estimatedDaysWithoutPlan` simulates the typical day on the clock of wherever the traveller is (home, layover
city, destination) with 100 lux in flight.

* **Model choice.** Estimates use **Hannay19** (Hannay, Booth & Forger 2019) by default: it is the Tier-2 model
  in §13.7, numerically less stiff than Forger99 at dt = 0.1 h, and gives finite no-plan values for all fixtures.
  Forger99 is available via `EstimateModel.Forger99` and is tested against the same fixtures.
* **Not adapted within the horizon** (`arr + 21 days`) is reported as 21.0 rather than infinity, so plans stay
  JSON-serialisable.
* Layovers are simulated on the ground at the layover city's clock (the reference treats the whole journey as
  "in flight").

### 6.1 Test coverage

* `CircadianModelTest`: §4.6 reference fixtures for both models (entrained CBTmin, dt sweep, constant-lux rows,
  100 000-lux stability).
* `CyclePlannerGoldenTest`: byte-for-byte equality with `ex_neutral.txt`, `ex_early.txt`, `ex_late.txt`.
* `PlanValidatorTest`: §14 estimates (single-leg within 0.05 d of an integer-step run of the Python reference;
  §14.2 within ±1 d), direction checks, plan faster than no plan.
* `DefaultJetLagPlannerTest`: the §14.1–14.4 tables at planner level (sleep, seek, avoid, melatonin windows in local
  time; days and kinds; phase; estimates), §14.5 planner cases (date line, no plan, home time, overrides),
  profile mapping, ids, robustness and a < 300 ms performance budget (measured ≈ 10 ms for a Tier-2 multi-leg plan
  on a laptop JVM).
* `PureLogicTest`: §14.5 pure logic (norm12, CBTmin estimate, direction thresholds, chronotype scoring).
* `PlannerPropertiesTest` (kotest-property, 400 realistic + 200 malformed itineraries across ±14 h, +5:45,
  +12:45, +8:45, −3:30 zones and DST): never throws; finite estimates; ids unique and deterministic; cards sorted;
  no conflicting cards overlap (sleep vs light, light vs dark, caffeine vs avoid caffeine); every card inside its
  day; `daySpans()` equals the builder's spans; hourly phase; no cycle shifts the clock by more than 2 h; with no
  return flight the final body clock is within 0.5 h of destination time. All extreme zone pairs are planned in
  both directions.
* `BodyClockTest`: read-side helpers.

## 7. Known limitations

* Tier-2 direction choice applies to the first segment only; later segments use the threshold rule.
* The chronotype → phase mapping is population-level (±1.5 h individual error, §8). There is no re-estimation
  from logged light yet ("couldn't do" re-planning is a UI feature that re-runs the planner).
* Hannay19 adapts faster when advancing; for large westward shifts that the threshold rule delays (e.g.
  NRT → JFK neutral) the model can estimate the plan as *slower* than doing nothing. The plan still follows the
  literature default; Max intensity lets the model choose for 8–12 h.
* The 24 h-gap filling of the CBTmin series between segments assumes the clock does not move during a stay.
* Stored phase offsets may wrap from +12 h to −12 h for trips across the date line; always interpolate along the
  shortest arc.
* Fixed commitments at the destination (§12.1 "optional") are not modelled.

## 8. References

Full list with DOIs in [`science.md` §16](science.md#16-references-dois-verified-against-pubmedpublisher-records-during-this-research-unless-marked).
Key sources for the implemented rules:

* Khalsa SBS et al. *J Physiol* 2003;549:945 — light PRC (seek/avoid windows).
* St Hilaire MA et al. *J Physiol* 2012;590:3035 — 1 h light PRC.
* Eastman CI, Burgess HJ. *Sleep Med Clin* 2009;4:241 — shift caps, pre-flight protocol, direction choice.
* Burgess HJ et al. *J Biol Rhythms* 2003;18:318; Eastman CI et al. *Sleep* 2005;28:33 — pre-flight advance.
* Burgess HJ et al. *J Clin Endocrinol Metab* 2010;95:3325 — 0.5 mg vs 3 mg melatonin PRC.
* Herxheimer A, Petrie KJ. Cochrane 2002 CD001520 — melatonin for jet lag.
* Drake C et al. *J Clin Sleep Med* 2013;9:1195; Burke TM et al. *Sci Transl Med* 2015;7:305ra146 — caffeine.
* Brooks A, Lack L. *Sleep* 2006;29:831 — nap length.
* Baehr EK et al. *J Sleep Res* 2000;9:117; Roenneberg T et al. *Curr Biol* 2004;14:R1038 — chronotype and phase.
* Sack RL. *N Engl J Med* 2010;362:440; Waterhouse J et al. *Lancet* 2007;369:1117 — short trips, practice.
* Forger DB, Jewett ME, Kronauer RE. *J Biol Rhythms* 1999;14:532 — Forger99 model.
* Hannay KM, Booth V, Forger DB. *J Biol Rhythms* 2019;34:658 — Hannay19 model.
* Dean DA, Forger DB, Klerman EB. *PLoS Comput Biol* 2009;5:e1000418 — model-based schedule design.
