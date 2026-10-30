# Keys

Grim simulates movement from the keys the client reports. Since 1.21.2 the client
sends which keys it holds (`PlayerInputC2SPacket`), and Grim tries only those. So
a movement module acts by pressing real keys, and the move, the reported keys and
the yaw must agree every tick.

## The ways to drive a key

| Want | Use | Where |
|---|---|---|
| Press a key for one tick (jump, sneak, sprint) | `TickKey.press()` at `TickEvent` PRE | `movement/control/TickKey`. Released by its own `TickEvent` POST subscription, whatever state the module is in |
| Hold a key across ticks | `Inputs.hold()` / `release()` | `adapter.game.player.Inputs`. Tracks what it forced; `releaseAll()` undoes exactly that |
| Report no movement for a tick (for an inventory click) | `Inputs.holdStill()` | Applied after `Input.tick()`; the keyboard is untouched |
| Report jump released for a tick while it is held | `Inputs.letGoOfJump()` | Same point. Zeroes vanilla's jump cooldown |
| Steer with keys a flow or module owns | `Core.controls()` | Written by `MovementKeys` after the Move Fix, corrected to the yaw the game moves by |

`Inputs.pressed()` is true whether the player or a module holds the key;
`Inputs.playerHolds()` is the player alone.

## Rules

- **No `jump()`, no velocity in place of a jump, no faked sneak.** Each is a key
  Grim was not told about (`Grim.md`, "Simulation").
- **`TickKey` leaves a key the player already holds alone,** and never releases it.
- **Every key a module forces is released when it stops.** A mode's `stop()`
  releases its `TickKey`s; the module calls it from `onDisable()`.

## The sprint key is not always a hold

The game's sprint (and sneak) can be set to Toggle. The binding then flips on
press and ignores release, so pressing it from code is not one tick of sprint.
`MovementKeys` sets sprinting on the player instead of pressing the key for this
reason. A module that presses `TickKey(SPRINT)` should be checked with Toggle
Sprint on; Strafe does ([STRAFE.md](../modules/STRAFE.md)).
