#!/usr/bin/env python3
"""
Builds `core/data/src/main/assets/places.tsv`, the offline airport + time zone dataset behind PlaceSearch.

Sources (see licenses/DATA_ATTRIBUTION.md):
  * OurAirports `airports.csv`  - Public Domain   - base list: IATA, type, scheduled service, coordinates.
  * mwgg/Airports `airports.json` - MIT           - IANA time zone per airport.
OpenFlights is deliberately NOT used (AGPL-3.0 code / ODbL data).

Pipeline:
  1. Keep OurAirports rows with a 3-letter IATA code that are airports/seaplane bases with scheduled
     service (plus large airports even when the flag is missing). One row per IATA code (best tier wins).
  2. Join mwgg on IATA (disambiguated by ICAO, then distance) to get the IANA zone.
  3. Missing zones are filled from the nearest mwgg airport in the same country (great-circle distance),
     falling back to the nearest airport anywhere. Deprecated aliases are canonicalised.
  4. Every zone is validated against Java's `ZoneId.getAvailableZoneIds()` (via ZoneIds.java) so the app
     can always `ZoneId.of()` it. Any invalid zone aborts the build.
  5. City names are tidied (`tidy_city`): trailing "Airport" and " - <Name> Island" suffixes go, only the
     first of "A/B" is kept, and CITY_OVERRIDES fixes garbled or over-long names. Dropped parts become aliases.
  6. Airports of multi-airport metros get the metro's city name ("Newark" -> "New York", "Narita" ->
     "Tokyo") with the original municipality and metro code kept as search aliases.
  7. A `size` score ranks results: type tier x 100 + a passenger-traffic bonus for a curated list of hubs.

Output: UTF-8 TSV, one airport per line, sorted by size desc then code:
  code  name  city  country  zone  lat  lon  size  aliases(|-separated)
Lines starting with '#' are comments.

Usage:
  python3 tools/places/build_places.py                # downloads sources into a cache dir
  python3 tools/places/build_places.py --cache /tmp/x # reuse previously downloaded files
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import math
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

OURAIRPORTS_URL = "https://davidmegginson.github.io/ourairports-data/airports.csv"
MWGG_URL = "https://raw.githubusercontent.com/mwgg/Airports/master/airports.json"

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUT = ROOT / "core/data/src/main/assets/places.tsv"
MAX_BYTES = 600 * 1024

TYPE_TIER = {"large_airport": 3, "medium_airport": 2, "small_airport": 1, "seaplane_base": 0}

# Deprecated tzdb links -> canonical names (all still parse in java.time, but canonical ids are nicer to store).
TZ_CANONICAL = {
    "Asia/Calcutta": "Asia/Kolkata",
    "Asia/Katmandu": "Asia/Kathmandu",
    "Asia/Saigon": "Asia/Ho_Chi_Minh",
    "Asia/Rangoon": "Asia/Yangon",
    "Asia/Ulan_Bator": "Asia/Ulaanbaatar",
    "Asia/Dacca": "Asia/Dhaka",
    "Asia/Thimbu": "Asia/Thimphu",
    "Asia/Ujung_Pandang": "Asia/Makassar",
    "Asia/Chongqing": "Asia/Shanghai",
    "Asia/Chungking": "Asia/Shanghai",
    "Asia/Harbin": "Asia/Shanghai",
    "Asia/Kashgar": "Asia/Urumqi",
    "Asia/Macao": "Asia/Macau",
    "Asia/Istanbul": "Europe/Istanbul",
    "Europe/Kiev": "Europe/Kyiv",
    "Europe/Uzhgorod": "Europe/Kyiv",
    "Europe/Zaporozhye": "Europe/Kyiv",
    "Atlantic/Faeroe": "Atlantic/Faroe",
    "America/Buenos_Aires": "America/Argentina/Buenos_Aires",
    "America/Catamarca": "America/Argentina/Catamarca",
    "America/Cordoba": "America/Argentina/Cordoba",
    "America/Jujuy": "America/Argentina/Jujuy",
    "America/Mendoza": "America/Argentina/Mendoza",
    "America/Indianapolis": "America/Indiana/Indianapolis",
    "America/Louisville": "America/Kentucky/Louisville",
    "America/Godthab": "America/Nuuk",
    "America/Montreal": "America/Toronto",
    "America/Shiprock": "America/Denver",
    "America/Santa_Isabel": "America/Tijuana",
    "America/Ensenada": "America/Tijuana",
    "Pacific/Truk": "Pacific/Chuuk",
    "Pacific/Ponape": "Pacific/Pohnpei",
    "Pacific/Enderbury": "Pacific/Kanton",
    "Pacific/Johnston": "Pacific/Honolulu",
    "Africa/Asmera": "Africa/Asmara",
    "Africa/Timbuktu": "Africa/Bamako",
    "Australia/Canberra": "Australia/Sydney",
    "Australia/ACT": "Australia/Sydney",
    "Australia/NSW": "Australia/Sydney",
}

# Multi-airport metros: code -> (display city, member airports). Members take the metro city name.
METROS = {
    "LON": ("London", ["LHR", "LGW", "STN", "LTN", "LCY", "SEN"]),
    "NYC": ("New York", ["JFK", "EWR", "LGA"]),
    "PAR": ("Paris", ["CDG", "ORY", "BVA"]),
    "TYO": ("Tokyo", ["HND", "NRT"]),
    "OSA": ("Osaka", ["KIX", "ITM", "UKB"]),
    "SEL": ("Seoul", ["ICN", "GMP"]),
    "BJS": ("Beijing", ["PEK", "PKX"]),
    "SHA": ("Shanghai", ["PVG", "SHA"]),
    "MIL": ("Milan", ["MXP", "LIN", "BGY"]),
    "ROM": ("Rome", ["FCO", "CIA"]),
    "WAS": ("Washington", ["IAD", "DCA", "BWI"]),
    "CHI": ("Chicago", ["ORD", "MDW"]),
    "QHO": ("Houston", ["IAH", "HOU"]),
    "QDF": ("Dallas", ["DFW", "DAL"]),
    "QLA": ("Los Angeles", ["LAX", "BUR", "LGB", "SNA", "ONT"]),
    "QSF": ("San Francisco", ["SFO", "OAK", "SJC"]),
    "QMI": ("Miami", ["MIA", "FLL"]),
    "DTT": ("Detroit", ["DTW"]),
    "YTO": ("Toronto", ["YYZ", "YTZ"]),
    "YMQ": ("Montreal", ["YUL"]),
    "SAO": ("São Paulo", ["GRU", "CGH", "VCP"]),
    "RIO": ("Rio de Janeiro", ["GIG", "SDU"]),
    "BUE": ("Buenos Aires", ["EZE", "AEP"]),
    "MOW": ("Moscow", ["SVO", "DME", "VKO", "ZIA"]),
    "STO": ("Stockholm", ["ARN", "BMA", "NYO"]),
    "OSL": ("Oslo", ["OSL", "TRF"]),
    "BKK": ("Bangkok", ["BKK", "DMK"]),
    "JKT": ("Jakarta", ["CGK", "HLP"]),
    "KUL": ("Kuala Lumpur", ["KUL", "SZB"]),
    "TPE": ("Taipei", ["TPE", "TSA"]),
    "IST": ("Istanbul", ["IST", "SAW"]),
    "DXB": ("Dubai", ["DXB", "DWC"]),
    "THR": ("Tehran", ["IKA", "THR"]),
    "SPK": ("Sapporo", ["CTS", "OKD"]),
    "NGO": ("Nagoya", ["NGO", "NKM"]),
    "BFS": ("Belfast", ["BFS", "BHD"]),
    "GLA": ("Glasgow", ["GLA", "PIK"]),
    "BRU": ("Brussels", ["BRU", "CRL"]),
    "VCE": ("Venice", ["VCE", "TSF"]),
    "BER": ("Berlin", ["BER"]),
    "ORL": ("Orlando", ["MCO", "SFB"]),
    "PHX": ("Phoenix", ["PHX", "AZA"]),
    "REK": ("Reykjavík", ["KEF", "RKV"]),
    "BHZ": ("Belo Horizonte", ["CNF", "PLU"]),
    "MEX": ("Mexico City", ["MEX", "NLU"]),
    "BCN": ("Barcelona", ["BCN"]),
    "EAP": ("Basel", ["BSL"]),
}

# City names longer than this need a hand fix in CITY_OVERRIDES (which may also confirm a long name as it is).
MAX_CITY_CHARS = 24

# Hand-fixed city names: code -> (city, extra search aliases). For upstream names that are garbled, far too long,
# or whose first "/" part isn't the place people would look for.
CITY_OVERRIDES: dict[str, tuple[str, list[str]]] = {
    "CXR": ("Nha Trang", ["Cam Ranh"]),  # upstream: "Nha Trang/nha Trang aiurportCam Ranh"
    "YSQ": ("Songyuan", ["Qian Gorlos Mongol Autonomous County"]),
    "LEU": ("La Seu d'Urgell", ["Pyrenees", "Andorra"]),  # "La Seu d'Urgell Pyrenees and Andorra"
    "CPC": ("San Martín de los Andes", ["Chapelco"]),  # "Chapelco/San Martin de los Andes"
    "TRS": ("Trieste", ["Ronchi dei Legionari"]),  # "Ronchi dei Legionari/Trieste"
    "RFD": ("Rockford", ["Chicago"]),  # "Chicago/Rockford"
    "VST": ("Västerås", ["Stockholm"]),  # "Stockholm / Västerås"
    "ZGS": ("La Romaine", ["Le Golfe-du-Saint-Laurent"]),
    "RNS": ("Rennes", ["Saint-Jacques-de-la-Lande"]),
    "JYV": ("Jyväskylä", ["Jyväskylän Maalaiskunta"]),
    "KLU": ("Klagenfurt", ["Klagenfurt am Wörthersee"]),
    "MFM": ("Macau", ["Nossa Senhora do Carmo"]),
    "DWO": ("Kotte", ["Sri Jayawardenepura Kotte"]),
    # Long, but the real names.
    "KMC": ("King Khaled Military City", []),
    "IRZ": ("Santa Isabel do Rio Negro", []),
}

# Busiest passenger airports (roughly ACI 2023-24 order). Only used to order results inside a tier;
# exact positions don't matter much, but hubs must beat regional fields with the same type.
HUBS = """
ATL DXB DFW HND LHR DEN IST LAX ORD DEL CDG JFK CAN AMS SIN ICN FRA PVG MAD BKK PEK LAS MCO SZX MIA CTU
BCN CLT KUL SEA SFO EWR PHX YYZ CGK BOM IAH DOH CKG MNL HKG TPE KMG SHA FCO MUC LGW SAW BOS MSP MEX FLL
DTW PHL SYD LGA GRU JED RUH BLR MEL BWI SLC DCA IAD SAN NRT KIX MDW TPA BNA AUS HYD MAA ORY PMI LIS DUB
ZRH CPH VIE OSL ARN HEL BRU MAN STN ATH MXP WAW PRG BUD DUS HAM BER YVR YUL YYC AKL BNE PER ITM FUK CTS
OKA GMP PUS CJU XIY HGH WUH CSX NKG XMN TAO CGO PKX SGN HAN DPS CCU CAI JNB CPT ADD NBO LOS CMN TLV AUH
MCT KWI BAH BOG LIM SCL EZE GIG PTY CUN GDL HNL PDX DAL HOU RDU STL OAK SJC SMF MSY MCI LTN EDI BHX GLA
NCE LYS MRS AGP ALC VLC BGY NAP VCE OPO FAO GVA BSL CGN STR SVO DME VKO LED OTP SOF BEG KEF RIX AYT ADB
ESB CMB DAC KTM ISB LHE KHI TAS ALA GYD TBS EVN AMM BEY MLE SEZ MRU DAR ACC ABJ DKR TUN ALG RAK HRG SSH
SJU MBJ NAS PUJ SDQ HAV SJO SAL GUA UIO GYE MVD ASU CCS BSB CNF SSA REC FOR POA CWB VCP CGH SDU AEP
SNA BUR ONT LGB SAT IND CLE CMH PIT CVG MKE JAX RSW OGG KOA LIH ANC ABQ TUS ELP OMA BDL PVD BUF RIC
ORF CHS SAV MEM SDF BHM OKC TUL BOI GEG RNO PBI SFB ADL CBR OOL CNS HBA WLG CHC ZQN NAN PPT GUM LCY
"""
HUB_ORDER = HUBS.split()


@dataclass
class Airport:
    code: str
    name: str
    city: str
    country: str
    lat: float
    lon: float
    type: str
    icao: str
    scheduled: bool
    zone: str | None = None
    zone_source: str = ""
    aliases: list[str] = field(default_factory=list)
    size: int = 0


def download(url: str, dest: Path) -> Path:
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    print(f"Downloading {url}", file=sys.stderr)
    req = urllib.request.Request(url, headers={"User-Agent": "clockblock-places/1"})
    with urllib.request.urlopen(req, timeout=120) as r, open(dest, "wb") as f:
        shutil.copyfileobj(r, f)
    return dest


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 6371.0 * 2 * math.asin(math.sqrt(min(1.0, a)))


def java_zone_ids() -> set[str]:
    """Zone ids java.time accepts; falls back to Python's zoneinfo when no JDK is on PATH."""
    helper = Path(__file__).with_name("ZoneIds.java")
    try:
        out = subprocess.run(["java", str(helper)], check=True, capture_output=True, text=True).stdout
        ids = {line.strip() for line in out.splitlines() if line.strip()}
        print(f"Validating against {len(ids)} java.time zone ids", file=sys.stderr)
        return ids
    except (OSError, subprocess.CalledProcessError) as e:
        print(f"WARNING: java unavailable ({e}); validating with Python zoneinfo instead", file=sys.stderr)
        import zoneinfo

        return set(zoneinfo.available_timezones())


