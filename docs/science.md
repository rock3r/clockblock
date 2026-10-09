# Circadian Science for Clockblock — Research Report & Algorithm Specification

*Prepared for the Clockblock Android app (open source, fully offline, Kotlin). Version 1.0, 2026-10-04.*

> [!CAUTION]
> **Not medical advice.** The app gives general information about circadian timing, not a diagnosis or treatment. Melatonin is a drug, and how it is regulated varies by country (US: dietary supplement sold over the counter; UK: prescription only; EU: varies, with low doses sometimes sold as food supplements and higher doses classed as medicines; Australia: prescription, except one pharmacist-only 2 mg prolonged-release product for people aged 55+; Canada: natural health product; Japan: not approved). People who are pregnant or breastfeeding, have epilepsy, take anticoagulants (warfarin), take fluvoxamine or other CYP1A2 inhibitors, take immunosuppressants, have an autoimmune disease, or are children should not take melatonin unless a clinician says so. Do not drive or operate machinery for several hours after taking melatonin or after short sleep. The app must say all this clearly and let users switch melatonin and caffeine advice off.

Companion reference code (pure Python, used to produce every number in this report): [`research/reference/`](research/reference/) (`sim.py` = Forger99 and Hannay19 RK4 with CBTmin detection; `planner.py` = rule-based reference planner; `validate.py` = turns a plan into lux(t) and simulates it with both ODEs; `examples.py` = the 4 worked itineraries; `jl.py` and `sweep.py` = no-intervention and advance-vs-delay sweeps; `ex_*.txt` = planner outputs for each chronotype).

---

## 0. Executive summary (what the app should do)

1. **Track one quantity: the body-clock phase**, stored as the clock time of the core body temperature minimum (**CBTmin**, written *T*). The other markers follow from it: DLMO ≈ T − 7 h. For a habitual 23:00–07:00 sleeper with a neutral chronotype, T ≈ 04:00 and DLMO ≈ 21:00 (Roach & Sargent 2019; Eastman & Burgess 2009).
2. **Light is the main tool. When light lands relative to T sets the direction of the shift:**
   - Light in roughly the 6 h *after* T **advances** the clock (it moves earlier, as needed for eastward travel).
   - Light in roughly the 6 h *before* T **delays** it (it moves later, as needed for westward travel).
   - The crossover is close to T. The human light PRC has **no long dead zone** (Khalsa 2003). The effect saturates with intensity (half-maximal at about 100 lux; Zeitzer 2000) and with duration (the first minutes count most; Chang 2012).
3. **Melatonin works the opposite way to light.** It advances when taken in the afternoon or evening, before DLMO (peak about 5 h before DLMO for 3 mg and 2–4 h before for 0.5 mg), and delays when taken in the morning (Burgess 2008, 2010). The default is **0.5 mg fast-release, only for advancing**, at T − 9 h.
4. **Shift rates for planning:** pre-flight 1 h/day advance or 1.5–2 h/day delay. After arrival, 1.5 h/day advance and 2 h/day delay when the plan is followed (Eastman & Burgess 2009). With no intervention, expect about 57 min/day eastward and 92 min/day westward (Aschoff, cited by E&B 2009).
5. **Direction:** if the eastward shift A ≤ 9 h (neutral chronotype; 10 h early types, 8 h late types), advance. Otherwise delay by 24 − A. The literature is more cautious (E&B: delay when ≥8 h east), while both ODE models favour advancing up to 11–12 h when light is structured. See §5.4. Make the threshold configurable, and optionally let the ODE choose (Tier 2).
6. **Sleep, caffeine and naps are secondary rules:**
   - Sleep follows the shifting clock, limited to ±3 h of the destination's habitual time after arrival.
   - Caffeine is allowed from waking until 6 h before sleep (8 h when advancing). It is suggested for the "body-night" while awake.
   - One nap of ≤30 min, at least 6 h before sleep, preferably during an avoid-light window.
7. **Short trips (< 72 h at the destination) and shifts < 2 h get no plan to adapt.** For short trips, use a "stay on home time" mode instead.
8. **Validation:** a pure-Kotlin RK4 implementation of Forger99 and Hannay19 (dt = 0.1 h, integer step counter). It is used (a) in unit tests to check that generated plans shift the model clock in the right direction and faster than no plan, and (b) optionally on device, to pick advance or delay and to re-estimate phase from logged light.

---

## 1. Phase markers and conventions

| Marker | Definition | Typical clock time (23:00–07:00 sleeper) | Relation |
|---|---|---|---|
| DLMO | Dim-light melatonin onset (saliva/plasma, ~3–4 pg/mL saliva threshold) | ~21:00 | ≈ 2 h before habitual sleep onset (Sletten 2010; Burgess & Eastman 2005) |
| CBTmin (T) | Minimum of core body temperature (constant routine) | ~04:00–05:00 | T ≈ DLMO + 7 h (E&B 2009; Benloucif 2005); T ≈ 3–4.5 h before habitual wake for 8 h sleep (E&B 2009) |
| Mid-sleep | Midpoint of habitual sleep | 03:00 | T ≈ mid-sleep + 0.5…1.5 h (chronotype dependent; Baehr 2000, Duffy 1999) |
| Light PRC crossover | Switch between delays and advances | ≈ T (Khalsa 2003) | Sometimes quoted as "~06:00 for a 23:00–07:00 sleeper" (Timeshifter), i.e. T plus a margin |

**Sign convention used throughout:**
- φ = cumulative phase shift in hours. φ > 0 means **advance** (T moves earlier on the home clock); φ < 0 means **delay**.
- Time-zone difference Δ = dest UTC offset − home UTC offset, normalised to (−12, +12]. Δ > 0 means eastward.
- Target φ* = Δ for an advance plan and Δ − 24 (or Δ, if Δ < 0) for a delay plan.

Baehr 2000 (n = 172, constant-routine-like CBT): Tmin at 03:50 for morning types, 05:02 for neither types and 06:01 for evening types. The Tmin-to-wake interval is longer in morning types, who wake at a later circadian phase (Duffy 1999).

---

## 2. Light: phase response curves and dose–response

### 2.1 Primary PRCs

| Study | Stimulus | Key result | DOI |
|---|---|---|---|
| **Khalsa et al. 2003**, J Physiol 549:945 | 6.7 h, ~10,000 lux, after ~2.5 days of constant routine; n = 21 | Type 1 PRC. Peak-to-trough amplitude **5.02 h** (≈ −3.4 h delay / +2.0 h advance). **Crossover at CBTmin.** Max delays ~3–4 h before T, max advances ~2–3 h after T. **No prolonged dead zone.** | 10.1113/jphysiol.2003.040477 |
| **St Hilaire et al. 2012**, J Physiol 590:3035 | 1 h, ~8,000 lux | Amplitude **2.20 h**, i.e. ~40% of the 6.7 h PRC using 15% of the duration. Same shape and timing. | 10.1113/jphysiol.2012.227892 |
| Minors, Waterhouse & Wirz-Justice 1991, Neurosci Lett 133:36 | 3 h bright light, 3 cycles | Classic PRC; max ~2 h per pulse; crossover near T | (DOI not verified) |
| **Revell et al. 2012**, J Physiol 590:4859 | Blue-enriched LED box (~185 lux), 3 days of 1 h × 2/day | Advance region extends broadly from morning into afternoon; delays in evening | 10.1113/jphysiol.2012.235416 |
| **Rüger et al. 2013**, J Physiol 591:353 | 6.5 h of 480 nm, 11.2 lux | PRC −2.6 h / +1.3 h, ≈ 75% of the 10,000 lux white-light PRC → melanopsin sensitivity | 10.1113/jphysiol.2012.239046 |

**Rule of thumb for the app (evidence B):**
- **Delay zone:** T − 6 h … T, strongest at T − 3 h ± 2 h.
- **Advance zone:** T … T + 6 h, strongest at T + 3 h ± 2 h.
- Light 6–12 h from T still has small effects of matching sign; the curve passes through a weak "neutral" region around T ± 12 h (midday for a normal sleeper). This is low gain, not a true dead zone, so midday light is harmless rather than useless. Roach & Sargent 2019: "largest shifts 3–6 h either side of CBTmin".

### 2.2 Intensity (dose–response)

- **Zeitzer et al. 2000**, J Physiol 526:695 (doi 10.1111/j.1469-7793.2000.00695.x): a single 6.5 h pulse in the early biological night gives a logistic dose–response for phase delay. **Half-maximal at ~100 lux** (≈1% of 9,000 lux). **Saturation from ~550 lux** (about 90% of the maximum delay). Ordinary room light (~180 lux) gives roughly half the effect of 9,000 lux.
- Martin & Eastman 1998 (Sleep 21:154): in a field shift protocol, medium light (~1,230 lux) was about as effective as high light (~5,700 lux).
- Melatonin suppression is even more sensitive: **ED50 24.6 lux**, with individual ED50s ranging from ~6 to ~350 lux (Phillips et al. 2019, PNAS 116:12019, doi 10.1073/pnas.1901824116).
- Room light (<200 lux) before bed delays melatonin onset and shortens melatonin duration by ~90 min (Gooley et al. 2011, JCEM 96:E463, doi 10.1210/jc.2010-2098).

**Implication:** "avoid light" must mean **really dim (≤10 lux at the eye)**. Ordinary indoor evening light (100–300 lux) is already near-maximally effective in the delay zone. "Seek light" ideally means ≥1,000 lux (outdoors), and indoor light of ~250+ lux melanopic EDI still helps.

### 2.3 Duration and pattern

- **Chang et al. 2012**, J Physiol 590:3103 (doi 10.1113/jphysiol.2011.226555): ~10,000 lux in the early biological night. **0.2 h (12 min) gave a 1.07 h delay**, 1 h gave ~2.2 h, and **4 h gave 2.65 h**. The 12-min pulse was >5× more effective per minute. Response vs duration is strongly non-linear and saturates.
- **Rimmer et al. 2000**, Am J Physiol 279:R1574 (doi 10.1152/ajpregu.2000.279.5.R1574): intermittent bright light (31% or 63% duty cycle over 5 h) was nearly as effective as continuous light.
- **Crowley & Eastman 2015**, Sleep Med 16:288 (doi 10.1016/j.sleep.2014.12.004): morning light for advancing. 2 × 30 min gave 2.4 h over 4 days; a single 30 min dose gave ~1.8 h (75% of the effect).
- **Burgess et al. 2003**, JBR 18:318 (doi 10.1177/0748730403253585): sleep advanced 1 h/day for 3 days plus 3.5 h of morning light (>3,000 lux). DLMO advance was 0.6 h in dim light, 1.5 h with intermittent bright light and 2.1 h with continuous bright light.

**Implication:**
- Seek windows can be presented as "**at least 30–60 min, more is better, intermittent is fine**".
- The plan does not need 6 continuous hours. A short 30–60 min block near the peak (T ± 3 h) gets most of the effect.
- The avoid window matters as much as the seek window.

### 2.4 Spectrum

- Melanopsin ipRGCs (peak ~480 nm) dominate long exposures. Lockley et al. 2003 (JCEM 88:4502, doi 10.1210/jc.2003-030570): 460 nm produced **2×** the delay of equal-photon 555 nm light.
- Cones contribute at the start of exposure and at low irradiance (Gooley et al. 2010, Sci Transl Med 2:31ra33, doi 10.1126/scitranslmed.3000741).
- **Brown et al. 2022 consensus**, PLoS Biol 20:e3001571 (doi 10.1371/journal.pbio.3001571), using CIE S 026 melanopic EDI (vertical plane, eye level):
  - **≥250 lux melanopic EDI during the day.**
  - **≤10 lux starting at least 3 h before bed.**
  - **≤1 lux during sleep** (≤10 lux if some light is needed).
