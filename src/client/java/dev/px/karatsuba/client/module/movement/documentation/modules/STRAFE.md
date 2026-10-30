# Strafe

`client/module/movement/StrafeModule.java`, with its modes in `movement/strafe/`.
Steers your motion with the movement keys. A `ModalModule` with two modes; the
module file has no logic.

**Status:** not yet hardened. The Grim extras were wired on 2026-10-17 and are
untested in game (`Grim.md`, "Strafe extras" and "Unverified").

## Vanilla (`VanillaStrafe`)

No attempt at an anticheat. Grim's simulation rejects it the moment the velocity
differs from what the keys give.

1. **At `TickEvent` PRE,** on the ground with a movement key held, it presses jump
   for the tick (`TickKey`).
2. **At `PlayerMoveEvent` PRE,** it reads the keys (not the game's input, which is
   still last tick's there) and claims the horizontal velocity through
   `Services.motion().setHorizontal()` at `NORMAL`:
   - no key held: zero, so letting go stops dead;
   - otherwise: the current speed, turned onto the direction of the keys.
3. **Hurt Boost** replaces the speed with Boost Speed on the first tick after a hit
   (`hurtTime == 9`).

| Setting | Default | What |
|---|---|---|
| Hurt Boost | off | Fixed speed on the tick after damage |
| Boost Speed | 1.0 | Blocks a tick for that |

## Grim (`GrimStrafe`)

Steers the yaw, never the velocity (except the extras below). Needs Move Fix.

1. **At `TickEvent` PRE,** with Move Fix on and a movement key held, it works out
   the heading the keys point (`MovementMath.movementYaw` from the camera's yaw).
2. **It asks for a silent rotation** to that heading, at `LOW` priority, camera
   pitch kept. Move Fix then rewrites the keys to W, so sprint, the sprint jump's
   0.2 boost and the air acceleration all go along the heading.
3. **Diagonal:** while slowed (using an item, sneaking), and not jumping from the
   ground, it faces 45° right of the heading, so the keys become W+A. A cut W+A
   keeps its length where a cut W does not: about 41% faster.
4. **It presses sprint** (Sprint) and, on the ground out of water, **jump**
   (Auto Jump), each for the tick.

| Setting | Default | What |
|---|---|---|
| Sprint | on | Hold sprint, which now works in every direction |
| Auto Jump | on | Hop whenever on the ground and moving |
| Diagonal | on | W+A while eating, blocking or sneaking |
| Steer | off | See "Air control" |
| Air Brake | off | See "Air control" |
| Smart Jump | off | See "Air control" |
| Entity Collide | off | See below |
| Boat Burst | off | See below |
| Burst Distance | 1.0 (0.1–1.15) | Extra blocks a tick during a burst |
| Tap Jump | off | See below |
| Keep Sprint | off | See below |

### The extras

Each stays inside an allowance read from Grim 2.3.73's bytecode. The details and
the bytecode are in `Grim.md`, "Strafe extras"; the shared facts in
[MOTION.md](../knowledge/MOTION.md).

| Extra | When | What it does |
|---|---|---|
| **Entity Collide** | `PlayerMoveEvent` PRE, beside pushable entities | Replaces the push vanilla gave since the last move (`EntityPush.sinceTravel()`) with `0.95 × 0.08 × pushers(0.1)` along the heading, scaled so its larger axis fills Grim's per-axis box. Only if it beats the push already given |
| **Boat Burst** | `PlayerMoveEvent` PRE, for 3 ticks from a boat or shulker within 0.8 | `Displacement.next()` of Burst Distance along the heading: position only. Cancelled at POST if the move didn't use it |
| **Tap Jump** | `TickEvent` PRE, jump held in the air, not in water, lava, a climbable or with an elytra that could open | `Inputs.letGoOfJump()`: the move and the input packet see jump released, so the jump cooldown is zeroed |
| **Keep Sprint** | Any sprinting hit while the mode runs | `AttackSlowdown.skipWhen()`, registered in the constructor: no ×0.6 and no sprint stop |

### The ArrayList

Shows "Move Fix off" while Move Fix is off, since the mode then does nothing.

## Air control (2026-10-30)

The user found Grim mode turns and stops worse than Vanilla, in the air and
strafing side to side. Researched, simulated, and three settings added. **All
three untested in game.**

### Why

Vanilla mode sets the velocity, so a turn is instant. Grim mode only picks the
yaw, so the game's physics decide how fast motion can change
([MOTION.md](../knowledge/MOTION.md), "How fast motion can change"): in the air,
**0.026 a tick** toward the keys against a bunny hop's ~0.3, with 91% of the old
speed kept. Auto Jump keeps the player in the air 11 ticks in 12. Velocity edits
are no way round it: Grim flags any offset from 0.001 ([MOTION.md](../knowledge/MOTION.md)).
The gap can be narrowed, not closed.

### The simulation

`../sim/turn_sim.py` and `../sim/brake_sim.py` (run with `py`). Horizontal 1.21.4
sprint physics on stone, 11 ticks of air a hop, no collisions or potions, every
run starting from a bunny hop, averaged over the 12 ticks of a hop the key could
change in. The numbers are relative, not exact.

| Approach | A→D reverse: ticks to 0.15 b/t the new way, avg (worst) | 90° turn: drift the old way, avg (worst) |
|---|---|---|
| Vanilla mode | 0 | 0 blocks |
| Grim before the change | 8.3 (11) | 2.44 (3.10) |
| + Steer | 8.3 (11) | **1.08** (1.74) |
| + Smart Jump | **6.2** (11) | 2.09 (3.10) |
| + both | 6.2 (11) | 1.12 (1.74) |
| Never jumping after the switch | 10.9 (15) | 1.74 (2.69) |

| Keys let go mid-hop | Slide, avg (worst) |
|---|---|
| Vanilla mode | 0 |
| Grim before the change (coast) | 1.74 (2.69) blocks |
| + Air Brake | **0.98** (1.44) blocks |

What the numbers say:
- **Steer** halves the drift on a turn. It changes nothing on a straight reversal:
  facing the new way is already the best push there.
- **Smart Jump** helps the reversal. Never jumping is worse than jumping: the jump
  tick's 0.2 along the yaw is the largest single push there is.
- **The worst case (11 ticks) is a switch just after take-off.** Nothing changes it.

### The settings

| Setting | When | What |
|---|---|---|
| **Steer** | Keys held, not swimming, climbing or gliding, and Diagonal not in use | Faces along `heading × max(speed, 0.36) − velocity` (the heading once that gap is under 0.02) and reports W at that yaw through `Core.controls().move()` at `LOW`, in place of the player's keys. Jump: this tick's jump, or a jump held mid-air. Sprint kept, or started from Sprint or the sprint key when `Players.canSprint()`. The jump tick's 0.2 goes along that yaw too |
| **Air Brake** | No key held, in the air, same situations, speed ≥ 0.03 | Faces against the velocity and reports W there. Sprint left as it is (0.026 a tick sprinting, 0.02 not) |
| **Smart Jump** | On the ground about to jump, speed ≥ 0.05, velocity more than ~45° (cos < 0.7) off the heading | No auto jump, and a held jump is reported let go (`Inputs.letGoOfJump()`), until the turn is done |

Why they should pass Grim: each only picks a yaw and keys. The yaw is exact and
the keys are W at it, so STRICT maps them with no drift, and the reported keys,
the yaw sent and the move agree (rules 2 and 3 in `Grim.md`). If an aura holds
the head, `MovementKeys` corrects the claim's W to the aura's yaw.

### To test

- [ ] Each setting alone, on the dev server, with Grim alerts on.
- [ ] Steer: 90° turns mid-hop. Expect about half the drift, no Simulation flag.
- [ ] Air Brake: let go mid-hop. Expect a shorter slide. Also check it when hit
  with no key held: it walks against knockback too, which is legal (the keys are
  reported) but changes how a hit feels. KnockbackHandler should pass; untested.
- [ ] Smart Jump: A↔D on the ground. Expect a quicker reversal and a
  one- or two-tick pause in the hops.
- [ ] Steer with Kill Aura holding the head: should fall back to walking at the
  aura's yaw.

## Switching off

`StrafeModule.onDisable()` calls both modes' `stop()`: the rotation and the
movement-key claim are released, the `TickKey`s let go, a pending displacement
dropped. Motion claims lapse on their own.

## Known to check

Found reading the code on 2026-10-30; none confirmed in game yet.

| What | Why it matters |
|---|---|
| **Neither mode checks the situation** ([SITUATIONS.md](../knowledge/SITUATIONS.md)) | Grim mode still turns the head and presses sprint and jump while gliding, swimming, riding or flying; a silent yaw in a glide steers the elytra and fights Elytra Fly. Vanilla mode zeroes or turns the velocity in all of those, and jumps in water |
| **`slowed()` reads last tick's state** ([TICK_ORDER.md](../knowledge/TICK_ORDER.md)) | It runs at `TickEvent` PRE, before this tick's item use starts or stops, so Diagonal comes on and goes off a tick late. The keys the move uses and the yaw still agree, so it is speed lost, not a flag |
| **The sprint key under Toggle Sprint** ([KEYS.md](../knowledge/KEYS.md)) | `TickKey(SPRINT)` presses the sprint binding. With the game's sprint set to Toggle, that binding toggles on press and ignores release, so the sprint can be left on after the module stops. Check before relying on it; `MovementKeys` sets sprinting on the player for this reason |
| **Vanilla turns velocity on the ground too** | The class says "mid-air". On the ground it fights friction every tick and overrides any other horizontal claim at `NORMAL` |
| **`EntityPush`'s doc says "the two after"** | `Grim.md` says Grim forgives the hard-collider tick and the **three** after. `BURST_TICKS = 3` is inside either reading, but one of the two docs is wrong |
| **`axis()` is written in both modes** | Harmless duplication; a shared helper (with `Inputs`) when the modes are next touched |

## TODO

- [ ] Debug log on the dev server, per [DEBUG_LOGGING.md](../knowledge/DEBUG_LOGGING.md).
- [ ] Test each Grim extra alone, then update `Grim.md`'s Unverified list.
- [ ] Work through "Known to check" against the README's checklist.
- [ ] Test the air-control settings ("Air control", "To test").
