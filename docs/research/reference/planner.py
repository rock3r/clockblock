"""Reference planner for Opus Clockblock (rule-based, PRC-driven) + ODE validation.
All internal times are UTC hours since t=0 (00:00 UTC of the reference date). Offsets are UTC offsets in hours.
This is a *reference implementation* used to generate fixtures for circadian_science.md."""
import math
from dataclasses import dataclass, field

P = dict(
    PRE_ADV_CAP=1.0, PRE_DEL_CAP=1.5, PRE_DEL_CAP_LIGHTBOX=2.0,
    POST_ADV_CAP=1.5, POST_DEL_CAP=2.0,
    ADV_THRESHOLD={'early': 10.0, 'neutral': 9.0, 'late': 8.0},
    CBT_FROM_MID={'early': 0.5, 'neutral': 1.0, 'late': 1.5},
    SEEK_LEN=6.0, AVOID_LEN=8.0,  # hours after/before CBTmin (advance) or before/after (delay)
    MAX_SLEEP_DEV=3.0,            # post-arrival sleep onset may deviate +/- this from local habitual onset
    MEL_ADV_OFFSET=-9.0,          # 0.5 mg: 9 h before CBTmin (= DLMO - 2 h)
    MEL_ADV_WINDOW=(-13.0, -7.0),  # acceptable window relative to CBTmin
    CAF_CUTOFF=6.0, CAF_CUTOFF_ADV=8.0,
    NAP_MAX_MIN=30, NAP_MIN_BEFORE_SLEEP=6.0,
    SHORT_TRIP_H=72.0, PRE_MAX_SHIFT=3.0, PRE_DEP_WAKE=3.0, ARR_SLEEP_END=0.75, MIN_SHIFT=2.0, DONE_TOL=0.5,
)

def norm12(x):
    """normalise to (-12, 12]"""
    y = math.fmod(x + 12.0, 24.0)
    if y <= 0: y += 24.0
    return y - 12.0

def mod24(x):
    return x % 24.0

def fmt(h):
    h = mod24(h); m = int(round(h * 60)) % 1440
    return f"{m // 60:02d}:{m % 60:02d}"

@dataclass
class Leg:
    dep: float; dep_off: float; arr: float; arr_off: float

@dataclass
class Cycle:
    k: int; T: float; phi: float; where: str; off: float
    sleep: tuple = None; sleep_parts: list = None; seek: list = field(default_factory=list); avoid: list = field(default_factory=list)
    melatonin: float = None; caffeine_ok: tuple = None; caffeine_use: list = field(default_factory=list)
    nap: tuple = None; notes: list = field(default_factory=list)

def subtract(iv, cuts):
    out = [iv]
    for c in cuts:
        nxt = []
        for a, b in out:
            if c[1] <= a or c[0] >= b: nxt.append((a, b)); continue
            if a < c[0]: nxt.append((a, c[0]))
            if c[1] < b: nxt.append((c[1], b))
        out = nxt
    return [x for x in out if x[1] - x[0] > 1e-6]

