# Debug logging a movement module

The logger itself is the combat one: its shape, line format, file location and
full source are in
[`combat/documentation/knowledge/DEBUG_LOGGING.md`](../../../combat/documentation/knowledge/DEBUG_LOGGING.md).
Build it the same way, as a temporary class next to the module, and delete it when
the module is done. This file is what a movement log records on top.

## Where to test

The dev server (`gradlew runServer`, then `gradlew runClientDebug`; see
`dev/README.md`): Paper with Grim 2.3.73, offline on `localhost:25565`, the dev
client (`Dev`) opped. Ops are not exempt from Grim. Turn
on Grim's alerts so its verdicts land in chat, and log them.

## What to log

**At open:** the same `SERVER`, `SETTINGS` and `PROFILE` lines as combat, plus
whether Move Fix is on.

**Every tick the module runs:**

| Kind | Details |
|---|---|
| `TICK` | onGround, inWater, gliding, usingItem, sneaking: the situation ([SITUATIONS.md](SITUATIONS.md)) |
| `KEYS` | the player's own keys, the keys pressed by the module, and the keys reported after `Input.tick()` |
| `LOOK` | camera yaw, requested yaw, yaw sent, and who won the rotation |
| `MOVE` | velocity at `PlayerMoveEvent` PRE, the claim filed, velocity after the move, position delta |
| `EXTRA` | any allowance used: pushers counted and push given, a burst and its distance |

**On events:**

| Kind | From |
|---|---|
| `SETBACK` | `ServerTeleportEvent` while the module is on |
| `FLAG` | a Grim alert in chat: check name and verbose |
| `KNOCKBACK` | `KnockbackEvent`: so a setback after a hit is not blamed on the module |

## Reading it

- **Setbacks by situation:** group `SETBACK` by the last `TICK` before it. A
  setback that only happens in water is a missing stand-down, not bad maths.
- **Keys against yaw:** a tick where the reported keys are not what STRICT maps
  from the yaw sent is a Move Fix bug.
- **One setting at a time:** turn the extras on one by one, so each flag has
  one cause.
