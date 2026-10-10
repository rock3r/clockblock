"""Generates the Clockblock icon family ("Horizon", #53) as vector drawables.

The mark: a sun cut along its horizon into day (marigold, above) and night (moonlight lilac, below), with the two
halves slid out of line. It shows the day you're in and the day your body still expects. The cut stays horizontal
(a diagonal one reads as a "no entry" sign).

One geometry, four files:
- app/src/main/res/drawable/ic_launcher_background.xml: Twilight Indigo, deeper towards the bottom.
- app/src/main/res/drawable/ic_launcher_foreground.xml: the two halves in colour, on the 108 dp adaptive canvas.
- app/src/main/res/drawable/ic_launcher_monochrome.xml: the same shapes in one colour, for themed icons.
- core/notifications/src/main/res/drawable/ic_notif_app.xml: the status-bar small icon, white on transparent on
  the 24 dp grid, used by every notification.

Each half is an exact construction: a half-disc whose two corners are rounded with true fillet arcs (tangent to
both the flat edge and the rim), so the outline has no kinks at any size. The night half is the day half turned
180 degrees about the centre, so the mark is balanced by construction. The flat edges sit on whole units, so they
land on pixel boundaries in the status bar (24 px at mdpi, 72 px at xxhdpi).

The script checks the art against the 66 dp safe zone (radius 33 on the 108 dp canvas) and the small icon against
its 20 dp live area (2 dp padding), and fails if either is broken.

Usage: python3 tools/icon/gen_icon.py
"""
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
APP_DRAWABLE = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
NOTIF_DRAWABLE = os.path.join(ROOT, "core", "notifications", "src", "main", "res", "drawable")

# Launcher (108 dp canvas, safe zone radius 33).
CANVAS = 108.0
SAFE_RADIUS = 33.0
L_RADIUS = 22.0  # radius of the sun
L_SHIFT = 6.0  # each half slides this far sideways (day left, night right)
L_GAP = 4.0  # the horizon: the gap between the halves
L_FILLET = 2.4  # corner rounding

# Small icon (24 dp grid, live area 2..22).
GRID = 24.0
LIVE_MIN, LIVE_MAX = 2.0, 22.0
S_RADIUS = 8.0
S_SHIFT = 2.0
S_GAP = 2.0
S_FILLET = 0.8

# Colours (docs/design.md: Twilight Indigo, Marigold, moonlight lilac).
BG_TOP, BG_BOTTOM = "#FF5B52F0", "#FF2F2A9E"
DAY_TOP, DAY_HORIZON = "#FFFFC93D", "#FFFF9A1F"
NIGHT_HORIZON, NIGHT_BOTTOM = "#FFE6E2FF", "#FFA9AEFF"
WHITE = "#FFFFFFFF"


def fmt(v):
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def p(x, y):
    return f"{fmt(x)},{fmt(y)}"


def half(c, radius, shift, gap, fillet, turn):
    """The day half (upper, slid left), or with `turn` the night half: the same outline turned 180 degrees.

    Returns the path data, points sampled along the whole outline (for the checks: the fillets bulge past their
    tangent points, so the end points alone would understate the reach) and the half's top and bottom y (for its
    gradient). A 180 degree turn keeps the winding, so the arc sweep flags stay the same.
    """
    ox, oy = c - shift, c - gap / 2  # centre of the full disc the day half was cut from
    xf = math.sqrt((radius - fillet) ** 2 - fillet ** 2)  # fillet centres: (ox +- xf, oy - fillet)
    k = radius / (radius - fillet)  # rim tangent points lie on the ray from the disc centre through a fillet centre
    left_flat, right_flat = (ox - xf, oy), (ox + xf, oy)
    left_rim, right_rim = (ox - xf * k, oy - fillet * k), (ox + xf * k, oy - fillet * k)
    top = (ox, oy - radius)

    def t(q):
        return (2 * c - q[0], 2 * c - q[1]) if turn else q

    d = (
        f"M{p(*t(left_flat))}"
        f"A{fmt(fillet)},{fmt(fillet)} 0,0 1,{p(*t(left_rim))}"
        f"A{fmt(radius)},{fmt(radius)} 0,0 1,{p(*t(right_rim))}"
        f"A{fmt(fillet)},{fmt(fillet)} 0,0 1,{p(*t(right_flat))}Z"
    )
    outline = (
        arc_points((ox - xf, oy - fillet), fillet, left_flat, left_rim)
        + arc_points((ox, oy), radius, left_rim, right_rim)
        + arc_points((ox + xf, oy - fillet), fillet, right_rim, right_flat)
    )
    ys = sorted((t(top)[1], t(left_flat)[1]))
    return d, [t(q) for q in outline], ys


def arc_points(centre, r, start, end, steps=720):
    """Points along the arc from [start] to [end] around [centre], clockwise on screen (y down), like sweep-flag 1."""
    a0 = math.atan2(start[1] - centre[1], start[0] - centre[0])
    a1 = math.atan2(end[1] - centre[1], end[0] - centre[0])
    span = (a1 - a0) % (2 * math.pi)
    return [
        (centre[0] + r * math.cos(a0 + span * i / steps), centre[1] + r * math.sin(a0 + span * i / steps))
        for i in range(steps + 1)
    ]


