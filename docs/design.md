# Clockblock — Product & Visual Design Research

> Scope: (1) Timeshifter teardown + MVP/"beyond" feature lists, (2) Material 3 Expressive visual & motion direction + a full visual identity, (3) widgets / glanceable surfaces.
> Research date: 2026-10-04. Facts marked **[verified]** were read directly from the cited source during this session; **[reported]** = from user reviews/press; **[inferred]** = my reading of screenshots/marketing copy or general knowledge, so check it before relying on it.

> [!NOTE]
> This document was written before the app was built. The visual identity in it is current, but some technical
> plans changed during implementation: widgets use Remote Compose instead of Glance,
> data lives in DataStore instead of Room, and `AdviceAlarmScheduler` plays the role described here as
> `PlanTicker`. For how the app works today, read [architecture.md](architecture.md) and [surfaces.md](surfaces.md).

---

## TL;DR (opinionated)

1. **Timeshifter's science is good; its UX is fragile.** Users love the results but hate four things: (a) **brittle trip editing** (3-hour edit window, "times overlap" bugs, can't add flights without a flight number), (b) **notifications that are unreliable or contradict the in-app plan**, (c) **timezone confusion** (the plan only shows the timezone you're currently in), and (d) **pay-per-trip pricing for something that runs entirely on-device.** We fix all four by being **offline-first, account-free, manual-entry-friendly, dual-timezone, and free.**
2. **Our signature visual is the "Two skies" dial**: a 24 h radial dial with an outer ring for the sky where you are and an inner ring for the sky your body thinks it is under, which turns day by day until it lines up with the outer one. Jet lag = the angle between the two nights. Everything else (timeline, widgets, notifications, celebration) comes out of that one idea.
3. **Our M3 Expressive metaphor: "wavy = jet-lagged, flat = adapted."** The wavy progress indicator's amplitude shrinks as you adapt. Shape-morphing advice markers (Sun → `Sunny`, Sleep → `Pill`, Fatigue → `SoftBurst`…) come alive when an action becomes "now".
4. **Identity**: "Dusk Instrument": a precise scientific instrument at twilight. Primary seed **Twilight Indigo `#4F46E5`**, tertiary **Marigold `#FFB000`** (light), fixed semantic "advice colours" harmonised to dynamic colour. Type: **Google Sans Flex**, which *is* open source (OFL) and on Google Fonts since 2025-11-12, with axes `wght 1–1000, wdth 25–151, opsz 6–144, ROND 0–100, GRAD 0–100, slnt −10–0` [verified via Google Fonts metadata + google/fonts repo METADATA.pb]. Bundle it as a variable TTF. Use **Fraunces** (SOFT/WONK axes) only for editorial moments.
5. **Glanceable first**: a single ongoing **"Now" notification** instead of 15 separate pings. On travel day it becomes an Android 16 `Notification.ProgressStyle` Live Update with segments for advice blocks, points for take-off/landing, and a plane tracker icon. Glance widgets: *Next up*, *Two Clocks dial*, *Today ribbon*, *Trip countdown*.

---

# Part 1 — Timeshifter product teardown

## 1.1 Product family & positioning [verified]

| Product | What | Pricing |
|---|---|---|
| **Timeshifter Jet Lag** (iOS/Android) | Personalised jet lag plans per trip | First plan free; then **US$9.99/plan or US$24.99/year** unlimited ([jet-lag-app](https://www.timeshifter.com/jet-lag-app), [Play listing](https://play.google.com/store/apps/details?id=com.timeshifter.timeshifter)) |
| **Timeshifter Shift Work** | Advice for rotating/night/irregular shifts | 30-day trial, then subscription ([App Store lookup](https://itunes.apple.com/lookup?id=1380684373&entity=software)) |
| **My Circadian Day®** | Daily timing for light, meals, exercise | ([advice guide](https://www.timeshifter.com/timeshifting/advice-guide)) |
| Concierge | Human-made plans for athletes/execs | Enterprise |

Marketing pillars (all trademarked): **Circadian Time™** (advice timed to biological time), **Practicality Filter™** (fits advice to the "real world"), **Quick Turnaround®** (auto-detects short trips, so you stay on home time), pre-travel adjustment, push notifications, "intuitive **3-hour view**" plus full-plan view, complex itineraries with **unlimited stopovers**, and flight editing for delays ([jet-lag-app](https://www.timeshifter.com/jet-lag-app)). Science claims: built on the St Hilaire 2012 light PRC, Eastman & Burgess 2009, and Czeisler 1999 (period ≈ 24.2 h); "96.4% of users… without feeling jet lagged", from about 130k post-flight surveys. Play: 4.6★ / 1.9k reviews, 100k+ installs. iOS: 4.66★ / 2.6k ratings [verified].

Disclaimers they ship: "intended for healthy adults 18+", "**not intended for pilots and flight crew on duty**", not FDA evaluated [verified, Play listing]. **We should ship similar disclaimers.**

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

**What's missing** [reported, App Store reviews via iTunes RSS]: "I can't sleep on planes"; "I won't wake at 6 am on vacation" (destination sleep preference); work hours on pre-travel days ("avoid light 8–11 am… I have to be at work at 8:30"); time to reach the airport ("4 hours away from the airport… told me to sleep during the time I need to travel"); "told me to sleep while I'm on my 2 hour layover".

## 1.3 Trip creation [verified unless noted]

- Search **by flight number**, then add it to a "basket" (swipe left to delete before submitting).
- Multi-leg trips and unlimited stopovers. Flights are **grouped automatically** into an outbound plan and a homebound plan ("your flights may be grouped differently than you expect" for multi-city).
- Flights can be edited, replaced or deleted **only up to 3 hours after the original departure**. After that they're greyed out and you have to "contact support".
- **You can't enter past flights** and **can't start a plan earlier than recommended**.
- Internet is needed only to create or modify a plan; the plan works offline after that. **No print/export.**
- [reported] Small airlines and unnumbered refuelling legs aren't found, with "no way to enter your info manually". Other reported problems: a "time zones don't match up… edit flight times" validation error, and a deleted flight that still blocks re-entry.

## 1.4 The plan

**Structure.** The home ("advice") page lists plans as **circles with destination and date**. A plan view has a **3-hour "now" view** and a **full plan** (day by day, hour by hour). Days run: **pre-travel days** (e.g. 2 days ahead for US West → Europe), **travel day(s)** (including a pre-flight nap and in-flight sleep/light advice), and **post-arrival days** until adapted. The homebound plan is separate. Notifications arrive **about 30 min before each change**, including on Apple Watch [reported].

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

Field-guide copy also worth echoing ([how to timeshift](https://www.timeshifter.com/timeshifting/how-to-timeshift)): "You can avoid light anywhere (sunglasses)… get exposed to light anywhere (turn on lights, max screen brightness)… If you can't sleep, just stay in the dark… Follow the advice as much as possible." Kit list: dark sunglasses, sleep mask, optional melatonin ([what you need](https://www.timeshifter.com/timeshifting/what-you-need)).

## 1.5 Shift work app [verified]
Quick Shift Entry (multiple dates in seconds), **Commute time** + **"get ready" time**, **Fatigue prediction**, advice notifications. It supports rotating, fixed-night, on-call and irregular schedules ([shift-work-app](https://www.timeshifter.com/shift-work-app)). *For us this is a natural v2: the same engine, fed by a repeating schedule instead of flights.*

## 1.6 Settings, widgets, platform
- Profile (bottom-left icon) → advice settings, push notifications [verified FAQ].
- An Apple Watch companion exists. A reviewer reported the **watch showing different advice than the phone** [reported].
- I found no evidence of Android home-screen widgets. An iOS reviewer explicitly asked for **Live Activities** ("leverage newer iOS functionalities, like live notifications") [reported]. **This is an open gap on Android: Live Updates + Glance widgets.**

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

**Design takeaway:** the product must reduce *effort* and *embarrassment*. Use framing like "Wear sunglasses (or stay indoors away from windows)", offer alternatives on each card, and show the *cost of skipping* ("skipping this delays adaptation by ~½ day").

## 1.8 Proposed MVP (v1.0)

**Onboarding (≤ 6 screens, no account)**
1. Welcome: animated "Two Clocks" hero, one-line value prop, "Free & open source" badge.
2. Usual sleep: drag the two ends of a sleep arc on a 24 h dial (like the Android Clock Bedtime screen). Weekday/weekend toggle.
3. Chronotype: 3 large cards (Lark / In between / Owl) + optional 5-question reduced MEQ.
4. Tools: toggles for **Melatonin** (+ fast/slow release), **Caffeine**, **Can sleep on planes**, **Start adjusting before I leave**.
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

**Platform**: offline, Room, no account, no analytics, JSON export/import, ICS export. Glance widgets: *Next up* + *Two Clocks*. M3 Expressive theme + dynamic colour, TalkBack, font scaling. Science page + disclaimers.

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

**Research basis.** Google ran 46 studies with 18,000+ participants. Expressive layouts let users spot key UI elements **up to 4× faster** and closed the age gap: 45+ users found controls as fast as younger ones ([design.google research](https://design.google/library/expressive-material-design-google-research), [M3 blog: building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive), [Google blog launch](https://blog.google/products/android/material-3-expressive-android-wearos-launch/)). *Implication for us:* emphasis is functional. The **current action** must be the most emphasised element on every surface.

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

## 2.2 Inspiration board: what to steal, and from whom

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
Chosen in #46 from three concepts. Two rings and nothing else structural: **the outer ring is the sky where you
are, the inner ring is the sky your body thinks it is under.** The angle between the two nights *is* the jet lag;
once you're adapted the two rings are identical.
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
  `BodyRingMode.Precise` paints the body's night from the model (habitual sleep moved by the offset); there is no
  setting for it yet (#52).
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
  labels shortened to the city and "BODY", no narration, no numerals), **Glance** < 110 dp (the two skies, the needle
  and the two times). The in-app hero is 200–320 dp, so compact phones get Simple; the Now card carries the words.
- **Day change:** the body ring **turns** into place (`ClockblockMotion` slow spatial, no overshoot: the ring is
  data). Rings aligned → `HapticFeedbackConstants.CONFIRM`.
- **Scrub:** drag the needle to preview any time; the cards below sync. `CLOCK_TICK` haptic each hour, `SEGMENT_TICK` at
  block boundaries, and a dot keeps the real now. Tap the centre to return.
- **A11y:** TalkBack: "14:20 local. Your body clock is 09:56. Now: see bright light until 15:00. Next: …" Custom
  actions *Next block*, *Previous block*, *Back to now*.
- **Spec and renderer:** `TwoSkies.spec()` in `dial/spec` is pure Kotlin (no Android UI types) and returns a list of
  `DialOp`s in dp. `drawDialSpec()` paints them with Compose; a Remote Compose widget can paint the same list. Notes
  for that port: the sky rings are `SweepRing`s with `segments()` (Remote Compose has no sweep shader from Kotlin),
  curved text needs `drawTextOnCircle` (to verify), only the needle animates, and widgets use the system font. See
  `dial_widget_fonts.png` in the designsystem goldens for the app font next to the system font. Small widgets get a
  separate "Two strips" layout (Phase 2 of #46).

### B. Day timeline ("Rail")
Vertical rail (Structured-style). Each advice block is a capsule whose **height = duration**. A leading shape glyph sits in an advice-colour container. The rail background is a faint **body-clock sky gradient**, so you *see* that your body is at "night" while the local label says 14:00. The current block expands into the **Now card** with a `CircularWavyProgressIndicator` countdown ring around its glyph. A dual time column shows local time plus a muted secondary tz; tapping the column header cycles Local / Home / Destination / Body.

### C. Body-clock sky header
A `LargeFlexibleTopAppBar` over a full-bleed sky painted by **body-clock time**, not local time, with a sun/moon at the body's solar position. Title "Lisbon → Tokyo", subtitle "Day 2 · body +5 h". It collapses to a thin gradient strip. This is the "aha": *outside it's noon, inside you it's 4 am.*

### D. Wavy adaptation indicator ("wavy = jet-lagged")
A `LinearWavyProgressIndicator` with `progress = adaptation %` and `amplitude = { misalignmentHours / initialMisalignment }`. The wave literally **calms down** as you adapt; it is flat on arrival-adapted. Reused in the trip list rows and the widget (as a static bitmap).

### E. Great-circle trip card
Dot-matrix orthographic globe (≈1,500 dots from a bundled 1-bit 360×180 land mask). Dashed great-circle arc with an `Arrow` glyph oriented along the tangent (`PathMeasure.getPosTan`). On travel day the plane advances in real time. Caption "+8 h · 8 zones east · eastward is harder". Fully Canvas, no map SDK, offline. For cards and editor headers the design system also offers a flattened `RouteArcBanner`: dot-matrix airport codes (`IataCode`, our own 5 × 7 Canvas glyphs, not a copy of any brand font) bridged by the same dashed arc, flat until a destination is picked.

### F. Adaptation tide chart
Days on x, misalignment hours on y. The predicted curve is a dashed line; check-ins are dots. The area fill uses the effort level's colour. Shows Gentle / Balanced / Max side by side during setup.

### G. Night-safe UI 🔑 (a functional design choice)
When the plan says **Avoid light** or **Sleep**, the app must not sabotage it:
- Force dark theme, true black `#000000` surfaces.
- Drop illustration luminance.
- Shift accents to a dim amber/red tone (tone 30–40).
- Disable celebratory motion.
- Switch to a custom *calm* `MotionScheme` (damping 1.0, stiffness ≈ 200).
- Show a banner: "Night-safe mode: you're in an avoid-light window".

Timeshifter tells you to avoid screens; we make our own screen comply.

### H. Light meter (beyond MVP)
Reads `Sensor.TYPE_LIGHT` into a log-scale lux gauge built on a `CircularWavyProgressIndicator`; amplitude rises with lux ("more light, more energy"). Labels: <50 dim · 100–500 indoor · 1k+ bright · 10k+ daylight.

### I. Celebration
When the dial's rings align, a **confetti burst of MaterialShapes** (Sunny, Clover4Leaf, PuffyDiamond, Cookie4Sided) in advice colours, with a physics drop. The wavy line flattens. Headline in Fraunces: **"Clockblocked."** Sub: "Your body is on Tokyo time. 3 days, 2 h faster than going it alone." Fire it once, and skip it under reduce-motion.

## 2.4 Visual identity: "Dusk Instrument"

**Personality:** a precise scientific instrument (think Braun/Dieter Rams dials, aviation instruments) at twilight: calm, competent, quietly witty. Not a wellness app (no pastel blobs, no stock photos), not a corporate travel tool (no navy-and-grey).

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

**Glyph edge.** The active advice glyph (Now card, checked tool rows, the rail's "now" block) is filled with the vivid colour. Where that fill is too pale to reach 3:1 against its own container and the surfaces it sits on (WCAG 1.4.11 for graphical objects), the role has an `outline`: a deeper (dark theme: lighter) tone of the same hue, drawn as a thin inner edge so the shape reads without changing the advice colour. Light: See bright light `#8A5A00`, See some light `#8F6400`, Nap `#4A58D0`, Nap if you're tired `#5560D8`, Melatonin `#7552C4`. Dark: Sleep `#AAB4FF`. Every other role's fill already reaches 3:1 and draws no edge. `ContrastAuditTest` checks this for every role.

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
  - **Body-clock readouts: `slnt −10, ROND 100`** (the "dreamy" twin of local time).
  - Map to the M3 type scale. Emphasized styles = +200 weight and, for display, `ROND 100`.
- **Fraunces** (OFL; axes `opsz 9–144, wght 100–900, SOFT 0–100, WONK 0–1` [verified]): **only** for editorial moments (onboarding headlines, celebration, Opus easter egg, empty states). Use `opsz 144, SOFT 100, WONK 1`. It adds warmth and a little wit, like a tiny serif signature on an instrument.
- **Fallback:** Roboto Flex (same axis model minus `ROND`).

### Shape language: "shapes carry meaning, never decoration"
A MaterialShape appears **only** as (a) an advice glyph container, (b) a dial marker, (c) loading/celebration. Cards stay rounded rectangles (extra-large 28 dp corners; the Now card 32 dp). This restraint is what keeps it from looking like generic M3E slop.

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

### Motion personality: "Springy by day, syrupy by night"
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
Short, precise, kind, occasionally dry. Examples:
- "See bright light · until 15:00. Outside is best. A window seat counts."
- "Avoid light · 2 h 10 m. Sunglasses on, even if it feels silly. Especially if it feels silly."
- "Your body thinks it's 04:12. Be gentle with it."

Never guilt; skipping shows the cost, then moves on.

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
| **Two Clocks** | 2×2 (square, shaped) | Mini dial bitmap: local ring, body ring, advice arcs, hand. "+5 h" centre chip | Render with Canvas to `Bitmap` in a worker; keep it small (≈ 2×2 cells at device density) to stay under RemoteViews bitmap memory limits |
| **Today ribbon** | 4×2 | Next 4 blocks as capsules on a horizontal rail (the current one filled and larger) + body-time chip + day label ("Arrival +1") | Each capsule deep-links to its card |
| **Trip countdown** | 2×2 / 3×2 | Before: "Tokyo · in 3 days · adjustment starts tomorrow 07:00" + mini great-circle. After landing: "Day 2 · 60% adapted" + static wavy line whose amplitude shrinks daily | Becomes a "Clockblocked ✓" Flower state when adapted |
| **Quick actions** (v1.x) | 2×1 toolbar | [I'm delayed] [Can't do this] [Light meter] | Toolbar canonical layout |

Inspiration for these: Flighty's Live Activity (countdown + plane-on-track), Pixel Weather's shaped widgets, Pixel Clock's shaped/world clock widgets, Nothing's dot-matrix widgets, Rise's energy widget (curve + "now" dot), Structured's "next task" widget.

## 3.3 Lock screen, status bar & AOD
- **Phones have no general third-party lock-screen widget surface** (keyguard widgets returned on tablets / the communal "hub"; check phone status per Android version before promising anything). **The lock-screen story is the notification**:
  - **Ongoing "Now" notification** (category `CATEGORY_REMINDER`, `setOnlyAlertOnce`, silent updates), a `DecoratedCustomViewStyle`: the advice glyph on its colour chip, **"Avoid light"** with "until 18:00" (the block's own end) and a progress bar for the block; expanded adds the other zone after every time ("· 02:00 Tokyo"), "Also now: …" for overlapping blocks, "Next: Sleep at 18:00 · 02:00 Tokyo" and the tip. Actions: *Done*, *Can't do this*, *Snooze 15 min*. Monochrome glyph small icon per advice type (AOD-legible). Details in [surfaces.md](surfaces.md#the-now-notification).
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
