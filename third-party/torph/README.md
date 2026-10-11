# Torph for Jetpack Compose (vendored)

Morphing text for Jetpack Compose, by Chris Sinco: <https://github.com/c5inco/torph-compose>, an unofficial port
of [torph](https://github.com/lochie/torph) by Lochie Axon. MIT licensed: see [LICENSE](LICENSE) and
[NOTICE](NOTICE).

Clockblock uses it for the sleep dial's bedtime and wake times (`:feature:onboarding`).

## Why it's vendored

Torph has no Maven release, and CI must not rely on `mavenLocal()` or an included build. So its source lives here,
unchanged, in its own packages (`des.c5inco.torph.core` and `des.c5inco.torph.compose`), built as one small module.

- Upstream commit: `de406f7b2893b7962eacedc95f3347bd6efda9d6` (2026-10-07).
- Copied: `torph-core/src/main` and `torph-compose/src/main` (Kotlin only). Not copied: the demo app, the
  benchmarks, the tests and the baseline profile.
- Changes: none.

## Updating

Copy the two `src/main/kotlin` trees from a newer upstream commit over `src/main/kotlin`, update the commit above,
and check `LICENSE` and `NOTICE` for changes.
