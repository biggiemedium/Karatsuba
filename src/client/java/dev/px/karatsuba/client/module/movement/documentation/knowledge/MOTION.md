# Velocity and position

Grim simulates every tick from the keys and keeps its own copy of the velocity, so
an edit it cannot explain is a Simulation flag with a setback. Edits are allowed
(`Grim.md` rule 6), but a module must know which pass, and say so in its
description when they don't.

## How fast motion can change

A module that only picks yaw and keys (Strafe's Grim mode, Steer, Air Brake) is
bound by vanilla's push. Read from the 1.21.4 bytecode on 2026-10-30.

| Where | Push a tick toward the keys | Speed kept a tick | Bytecode |
|---|---|---|---|
| In the air, sprinting | **0.026** | ×0.91 | `PlayerEntity.getOffGroundSpeed` (0.025999999f), `LivingEntity.travelMidAir` |
| In the air, walking | 0.02 | ×0.91 | same |
| On the ground | `movementSpeed × 0.216 / slipperiness³`: **0.13** sprinting on stone (slip 0.6) | ×0.546 (slip × 0.91) | `LivingEntity.getMovementSpeed(float)` |
| Sprint jump | +0.2 along the **yaw**, then the ground push | | `GrimStrafe` doc; not re-read |

- **Sprint can start in the air.** `ClientPlayerEntity.canStartSprinting` has no
  ground check, only forward input of at least 0.8, hunger, no item use,
  blindness, gliding or sneak slowdown.
- **So:** from a bunny hop (~0.3), a reversal takes about 8 ticks in the air and
  2 on the ground. The jump tick is the biggest single redirect. Numbers and the
  simulation are in [STRAFE.md](../modules/STRAFE.md), "Air control".

## How much error Grim allows

From Grim 2.3.73's `config/en.yml` and `OffsetHandler.onPredictionComplete`, read
on 2026-10-30. Rule of thumb: **there is no free velocity**.

| Setting | Default | Meaning |
|---|---|---|
| `threshold` | 0.001 | An offset at or above this is a flag and an alert |
| `immediate-setback-threshold` | 0.1 | One tick at or above this sets back (with ≥ 1 violation) |
| `max-advantage` | 1 | Flagged offsets add up; at 1 block, set back |
| `setback-decay-multiplier` | 0.999 | The sum shrinks by this each clean tick: very slowly |
| `max-ceiling` | 4 | The sum is capped here |

Under 0.001 a tick is about 4% of the air push: nothing a player would feel.

## Where edits go

| Edit | Through | Notes |
|---|---|---|
| Velocity, horizontal or vertical | `Services.motion()`: `setHorizontal`, `scaleHorizontal`, `setVertical`, `scaleVertical` | A claim for one tick, per axis group. Applied at `PlayerMoveEvent` PRE, `LOWEST`, so the game's friction and gravity still follow. Highest priority wins each axis |
| Knockback and explosion pushes | `KnockbackEvent` (cancel, `scale()`, `setVelocity()`) | Never by writing the velocity after the fact |
| Position only, this move | `adapter.movement.Displacement.next()` | Added at `Entity.move` HEAD, own moves only; refused if a block is in the way. Never touches velocity, so nothing outlives the tick. Cancel what the move did not use |
| The sprint-hit slowdown | `adapter.movement.AttackSlowdown.skipWhen()` | Register once, from a constructor |

Never write the player's velocity directly: two modules doing that is a race that
neither can see.

## The edits known to pass Grim

Only these, each read from the 2.3.73 jar (`Grim.md`, "Strafe extras"); all
untested in game.

| Allowance | Grim | What fits inside |
|---|---|---|
| **Entity pushing** | Not simulated. Starting velocity widened by `0.08 × pushers` on X and Z (box grown 0.2, largest count of the last three ticks) | A push of up to that size, in any direction. Read what vanilla already gave with `EntityPush.sinceTravel()` |
| **Boat or shulker** within 1.0 at the start of the move | 1.2 off the offset that tick and the three after; position only | A `Displacement` for 3 ticks |
| **Sprint-hit slowdown** for a 1.21+ client on a 1.9+ server | The unslowed velocity stays a candidate | Skipping the ×0.6 and the sprint stop |
| **Jump Reset** | Simulated: a real jump the tick knockback lands | `JumpReset`, a `TickKey(JUMP)` |

## Staying inside an allowance

- **Measure smaller than Grim does.** `EntityPush` callers grow the box by less
  than Grim's (0.1 against 0.2, 0.8 against 1.0) and count only this tick, so
  anything counted is something Grim counts.
- **Leave a margin.** Strafe takes 0.95 of the push allowance and stops a burst a
  tick before Grim's window closes.
- **Server-set motion is not a push.** `EntityPush.serverVelocity()` voids the
  reading on a velocity packet, explosion or teleport.

## Everything else

Scaling or turning the velocity (the removed Speed module, Vanilla Strafe) is
outside every allowance and flags. That's allowed as a mode that says so, never as
a mode that claims to pass.
