"""Validate plans with Forger99 / Hannay19: convert plan to lux(t), simulate, read CBTmin per cycle."""
import math, sys
from planner import *
from sim import f99, h19, rk4, cbtmins

LUX = dict(sleep=0.0, avoid=10.0, seek_day=3000.0, seek_night_box=5000.0, seek_night=500.0,
           neutral=250.0, outdoor=3000.0, flight=100.0, flight_seek=1000.0, flight_avoid=5.0)

def typical_day(local_h, onset=23.0, wake=7.0):
    h = local_h % 24
    asleep = (h >= onset or h < wake) if onset > wake else (onset <= h < wake)
    if asleep: return LUX['sleep']
    if 12.0 <= h < 14.0: return LUX['outdoor']
    return LUX['neutral']

def in_any(t, ivs):
    return any(a <= t < b for a, b in ivs)

def plan_lux(res, home_off, dest_off, dep, arr, light_box=False):
    cyc = res['cycles']
    start = cyc[0].T - 12.0
    end = cyc[-1].T + 12.0
    sleeps = [p for c in cyc for p in c.sleep_parts]
    seeks = [p for c in cyc for p in c.seek]
    avoids = [p for c in cyc for p in c.avoid]
    def L(t):
        if t < start:
            return typical_day(t + home_off)
        if t >= end:
            return typical_day(t + dest_off)
        off = home_off if t < dep else dest_off
        h = (t + off) % 24
        inflight = dep <= t < arr
        if in_any(t, sleeps): return LUX['sleep']
        if in_any(t, avoids): return LUX['flight_avoid'] if inflight else LUX['avoid']
        if in_any(t, seeks):
            if inflight: return LUX['flight_seek']
            if 7.0 <= h < 19.0: return LUX['seek_day']
            return LUX['seek_night_box'] if light_box else LUX['seek_night']
        if inflight: return LUX['flight']
        if 12.0 <= h < 14.0: return LUX['outdoor']
        return LUX['neutral']
    return L

def noplan_lux(home_off, dest_off, dep, arr):
    def L(t):
        if t < dep: return typical_day(t + home_off)
        if t < arr: return LUX['flight']
        return typical_day(t + dest_off)
    return L

def simulate_L(model, L, t0, t1, dt=0.1):
    f = f99 if model == 'f99' else h19
    s = (-0.0843259, -1.09607546, 0.45584306) if model == 'f99' else (0.82041911, 1.71383697, 0.52318122)
    # entrain 40 days on the light function evaluated with a 24h-periodic pre-history
    t = t0 - 24.0 * 40; tr = []
    while t < t1:
        s = rk4(f, s, t, dt, L(t)); t += dt
        if t >= t0 - 72: tr.append((t, s))
    return tr

def validate(model, res, home_off, dest_off, dep, arr, light_box=False, days_after=14):
    t0 = dep - 24.0 * 5
    t1 = arr + 24.0 * days_after
    # model's own baseline CBTmin clock time (home, typical day)
    trb = simulate_L(model, lambda t: typical_day(t + home_off), t0, t0 + 72)
    base_clock = (cbtmins(model, trb)[-1] + home_off) % 24
    out = {}
    for name, L in (('plan', plan_lux(res, home_off, dest_off, dep, arr, light_box)), ('noplan', noplan_lux(home_off, dest_off, dep, arr))):
        tr = simulate_L(model, L, t0, t1)
        cb = [c for c in cbtmins(model, tr) if c > t0]
        rows = []
        for c in cb:
            loc = (c + (home_off if c < dep else dest_off)) % 24
            err = norm12(((c + dest_off) % 24) - base_clock)  # vs destination target
            rows.append((c, loc, err))
        adapt = None
        for i, (c, loc, e) in enumerate(rows):
            if c < arr: continue
            if all(abs(r[2]) <= 1.0 for r in rows[i:i + 3]) and len(rows[i:i + 3]) == 3:
                adapt = (c - arr) / 24.0; break
        # net movement (home clock) to tell direction
        moves = [rows[i + 1][0] - rows[i][0] - 24 for i in range(len(rows) - 1)]
        out[name] = dict(rows=rows, adapt_days=adapt, net=sum(moves))
    out['base_clock'] = base_clock
    return out

if __name__ == '__main__':
    from examples import EX
    chrono = sys.argv[1] if len(sys.argv) > 1 else 'neutral'
    models = sys.argv[2].split(',') if len(sys.argv) > 2 else ['f99', 'h19']
    for name, e in EX.items():
        legs = e['legs']; ho = e['home_off']; do = legs[-1].arr_off
        r = plan(ho, 23.0, 7.0, chrono, legs)
        if r['mode'] != 'plan': print(name, r); continue
        for m in models:
            v = validate(m, r, ho, do, legs[0].dep, legs[-1].arr)
            print(f"== {name} [{chrono}] model={m} dir={r['direction']} target_phi={r['target_phi']:+.0f} model_base_CBTmin={fmt(v['base_clock'])}")
            for k in ('plan', 'noplan'):
                rows = v[k]['rows']
                post = [r_ for r_ in rows if r_[0] >= legs[-1].arr - 24]
                print(f"   {k:6s}: days_to_adapt(|err|<=1h x3)={v[k]['adapt_days'] if v[k]['adapt_days'] is None else round(v[k]['adapt_days'],1)}"
                      f" net_shift={v[k]['net']:+.1f}h  err_by_cycle={[round(r_[2],1) for r_ in post[:10]]}")
