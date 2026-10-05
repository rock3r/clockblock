import copy, sys
from planner import *
from validate import validate

def sweep(models=('f99', 'h19'), light_box=False, dep_local=21.0, dur=12.0):
    print(f'#### light_box={light_box} dep_local={dep_local} flight={dur}h   (adapt days after arrival; plan / noplan)')
    for A in [6, 7, 8, 9, 10, 11, 12]:
        row = [f'east {A:2d}h']
        for direction in ('advance', 'delay'):
            Pm = copy.deepcopy(P)
            th = 12.5 if direction == 'advance' else -1.0
            Pm['ADV_THRESHOLD'] = {'neutral': th, 'early': th, 'late': th}
            dep = 24.0 + dep_local  # home tz UTC+0
            legs = [Leg(dep, 0.0, dep + dur, float(A))]
            r = plan(0.0, 23.0, 7.0, 'neutral', legs, light_box=light_box, P=Pm)
            for m in models:
                v = validate(m, r, 0.0, float(A), dep, dep + dur, light_box=light_box, days_after=16)
                a = v['plan']['adapt_days']; n = v['noplan']['adapt_days']
                row.append(f"{direction[:3]}-{m}: {('%.1f' % a) if a is not None else '>16'} (net {v['plan']['net']:+.0f})")
                if direction == 'advance':
                    row.append(f"noplan-{m}: {('%.1f' % n) if n is not None else '>16'} (net {v['noplan']['net']:+.0f})")
        print(' | '.join(row), flush=True)

if __name__ == '__main__':
    lb = len(sys.argv) > 1 and sys.argv[1] == 'box'
    dl = float(sys.argv[2]) if len(sys.argv) > 2 else 21.0
    sweep(light_box=lb, dep_local=dl)
