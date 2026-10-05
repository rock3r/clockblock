# Contributing to Opus Clockblock

Thanks for helping. Bug reports, science corrections, translations, accessibility fixes and design feedback are
all welcome. For anything bigger than a small fix, please open an issue first so we can agree on the approach.

## Setup

1. **JDK 21.** The build uses Gradle toolchains; point `JAVA_HOME` at a JDK 21 (Android Studio's bundled JBR
   works).
2. **Android SDK** with platform **37.1** (`compileSdk = release(37) { minorApiLevel = 1 }`) and current build
   tools. Install them from Android Studio's SDK Manager or with `sdkmanager`.
3. **`local.properties`** at the repo root (it's gitignored):

   ```properties
   sdk.dir=/path/to/Android/sdk
   ```

4. Build and run the tests:

   ```sh
   ./gradlew :app:assembleDebug
   ./gradlew test
   ```

5. For the end-to-end suite, start an emulator (API 37 image) and run
   `./gradlew :app:connectedDebugAndroidTest`.

There's no backend, no API key and no signing config to set up: the debug build is all you need.

## Project layout

See [docs/architecture.md](docs/architecture.md) for the module graph and how data flows. Rules of thumb:

- `:core:model` and `:core:circadian` are pure JVM. Keep Android out of them.
- Features depend on `:core:*` and talk to data through its interfaces. Avoid feature-to-feature dependencies
  (the one exception: `:feature:settings` reuses the profile editors from `:feature:onboarding`); the `:app`
  module wires everything together through the Metro graph.
- Versions live in `gradle/libs.versions.toml`; module setup lives in the `build-logic` convention plugins
  (`opus.jvm.library`, `opus.android.library`, `opus.android.compose`, `opus.android.feature`).
- The build uses AGP 9's **built-in Kotlin**: never apply `org.jetbrains.kotlin.android`.
- Never hardcode the application id; use `context.packageName` (or `BuildConfig.APPLICATION_ID` in `:app`).
- Store IANA zone ids, never UTC offsets. Use `java.time` everywhere.

[`AGENTS.md`](AGENTS.md) has the full list of conventions (it's written for coding agents, but it's the most
complete reference for humans too).

## Tests first

Every change comes with tests, written before the code.

| What you're changing | How to test it |
|---|---|
| Pure logic (planner, models, repositories) | JUnit 6 Jupiter (`org.junit.jupiter.api.Test`) + Kotest assertions; `kotest-property` property tests where an invariant exists |
| ViewModels | `MainDispatcherRule` from `:core:testing`, Turbine, and the fakes in `:core:testing` |
| Compose UI | JUnit 4 + Robolectric (`@RunWith(RobolectricTestRunner::class)`, `@GraphicsMode(NATIVE)`, `@Config(sdk = [36])`) |
| How it looks | Roborazzi `captureRoboImage()`: light, dark, font scale 1.5, compact and expanded widths |
| Whole flows | `app/src/androidTest` (Compose test + UiAutomator) on an emulator |

### Screenshot goldens

Goldens are committed in each module's `src/test/screenshots/`. After an intended visual change:

```sh
./gradlew :feature:plan:recordRoborazziDebug   # re-record (filter with --tests '*PlanScreenshotTest*')
./gradlew :feature:plan:verifyRoborazziDebug   # must pass before you open the PR
```

Look at every re-recorded PNG before committing it; the diff in the pull request is the review.

## Design and motion

Read [`docs/design.md`](docs/design.md) (Part 2: the visual identity) and [`MOTION.md`](MOTION.md) before touching
UI. The short version:

- **Motion tokens only.** No literal `tween()` or `spring()` outside the token definitions (`OpusMotion`,
  `MaterialTheme.motionScheme`).
- **Frequency gate.** Surfaces used 100+ times a day get only the platform state layer; delight is for rare
  moments.
- **Meaning survives "Remove animations".** Every animated state has a static carrier; self-timed loops read
  `MotionDurationScale`.
- **Frame-rate values are read in layout or draw** (lambda modifiers), never in composition.
- **No bouncy springs on data.** Times and timeline positions use the standard (non-expressive) specs.
- **Navigation transitions are always specified**: fade-through between top-level destinations,
  forward/backward within a hierarchy.
- **Labels, always.** Every advice card shows a text label, never an icon alone. Times show local time plus a
  secondary zone.
- **Strings** go in `res/values/strings.xml`, in the copy voice of design.md §2.6: short, precise, kind.

## Product rules

- It's not medical advice. Keep the disclaimers, and keep melatonin off by default behind its safety note.
- Stay offline: no network permission, no analytics, no accounts, no third-party SDKs that phone home.
- Don't use Timeshifter's trademarks or product terms in the UI or code.

## Updating the airport dataset

The offline airport and time zone search reads `core/data/src/main/assets/places.tsv`, generated from
OurAirports (public domain) and mwgg/Airports (MIT). To refresh it:

```sh
python3 tools/places/build_places.py --refresh   # needs Python 3.10+ and a JDK on PATH
./gradlew :core:data:testDebugUnitTest           # dataset tests
```

Then commit the new `places.tsv`. The pipeline, the format and the size budget are documented in
[`tools/places/README.md`](tools/places/README.md); attribution requirements are in
[`licenses/DATA_ATTRIBUTION.md`](licenses/DATA_ATTRIBUTION.md). Don't add OpenFlights data (AGPL/ODbL).

## Launcher icon

The adaptive icon's foreground and monochrome layers are generated:

```sh
python3 tools/icon/gen_launcher_icon.py app/src/main/res/drawable
./gradlew :app:recordRoborazziDebug --tests '*LauncherIconScreenshotTest*'
```

Edit the script, not the XML, and check `app/src/test/screenshots/launcher_icon*.png` afterwards.

## Pull requests

- Keep them focused, with a clear description and screenshots for UI changes.
- Before you push, run `./gradlew test :app:assembleDebug verifyRoborazziDebug`.
- On every pull request, the CI workflow validates the Gradle wrapper, runs the unit tests, verifies the
  screenshots, assembles the APKs and tests the babysit-pr watcher. A separate e2e workflow runs the emulator
  tests. Codex reviews the change. [docs/testing.md](docs/testing.md#pull-requests-and-review) describes the
  checks and the review loop.
- By contributing you agree that your contribution is licensed under the [Apache License 2.0](LICENSE).