def plan(home_off, hab_onset, hab_wake, chrono, legs, use_mel=True, use_caf=True, pre_days=3, light_box=False, P=P):
    dep, arr = legs[0].dep, legs[-1].arr
    dest_off = legs[-1].arr_off
    delta = norm12(dest_off - home_off)
    out = dict(delta=delta)
    if abs(delta) < P['MIN_SHIFT']:
        out['mode'] = 'no_plan'; return out
    A = delta % 24.0
    direction = 'advance' if A <= P['ADV_THRESHOLD'][chrono] else 'delay'
    target = A if direction == 'advance' else A - 24.0      # phi* : +advance hours / -delay hours
    out.update(direction=direction, target_phi=target)
    sd = (hab_wake - hab_onset) % 24.0
    mid = hab_onset + sd / 2.0
    T0_clock = mod24(mid + P['CBT_FROM_MID'][chrono])       # home clock
    psi_on = (T0_clock - hab_onset) % 24.0                    # CBTmin - sleep onset (h)
    psi_wake = (hab_wake - T0_clock) % 24.0
    out.update(T0_home_clock=T0_clock, psi_on=psi_on, psi_wake=psi_wake, sleep_dur=sd)
    # first CBTmin (UTC) on the day pre_days before departure
    T = T0_clock - home_off
    while T < dep - 24.0 * (pre_days + 1): T += 24.0
    while T > dep - 24.0 * pre_days: T -= 24.0
    sgn = 1.0 if target > 0 else -1.0
    # practicality (delay only): total pre-flight delay limited so that the last pre-departure wake <= dep - PRE_DEP_WAKE
    pre_cap_total = P['PRE_MAX_SHIFT']
    if sgn < 0:
        Tl = T
        while Tl + 24.0 < dep: Tl += 24.0
        pre_cap_total = max(0.0, min(pre_cap_total, (dep - P['PRE_DEP_WAKE']) - (Tl + psi_wake)))
    out['pre_cap_total'] = pre_cap_total
    phi = 0.0; cycles = []; k = 0
    while k < 30:
        where = 'home' if T < dep else ('flight' if T < arr else 'dest')
        off = home_off if where == 'home' else (dest_off if where == 'dest' else None)
        c = Cycle(k, T, phi, where, off if off is not None else dest_off)
        # --- sleep window
        on_al, wk_al = T - psi_on, T + psi_wake
        if where == 'home' or (where == 'flight'):
            c.sleep = (on_al, wk_al)
        else:
            loc_on_hab = hab_onset                               # habitual onset, local clock
            on_loc = mod24(on_al + dest_off)
            dev = norm12(on_loc - loc_on_hab)
            dev_c = max(-P['MAX_SLEEP_DEV'], min(P['MAX_SLEEP_DEV'], dev))
            on = on_al - dev + dev_c
            c.sleep = (on, on + sd)
            if abs(dev - dev_c) > 1e-6: c.notes.append(f'sleep clamped by {dev - dev_c:+.1f}h')
        # sleep is blocked during airport transit: [dep - PRE_DEP_WAKE, dep + 0.5] and [arr - ARR_SLEEP_END, arr + 1.5]
        s0, s1 = c.sleep
        parts = subtract((s0, s1), [(dep - P['PRE_DEP_WAKE'], dep + 0.5), (arr - P['ARR_SLEEP_END'], arr + 1.5)])
        if parts and abs(sum(b - a for a, b in parts) - (s1 - s0)) > 1e-6:
            c.notes.append('sleep split/truncated by travel: ' + ','.join(f'{a:.2f}-{b:.2f}' for a, b in parts))
        c.sleep_parts = parts if parts else [(s0, s0)]
        c.sleep = (c.sleep_parts[0][0], c.sleep_parts[-1][1]) if parts else (s0, s0)
        # --- light windows
        if direction == 'advance':
            seek = (T, T + P['SEEK_LEN']); avoid = (T - P['AVOID_LEN'], T)
        else:
            seek = (T - P['SEEK_LEN'], T); avoid = (T, T + P['AVOID_LEN'])
        c.seek = subtract(seek, c.sleep_parts); c.avoid = subtract(avoid, c.sleep_parts)
        # --- melatonin (advance only)
        if use_mel and direction == 'advance' and abs(target - phi) > P['DONE_TOL']:
            m = T + P['MEL_ADV_OFFSET']
            lo, hi = T + P['MEL_ADV_WINDOW'][0], T + P['MEL_ADV_WINDOW'][1]
            prev_sleep = cycles[-1].sleep if cycles else None
            asleep = lambda t: (c.sleep[0] <= t < c.sleep[1]) or (prev_sleep and prev_sleep[0] <= t < prev_sleep[1])
            if asleep(m):
                if lo <= c.sleep[0] <= hi: m = c.sleep[0] - 0.25
                else: m = None
            c.melatonin = m
        # --- caffeine: allowed from wake (previous sleep end) until cutoff before this cycle's sleep onset?
        cut = P['CAF_CUTOFF_ADV'] if direction == 'advance' else P['CAF_CUTOFF']
        nxt_on = c.sleep[0] if c.sleep[0] > T else c.sleep[0] + 24  # sleep that follows the waking day
        c.caffeine_ok = (c.sleep[1], None)  # filled after next cycle known
        cycles.append(c)
        if abs(target - phi) <= P['DONE_TOL'] and where == 'dest':
            break
        # --- advance phi for next cycle
        if T < dep:
            in_pre = True
            cap = P['PRE_ADV_CAP'] if direction == 'advance' else (P['PRE_DEL_CAP_LIGHTBOX'] if light_box else P['PRE_DEL_CAP'])
            if pre_days == 0: cap = 0.0
            cap = min(cap, max(0.0, pre_cap_total - abs(phi)))
            # practicality: if the next cycle is still pre-departure, its aligned wake must be <= dep - PRE_DEP_WAKE
            Tn = T + 24.0 - sgn * cap
            if Tn < dep and sgn < 0:
                latest_wake = dep - P['PRE_DEP_WAKE']
                if Tn + psi_wake > latest_wake:
                    cap = max(0.0, cap - ((Tn + psi_wake) - latest_wake))
        else:
            cap = P['POST_ADV_CAP'] if direction == 'advance' else P['POST_DEL_CAP']
        step = sgn * min(cap, abs(target - phi))
        phi += step
        T = T + 24.0 - step
        k += 1
    # caffeine windows: between wake of cycle i and sleep onset of cycle i+1 minus cutoff
    cut = P['CAF_CUTOFF_ADV'] if direction == 'advance' else P['CAF_CUTOFF']
    for i in range(len(cycles) - 1):
        w = cycles[i].sleep[1]; s = cycles[i + 1].sleep[0]
        cycles[i].caffeine_ok = (w, s - cut) if s - cut > w else None
        # 'use caffeine' when awake during body-night [T-7, T+3] of either cycle
        uses = []
        for c2 in (cycles[i], cycles[i + 1]):
            bn = (c2.T - 7.0, c2.T + 3.0)
            a, b = max(bn[0], w), min(bn[1], s - cut)
            if b - a > 0.25: uses.append((a, b))
        cycles[i].caffeine_use = uses
        # nap: one <=30 min nap, >= NAP_MIN_BEFORE_SLEEP before next sleep, not inside a seek window
        lo, hi = w + 1.0, s - P['NAP_MIN_BEFORE_SLEEP']
        if hi - lo > 0.5:
            seeks = cycles[i].seek + cycles[i + 1].seek
            avoid = cycles[i].avoid + cycles[i + 1].avoid
            cand = None
            for a, b in sorted(avoid):
                a2, b2 = max(a, lo), min(b, hi)
                if b2 - a2 >= 0.5: cand = (a2, a2 + 0.5); break
            if cand is None and direction == 'delay':
                # siesta ~7-8 h after wake when delaying (Eastman & Burgess 2009)
                t = w + 7.0
                if lo <= t <= hi and not any(a <= t < b for a, b in seeks): cand = (t, t + 0.5)
            cycles[i].nap = cand
    out['cycles'] = cycles
    out['mode'] = 'plan'
    return out

