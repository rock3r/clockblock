import sys, math
from sim import *

def entrain(model, L, days=40, dt=0.1):
    tr = simulate(model, L, days, dt)
    return tr[-1][1]

def wrap(h):
    return (h + 12) % 24 - 12

def run(model, shift, wake=7, sleep=23, lux_wake=250, outdoor=None, dt=0.1, days_after=25, plan=None):
    """Home entrained to wake..sleep; at t=0 (home midnight) jump to destination with clock shift `shift`
    (east positive). Traveller then sleeps wake..sleep local time. returns (days_to_adapt, direction, daily cbt list)."""
    def mk(shift_at, sh):
        def extra(h, day, t):
            if plan is not None and shift_at is not None and t >= shift_at:
                v = plan(h, day, t)
                if v is not None:
                    return v
            if outdoor is not None:
                awake = wake <= h < sleep
                if awake and outdoor[0] <= h < outdoor[1]:
                    return outdoor[2]
            return None
        return schedule(wake, sleep, lux_wake, 0.0, shift_at=shift_at, shift=sh, extra=extra)
    s0 = entrain(model, mk(None, 0), 40, dt)
    tr0 = simulate(model, mk(None, 0), 3, dt, s0)
    base = cbtmins(model, tr0)[-1] % 24      # home clock CBTmin
    T0 = 72.0
    tr = simulate(model, mk(T0, shift), 3 + days_after, dt, s0)
    cb = cbtmins(model, tr)
    # in destination local time, target CBTmin clock = base
    res = []
    for c in cb:
        if c < T0: continue
        local = (c + shift) % 24
        err = wrap(local - base)     # >0: body clock CBTmin later than target (needs advance)
        res.append((round((c - T0) / 24, 2), round(local, 2), round(err, 2)))
    adapt = None
    for i, (d, l, e) in enumerate(res):
        if all(abs(x[2]) <= 1.0 for x in res[i:i + 3]):
            adapt = d; break
    # direction: cumulative movement of CBTmin in home-clock terms over first days
    hom = [c for c in cb if c >= T0 - 24]
    moves = [hom[i + 1] - hom[i] - 24 for i in range(len(hom) - 1)]
    total = sum(moves)
    direction = ('delay' if total > 0 else 'advance') + f' (net {total:+.1f}h)'
    return base, adapt, direction, res

if __name__ == '__main__':
    model = sys.argv[1]
    for lw, od in [(250, None), (250, (9, 17, 3000))]:
        print('#### model', model, 'lux_wake', lw, 'outdoor', od)
        for sh in [-12, -10, -9, -8, -6, -3, 3, 6, 8, 9, 10, 11, 12]:
            base, adapt, d, res = run(model, sh, lux_wake=lw, outdoor=od)
            print(f'shift {sh:+3d}h  base CBTmin {base:5.2f}  days_to_adapt(|err|<=1h) {adapt}  direction {d}  first errs {[r[2] for r in res[:8]]}')
