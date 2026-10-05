# Bundled fonts

| File | Family | Licence | Size |
|---|---|---|---|
| `src/main/res/font/google_sans_flex.ttf` | Google Sans Flex (variable) | SIL OFL 1.1 — [GoogleSansFlex-OFL.txt](GoogleSansFlex-OFL.txt), trademark note in [GoogleSansFlex-TRADEMARKS.md](GoogleSansFlex-TRADEMARKS.md) | 697,044 B (~681 KB) |
| `src/main/res/font/fraunces.ttf` | Fraunces (variable, roman) | SIL OFL 1.1 — [Fraunces-OFL.txt](Fraunces-OFL.txt) | 160,240 B (~156 KB) |

Neither licence declares a Reserved Font Name, so the subset files keep the original family names.

## How the files were made (fontTools 4.x)

Both are subsets of the upstream variable fonts. Glyph coverage: Latin, Latin-1, Latin Extended-A, the
Romanian/Hawaiian extras, combining marks, general punctuation, super/subscript digits, currency, arrows,
`−`, `≈`, `≠`, `≤`, `≥`, `●`, `○`, `☀`, `✓`, `✕`.

```sh
U="U+0000-00FF,U+0131,U+0152-0153,U+0100-017F,U+0218-021B,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0300-0304,U+0308,U+030A,U+030C,U+0327-0328,U+2000-206F,U+2070-2079,U+207F,U+2080-2089,U+20AC,U+20A4-20BF,U+2113,U+2116,U+2122,U+2190-2199,U+21BA-21BB,U+2212,U+2215,U+2219,U+221E,U+2248,U+2260,U+2264-2265,U+25CF,U+25CB,U+2600,U+2609,U+263C,U+2713,U+2715,U+FEFF,U+FFFD"
F="kern,liga,calt,ccmp,locl,mark,mkmk,tnum,pnum,lnum,case,ss01,ss02,ss03,ss04,ss05,ss06,ss07,ss08,frac,sups,subs,zero"

# Google Sans Flex: subset, then pin GRAD to 0 and drop the wdth axis (default 100).
pyftsubset GoogleSansFlex.ttf --unicodes="$U" --layout-features="$F" --output-file=gsf_subset.ttf
fonttools varLib.instancer gsf_subset.ttf GRAD=0 wdth=drop -o google_sans_flex.ttf

# Fraunces: subset, then pin SOFT=100 and WONK=1 (the house style).
pyftsubset Fraunces.ttf --unicodes="$U" --layout-features="$F" --output-file=fraunces_subset.ttf
fonttools varLib.instancer fraunces_subset.ttf SOFT=100 WONK=1 -o fraunces.ttf
```

Remaining axes — Google Sans Flex: `opsz` 6–144, `wght` 1–1000, `ROND` 0–100, `slnt` −10–0.
Fraunces: `opsz` 9–144, `wght` 100–900.

Dropping `wdth` and `GRAD` takes Google Sans Flex from ~3.2 MB to ~681 KB. If width ever becomes a design
requirement (e.g. condensed chips), re-run the instancer with `wdth=75:100` (the file grows accordingly).
