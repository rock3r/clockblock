# MOTION.md — Clockblock

> Agent-drafted from the code in `:core:designsystem` (commit on `feat/design`). Every spec is tagged
> `OBSERVED`; a human must review and re-tag each one `DECIDED` before it is treated as policy.

## Design system
- Material 3 Expressive via `MaterialExpressiveTheme`; `ClockblockTheme` picks the `MotionScheme`:
  `MotionScheme.expressive()` by default, `CalmMotionScheme` in Night-safe **or while the body clock is in its
  night** (`calmMotion`, set by the shell from `JetLagPlan.isBodyNightAt`, home sleep habit as fallback),
  `StillMotionScheme` when motion is reduced (spatial = `snap()`, effects keep Calm fades).
- Intent tokens live in `ClockblockMotion` (`theme/Motion.kt`), read as `ClockblockTheme.motion`. No literal
  `tween()`/`spring()` anywhere else (exception list below).
- Values read as data (dial rings, timeline positions, numbers) bind to the Standard scheme via
  `dataSpatial()` / `dialDayRotation()`; Expressive bounce never wobbles a value. The body sky never
  overshoots (design.md agrees: the ring is data).

## Fallback
Unlisted motion → `ClockblockTheme.motion.containerSpatial()` for movement, `colour()` for colour/alpha.