def clean(text: str) -> str:
    return " ".join(text.replace("\t", " ").replace("|", "/").split())


def municipality_city(raw: str) -> tuple[str, str | None]:
    """
    "London, Essex" -> ("London", None); "Guangzhou (Huadu)" -> ("Guangzhou", "Huadu");
    "Paris (Roissy-en-France, Val-d'Oise)" -> ("Paris", "Roissy-en-France").
    """
    if not raw:
        return "", None
    qualifier = None
    if "(" in raw:
        head, _, rest = raw.partition("(")
        qualifier = clean(rest.split(")")[0].split(",")[0]) or None
        raw = head + rest.partition(")")[2]
    return clean(raw.split(",")[0]), qualifier


_AIRPORT_SUFFIX = re.compile(r"\s+airport$", re.IGNORECASE)
# "Tanjung Redeb - Borneo Island", "Tanjung Pinang-Bintan Island" (but not "Wangi-wangi Island": the island part
# must be capitalised).
_ISLAND_SUFFIX = re.compile(r"\s*-\s*([A-Z][^-/]* Island)$")


def tidy_city(code: str, raw: str) -> tuple[str, list[str]]:
    """
    The display city for an upstream municipality, and the search aliases that keep the dropped parts findable:
    a hand fix from CITY_OVERRIDES, else the first of several "/"-separated places, without a trailing "Airport"
    or a " - <Name> Island" suffix.
    """
    if code in CITY_OVERRIDES:
        city, aliases = CITY_OVERRIDES[code]
        return city, list(aliases)
    parts = [p for p in (clean(_AIRPORT_SUFFIX.sub("", x)) for x in raw.split("/")) if p]
    if not parts:
        return clean(raw), []
    city, aliases = parts[0], parts[1:]
    island = _ISLAND_SUFFIX.search(city)
    if island:
        city = city[: island.start()].strip()
        aliases.insert(0, island.group(1))
    return city, aliases