- Converting photopic lux to melanopic EDI uses the melanopic daylight efficacy ratio (DER): daylight (D65) ≡ 1.0; cool-white LED (~6,500 K) ≈ 0.9; neutral-white (~4,000 K) ≈ 0.6; warm-white (~2,700–3,000 K) ≈ 0.4–0.5. These are approximate, derived from the CIE S 026 toolbox.
- **Practical implication for the app:**
  - Daylight is the best "seek" source.
  - Warm, dim light is the best "avoid" condition.
  - Dark sunglasses (category 3: 8–18% transmission) or amber/blue-blocking glasses help in avoid windows. E&B 2009 recommend dark sunglasses; Timeshifter states that blue blockers are less effective than dark sunglasses.

### 2.5 Light units cheat-sheet (photopic lux at the eye, approximate)

| Condition | Lux |
|---|---|
| Moonless night / dark bedroom with blackout | <0.1–1 |
| Dim "avoid" conditions (one warm lamp, screen dimmed, sunglasses) | ≤10 |
| Typical home evening | 50–200 |
| Aircraft cabin, cruise / night mode | ~50–300 / <10 |
| Office | 300–500 |
| Light box (10,000 lux rated at its specified distance, often 30–60 cm; falls off steeply with distance) | 2,500–10,000 |
| Outdoors, overcast / in shade | 1,000–10,000 |
| Outdoors, direct sun | 10,000–100,000+ |

### 2.6 The light response curve card (what the app draws)

The Why sheet of every light block (See bright light, See some light, Avoid light) and About ("How the plan works")
show a schematic light PRC that the user can explore by dragging a sun along it. It is a teaching aid. **The planner
does not use it**: the planner's light windows come from §12. The card's model is
`LightResponseCurve` in `:core:designsystem` (unit-tested in `LightResponseCurveTest`):

- The x axis is hours from CBTmin (T), −12…+12 h. The curve repeats every 24 h.
- Shape after **Khalsa 2003**. Crossover at T. Delays before T, peaking at **−3.4 h at T − 3.5 h**; advances after
  T, peaking at **+2.0 h at T + 2.5 h**. So the delay lobe is the larger one. Each lobe is a skewed bump `u·(1−u)^b`
  (u = |x| / 12, b set by where the peak falls), scaled to its peak. Both lobes reach zero only at T ± 12 h, which is
  the weak region. There is no long dead zone: |shift| > 0.2 h for 0.5 h ≤ |x| ≤ 8 h.
- The readout is qualitative, never a number of hours. "Strongly" means |shift| ≥ 1.5 h, "barely" means < 0.3 h,
  and anything between is "a little". The card cites Khalsa 2003 and **St Hilaire 2012**: a 1 h pulse gives the
  same shape and timing at about 40 % of the amplitude, so these words describe direction and relative strength,
  not a prediction for a given exposure.
