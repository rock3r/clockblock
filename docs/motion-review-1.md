# Motion review — Clockblock (main @ 1a37da0)

## Verdict
**Block**
Method: dual-agent (A: a4efefbe · B: 358ff83e). A was recorded before B entered synthesis.

Blocking: unresolved **floor** violations F-001 ×3 and F-002 ×2 (plus a latent F-007). There are no `DECIDED` entries, because MOTION.md is all `OBSERVED` and still needs your ratification.

## Judgment (Assessment A, condensed but complete in substance)
- **What should not exist on the plan screen:**
  - The endless glyph loops on the Now card and the rail "now" block (`AdviceGlyph` ambient: spin, twinkle, pulse). They carry no information, and they run the whole time the plan screen is on screen, 5–15 times a day.
  - The fastest of them (Melatonin) runs at bedtime. That is the opposite of "syrupy by night".
- **The travelling wave** on the plan screen and the in-progress trip cards. The meaning is the amplitude; the travel is wallpaper.
- **Toolbar ownership conflict** in `PlanContent`: `exitAlwaysScrollBehavior` and an `AnimatedVisibility` gate fire on the same scroll with opposite intent.
- **The Rewind payoff is missing.** The return springs from about −2 min, not −1440, so the overshoot is invisible. The sky doesn't reverse. It bounces with Expressive even inside Night-safe.
- **The Night-safe re-theme is a one-frame hard cut** and it rebuilds the subtree, losing remembered state. Black to bright at wake-up hurts.
- **A possible bright flash** from the loading state before Night-safe Ready.
- **Releasing a scrub is two events:** the cards snap to now while the hand springs back (≈400 ms of disagreement between two pieces of data). The Now card also loses its split button while scrubbing, a ≈56 dp height jolt.
- **The meaningful motions are rarely seen:** the glyph morph and the ring day-rotation only fire if the screen is open at the boundary.
- **The celebration doesn't hold together:**
  - It isn't linked to the dial (the rings never align).
  - The `AnimatedVisibility(visible = true)` fade never plays.
  - Four movers start on one frame, under the nav transition.
  - Two confetti systems run, and the burst comes from the screen centre instead of the Bloom.
- **Calm motion applies too narrowly:** only inside the plan screen and only when Night-safe colours are on. Loops ignore the scheme.
- **Smaller items:**
  - Onboarding steps use bouncy `containerSpatial` and a full/5 slide, against the app's `navigationSpatial` 30 dp shared axis.
  - Chevrons rotate on a bouncy spring.
  - The Opus title card has a double entrance (Dialog window animation plus inner scaleIn).
  - The moon egg snaps from the crescent to a Circle, steps stop-start, and taps 1–6 give no feedback.
  - Hiding the nav suite while pushing the editor makes two events.
- **MOTION.md:** the frequency map wrongly calls the plan screen 100+/day; the driver is continuous on-screen time.
  - Missing rows: Night-safe cross-fade, moon egg, 24.2 egg, toolbar.
  - design.md and MOTION.md disagree on ring overshoot. The code (no bounce) is right.
- **Already fine:** meaning survives with animations off; tab fade-through, hierarchy, predictive back and panes; the onboarding welcome; the 24.2 egg; trips swipe and sections.

## Synthesis
- **A and B agree:** T-009 on the Now card and rail glyph loops, and on both wavy indicators. T-020/T-016 on onboarding steps and the Opus card. T-026 on the celebration. T-019 on loading.
- **B caught what A missed:**
  - F-001 composition reads: ToolsEditor and NowCard chevrons, and the trips scrim alpha check.
  - F-002 dropped fling velocity on the dial and SleepDial release.
  - T-008: the FAB menu, celebration and onboarding back are not seekable.
  - T-017: art entrances replay on re-entry.
  - T-024 asymmetries in the pane exit and predictive-back direction.
  - T-012: the navigation guard gates input during transitions.
- **B false positives in context:**
  - T-012 `snapTo` before `animateTo` in the dial and SleepDial: the visual position is preserved.
  - F-002 on a scrub release, if A's fix (snap the hand) is applied: no animation, so no velocity is needed.
  - T-004 on rewind: an explicit easter-egg exception, documented.
- **Severity raised by convention:**
  - T-009: NowCard's own art is still because "frequent surface".
  - F-001: the TripsContent chevron already uses `graphicsLayer`.
  - T-017: onboarding uses `rememberSaveable`.