## Specs
| Intent | Binding | Tag |
|---|---|---|
| Advice glyph Circle → MaterialShape when it becomes "now" | `glyphMorph()` = scheme fastSpatial | OBSERVED |
| Active glyph ambient (VerySunny step, melatonin twinkle, fatigue pulse) | one `ambientOnce(period)` cycle when it becomes active, then rest (saveable "played"); never loops | OBSERVED |
| Glyph rotation / heading change, chevrons | `dataSpatial()` | OBSERVED |
| Glyph fill colour | `colour()` = scheme defaultEffects | OBSERVED |
| Card → Now card, containers around data | `containerSpatial()` = scheme defaultSpatial | OBSERVED |
| Small in-place swaps | `fade()` = scheme fastEffects | OBSERVED |
| Dial scrub settle / "back to now" | `dataSpatial()` (Standard); every frame reported so cards follow the hand | OBSERVED |
| Dial body sky (inner ring) on day change | `dialDayRotation()` = Standard slowSpatial, no overshoot; its ring labels turn with it | OBSERVED |
| Rewind easter egg springing home | `rewindReturn()`: raw −1440 → 0 with the release velocity; Expressive slowSpatial by day, Calm slowSpatial (no bounce) under Calm | OBSERVED |
| Night-safe / light–dark / Opus-mode switch | `themeCrossFade()` = Calm slowEffects palette cross-fade inside `ClockblockTheme` (stable call shape via `NightSafeTheme`) | OBSERVED |
| Illustration entrance / `progress` | `artEntrance()` = scheme slowSpatial, once per instance (saveable); bouncy arts use `glyphMorph()` | OBSERVED |
| Two Clocks discs sliding on adaptation | `containerSpatial()` via `rememberArtValue` | OBSERVED |
| Illustration ambient loops (rare surfaces only) | `ambientLoop(ClockblockMotion.*Millis)` (sun 9 s, twinkle 1.8 s, drift 7.2 s, steam 3.2 s, sway 4.2 s, pulse 2.4 s); ×1.5 under Calm | OBSERVED |
| Confetti flight | `celebrationClock()` (2.2 s linear clock; physics from t), one burst from the Bloom's centre | OBSERVED |
| Celebration sequence | navigation settles (450 ms) → rings turn into alignment on `dialDayRotation()` + `CONFIRM` → overlay fades in on `colour()` → Bloom scales on `glyphMorph()` → check + confetti together; predictive back seeks the overlay fade | OBSERVED |
| Wavy adaptation line | still by default (`travel = false`); travel is opt-in for rare surfaces | OBSERVED |
| Plan loading | nothing for 150 ms (fast loads never flash a loader), then M3 `LoadingIndicator`; ready state cross-fades in (`colour()` / `fade()`) only if the loader was shown | OBSERVED |
| Plan floating toolbar show/hide | one owner: `AnimatedVisibility` fade (`fade()`) + half-height slide on `navigationSpatial()`; no scroll-driven exit-always. Its surface-gradient scrim lives inside the same `AnimatedVisibility` and only fades (one event, nothing slides separately) | OBSERVED |
| "First light" (plan of a trip saved ≤ 90 s ago) | header sun/moon rises from below the header's bottom edge to its resting place with an alpha ramp, `artEntrance()` (scheme slowSpatial), once per plan entry (saveable); clipped to the header. Reduce motion: already in place (static carrier; the rise carries no meaning) | OBSERVED |
| Header moon egg (7 taps) | `CLOCK_TICK` on taps 1–6; crescent waxes to full, then Circle → Cookie12 → Clover8 → Ghostish → Heart → Cookie9 → bite as one continuous `eggChain(steps)` progress | OBSERVED |
| Sleep dial 24.2 egg | wake handle pushed past 24 h peeks "24:12" + Czeisler line, then rubber-bands back; inline note under the phone's Remove animations; off with the in-app Reduce motion and while the plan says sleep | OBSERVED |
| Konami 8-bit egg (↑↑↓↓←→←→ swipes on the plan rail) | glyphs morph to `PixelCircle` / `PixelTriangle` on `glyphMorph()` and straighten; sky quantises to 6 bands; times monospace; session-only, snackbar Exit; off under reduce motion and sleep windows | OBSERVED |
| Suitcase clock ticks | 3 discrete 1 s steps (`delay` × `MotionDurationScale`), then rest | OBSERVED |
| Top-level destination switch (fade-through) | `navigationFadeOut()` 90 ms EmphasizedAccelerate → `navigationFadeIn()` 210 ms (+90 ms delay) EmphasizedDecelerate + scale-in 0.92 via `navigationSpatial()`; ×1.5 under Calm | OBSERVED |
| Hierarchy push/pop (shared axis X, 30 dp, RTL-mirrored) | `navigationSpatial()` = Standard defaultSpatial (no bounce; snaps on reduce) + fades | OBSERVED |
| Predictive back (seekable) | exit scale 0.9 + slide away from swipe edge on `navigationSpatial()`, fade on `colour()` | OBSERVED |
| List-detail pane enter/exit, pane bounds | `navigationFadeIn()` / `navigationFadeOut()`; bounds `navigationSpatial()` | OBSERVED |
| Dot-matrix airport code picked (`IataCode`, `· · ·` → `SFO`) | dots turn to the new characters left to right, cell by cell and column by column, on `colour()` (~180 ms); first composition sits at rest | OBSERVED |
| Rolling readouts (`RollingText` / `RollingTimeText` / `RollingMetricText`) | only the changed run of characters rolls vertically, on `dataSpatial()` (no bounce: the readout is data), with a cross-fade from the same progress; up when the value grows, down when it shrinks; units and unchanged digits stay still; not used for per-frame values (scrubbing) | OBSERVED |
| Dial offset pill ("7 h behind") and body time on a day change | read the body sky's own animated offset in the draw phase, so the words step through the half hours exactly as the ring turns (one event, never two that disagree) | OBSERVED |
| Sleep dial readouts (bedtime / wake pills, centre duration) | follow a drag live with no roll (per-frame values); roll on discrete changes (TalkBack or keyboard nudge, a picked time, the 24.2 rubber band) as rolling readouts; text sized for the widest value so it never resizes | OBSERVED |
| Tool row switched on (onboarding / settings tools) | leading advice glyph Circle → advice shape on `glyphMorph()` (rare: a setup choice), back on `dataSpatial()` (the melatonin glyph twinkles once, as on the plan); row container and text tint to the advice container on `colour()`, read in draw; the switch and label carry the state without them | OBSERVED |
| Route arc banner: destination picked | flat dotted horizon springs up into the dashed arc on `containerSpatial()` (the dots stretch into dashes as it lifts); the plane glides to its position on `dataSpatial()`; destination code reveals as above | OBSERVED |
| Header body sky on a day pick (a day strip pill, or a pick going live; the toolbar's day picker only scrolls the rail) | the new day's sky fades in over the old one on `colour()`, keyed by the picked day; the old sky stays put underneath, so the header never shows through, and the fade's alpha is read in the layer. Scrubbing and the minute tick repaint in place with no transition. Header text, icons and status-bar ink follow the sky that is mostly showing: they switch once, at the fade's midpoint | OBSERVED |

## Exceptions
- `CalmMotionScheme`, `StillMotionScheme`: `spring()`/`snap()` literals (they *are* token definitions).
- `ClockblockMotion.celebrationClock`, `ambientLoop`, `ambientOnce`: linear `tween()` used as a clock, not an easing.
- `ClockblockMotion.eggChain`, `navigationFadeIn/Out`: eased `tween()` token definitions.

## Frequency map
The plan screen is opened 5–15 times a day, but it then **stays on screen** for long stretches: continuous
on-screen time, not opens, is what gates its motion.

| Surface | Frequency | Motion allowed |
|---|---|---|
| Plan timeline, cards, chips, buttons | 100+/day interactions | Platform state layer + `containerSpatial` only |
| Plan screen at rest (glyphs, wave, sky, dial) | continuous on-screen time | Nothing loops; one ambient cycle on change, then still |
| Plan screen open | 5–15/day | Navigation transition only; no entrance choreography |
| New trip saved → its plan opens | a few/month | `CONFIRM` haptic on save + "first light" rise (the only plan entrance choreography) |
| Airport picked in the trip editor | a few/month | Dot-matrix reveal + route arc apex spring (`containerSpatial`); the plane stays on `dataSpatial` |
| Advice glyph morph | a few/day (advice boundaries) | `glyphMorph` (Expressive) + one ambient cycle |
| Dial scrub / back to now | daily | `dataSpatial`, hour haptics |
| Body-clock sky | continuous | Repaint only; no animation of its own (the header sky cross-fades once on a day pick, a state change the user asked for) |
| Illustrations (Why? sheet, onboarding, empty state) | rare | Ambient loops + entrances |
| Adaptation celebration (rings → Bloom + confetti) | once per trip | Full delight, one mover at a time |
| Rewind, moon, 24.2 and Konami easter eggs | very rare | Expressive (Calm at night) |

## Gestures and haptics
- Dial drag scrubs time: `CLOCK_TICK` per local hour crossed, `SEGMENT_TICK` per advice boundary.
- Dial centre tap → back to now (`dataSpatial`). Custom a11y actions: next / previous block / back to now.
- Long-press then a full counter-clockwise turn → Rewind (`LONG_PRESS` on arm, `CONFIRM` on fire).
- Rings aligning on adaptation → `CONFIRM` once the turn has landed (and the celebration follows).
- Moon egg taps 1–6 → `CLOCK_TICK`.
- Trip editor Save succeeds → `CONFIRM` (once, on the `Saved` event; validation failures stay silent).

## Reduced motion
- Trigger: system "Remove animations" (`ANIMATOR_DURATION_SCALE == 0`) or the in-app toggle → `LocalReduceMotion`.
- Spatial snaps (`StillMotionScheme`); colour/alpha still cross-fade.
- Every ambient loop stops at a complete still (`ArtCanvas` rest phase); entrances sit at their target.
- Wavy line keeps its amplitude (the meaning) and never travels.
- Loading indicator becomes a still Sunny with the same "Working out your plan" semantics.
- Confetti is skipped; `onFinished` fires immediately. The celebration goes straight to its overlay.
- Easter eggs are off (`easterEggs` is false under reduce motion and inside sleep windows).
- Dot-matrix codes, rolling readouts, the dial's body sky and offset pill and the route arc (apex and plane) snap to their
  end state; the still picture carries the whole meaning.
- Screenshot goldens render with reduce motion on, proving each static carrier. `_mid` goldens
  (`snapMidChange`) freeze one frame mid-transition with motion on.

## Not in this codebase
- Rolling digits on every minute tick: `RollingTimeText` exists, but clocks that stay on screen (the dial centre)
  don't roll each minute (frequency gate), and scrubbed readouts change every frame, so they never roll.
- Variable-weight pulse on the scrubbed readout (`wght 400 → 650`): skipped. Each weight is its own Typeface
  instance of the variable font, so animating it would create and re-measure typefaces mid-gesture on a daily
  surface, for no extra meaning (the hand and the cards already show the preview).
- Predictive-back shared element (plan → trip circle): owned by `:app` / `:feature:plan`.
- `wdth`-axis typographic motion (the bundled Google Sans Flex subset drops `wdth`).
