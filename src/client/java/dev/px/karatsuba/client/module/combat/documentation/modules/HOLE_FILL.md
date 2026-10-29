# Hole Fill

`client/module/combat/HoleFillModule.java`. Fills holes an enemy is heading for
(Predict, through the kit's `HoleFill` search) or every hole in reach (All), with
blocks or cobwebs, one click a tick through the kit's planners.

**Status:** `REFUSED` handling fixed 2026-10-29, from the README's table and the
code. No debug log has been taken yet, and the rest of the checklist hasn't been
gone through. Untested in game.

## A refused click that went

1. **Blocks:** the planner (`kit.blocking()`) ignores a crystal that's already been
   hit, Crystal Aura's above all. The client still shows that crystal, so it
   refuses the block, though the packet goes and the server places it.
   **Webs:** only you are in a web's way (a web has no collision box). Any `REFUSED`
   web with you outside the cell went.
2. **`went` counts such a click as sent.** For blocks, nothing in the cell may count
   as in the way (`kit.blocking()`). For webs, you may not be in it. The cell goes
   into `sent`.
3. **A cell in `sent` isn't planned again** until the server's block shows up or a
   round trip passes (`ceil(ping / 50) + 3` ticks). If the planner puts a support
   into such a cell, the tick waits.
4. **`openCells` doesn't count a cell in `sent` as open,** so the hole is kept in
   `started` or reported to the search (`search.filled`) as with a `SENT` click.

**Before:** neither outcome was recorded. The cell was planned and clicked again
every tick until the crystal's removal arrived, and Predict mode never marked
the fill as pending.

## TODO

- [ ] **Take a debug log** against the Fake Player beside Crystal Aura, and go
  through the rest of the checklist in [../README.md](../README.md).
