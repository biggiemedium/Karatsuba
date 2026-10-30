# Situations

A movement module is written for one kind of movement, usually walking and
jumping on land. In any other, vanilla's physics change under it, and an
edit or a turned head that was right on land is wrong there. Each module should
say which of these it runs in, and stand down in the rest.

| Situation | Read it with | What changes |
|---|---|---|
| **Gliding** | `Players.gliding()` | Motion follows the look, not the keys. A silent yaw steers the elytra; velocity edits fight the glide. Elytra modules own this ([ELYTRA.md](ELYTRA.md)) |
| **Could open an elytra** | `Players.canGlide()` | Jump in the air opens it. Don't press or release jump there unless that is the point |
| **In water** | `Players.inWater()` | Jump swims up; sprint swims. Different drag; SprintG flags sprint touching water while not swimming |
| **In lava** | `Players.inLava()` | Jump swims; heavy drag |
| **Climbing** | `Players.climbing()` | Jump climbs; horizontal motion is capped |
| **Riding** | (no seam yet) | The player does not move itself; Grim checks the vehicle. `EntityPush` already returns nothing while riding |
| **Flying (creative/spectator)** | (no seam yet) | No gravity or ground; different speed |
| **Dead, or no player** | `Players.present()` | Nothing to move |
| **A screen open** | (check) | Keys are not read; a module pressing keys should not walk the player through a menu |

"No seam yet" means `Players` has no method for it: add one in the adapter
rather than reaching for the game.

## Rules

- **Check at the top of the handler, once,** before any claim, rotation or key.
  Returning early also skips the work ([README checklist](../README.md) item 8).
- **Release what a skipped tick would have held.** Claims lapse on their own;
  `TickKey`s let go at tick end; a pending `Displacement` must be cancelled.
- **Ground-only is an honest default** for anything that scales motion: in the
  air there is no friction to damp it, and a constant multiplier compounds.
