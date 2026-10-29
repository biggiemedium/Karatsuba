# Placement rules

What decides whether a crystal, a block, a bed or an anchor can go somewhere, as
the game and the kit see it. Facts marked *(bytecode)* were checked against the
1.21.4 game jar, not the wiki.

## End crystals

- **Base:** obsidian or bedrock.
- **Above the base:** one block of air. Since 1.13 a crystal fits a one-block hole.
- **No entity** in the two blocks above the base (the base's footprint, from its
  top face up 2). That means any entity at all, dropped items included,
  spectators excepted (`EndCrystalItem.useOnBlock`).
- **The crystal's box** is 2 × 2 × 2, centred on the middle of the base's top
  face. So it reaches a block out on every side, which matters for aiming
  ([AIMING.md](AIMING.md)).
- **Explosion:** power 6, from where the crystal stands. Damage isn't truncated.
- **A crystal dies on any hit,** and the server handles the attack before any
  click sent after it. So the base under a crystal we just hit is free on the
  server at once.
- These rules live in the kit's `CrystalRules`. **Don't change `CrystalRules`**
  (user decision, Breach).

## Blocks, beds and anchors

- **Blocked by any entity whose `intersectionChecked` is set** (`World.doesNotIntersectEntities`).
- **End crystals set it** *(bytecode: `EndCrystalEntity` constructor)*, so a
  crystal in a cell stops a block going in.
- **Placing goes through the kit's planners** (`kit.planner(style)`), with
  `strictStyle()` for Grim: faces turned toward you, in sight, in reach, never
  mid-air, one a tick.

## What the kit counts as "in the way"

| Seam | Used for | Ignores |
|---|---|---|
| `kit.everything()` | crystals (`CrystalRules`) and Breach's crystal room | crystals hit and still inhibited in the kit's `AttackLog`, and the one named by `asIfGone` |
| `kit.blocking()` | blocks, beds, anchors, every `kit.planner` (Surround, Hole Fill's blocks, Bed Aura, Ender Chest Farmer) | the same |
| `Obstructions.of(Core.entities())` | cobwebs (Web Aura, Hole Fill's webs): a web has no collision box, so only you are in its way | every entity but you |

Ignoring hit crystals is right for all of them, because the server removes a
crystal when it handles the attack. **A module that attacks a crystal itself
must inhibit it** on `SENT` with `kit.log().attacked(entity, ticks)`. The
searches do this already. Without it, the module keeps re-attacking the dead
crystal and the crystal keeps blocking.

## Placing in the same tick as the break that frees the room

1. Search or plan inside `kit.asIfGone(crystal, () -> ...)`, so the crystal being
   broken doesn't block.
2. If the result's room overlaps that crystal, mark the click
   `Action.afterAttack()`. It then goes only behind the attack ([ACTIONS.md](ACTIONS.md)).
3. Share one look between the two if it passes [AIMING.md](AIMING.md)'s check.

## When the client refuses a placement that went

The client still shows a crystal we hit until the server's removal arrives, so
its own check refuses a place into that room, while the packet goes out and the
server places it.

**Rule:** count a `REFUSED` place as sent when the room is empty by the kit's
count (`!kit.everything().any(room)` for crystals, `!kit.blocking().any(cell)`
for blocks).

Then:
- **Crystals:** record it as placed.
- **Blocks:** the client shows no ghost block for such a click, so remember the
  cell and don't plan it again until the server's block arrives or a round trip
  passes (`sent` in Surround, Hole Fill and Web Aura).
- **Leaving the cell out of the request isn't enough.** The planner can still
  put a *support* into it for another cell. When the first step's cell is in
  `sent`, wait the tick (Hole Fill and Web Aura do this; Surround doesn't yet).

Ghost blocks from clicks the client *did* accept are settled by `PlacementSync`.

## Grim

From the 2.3.73 jar. The full rulebook is `client/anticheat/Grim.md`.
- **MultiPlace:** one placement a tick, so no bursts.
- **RotationPlace:** the click must be hit by the look after the next movement packet.
- **PositionPlace:** the eyes must be past the clicked face's plane.
- **DuplicateRotPlace:** two clicks with an identical turn before them.
