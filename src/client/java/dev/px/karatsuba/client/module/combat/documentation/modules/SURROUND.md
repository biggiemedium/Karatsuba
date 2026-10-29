# Surround

`client/module/combat/SurroundModule.java`. Walls in the cells around your feet
with obsidian (or ender chests), one block a tick, through the kit's planner.

**Status:** hardened 2026-10-29 from the checklist in [../README.md](../README.md).
No debug log has been taken yet; untested in game.

## A crystal in a surround cell

1. **The planner leaves out a cell something stands in** (`Plan.Skip.OCCUPIED`).
2. **`crystalIn` picks the first crystal in such a cell** that is in your entity
   reach and safe to break (`Max Self Damage`, and above a heart).
3. **The cells are planned again inside `kit.asIfGone(crystal)`,** so that cell
   can take its block this tick.
4. **If the first step fills that cell,** the click is `afterAttack`, and the
   attack shares its look when `sharedLook` passes. Both go out in one tick.
   Otherwise the attack aims at the crystal's nearest point and the click waits.
5. **On `SENT`, the crystal is inhibited** in the kit's `AttackLog` for a round
   trip plus 3 ticks. It is never attacked twice, and it no longer blocks
   (`kit.blocking()`).
6. **A click `REFUSED` only because of that crystal is remembered in `sent`.**
   The cell isn't planned again until the server's block shows up, or a round
   trip passes.

## What was wrong before

| Problem | Effect |
|---|---|
| Attack aimed at the crystal's centre | `TURNING` forever at the edge of reach |
| Only the first crystal found was tried | A reachable one behind an unreachable one was never broken |
| No inhibit | The dead crystal was attacked every tick until the server's removal arrived |
| `kit.blocking()` counted the dead crystal | The block went in a round trip after the break, not with it |

The `kit.blocking()` change also applies to every other placer:
[PLACEMENT.md](../knowledge/PLACEMENT.md).

## TODO

- [ ] **Test on Grim:** a crystal in a surround cell should go with its block in
  one tick, with no flag (Same Tick on).
