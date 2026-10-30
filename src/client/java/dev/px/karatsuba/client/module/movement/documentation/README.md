# Movement documentation

What we know about the movement modules, and how each one stands. Read the
relevant files before changing a module, and update them when a test settles
something.

## Where things are

| Folder | Holds |
|---|---|
| [`modules/`](modules/) | One file per module: how it works, its settings and decisions, what testing found, and its TODOs |
| [`knowledge/`](knowledge/) | What holds for every movement module, by topic |
| [`sim/`](sim/) | Python simulations of vanilla movement behind numbers in the docs (`py sim/turn_sim.py`) |

**`knowledge/`**

| File | Read it when |
|---|---|
| [TICK_ORDER.md](knowledge/TICK_ORDER.md) | a module reads or changes anything in the tick: which event fires where, and what is still last tick's there |
| [KEYS.md](knowledge/KEYS.md) | a module presses, holds or rewrites a key: `TickKey`, `Inputs`, the keys Grim is told about |
| [MOVE_FIX.md](knowledge/MOVE_FIX.md) | a module turns the head silently while the player walks: Move Fix, STRICT, exact yaws, priorities |
| [MOTION.md](knowledge/MOTION.md) | a module touches velocity or position: `Services.motion()`, `Displacement`, and which edits Grim forgives |
| [ELYTRA.md](knowledge/ELYTRA.md) | anything opens, flies or boosts an elytra: glide physics, Grim's take-off, sprint and firework rules |
| [PACKET_HOLD.md](knowledge/PACKET_HOLD.md) | anything holds packets back: `PacketHold`, holding everything, TimerLimit, bursts |
| [SITUATIONS.md](knowledge/SITUATIONS.md) | always: the states a movement module has to stand down in (gliding, water, riding, …) |
| [DEBUG_LOGGING.md](knowledge/DEBUG_LOGGING.md) | you need to find out why: what a movement log records, on top of the combat logger |

The Grim rulebook is `client/anticheat/Grim.md`. Shared movement code is in
`movement/control/` (`TickKey`, `Ledges`).

## Hardening a module

Begun 2026-10-30, after the combat modules: make each movement module more
reliable, and safe on Grim by construction.

1. **Measure first.** Add a temporary debug log ([DEBUG_LOGGING.md](knowledge/DEBUG_LOGGING.md))
   and test on the dev server (`gradlew runServer`, Grim 2.3.73). A setback is a
   `ServerTeleportEvent`; log Grim's alerts too.
2. **Read it** with awk: setbacks by situation and by setting, the keys reported
   against the yaw sent, ticks where the module acted and should not have.
3. **Fix what the log shows,** against the checklist below.
4. **Write it up** in the module's file in `modules/`. Put anything that holds
   beyond that module into `knowledge/`.
5. **Delete the debug log.**

### Checklist

1. Is every key a real key, pressed at `TickEvent` PRE and released by tick end,
   or written through an `Inputs` seam after `Input.tick()`? No `jump()`, no faked
   sneak. ([KEYS.md](knowledge/KEYS.md))
2. Is every silent rotation behind Move Fix, at a yaw STRICT maps exactly, at a
   priority that lets combat and building take the head? Does the ArrayList say
   "Move Fix off"? ([MOVE_FIX.md](knowledge/MOVE_FIX.md))
3. Does every decision read state that is current where it is read, not last
   tick's? ([TICK_ORDER.md](knowledge/TICK_ORDER.md))
4. Does every velocity or position edit go through `Services.motion()` or
   `Displacement`, sit inside an allowance in `Grim.md`, and say so in its
   description if it doesn't? ([MOTION.md](knowledge/MOTION.md))
5. Does it stand down in every situation it was not written for: gliding, water,
   lava, climbing, riding, flying, spectator, dead, a screen open?
   ([SITUATIONS.md](knowledge/SITUATIONS.md))
6. Does it give way to other modules (a lower priority, or no claim at all) rather
   than check whether they are on?
7. Does switching it off, or switching mode, leave nothing held, and does a
   world change or respawn leave no stale state?
8. Does it skip work it can't act on: no scans, simulations or claims in ticks
   where it does nothing?

## The movement modules

| Module | State | Known to check |
|---|---|---|
| [Strafe](modules/STRAFE.md) | **Not yet** hardened. Grim extras and the air-control settings (Steer, Air Brake, Smart Jump) untested in game | Situations (glide, water, riding); `slowed()` a tick late; sprint key under Toggle Sprint |
| Sprint | Not yet | — |
| Step | Not yet | — |
| [Elytra Fly](modules/ELYTRA_FLY.md) | **Not yet** hardened. Bounce tested on the dev server (about 30 b/s, no alerts); Firework untested | Hold Glide works 1 bounce in 42; Firework has no "Move Fix off"; launch wait not tied to ping |
| [No Slow](modules/NO_SLOW.md) | **Not yet** hardened. Grim mode rewritten 2026-10-30 from Grim's bytecode (alternate fast/slowed, no packets); untested in game | Ticks Grim skips other than teleports; whether `itemUseTicks()` counts once a tick |
| [Blink](modules/BLINK.md) | **Not yet** hardened. Untested since the 2026-10-22 and 10-28 reworks | Adaptive untested where it rubberbanded (Crystal Velocity); tab-list ping; crystal estimate every tick |

Speed was removed on 2026-10-30: it scaled velocity, which Grim flags, and Strafe's
Grim mode is the good sprint-jumping it would have become.