def describe(res, home_off, dest_off):
    lines = [f"delta={res['delta']:+.1f}h mode={res['mode']}"]
    if res['mode'] != 'plan': return '\n'.join(lines)
    lines[0] += f" direction={res['direction']} target_phi={res['target_phi']:+.1f} T0(home clock)={fmt(res['T0_home_clock'])}"
    for c in res['cycles']:
        off = home_off if c.where == 'home' else dest_off
        tag = 'home' if c.where == 'home' else ('dest' if c.where == 'dest' else 'flight(dest clock)')
        L = lambda t: fmt(t + off)
        s = (f" k={c.k:2d} {tag:18s} phi={c.phi:+5.1f} CBTmin={L(c.T)} sleep={L(c.sleep[0])}-{L(c.sleep[1])}"
             f" seek={[L(a)+'-'+L(b) for a,b in c.seek]} avoid={[L(a)+'-'+L(b) for a,b in c.avoid]}")
        if c.melatonin is not None: s += f" mel={L(c.melatonin)}"
        if c.caffeine_ok and c.caffeine_ok[1] is not None: s += f" caf_ok={L(c.caffeine_ok[0])}-{L(c.caffeine_ok[1])}"
        if c.nap: s += f" nap={L(c.nap[0])}"
        if c.notes: s += ' ' + ';'.join(c.notes)
        lines.append(s)
    return '\n'.join(lines)
