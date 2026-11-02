# Horizontal speed while using an item, under Grim's NoSlow/SprintC latch:
# never two fast slowed-ticks in a row. Vanilla 1.21.4 physics, 1D along the
# heading, flat normal ground, forward held. See modules/NO_SLOW.md.
#
#   py sim/noslow_sim.py

SLIP = 0.6 * 0.91              # ground friction after the move
GROUND = 0.21600002 / 0.6**3   # vanilla cubes the block slipperiness
WALK = 0.2159                  # blocks a tick, walking with no item (checked below)


def run(policy, sprint_on_fast, jump, ticks=4000):
    v = 0.0
    ground = True
    air = 0
    owed = 0
    dist = 0.0
    n = 0
    for t in range(ticks):
        fast = policy(ground) and owed == 0
        owed = 1 if fast else 0
        sprint = sprint_on_fast and fast
        inp = 0.98 * (1.0 if fast else 0.2)
        was_ground = ground                  # the jump tick is still a ground tick
        if ground and jump and (fast or policy is NEVER):
            if sprint:
                v += 0.2                     # sprint-jump boost: not scaled by the item
            ground = False
            air = 11
        if was_ground:
            v += inp * 0.1 * (1.3 if sprint else 1.0) * GROUND
            move = v
            v *= SLIP
        else:
            v += inp * (0.026 if sprint else 0.02)
            move = v
            v *= 0.91
            air -= 1
            if air == 0:
                ground = True
        if t >= ticks // 2:
            dist += move
            n += 1
    return dist / n


ALWAYS = lambda ground: True
NEVER = lambda ground: False
GROUND_ONLY = lambda ground: ground

rows = [
    ("Vanilla eating, walking", NEVER, False, False),
    ("Alternate, walking (current Grim mode)", ALWAYS, False, False),
    ("Alternate, jumping", ALWAYS, False, True),
    ("Alternate + sprint on fast ticks, walking", ALWAYS, True, False),
    ("Alternate + sprint, sprint-jumping", ALWAYS, True, True),
    ("  same, but slowed in the air", GROUND_ONLY, True, True),
]
refs = [
    ("No item: walking", ALWAYS, False, False, True),
    ("No item: sprinting", ALWAYS, True, False, True),
    ("No item: sprint-jumping", ALWAYS, True, True, True),
]

for name, policy, sprint, jump in rows:
    s = run(policy, sprint, jump)
    print(f"{name:44s} {s * 20:5.2f} b/s  {s / WALK:4.2f}x walk")
print()
for name, policy, sprint, jump, _ in refs:
    # No item: every tick is fast and nothing is owed.
    v = 0.0
    s = 0.0
    ground, air = True, 0
    for t in range(4000):
        was_ground = ground
        if ground and jump:
            if sprint:
                v += 0.2
            ground, air = False, 11
        if was_ground:
            v += 0.98 * 0.1 * (1.3 if sprint else 1.0) * GROUND
            move = v
            v *= SLIP
        else:
            v += 0.98 * (0.026 if sprint else 0.02)
            move = v
            v *= 0.91
            air -= 1
            if air == 0:
                ground = True
        if t >= 2000:
            s += move
    s /= 2000
    print(f"{name:44s} {s * 20:5.2f} b/s  {s / WALK:4.2f}x walk")