def load_ourairports(path: Path) -> list[Airport]:
    by_code: dict[str, Airport] = {}
    with open(path, encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            code = r["iata_code"].strip().upper()
            if len(code) != 3 or not code.isalpha():
                continue
            t = r["type"]
            if t not in TYPE_TIER:
                continue  # closed, heliport, balloonport
            scheduled = r["scheduled_service"] == "yes"
            if not scheduled and t != "large_airport":
                continue
            city, qualifier = municipality_city(r["municipality"])
            a = Airport(
                code=code,
                name=clean(r["name"]),
                city=city,
                country=r["iso_country"].strip().upper(),
                lat=float(r["latitude_deg"]),
                lon=float(r["longitude_deg"]),
                type=t,
                icao=(r["icao_code"] or r["ident"]).strip().upper(),
                scheduled=scheduled,
                aliases=[qualifier] if qualifier else [],
            )
            prev = by_code.get(code)
            if prev is None or (a.scheduled, TYPE_TIER[a.type]) > (prev.scheduled, TYPE_TIER[prev.type]):
                by_code[code] = a
    return list(by_code.values())


def load_mwgg(path: Path) -> list[dict]:
    with open(path, encoding="utf-8") as f:
        return [v for v in json.load(f).values() if v.get("tz")]


def assign_zones(airports: list[Airport], mwgg: list[dict]) -> dict[str, int]:
    by_iata: dict[str, list[dict]] = {}
    by_icao: dict[str, dict] = {}
    by_country: dict[str, list[dict]] = {}
    for m in mwgg:
        if m.get("iata"):
            by_iata.setdefault(m["iata"].upper(), []).append(m)
        by_icao[m["icao"].upper()] = m
        by_country.setdefault(m.get("country", "").upper(), []).append(m)

    stats = {"iata": 0, "icao": 0, "nearest_same_country": 0, "nearest_any": 0}
    for a in airports:
        cands = by_iata.get(a.code, [])
        match = next((m for m in cands if m["icao"].upper() == a.icao), None)
        if match is None and cands:
            nearest = min(cands, key=lambda m: haversine_km(a.lat, a.lon, m["lat"], m["lon"]))
            if haversine_km(a.lat, a.lon, nearest["lat"], nearest["lon"]) < 100:
                match = nearest
        if match is not None:
            a.zone, a.zone_source = match["tz"], "iata"
            stats["iata"] += 1
            if not a.city and match.get("city"):
                a.city = clean(match["city"])
            continue
        if a.icao in by_icao:
            a.zone, a.zone_source = by_icao[a.icao]["tz"], "icao"
            stats["icao"] += 1
            continue
        pool = by_country.get(a.country)
        key = "nearest_same_country" if pool else "nearest_any"
        nearest = min(pool or mwgg, key=lambda m: haversine_km(a.lat, a.lon, m["lat"], m["lon"]))
        a.zone, a.zone_source = nearest["tz"], key
        stats[key] += 1
    return stats


def apply_metros(airports: list[Airport]) -> None:
    index = {a.code: a for a in airports}
    for metro_code, (city, members) in METROS.items():
        for code in members:
            a = index.get(code)
            if a is None:
                continue
            if a.city and a.city.casefold() != city.casefold():
                a.aliases.append(a.city)
            a.city = city
            if metro_code != a.code:
                a.aliases.append(metro_code)


def score(a: Airport) -> int:
    # Large airports without scheduled service (closed for now, opening soon, executive fields) stay
    # searchable for manual entry but rank below every scheduled medium airport.
    base = 100 * TYPE_TIER[a.type] if a.scheduled else 150
    if a.code in HUB_ORDER:
        i = HUB_ORDER.index(a.code)
        base += max(1, round(99 * (1 - i / len(HUB_ORDER))))
        base = max(base, 300)  # a curated hub is never ranked below a large airport
    return max(base, 1)


def build(cache: Path, out: Path) -> None:
    cache.mkdir(parents=True, exist_ok=True)
    airports = load_ourairports(download(OURAIRPORTS_URL, cache / "airports.csv"))
    mwgg = load_mwgg(download(MWGG_URL, cache / "airports.json"))
    stats = assign_zones(airports, mwgg)
    valid = java_zone_ids()

    errors = []
    for a in airports:
        a.zone = TZ_CANONICAL.get(a.zone, a.zone)
        if a.zone not in valid:
            errors.append(f"{a.code}: invalid zone {a.zone!r}")
        if not a.city:
            a.city = a.name
        a.city, extra = tidy_city(a.code, a.city)
        a.aliases.extend(extra)
    if errors:
        raise SystemExit("Invalid zones:\n" + "\n".join(errors))

    apply_metros(airports)
    for a in airports:
        a.size = score(a)
    airports.sort(key=lambda a: (-a.size, a.code))

    buf = io.StringIO()
    buf.write("# Clockblock places: OurAirports (Public Domain) + mwgg/Airports time zones (MIT).\n")
    buf.write("# Generated by tools/places/build_places.py - do not edit by hand.\n")
    buf.write("# code\tname\tcity\tcountry\tzone\tlat\tlon\tsize\taliases\n")
    for a in airports:
        aliases = "|".join(dict.fromkeys(clean(x) for x in a.aliases if x))
        buf.write(
            f"{a.code}\t{a.name}\t{a.city}\t{a.country}\t{a.zone}\t"
            f"{a.lat:.3f}\t{a.lon:.3f}\t{a.size}\t{aliases}\n"
        )
    data = buf.getvalue().encode("utf-8")
    if len(data) > MAX_BYTES:
        raise SystemExit(f"places.tsv is {len(data)} bytes, over the {MAX_BYTES} byte budget")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(data)

    print(
        f"Wrote {out.relative_to(ROOT) if out.is_relative_to(ROOT) else out}: {len(airports)} airports, "
        f"{len(data) / 1024:.1f} KB, {len({a.zone for a in airports})} zones; zone sources {stats}",
        file=sys.stderr,
    )


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--cache", type=Path, default=Path(tempfile.gettempdir()) / "clockblock-places",
                   help="where to download/reuse the source files")
    p.add_argument("--out", type=Path, default=DEFAULT_OUT)
    p.add_argument("--refresh", action="store_true", help="re-download sources even if cached")
    args = p.parse_args()
    if args.refresh and args.cache.exists():
        for name in ("airports.csv", "airports.json"):
            (args.cache / name).unlink(missing_ok=True)
    build(args.cache, args.out)


if __name__ == "__main__":
    main()
