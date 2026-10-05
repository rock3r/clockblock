from sim import *
def L(t):
    h = round(t, 9) % 24
    if h >= 23 or h < 7: return 0.0
    if 12 <= h < 14: return 3000.0
    return 250.0
def sim2(m, days, dt):
    f = f99 if m == 'f99' else h19
    s = (-0.0843259, -1.09607546, 0.45584306) if m == 'f99' else (0.82041911, 1.71383697, 0.52318122)
    out = [(0.0, s)]
    N = int(round(days * 24 / dt))
    for i in range(N):
        t = i * dt
        s = rk4(f, s, t, dt, L(t))
        out.append(((i + 1) * dt, s))
    return out
for m in ('f99', 'h19'):
    for dt in (0.25, 0.1, 0.05, 0.02, 0.01):
        tr = sim2(m, 60, dt)
        print(m, dt, round(cbtmins(m, tr)[-1] % 24, 3))
# stability at 100k lux for F99
for dt in (0.5, 0.25, 0.1):
    s = (-0.0843259, -1.09607546, 0.45584306)
    for i in range(int(48 / dt)):
        s = rk4(f99, s, i * dt, dt, 100000.0)
    print('f99 100k lux dt', dt, 'n=', s[2])
