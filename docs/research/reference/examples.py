from planner import *
EX = {
 'SFO-LHR (2026-06-15, +8h)': dict(home_off=-7, legs=[Leg(23.5, -7, 33.75, +1)]),
 'LHR-SIN-SYD (2026-11-10, +11h)': dict(home_off=0, legs=[Leg(21.0, 0, 33.5, +8), Leg(36.0, +8, 43.5, +11)]),
 'JFK-LAX (2026-03-20, -3h)': dict(home_off=-4, legs=[Leg(12.0, -4, 18.33, -7)]),
 'NRT-JFK (2026-01-15, -14h)': dict(home_off=+9, legs=[Leg(2.0, +9, 15.5, -5)]),
}
if __name__ == '__main__':
    import sys
    chrono = sys.argv[1] if len(sys.argv) > 1 else 'neutral'
    for name, e in EX.items():
        r = plan(e['home_off'], 23.0, 7.0, chrono, e['legs'])
        print('=====', name, chrono)
        print(describe(r, e['home_off'], e['legs'][-1].arr_off))
