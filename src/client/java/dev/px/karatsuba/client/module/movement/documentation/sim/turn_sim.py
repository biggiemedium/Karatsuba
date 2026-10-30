# Horizontal 1.21.4 sprint physics on stone. jump tick: v += 0.2*yaw; ground: move=v+0.13u, v=move*0.546; air: move=v+0.026u, v=move*0.91
import math
AIR_T = 11
def unit(x, z):
    l = math.hypot(x, z); return (x/l, z/l) if l > 1e-9 else (0.0, 0.0)
def run(policy, jumpmode, d, phase, ticks=30):
    vx = vz = 0.0; px = pz = 0.0; air = 0; warm = 48 + phase; o = []
    for t in range(warm + ticks):
        dd = (1.0, 0.0) if t < warm else d
        ground = air == 0
        if policy == 'vanilla':
            sp = math.hypot(vx, vz); vx, vz = dd[0]*sp, dd[1]*sp; u = dd
        elif policy == 'optimal' and t >= warm:
            ex, ez = dd[0]*0.36 - vx, dd[1]*0.36 - vz
            u = unit(ex, ez)
        else:
            u = dd
        jump = ground
        if t >= warm and jumpmode == 'none': jump = False
        if t >= warm and jumpmode == 'smart' and ground:
            sp = math.hypot(vx, vz)
            if sp > 0.05 and (vx*dd[0] + vz*dd[1]) / sp < 0.7: jump = False
        if jump: vx += 0.2*u[0]; vz += 0.2*u[1]
        acc, fr = (0.13, 0.546) if ground else (0.026, 0.91)
        mx, mz = vx + acc*u[0], vz + acc*u[1]
        px += mx; pz += mz; vx, vz = mx*fr, mz*fr
        air = AIR_T if jump else max(0, air-1)
        if t == warm - 1: x0 = px
        if t >= warm: o.append((t-warm, px - x0, vx*d[0] + vz*d[1]))
    reach = next((r[0] for r in o if r[2] >= 0.15), 99)
    over = max(0.0, max(r[1] for r in o))
    gone = o[19][1]*d[0] + (0 if d[0] else 0)  # distance along new way after 20 ticks (x only for reversal)
    return reach, over
cases = [("Vanilla Strafe", 'vanilla', 'auto'), ("Grim now (face keys, auto jump)", 'keys', 'auto'),
         ("+ aim at velocity error", 'optimal', 'auto'), ("+ smart jump", 'keys', 'smart'),
         ("+ both", 'optimal', 'smart'), ("stop jumping after the switch", 'keys', 'none')]
for label, d in [("REVERSE (A -> D)", (-1.0, 0.0)), ("90 deg TURN", (0.0, -1.0))]:
    print(label, "  [average / worst over the 12 phases of a hop]")
    for name, pol, jm in cases:
        rs = [run(pol, jm, d, ph) for ph in range(12)]
        r = [x[0] for x in rs]; ov = [x[1] for x in rs]
        print(f"  {name:32s} ticks to 0.15 b/t new way: avg {sum(r)/12:4.1f} worst {max(r):2d}   overshoot: avg {sum(ov)/12:4.2f} worst {max(ov):4.2f} blocks")
