# Clockblock product and visual design

> This research covers Timeshifter, initial and later feature proposals, visual identity, motion, widgets and notifications.
> Research date: 2026-10-04.
> **[verified]** means the research checked the cited source. **[reported]** identifies user reviews or press reports.
> **[inferred]** identifies an unverified interpretation of screenshots, marketing text or background knowledge.

> [!NOTE]
> This document was written before the app was built. The visual identity in it is current, but some technical
> plans changed during implementation: widgets use Remote Compose instead of Glance,
> data lives in DataStore instead of Room, and `AdviceAlarmScheduler` plays the role described here as
> `PlanTicker`. For how the app works today, read [architecture.md](architecture.md) and [surfaces.md](surfaces.md).

---

## Design summary

1. Clockblock addresses reported Timeshifter problems with editing, notification reliability, time-zone clarity and pricing.
   It supports offline manual entry, two-zone times and free use without an account.
   Reports include a three-hour edit limit, overlap errors and missing flights without flight numbers.
2. The Two skies dial compares local sky time with estimated body clock time on two rings.
   Their night sections align as the body clock adjusts.
3. The adaptation indicator reduces wave amplitude as adjustment progresses. Advice markers change shape when their blocks become active.
4. The visual identity, "Dusk Instrument", uses Twilight Indigo `#4F46E5`, Marigold `#FFB000` and fixed advice colours.
   Google Sans Flex supplies primary text; Fraunces supplies selected headlines.
   Google Fonts metadata and `METADATA.pb` confirm its SIL Open Font License (OFL) and availability since 2025-11-12.
   Bundle the variable TTF with axes `wght 1–1000, wdth 25–151, opsz 6–144, ROND 0–100, GRAD 0–100, slnt −10–0`.
   Use Fraunces's SOFT and WONK axes only in the specified headline contexts.
5. One ongoing Now notification shows current advice. The travel-day version uses Android 16 `Notification.ProgressStyle` with advice segments and take-off/landing points.
   The initial widget proposals include Next up, Two Clocks dial, Today ribbon and Trip countdown.

---

# Part 1 — Timeshifter product teardown

## 1.1 Product family & positioning [verified]

