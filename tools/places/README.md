# tools/places — offline airport + time zone dataset

Builds `core/data/src/main/assets/places.tsv`, the bundled dataset behind `PlaceSearch` (manual trip entry
works fully offline: airport/city search → IANA time zone).

## Run

```sh
python3 tools/places/build_places.py            # downloads sources to $TMPDIR/opus-places, writes the asset
python3 tools/places/build_places.py --refresh  # force re-download
python3 tools/places/build_places.py --cache /path/to/cache --out /tmp/places.tsv
```

Requirements: Python 3.10+ (stdlib only) and a JDK on `PATH` (`java`), used to run `ZoneIds.java` so every
zone is validated against exactly what `java.time.ZoneId.of()` accepts. Without a JDK the script falls back
to Python's `zoneinfo` and prints a warning.

Then run the dataset tests and commit the asset:

```sh
./gradlew :core:data:testDebugUnitTest
```

## Sources (see `licenses/DATA_ATTRIBUTION.md`)

| Source | Used for | License |
|---|---|---|
| [OurAirports](https://ourairports.com/data/) `airports.csv` | IATA codes, names, municipality, country, coordinates, type, scheduled service | Public Domain |
| [mwgg/Airports](https://github.com/mwgg/Airports) `airports.json` | IANA time zone per airport | MIT |

OpenFlights is deliberately **not** used (AGPL-3.0 code, ODbL data).

## Pipeline

1. Keep OurAirports rows with a 3-letter IATA code, of type large/medium/small airport or seaplane base, with
   `scheduled_service=yes` (large airports are kept even without the flag: temporarily closed, newly opened).
   Heliports and closed fields are dropped. One row per IATA code.
2. Join mwgg on IATA (disambiguated by ICAO, then by distance < 100 km), then on ICAO.
3. Airports still without a zone take the zone of the nearest mwgg airport in the same country
   (great-circle distance), else the nearest anywhere. Deprecated tz links are canonicalised
   (`Asia/Calcutta` → `Asia/Kolkata`, `Europe/Kiev` → `Europe/Kyiv`, …).
4. Validate every zone against `ZoneId.getAvailableZoneIds()`; any invalid zone aborts the build.
5. Multi-airport metros (`METROS` in the script) get the metro's city name ("Newark" → "New York",
   "Narita" → "Tokyo") and keep the real municipality and the metro code (`LON`, `NYC`, `TYO`…) as aliases.
6. `size` = type tier × 100 (large 300, medium 200, small 100, seaplane 0; unscheduled large 150) plus a
   bonus for a curated list of the world's busiest airports, so hubs rank first within a tier.
7. Sorted by size, written as UTF-8 TSV; the build fails above 600 KB.

## Format

```
# comment lines start with '#'
code  name  city  country  zone  lat  lon  size  aliases
LHR   London Heathrow Airport  London  GB  Europe/London  51.471  -0.460  398  LON
```

Tab-separated; `aliases` is `|`-separated and may be empty. Coordinates have 3 decimals (~100 m).

## Current output

4,101 airports, ~318 KB, 373 distinct zones (3,800 zones joined by IATA, 43 by ICAO, 258 filled from the
nearest airport in the same country). Parse + index: ~12 ms warm on a laptop JVM, ~60 ms cold under
Robolectric.
