"""Icon family options for #53 (exploration, not shipped).

Writes, for each option, the adaptive-icon background + foreground + monochrome layers (108 dp canvas, artwork inside
the 66 dp safe zone) and the status-bar small icon (24 dp, white on transparent) as vector drawables into the app's
debug resources, so the Robolectric contact sheets render the real VectorDrawables.

Angles are clock angles: 0 = 12 o'clock, increasing clockwise (y points down).

Usage: python3 tools/icon/options/gen_icon_options.py app/src/debug/res/drawable
"""
import math
import os
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
os.makedirs(OUT, exist_ok=True)

C = 54.0  # adaptive canvas centre
N = 12.0  # small-icon canvas centre


def fmt(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def pt(cx, cy, r, a):
    t = math.radians(a)
    return cx + r * math.sin(t), cy - r * math.cos(t)


def arc(cx, cy, r, a0, a1):
    """Open arc from clock angle a0 clockwise to a1 (stroke it)."""
    x0, y0 = pt(cx, cy, r, a0)
    x1, y1 = pt(cx, cy, r, a1)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return f"M{fmt(x0)},{fmt(y0)}A{fmt(r)},{fmt(r)} 0,{large} 1,{fmt(x1)},{fmt(y1)}"


def circle(cx, cy, r):
    return (
        f"M{fmt(cx - r)},{fmt(cy)}a{fmt(r)},{fmt(r)} 0,1 1,{fmt(2 * r)},0"
        f"a{fmt(r)},{fmt(r)} 0,1 1,{fmt(-2 * r)},0Z"
    )


def thick_arc(cx, cy, r, w, a0, a1, round_caps=True):
    """Filled outline of a stroked arc (so it can carry a gradient fill and survives any renderer)."""
    ro, ri = r + w / 2, r - w / 2
    large = 1 if (a1 - a0) % 360 > 180 else 0
    o0, o1 = pt(cx, cy, ro, a0), pt(cx, cy, ro, a1)
    i1, i0 = pt(cx, cy, ri, a1), pt(cx, cy, ri, a0)
    cap = w / 2
    d = f"M{fmt(o0[0])},{fmt(o0[1])}A{fmt(ro)},{fmt(ro)} 0,{large} 1,{fmt(o1[0])},{fmt(o1[1])}"
    d += (f"A{fmt(cap)},{fmt(cap)} 0,0 1,{fmt(i1[0])},{fmt(i1[1])}" if round_caps else f"L{fmt(i1[0])},{fmt(i1[1])}")
    d += f"A{fmt(ri)},{fmt(ri)} 0,{large} 0,{fmt(i0[0])},{fmt(i0[1])}"
    d += (f"A{fmt(cap)},{fmt(cap)} 0,0 1,{fmt(o0[0])},{fmt(o0[1])}Z" if round_caps else "Z")
    return d


def crescent(cx, cy, r, toward, bite_r, bite_off):
    """Disc of radius r minus a disc of radius bite_r offset by bite_off at clock angle `toward`."""
    bx, by = pt(cx, cy, bite_off, toward)
    dx, dy = bx - cx, by - cy
    d = math.hypot(dx, dy)
    a = (d * d + r * r - bite_r * bite_r) / (2 * d)
    h = math.sqrt(r * r - a * a)
    px, py = cx + a * dx / d, cy + a * dy / d
    i1 = (px - h * dy / d, py + h * dx / d)
    i2 = (px + h * dy / d, py - h * dx / d)
    return (
        f"M{fmt(i1[0])},{fmt(i1[1])}A{fmt(r)},{fmt(r)} 0,1 1,{fmt(i2[0])},{fmt(i2[1])}"
        f"A{fmt(bite_r)},{fmt(bite_r)} 0,0 0,{fmt(i1[0])},{fmt(i1[1])}Z"
    )


def half_disc(cx, cy, r, axis, side, shift, gap, rc=0.0):
    """Half of a disc cut along the diameter at clock angle `axis`; side=+1 keeps the half below/clockwise.

    The half is slid by `shift` along the axis and pushed `gap / 2` away from the cut; its two corners are softened
    with radius ~rc.
    """
    ax, ay = math.sin(math.radians(axis)), -math.cos(math.radians(axis))
    nx, ny = -ay * side, ax * side  # normal pointing into the kept half
    ox, oy = cx + ax * shift + nx * gap / 2, cy + ay * shift + ny * gap / 2

    def on_arc(t):
        return ox + r * (math.cos(t) * ax + math.sin(t) * nx), oy + r * (math.cos(t) * ay + math.sin(t) * ny)

    p1, p0 = on_arc(0), on_arc(math.pi)
    sweep = 1 if side > 0 else 0
    if rc <= 0:
        return f"M{fmt(p1[0])},{fmt(p1[1])}A{fmt(r)},{fmt(r)} 0,0 {sweep},{fmt(p0[0])},{fmt(p0[1])}Z"
    q1 = (p1[0] - ax * rc, p1[1] - ay * rc)
    q0 = (p0[0] + ax * rc, p0[1] + ay * rc)
    a1, a0 = on_arc(rc / r), on_arc(math.pi - rc / r)
    return (
        f"M{fmt(q1[0])},{fmt(q1[1])}Q{fmt(p1[0])},{fmt(p1[1])} {fmt(a1[0])},{fmt(a1[1])}"
        f"A{fmt(r)},{fmt(r)} 0,0 {sweep},{fmt(a0[0])},{fmt(a0[1])}"
        f"Q{fmt(p0[0])},{fmt(p0[1])} {fmt(q0[0])},{fmt(q0[1])}Z"
    )


# ------------------------------------------------------------------------------------------ XML writers


def linear(x0, y0, x1, y1, stops):
    items = "".join(
        f'\n                <item android:offset="{fmt(o)}" android:color="{c}" />' for o, c in stops
    )
    return (
        f'<gradient android:type="linear" android:startX="{fmt(x0)}" android:startY="{fmt(y0)}" '
        f'android:endX="{fmt(x1)}" android:endY="{fmt(y1)}">{items}\n            </gradient>'
    )


def radial(cx, cy, r, stops):
    items = "".join(
        f'\n                <item android:offset="{fmt(o)}" android:color="{c}" />' for o, c in stops
    )
    return (
        f'<gradient android:type="radial" android:centerX="{fmt(cx)}" android:centerY="{fmt(cy)}" '
        f'android:gradientRadius="{fmt(r)}">{items}\n            </gradient>'
    )


def path(name, d, fill):
    ft = ""
    if name.endswith("!"):
        name, ft = name[:-1], ' android:fillType="evenOdd"'
    if fill.startswith("<gradient"):
        return (
            f'    <path android:name="{name}"{ft} android:pathData="{d}">\n'
            f'        <aapt:attr name="android:fillColor">\n            {fill}\n        </aapt:attr>\n    </path>\n'
        )
    return f'    <path android:name="{name}"{ft} android:fillColor="{fill}" android:pathData="{d}" />\n'


def vector(comment, body, size=108):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generated by tools/icon/options/gen_icon_options.py (exploration for #53). -->\n"
        f"<!-- {comment} -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        f'    android:width="{size}dp"\n    android:height="{size}dp"\n'
        f'    android:viewportWidth="{size}"\n    android:viewportHeight="{size}">\n'
        + body
        + "</vector>\n"
    )


def write(name, text):
    with open(os.path.join(OUT, name), "w") as f:
        f.write(text)


FULL = "M0,0h108v108h-108z"
WHITE = "#FFFFFFFF"


def emit(key, title, bg, fg_parts, mono_parts, notif_parts):
    write(f"iconopt_{key}_background.xml", vector(f"{title}: background.", path("bg", FULL, bg) if isinstance(bg, str) else "".join(bg)))
    write(f"iconopt_{key}_foreground.xml", vector(f"{title}: foreground.", "".join(path(n, d, f) for n, d, f in fg_parts)))
    write(f"iconopt_{key}_monochrome.xml", vector(f"{title}: themed-icon layer.", "".join(path(q[0], q[1], q[2] if len(q) > 2 else WHITE) for q in mono_parts)))
    write(
        f"iconopt_{key}_notif.xml",
        vector(f"{title}: status-bar small icon (white on transparent; the system tints it).", "".join(path(q[0], q[1], q[2] if len(q) > 2 else WHITE) for q in notif_parts), size=24),
    )


INDIGO_BG = linear(54, 0, 54, 108, [(0, "#FF5B52F0"), (1, "#FF2F2A9E")])

# ------------------------------------------------------------------------------------------ A · Two skies
# The Two skies dial as a mark: two concentric rings, each split into a bright day arc and a dim night arc. Outer:
# the sky where you are (marigold day centred on noon, at the top). Inner: your body's sky (blue), the same split
# turned by the jet lag (7 h = 105 degrees later). Adapted, the two splits would line up. In one colour the night
# arcs keep partial alpha, which themed icons and the status bar both honour.

NIGHT_ALPHA = "#5CFFFFFF"  # ~36 % white


def sky_ring(cx, cy, r, w, day_span, centre, gap):
    sep = math.degrees((gap + w) / r) / 2  # half the angular gap between the round-capped arcs
    d0, d1 = centre - day_span / 2, centre + day_span / 2
    day = thick_arc(cx, cy, r, w, d0 + sep, d1 - sep)
    night = thick_arc(cx, cy, r, w, d1 + sep, d0 + 360 - sep)
    return day, night


TURN = 105
DAY = 200
a_od, a_on = sky_ring(C, C, 24.5, 7.6, DAY, 0, 2.6)
a_id, a_in = sky_ring(C, C, 13.9, 7.6, DAY, TURN, 2.6)
n_od, n_on = sky_ring(N, N, 8.55, 2.9, DAY, 0, 1.1)
n_id, n_in = sky_ring(N, N, 4.35, 2.9, DAY, TURN, 1.1)
i_hi = pt(C, C, 14, TURN)
i_lo = pt(C, C, 14, TURN + 180)
NIGHT = linear(54, 28, 54, 80, [(0, "#FF2A2580"), (1, "#FF1B1857")])
emit(
    "skies",
    "A · Two skies",
    INDIGO_BG,
    [
        ("local_night", a_on, NIGHT),
        ("body_night", a_in, NIGHT),
        ("local_day", a_od, linear(54, 29, 54, 70, [(0, "#FFFFC93D"), (0.6, "#FFFFAA1A"), (1, "#FFFF8A3D")])),
        ("body_day", a_id, linear(i_hi[0], i_hi[1], i_lo[0], i_lo[1], [(0, "#FFD6EBFF"), (1, "#FF9FB4FF")])),
    ],
    [("local_night", a_on, NIGHT_ALPHA), ("body_night", a_in, NIGHT_ALPHA), ("local_day", a_od), ("body_day", a_id)],
    [("local_night", n_on, NIGHT_ALPHA), ("body_night", n_in, NIGHT_ALPHA), ("local_day", n_od), ("body_day", n_id)],
)
# Solid-only small icon: just the two day arcs (night is the gap), for hosts that flatten alpha.
write(
    "iconopt_skies_notif_solid.xml",
    vector("A · Two skies: solid-only status-bar icon (day arcs only).", path("local_day", n_od, WHITE) + path("body_day", n_id, WHITE), size=24),
)

# ------------------------------------------------------------------------------------------ B · Moon clock
# A clock ring with the sun riding on it (the time where you are) and a crescent moon inside (your body is still in
# its night). The ring is bitten around the sun so the two read apart in one colour; ring + sun also reads as a "C".


def bitten_two(cx, cy, r, w, b0, b1, h0, h1):
    """Ring stretch from bead b0 to bead b1 (clockwise) with different halo radii at each end."""
    ro, ri = r + w / 2, r - w / 2

    def delta(R, h):
        return math.degrees(math.acos((R * R + r * r - h * h) / (2 * R * r)))

    oa, ob = pt(cx, cy, ro, b0 + delta(ro, h0)), pt(cx, cy, ro, b1 - delta(ro, h1))
    ia, ib = pt(cx, cy, ri, b0 + delta(ri, h0)), pt(cx, cy, ri, b1 - delta(ri, h1))
    lo = 1 if ((b1 - delta(ro, h1)) - (b0 + delta(ro, h0))) % 360 > 180 else 0
    li = 1 if ((b1 - delta(ri, h1)) - (b0 + delta(ri, h0))) % 360 > 180 else 0
    return (
        f"M{fmt(oa[0])},{fmt(oa[1])}A{fmt(ro)},{fmt(ro)} 0,{lo} 1,{fmt(ob[0])},{fmt(ob[1])}"
        f"A{fmt(h1)},{fmt(h1)} 0,0 0,{fmt(ib[0])},{fmt(ib[1])}"
        f"A{fmt(ri)},{fmt(ri)} 0,{li} 0,{fmt(ia[0])},{fmt(ia[1])}"
        f"A{fmt(h0)},{fmt(h0)} 0,0 0,{fmt(oa[0])},{fmt(oa[1])}Z"
    )


def moon_clock(cx, cy, r, w, sun_r, halo, sun_at, moon_r, bite_r, bite_off, moon_shift):
    sx, sy = pt(cx, cy, r, sun_at)
    ring = bitten_two(cx, cy, r, w, sun_at, sun_at + 360, sun_r + halo, sun_r + halo)
    sun = circle(sx, sy, sun_r)
    mx, my = pt(cx, cy, moon_shift, sun_at + 180)
    moon = crescent(mx, my, moon_r, sun_at, bite_r, bite_off)  # opens toward the sun, like the familiar crescent
    return ring, sun, moon, (sx, sy)


SUN_AT = 45
m_ring, m_sun, m_moon, m_sp = moon_clock(C, C, 22.5, 6.0, 7.6, 3.0, SUN_AT, 10.5, 8.4, 5.6, 2.2)
q_ring, q_sun, q_moon, _ = moon_clock(N, N, 8.0, 2.4, 3.0, 1.2, SUN_AT, 3.9, 3.1, 2.1, 0.8)
emit(
    "moonclock",
    "B · Moon clock",
    INDIGO_BG,
    [
        ("clock", m_ring, linear(70, 30, 38, 78, [(0, "#FFFFD27A"), (0.35, "#FFF4E6FF"), (1, "#FFB8BCFF")])),
        ("sun", m_sun, linear(m_sp[0], m_sp[1] - 8, m_sp[0], m_sp[1] + 8, [(0, "#FFFFC93D"), (1, "#FFFF9A1F")])),
        ("moon", m_moon, linear(48, 44, 60, 66, [(0, "#FFFFFFFF"), (1, "#FFD9D4FF")])),
    ],
    [("clock", m_ring), ("sun", m_sun), ("moon", m_moon)],
    [("clock", q_ring), ("sun", q_sun), ("moon", q_moon)],
)

# ------------------------------------------------------------------------------------------ C · Horizon
# A sun cut along its horizon into day (marigold, above) and night (moonlight lilac, below), the halves slid out
# of line: the day you're in and the day your body expects. Jet lag as one solid shape.

SPLIT = 90  # horizontal cut
c_day = half_disc(C, C, 22.0, SPLIT, -1, -6.0, 4.4, 2.2)
c_night = half_disc(C, C, 22.0, SPLIT, +1, 6.0, 4.4, 2.2)
s_day = half_disc(N, N, 7.9, SPLIT, -1, -2.0, 1.9, 0.7)
s_night = half_disc(N, N, 7.9, SPLIT, +1, 2.0, 1.9, 0.7)
emit(
    "horizon",
    "C · Horizon",
    INDIGO_BG,
    [
        ("day", c_day, linear(54, 30, 54, 52, [(0, "#FFFFC93D"), (1, "#FFFF9A1F")])),
        ("night", c_night, linear(54, 55, 54, 76, [(0, "#FFE6E2FF"), (1, "#FFA9AEFF")])),
    ],
    [("day", c_day), ("night", c_night)],
    [("day", s_day), ("night", s_night)],
)

print("written to", OUT, file=sys.stderr)
