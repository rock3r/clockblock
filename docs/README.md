# Documentation

This folder is for people who want to understand, change or check Opus Clockblock. If you just want to use the
app, read the [user guide](../user-guide/README.md) instead.

## Start here

| Document | Read it to learn |
|---|---|
| [architecture.md](architecture.md) | How the modules fit together, how data moves from storage to the screen, dependency injection, navigation and the time-zone rules |
| [planning-engine.md](planning-engine.md) | How a trip becomes a plan, which stages run, and when a plan is recomputed |
| [surfaces.md](surfaces.md) | How reminders, the ongoing Now notification, the travel-day Live Update and the widgets stay in step with the plan |
| [testing.md](testing.md) | The test layers and how to run them, the CI jobs, and how pull requests are reviewed |

## The science

| Document | What it is |
|---|---|
| [algorithm.md](algorithm.md) | The exact rules the planner follows: inputs, modes, every parameter, the validation and the known limitations |
| [science.md](science.md) | The research review behind the planner: light and melatonin phase-response curves, caffeine, chronotype and the two mathematical models, with references |
| [research/reference/](research/reference/) | The Python reference code that produced the numbers in science.md and the golden outputs for the Kotlin tests |

## Design and motion

| Document | What it is |
|---|---|
| [design.md](design.md) | Product research and the visual identity: colour, type, shapes, illustrations and copy voice. Written before the app was built, so some technical plans in it changed. |
| [../MOTION.md](../MOTION.md) | The motion language. Entries are marked `OBSERVED` until a human ratifies them. |
| [motion-review-1.md](motion-review-1.md) | The first motion review of the app and its findings |

## Records and research

| Document | What it is |
|---|---|
| [qa/device-qa-1.md](qa/device-qa-1.md) | The first exploratory QA pass on a physical device |
| [research/android_tech.md](research/android_tech.md) | The Android library and platform research done before building (versions, Remote Compose, Live Updates) |
| [../tools/places/README.md](../tools/places/README.md) | How the offline airport and time-zone dataset is built |
| [../licenses/DATA_ATTRIBUTION.md](../licenses/DATA_ATTRIBUTION.md) | Attribution for the bundled airport data |

## Screenshots

[screenshots/](screenshots/) holds the images used by the README and these pages. Most are copies of the
Roborazzi goldens in each module's `src/test/screenshots/` folder. The ones in `screenshots/device/` and
`screenshots/widgets-on-device.png` are captures of the running app on an Android emulator.
