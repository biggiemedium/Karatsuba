# Web Aura

`client/module/combat/WebAuraModule.java`. Puts cobwebs on an enemy's feet (and
head) where they are, where they will be, or where they will likely pass. One web
a tick through `Services.actions()`.

**Status:** `REFUSED` handling fixed 2026-10-29, from the README's table and the
code. No debug log has been taken yet, and the rest of the checklist hasn't been
gone through. Untested in game.

## A refused click that went

- **A web has no collision box,** so the planner counts only you as in its way
  (`Obstructions.of(Core.entities())`), and the client's own check rarely refuses
  one. No cause of a refused web is known yet. A debug log would show whether
  any happen.
- **When one does,** and you're not in the cell, `went` counts it as sent: it
  starts the Delay and puts the cell in `sent`. The cell isn't planned again
  until the server's web shows up or a round trip passes. If the planner puts a
  support into such a cell (the feet web under a head web), the tick waits.

**Before:** only `SENT` was recorded. A refused web was clicked again every tick,
and Delay didn't apply to it.

## TODO

- [ ] **Take a debug log** and go through the rest of the checklist in
  [../README.md](../README.md). Of note: the head web is clicked against the feet
  web placed a tick earlier, which the server may not have yet.