- In the Why sheet, the block is drawn as a band, placed in hours from the CBTmin estimate nearest to it
  (`PhasePoint.cbtMin` of the plan's body-clock track). Every See light block of a real plan lands on the side that
  matches its reason (`LightCurveWindowTest`).
- Not medical advice. Individual curves vary, and CBTmin is itself an estimate (§8).

---

## 3. Melatonin

### 3.1 PRCs

| Study | Dose | Advance peak | Delay peak | Size | DOI |
|---|---|---|---|---|---|
| **Burgess et al. 2008**, J Physiol 586:639 | 3 mg, 3 days, free-running ultradian protocol | ~**5 h before DLMO** (~early–mid afternoon for a 23:00 sleeper) | ~**11 h after DLMO** (≈ 4 h after T, around habitual wake) | max +1.8 h / −1.3 h over 3 days; dead zone covers roughly the first half of habitual sleep | 10.1113/jphysiol.2007.143180 |
| **Burgess et al. 2010**, JCEM 95:3325 | 0.5 mg vs 3 mg | 0.5 mg: **2–4 h before DLMO** (9–11 h before mid-sleep). 3 mg peak earlier | delays shortly after wake | 0.5 mg ≈ 3 mg in maximum advance | 10.1210/jc.2009-2590 |
| Lewy et al. 1998, Chronobiol Int 15:71 | 0.5 mg | Advances when taken in afternoon/evening; delays when taken in morning | — | — | 10.3109/07420529808998671 |

**Eastman & Burgess 2009 working estimates (evidence B):**

| Dose | Before DLMO | Before habitual sleep onset | Before T | Tolerance |
|---|---|---|---|---|
| 3 mg | 5 h | 7.5 h | 12 h | ±2 h still effective |
| 0.5 mg | 2 h | 4.5 h | 9 h | ±2 h |

- A higher dose produces higher and longer-lasting blood levels, which spill into the delay zone. That is why low doses are preferred for advancing.
- There is **no evidence that melatonin adds to light-induced delays** (E&B 2009). Roach & Sargent 2019 nevertheless suggest 3 mg 4 h after T for westward travel. Because the evidence is weak and morning drowsiness is a risk, the app makes delay melatonin **off by default, opt-in only**.
- Melatonin plus light gives additive advances. Revell et al. 2006 (JCEM 91:54, doi 10.1210/jc.2005-1009): an advancing sleep schedule plus morning light plus 0.5 mg or 3 mg melatonin gave **2.5 h and 2.6 h** over 3 days, vs 1.7 h with placebo. Paul et al. 2011 (Psychopharmacology, doi 10.1007/s00213-010-2059-5) found melatonin and light additive.

### 3.2 Clinical evidence for jet lag

- **Cochrane review (Herxheimer & Petrie 2002**, doi 10.1002/14651858.CD001520):
  - 9 of 10 trials found melatonin, taken close to target bedtime at the destination (22:00–24:00), reduced jet lag after ≥5 time zones, especially eastward. NNT = 2.
  - 0.5–5 mg had similar effects. 5 mg helped people fall asleep faster; >5 mg was no better.
  - Cautions: epilepsy, warfarin.
- **AASM practice parameters 2007** (Morgenthaler et al., Sleep 30:1445, doi 10.1093/sleep/30.11.1445; review by Sack et al. 2007, doi 10.1093/sleep/30.11.1460):

  | Intervention for jet lag disorder | AASM 2007 level |
  |---|---|
  | Melatonin at the appropriate time | **Standard** |
  | Planned sleep schedules | Option |
  | Timed light exposure | Option |
  | Short-term hypnotics | Option |
  | Stimulants such as caffeine | Option |

### 3.3 Dose, formulation and safety rules

- **Default: 0.5 mg fast-release.** Allowed range 0.5–3 mg; never suggest more than 5 mg. Use fast-release, not prolonged-release: PR formulations extend into the delay zone. Timeshifter recommends "low-dose (1–3 mg) fast-release".
- **Product quality varies widely.** Measured content was −83% to +478% of the label, and some products contained serotonin (Erland & Saxena 2017, JCSM 13:275, doi 10.5664/jcsm.6462). Gummies are worse: 74–347% of label (Cohen et al. 2023, JAMA 329:1401, doi 10.1001/jama.2023.2296). The app can suggest pharmaceutical-grade or certified (e.g. USP-verified) products where available.
- **Common side effects:** drowsiness, headache, dizziness, nausea. Do not drive for ~5–8 h after a dose (Timeshifter uses 8 h).
- **Contraindications and interactions (show at opt-in):**
  - pregnancy or breastfeeding
  - epilepsy
  - warfarin or other anticoagulants
  - fluvoxamine (CYP1A2 inhibition raises melatonin levels many-fold)
  - immunosuppressants
  - autoimmune disease
  - other sedatives or alcohol
  - children
  - "talk to your doctor if on any prescription medication"
- **Legality:** show a country notice (see the disclaimer at the top) and do not give purchasing advice.

---

## 4. Mathematical models of the human circadian pacemaker

All models below take **light intensity I(t) in photopic lux at the eye** and use **time in hours**. They share Kronauer's "Process L": a photoreceptor activation variable n ∈ [0, 1] that is used up by light and recovers in darkness, so the drive saturates under sustained light. That is how the models reproduce the Chang 2012 duration non-linearity. The constants below come from the original papers and the reference implementation maintained by Forger's group (Arcascope `circadian` Python package, `circadian/models.py`, github.com/Arcascope/circadian). They were double-checked against St Hilaire 2007 (PMC3123888) for Process L.

### 4.1 Forger, Jewett & Kronauer 1999 ("Forger99", simpler Kronauer model) — *recommended Tier-1 validator*

Forger DB, Jewett ME, Kronauer RE. J Biol Rhythms 14:532–537 (1999). doi 10.1177/074873099129000867.

State (x, x_c, n). x ≈ normalised CBT rhythm, x_c = complementary variable, n = fraction of photoreceptors that are "used".

```
α(I)  = α0 · (I / I0)^p                       (α = 0 when I = 0)
B̂     = G · (1 − n) · α(I)
B     = B̂ · (1 − 0.4·x) · (1 − 0.4·x_c)        (circadian sensitivity modulation)

dx/dt   = (π/12) · ( x_c + B )
dx_c/dt = (π/12) · ( μ·(x_c − (4/3)·x_c³) − x · ( (24 / (0.99669·τx))² + k·B ) )
dn/dt   = 60 · ( α(I)·(1 − n) − β·n )
```

| Param | Value | Meaning |
|---|---|---|
| τx | 24.2 h | intrinsic period (Czeisler 1999 mean 24.18 h, doi 10.1126/science.284.5423.2177) |
| μ | 0.23 | van der Pol stiffness |
| k | 0.55 | light modulation of period |
| G | 33.75 | drive gain |
| α0 | 0.05 | photoreceptor activation rate at I0 |
| β | 0.0075 | photoreceptor recovery rate (per min → ×60 per h) |
| p | 0.5 | intensity exponent |
| I0 | 9500 lux | reference intensity |

- Initial condition (Arcascope, at midnight, entrained to 16 h light : 8 h dark): (x, x_c, n) = (−0.0843259, −1.09607546, 0.45584306). In practice, warm up for ≥30–40 days on the user's habitual schedule, after which the IC no longer matters.
- **CBTmin readout:** CBTmin = time of the local minimum of x (Arcascope convention). Use a minimum peak separation of ~13 h to suppress spurious shallow minima during strong light. Alternative conventions:
  - Jewett 1999: CBTmin = time of x minimum + 0.8 h.
  - St Hilaire 2007 / Kronauer: CBTmin occurs when atan2(x_c, x) = −170.7°, plus φ_ref = 0.97 h.
  - Pick one convention and use it consistently. All fixtures in this report use **x-min**. Plan validation uses *relative* shifts, so the constant offset cancels.
- DLMO = CBTmin − 7 h (Arcascope convention; matches E&B 2009).

### 4.2 Hannay, Booth & Forger 2019 single-population macroscopic model ("Hannay19 SP") — *recommended Tier-2 / second opinion*

Hannay KM, Booth V, Forger DB. "Macroscopic models for human circadian rhythms." J Biol Rhythms 34:658–671 (2019). doi 10.1177/0748730419878298.

State (R, ψ, n). This is a Kuramoto/Ott–Antonsen reduction of a population of SCN oscillators: R = synchrony (amplitude), ψ = collective phase (rad, unwrapped).

```
α(I)  = α0 · I^p / (I^p + I0)
B̂     = G · (1 − n) · α(I)

L_R   = (A1/2)·B̂·(1 − R⁴)·cos(ψ + βL1) + (A2/2)·B̂·R·(1 − R⁸)·cos(2ψ + βL2)
L_ψ   = σ·B̂ − (A1/2)·B̂·(R³ + 1/R)·sin(ψ + βL1) − (A2/2)·B̂·(1 + R⁸)·sin(2ψ + βL2)

dR/dt = −γ·R + (K/2)·cos(β1)·R·(1 − R⁴) + L_R
dψ/dt = 2π/τ + (K/2)·sin(β1)·(1 + R⁴) + L_ψ
dn/dt = 60 · ( α(I)·(1 − n) − δ·n )
```

| Param | Value | | Param | Value |
|---|---|---|---|---|
| τ | 23.84 h | | A1 | 0.3855 |
| K | 0.06358 | | A2 | 0.1977 |
| γ | 0.024 | | βL1 | −0.0026 |
| β1 | −0.09318 | | βL2 | −0.957756 |
| σ | 0.0400692 | | G | 33.75 |
| α0 | 0.05 | | δ | 0.0075 |
| p | 1.5 | | I0 | 9325 |

- IC (Arcascope): (R, ψ, n) = (0.82041911, 1.71383697, 0.52318122).
- **CBTmin readout:** times where ψ crosses π (mod 2π); interpolate linearly between steps. DLMO = CBTmin − 7 h (equivalently ψ = 5π/12).
- τ = 23.84 h is not a mistake. In the SP model the light-free period is shifted by the coupling term (K/2)·sin(β1)·(1 + R⁴) < 0 and by σ.
- Two-population model parameters (ventral/dorsal SCN, for reference only): τV 24.25, τD 24.0, Kvv 0.05, Kdd 0.04, Kvd 0.05, Kdv 0.01, A1 0.440068, A2 0.159136, βL1 0.06452, βL2 −1.38935, σ 0.0477375.

### 4.3 Other models (for context; not required for v1)

| Model | Notes | DOI |
|---|---|---|
| Kronauer et al. 1999, JBR 14:500 | Introduced Process L. Photoreceptor time constants of minutes; explains why 30–80 min darkness gaps barely reduce effect | 10.1177/074873099129001073 |
| Jewett, Forger & Kronauer 1999, JBR 14:493 ("Jewett99") | Higher-order van der Pol: dx/dt = π/12·(x_c + μ(x/3 + 4x³/3 − 256x⁷/105) + B); dx_c/dt = π/12·(q·B·x_c − x·((24/(0.99729τ))² + k·B)). τ = 24.2, μ = 0.13, q = 1/3, k = 0.55, G = 19.875, α0 = 0.16, β = 0.013, p = 0.6, I0 = 9500, φ_ref = 0.8 h | 10.1177/074873049901400608 |
| St Hilaire et al. 2007, J Theor Biol 247:583 | Adds non-photic (sleep/wake) drive to Jewett99. Arcascope params: τ 24.2, G 37, k 0.55, μ 0.13, β 0.007, q 1/3, ρ 0.032, I0 9500, p 0.5, α0 0.1, φ_xcx −2.98, φ_ref 0.97. (Non-photic equations not re-verified for this report.) | 10.1016/j.jtbi.2007.04.001 |
| Dean, Forger & Klerman 2009, PLoS Comput Biol 5:e1000418 | Optimal light schedules for shift work / jet lag via optimal control | 10.1371/journal.pcbi.1000418 |
| Serkh & Forger 2014, PLoS Comput Biol 10:e1003523 | Optimal re-entrainment schedules (basis for Entrain). **Principles:** (1) for small shifts or dim light, *amplitude-preserving* schedules; (2) for large shifts with bright light, *shortest path* schedules that may pass through low amplitude; (3) sustained exposure beats short pulses; (4) when delaying, the optimal "day" is shorter | 10.1371/journal.pcbi.1003523 |
| Diekman & Bose 2018, J Theor Biol 437:261 | Entrainment maps; explains antidromic re-entrainment and direction dependence on departure time | 10.1016/j.jtbi.2017.10.002 |
| Lu, Klein-Cardeña & Wei 2016, Chaos 26:094811 | Freezing the clock (low amplitude) then restarting for jet lag | 10.1063/1.4954275 |

### 4.4 Light → drive conversion

- Inputs are **photopic lux at the eye**, vertical plane. If the app records melanopic EDI, feeding it in place of lux is a defensible approximation (models were fitted to broadband white light); document the choice.
- Clamp I to [0, 100,000]. Sleep or eyes closed = 0 lux. Closed eyelids transmit a few percent, which is negligible.
- **Saturation behaviour:**
  - Forger99: α ∝ √I has no hard saturation, but Process L limits sustained drive.
  - Hannay19 saturates: I^1.5/(I^1.5 + 9325) gives α ≈ ½α0 at I ≈ 443 lux, 0.25α0 at ~213 lux and 0.9α0 at ~1,900 lux.
  - These match the Zeitzer half-maximum at ~100 lux only approximately, so treat model results quantitatively with caution.
- **Default lux levels when simulating a plan** (used for all fixtures; `validate.py`):

  | Plan state | lux |
  |---|---|
  | sleep | 0 |
  | avoid window | 10 (in flight: 5) |
  | seek window, local daytime 07–19 | 3,000 |
  | seek window, local night (indoor/box) | 500 (5,000 with a light box) |
  | seek in flight | 1,000 |
  | neutral awake | 250 |
  | neutral awake 12:00–14:00 local (lunch outdoors) | 3,000 |
  | neutral in flight | 100 |

### 4.5 Numerics (Kotlin guidance)

- **Integrator:** classical RK4 with **dt = 0.1 h** (6 min). Light is piecewise-constant, sampled at step start.
- **Use an integer step counter** (`t = i * dt`), never `t += dt`.
  - Accumulated floating-point error moves light transitions by one step and shifts the entrained CBTmin by up to ~6 min. Measured: `t += dt` with dt = 0.1 gave F99 04:20 vs 04:14; with the integer counter, every dt from 0.25 to 0.01 h gives **04:14.2 (F99) / 03:59.8 (H19)** for the "typical day" schedule.
  - Alternatively, align light changes to step boundaries.
- **Stability:**
  - Process L is stiff under very bright light. For Forger99 at 100,000 lux, the n-equation rate is 60·(α + β) ≈ 10 h⁻¹.
  - **RK4 with dt = 0.5 h overflows** in that case; dt ≤ 0.25 h is stable. Keep dt = 0.1 h.
  - Hannay19's α saturates at α0, so it is less stiff.
- **Cost:** 54 days (40 warm-up + 14) at dt = 0.1 h ≈ 13,000 RK4 steps × 4 derivative evaluations ≈ under 5 ms on a phone. Running both directions × both models on device is cheap.
- **Warm-up:** 40 days on the habitual schedule (or 30 days from the published ICs). Cache the entrained state per (model, habitual schedule).
- **CBTmin detection:**
  - F99: discrete local minima of x, refined by parabolic interpolation over 3 points, then greedily de-duplicated (keep deepest first, minimum separation 13 h).
  - H19: crossing of ψ = π + 2πk, with linear interpolation.
- **Kotlin sketch:**

```kotlin
interface CircadianModel {
    val dim: Int
    fun deriv(s: DoubleArray, lux: Double, out: DoubleArray)
}

object Forger99 : CircadianModel {
    const val TAUX = 24.2; const val MU = 0.23; const val K = 0.55; const val G = 33.75
    const val ALPHA0 = 0.05; const val BETA = 0.0075; const val P = 0.5; const val I0 = 9500.0
    val IC = doubleArrayOf(-0.0843259, -1.09607546, 0.45584306)
    override val dim = 3
    override fun deriv(s: DoubleArray, lux: Double, out: DoubleArray) {
        val (x, xc, n) = Triple(s[0], s[1], s[2])
        val alpha = if (lux > 0) ALPHA0 * Math.pow(lux / I0, P) else 0.0
        val bHat = G * (1 - n) * alpha
        val b = bHat * (1 - 0.4 * x) * (1 - 0.4 * xc)
        val w = 24.0 / (0.99669 * TAUX)
        out[0] = Math.PI / 12 * (xc + b)
        out[1] = Math.PI / 12 * (MU * (xc - 4.0 / 3.0 * xc * xc * xc) - x * (w * w + K * b))
        out[2] = 60.0 * (alpha * (1 - n) - BETA * n)
    }
}

object Hannay19 : CircadianModel {
    const val TAU = 23.84; const val KC = 0.06358; const val GAMMA = 0.024; const val BETA1 = -0.09318
    const val A1 = 0.3855; const val A2 = 0.1977; const val BL1 = -0.0026; const val BL2 = -0.957756
    const val SIGMA = 0.0400692; const val G = 33.75; const val ALPHA0 = 0.05; const val DELTA = 0.0075
    const val P = 1.5; const val I0 = 9325.0
    val IC = doubleArrayOf(0.82041911, 1.71383697, 0.52318122)
    override val dim = 3
    override fun deriv(s: DoubleArray, lux: Double, out: DoubleArray) {
        val r = s[0]; val psi = s[1]; val n = s[2]
        val lp = if (lux > 0) Math.pow(lux, P) else 0.0
        val alpha = ALPHA0 * lp / (lp + I0)
        val bHat = G * (1 - n) * alpha
        val r4 = r * r * r * r; val r8 = r4 * r4
        val lR = A1 / 2 * bHat * (1 - r4) * Math.cos(psi + BL1) +
                 A2 / 2 * bHat * r * (1 - r8) * Math.cos(2 * psi + BL2)
        val lPsi = SIGMA * bHat - A1 / 2 * bHat * (r * r * r + 1 / r) * Math.sin(psi + BL1) -
                   A2 / 2 * bHat * (1 + r8) * Math.sin(2 * psi + BL2)
        out[0] = -GAMMA * r + KC / 2 * Math.cos(BETA1) * r * (1 - r4) + lR
        out[1] = 2 * Math.PI / TAU + KC / 2 * Math.sin(BETA1) * (1 + r4) + lPsi
        out[2] = 60.0 * (alpha * (1 - n) - DELTA * n)
    }
}

/** Classical RK4; light is constant over the step. t0 in hours (UTC epoch hours), integer stepping. */
fun integrate(m: CircadianModel, s0: DoubleArray, t0: Double, steps: Int, dt: Double,
              lux: (Double) -> Double, onStep: (Double, DoubleArray) -> Unit) {
    val s = s0.copyOf(); val k1 = DoubleArray(m.dim); val k2 = DoubleArray(m.dim)
    val k3 = DoubleArray(m.dim); val k4 = DoubleArray(m.dim); val tmp = DoubleArray(m.dim)
    for (i in 0 until steps) {
        val t = t0 + i * dt; val l = lux(t)
        m.deriv(s, l, k1)
        for (j in s.indices) tmp[j] = s[j] + dt / 2 * k1[j]; m.deriv(tmp, l, k2)
        for (j in s.indices) tmp[j] = s[j] + dt / 2 * k2[j]; m.deriv(tmp, l, k3)
        for (j in s.indices) tmp[j] = s[j] + dt * k3[j];     m.deriv(tmp, l, k4)
        for (j in s.indices) s[j] += dt / 6 * (k1[j] + 2 * k2[j] + 2 * k3[j] + k4[j])
        onStep(t0 + (i + 1) * dt, s)
    }
}
```

### 4.6 Model reference fixtures (unit tests for the Kotlin ODE port)

All runs use RK4 with the integer step counter, 60 days from the published IC, and report the CBTmin clock time on the last day. Unless stated otherwise, schedule = sleep 23:00–07:00 at 0 lux. **Tolerance ±0.1 h.**

| Schedule (awake light) | Forger99 CBTmin | Hannay19 CBTmin |
|---|---|---|
| 250 lux all day + 3,000 lux 12:00–14:00 ("typical day") | **04:14 (4.237 h)** | **04:00 (3.997 h)** |
| constant 100 lux (07–23) | 04:44 (4.73) | 04:10 (4.17) |
| constant 250 lux | 04:34 (4.56) | 04:29 (4.48) |
| constant 500 lux | 04:29 (4.48) | 04:43 (4.71) |
| constant 1,000 lux | 04:25 (4.42) | 04:50 (4.83) |

The constant-lux rows came from an earlier run with `t += dt`; allow ±0.15 h for those. The direction of the intensity trend differs between models: F99's CBTmin gets earlier with more light, H19's gets later. That is a known structural difference, so do not test cross-model agreement more tightly than ~±40 min.

**No-intervention jet lag** (`jl.py`):
- Setup: the clock switches to destination time at home-time midnight; from then on, sleep 23:00–07:00 local at 0 lux and 250 lux while awake.
- "Days" = days until CBTmin is within 1 h of the new entrained phase. "Antidromic" = the model re-entrained in the wrong direction.

| Shift | F99 days | H19 days |
|---|---|---|
| +3 h east | 4.1 | — |
| +6 h east | 7.0 | — |
| +8 h east | 8.9 | 6.9 |
| +9 h east | 10.9 | — |
| +10 h east | ~18–20, **antidromic** (delays) | 5.8 (advances) |
| +11 h east | ~18–20, **antidromic** | 12.7, **antidromic** |
| −3 h west | 4.3 | — |
| −6 h west | 8.4 | — |
| −8 h west | 10.5 | 8.5 |
| −10 h west | 13.6 | — |
| With outdoor light 09–17 (3,000 lux): +8 h | 5.9 | 5.9 |
| With outdoor light 09–17 (3,000 lux): +11 h | 11.7 (advance) | 6.7 (delay) |

These agree reasonably with the ~1 day per time zone rule of thumb and with E&B's field rates of 57 min/day advance and 92 min/day delay.

---

## 5. Practical protocols

### 5.1 Key protocol sources

| Source | Core recommendations | Evidence |
|---|---|---|
| **Eastman & Burgess 2009**, "How to travel the world without jet lag", Sleep Med Clin 4:241 (doi 10.1016/j.jsmc.2009.02.006, PMC2829880) | Goal: keep T inside the sleep period (sleep is good within ~6 h either side of T). **Pre-flight:** advance 1 h/day (2 h/day is no better and causes misalignment; owls ½ h/day) with morning bright light plus afternoon melatonin; or delay 1–2 h/day with a light box ~2 h before bed (larks 1–1.5 h/day). Advance for ≤7 zones east, delay for ≥8 (not universal). After arrival: seek and avoid light relative to T, dark sunglasses during avoid windows, nap when delaying. | B/C (protocol built from lab studies) |
| **Revell & Eastman 2005**, JBR 20:353 (doi 10.1177/0748730405277233) | Review ("How to trick mother nature into letting you fly around or stay up all night"): light-schedule design for jet lag and shift work; same principles | C |
| **Burgess et al. 2003**, JBR 18:318 | 3-day pre-flight advance: 1 h/day earlier sleep plus 3.5 h of morning bright light → DLMO +2.1 h | B |
| **Eastman et al. 2005**, Sleep 28:33 (doi 10.1093/sleep/28.1.33) | 1 h/day vs 2 h/day advance: 1.4 vs 1.9 h median (not significant); 2 h/day caused misalignment | B |
| **Waterhouse et al. 2007**, Lancet 369:1117 (doi 10.1016/S0140-6736(07)60529-7) | Review. Jet lag is worse eastward. For short stays (≤2–3 days), stay on home time. Light and melatonin timing rules. | C |
| **Sack 2010**, NEJM 362:440 (doi 10.1056/NEJMcp0909838) | Clinical practice review; same rules; short trips → consider staying on home time | C |
| **AASM 2007** (Morgenthaler; Sack review) | Melatonin = Standard; light, sleep scheduling, hypnotics, caffeine = Option | Guideline |
| **Roach & Sargent 2019**, Front Physiol 10:927 (doi 10.3389/fphys.2019.00927) | 23:00–07:00 sleeper: DLMO 21:00, T 04:00. **Westward:** seek light ~3 h before T, avoid ~3 h after; melatonin 3 mg 4 h after T. **Eastward:** avoid light 3 h before T, seek 3 h after; melatonin 3 mg 11.5 h before T (6.5 h before bed). Pre-flight 30–60 min/day for 3–4 days. Naps ≤1 h, placed in avoid windows. "Partial adaptation" = T within night-time sleep. | C (applied review, sports) |
| Janse van Rensburg et al. 2021, Sports Med consensus (doi 10.1007/s40279-021-01502-0) | Elite athletes; consistent with the above | C |
| **Timeshifter** (Lockley, scientific adviser; company material, not peer-reviewed) | Personalised by chronotype; "Practicality Filter"; light seek/avoid, sleep, naps up to 1 h pre-flight, caffeine "little and often" (20–25 mg/h or 40–50 mg/2 h); melatonin 1–3 mg fast-release; groups multi-leg trips; claims ~1 day/zone without intervention; claims up to 3–4 h/day shift; self-reported survey of 129,852 trips: 14.1× less likely to report very severe jet lag (no control group) | D |

### 5.2 Re-entrainment rates (use as planner caps)

| Situation | Rate | Source |
|---|---|---|
| Field, no intervention, eastward | ~57 min/day (~1 day per zone) | Aschoff 1975 via E&B 2009 |
| Field, no intervention, westward | ~92 min/day | idem |
| Lab, 12 h shift with bright light | delay 2.4 h/day; advance 1.6 h/day | E&B 2009 summary |
| Pre-flight advance, home | **1.0 h/day** (owls 0.5) | Eastman 2005; Burgess 2003 |
| Pre-flight delay, home | **1.5 h/day** (2.0 with evening light box; larks 1–1.5) | E&B 2009 |
| Post-arrival advance, structured | **1.5 h/day** | E&B 2009 planning value |
| Post-arrival delay, structured | **2.0 h/day** | E&B 2009 planning value |
| Realistic post-landing (imperfect adherence) | ~1 h/day | E&B 2009 |

### 5.3 Pre-flight adjustment

- **Default: 3 days, capped at 3 h in total** (practicality).
- **Advance:** each day, move sleep 1 h earlier; get bright light right after waking (in the advance zone); take 0.5 mg melatonin at T − 9 h; keep the evening dim.
- **Delay:** move sleep 1.5 h later per day; seek light in the evening before bed (box or bright room); keep the morning dim (sunglasses).
- **Constraints:** a pre-flight delay is not allowed if it would push the wake time on departure day later than **departure − 3 h**. The pre-departure night's sleep is cut short by airport transit (blocked from dep − 3 h to dep + 0.5 h).

### 5.4 Advance or delay?

The PRC crossover sits at T. A large eastward shift of 8–12 h puts destination morning light **before** T on the first days, which delays the clock when it should advance it. This is the root of **antidromic re-entrainment** (E&B 2009). In their data, after 11 zones east 7 of 8 subjects re-entrained by delaying, and after 8 zones east 4 of 6 advanced, 1 delayed and 1 did not shift.

**Literature guideline:** advance for ≤7 zones east, delay for ≥8 (E&B 2009, "not universal"). Larks (early types) advance more easily and owls delay more easily.

**ODE sweep with a structured plan** (`sweep.py`):
- Setup: home UTC+0, departure 21:00 local, 12 h flight, 3 pre-flight days, planner windows simulated with the lux table in §4.4, no light box. The direction is forced.
- Days to adapt = first cycle after arrival from which 3 consecutive CBTmins are within 1 h of target.

| Eastward Δ | Advance plan F99 / H19 (days) | Delay plan (24 − Δ) F99 / H19 | No plan F99 |
|---|---|---|---|
| 6 h | 2.6 / 1.5 | — | — |
| 7 h | 3.5 / 2.5 | — | — |
| 8 h | 5.5 / 2.5 | 12.4 / 8.4 | — |
| 9 h | 5.5 / 3.4 | — | — |
| 10 h | 5.4 / 4.4 | 11.3 / 7.4 | antidromic / fails |
| 11 h | 6.4 / 4.4 | — | — |
| 12 h | 6.3 / 5.3 | 10.3 / 6.3 | antidromic / fails |

Departing at 10:00 instead gives similar results, and adding a light box changes little.

**Interpretation:**
- Once the avoid-light window keeps the delay-zone light away, both models adapt faster by **advancing even up to 11–12 h east**.
- Delay plans are slow because most delay-zone light falls in the destination evening or night, when there is no sunlight.
- The literature limit (≤7–8 h) comes from people *without* strict light avoidance.

**Recommendation:**

| Setting | Value |
|---|---|
| Default threshold, neutral chronotype | **advance if A ≤ 9 h, else delay** (A = eastward shift in [0, 24)) |
| Early chronotype | 10 h |
| Late chronotype | 8 h |
| Configurable range | 7–12 h |
| Tier 2 option | "simulate both, choose fewer days" with Hannay19, restricted to A ∈ [8, 12] |
| UI note | If advancing ≥8 h, emphasise strict morning light avoidance (sunglasses) for the first days |
| Evidence | Low–moderate (C). Modelling and literature disagree; document it. |

### 5.5 Post-arrival rules (all chronotypes)

1. Carry the body-clock estimate across the flight. Never reset it to destination time.
2. **Every day, compute T_k = T_{k−1} + 24 h − step**, where step = min(cap, |φ* − φ|) in the plan direction.
3. **Seek light:** [T, T + 6 h] when advancing; [T − 6 h, T] when delaying. **Avoid light:** [T − 8 h, T] when advancing; [T, T + 8 h] when delaying. Remove times when the traveller is asleep.
   - For an 8 h eastward trip this means: on day 1 at the destination, avoid morning light until ~09:00–10:00 local and seek late-morning or early-afternoon light. The windows move about 1.5 h earlier each day.
4. **Sleep:**
   - Ideally the aligned window [T − ψ_on, T + ψ_wake] (so T stays inside sleep).
   - After arrival, clamp sleep onset to within ±3 h of habitual local onset (practicality), and keep the habitual sleep duration.
   - Sleep in flight if the aligned sleep window overlaps the flight.
   - No sleep from 0.75 h before arrival until 1.5 h after arrival.
5. Adaptation counts as done when |φ* − φ| ≤ 0.5 h. The plan then stops (typically 3–7 days after arrival).

---

## 6. Caffeine

| Fact | Source | Evidence |
|---|---|---|
| Half-life ~3–7 h (mean ~5 h). Longer with oral contraceptives and pregnancy; shorter in smokers | pharmacology texts | A |
| 400 mg taken 0, 3 or **6 h** before bed significantly disrupted sleep; even at 6 h, total sleep fell by >1 h | Drake et al. 2013, JCSM 9:1195 (doi 10.5664/jcsm.3170) | B |
| Caffeine (≈ double espresso, 2.9 mg/kg) 3 h before habitual bedtime **delayed the melatonin rhythm by ~40 min**, about half the delay from 3 h of evening bright light. Mechanism: adenosine/cAMP | Burke et al. 2015, Sci Transl Med 7:305ra146 (doi 10.1126/scitranslmed.aac5125) | B |
| Slow-release caffeine (300 mg) improved daytime alertness after a 7-zone eastward flight; disturbed sleep. *(From memory; verify before citing.)* | Beaumont et al. 2004, J Appl Physiol 96:50 (doi 10.1152/japplphysiol.00940.2002) | B |
| "Little and often" (20–25 mg/h or 40–50 mg/2 h) to keep alertness during the adaptation window | Timeshifter | D |
| AASM 2007: caffeine as a countermeasure for jet-lag sleepiness = Option | Morgenthaler 2007 | Guideline |

**App rules:**
- `caffeine_ok` window: from wake until **next planned sleep onset − 6 h**. Use 8 h on advance plans: evening caffeine delays the clock (Burke 2015), which works against an advance, and sleep onset is earlier than habitual.
- `caffeine_use` suggestion: when the traveller is awake during biological night (T − 7 h … T + 3 h), clipped to `caffeine_ok`. This is the circadian low point of alertness.
- **Avoid** caffeine in the 6–8 h before a planned sleep. Suggest small doses (≤100 mg per serving, ≤400 mg/day for healthy adults).
- Allow the user to turn caffeine advice off or set a personal cutoff.
- On delay plans, evening caffeine slightly helps the shift but harms sleep. Do **not** recommend it; the sleep cost dominates.

---

## 7. Naps and in-flight sleep

| Fact | Source | Evidence |
|---|---|---|
| NASA cockpit nap study: a 40-min planned rest opportunity (~26 min of sleep) improved performance (~34%) and physiological alertness (~54%) on long-haul flights. *(Figures from memory; verify.)* | Rosekind et al. 1994/1995, J Sleep Res 4(S2):62 (doi 10.1111/j.1365-2869.1995.tb00229.x) | B |
| A 10-min afternoon nap gave the best immediate benefit; a 30-min nap caused sleep inertia | Brooks & Lack 2006, Sleep 29:831 (doi 10.1093/sleep/29.6.831) | B |
| Naps during delay plans (when wake periods are long) and placed in avoid-light windows | E&B 2009; Roach & Sargent 2019 (≤1 h) | C |
| Pre-flight naps up to 1 h | Timeshifter | D |

**App rules:**
- At most **one nap per waking period, ≤30 min** (suggest 10–20 min; ≤45 min in flight if a long wake period follows).
- It must end **≥6 h before the next planned sleep**.
- Prefer naps inside an avoid-light window (they double as darkness). Never inside a seek window.
- On delay plans, add a "siesta" option ~7 h after wake.
- **In-flight:** sleep when the aligned sleep window overlaps the flight. Use eye mask and earplugs. Do not sleep during in-flight seek windows; use cabin and reading lights or a light-therapy device.

---

## 8. Chronotype and initial phase estimation

### 8.1 Instruments

**MCTQ** (Munich ChronoType Questionnaire; Roenneberg et al. 2003, JBR 18:80, doi 10.1177/0748730402239679; Roenneberg 2004 Curr Biol 14:R1038, doi 10.1016/j.cub.2004.11.039; Roenneberg 2007 Sleep Med Rev 11:429, doi 10.1016/j.smrv.2007.07.005; Roenneberg et al. 2019 Biology 8:54, doi 10.3390/biology8030054):

```
SO_f  = sleep onset on free days        SD_f = sleep duration on free days
SD_w  = sleep duration on work days     (5 work days, 2 free days by default)
MSF   = SO_f + SD_f / 2                                       (mid-sleep on free days)
SD_week = (5·SD_w + 2·SD_f) / 7
MSF_sc  = MSF                         if SD_f ≤ SD_w
        = MSF − (SD_f − SD_week) / 2  if SD_f > SD_w           (sleep-debt corrected)
```
MSF_sc is not valid if the person uses an alarm on free days.

**MEQ** (Horne & Östberg 1976, Int J Chronobiol 4:97). Score 16–86:

| Score | Category |
|---|---|
| 70–86 | definite morning |
| 59–69 | moderate morning |
| 42–58 | neither |
| 31–41 | moderate evening |
| 16–30 | definite evening |

- **Validity against DLMO:** Kantermann et al. 2015 (JBR 30:449, doi 10.1177/0748730415597520): DLMO correlates with MSF_sc at r = 0.68 and with MEQ at r = −0.70, but at a given score DLMO still spans ~4 h. Questionnaires are only good to about ±1–2 h.
- Burgess & Eastman 2005 (J Sleep Res 14:229, doi 10.1111/j.1365-2869.2005.00470.x): free-sleeper wake time predicted DLMO within ±1.5 h in 96% of people.
- DLMO ≈ 2 h before habitual sleep onset (Sletten et al. 2010, Front Neurol 1:137, doi 10.3389/fneur.2010.00137).

### 8.2 Rule used by the planner (evidence B/C)

Inputs: habitual sleep onset and wake, preferably on **free days with no alarm**, plus chronotype (early / neutral / late, from a 1-question self-assessment or the MEQ/MCTQ category).

```
mid   = onset + duration/2
T0    = mid + offset[chrono]        offset = early 0.5 h, neutral 1.0 h, late 1.5 h
DLMO  = T0 − 7 h
```

- Neutral 23:00–07:00 → T0 = 04:00, DLMO = 21:00 (matches Roach & Sargent 2019 and E&B 2009).
- Early 23:00–07:00 → T0 = 03:30. Late → T0 = 04:30. Baehr 2000 is consistent: T sits closer to wake in evening types.
- If only work-day times are known for a late type, add +0.5 h, because workday alarms hide a later clock.
- Mapping from questionnaires:
  - MEQ: ≥59 → early; ≤41 → late; else neutral.
  - MCTQ: MSF_sc < 03:00 → early; > 05:00 → late; else neutral. These are the population tertiles of roughly 02:30–05:30 in Roenneberg data; configurable.
- Show users that the estimate is uncertain (±1.5 h). This is the main reason the plan's windows are deliberately broad.
- Kantermann & Eastman 2018 (Chronobiol Int 35:280, doi 10.1080/07420528.2017.1400979) give additional evidence on the reliability of the phase estimate.

---

## 9. Short trips

- **Evidence (C):** Waterhouse 2007, Sack 2010 and E&B 2009 all advise that for stays of **≤2–3 days** it is usually better to **stay on home time** (or shift only partially). The cost of re-entraining twice exceeds the benefit.
- **App rules:**

  | Situation | Behaviour |
  |---|---|
  | \|Δ\| < 2 h | No plan. Tips only (e.g. get morning light for a 1 h DST spring-forward). |
  | Stay at destination < 72 h | **Home-time mode** (below) |
  | Stay ≥ 72 h | Full plan. Its "done" day may come after the return; if so, the return plan starts from the estimated phase at return departure. |

- **Home-time mode:**
  - Keep T fixed on the home clock.
  - Schedule sleep at home body-clock times where feasible, intersected with the user's fixed local commitments if they supply them.
  - Seek light during home "day" hours and avoid it in home body-night (T − 8 … T + 3).
  - Caffeine when commitments fall in body-night.
  - No phase-shifting melatonin. Optionally a sleep-aid dose at local bedtime only if that falls in the melatonin advance zone or dead zone, never in the delay zone (T − 2 … T + 8).
- **Alternative "partial adaptation" option** for 3–5 day trips: target |φ*| = min(|Δ|, 3 h). This is the compromise strategy (Revell & Eastman 2005; Roach & Sargent 2019, where partial adaptation = T inside night-time sleep).

---

## 10. Multi-leg itineraries, date line, DST

1. **Everything is computed in UTC instants.** Use `java.time.Instant` and `ZoneId` (IANA tz database bundled in the APK). Obtain offsets with `zone.rules.getOffset(instant)`. Never use fixed offsets per city.
2. **Shift Δ** = offset(dest, at arrival) − offset(home, at departure), in hours (fractional zones such as +5:30, +5:45 and +12:45 are fine). Normalise to (−12, +12]: `norm12(x) = ((x + 12) mod 24, mapped into (0, 24]) − 12`. Crossing the international date line therefore needs no special case: Kiribati (+14) to Hawaii (−10) gives raw −24 → Δ = 0, so no shift and only the calendar date changes. Display both dates.
3. **Segmentation:**
   - **Layover < 24 h** (e.g. LHR→SIN→SYD with 2.5 h in SIN): pass-through. Use the final destination's Δ. Layovers just modify light/sleep feasibility, e.g. airport lighting during a seek or avoid window.
   - **Stopover 24–72 h:** if it is roughly in the direction of the final destination, keep planning towards the final destination and show windows on local time. Otherwise, use home-time mode relative to the current body clock.
   - **Stop ≥ 72 h:** treat it as a destination. Plan segment 1 to adapt there, then plan segment 2 starting from the *estimated* body-clock state at departure (carry φ, not the ideal). Timeshifter similarly groups flights into outbound and homebound plans.
4. **DST transitions during a plan:** handled automatically because windows are stored in UTC and rendered in local time. A DST change on a non-travel day is a 1 h "mini-shift". No plan is needed (|Δ| < 2), but the app may show a tip. Kantermann et al. 2007 (Curr Biol 17:1996, doi 10.1016/j.cub.2007.10.025) found that the human clock does not adjust fully to DST.
5. **Day boundaries:** the planner works in **body-clock cycles** (CBTmin to CBTmin), not calendar days. The UI maps them to calendar days by local wake time.

---

## 11. Recent research and tools (2019–2026)

- **Model-based apps:**
  - **Entrain** (Forger lab, Univ. Michigan): optimal light schedules from Serkh & Forger 2014.
  - Walch et al. 2016 (Sci Adv 2:e1501705, doi 10.1126/sciadv.1501705): global sleep data from app users.
  - Christensen et al. 2020 (PLoS Comput Biol 16:e1008445, doi 10.1371/journal.pcbi.1008445): an analysis of >100 travellers using a model-based jet-lag app found partial adherence was common, and better adherence went with better mood. Evidence C/B (observational).
- **Arcascope** (Forger-lab spin-out): open-source `circadian` Python package (MIT), implementing Forger99, Jewett99, Hilaire07, Hannay19 SP and TP, with wearable integration. This is our source for the exact constants.
- **Wearables:**
  - Huang et al. 2021 (Sleep 44:zsab126, doi 10.1093/sleep/zsab126): Apple Watch activity and heart rate predicted DLMO within ~1 h. Activity-based model inputs did better than wrist light sensors, which are often covered by sleeves.
  - Cheng et al. 2021 (Sleep 44:zsaa180, doi 10.1093/sleep/zsaa180): phase estimation from wearable data.
  - Mayer et al. 2024/2025 (J Sleep Res 34:e14425, doi 10.1111/jsr.14425): MAE ~1.4 h in older adults.
  - **Implication:** a future version could feed Health Connect step counts into a model (e.g. activity as a light proxy, the Huang 2021 approach) to refine T. Keep it optional and offline.
- **Light guidelines:** Brown et al. 2022 consensus on melanopic EDI (§2.4).
- **Timeshifter:**
  - Company white papers and survey data (129,852 trips, 2017–2019 era).
  - No randomized controlled trial was found by this review. Treat efficacy claims as **D (company claims)**. Their published principles match the academic protocols.
- **Melatonin product quality:** Cohen 2023 JAMA (gummies).
- **Ongoing gap:** few RCTs of complete jet-lag *protocols* in real travellers. Most evidence comes from lab phase-shift studies plus older field melatonin trials.

---

## 12. Algorithm design (Tier 1: rule-based, PRC-driven; Tier 2: ODE-assisted)

### 12.1 Inputs

| Input | Type | Default |
|---|---|---|
| Home zone | IANA ZoneId | device zone |
| Itinerary | list of legs (departure instant + zone, arrival instant + zone) | — |
| Habitual sleep | onset and wake (local clock, free days preferred) | 23:00–07:00 |
| Chronotype | early / neutral / late (or MEQ / MCTQ → category) | neutral |
| Toggles | melatonin (off by default, opt-in with safety screen), caffeine (on), naps (on), light box available (off), pre-flight days (3, range 0–4) | |
| Optional | fixed commitments at destination; stay length; return flight | |

### 12.2 Parameters (all configurable; values used for the fixtures)

| Name | Value | Meaning | Evidence |
|---|---|---|---|
| `PRE_ADV_CAP` | 1.0 h/day | pre-flight advance per cycle | B (Eastman 2005, Burgess 2003) |
| `PRE_DEL_CAP` | 1.5 h/day (2.0 with box) | pre-flight delay per cycle | B/C (E&B 2009) |
| `POST_ADV_CAP` | 1.5 h/day | post-arrival advance | B/C (E&B 2009) |
| `POST_DEL_CAP` | 2.0 h/day | post-arrival delay | B/C (E&B 2009) |
| `PRE_MAX_SHIFT` | 3.0 h | total pre-flight shift | C (practicality) |
| `ADV_THRESHOLD` | early 10 / neutral 9 / late 8 h | advance if A ≤ threshold | C (literature 7–8; models 11–12) |
| `CBT_FROM_MID` | early 0.5 / neutral 1.0 / late 1.5 h | T0 = mid-sleep + offset | B (Baehr 2000; E&B; Sletten) |
| `SEEK_LEN` | 6 h | seek window length from T | B (Khalsa 2003) |
| `AVOID_LEN` | 8 h | avoid window length from T | B/C |
| `MAX_SLEEP_DEV` | 3 h | post-arrival sleep onset within ±3 h of local habitual | C |
| `MEL_ADV_OFFSET` | −9 h (0.5 mg) | melatonin time relative to T (3 mg: −12 h) | B (Burgess 2010; E&B) |
| `MEL_ADV_WINDOW` | [−13, −7] h | acceptable melatonin window; if planned time falls in sleep, take at bedtime − 15 min if in window, else skip | B/C |
| `CAF_CUTOFF` | 6 h (8 h on advance plans) | no caffeine within this time of planned sleep | B (Drake 2013; Burke 2015) |
| `NAP_MAX` | 30 min | | B (Brooks & Lack 2006) |
| `NAP_MIN_BEFORE_SLEEP` | 6 h | | C |
| `PRE_DEP_WAKE` | 3 h | no sleep from dep − 3 h to dep + 0.5 h; latest pre-departure wake | C |
| `ARR_SLEEP_END` | 0.75 h | no sleep from arr − 0.75 h to arr + 1.5 h | C |
| `MIN_SHIFT` | 2 h | below this: no plan | C |
| `SHORT_TRIP` | 72 h | below this: home-time mode | C (Waterhouse 2007; Sack 2010) |
| `DONE_TOL` | 0.5 h | plan finished when \|φ* − φ\| ≤ tol | C |

### 12.3 Decision procedure (pseudo-code)

All times are UTC hours (Double), converted to local time only for display. This mirrors `reference/planner.py` exactly.

```text
function PLAN(homeZone, legs, habOnset, habWake, chrono, opts):
    dep  ← legs.first.departure;   arr ← legs.last.arrival
    Δ    ← norm12(offset(legs.last.arrZone, arr) − offset(homeZone, dep))
    if |Δ| < MIN_SHIFT:              return NoPlan(Δ)
    if stayLength(legs) < SHORT_TRIP: return HomeTimePlan(...)                       // §9

    A ← Δ mod 24                                                                     // eastward hours in [0,24)
    direction ← (A ≤ ADV_THRESHOLD[chrono]) ? ADVANCE : DELAY
    if opts.tier2 and 8 ≤ A ≤ 12:                                                    // optional, §13
        direction ← argmin_dir daysToAdapt(Hannay19, simulate(PLAN(... forced dir)))
    φ* ← (direction == ADVANCE) ? A : A − 24                                         // + advance, − delay
    sgn ← sign(φ*)

    sd    ← (habWake − habOnset) mod 24
    T0clk ← (habOnset + sd/2 + CBT_FROM_MID[chrono]) mod 24                          // home clock
    ψon   ← (T0clk − habOnset) mod 24;    ψwake ← (habWake − T0clk) mod 24

    // first CBTmin in UTC at least preDays before departure
    T ← T0clk − homeOffset;  move T by ±24 until  dep − 24(preDays+1) ≤ T ≤ dep − 24·preDays
    preTotal ← PRE_MAX_SHIFT
    if sgn < 0:   // a delay must not make the last pre-departure wake later than dep − PRE_DEP_WAKE
        Tlast ← last T + 24k < dep;  preTotal ← clamp(dep − PRE_DEP_WAKE − (Tlast + ψwake), 0, preTotal)

    φ ← 0; cycles ← []
    loop k = 0..29:
        where ← T < dep ? HOME : (T < arr ? FLIGHT : DEST)

        // 1. sleep
        on ← T − ψon
        if where == DEST:                                                            // practicality clamp
            dev ← norm12(local(on) − habOnset); on ← on − dev + clamp(dev, −MAX_SLEEP_DEV, +MAX_SLEEP_DEV)
        sleep ← [on, on + sd] minus { [dep − PRE_DEP_WAKE, dep + 0.5], [arr − ARR_SLEEP_END, arr + 1.5] }

        // 2. light windows (PRC, Khalsa 2003)
        if ADVANCE: seek ← [T, T + SEEK_LEN];  avoid ← [T − AVOID_LEN, T]
        else:       seek ← [T − SEEK_LEN, T];  avoid ← [T, T + AVOID_LEN]
        seek ← seek − sleep;  avoid ← avoid − sleep

        // 3. melatonin (advance plans only, if enabled and not yet done)
        if opts.melatonin and ADVANCE and |φ* − φ| > DONE_TOL:
            m ← T + MEL_ADV_OFFSET
            if m inside this or previous sleep:
                m ← (sleep.start ∈ T + MEL_ADV_WINDOW) ? sleep.start − 0.25 : NONE

        emit cycle(k, T, φ, where, sleep, seek, avoid, m)
        if where == DEST and |φ* − φ| ≤ DONE_TOL: break

        // 4. step to the next cycle
        if T < dep:
            cap ← ADVANCE ? PRE_ADV_CAP : (lightBox ? PRE_DEL_CAP_BOX : PRE_DEL_CAP)
            if preDays == 0: cap ← 0
            cap ← min(cap, max(0, preTotal − |φ|))
            if DELAY and (T + 24 + cap) < dep and (T + 24 + cap + ψwake) > dep − PRE_DEP_WAKE:
                cap ← max(0, cap − ((T + 24 + cap + ψwake) − (dep − PRE_DEP_WAKE)))
        else:
            cap ← ADVANCE ? POST_ADV_CAP : POST_DEL_CAP
        step ← sgn · min(cap, |φ* − φ|)
        φ ← φ + step;  T ← T + 24 − step

    // 5. caffeine and naps, per waking period i (wake_i = cycles[i].sleep.end, next = cycles[i+1].sleep.start)
    cut ← ADVANCE ? CAF_CUTOFF_ADV : CAF_CUTOFF
    for each i:
        caffeine_ok[i]  ← [wake_i, next − cut]   (if non-empty)
        caffeine_use[i] ← caffeine_ok[i] ∩ ([T_i − 7, T_i + 3] ∪ [T_{i+1} − 7, T_{i+1} + 3])   (pieces > 15 min)
        napRange ← [wake_i + 1, next − NAP_MIN_BEFORE_SLEEP]
        nap[i] ← first 30-min slot of (avoid_i ∪ avoid_{i+1}) inside napRange
                 else if DELAY: wake_i + 7 h if inside napRange and not in a seek window
                 else NONE
    return Plan(direction, φ*, cycles)
```

**UI mapping and feasibility filter** (must not change the science):
- Clip seek windows to ≥30 min (show as "at least 30 min, ideally the whole window").
- During a seek window at local night, suggest a light box or bright indoor light; during an avoid window in daylight, suggest dark sunglasses.
- Merge windows < 15 min apart.
- Round displayed times to 15 min.
- Let users mark windows "couldn't do", then re-plan from the current estimate. In Tier 2, re-estimate φ by simulating the reported light.

### 12.4 Sleep, melatonin, caffeine and nap rules (summary)

- **Sleep:** aligned window [T − ψ_on, T + ψ_wake] at home and in flight. At the destination, onset is clamped to ±3 h of habitual local onset and duration kept at habitual. Sleep is blocked around airport transit.
- **Melatonin:** advance plans only (delay = opt-in, 0.5 mg at wake ≈ T + 3…4 h, low evidence).
  - 0.5 mg at T − 9 h, which is ~4.5–5 h before the shifted bedtime.
  - If that time falls in sleep (common on the first destination nights of large advances), take it at bedtime − 15 min, but only if bedtime ∈ [T − 13, T − 7]. Otherwise skip, so it never lands in the delay zone.
  - Stop once adapted.
- **Caffeine:** OK from wake until sleep − 6 h (− 8 h on advance plans); suggested while awake in body-night.
- **Nap:** ≤30 min, ≥6 h before sleep, preferably inside an avoid window; delay plans get a siesta at wake + 7 h.

---

## 13. ODE validation module

**Purpose:** catch planner regressions and wrong-direction bugs, not predict an individual's exact phase.

1. **Plan → lux(t):** map each instant to a state with the precedence sleep > avoid > seek > neutral, then look up the §4.4 lux table. Before the plan starts, use the "typical day" at the home clock; after it ends, the typical day at the destination clock.
2. **Simulate:** warm up 40 days on lux(t) evaluated before the plan (24 h periodic), then run from dep − 5 days to arr + 14 days with RK4 at dt = 0.1 h.
3. **Read out CBTmin times** (§4.5). Error e_k = norm12(local_dest(CBTmin_k) − baseline), where baseline = the model's own entrained CBTmin clock time on the typical day (F99 04:14, H19 04:00).
4. **days_to_adapt** = (time of the first post-arrival CBTmin from which 3 consecutive |e| ≤ 1 h) − arr, in days.
5. **Direction check:** net shift = Σ(CBTmin_{k+1} − CBTmin_k − 24) on the home clock. It must be negative (earlier) for advance plans and positive for delay plans, with magnitude ≈ |φ*| ± 1 h.
6. **Assertions for each fixture:**
   - (a) the direction is correct;
   - (b) days_to_adapt(plan) ≤ days_to_adapt(no plan) and is finite within 14 days;
   - (c) days_to_adapt is within ±1 day of the fixture value (models are deterministic; a tighter ±0.2 d is fine for a bit-for-bit port).
7. **Tier 2 (optional on device):** choose the direction for A ∈ [8, 12] by simulating both plans with Hannay19 and taking the smaller days_to_adapt. Ties (within 1 day) go to the literature default. Also re-estimate the current phase from user-logged light, by replaying the plan with "couldn't do" windows set to neutral or avoid lux.

---

## 14. Worked examples (TDD fixtures)

Common assumptions:
- Habitual sleep 23:00–07:00, neutral chronotype (T0 = 04:00 home), 3 pre-flight days, melatonin on, no light box.
- Times are local: home clock for home rows, destination clock for flight and destination rows.
- φ = cumulative shift at that cycle (+ advance, − delay).
- "t" is UTC hours from 00:00 UTC on the given date.
- Generated with `reference/planner.py` (full outputs for every chronotype in `ex_neutral.txt`, `ex_early.txt`, `ex_late.txt`).

### 14.1 SFO → LHR (eastward 8 h, advance)

- Flight: 2026-06-15, dep 16:30 PDT (UTC−7, t = 23.5), arr 10:45 BST (UTC+1, next day, t = 33.75).
- Δ = +8 → A = 8 ≤ 9 → **advance, φ* = +8**.

| k | where | φ | CBTmin | sleep | seek | avoid | melatonin | caffeine OK |
|---|---|---|---|---|---|---|---|---|
| 0 | home | 0 | 04:00 | 23:00–07:00 | 07:00–10:00 | 20:00–23:00 | 19:00 | 07:00–14:00 |
| 1 | home | +1 | 03:00 | 22:00–06:00 | 06:00–09:00 | 19:00–22:00 | 18:00 | 06:00–13:00 |
| 2 | home | +2 | 02:00 | 21:00–05:00 | 05:00–08:00 | 18:00–21:00 | 17:00 | 05:00–12:00 |
| 3 | home | +3 | 01:00 | 20:00–04:00 | 04:00–07:00 | 17:00–20:00 | 16:00 | 04:00–12:00 |
| 4 | flight (BST) | +3 | 09:00 | 04:00–10:00 (in flight) | 10:00–15:00 | 01:00–04:00 | 00:00 | 10:00–18:00 |
| 5 | LHR | +4.5 | 07:30 | 02:00–10:00 (clamped +0.5 h) | 10:00–13:30 | 23:30–02:00 | 22:30 | 10:00–17:00 |
| 6 | LHR | +6 | 06:00 | 01:00–09:00 | 09:00–12:00 | 22:00–01:00 | 21:00 | 09:00–15:30 |
| 7 | LHR | +7.5 | 04:30 | 23:30–07:30 | 07:30–10:30 | 20:30–23:30 | — (done) | — |

Model validation (days to adapt after arrival):

| Chronotype | F99 plan / no plan | H19 plan / no plan |
|---|---|---|
| neutral | **4.8 / 6.8** | **2.7 / 5.8** |
| early | 4.8 / 6.8 | 2.7 / 5.8 |
| late | 4.8 / 6.8 | 3.7 / 5.8 |

Net shift ≈ −8.0 h (earlier) in both models.

### 14.2 LHR → SIN → SYD (eastward 11 h, layover pass-through, delay)

- Flights: 2026-11-10, dep 21:00 GMT (t = 21.0); SIN arr t = 33.5 (UTC+8), SIN dep t = 36.0 (2.5 h layover → pass-through); SYD arr 06:30 AEDT (UTC+11, Nov 12, t = 43.5).
- Δ = +11 → A = 11 > 9 → **delay 13 h, φ* = −13**.
- Pre-flight delay is capped at 3 h by the departure-wake rule (latest wake ≤ 18:00 on departure day).

| k | where | φ | CBTmin | sleep | seek | avoid | nap |
|---|---|---|---|---|---|---|---|
| 0 | home | 0 | 04:00 | 23:00–07:00 | 22:00–23:00 | 07:00–12:00 | 08:00 |
| 1 | home | −1.5 | 05:30 | 00:30–08:30 | 23:30–00:30 | 08:30–13:30 | 09:30 |
| 2 | home | −3 | 07:00 | 02:00–10:00 | 01:00–02:00 | 10:00–15:00 | 11:00 |
| 3 | home | −3 | 07:00 | 02:00–10:00 | 01:00–02:00 | 10:00–15:00 | 11:00 |
| 4 | flight (AEDT) | −3 | 18:00 | 13:00–21:00 (in flight) | 12:00–13:00 | 21:00–02:00 | 22:00 |
| 5 | SYD | −5 | 20:00 | 20:00–04:00 (clamped −5 h) | 14:00–20:00 | — (inside sleep) | 11:00 |
| 6 | SYD | −7 | 22:00 | 20:00–04:00 | 16:00–20:00 | 04:00–06:00 | 05:00 |
| 7 | SYD | −9 | 00:00 | 20:00–04:00 | 18:00–20:00 | 04:00–08:00 | 05:00 |
| 8 | SYD | −11 | 02:00 | 21:00–05:00 | 20:00–21:00 | 05:00–10:00 | 06:00 |
| 9 | SYD | −13 | 04:00 | 23:00–07:00 | 22:00–23:00 | 07:00–12:00 | — (done) |

No melatonin (delay plan).

Model validation:

| Chronotype | F99 plan / no plan | H19 plan / no plan |
|---|---|---|
| neutral | **10.9 / >14 (antidromic, unresolved)** | **5.9 / 10.9** |
| early | 9.9 / >14 | 5.9 / 10.9 |
| late | 10.9 / >14 | 5.9 / 10.9 |

Net plan shift ≈ +12.7 to +13.0 h (later).

**Design note:** sweep §5.4 suggests that an 11 h advance plan would adapt faster in both models (~6.4 / 4.4 days). This is the key configurable decision.

### 14.3 JFK → LAX (westward 3 h, delay, early flight)

- Flight: 2026-03-20 (after the US DST start on 2026-03-08), dep 08:00 EDT (UTC−4, t = 12.0), arr 11:20 PDT (UTC−7, t = 18.33).
- Δ = −3 → **delay, φ* = −3**.
- Pre-flight delay = 0: any delay would make the departure-day wake later than 05:00. The night before departure is cut short to 23:00–05:00.

| k | where | φ | CBTmin | sleep | seek | avoid |
|---|---|---|---|---|---|---|
| 0–2 | home | 0 | 04:00 | 23:00–07:00 | 22:00–23:00 | 07:00–12:00 |
| 3 | home | 0 | 04:00 | 23:00–05:00 (truncated) | 22:00–23:00 | 05:00–12:00 |
| 4 | LAX | 0 | 01:00 | 20:00–04:00 | 19:00–20:00 | 04:00–09:00 |
| 5 | LAX | −2 | 03:00 | 22:00–06:00 | 21:00–22:00 | 06:00–11:00 |
| 6 | LAX | −3 | 04:00 | 23:00–07:00 | 22:00–23:00 | 07:00–12:00 (done) |

Model validation (all chronotypes): F99 **3.7 / 4.7**, H19 **1.7 / 3.7** (plan / no plan). Net shift +3.0 h.

### 14.4 NRT → JFK (westward 14 h = eastward 10 h; chronotype changes the direction)

- Flight: 2026-01-15, dep 11:00 JST (UTC+9, t = 2.0), arr 10:30 EST (UTC−5, same date, t = 15.5).
- Δ = norm12(−14) = **+10** → A = 10.

Two branches:
- **neutral / late:** A > 9 → **delay 14 h, φ* = −14**. Pre-flight delay capped at −1 h (early departure). Destination CBTmin by cycle: 15:00 (cycle 4, mostly in flight), 17:00, 19:00, 21:00, 23:00, 01:00, 03:00, 04:00 (k = 11, done). Sleep is clamped to 20:00–04:00 EST for the first nights. Seek windows are afternoon and evening (e.g. k5 11:00–17:00) and avoid windows come after T.
- **early:** A ≤ 10 → **advance 10 h, φ* = +10**, with melatonin. k = 0: CBTmin 03:30, sleep 23:00–07:00, seek 07:00–09:30, avoid 19:30–23:00, melatonin 18:30.

Model validation:

| Branch | F99 plan / no plan | H19 plan / no plan |
|---|---|---|
| neutral (delay 14) | **11.7 / >14 (antidromic)** | **7.7 / 5.8** |
| early (advance 10) | **5.8 / >14** | **3.8 / 5.8** |

In the neutral delay branch, H19 *without* a plan advances 10 h in 5.8 days, which is faster than the delay plan. This again shows the models favour advancing. It is a known, documented tension.
- **Fixture assertion for the neutral delay branch:** use direction and finite adaptation only. Do not assert that it beats no plan in H19.

### 14.5 Pure-logic fixtures (no ODE)

| Case | Expected |
|---|---|
| norm12(+14 − (−10)) (Kiribati → Hawaii) | 0 → NoPlan |
| norm12(−5 − 9) (JST → EST) | +10 |
| norm12(+5.75 − 0) | +5.75 |
| Δ = +1 (DST) | NoPlan (MIN_SHIFT) |
| Stay of 48 h, Δ = +6 | HomeTimePlan |
| Habitual 00:30–08:30, late | T0 = 04:30 + 1.5 = 06:00 |
| MCTQ: SO_f 01:00, SD_f 9 h, SD_w 7 h | MSF = 05:30; SD_week = 7.571; MSF_sc = 05:30 − (9 − 7.571)/2 = 04:47 |
| MEQ 60 | early (moderate morning) |
| A = 9, neutral | advance |
| A = 9.5, neutral | delay |
| A = 9.5, early | advance |

---

## 15. Rules and evidence levels

Evidence levels:
- **A** = meta-analysis or multiple RCTs.
- **B** = controlled laboratory studies or individual RCTs.
- **C** = expert consensus, protocol reviews or modelling.
- **D** = company claims or opinion.

| # | Rule | Value | Evidence |
|---|---|---|---|
| 1 | Body clock tracked as CBTmin T; DLMO = T − 7 h | — | B |
| 2 | T0 = mid-sleep + 0.5/1.0/1.5 h (early/neutral/late) | — | B/C |
| 3 | Advance with light in [T, T + 6], delay with [T − 6, T] | — | B (Khalsa 2003, St Hilaire 2012) |
| 4 | Avoid light in [T − 8, T] when advancing, [T, T + 8] when delaying; "avoid" = ≤10 lux, sunglasses | — | B (Zeitzer 2000; Phillips 2019) / C |
| 5 | Seek ≥30–60 min, intermittent OK, outdoor preferred | — | B (Chang 2012; Rimmer 2000; Crowley 2015) |
| 6 | Shift caps pre 1.0 adv / 1.5 del; post 1.5 adv / 2.0 del h/day | — | B/C |
| 7 | Advance if A ≤ 9 (early 10, late 8), else delay | configurable 7–12 | C (literature vs model tension) |
| 8 | Melatonin 0.5 mg fast-release at T − 9 h for advancing | 3 mg alternative at T − 12 h | B (Burgess 2008/2010; Revell 2006); clinical A (Cochrane) |
| 9 | Melatonin never in [T − 2, T + 8] unless delay opt-in | — | B |
| 10 | Melatonin as sleep aid at destination bedtime (if inside advance window) | — | A (Cochrane; AASM Standard) |
| 11 | Caffeine cutoff 6 h before sleep (8 h advance) | — | B (Drake 2013; Burke 2015) |
| 12 | Caffeine during awake body-night, small doses | — | C/D |
| 13 | Nap ≤30 min, ≥6 h before sleep, in avoid window; siesta on delay plans | — | B/C |
| 14 | Sleep clamped ±3 h of habitual local after arrival | — | C |
| 15 | No plan if \|Δ\| < 2 h | — | C |
| 16 | Home-time mode if stay < 72 h | — | C |
| 17 | Layover < 24 h = pass-through; stop ≥ 72 h = destination | — | C |
| 18 | All computation in UTC instants with tz database; Δ normalised to (−12, 12] | — | engineering |
| 19 | Pre-flight ≤3 days / ≤3 h total; no delay that makes departure-day wake later than dep − 3 h | — | C |
| 20 | ODE validation: correct direction, faster than no plan, within ±1 day of fixture | — | C (modelling) |

---

## 16. References (DOIs verified against PubMed/publisher records during this research unless marked)

**Light PRCs and dose–response**
- Khalsa SBS, Jewett ME, Cajochen C, Czeisler CA. A phase response curve to single bright light pulses in human subjects. J Physiol 2003;549:945–952. doi:10.1113/jphysiol.2003.040477
- St Hilaire MA, Gooley JJ, Khalsa SBS, et al. Human phase response curve to a 1 h pulse of bright white light. J Physiol 2012;590:3035–3045. doi:10.1113/jphysiol.2012.227892
- Zeitzer JM, Dijk DJ, Kronauer RE, Brown EN, Czeisler CA. Sensitivity of the human circadian pacemaker to nocturnal light: melatonin phase resetting and suppression. J Physiol 2000;526:695–702. doi:10.1111/j.1469-7793.2000.00695.x
- Chang AM, Santhi N, St Hilaire M, et al. Human responses to bright light of different durations. J Physiol 2012;590:3103–3112. doi:10.1113/jphysiol.2011.226555
- Rimmer DW, Boivin DB, Shanahan TL, et al. Dynamic resetting of the human circadian pacemaker by intermittent bright light. Am J Physiol Regul Integr Comp Physiol 2000;279:R1574–R1579. doi:10.1152/ajpregu.2000.279.5.R1574
- Lockley SW, Brainard GC, Czeisler CA. High sensitivity of the human circadian melatonin rhythm to resetting by short wavelength light. J Clin Endocrinol Metab 2003;88:4502–4505. doi:10.1210/jc.2003-030570
- Gooley JJ, Rajaratnam SMW, Brainard GC, et al. Spectral responses of the human circadian system depend on the irradiance and duration of exposure to light. Sci Transl Med 2010;2:31ra33. doi:10.1126/scitranslmed.3000741
- Rüger M, St Hilaire MA, Brainard GC, et al. Human phase response curve to a single 6.5 h pulse of short-wavelength light. J Physiol 2013;591:353–363. doi:10.1113/jphysiol.2012.239046
- Revell VL, Molina TA, Eastman CI. Human phase response curve to intermittent blue light using a commercially available device. J Physiol 2012;590:4859–4868. doi:10.1113/jphysiol.2012.235416
- Minors DS, Waterhouse JM, Wirz-Justice A. A human phase-response curve to light. Neurosci Lett 1991;133:36–40. (DOI not verified)
- Phillips AJK, Vidafar P, Burns AC, et al. High sensitivity and interindividual variability in the response of the human circadian system to evening light. PNAS 2019;116:12019–12024. doi:10.1073/pnas.1901824116
- Gooley JJ, Chamberlain K, Smith KA, et al. Exposure to room light before bedtime suppresses melatonin onset and shortens melatonin duration in humans. J Clin Endocrinol Metab 2011;96:E463–E472. doi:10.1210/jc.2010-2098
- Brown TM, Brainard GC, Cajochen C, et al. Recommendations for daytime, evening, and nighttime indoor light exposure to best support physiology, sleep, and wakefulness in healthy adults. PLoS Biol 2022;20:e3001571. doi:10.1371/journal.pbio.3001571
- Martin SK, Eastman CI. Medium-intensity light produces circadian rhythm adaptation to simulated night-shift work. Sleep 1998;21:154–165. (DOI not verified)

**Melatonin**
- Burgess HJ, Revell VL, Eastman CI. A three pulse phase response curve to three milligrams of melatonin in humans. J Physiol 2008;586:639–647. doi:10.1113/jphysiol.2007.143180
- Burgess HJ, Revell VL, Molina TA, Eastman CI. Human phase response curves to three days of daily melatonin: 0.5 mg versus 3.0 mg. J Clin Endocrinol Metab 2010;95:3325–3331. doi:10.1210/jc.2009-2590
- Lewy AJ, Bauer VK, Ahmed S, et al. The human phase response curve (PRC) to melatonin is about 12 hours out of phase with the PRC to light. Chronobiol Int 1998;15:71–83. doi:10.3109/07420529808998671
- Herxheimer A, Petrie KJ. Melatonin for the prevention and treatment of jet lag. Cochrane Database Syst Rev 2002;(2):CD001520. doi:10.1002/14651858.CD001520
- Revell VL, Burgess HJ, Gazda CJ, et al. Advancing human circadian rhythms with afternoon melatonin and morning intermittent bright light. J Clin Endocrinol Metab 2006;91:54–59. doi:10.1210/jc.2005-1009
- Paul MA, Gray GW, Lieberman HR, et al. Phase advance with separate and combined melatonin and light treatment. Psychopharmacology 2011;214:515–523. doi:10.1007/s00213-010-2059-5
- Erland LAE, Saxena PK. Melatonin natural health products and supplements: presence of serotonin and significant variability of melatonin content. J Clin Sleep Med 2017;13:275–281. doi:10.5664/jcsm.6462
- Cohen PA, Avula B, Wang YH, Katragunta K, Khan I. Quantity of melatonin and CBD in melatonin gummies sold in the US. JAMA 2023;329:1401–1402. doi:10.1001/jama.2023.2296

**Guidelines, protocols, reviews**
- Morgenthaler TI, Lee-Chiong T, Alessi C, et al. Practice parameters for the clinical evaluation and treatment of circadian rhythm sleep disorders. Sleep 2007;30:1445–1459. doi:10.1093/sleep/30.11.1445
- Sack RL, Auckley D, Auger RR, et al. Circadian rhythm sleep disorders: part I, basic principles, shift work and jet lag disorders. Sleep 2007;30:1460–1483. doi:10.1093/sleep/30.11.1460
- Sack RL. Jet lag. N Engl J Med 2010;362:440–447. doi:10.1056/NEJMcp0909838
- Waterhouse J, Reilly T, Atkinson G, Edwards B. Jet lag: trends and coping strategies. Lancet 2007;369:1117–1129. doi:10.1016/S0140-6736(07)60529-7
- Eastman CI, Burgess HJ. How to travel the world without jet lag. Sleep Med Clin 2009;4:241–255. doi:10.1016/j.jsmc.2009.02.006 (PMC2829880)
- Revell VL, Eastman CI. How to trick mother nature into letting you fly around or stay up all night. J Biol Rhythms 2005;20:353–365. doi:10.1177/0748730405277233
- Burgess HJ, Crowley SJ, Gazda CJ, Fogg LF, Eastman CI. Preflight adjustment to eastward travel: 3 days of advancing sleep with and without morning bright light. J Biol Rhythms 2003;18:318–328. doi:10.1177/0748730403253585
- Eastman CI, Gazda CJ, Burgess HJ, Crowley SJ, Fogg LF. Advancing circadian rhythms before eastward flight: a strategy to prevent or reduce jet lag. Sleep 2005;28:33–44. doi:10.1093/sleep/28.1.33
- Crowley SJ, Eastman CI. Phase advancing human circadian rhythms with morning bright light, afternoon melatonin, and gradually shifted sleep: can we reduce morning bright-light duration? Sleep Med 2015;16:288–297. doi:10.1016/j.sleep.2014.12.004
- Roach GD, Sargent C. Interventions to minimize jet lag after westward and eastward flight. Front Physiol 2019;10:927. doi:10.3389/fphys.2019.00927
- Janse van Rensburg DC, Jansen van Rensburg A, Fowler PM, et al. Managing travel fatigue and jet lag in athletes: a review and consensus statement. Sports Med 2021;51:2029–2050. doi:10.1007/s40279-021-01502-0
- Boulos Z, Campbell SS, Lewy AJ, et al. Light treatment for sleep disorders: consensus report. VII. Jet lag. J Biol Rhythms 1995;10:167–176. doi:10.1177/074873049501000209

**Caffeine, naps**
- Drake C, Roehrs T, Shambroom J, Roth T. Caffeine effects on sleep taken 0, 3, or 6 hours before going to bed. J Clin Sleep Med 2013;9:1195–1200. doi:10.5664/jcsm.3170
- Burke TM, Markwald RR, McHill AW, et al. Effects of caffeine on the human circadian clock in vivo and in vitro. Sci Transl Med 2015;7:305ra146. doi:10.1126/scitranslmed.aac5125
- Beaumont M, Batéjat D, Piérard C, et al. Caffeine or melatonin effects on sleep and sleepiness after rapid eastward transmeridian travel. J Appl Physiol 2004;96:50–58. doi:10.1152/japplphysiol.00940.2002 (from memory — verify)
- Rosekind MR, Graeber RC, Dinges DF, et al. Crew factors in flight operations IX: effects of planned cockpit rest on crew performance and alertness in long-haul operations. NASA TM-108839, 1994; summary in J Sleep Res 1995;4(S2):62–66. doi:10.1111/j.1365-2869.1995.tb00229.x (figures from memory — verify)
- Brooks A, Lack L. A brief afternoon nap following nocturnal sleep restriction: which nap duration is most recuperative? Sleep 2006;29:831–840. doi:10.1093/sleep/29.6.831

**Chronotype and phase estimation**
- Roenneberg T, Wirz-Justice A, Merrow M. Life between clocks: daily temporal patterns of human chronotypes. J Biol Rhythms 2003;18:80–90. doi:10.1177/0748730402239679
- Roenneberg T, Kuehnle T, Pramstaller PP, et al. A marker for the end of adolescence. Curr Biol 2004;14:R1038–R1039. doi:10.1016/j.cub.2004.11.039
- Roenneberg T, Kuehnle T, Juda M, et al. Epidemiology of the human circadian clock. Sleep Med Rev 2007;11:429–438. doi:10.1016/j.smrv.2007.07.005
- Roenneberg T, Pilz LK, Zerbini G, Winnebeck EC. Chronotype and social jetlag: a (self-)critical review. Biology 2019;8:54. doi:10.3390/biology8030054
- Horne JA, Östberg O. A self-assessment questionnaire to determine morningness-eveningness in human circadian rhythms. Int J Chronobiol 1976;4:97–110.
- Kantermann T, Sung H, Burgess HJ. Comparing the Morningness-Eveningness Questionnaire and Munich ChronoType Questionnaire to the dim light melatonin onset. J Biol Rhythms 2015;30:449–453. doi:10.1177/0748730415597520
- Kantermann T, Eastman CI. Circadian phase, circadian period and chronotype are reproducible over months. Chronobiol Int 2018;35:280–288. doi:10.1080/07420528.2017.1400979
- Baehr EK, Revelle W, Eastman CI. Individual differences in the phase and amplitude of the human circadian temperature rhythm: with an emphasis on morningness-eveningness. J Sleep Res 2000;9:117–127. doi:10.1046/j.1365-2869.2000.00196.x
- Duffy JF, Dijk DJ, Hall EF, Czeisler CA. Relationship of endogenous circadian melatonin and temperature rhythms to self-reported preference for morning or evening activity in young and older people. J Investig Med 1999;47:141–150.
- Burgess HJ, Eastman CI. The dim light melatonin onset following fixed and free sleep schedules. J Sleep Res 2005;14:229–237. doi:10.1111/j.1365-2869.2005.00470.x
- Sletten TL, Vincenzi S, Redman JR, Lockley SW, Rajaratnam SMW. Timing of sleep and its relationship with the endogenous melatonin rhythm. Front Neurol 2010;1:137. doi:10.3389/fneur.2010.00137
- Benloucif S, Guico MJ, Reid KJ, et al. Stability of melatonin and temperature as circadian phase markers and their relation to sleep times in humans. J Biol Rhythms 2005;20:178–188. doi:10.1177/0748730404273983
- Czeisler CA, Duffy JF, Shanahan TL, et al. Stability, precision, and near-24-hour period of the human circadian pacemaker. Science 1999;284:2177–2181. doi:10.1126/science.284.5423.2177
- Kantermann T, Juda M, Merrow M, Roenneberg T. The human circadian clock's seasonal adjustment is disrupted by daylight saving time. Curr Biol 2007;17:1996–2000. doi:10.1016/j.cub.2007.10.025

**Mathematical models and apps**
- Forger DB, Jewett ME, Kronauer RE. A simpler model of the human circadian pacemaker. J Biol Rhythms 1999;14:532–537. doi:10.1177/074873099129000867
- Kronauer RE, Forger DB, Jewett ME. Quantifying human circadian pacemaker response to brief, extended, and repeated light stimuli over the phototopic range. J Biol Rhythms 1999;14:500–515. doi:10.1177/074873099129001073
- Jewett ME, Forger DB, Kronauer RE. Revised limit cycle oscillator model of human circadian pacemaker. J Biol Rhythms 1999;14:493–499. doi:10.1177/074873049901400608
- St Hilaire MA, Klerman EB, Khalsa SBS, et al. Addition of a non-photic component to a light-based mathematical model of the human circadian pacemaker. J Theor Biol 2007;247:583–599. doi:10.1016/j.jtbi.2007.04.001
- Hannay KM, Booth V, Forger DB. Macroscopic models for human circadian rhythms. J Biol Rhythms 2019;34:658–671. doi:10.1177/0748730419878298
- Dean DA, Forger DB, Klerman EB. Taking the lag out of jet lag through model-based schedule design. PLoS Comput Biol 2009;5:e1000418. doi:10.1371/journal.pcbi.1000418
- Serkh K, Forger DB. Optimal schedules of light exposure for rapidly correcting circadian misalignment. PLoS Comput Biol 2014;10:e1003523. doi:10.1371/journal.pcbi.1003523
- Diekman CO, Bose A. Reentrainment of the circadian pacemaker during jet lag: east-west asymmetry and the effects of north-south travel. J Theor Biol 2018;437:261–285. doi:10.1016/j.jtbi.2017.10.002
- Lu Z, Klein-Cardeña K, et al. (author list not verified). Resynchronization of circadian oscillators and the east-west asymmetry of jet-lag. Chaos 2016;26:094811. doi:10.1063/1.4954275
- Walch OJ, Cochran A, Forger DB. A global quantification of "normal" sleep schedules using smartphone data. Sci Adv 2016;2:e1501705. doi:10.1126/sciadv.1501705
- Christensen S, Huang Y, Walch OJ, Forger DB. Optimal adjustment of the human circadian clock in the real world. PLoS Comput Biol 2020;16:e1008445. doi:10.1371/journal.pcbi.1008445
- Huang Y, Mayer C, Cheng P, et al. Predicting circadian phase across populations: a comparison of mathematical models and wearable devices. Sleep 2021;44:zsab126. doi:10.1093/sleep/zsab126
- Cheng P, Walch O, Huang Y, et al. Predicting circadian misalignment with wearable technology: validation of wristband sensors and modeled light. Sleep 2021;44:zsaa180. doi:10.1093/sleep/zsaa180
- Mayer C, et al. Predicting circadian phase in community-dwelling later-life adults using wearables and models. J Sleep Res 2025;34(4):e14425 (epub 2024). doi:10.1111/jsr.14425
- Arcascope `circadian` (open-source Python, MIT): https://github.com/Arcascope/circadian, `circadian/models.py`. Reference implementation for the constants in §4.
- Timeshifter (company website, help-centre and blog pages; Lockley SW scientific adviser). Non-peer-reviewed; evidence D.

> Some author lists and titles above (marked or not) were reconstructed from abstracts and may contain minor errors. DOIs are the authoritative identifiers. Items marked "verify" should be checked before being cited in user-facing text.