| Product | What | Pricing |
|---|---|---|
| **Timeshifter Jet Lag** (iOS/Android) | Personalised jet lag plans per trip | First plan free; then **US$9.99/plan or US$24.99/year** unlimited ([jet-lag-app](https://www.timeshifter.com/jet-lag-app), [Play listing](https://play.google.com/store/apps/details?id=com.timeshifter.timeshifter)) |
| **Timeshifter Shift Work** | Advice for rotating/night/irregular shifts | 30-day trial, then subscription ([App Store lookup](https://itunes.apple.com/lookup?id=1380684373&entity=software)) |
| **My Circadian Day®** | Daily timing for light, meals, exercise | ([advice guide](https://www.timeshifter.com/timeshifting/advice-guide)) |
| Concierge | Human-made plans for athletes/execs | Enterprise |

Timeshifter markets biological-time advice as **Circadian Time™**, practical scheduling as **Practicality Filter™**, and short-trip detection as **Quick Turnaround®**.
Its [jet lag page](https://www.timeshifter.com/jet-lag-app) also lists pre-travel adjustment, notifications, three-hour and full-plan views, unlimited stopovers and delay editing.
The company cites St Hilaire 2012, Eastman & Burgess 2009 and Czeisler 1999 (period ≈ 24.2 h).
Its claim that "96.4% of users… without feeling jet lagged" comes from about 130,000 post-flight surveys.
At research time, Play listed 4.6★ from 1,900 reviews and 100,000+ installs. iOS listed 4.66★ from 2,600 ratings [verified].

The Play listing limits use to healthy adults aged 18+ and excludes pilots and flight crew on duty [verified].
It also states that the FDA has not evaluated the product. Clockblock must include comparable safety limits.

> Naming caution: avoid their trademarks ("Timeshift®", "Practicality Filter™", "Quick Turnaround®", "Circadian Time™"). Suggested names for ours: **"Real-life mode"**, **"Short trip: stay on home time"**, **"Body clock"**.

## 1.2 Onboarding flow

Appllama indexes the iOS app as: Welcome (3 screens) → **Onboarding (9)** → Paywall (2) → **Profile Setup (11)** → Trip Planning (7) → Timeshift Guide (3) → Trip Management (2) ([appllama](https://appllama.io/apps/1380684374/timeshifter), screens are paywalled) [verified counts]. A UX teardown video calls out "an onboarding that creates a connection with the customer service team from the off" (in-app live chat) and "the journey planner UI" ([YouTube](https://www.youtube.com/watch?v=rnJivg2Zhlw)) [verified].

Questions asked (sources: Rick Steves forum walkthrough, DIY Travel review, support FAQ, advice guide):
- **Account** (email / social sign-in). It is required, and plans sync to the account. [verified: password-reset FAQ]
- **Normal sleep pattern**: usual bedtime and wake time. [verified: "your normal sleep times"]
- **Chronotype**: "whether you are a lark or a night owl". [reported, [Rick Steves forum](https://community.ricksteves.com/travel-forum/general-europe/jet-lag-i-tried-the-timeshifter-app)]
- **Melatonin advice** opt-in, plus *type*. The card says "take the recommended **type and dose**" (fast- vs slow-release). [verified: advice guide]
- **Caffeine advice** opt-in. "If you're not [a caffeine user], there's no need to start now!" [verified]
- **Pre-travel adjustment** (start shifting 1–3 days before departure). [verified feature; exact UI inferred]
- **Push notification permission.** One user says you *can't decline*: "The app will insist over and over." [reported]
- Probably also age/sex for chronotype norms. [inferred, unverified]
- Advice settings **can't be changed while a plan is active**. To change them you delete the flights, edit settings, then re-add the flights ([support FAQ](https://www.timeshifter.com/support/jet-lag)) [verified].

App Store reviews report missing controls for in-flight sleep, destination bedtime, pre-travel work hours, airport transit and layovers.
Examples include "I can't sleep on planes" and "told me to sleep while I'm on my 2 hour layover".
Another traveller needed four hours to reach the airport during a planned sleep block [reported, iTunes RSS].

## 1.3 Trip creation [verified unless noted]

- Search **by flight number**, then add it to a "basket" (swipe left to delete before submitting).
- Multi-leg trips and unlimited stopovers. Flights are **grouped automatically** into an outbound plan and a homebound plan ("your flights may be grouped differently than you expect" for multi-city).
- Flights can be edited, replaced or deleted **only up to 3 hours after the original departure**. After that they're greyed out and you have to "contact support".
- **You can't enter past flights** and **can't start a plan earlier than recommended**.
- Internet is needed only to create or modify a plan; the plan works offline after that. **No print/export.**
- [reported] Small airlines and unnumbered refuelling legs aren't found, with "no way to enter your info manually". Other reported problems: a "time zones don't match up… edit flight times" validation error, and a deleted flight that still blocks re-entry.

## 1.4 The plan

The advice page lists plans as circles with destination and date.
Each plan offers a three-hour current view and a full day-by-day schedule.
Schedules include pre-travel, travel and post-arrival days through adjustment. Travel advice can include pre-flight naps and in-flight sleep or light.
Return plans appear separately. Users report notifications about 30 minutes before changes, including on Apple Watch [reported].

**Advice card catalogue**: the exact set we must cover, from the [advice guide](https://www.timeshifter.com/timeshifting/advice-guide) [verified]:

| Card | Instruction (abridged) | Notes for us |
|---|---|---|
| **See bright light** | As much bright light as possible; no sunglasses; all lights on; screens at max | Highest priority (phase-shifting) |
| **See some light** | Avoid dim/dark; no sunglasses | Lower-intensity window |
| **Avoid light** | Minimise light; dark wrap-around sunglasses; avoid screens | "Blue-blockers… likely not as effective as sunglasses" |
| **Sleep (or avoid light)** | Cool, quiet, dark; mask, earplugs, alarm | "If you can't sleep, just stay in the dark" |
| **Nap** | Timed nap; pre-flight naps up to ~1 h | Part of plan |
| **Nap if you're tired** | Optional | |
| **Take melatonin** | Recommended type/dose; consult doctor | Opt-in; ≤5% side effects; no driving for 8 h |
| **Use caffeine** | "Little and often": ~20–25 mg/h or 40–50 mg/2 h | Opt-in |
| **Avoid caffeine** | Half-life 3–5 h | Opt-in |
| **Peak fatigue** | Be extra cautious | Safety warning |
| *(My Circadian Day)* Eat normally / lean & light / avoid eating; Exercise normally / low / avoid | | "Beyond" scope. Timeshifter itself says food is a *weak* zeitgeber |

Timeshifter's [field guide](https://www.timeshifter.com/timeshifting/how-to-timeshift) gives alternatives such as sunglasses, indoor lighting and dark rest when sleep is difficult.
Its [equipment list](https://www.timeshifter.com/timeshifting/what-you-need) includes dark sunglasses, a sleep mask and optional melatonin.

## 1.5 Shift work app [verified]
The [shift-work app](https://www.timeshifter.com/shift-work-app) supports quick entry, commute and preparation time, fatigue prediction and advice notifications.
It covers rotating, fixed-night, on-call and irregular schedules.
A later Clockblock version could explore repeating schedules as planner inputs.

## 1.6 Settings, widgets, platform
- Profile (bottom-left icon) → advice settings, push notifications [verified FAQ].
- An Apple Watch companion exists. A reviewer reported the **watch showing different advice than the phone** [reported].
- The research found no evidence of Android home-screen widgets.
  An iOS reviewer requested Live Activities [reported]. Clockblock could address similar needs through Android Live Updates and widgets.

## 1.7 What users complain about (≈750 recent App Store reviews: US/GB/AU, 57 at ≤3★) [reported]

| # | Theme | Representative quote | Our fix |
|---|---|---|---|
| 1 | **Brittle editing / delays** | "delayed on the tarmac and had to be rebooked. The time limitations for editing are not r[easonable]"; "'these times overlap'… goes straight to 'Pay for a subscription'" | Edit anything, any time. "I'm delayed" one-tap shift. Re-plan from *now* using the current body-clock estimate |
| 2 | **Flight lookup only** | "doesn't know the small airline… no way to enter your info manually" | Manual entry is first-class (airport/city + local times) via a bundled offline airport/tz DB. Flight-number lookup is optional |
| 3 | **Notifications unreliable / contradictory** | "push notifications that directly contradicted the app's advice"; "vanish when clicked on"; "didn't send me any notifications for my trip home" | One source of truth: exact alarms + one ongoing "Now" notification regenerated from the same plan model. "Send test reminder" + battery-optimisation helper |
| 4 | **Timezone confusion** | "you can only see the plan with the time zone of where you will be… wanted to know how the plan fit with the time zone at my destination" | **Dual clock everywhere**: local time big, home/destination small, body-clock time as a third readout. Timezone toggle chip |
| 5 | **Ignores real-life constraints** | "avoid light 8–11 am… I have to be at work at 8:30"; "sleep during the time I need to travel to the airport"; "sleep while I'm on my 2 hour layover" | **Busy blocks / "Can't do this"**: mark windows unavailable and re-optimise. "Can't sleep on planes" toggle |
| 6 | **Too intense** | "If I could stay awake for 20 hours straight…"; "felt like I was experiencing jet lag before the holiday" | **Effort slider** (Gentle / Balanced / Max) with predicted days-to-adapt for each |
| 7 | **Crashes, white screen, lost data → pay again** | many "white screen" reviews; "plan deleted… asked to pay to create a new plan" | Local Room DB, no account, no paywall, export/import |
| 8 | **Pricing** | "no reason for an app like this to use a subscription… Everything can be done offline" | Free + OSS |
| 9 | **Accessibility** | "tiny gray font… impossible to zoom"; "icons hard to decipher at a glance" | Font scale to 200%, ≥4.5:1 contrast, icon **+ label** always, TalkBack-narrated dial |
| 10 | **No print/export** | (FAQ admits it) | ICS export, printable pocket plan, share image |
| 11 | **Opaque science** | "recommendations went against every other piece of science" | A "Why?" sheet on every card (mechanism, PRC side, hours shifted, citations) |
| 12 | **Support** | "only… a chat box… AI response" | GitHub issues + in-app "copy debug info" |

Press tone: Wirecutter's headline is literally *"This App Can Help You Beat Jet Lag—If You Don't Have Much Else to Do"* ([Wirecutter](https://www.nytimes.com/wirecutter/reviews/timeshifter-app-review/)). DIY Travel gave it 3.5/5: "it did work… but I found the whole process quite annoying… wearing sunglasses at odd times, even indoors… embarrassing at work" ([DIY Travel](https://diytravelagent.blog/2026/05/12/timeshifter-app-review-does-it-actually-work/)). A Rick Steves forum user "printed out the whole schedule, laid it out on the dining room table", then loved the 30-min Apple Watch nudges ([forum](https://community.ricksteves.com/travel-forum/general-europe/jet-lag-i-tried-the-timeshifter-app)) [verified]. Other coverage: [Fodor's](https://www.fodors.com/news/news/review-timeshifter-the-neuroscience-app-that-claims-to-fight-against-jet-lag), [VICE](https://www.vice.com/en/article/timeshifter-jet-lag-app/), [r/TravelHacks thread](https://www.reddit.com/r/TravelHacks/comments/1axswmf/experiences_with_timeshifter_app/) (not readable from my environment).

Advice cards must offer practical alternatives, such as sunglasses or a dim indoor space.
Explain possible effects of skipping without blaming the user. Give a numerical delay only when the model supports that estimate.

## 1.8 Proposed MVP (v1.0)

**Onboarding (≤ 6 screens, no account)**
1. Welcome: animated "Two Clocks" hero, one-line value prop, "Free & open source" badge.
2. Usual sleep: drag the two ends of a sleep arc on a 24 h dial (like the Android Clock Bedtime screen). Weekday/weekend toggle.
3. Chronotype: 3 large cards (Lark / In between / Owl) + optional 5-question reduced MEQ.
4. Tools: toggles for **Melatonin** (+ fast/slow release), **Caffeine**, **Can sleep on planes**, **Adjust before I leave**.
5. Effort: Gentle / Balanced / Max (a `ButtonGroup` with morphing selection).
6. Reminders: explain → `POST_NOTIFICATIONS` → exact-alarm rationale. "Not now" is honoured, permanently.

**Trips**
- Manual legs: from/to airport or city (offline IATA + IANA tz DB), local departure/arrival times, sanity checks (duration, date-line).
- Multi-leg with auto layovers. Outbound/return grouping that the **user can override**.
- Edit any time, including mid-trip: an **"I'm delayed by __"** quick action re-plans from now.
- Stay length → short trips offer **Stay on home time** vs **Adapt**.

**Plan**
- **Now** card + next 2 (always with "until" times).
- **Day timeline** grouped Pre-trip −2 / −1 · Travel · Arrival +1…, with local time + secondary tz + a "Why?" sheet.
- **Two skies dial** per day.
- Card types: See bright light, See light, Avoid light, Sleep, Nap, Optional nap, Melatonin, Caffeine OK, Avoid caffeine, Peak fatigue. Travel markers: Depart, Arrive, Layover.
- **"Can't do this"** on any card → blocked → re-plan. Done/skipped logging.

**Reminders**: exact alarms per transition with lead time (0/15/30 min). Never notify inside a sleep block except to wake you. One ongoing "Now" notification (Live Update on Android 16 travel days). Per-advice channels.

**Platform**: offline, Room, no account, no analytics, JSON export/import, ICS export. Glance widgets: *Next up* + *Two clocks*. M3 Expressive theme + dynamic colour, TalkBack, font scaling. Science page + disclaimers.

## 1.9 "Beyond Timeshifter" (v1.x → v2)

| Idea | Why it's better |
|---|---|
| **Body clock always visible** ("Your body thinks it's 03:12") with uncertainty band | Makes the invisible visible and explains counter-intuitive light timing |
| **Re-plan from reality**: actual sleep from Health Connect sleep sessions + opt-in ambient-light sampling → update phase estimate | Timeshifter can't adapt once you deviate ("just try to return to the schedule") |
| **Effort slider** with 3 alternative plans side by side + days-to-adapt | Answers complaint #6 |
| **Constraint blocks** imported from the calendar (read-only) | Answers complaint #5 |
| **Light-meter mode** (ambient light sensor → lux gauge; "bright" ≥ ~1,000 lx, typical indoor 100–500 lx) | Turns "see bright light" into live feedback |
| **Morning check-in** (sleepiness slider, e.g. Karolinska Sleepiness Scale) → adaptation curve | Feedback + celebration trigger |
| **Shift-work mode** (repeating schedules, commute and get-ready times, fatigue prediction) | Timeshifter sells this as a separate subscription |
| **DST mode** (15 min/day shift around clock changes) & **social-jet-lag** weekly mode | Daily-life use between trips |
| **Group trips**: share a plan via QR/file; overlay two people's dials | Couples/teams travel together |
| **Wear OS tile + complication** ("Avoid light · 42 min") | Watch nudges are loved; Timeshifter's watch is buggy |
| **Pocket plan PDF** (A6 fold) + calendar export | Answers a FAQ gap and the "printed it out" behaviour |
| **Open model**: documented oscillator model (Kronauer/Forger-style, cf. U-Michigan *Entrain*) with light + melatonin PRCs and unit-tested scenarios; "Explain my plan" dev view | Trust through transparency |
| **Trip "Passport"** (à la Flighty Passport): time zones crossed, hours shifted, fastest adaptation | Offline delight |

---

# Part 2 — Visual & motion direction (Material 3 Expressive)

## 2.1 What M3 Expressive gives us (API facts checked against androidx source)

Google reports 46 studies with 18,000+ participants.
Participants found important controls up to four times faster in Expressive layouts, with similar results for older and younger users.
Sources: [Google research](https://design.google/library/expressive-material-design-google-research), [M3 blog](https://m3.material.io/blog/building-with-m3-expressive), [launch post](https://blog.google/products/android/material-3-expressive-android-wearos-launch/).
Clockblock must give the current action the strongest visual emphasis on each view.

**Shapes.** `androidx.compose.material3.MaterialShapes` has **35** `RoundedPolygon`s [verified in `MaterialShapes.kt`]: `Circle, Square, Slanted, Arch, Fan, Arrow, SemiCircle, Oval, Pill, Triangle, Diamond, ClamShell, Pentagon, Gem, Sunny, VerySunny, Cookie4Sided, Cookie6Sided, Cookie7Sided, Cookie9Sided, Cookie12Sided, Clover4Leaf, Clover8Leaf, Burst, SoftBurst, Boom, SoftBoom, Flower, Puffy, PuffyDiamond, Ghostish, PixelCircle, PixelTriangle, Bun, Heart`. Morph them with `androidx.graphics.shapes.Morph(a, b)` → `morph.toPath(progress)` and wrap in a custom `Shape` ([shapes guide](https://developer.android.com/develop/ui/compose/graphics/draw/shapes), [MaterialShapes ref](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialShapes)).

**Motion.** `MotionScheme.expressive()` vs `.standard()` [verified in `ExpressiveMotionTokens.kt` / `StandardMotionTokens.kt`]:

| Spec | Expressive (damping / stiffness) | Standard |
|---|---|---|
| fastSpatial | 0.6 / 800 | 0.9 / 1400 |
| defaultSpatial | 0.8 / 380 | 0.9 / 700 |
| slowSpatial | 0.8 / 200 | 0.9 / 300 |
| fast/default/slow Effects | 1.0 / 3800 · 1600 · 800 | same |

Spatial specs (position, size, shape) bounce. Effects specs (colour, alpha) never bounce ([motion: how it works](https://m3.material.io/styles/motion/overview/how-it-works)).

**Components we'll use** (all in `material3` 1.4/1.5; many `@ExperimentalMaterial3ExpressiveApi`) [verified in package summary/source]:
- `MaterialExpressiveTheme`
- `LargeFlexibleTopAppBar` / `MediumFlexibleTopAppBar`
- `HorizontalFloatingToolbar`
- `ButtonGroup`
- `SplitButtonLayout`
- `ToggleButton`
- `FloatingActionButtonMenu` + `ToggleFloatingActionButton`
- `LoadingIndicator(polygons = …)` / `ContainedLoadingIndicator` (takes a **custom polygon list**)
- `LinearWavyProgressIndicator` / `CircularWavyProgressIndicator`. The determinate overloads take `amplitude: (progress: Float) -> Float`, `wavelength: Dp`, `waveSpeed: Dp` [verified in `WavyProgressIndicator.kt`]. **That's the hook for "wavy = jet-lagged".**
- Button `shapes` that morph on press
- Emphasized type styles (`displayLargeEmphasized` … `labelSmallEmphasized`) [verified in `Typography.kt`]

## 2.2 Design references

| Source | Steal this | Avoid |
|---|---|---|
| **Pixel Clock** (timer/bedtime) | Shape-morphing timer containers. Big variable-weight numerals. The Bedtime screen's **draggable sleep arc on a dial** → our onboarding sleep picker | Its generic cookie shapes everywhere |
| **Pixel Weather** | Bento tiles of mixed shapes. Procedural sky that reflects real conditions → our **body-clock sky** | Over-decorated tiles |
| **Android 16 system UI** | Wavy progress inside sliders/media. Status-bar chips for Live Updates | |
| **Nothing OS** | Dot-matrix language for *data* (our flight globe is dots). Monochrome + one accent red | Copying the NDot font |
| **Flighty** ([site](https://flighty.com/)) | Live Activities with countdown + plane on a progress track. "Passport" lifetime stats. Unfiltered, precise status copy ("Taxiing for 13m") → our advice copy should be this precise ("Avoid light · 2h 14m left · then Sleep") | Information overload |
| **Rise Science** ([site](https://www.risescience.com/)) | **Energy curve** with a "now" dot and named windows (grogginess, peak, melatonin window). Daily "habits nudged at the right time" | Health-guilt framing (sleep debt red numbers) |
| **Gentler Streak** ([site](https://gentler.app/)) | Soft, kind tone. Band-shaped "go/rest" zone chart. Mascot-free warmth through colour | |
| **Structured** ([site](https://structured.app/)) | Vertical day timeline with **coloured capsules on a time rail**, current-time line, icon-in-capsule. Best reference for our day view | Its iOS-specific chrome |
| **Timepage (Moleskine)** | Bold full-bleed colour, confident typography, heat-map calendar for "days to adapt" | |
| **Sleep Cycle / Calm** | Dark-first, low-luminance night UI | Stock-photo landscapes, starfield clichés |
| **Headspace** | Flat geometric illustration built from primitives. Proof that simple shapes can carry personality → our illustration style | Characters/faces (off-brand for an "instrument") |

Search links for further moodboarding (no specific shots verified): [Dribbble circadian](https://dribbble.com/search/circadian-rhythm), [Dribbble jet lag](https://dribbble.com/search/jet-lag), [Dribbble sleep tracker](https://dribbble.com/search/sleep-tracker), [Mobbin](https://mobbin.com) (paywalled; check Flighty, Rise, Structured flows).

## 2.3 Signature design concepts (concrete)

### A. "Two skies" dial ⭐ (hero of Plan screen, widget, celebration)
Issue #46 selected this design from three proposals.
The outer ring shows the local sky. The inner ring shows that sky shifted by the estimated body clock offset.
The angle between their night sections shows the difference. The rings align when the estimate reaches destination time.
- **Geometry:** 24 h dial, **noon at top, midnight at bottom** (sun overhead = day up). Numerals 12 · 18 · 00 · 06
  (12 p · 6 p · 12 a · 6 a on 12-hour clocks) sit just inside the body ring.
- **Outer ring = local sky.** Day, dawn and dusk colours from that day's real sunrise and sunset at the trip's
  airport in the zone shown (`Sun` in `:core:model`: NOAA's solar equations, offline, checked against the US Naval
  Observatory), labelled on the ring: "TOKYO DAY", "TOKYO NIGHT". Short days and nights squeeze their twilight so
  mid-day stays day and mid-night stays night. A polar night paints the ring night all round, labelled only with the
  night and centred on solar midnight; the midnight sun paints it day all round. With no airport for the zone the dial
  falls back to 06:30/19:00.
- **Inner ring = body sky**, labelled "YOUR BODY'S DAY" / "YOUR BODY'S NIGHT". `BodyRingMode.Simple` (the default)
  paints the same sky as the outer ring, polar days and nights included, and turns it by the jet lag.
  `BodyRingMode.Precise` paints the body's night from the model (habitual sleep moved by the offset). The app's dial
  is always Simple; each *Two clocks* widget can switch to Precise in its own options
  ([surfaces](surfaces.md#widget-options), #52).
- **Three encodings only:** the day/night colour of the two rings, **one needle** across both, and **one advice arc**
  outside the rings: the block under the needle (or the next one when nothing is on) with its glyph at the start,
  narrated along the rim ("See bright light until 15:00", then "then see some light" in a muted tone; advice that
  starts before the block ends, like sleep during a long flight, reads "Sleep at 16:00" instead of "then"). Avoid
  light keeps its **diagonal hatch** (the colour-blind carrier). The part of the block already behind the needle is
  washed back towards the face. Every mark carries its own label; no legend.
- **Centre readouts:** the place, local time upright and big, body time beneath **slanted** (`slnt −10`,
  `ROND 100`; italic with the system font), and the offset in words in a pill ("4½ h behind", "in sync"). Type rule:
  **upright = local, slanted = body.** The place (here and on the ring labels) is the trip's stop in the zone shown,
  so Tromsø reads "TROMSØ" though it keeps Oslo's zone id. With no stop there, or a name too wide for the hub ("Qian
  Gorlos Mongol Autonomous County"), the dial uses the zone's city at every detail level.
- **Detail levels** by the dial's smaller side: **Full** ≥ 250 dp (everything above), **Simple** 110–250 dp (ring
  labels shortened to the city and "BODY", no narration, no numerals; when the two would sit side by side, as once
  adapted, "BODY" moves to the body's day, or goes, so they never read as one phrase), **Glance** < 110 dp (the two
  skies, the needle and the two times). The in-app hero is 200–320 dp, so compact phones get Simple; the Now card
  carries the words.
- **Day change:** the body ring **turns** into place (`ClockblockMotion` slow spatial, no overshoot: the ring is
  data). Rings aligned → `HapticFeedbackConstants.CONFIRM`.
- **Scrub:** drag the needle to preview any time; the cards below sync. `CLOCK_TICK` haptic each hour, `SEGMENT_TICK` at
  block boundaries, and a dot keeps the real now. Tap the centre to return.
- **A11y:** TalkBack: "14:20 local. Your body clock is 09:56. Now: see bright light until 15:00. Next: …" Custom
  actions *Next block*, *Previous block*, *Back to now*.
- **Spec and renderers:** `TwoSkies.spec()` in `dial/spec` is pure Kotlin (no Android UI types) and returns a list of
  `DialOp`s in dp. `drawDialSpec()` paints them with Compose in the app; the Two clocks widget replays the same list
  into a Remote Compose canvas (`widget/…/rc/RemoteDialSpec.kt`). The widget port draws the sky rings and bars as
  constant-colour segments (Remote Compose has no shader from Kotlin), sets curved and tracked text glyph by glyph
  (`drawTextOnCircle` is still unverified on a real launcher), rounds weights to regular or bold (the system font's
  four styles), and leaves out the wash behind the needle. Only the needle moves with the launcher's clock, and the
  readouts are written from it. A host can set a text floor (`minText`, 7 dp on widgets): the AM/PM marker holds
  it and the digits give way. It can also set a label floor (`labelText`, 10 sp at the font scale on widgets): ring
  and bar labels and the smaller readouts are at least that big; labels are left out when their ring or bar can't
  hold them, and readouts lose "in sync", then AM/PM, but never the body time. `liveReadouts` lays a
  12-hour time out for its widest reading, for hosts that rewrite it between captures. `remote_dial_vs_app.png` in the widget goldens shows the two
  renderers side by side.
- **Two strips** (`TwoStrips.spec()`): the same two skies as two horizontal bars over a 24 h window round now (8 h back
  to the hour, like the dial, so it never shows more than the dial's 16 h ahead), local on
  top and body below, with a now line across both. Widgets use them where a round dial gets too small to read: the
  1×1, the 2×1 and 4×1 rows and the 2×2 landscape card. The app keeps Two skies as its hero. Levels go by the box:
  under 150 dp wide is Glance (local time, the bars, and body time where it fits), wide boxes are Simple (bar labels,
  the jet lag in a pill and, on a tall enough box, the advice with its name beside its capsule), and 280×128 dp up is
  Full (narration). A glyph never shows without its name.

### B. Day timeline ("Rail")
The proposed timeline uses vertically arranged capsules with **height = duration**, following Structured's layout.
Each block starts with an advice glyph on its advice-colour container.
A faint body-clock sky gradient shows estimated biological day or night behind local-time labels.
The current block expands into the Now card, with `CircularWavyProgressIndicator` around its glyph.
The time column shows local and secondary zones. Its proposed header cycles through Local, Home, Destination and Body.

`RailColors` reserves advice fills for capsules and chips. Neutral rows avoid grey containers that could resemble pale sleep, nap, melatonin or avoid-light advice.
Each capsule has a 1.5 dp edge in its advice hue.
The edge darkens in light theme or lightens in dark theme until contrast reaches 3.2:1 against all supported backgrounds.
Those backgrounds include the page, Now card and body-clock strip over either.

The Now row uses the lowest surface container, a 2 dp `primary` outline and 2 dp inset.
Its start time uses `primary`. A dial-highlighted row uses `outline` instead.
Upcoming rows use `onSurface`. Past labels and times use `onSurfaceVariant` without alpha changes.
Past capsules blend 45 % toward the page and retain a 2.2:1 edge.
Nested chips use `outlineVariant` at rest. Active or highlighted chips use the advice container and capsule edge.

A 2 dp `outlineVariant` line connects capsules from row edge to row edge and stops at day headers.
It continues through the Now marker, including after the day's last block. The Now card's outline draws above it.
Patterns use content colour at 22 % alpha.
Sleep's sparse star dots use the lowest alpha reaching 3:1, or 2.2:1 for past blocks.
When a range and duration cannot share a line, the whole duration moves below without a separator.
The secondary city also moves whole below its times.
`RailContrastAuditTest` checks these rules in light and dark static and wallpaper palettes.

### C. Body-clock sky header
`PlanHeader` fills the available width with a sky based on **body-clock time** and a fade into the page below.
It shows dot-matrix airport codes, the plan stage, city title and next action.
Compact phones add the body offset to the stage when the dial lacks its offset label.
The next-action line includes both zones, or a date and time for actions more than 24 h away.
It always counts from now, including during day previews and scrubbing. Cards below avoid repeating this line.

Scrolling collapses the header to a navigation row with the trip title.
The sky keeps its expanded layout and clips to the header. Its bottom mask moves and fades without redrawing the sky.
Short windows keep the header collapsed, with stage and next action below the title.
Times move below the action headline when necessary to prevent clipping.

Issue #21 adds a sun or moon on a faint dashed half-sine path across the navigation row.
The sun follows sunrise to sunset with a warm radial glow strongest near sunrise and sunset.
The moon follows sunset to sunrise, with seeded stars appearing through dusk.
An outline ring marks the equivalent local-clock position.
An arc joins the markers when their difference is at least ½ h and both occupy the same half-day.
Aligned markers overlap. If local time falls in the other half-day, its marker sits at the nearer endpoint.

The sky and markers share the dial's NOAA `Sun` sunrise and sunset calculation for that day's location.
`HeaderAmbient` allows a decorative star-and-plane session at most every 12 h, after several seconds of header visibility.
It scales time through `MotionDurationScale` and disables sessions during reduced motion, sleep, Night-safe and short windows.
The dial label or compact header subtitle retains the body offset in text without this animation.

### D. Wavy adaptation indicator ("wavy = jet-lagged")
`LinearWavyProgressIndicator` uses `progress = adaptation %` and `amplitude = { misalignmentHours / initialMisalignment }`.
Its wave amplitude reaches zero at adjustment. Trip rows and widget bitmaps reuse the same indicator.

### E. Great-circle trip card
The proposed orthographic globe uses about 1,500 dots from a bundled 1-bit 360×180 land mask.
A dashed great-circle route carries an `Arrow` tangent to the path through `PathMeasure.getPosTan`.
The plane advances during travel. The caption gives shift hours and direction.
Canvas draws the globe offline without a map SDK.
Cards and editor headers can use `RouteArcBanner`, with dot-matrix `IataCode` airport labels and a dashed arc.
Its custom 5 × 7 Canvas glyphs use original geometry. The arc stays flat until a destination is selected.

### F. Adaptation chart
The proposed chart plots days on the x axis and misalignment hours on the y axis.
A dashed line shows predictions; dots show check-ins. The area fill uses the effort level's colour.
Setup would compare Gentle, Balanced and Max side by side.

### G. Night-safe UI 🔑 (a functional design choice)
During **Avoid light** or **Sleep**, Night-safe must reduce screen light:
- Force dark theme, true black `#000000` surfaces.
- Drop illustration luminance.
- Shift accents to a dim amber/red tone (tone 30–40).
- Disable celebratory motion.
- Switch to a custom *calm* `MotionScheme` (damping 1.0, stiffness ≈ 200).
- Show a banner: "Night-safe mode: you're in an avoid-light window".

### H. Light meter (beyond MVP)
The proposed meter reads `Sensor.TYPE_LIGHT` into a logarithmic lux gauge using `CircularWavyProgressIndicator`.
Wave amplitude increases with lux. Labels identify <50 as dim, 100–500 as indoor, 1k+ as bright and 10k+ as daylight.

### I. Celebration
When the dial rings align, the proposed celebration drops Sunny, Clover4Leaf, PuffyDiamond and Cookie4Sided confetti in advice colours.
The wave flattens and a Fraunces headline says **"Clockblocked."**
Supporting text names destination time and estimated time saved. Play the celebration once and skip its motion under reduced motion.

## 2.4 Visual identity: "Dusk Instrument"

**Visual style:** Use clear dial markings, readable numbers and simple controls. Use dark surfaces and indigo accents, with fixed colours for each advice type. Avoid decorative pastel shapes and stock photos. The colour table below defines the palette.

### Colour
- **Dynamic colour on by default** (Android 12+): `dynamicLightColorScheme` / `dynamicDarkColorScheme` for chrome.
- **Static fallback** from Material Theme Builder, **seed `#4F46E5` Twilight Indigo**, scheme variant *Expressive* or *Vibrant*.
- **Semantic advice colours are fixed "custom colours"**, harmonised toward the current primary (`Blend.harmonize` from material-color-utilities / `MaterialColors.harmonize`). Each gets a tonal palette mapped to color / onColor / container / onContainer, so they stay recognisable under any wallpaper.

| Role | Seed | Light container / on | Dark container / on | Pattern (colour-blind safety) |
|---|---|---|---|---|
| See bright light | Marigold `#FFB000` | `#FFDEA0` / `#261900` | `#5C4300` / `#FFDEA0` | solid |
| See some light | Marigold tone 90 | `#FFEFD3` / `#261900` | `#3F2E00` / `#FFDEA0` | 50% solid |
| Avoid light | Ink Plum `#3B2F5C` | `#E8DEFF` / `#1F1640` | `#362B5E` / `#E8DEFF` | 45° hatch |
| Sleep | Midnight `#1E2A78` | `#DEE0FF` / `#00105C` | `#2B3A8F` / `#DEE0FF` | solid + star dots |
| Nap | Periwinkle `#7C8CFF` | `#E0E3FF` / `#1A1F66` | `#3A4399` / `#E0E3FF` | rounded dots |
| Melatonin | Lilac `#B69DF8` | `#EADDFF` / `#25005A` | `#4F378B` / `#EADDFF` | — |
| Caffeine OK | Espresso Copper `#B5652B` | `#FFDBC8` / `#331200` | `#6B3A12` / `#FFDBC8` | — |
| Avoid caffeine | Copper outline | outline + strike | outline + strike | strike |
| Peak fatigue | Coral `#FF5A4E` | `#FFDAD5` / `#410001` | `#8C1D18` / `#FFDAD5` | zig-zag edge |
| Travel | Sky Teal `#00A3A3` | `#B9F0EF` / `#002020` | `#004F4F` / `#B9F0EF` | dashed |

*(Container/on hexes are M3 tone 90/10 and 30/90 approximations; regenerate them with material-color-utilities `TonalPalette.fromInt(seed)` at build time.)*

Active glyphs use vivid advice fills on the Now card, enabled tool rows and current timeline blocks.
When fill contrast falls below 3:1 against its container or background, an `outline` supplies a thin inner edge.
The outline uses the same hue, darker in light theme and lighter in dark theme, following WCAG 1.4.11.
Light-theme edges use `#8A5A00` for bright light, `#8F6400` for some light and `#4A58D0` for naps.
Optional naps use `#5560D8`; melatonin uses `#7552C4`. Dark-theme sleep uses `#AAB4FF`.
Other fills reach 3:1 without an edge. `ContrastAuditTest` checks every role.

**Sky ramp** (illustrations, headers, dial outer ring). Two-stop gradients, indexed by solar or body time:
- Night `#0B1026 → #1B1F4B`
- Pre-dawn `#2A2E6E → #6B4E9B`
- Dawn `#F49D6E → #FFD29D`
- Day `#8EC5FF → #DDEFFF`
- Golden `#FFB36B → #FF7E6B`
- Dusk `#6B4E9B → #2A2E6E`

Blend 15% toward `primary` when dynamic colour is active so skies sit inside the user's palette.

### Typography
- **Google Sans Flex**: OFL, on Google Fonts since 2025-11-12 [verified]. Axes: `wght 1–1000, wdth 25–151, opsz 6–144, ROND 0–100, GRAD 0–100, slnt −10–0`. **Bundle** the variable TTF in `res/font`, subset to Latin + Latin-ext + symbols with `pyftsubset`, keeping `tnum`, `case`, `ss0x`. Bundle rather than use GMS downloadable fonts, because downloadable fonts don't give reliable arbitrary-axis control and don't work on de-Googled/F-Droid devices. Set axes with `FontVariation.Settings(FontVariation.weight(…), FontVariation.width(…), FontVariation.Setting("ROND", …), FontVariation.Setting("slnt", …))`.
  - Display (times): `wght 300` idle → `wght 700` when emphasised. Animate weight with `fastSpatialSpec` on minute change. `tnum` always on so times don't jitter.
  - Headlines: `wght 600, ROND 40`. Body: `wght 400, ROND 0, opsz auto`. Compact chips/labels: `wdth 88`.
  - Body-clock readouts use `slnt −10, ROND 100` to distinguish them from local time.
  - Map to the M3 type scale. Emphasized styles = +200 weight and, for display, `ROND 100`.
- Use **Fraunces** for onboarding headlines, celebrations, Opus mode and empty states only.
  Its OFL font has axes `opsz 9–144, wght 100–900, SOFT 0–100, WONK 0–1` [verified].
  Set `opsz 144, SOFT 100, WONK 1`.
- Use Roboto Flex as fallback. It has the same axis model except `ROND`.

### Shape roles
Use MaterialShape only for advice glyph containers, dial markers, loading indicators and celebrations.
Cards use rounded rectangles with 28 dp corners; the Now card uses 32 dp corners.

| Meaning | Shape | Morph when it becomes "now" |
|---|---|---|
| See bright light | `VerySunny` | Circle → VerySunny + slow 45° rotation (symmetric, so it loops seamlessly) |
| See some light | `Sunny` | Circle → Sunny |
| Avoid light | `SemiCircle` (sun below horizon) | Sunny → SemiCircle ("sun sets") |
| Sleep | `Pill` (lying down) | Circle → Pill |
| Nap | `Bun` | Circle → Bun |
| Melatonin | `PuffyDiamond` (night sparkle) | Circle → PuffyDiamond + twinkle scale |
| Caffeine | `Cookie4Sided` | Circle → Cookie4Sided |
| Peak fatigue | `SoftBurst` | Circle → SoftBurst + gentle pulse |
| Flight / travel | `Arrow` | rotates to heading |
| Adapted | `Flower` | Circle → Flower bloom |
| Plan computing | `LoadingIndicator(polygons = listOf(Sunny, Pill, PuffyDiamond, Circle))` | "a day passing" |
| Easter egg | `PixelCircle`, `PixelTriangle`, `Ghostish`, `Heart` | see 2.7 |

### Iconography
**Material Symbols Rounded** (Apache-2.0), imported as vector XML or `ImageVector` for only the icons we use. Don't pull in the deprecated `material-icons-extended`. Defaults: `wght 400, opsz 24, GRAD 0` (`−25` on dark), `FILL 0` inactive → `FILL 1` active, cross-faded with an effects spec.

Mapping: `light_mode`, `wb_twilight`, `dark_mode`, `bedtime`, `hotel`, `pill`/`medication`, `coffee`, `no_drinks`, `flight_takeoff`, `flight_land`, `connecting_airports`, `public`, `schedule`, `battery_alert`, `edit_calendar`.

**Custom icons** (24 dp grid, 2 dp rounded strokes to match Symbols): *sunglasses*, *sleep mask*, *body clock* (a clock with a crescent hand), *jet lag wedge*.

Always pair icons with a text label in cards (fixes complaint #9).

### Illustration style + 10 subjects (all buildable as Canvas / ImageVector)
**Rules**
- 120×120 dp art board on an 8 dp grid.
- 2–3 flat fills from **colour roles** (primaryContainer, tertiary, surfaceContainerHighest, onSurface) so dynamic colour works.
- One accent max. Gradients only for skies.
- Detail strokes 2 dp `onSurface @ 80%`, round caps.
- Depth via a **"cut-paper offset shadow"**: the same path translated (3 dp, 3 dp) in a darker tone. No blur, no grain, no faces.
- Author each illustration as `@Composable fun XxxArt(colors: ArtColors, progress: Float = 0f)`. Export a static `ImageVector` (or render to `Bitmap`) for Glance.

| # | Subject | Composition (buildable) | Motion |
|---|---|---|---|
| 1 | **Two Clocks** (onboarding hero, empty state) | Two overlapping discs. Left disc (primaryContainer) holds a `Sunny` sun top-left. Right disc (inverseSurface) holds a crescent (circle minus offset circle via `Path.op(DIFFERENCE)`). Lens overlap filled tertiary. A 6 dp round-cap arc arrow sweeps over the top from right to left. 3 `PuffyDiamond` stars | Discs slide together on "adapted"; arrow trims in |
| 2 | **Shades on** (Avoid light) | `VerySunny` (tertiary) wearing sunglasses: two rounded rects (40% radius, onSurface) + 2 dp bridge, a white 30% highlight slash per lens. `SemiCircle` horizon below | Sunglasses drop in with fastSpatial bounce |
| 3 | **Window light** (See bright light) | 4-pane window (24 dp corner frame) with `VerySunny` peeking top-right; 8 rounded ray segments | Rays rotate 0→45° loop |
| 4 | **Pillow moon** (Sleep) | Crescent lying on a `Pill` pillow/cloud; three "z" glyphs in Fraunces | z's drift up and fade |
| 5 | **Night capsule** (Melatonin) | Capsule (two half-pills) split diagonally lilac / surface; tiny crescent in the upper half; `PuffyDiamond` sparkle | Sparkle twinkle |
| 6 | **Little & often** (Caffeine) | Cup = `RoundedPolygon` trapezoid with `CornerRounding(8dp)`, ellipse saucer, three **sine-wave steam lines** (echoing the wavy indicator). *Avoid* variant: steam lines lie flat + a `Pill` strike | Steam phase animates |
| 7 | **Great circle** (Trip) | Dot-matrix hemisphere (1.5 dp dots), dashed arc, `Arrow` plane on the tangent | Plane advances via `PathMeasure` |
| 8 | **Power nap** (Nap) | `Bun`-shaped hammock between two posts, a `SemiCircle` sun half-set behind | Gentle sway (rotation ±3°) |
| 9 | **Running low** (Peak fatigue) | Coral `SoftBurst` behind a battery (rounded rect) whose fill level has a wavy top | Wave sloshes |
| 10 | **Bloom** (Adapted / celebration) | `Flower` scales in, a check mark path trims on, orbiting `Clover4Leaf`/`Sunny` confetti | Bounce + orbit |
| 11 | **Suitcase o'clock** (No trips yet) | Suitcase rounded rect, `Arch` handle, a clock face on its side, luggage tag with "?" | Clock hand ticks once per second, 3 times, then rests |

### Day and night motion
- **Day (body-clock day):** `MotionScheme.expressive()`. Card → Now uses defaultSpatial; glyph morphs use fastSpatial; colour uses defaultEffects.
- **Night / avoid-light windows:** custom calm scheme (no overshoot, about 1.5× slower) + Night-safe UI.
- **Signature moments:** inner-ring rotation on day change (Standard slowSpatial, no overshoot); hour-tick haptics while scrubbing; `SEGMENT_TICK` haptic when crossing an advice boundary; ring "click" + confetti on adaptation; changed digits roll vertically (`RollingText`, Standard spatial; no weight spring, see MOTION.md).
- **Predictive back:** plan screen shrinks back into its trip circle on the Trips list (shared element).
- **Reduce motion:** respect `ANIMATOR_DURATION_SCALE == 0` plus an in-app toggle. Morphs become cross-fades, waves go flat (`amplitude = 0`), no confetti.

## 2.5 Screen sketches (component choices)

- **Trips (home):** `LargeFlexibleTopAppBar` titled "Clockblock" with the current body-clock sky. Trip rows show destination (headlineEmphasized), dates, a mini dial, and a wavy adaptation line. `FloatingActionButtonMenu`: *Add trip* / *Shift schedule (v2)* / *Clock change (v2)*.
- **Trip editor:** stacked leg cards connected by a dotted rail. Each leg: from → to chips, local times with a tz suffix, a duration chip. "+ Add leg" between legs. A sanity banner if the dates cross the date line.
- **Plan:** sky header → **Two skies dial** → Now card (with `SplitButtonLayout`: **Done** | ▾ Skipped · Can't do this · Remind me in 15) → Rail timeline. Bottom `HorizontalFloatingToolbar`: [Now · Day · Trip] toggles + an "I'm delayed" FAB.
- **Card "Why?" sheet:** `ModalBottomSheet` with an illustration, a 2-sentence mechanism, "If you skip this: ~X h slower", alternatives ("Can't go outside? Sit by a window, lights on, screen bright"), citations.
- **Onboarding:** full-bleed pages, Fraunces headlines, `ButtonGroup` for chronotype/effort, `ToggleButton`s for tools, the sleep-arc dial picker.
- **Settings:** profile (sleep, chronotype, tools), reminders (lead time, channels, quiet rules, test reminder), appearance (dynamic colour, theme, reduce motion, Night-safe auto), data (export/import, delete all), about/science/licences.

## 2.6 Copy voice
Use short sentences with specific actions and times. Describe estimated body clock times as estimates.
Avoid jokes, idioms and blame. Examples:
- "See bright light until 15:00. Go outside or sit by a window."
- "Avoid light for 2 h 10 m. Wear sunglasses or stay somewhere dim."
- "Your estimated body clock time is 04:12."

When users skip advice, explain the possible effect and show the next action.

## 2.7 Easter eggs (tasteful; never during sleep windows, never block function, disabled by reduce-motion)

1. **Rewind ("Clockblock").** Long-press the dial centre and drag counter-clockwise: the hand spins backwards, the sky gradient reverses (the sun sets in the east), `CLOCK_TICK` haptics each hour. After a full 24 h back-spin: snackbar *"Clockblocked. If only it were that easy."* Then the dial springs home with an expressive slowSpatial overshoot.
2. **Moon phases.** Tap the moon on the dial 7 times. It morphs through `Circle → Cookie12Sided → Clover8Leaf → Ghostish → Heart` and ends as a *bitten cookie* (`Cookie9Sided` minus a circle). Tip: *"Midnight snack? Your gut has a clock too."*
3. **Opus mode.** Tap the version number 7 times in About (homage to Android developer options). Your plan is **performed**: the rail becomes a five-line staff, each advice block becomes a note (bright light = high, sleep = low, caffeine = staccato ticks), synthesised with a tiny `AudioTrack` sine/triangle synth (~15 s). The theme switches to "concert hall" (black + gold `#D4AF37`, Fraunces everywhere). Title card: *"Opus No. 1 in Jet-Lag Minor."* Stays unlockable in Settings → Appearance.
4. **24.2.** In the time picker, keep scrolling past 23:59: "24:12" peeks out with *"Your body's day is about 24.2 hours (Czeisler 1999). That's why flying west feels easier."* Then it rubber-bands back.
5. *(Bonus)* **Konami swipe** (↑↑↓↓←→←→ on the timeline): **8-bit mode**. Every glyph morphs into `PixelCircle`/`PixelTriangle`, digits switch to `wdth 25`, and the sky quantises to 6 bands. MaterialShapes ships pixel shapes, so the tie-in is native.

---

# Part 3 — Widgets & glanceable surfaces

## 3.1 Principles
- **What to show at a glance:** (1) **what to do now**, (2) **until when**, (3) **what's next**, (4) optionally **how far you've adapted**. Nothing else.
- **One scheduler for everything.** A single `PlanTicker` schedules exact alarms at every advice transition. Each alarm (a) posts/updates the Now notification, (b) calls `GlanceAppWidget.updateAll()`, (c) updates Wear tiles. No periodic polling; no drift between surfaces (fixes complaint #3, including Timeshifter's watch/phone mismatch).
- **Glance + GlanceTheme** for dynamic colour. Advice colours are harmonised at render time. Edge-to-edge content, system corner radius, 48 dp touch targets ([widget layouts](https://developer.android.com/design/ui/mobile/guides/widgets/layouts), [sizing](https://developer.android.com/design/ui/mobile/guides/widgets/sizing), [style](https://developer.android.com/design/ui/mobile/guides/widgets/style)).
- **Generated previews** (`AppWidgetManager.setWidgetPreview`, API 35) using a sample "Lisbon → Tokyo" plan so the picker looks alive ([Android 15 features](https://developer.android.com/about/versions/15/features)).
- **Countdowns without battery cost.** Use a `Chronometer` in count-down mode via `AndroidRemoteViews` (Glance has no native chronometer), or show "until 15:00" (absolute times don't need ticking). Redraw dial bitmaps at transitions plus at most every 15 min.

## 3.2 Home-screen widgets

| Widget | Size (cells) | Content | Notes |
|---|---|---|---|
| **Next up** ⭐ | 2×1 → 4×1 (responsive) | Shape glyph in advice container · **"Avoid light"** (titleMediumEmphasized) · "until 18:00 · then Sleep" · ticking "42m" at 4×1 | Tap → Plan scrolled to Now. A 1×1 variant shows just the glyph + "42m" |
| **Two clocks** | 1×1 → 4×3 (responsive) | The Two skies dial (2×2, 2×3, 4×3) or the Two strips (1×1, 2×1, 4×1, 2×2 landscape) from the app's dial spec, with the now card and Done on larger sizes | Shipped as a Remote Compose document (see A); the needle and readouts follow the launcher's clock |
| **Today ribbon** | 4×2 | Next 4 blocks as capsules on a horizontal rail (the current one filled and larger) + body-time chip + day label ("Arrival +1") | Each capsule deep-links to its card |
| **Trip countdown** | 2×2 / 3×2 | Before: "Tokyo · in 3 days · adjustment starts tomorrow 07:00" + mini great-circle. After landing: "Day 2 · 60% adapted" + static wavy line whose amplitude shrinks daily | Becomes a "Clockblocked ✓" Flower state when adapted |
| **Quick actions** (v1.x) | 2×1 toolbar | [I'm delayed] [Can't do this] [Light meter] | Toolbar canonical layout |

Inspiration for these: Flighty's Live Activity (countdown + plane-on-track), Pixel Weather's shaped widgets, Pixel Clock's shaped/world clock widgets, Nothing's dot-matrix widgets, Rise's energy widget (curve + "now" dot), Structured's "next task" widget.

## 3.3 Lock screen, status bar & AOD
- **Phones have no general third-party lock-screen widget surface** (keyguard widgets returned on tablets / the communal "hub"; check phone status per Android version before promising anything). **The lock-screen story is the notification**:
  - **Ongoing "Now" notification** (category `CATEGORY_REMINDER`, `setOnlyAlertOnce`, silent updates), a `DecoratedCustomViewStyle` with Remote Compose content (#49; the classic views are the fallback): the advice glyph beside **"Avoid light"** and "until 18:00 · 02:00 in Tokyo" (the block's own end, with the other zone's time); expanded adds a live progress bar with the time left and "Next: Sleep at 20:00 · 04:00 in Tokyo". One document carries the light and dark palettes, and the shade's theme picks one. Actions: *Done*, *Can't do this*, *Snooze 15 min*. The small icon is always the app's Horizon mark (AOD-legible); the advice glyph sits on its chip in the body. Details in [surfaces.md](surfaces.md#the-now-notification).
  - **Android 16 Live Update on travel day** via `Notification.ProgressStyle` ([feature page](https://developer.android.com/about/versions/16/features/progress-centric-notifications), [guide](https://developer.android.com/develop/ui/compose/notifications/progress-centric)):
    - progress = minutes elapsed in the travel window (leave home → hotel)
    - **segments** = advice blocks, coloured by advice colour
    - **points** = take-off, landing, layovers
    - **tracker icon** = plane glyph
    - a short status-bar chip text like "☀ 42m"

    Promotion criteria (ongoing, user-initiated, time-sensitive) fit a travel-day plan the user started. *Verify current promotion permission/API names (`setRequestPromotedOngoing`, `setShortCriticalText`) against the API level we target.* Outside travel days, use the standard ongoing notification.
- **Wear OS (v1.x):** Tile showing a mini dial + "Avoid light · 42m". Complications: `SHORT_TEXT` "☀ 42m", `RANGED_VALUE` adaptation %, `SMALL_IMAGE` glyph.
- **Quick Settings tile:** label = current action, subtitle = "until 18:00". Long-press opens the plan. A tap could toggle Night-safe mode.

## 3.4 Notification hygiene (Timeshifter's biggest UX failure)
- Default: **one notification per transition**, collapsed into the single ongoing Now notification. No stacked history.
- Per-advice channels so users can mute caffeine without muting light.
- Lead time 0/15/30 min (default 15).
- Never alert inside a sleep block except a "wake / sleep window over" alarm (optional, uses an alarm-style channel).
- **Weekly reliability check**: if exact alarms are revoked or battery optimisation is killing us, show a one-tap fix card (link to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` / ignore-battery-optimisation settings) and a "send test reminder" button.

---

## Sources (consolidated)
- Timeshifter: [home](https://www.timeshifter.com/) · [jet lag app](https://www.timeshifter.com/jet-lag-app) · [how to timeshift](https://www.timeshifter.com/timeshifting/how-to-timeshift) · [advice guide](https://www.timeshifter.com/timeshifting/advice-guide) · [what you need](https://www.timeshifter.com/timeshifting/what-you-need) · [support/jet lag FAQ](https://www.timeshifter.com/support/jet-lag) · [app comparison](https://www.timeshifter.com/jet-lag/jet-lag-app-comparison) · [shift work app](https://www.timeshifter.com/shift-work-app) · [Google Play](https://play.google.com/store/apps/details?id=com.timeshifter.timeshifter) · [App Store](https://apps.apple.com/us/app/timeshifter/id1380684374) · App Store reviews via `https://itunes.apple.com/{us,gb,au}/rss/customerreviews/id=1380684374/sortby=mostrecent/json`
- Reviews/press: [Wirecutter](https://www.nytimes.com/wirecutter/reviews/timeshifter-app-review/) · [DIY Travel](https://diytravelagent.blog/2026/05/12/timeshifter-app-review-does-it-actually-work/) · [Rick Steves forum](https://community.ricksteves.com/travel-forum/general-europe/jet-lag-i-tried-the-timeshifter-app) · [Fodor's](https://www.fodors.com/news/news/review-timeshifter-the-neuroscience-app-that-claims-to-fight-against-jet-lag) · [VICE](https://www.vice.com/en/article/timeshifter-jet-lag-app/) · [Reddit r/TravelHacks](https://www.reddit.com/r/TravelHacks/comments/1axswmf/experiences_with_timeshifter_app/) · [Appllama screens index](https://appllama.io/apps/1380684374/timeshifter) · [YouTube teardown](https://www.youtube.com/watch?v=rnJivg2Zhlw)
- M3 Expressive: [M3 blog](https://m3.material.io/blog/building-with-m3-expressive) · [Google research](https://design.google/library/expressive-material-design-google-research) · [Google blog launch](https://blog.google/products/android/material-3-expressive-android-wearos-launch/) · [motion](https://m3.material.io/styles/motion/overview/how-it-works) · [shape](https://m3.material.io/styles/shape/overview-principles) · [MaterialShapes ref](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialShapes) · [material3 package](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary) · [androidx source: WavyProgressIndicator.kt / LoadingIndicator.kt / MaterialShapes.kt / tokens](https://github.com/androidx/androidx/tree/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3) · [Compose shapes guide](https://developer.android.com/develop/ui/compose/graphics/draw/shapes) · [Compose variable fonts](https://developer.android.com/develop/ui/compose/text/fonts)
- Fonts: [Google Sans Flex](https://fonts.google.com/specimen/Google+Sans+Flex) (OFL, [METADATA.pb](https://github.com/google/fonts/tree/main/ofl/googlesansflex)) · [Fraunces](https://fonts.google.com/specimen/Fraunces) · [Roboto Flex](https://fonts.google.com/specimen/Roboto+Flex)
- Widgets/notifications: [widget layouts](https://developer.android.com/design/ui/mobile/guides/widgets/layouts) · [sizing](https://developer.android.com/design/ui/mobile/guides/widgets/sizing) · [style](https://developer.android.com/design/ui/mobile/guides/widgets/style) · [Android 15 generated previews](https://developer.android.com/about/versions/15/features) · [Android 16 progress-centric notifications](https://developer.android.com/about/versions/16/features/progress-centric-notifications)
- Inspiration: [Flighty](https://flighty.com/) · [Rise Science](https://www.risescience.com/) · [Gentler Streak](https://gentler.app/) · [Structured](https://structured.app/) · [Sleep Cycle](https://www.sleepcycle.com/) · Entrain (U-Michigan, Forger lab; open circadian model app) · Arcashift (site unreachable during research)
