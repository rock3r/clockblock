# Contributing to Clockblock

Contributions can include bug reports, science corrections, translations, accessibility fixes and design feedback.
Before starting a larger change, open an issue to agree on the approach with the maintainer.

## Setup

1. Set `JAVA_HOME` to JDK 21. Gradle uses toolchains; Android Studio's bundled JetBrains Runtime (JBR) also works.
2. Install Android SDK platform **37.1** (`compileSdk = release(37) { minorApiLevel = 1 }`) and current build tools.
   Use Android Studio's SDK Manager or `sdkmanager`.
3. Create the gitignored **`local.properties`** file at the repository root:

   ```properties
   sdk.dir=/path/to/Android/sdk
   ```

4. Build and run the tests:

   ```sh
   ./gradlew :app:assemblePlayDebug
   ./gradlew test
   ```

5. For the end-to-end suite, start an emulator with an Android 17 (API 37) image and run
   `./gradlew :app:connectedPlayDebugAndroidTest`.

The app has two flavours: the default `play` build for Google Play and `oss` for other distribution with `USE_EXACT_ALARM`.
Library modules have no flavours.
In `:app`, `testDebugUnitTest`, `recordRoborazziDebug` and `verifyRoborazziDebug` alias the play tasks.
Use the play task name when filtering with `--tests`; aliases reject that option.

The debug build needs no backend, API key or signing configuration.

## Project layout

The [architecture guide](docs/architecture.md) describes module dependencies and data flow. Follow these module rules:

- `:core:model` and `:core:circadian` are pure JVM. Keep Android out of them.
- Features depend on `:core:*` and use data interfaces.
  Only `:feature:settings` can depend on another feature: it reuses `:feature:onboarding` profile editors.
  The `:app` module connects implementations through Metro's dependency graph.
- Versions live in `gradle/libs.versions.toml`; module setup lives in the `build-logic` convention plugins
  (`clockblock.jvm.library`, `clockblock.android.library`, `clockblock.android.compose`, `clockblock.android.feature`).
- The build uses AGP 9's **built-in Kotlin**: never apply `org.jetbrains.kotlin.android`.
- Never hardcode the application id; use `context.packageName` (or `BuildConfig.APPLICATION_ID` in `:app`).
- Store IANA zone ids, never UTC offsets. Use `java.time` everywhere.

[`AGENTS.md`](AGENTS.md) lists the repository's coding conventions for contributors and coding agents.

## Tests first

Write tests before implementing behaviour changes.

| What you're changing | How to test it |
|---|---|
| Pure logic (planner, models, repositories) | JUnit 6 Jupiter (`org.junit.jupiter.api.Test`) + Kotest assertions; `kotest-property` property tests where an invariant exists |
| ViewModels | `MainDispatcherRule` from `:core:testing`, Turbine, and the fakes in `:core:testing` |
| Compose UI | JUnit 4 + Robolectric (`@RunWith(RobolectricTestRunner::class)`, `@GraphicsMode(NATIVE)`, `@Config(sdk = [37])`) |
| How it looks | Roborazzi via `compose.captureRoboImageInvalidated()`: light, dark, font scale 1.5, compact and expanded widths |
| Whole flows | `app/src/androidTest` (Compose test + UiAutomator) on an emulator |

### Screenshot goldens

Goldens are committed in each module's `src/test/screenshots/`. After an intended visual change:

```sh
./gradlew :feature:plan:recordRoborazziDebug   # re-record (filter with --tests '*PlanScreenshotTest*')
./gradlew :feature:plan:verifyRoborazziDebug   # must pass before you open the PR
```

Inspect each new PNG before committing it. Reviewers use the pull request's image diff to check visual changes.

## Design and motion

Before changing UI, read Part 2 of [`docs/design.md`](docs/design.md) and [`MOTION.md`](MOTION.md).
The following rules apply to UI changes:

- Use motion tokens. Keep literal `tween()` and `spring()` calls inside `ClockblockMotion` or `MaterialTheme.motionScheme` token definitions.
- Limit surfaces used 100+ times daily to the platform state layer. Reserve decorative motion for rare events.
- Preserve each animated state's meaning in a still view. Self-timed loops must read `MotionDurationScale`.
- Read frame-rate values in layout or draw through lambda modifiers. Keep those reads out of composition.
- Use standard, non-expressive specifications for times and timeline positions to prevent overshoot.
- Specify navigation transitions: fade-through between top-level destinations and forward/backward transitions within a hierarchy.
- Give every advice card a text label. Show local times with a secondary zone.
- Put strings in `res/values/strings.xml`. Follow design.md §2.6 for plain, precise wording.

## Product rules

- Keep the medical disclaimers. Melatonin must stay off by default and require the safety note before opt-in.
- Keep the app offline. Do not add network permission, analytics, accounts or third-party SDKs that send data.
- Exclude Timeshifter's trademarks and product terms from UI and code.

## Updating the airport dataset

The offline airport and time zone search reads `core/data/src/main/assets/places.tsv`, generated from
OurAirports (public domain) and mwgg/Airports (MIT). To refresh it:

```sh
python3 tools/places/build_places.py --refresh   # needs Python 3.10+ and a JDK on PATH
./gradlew :core:data:testDebugUnitTest           # dataset tests
```

Commit the updated `places.tsv`.
[`tools/places/README.md`](tools/places/README.md) defines the generation steps, format and size budget.
Follow [`licenses/DATA_ATTRIBUTION.md`](licenses/DATA_ATTRIBUTION.md) for attribution.
Do not add OpenFlights data, which uses AGPL/ODbL licences.

## App icon

One script generates the adaptive icon's background, foreground and monochrome layers, plus the small icon used by every notification.

```sh
python3 tools/icon/gen_icon.py
./gradlew :app:recordRoborazziPlayDebug --tests '*LauncherIconScreenshotTest*' --tests '*IconFamilyScreenshotTest*'
```

Change icon geometry in the script. Keep generated XML unchanged by hand.
The script rejects art outside the 66 dp safe zone or the small icon's live area.
After generation, inspect `app/src/test/screenshots/launcher_icon*.png` and `app/src/test/screenshots/icon_family.png`.

## Pull requests

- Keep them focused, with a clear description and screenshots for UI changes.
- Before you push, run `./gradlew test :app:assemblePlayDebug :app:assembleOssDebug verifyRoborazziDebug`.
- On every pull request, the CI workflow validates the Gradle wrapper, runs the unit tests, verifies the
  screenshots, assembles the APKs and tests the babysit-pr watcher. A separate e2e workflow runs the emulator
  tests. Codex reviews the change. [docs/testing.md](docs/testing.md#pull-requests-and-review) describes the
  checks and the review loop.
- By contributing you agree that your contribution is licensed under the [Apache License 2.0](LICENSE).
