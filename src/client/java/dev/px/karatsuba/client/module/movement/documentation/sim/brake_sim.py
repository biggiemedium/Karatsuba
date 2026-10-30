# Keys let go mid-hop: coast (Grim now: no keys, no turn) vs brake (face against velocity, report W, sprint off) vs Vanilla Strafe (zeroed)
import math
AIR_T = 11
def run(mode, phase):
    v = 0.0; x = 0.0; air = 0; warm = 48 + phase
    for t in range(warm + 40):
        ground = air == 0
        if t < warm:
            jump = ground
            if jump: v += 0.2
            acc = 0.13 if ground else 0.026
        else:
            jump = False
            if mode == 'vanilla': v = 0.0; acc = 0.0
            elif mode == 'coast': acc = 0.0
            else: acc = -(0.1 if ground else 0.02) if v > 0.0 else 0.0  # braking walks back (no sprint), stop at zero
        m = v + acc
        if mode == 'brake' and t >= warm and (v > 0) != (m > 0): m = 0.0
        x += m; v = m * (0.546 if ground else 0.91)
        air = AIR_T if jump else max(0, air-1)
        if t == warm - 1: x0 = x
    return x - x0
for mode in ['vanilla', 'coast', 'brake']:
    d = [run(mode, p) for p in range(12)]
    print(f"{mode:8s} slide after letting go: avg {sum(d)/12:4.2f}  worst {max(d):4.2f} blocks")
