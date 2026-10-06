# AGENTS.md — Clockblock

Open-source, fully offline jet lag app for Android (a Timeshifter alternative). Material 3 Expressive, Remote
Compose widgets, science-based planning. No backend, no accounts, no analytics.

Application id: **`dev.sebastiano.clockblocker.opus`**. Never hardcode it in code — use `context.packageName`
(or `BuildConfig.APPLICATION_ID` in `:app`).

## Build & test

Agents: always put `build-brief` in front of Gradle (see user rules). This rule covers commands that agents run.
Human-facing docs (README, `docs/`, `user-guide/`, tool READMEs) show plain `./gradlew`, because contributors may
not have `build-brief` installed.

| What | Command |
|---|---|
| Debug APK | `build-brief ./gradlew :app:assembleDebug` |
| All unit tests (JVM + Robolectric) | `build-brief ./gradlew test` (Android modules: `testDebugUnitTest`) |
| One module | `build-brief ./gradlew :core:circadian:test` |
| Record screenshots | `build-brief ./gradlew <module>:recordRoborazziDebug` |
| Verify screenshots | `build-brief ./gradlew <module>:verifyRoborazziDebug` |
| e2e on emulator | `build-brief ./gradlew :app:connectedDebugAndroidTest` (AVD `Opus_API37`) |

`local.properties` must contain `sdk.dir=<path to your Android SDK>` (gitignored).

## Pull requests & CI

- All changes land on `main` through pull requests. CI (`.github/workflows/ci.yml`) runs wrapper validation, unit
  tests, Roborazzi verification, APK assembly and the babysit watcher tests on every PR; emulator e2e runs in its
  own workflow (`.github/workflows/e2e.yml`) so the watcher can retry emulator flakes.
- Codex reviews PRs. After opening a PR, babysit it with the vendored skill in `.agents/skills/babysit-pr/`
  (read its `SKILL.md`; project settings in `config.json`) until it is ready or needs the owner.
- Run the local gate from that config before every push.
- Update the skill only with its `sync.py` (see `.agents/skills/babysit-pr/VERSION` for the source); never edit it here.

## Stack (do not change without reason)

- AGP 9.4.1 with **built-in Kotlin**: never apply `org.jetbrains.kotlin.android`. Kotlin 2.4.20, JDK 21.
- compileSdk 37.1 (`compileSdk { version = release(37) { minorApiLevel = 1 } }`), targetSdk 37, minSdk 29.
- Compose **alpha BOM** (material3 1.5.0-alpha29) for public M3 Expressive APIs.
- Navigation 3, Metro DI (`@Inject`, `@ContributesBinding(AppScope::class)`, `@SingleIn(AppScope::class)`,
  ViewModels via `@ViewModelKey @ContributesIntoMap(AppScope::class)` and `metroViewModel()` /
  `assistedMetroViewModel()` from `dev.zacsweers.metrox.viewmodel`).
- DataStore + kotlinx-serialization for persistence. `java.time` everywhere (store IANA zone ids, never offsets).
- Versions live in `gradle/libs.versions.toml`; module setup lives in `build-logic` convention plugins
  (`clockblock.jvm.library`, `clockblock.android.library`, `clockblock.android.compose`, `clockblock.android.feature`).

## Modules

| Module | Kind | Purpose |
|---|---|---|
| `:core:model` | JVM | Domain types (`Trip`, `UserProfile`, `JetLagPlan`, `Advice`…) |
| `:core:circadian` | JVM | Jet lag planner + Forger99/Hannay19 ODE validator. No Android. |
| `:core:data` | Android | Repositories (DataStore), offline airport search, plan cache |
| `:core:designsystem` | Android+Compose | Theme, type, colour, motion tokens, shapes, illustrations, dial |
| `:core:notifications` | Android | Exact-alarm scheduler, Now notification / Live Update |
| `:core:testing` | Android | Test rules, fakes, screenshot helpers |
| `:feature:*` | Android+Compose | onboarding, trips, plan, settings |
| `:widget` | Android+Compose | Remote Compose widgets (+ RemoteViews fallback) |
| `:app` | Application | Nav3 shell, adaptive layouts, Metro graph |

## Testing conventions (TDD)

- Write the failing test first. Pure logic → JUnit 6 Jupiter (`org.junit.jupiter.api.Test`) + Kotest
  assertions (`io.kotest.matchers.*`), property tests with `kotest-property` where invariants exist.
- Android/Compose tests → JUnit 4 + Robolectric (`@RunWith(RobolectricTestRunner::class)`,
  `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `@Config(sdk = [36])`), run on the JUnit Platform via Vintage.
- Screenshots → Roborazzi `captureRoboImage()`; goldens are committed in `<module>/src/test/screenshots`.
- ViewModels → `MainDispatcherRule` from `:core:testing` + Turbine.
- e2e → `app/src/androidTest` (Compose test + UiAutomator 2.4) on the emulator.

## Motion & design rules

Read `MOTION.md` (motion language) and `docs/design.md` (visual identity) before touching UI. Key rules from
[rock3r/android-ux-skills](https://github.com/rock3r/android-ux-skills):
- No literal `tween()`/`spring()` outside token definitions (`ClockblockMotion` / `MaterialTheme.motionScheme`).
- Frequency gate: 100+/day surfaces get only the platform state layer; delight only for rare moments.
- Meaning must survive "Remove animations" (static carrier). Self-timed loops read `MotionDurationScale`.
- Frame-rate values are read in layout/draw (lambda modifiers), never composition.
- NavDisplay transitions are always specified (never the 700 ms default). Top-level = fade-through,
  hierarchy = forward/backward.
- Expressive bouncy spatial springs stay off elements read as data (times, timeline positions).

## Product rules
- Not medical advice. Melatonin off by default, opt-in behind a safety note.
- Avoid Timeshifter trademarks ("Timeshift", "Practicality Filter", "Quick Turnaround", "Circadian Time").
- Every advice card shows a label, never an icon alone. Times show local time plus a secondary zone.

## Cross-module contracts
- `PlanSurface` (`:core:data`): widgets + Now notification implement it and contribute into a Metro set;
  the scheduler in `:core:notifications` refreshes all of them at advice boundaries and on plan changes.
- `JetLagPlanner` (`:core:circadian`): the app binds `DefaultJetLagPlanner`; data depends on the interface only.
- Deep links (`DeepLinks` in `:core:model`): `clockblock://trips`, `…/trips/new`, `…/plan/{tripId}`,
  `…/plan/current`.