def mark(c, radius, shift, gap, fillet):
    day, day_points, day_span = half(c, radius, shift, gap, fillet, turn=False)
    night, night_points, night_span = half(c, radius, shift, gap, fillet, turn=True)
    return day, night, day_points + night_points, day_span, night_span


def gradient(x0, y0, x1, y1, start, end):
    return (
        '<gradient android:type="linear" '
        f'android:startX="{fmt(x0)}" android:startY="{fmt(y0)}" android:endX="{fmt(x1)}" android:endY="{fmt(y1)}">\n'
        f'                <item android:offset="0" android:color="{start}" />\n'
        f'                <item android:offset="1" android:color="{end}" />\n'
        "            </gradient>"
    )


def path(name, d, fill):
    if fill.startswith("<gradient"):
        return (
            f'    <path android:name="{name}" android:pathData="{d}">\n'
            f'        <aapt:attr name="android:fillColor">\n            {fill}\n        </aapt:attr>\n'
            "    </path>\n"
        )
    return f'    <path android:name="{name}" android:fillColor="{fill}" android:pathData="{d}" />\n'


def vector(comment, body, size):
    aapt = '\n    xmlns:aapt="http://schemas.android.com/aapt"' if "aapt:attr" in body else ""
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generated by tools/icon/gen_icon.py: edit the script, not this file. -->\n"
        f"<!-- {comment} -->\n"
        f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{aapt}\n'
        f'    android:width="{fmt(size)}dp"\n    android:height="{fmt(size)}dp"\n'
        f'    android:viewportWidth="{fmt(size)}"\n    android:viewportHeight="{fmt(size)}">\n'
        + body
        + "</vector>\n"
    )


def write(directory, name, text):
    with open(os.path.join(directory, name), "w") as f:
        f.write(text)


def main():
    c = CANVAS / 2
    day, night, points, (day_top, day_horizon), (night_horizon, night_bottom) = mark(
        c, L_RADIUS, L_SHIFT, L_GAP, L_FILLET
    )
    reach = max(math.hypot(x - c, y - c) for x, y in points)
    if reach > SAFE_RADIUS:
        sys.exit(f"launcher art reaches {reach:.2f} from the centre, outside the safe zone ({SAFE_RADIUS})")

    s = GRID / 2
    s_day, s_night, s_points, _, _ = mark(s, S_RADIUS, S_SHIFT, S_GAP, S_FILLET)
    xs, ys = [x for x, _ in s_points], [y for _, y in s_points]
    if min(xs) < LIVE_MIN - 1e-6 or max(xs) > LIVE_MAX + 1e-6 or min(ys) < LIVE_MIN - 1e-6 or max(ys) > LIVE_MAX + 1e-6:
        sys.exit(f"small icon leaves the live area: x {min(xs):.2f}..{max(xs):.2f}, y {min(ys):.2f}..{max(ys):.2f}")

    full = f"M0,0h{fmt(CANVAS)}v{fmt(CANVAS)}h-{fmt(CANVAS)}z"
    write(
        APP_DRAWABLE,
        "ic_launcher_background.xml",
        vector(
            "Twilight Indigo, deeper towards the bottom so the lilac night half keeps its contrast.",
            path("sky", full, gradient(c, 0, c, CANVAS, BG_TOP, BG_BOTTOM)),
            CANVAS,
        ),
    )
    write(
        APP_DRAWABLE,
        "ic_launcher_foreground.xml",
        vector(
            "Horizon: a sun cut along its horizon into day (marigold) and night (lilac), the halves slid out of "
            f"line. Inside the 66 dp safe zone (reach {reach:.1f} of {SAFE_RADIUS:g}).",
            path("day", day, gradient(c, day_top, c, day_horizon, DAY_TOP, DAY_HORIZON))
            + path("night", night, gradient(c, night_horizon, c, night_bottom, NIGHT_HORIZON, NIGHT_BOTTOM)),
            CANVAS,
        ),
    )
    write(
        APP_DRAWABLE,
        "ic_launcher_monochrome.xml",
        vector(
            "Themed-icon layer: the foreground's two halves in one colour (the horizon gap keeps them apart).",
            path("day", day, WHITE) + path("night", night, WHITE),
            CANVAS,
        ),
    )
    write(
        NOTIF_DRAWABLE,
        "ic_notif_app.xml",
        vector(
            "Status-bar small icon for every notification: the Horizon mark, white on transparent (the system "
            "tints it). Flat edges on whole units so they stay crisp at 24 px.",
            path("day", s_day, WHITE) + path("night", s_night, WHITE),
            GRID,
        ),
    )
    print(f"launcher reach {reach:.2f}/{SAFE_RADIUS:g}; small icon x {min(xs):.2f}..{max(xs):.2f}, "
          f"y {min(ys):.2f}..{max(ys):.2f}", file=sys.stderr)


if __name__ == "__main__":
    main()
