"""Pure-python reference implementation of Forger99 & Hannay19 (SP) with RK4.
Used to generate reference numbers for the circadian_science.md report."""
import math

# ---------------- Forger, Jewett & Kronauer 1999 ----------------
F99 = dict(taux=24.2, mu=0.23, G=33.75, alpha0=0.05, beta=0.0075, p=0.5, I0=9500.0, k=0.55)

def f99(t, s, lux, P=F99):
    x, xc, n = s
    alpha = P['alpha0'] * (lux / P['I0']) ** P['p'] if lux > 0 else 0.0
    Bhat = P['G'] * (1 - n) * alpha
    B = Bhat * (1 - 0.4 * x) * (1 - 0.4 * xc)
    dx = math.pi / 12 * (xc + B)
    dxc = math.pi / 12 * (P['mu'] * (xc - 4.0 / 3.0 * xc ** 3) - x * ((24.0 / (0.99669 * P['taux'])) ** 2 + P['k'] * B))
    dn = 60.0 * (alpha * (1 - n) - P['beta'] * n)
    return (dx, dxc, dn)

# ---------------- Hannay, Booth & Forger 2019 single population ----------------
H19 = dict(tau=23.84, K=0.06358, gamma=0.024, Beta1=-0.09318, A1=0.3855, A2=0.1977,
           BetaL1=-0.0026, BetaL2=-0.957756, sigma=0.0400692, G=33.75, alpha0=0.05,
           delta=0.0075, p=1.5, I0=9325.0)

def h19(t, s, lux, P=H19):
    R, psi, n = s
    lp = lux ** P['p'] if lux > 0 else 0.0
    alpha = P['alpha0'] * lp / (lp + P['I0'])
    Bhat = P['G'] * (1 - n) * alpha
    LR = (P['A1'] / 2 * Bhat * (1 - R ** 4) * math.cos(psi + P['BetaL1'])
          + P['A2'] / 2 * Bhat * R * (1 - R ** 8) * math.cos(2 * psi + P['BetaL2']))
    Lpsi = (P['sigma'] * Bhat
            - P['A1'] / 2 * Bhat * (R ** 3 + 1 / R) * math.sin(psi + P['BetaL1'])
            - P['A2'] / 2 * Bhat * (1 + R ** 8) * math.sin(2 * psi + P['BetaL2']))
    dR = -P['gamma'] * R + P['K'] * math.cos(P['Beta1']) / 2 * R * (1 - R ** 4) + LR
    dpsi = 2 * math.pi / P['tau'] + P['K'] / 2 * math.sin(P['Beta1']) * (1 + R ** 4) + Lpsi
    dn = 60.0 * (alpha * (1 - n) - P['delta'] * n)
    return (dR, dpsi, dn)

def rk4(f, s, t, dt, lux):
    k1 = f(t, s, lux)
    k2 = f(t + dt / 2, tuple(a + dt / 2 * b for a, b in zip(s, k1)), lux)
    k3 = f(t + dt / 2, tuple(a + dt / 2 * b for a, b in zip(s, k2)), lux)
    k4 = f(t + dt, tuple(a + dt * b for a, b in zip(s, k3)), lux)
    return tuple(a + dt / 6 * (b1 + 2 * b2 + 2 * b3 + b4) for a, b1, b2, b3, b4 in zip(s, k1, k2, k3, k4))

def simulate(model, light, days, dt=0.1, s0=None):
    """light(t_hours)->lux. returns list of (t, state)."""
    f = f99 if model == 'f99' else h19
    if s0 is None:
        s0 = (-0.0843259, -1.09607546, 0.45584306) if model == 'f99' else (0.82041911, 1.71383697, 0.52318122)
    s = s0; t = 0.0; out = [(t, s)]
    N = int(round(days * 24 / dt))
    for _ in range(N):
        s = rk4(f, s, t, dt, light(t))  # light sampled at step start (piecewise-constant)
        t += dt
        out.append((t, s))
    return out

def cbtmins(model, traj):
    """Times of CBTmin: F99 -> local minima of x; H19 -> times where psi crosses pi (mod 2pi)."""
    res = []
    if model == 'f99':
        for i in range(1, len(traj) - 1):
            a, b, c = traj[i - 1][1][0], traj[i][1][0], traj[i + 1][1][0]
            if b < a and b <= c:
                # parabolic interpolation
                t0 = traj[i][0]; dt = traj[i + 1][0] - t0
                denom = a - 2 * b + c
                off = 0.5 * (a - c) / denom if denom != 0 else 0
                res.append((t0 + off * dt, b))
        # greedy de-duplication like scipy find_peaks(distance=13h): keep deepest minima first
        res.sort(key=lambda p: p[1])
        kept = []
        for t_, v_ in res:
            if all(abs(t_ - k) >= 13.0 for k in kept):
                kept.append(t_)
        res = sorted(kept)
    else:
        for i in range(len(traj) - 1):
            p0 = traj[i][1][1]; p1 = traj[i + 1][1][1]
            k0 = math.floor((p0 - math.pi) / (2 * math.pi)); k1 = math.floor((p1 - math.pi) / (2 * math.pi))
            if k1 > k0:
                target = math.pi + 2 * math.pi * k1
                fr = (target - p0) / (p1 - p0)
                res.append(traj[i][0] + fr * (traj[i + 1][0] - traj[i][0]))
    return res

def schedule(wake, sleep, lux_wake, lux_sleep=0.0, shift_at=None, shift=0.0, extra=None):
    """wake/sleep: clock hours (home time). After t>=shift_at (hours), clock is shifted by `shift` hours
    (positive = eastward: local clock is ahead, so local 07:00 occurs `shift` hours earlier in home time).
    extra(t_local_day_hour, day) -> lux override or None."""
    def L(t):
        tl = t
        if shift_at is not None and t >= shift_at:
            tl = t + shift
        h = tl % 24.0
        day = int(tl // 24)
        if extra is not None:
            v = extra(h, day, t)
            if v is not None:
                return v
        awake = (h >= wake and h < sleep) if wake < sleep else (h >= wake or h < sleep)
        return lux_wake if awake else lux_sleep
    return L
