# Licences the build can't see

The licences screen (Settings → About Clockblock → Licences) lists every library the app is built with:
the [AboutLibraries](https://github.com/mikepenz/AboutLibraries) Gradle plugin reads them from the dependency
graph at build time. This folder adds what isn't in that graph:

- `libraries/`: one JSON file per entry. Vendored code (`tag` `vendored`), fonts (`font`), icons (`icons`) and data
  sources (`data`), plus the app itself (`tag` `clockblock`, always listed first).
- `licenses/`: licence texts no dependency brings (the SIL Open Font License, public domain). MIT and Apache 2.0
  come from the plugin (`additionalLicenses` in `app/build.gradle.kts`).

To credit something new (say, a ported component or a data set), copy a file in `libraries/` and edit it:

```json
{
  "uniqueId": "data:natural-earth",
  "name": "Natural Earth",
  "description": "What it's used for in Clockblock.",
  "website": "https://www.naturalearthdata.com",
  "developers": [{ "name": "Natural Earth contributors" }],
  "licenses": ["PublicDomain"],
  "tag": "data"
}
```

`licenses` takes an SPDX id the plugin knows (`MIT`, `Apache-2.0`) or the `hash` of a file in `licenses/`. Also add
the entry to the repository's `NOTICE`, and its `uniqueId` to `LicencesDataTest` in `app/src/test`.
