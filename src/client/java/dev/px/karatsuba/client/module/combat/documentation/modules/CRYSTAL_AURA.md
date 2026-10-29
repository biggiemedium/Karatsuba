# Crystal Aura

`client/module/combat/CrystalAuraModule.java`. Places end crystals where they
hurt an enemy most and breaks them. Built on the kit's crystal search and sent
through `Services.actions()`.

**Status:** hardened 2026-10-29 over five test logs on crystalpvp.cc (Grim,
about 100 ms ping). The user says it "feels so much smoother". Hurt Sync's
damage gain is not yet measured (see TODO).

## How a cycle runs

1. **Tick N:** `findBreak`. If a crystal is worth breaking:
   - **Hurt Sync off:** `findPlace` runs inside `kit.asIfGone(crystal)` for its
     replacement, and the place is marked `afterAttack` when it needs that
     crystal gone.
   - **Hurt Sync on:** no replacement yet. The next hit is a window away
     (`waitToPlace`).
2. **Start of tick N+1:** `ActionService` sends the attack, then the place
   (Same Tick). One look serves both when `sharedLook` passes.
3. **A round trip later,** the new crystal's spawn arrives between ticks.
   `CrystalSpawnEvent` → `search.spawned` → an instant break (and replacement),
   sent at the start of the next tick.
4. **While waiting,** `watchPending` holds the look on the newest crystal out, so
   the server's look already hits it when it may be broken.
5. **Max Crystals** counts the `Out` list. While full, the place search is
   skipped. Crystals that never showed up, or that went stale, stop counting
   (`forgetOut`, `forgetStale`).

**Cycle floor:** ping, plus up to one server tick (spawns are sent at the server's
tick end), plus up to one client tick. That's about 150–200 ms at 100 ms ping.
With Hurt Sync on, it's the cooldown instead: about 11 ticks per hit on one target.

## Settings worth knowing

| Setting | Default | What it does |
|---|---|---|
| Max Crystals | 1 | Crystals out at once. 1 is fastest: more out means more looks to turn between |
| Instant | on | Breaks a crystal from its spawn packet, before the next tick |
| Hurt Sync | on | Times breaks to the target's hurt cooldown ([HURT_COOLDOWN.md](../knowledge/HURT_COOLDOWN.md)) |
| Sync Margin | on | Waits 11 ticks rather than 10 |
| Show Crystals/s | on | Metadata shows the last target (kept 20 s, then "None") and breaks a second, averaged over 3 s |
| Predict | on | Scores damage where the target will be; costs about 1 ms a search ([PERFORMANCE.md](../knowledge/PERFORMANCE.md)) |

## Decisions

- **Eating:** nothing goes out while an item is in use (`BUSY`). Decided 2026-10-29; don't reopen unless the user asks.
- **ID prediction** (attacking the next entity id before its spawn arrives) is
  not built. It would save a round trip, but it attacks an entity the client has
  never seen, which risks flags, and the hurt cooldown makes it unnecessary.

## What testing found

Every row was found in a debug log ([DEBUG_LOGGING.md](../knowledge/DEBUG_LOGGING.md))
and fixed. The general lessons are in `knowledge/`.

| Log | Finding | Fix |
|---|---|---|
| — | Placing every tick while the first crystal was in flight put 3 out, and the rotation thrashed between them | Max Crystals |
| — | A crystal we'd hit still blocked its own base until the server removed it | Hit crystals don't block (`kit.everything()`); `asIfGone` for the same-tick place |
| 1 | Shared aim at the top of the base reached the crystal past the 3.0 reach: `TURNING` for 15 ticks straight | `sharedLook` with a 0.25 margin ([AIMING.md](../knowledge/AIMING.md)) |
| 1 | Centre aim at the edge of reach: 65 of 139 breaks `TURNING` | No `aimAt`; the service aims at the nearest point |
| 1 | The dependent place went out without its break: 140 wasted clicks | `Action.afterAttack()` |
| 1, 2 | Places that went out were reported `REFUSED` (the client still showed the dead crystal), not recorded, and placed again | `REFUSED` counts as placed when only hit crystals are in the room ([PLACEMENT.md](../knowledge/PLACEMENT.md)) |
| 2 | The held look reached the crystal past reach, so instant breaks waited a tick (spawn→break about 83 ms) | The held look uses `sharedLook`, or the nearest point |
| 3 | Eyes inside the crystal's box gave a degenerate look (pitch −90, yaw a multiple of 90); 3 crystals were never taken | Box centre under 0.5 blocks, in `ActionService.aimOf` |
| 3 | 63% of hits landed inside the target's hurt cooldown | Hurt Sync |
| 4 | Hurt Sync held crystals through the cooldown; they went stale and blocked the slot: about 1 break/s | `waitToPlace`, `forgetStale` |
| 5 | Breaks came 12 ticks apart at an 11-tick window | Judge the window a tick early (`judgedWindow`) |
| 5 | The same degenerate look, in `watchPending` | The same 0.5-block rule |

**Results:** spawn→break went from up to 384 ms to 29–55 ms. Breaks stuck
`TURNING` went from about half to 0–2 per 5 s.

## TODO

- [ ] **Measure Hurt Sync's damage** against a real player on a server that shows
  health: pops and kills, with it on and off. Our Fake Player (Barnaby) and
  crystalpvp.cc can't show damage.
- [ ] **A hit the server refuses still opens a cooldown window**, so the next
  break waits for nothing. This happened 1–3 times per log. Fix: take the hit back
  out of the `AttackLog` when its crystal is still there a round trip later.
