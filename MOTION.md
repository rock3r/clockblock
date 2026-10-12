# MOTION.md — Clockblock

> Agent-drafted from the code in `:core:designsystem`, then reviewed: the owner approved every spec as `DECIDED`
> (policy) on 2026-10-10. New specs added by agents are tagged `OBSERVED` until a human reviews them.

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
| Advice glyph Circle → MaterialShape when it becomes "now" | `glyphMorph()` = scheme fastSpatial | DECIDED |
| Active glyph ambient (VerySunny step, melatonin twinkle, fatigue pulse) | one `ambientOnce(period)` cycle when it becomes active, then rest (saveable "played"); never loops | DECIDED |
| Glyph rotation / heading change, chevrons | `dataSpatial()` | DECIDED |
| Glyph fill colour | `colour()` = scheme defaultEffects | DECIDED |
| Card → Now card, containers around data | `containerSpatial()` = scheme defaultSpatial | DECIDED |
| Small in-place swaps | `fade()` = scheme fastEffects | DECIDED |
| Dial scrub settle / "back to now" | `dataSpatial()` (Standard); every frame reported so cards follow the hand | DECIDED |
| Dial body sky (inner ring) on day change | `dialDayRotation()` = Standard slowSpatial, no overshoot; its ring labels turn with it | DECIDED |
| Rewind easter egg springing home | `rewindReturn()`: raw −1440 → 0 with the release velocity; Expressive slowSpatial by day, Calm slowSpatial (no bounce) under Calm | DECIDED |
| Night-safe / light–dark / Opus-mode switch | `themeCrossFade()` = Calm slowEffects palette cross-fade inside `ClockblockTheme` (stable call shape via `NightSafeTheme`) | DECIDED |
| Illustration entrance / `progress` | `artEntrance()` = scheme slowSpatial, once per instance (saveable); bouncy arts use `glyphMorph()` | DECIDED |
| Two Clocks discs sliding on adaptation | `containerSpatial()` via `rememberArtValue` | DECIDED |
| Illustration ambient loops (rare surfaces only) | `ambientLoop(ClockblockMotion.*Millis)` (sun 9 s, twinkle 1.8 s, drift 7.2 s, steam 3.2 s, sway 4.2 s, pulse 2.4 s); ×1.5 under Calm | DECIDED |
| Confetti flight | `celebrationClock()` (2.2 s linear clock; physics from t), one burst from the Bloom's centre | DECIDED |
| Celebration sequence | navigation settles (450 ms) → rings turn into alignment on `dialDayRotation()` + `CONFIRM` → overlay fades in on `colour()` → Bloom scales on `glyphMorph()` → check + confetti together; predictive back seeks the overlay fade | DECIDED |
| Wavy adaptation line | still by default (`travel = false`); travel is opt-in for rare surfaces | DECIDED |
| Plan loading | nothing for 150 ms (fast loads never flash a loader), then M3 `LoadingIndicator`; ready state cross-fades in (`colour()` / `fade()`) only if the loader was shown | DECIDED |
| Plan floating toolbar show/hide | one owner: `AnimatedVisibility` fade (`fade()`) + a slide by the toolbar's whole slot (its shadow above, the bottom insets below) on `navigationSpatial()`, so it leaves the screen completely; no M3 exit-always on top. Shown once the Now card has scrolled away (always in two panes); steps aside after 48 dp of the user's own scrolling on through the plan and returns after 48 dp back, or at the end of the list, where the count starts afresh so the way back never hides it (`ToolbarScrollState`; the toolbar's own jumps never hide it). Its surface-gradient scrim lives inside the same `AnimatedVisibility` and only fades | OBSERVED |
| "First light" (plan of a trip saved ≤ 90 s ago) | header sun/moon rises from below the header's bottom edge to its resting place with an alpha ramp, `artEntrance()` (scheme slowSpatial), once per plan entry (saveable); clipped to the header. Reduce motion: already in place (static carrier; the rise carries no meaning) | DECIDED |
| Header sky ambient session (stars breathing, a plane crossing) | rare, slow and draw-phase only: at most one 45 s session every 12 h, persisted across launches (`AmbientSkyStore`), and only once the header has been in view for 6 s (a glance doesn't spend it); no repeat on a timer. A session waits (clock and frame loop stopped) while the header is collapsed past half way, and never runs on short windows, where the header stays collapsed. In a session a third of the stars (only at body night, as they come out through dusk) each breathe once every ~7 s, dimming to 15 % and back, eased in and out over the session's first and last 3 s; and a small plane climbs across the upper sky in 18 s with a contrail streaming behind it that fades over 9 s once it has gone (by night only its blinking light). A self-timed clock (`withFrameMillis`, every wait and the clock scaled by `MotionDurationScale`; scale 0 = no session ever); `HeaderAmbient.sessionMillis` is read only in draw, so a session never recomposes. Fades with the collapsing header. Off under Reduce motion and in sleep windows (the easter-egg gate) and in Night-safe. Static carrier: the sky, its stars at their resting brightness, the sun or moon and the words; the session adds no meaning | OBSERVED |
| Plan header collapse | the height follows the exit-until-collapsed offset, read in layout (scrolling never recomposes); the expanded block (codes, city line, where the plan is, the next action) fades out over the first half and the navigation row's trip name fades in over the second (read in the layer); the sky's melt into the page fades with it | OBSERVED |
| Header moon egg (7 taps) | `CLOCK_TICK` on taps 1–6; crescent waxes to full, then Circle → Cookie12 → Clover8 → Ghostish → Heart → Cookie9 → bite as one continuous `eggChain(steps)` progress | DECIDED |
| Sleep dial 24.2 egg | wake handle pushed past 24 h peeks "24:12" + Czeisler line, then rubber-bands back; inline note under the phone's Remove animations; off with the in-app Reduce motion and while the plan says sleep | DECIDED |
| Konami 8-bit egg (↑↑↓↓←→←→ swipes on the plan rail) | glyphs morph to `PixelCircle` / `PixelTriangle` on `glyphMorph()` and straighten; sky quantises to 6 bands; times monospace; session-only, snackbar Exit; off under reduce motion and sleep windows | DECIDED |
| Suitcase clock ticks | 3 discrete 1 s steps (`delay` × `MotionDurationScale`), then rest | DECIDED |
| Top-level destination switch (fade-through) | `navigationFadeOut()` 90 ms EmphasizedAccelerate → `navigationFadeIn()` 210 ms (+90 ms delay) EmphasizedDecelerate + scale-in 0.92 via `navigationSpatial()`; ×1.5 under Calm | DECIDED |
| Hierarchy push/pop (shared axis X, 30 dp, RTL-mirrored) | `navigationSpatial()` = Standard defaultSpatial (no bounce; snaps on reduce) + fades | DECIDED |
| Predictive back (seekable) | exit scale 0.9 + slide away from swipe edge on `navigationSpatial()`, fade on `colour()` | DECIDED |
| List-detail pane enter/exit, pane bounds | `navigationFadeIn()` / `navigationFadeOut()`; bounds `navigationSpatial()` | DECIDED |
| Dot-matrix airport code picked (`IataCode`, `· · ·` → `SFO`) | dots turn to the new characters left to right, cell by cell and column by column, on `colour()` (~180 ms); first composition sits at rest | DECIDED |
| Rolling readouts (`RollingText` / `RollingTimeText` / `RollingMetricText`) | only the changed run of characters rolls vertically, on `dataSpatial()` (no bounce: the readout is data), with a cross-fade from the same progress; up when the value grows, down when it shrinks; units and unchanged digits stay still; not used for per-frame values (scrubbing) | DECIDED |
| Dial offset pill ("7 h behind") and body time on a day change | read the body sky's own animated offset in the draw phase, so the words step through the half hours exactly as the ring turns (one event, never two that disagree) | DECIDED |
| Sleep dial readouts (bedtime / wake pills, centre duration) | follow a drag live with no roll (per-frame values); roll on discrete changes (TalkBack or keyboard nudge, a picked time, the 24.2 rubber band) as rolling readouts; text sized for the widest value so it never resizes | DECIDED |
| Tool row switched on (onboarding / settings tools) | leading advice glyph Circle → advice shape on `glyphMorph()` (rare: a setup choice), back on `dataSpatial()` (the melatonin glyph twinkles once, as on the plan); row container and text tint to the advice container on `colour()`, read in draw; the switch and label carry the state without them | DECIDED |
| Route arc banner: destination picked | flat dotted horizon springs up into the dashed arc on `containerSpatial()` (the dots stretch into dashes as it lifts); the plane glides to its position on `dataSpatial()`; destination code reveals as above | DECIDED |
| Day strip following the two-pane rail (scrolled by hand) | the pill of the day the rail shows gets a short bar at its foot, swapped with no animation (rail scrolling is a 100+/day gesture; the day is read through a derived state, so the strip only recomposes at day boundaries); the strip scrolls only when that pill is out of sight, on `animateScrollToItem` (a jump under reduce motion). The bar and TalkBack's "In view on your plan" carry it; it never picks the day | DECIDED |
| Header body sky on a day pick (a day strip pill, or a pick going live; the toolbar's day picker only scrolls the rail) | the new day's sky fades in over the old one on `colour()`, keyed by the picked day; the old sky stays put underneath, so the header never shows through, and the fade's alpha is read in the layer. Scrubbing and the minute tick repaint in place with no transition. Header text, icons, status-bar ink and the sun / moon follow the sky that is mostly showing: they switch once, at the fade's midpoint. A pick during a fade freezes the blend on screen and fades the new day in over it (no jump to the interrupted target) | DECIDED |
| Two-pane rail following a dial scrub | the rail row under the dial's hand is highlighted (as before) and, when it is fully or partly off screen, the rail scrolls it into view on `dataSpatial()` (it is a time position: no bounce); a jump under reduce motion. Keyed by the row, so it moves once per block crossed, never per frame of the scrub; a row already in view (below the pinned day header) never moves the rail; a scrub into a day folded behind "earlier days" unfolds it first; never starts over a drag or fling of the user's own, and a grab mid-scroll takes the rail over. One pane: no scroll (the dial would scroll away) | DECIDED |
| Light response curve sun (Why sheet of light advice, About) | follows a drag live (position read in draw; no recomposition per frame); a tap, TalkBack's Earlier / Later hour and Reset settle on `dataSpatial()` (where the sun sits is data); while held the sun grows 1.2× on `containerSpatial()` (a rare surface, so the Expressive container spring is allowed). The curve, lobes and band never move. Reduce motion: everything snaps; the readout text and the still sun carry the meaning | DECIDED |
| Sleep dial: Work days ⇄ Days off (centre toggle or a tap on the outer ring) | the two schedule rings trade radius and thickness on `containerSpatial()` (Expressive by day: a rare setup surface), read in draw; the arcs keep their angles (times are data, nothing turns), the edited ring's handles scale in with it, the outer ring's arc dims to 50 %. Toggle tabs tint on `colour()`. Reduce motion: the geometry snaps and the old picture cross-fades into the new one on `colour()` | OBSERVED |
| Sleep dial handle held | grows 1.1× on `containerSpatial()` (stays clear of the outer ring); back on release | OBSERVED |
| Sleep dial bedtime / wake pills | torph morphing text (vendored `:third-party:torph`): changed digits roll by place value on a spring built from `dataSpatial()`'s stiffness and damping (no bounce), including on a schedule switch; live with no morph while dragging; snaps under reduce motion (app or system) | OBSERVED |
| Onboarding Sleep → chronotype reveal | step transition: the dial fades out (`navigationFadeOut()`), the reveal fades in (`navigationFadeIn()`) and grows from 92 % on `containerSpatial()`; then, once per visit (saveable): the sky warms from three hours earlier and the sun (or moon) rises along its path on `artEntrance()`, and at 70 % the words fade in on `colour()` while rising 24 dp on `containerSpatial()` (one mover at a time). Rays turn (`ambientLoop(SunRotationMillis)`) and stars twinkle (`ambientLoop(TwinkleMillis)`) on the rare reveal only. A chronotype picked in the sheet moves the sun along the path on `artEntrance()`, sun ⇄ moon cross-fading on `colour()`. Reduce motion: all at rest, the words carry the meaning | OBSERVED |
| Onboarding Reminders card | the permission slot swaps Allow → Allowed (or Off after a refusal) with `fade()` in one fixed slot; the Exact timing row grows in below on `containerSpatial()` (expand + fade, one tier) | OBSERVED |
| Trip editor ticket: route arc between the codes | the same `RouteArc` as the banner (apex on `containerSpatial()`, plane on `dataSpatial()`, snaps under reduce motion), drawn between the ticket's own code buttons | OBSERVED |
| Trip editor Return flight switch | the return date row grows in (and out) on `containerSpatial()` with a `fade()`; reduce motion: it appears at once, and the switch carries the state | OBSERVED |
| Trip editor airport search sheet | the platform `ModalBottomSheet` motion only; picking From turns the open sheet into To in place (no motion of its own) | OBSERVED |
| Trip editor main action ("Build my plan") | the platform state layer and the Expressive button shape morph on press (`ButtonDefaults.shapes()`); nothing else moves | OBSERVED |
| Route globe turning to a ticket (editor card, full-size sheet, world view) | the view centre (latitude, longitude) turns on `dataSpatial()` the short way round (the route's place on Earth is data: no bounce); snaps under reduce motion. The globe never spins or drifts on its own: no self-timed motion, so nothing reads `MotionDurationScale`. The brighter active arc swaps with no animation | OBSERVED |
| Editor globe full size | the platform `ModalBottomSheet` motion (and its predictive back) only; tapping a flight closes the sheet and scrolls its ticket into view (`bringIntoView`) | OBSERVED |
| Plan dial ↔ world (pinch out / pinch in, Back, TalkBack Show the world / Back to the dial) | the dial and the globe cross-fade in place on `fade()`, the dial's alpha read in its layer; nothing scales or moves. Reduce motion keeps the cross-fade (effects are still allowed); the globe is the static carrier | OBSERVED |

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
| Body-clock sky | continuous | Repaint only; no animation of its own (the header sky cross-fades once on a day pick, a state change the user asked for). Its ambient session (stars, a plane) runs at most once every 12 h (persisted), after 6 s in view, off under Reduce motion, in sleep windows, in Night-safe and on short windows |
| Header sun path + ghost ring (issue #21) | continuous | Static: repaints with the minute tick and a scrub, drawn in the draw phase, fades with the collapsing header; nothing animates, so Remove animations changes nothing |
| Illustrations (Why? sheet, onboarding, empty state) | rare | Ambient loops + entrances |
| Light response curve (Why sheet, About) | rare | Handle grows on `containerSpatial`; position on `dataSpatial`; hour haptics |
| Route globe (trip editor) / world behind the dial (pinch out) | a few/month / rare | Turn on `dataSpatial`, cross-fade on `fade()`; never spins on its own |
| Adaptation celebration (rings → Bloom + confetti) | once per trip | Full delight, one mover at a time |
| Rewind, moon, 24.2 and Konami easter eggs | very rare | Expressive (Calm at night) |

## Gestures and haptics
- Dial drag scrubs time: `CLOCK_TICK` per local hour crossed, `SEGMENT_TICK` per advice boundary.
- Dial centre tap → back to now (`dataSpatial`). Custom a11y actions: next / previous block / back to now.
- Two-finger pinch out on the dial → the world (pinch in, or Back, returns). The pinch takes the gesture only once a
  second finger is down, so one-finger scrub, the Rewind long-press and the moon taps never see it. No haptic. Custom
  a11y actions: Show the world (dial), Back to the dial (globe).
- Long-press then a full counter-clockwise turn → Rewind (`LONG_PRESS` on arm, `CONFIRM` on fire).
- Rings aligning on adaptation → `CONFIRM` once the turn has landed (and the celebration follows).
- Moon egg taps 1–6 → `CLOCK_TICK`.
- Light response curve drag → `CLOCK_TICK` per hour crossed, `SEGMENT_TICK` at the body's coldest point. Custom a11y
  actions: Earlier hour / Later hour / Reset ("Reset to this block" in the Why sheet).
- Trip editor Save succeeds → `CONFIRM` (once, on the `Saved` event; validation failures stay silent).
- Rail row check-off circle ticked → `CONFIRM` (like the Now card's Done; unticking is silent). The circle just swaps
  between outlined and filled with a check: the rail is a 100+/day surface, so the state layer is its only motion.

## Reduced motion
- Trigger: system "Remove animations" (`ANIMATOR_DURATION_SCALE == 0`) or the in-app toggle → `LocalReduceMotion`.
- Spatial snaps (`StillMotionScheme`); colour/alpha still cross-fade.
- Every ambient loop stops at a complete still (`ArtCanvas` rest phase); entrances sit at their target.
- The plan header's ambient session (stars breathing, the plane) never starts; the stars rest at their brightness.
- Wavy line keeps its amplitude (the meaning) and never travels.
- Loading indicator becomes a still Sunny with the same "Working out your plan" semantics.
- Confetti is skipped; `onFinished` fires immediately. The celebration goes straight to its overlay.
- Easter eggs are off (`easterEggs` is false under reduce motion and inside sleep windows).
- Dot-matrix codes, rolling readouts, the dial's body sky and offset pill, the route arc (apex and plane) and the route
  globe's turn snap to their end state; the still picture carries the whole meaning. The world behind the dial is not
  an easter egg: it works under reduce motion and in sleep windows.
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