## Findings → fixes
| Rule | Where | Class | Severity | Fix |
|---|---|---|---|---|
| F-001 | ToolsEditor.kt:140,150 | floor | major | `Modifier.graphicsLayer { rotationZ = rotation }` (read inside the lambda) |
| F-001 | NowCard.kt:218,228 | floor | major | same |
| F-001 | TripsContent.kt:154-159 | floor | major | Drop the composition `if (alpha == 0f)`. Gate with `transition.isRunning \|\| target` or draw unconditionally inside `drawBehind { if (alpha() > 0f) … }` |
| F-002 | TwoClocksDial.kt:232-237,268 | floor | major | Scrub release: `snapTo(0f)` (A's "one event" fix). Rewind: track velocity with `VelocityTracker` and pass `initialVelocity` |
| F-002 | SleepDial.kt:246-259 | floor | major | Seed `animateTo(…, initialVelocity = tracker.calculateVelocity()…)` |
| F-007 | TwoClocksDial.kt:104,129 | floor | major (latent) | Make the scrub offset `rememberSaveable`, or default `returnOnRelease = true` |
| T-009 | NowCard.kt:95, PlanRail.kt:368-373, AdviceGlyph.kt:123,198-209 | taste | major | Remove the endless ambient loops. Play one cycle on appear (or when the active advice changes since last seen), then rest |
| T-009 | TripCard.kt:71-75, PlanCards.kt:132-137, WavyAdaptationIndicator.kt:69 | taste | major | `waveSpeed = 0` on these surfaces; keep the amplitude |
| — (A) | PlanContent.kt:296,335-342 | taste | major | One owner: keep the content gate, drop `exitAlwaysScrollBehavior`; enter on `navigationSpatial()`/`dataSpatial()`, not `containerSpatial()` |
| — (A) | TwoClocksDial.kt:232-237, Motion.kt:93-94 | taste | major | Rewind: animate from the raw −1440 → 0, `report()` frames so the sky follows, use the Calm scheme when Night-safe |
| O-/A | PlanContent.kt:154-160 | taste | major | Stable `ClockblockTheme` call shape; cross-fade the scheme on `colour()`. No `if` branch that rebuilds the subtree |
| T-019 | PlanContent.kt:151-153,186-199 | taste | minor | ≈150 ms show delay before the loader; cross-fade Loading → Ready; start in the night theme when Night-safe applies |
| — (A) | NowCard.kt:172-181 | taste | major | Reserve the split-button slot while previewing (alpha 0, disabled); keep the card height stable |
| T-026 / A | Celebration.kt:52-69, JourneyArt.kt:166-185, ConfettiBurst.kt:90 | taste | major | Sequence: nav settles → rings align (CONFIRM haptic) → overlay fade (MutableTransitionState(false)) → Bloom scale → burst from the Bloom centre + check trim; drop the orbit loop |
| T-008 | TripsContent.kt:107; Celebration.kt:54-60; OnboardingScreen.kt:198-230 | taste | major | Use `PredictiveBackHandler`/`NavigationBackHandler` progress driving `SeekableTransitionState` (FAB menu, overlay, onboarding steps) |
| T-020/T-016 | OnboardingScreen.kt:243-247 | taste | major | Use the shell's `navigationSpatial()` + `navigationFadeIn/Out()` and a 30 dp shared axis |
| T-017 | ArtKit.kt:239-248, JourneyArt.kt:217-236 | taste | major | `rememberSaveable` for the played flag |
| T-024 | AppTransitions.kt:59-69,81-84; OnboardingScreen.kt:224-229 | taste | major/minor | Exit the pane toward its entry edge; predictive back drifts toward the forward-entry edge (mirrored) |
| — (A) | NowCard.kt:218, TripsContent.kt:325, ToolsEditor.kt:140 | taste | minor | Chevron rotation on `dataSpatial()` (no bounce) |
| T-016/T-020 | AboutScreen.kt:114-117,276-280 | taste | minor | Single entrance: disable the Dialog window animation (`DialogProperties(decorFitsSystemWindows…)` + `windowAnimations = 0`) or drop the inner scaleIn; add an exit |
| — (A) | PlanHeader.kt:211-219,279,294 | taste | minor | Start the morph from the crescent; one continuous progress over the chain; light haptic on taps 1–6; restore the state layer |
| T-010/T-020 | ToolsEditor.kt:153-156, SleepDial.kt:381-384, SettingsScreens.kt:708-711, TripsContent.kt:292-307 | taste | minor | Use one tier per block (expand/shrink on `fade()`-tier effects, or both spatial) |
| — (A) | app-wide | taste | major | Derive Calm motion from body-clock night at app level (independent of the Night-safe colours); loops/periods read the scheme |
| — (A) | ClockblockApp.kt:101-107 | taste | minor | Hide the suite without animation on the editor route |
| docs | MOTION.md | — | — | Fix the frequency map (plan screen 5–15 opens/day; continuous on-screen time); add rows (Night-safe cross-fade, moon egg, 24.2, toolbar, celebration sequence); settle ring overshoot = none; remove the unused `PlaneMillis` |

## Floor / taste split
Floor: 6 findings (F-001 ×3, F-002 ×2, F-007 ×1 latent). Taste: 21 findings.
