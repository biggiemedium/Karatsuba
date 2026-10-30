# No Slow

`client/module/movement/NoSlowModule.java`, with its modes in `movement/noslow/`.
Keeps your speed while using an item: eating, drinking, blocking, drawing a bow.
A `ModalModule` with two modes; the module file has no logic. Neither mode has
settings yet, so neither has a panel.

**Status:** not yet hardened. Grim mode was rewritten on 2026-10-30 after reading
Grim 2.3.73's bytecode (see "What reading Grim found"). It compiles; it is
untested in game.

## The seam: `SlowdownEvent`

`adapter.event.game.player.SlowdownEvent`, posted by `ClientPlayerEntityMixin`:

- **Where:** at the one `isUsingItem()` check in `ClientPlayerEntity.tickMovement`,
  after `Input.tick()`, Move Fix and the movement claims (step 7 in
  [TICK_ORDER.md](../knowledge/TICK_ORDER.md)). So "using an item" is this tick's
  answer there, unlike at `TickEvent` PRE.
- **When:** once a tick, only while an item is in use on foot. A tick it isn't
  posted is a tick nothing is slowed.
- **What:** cancel it and the inputs are not scaled; `setMultiplier()` scales
  them by something else. The mixin swaps both `0.2f` constants for the answer.
- **`isMoving()`:** whether this tick's movement input (after Move Fix and the
  claims) is not zero. With no input the slowdown changes nothing.
- **Not touched:** the sprint rules for item use (`shouldStopSprinting`,
  `canStartSprinting`). Vanilla still stops sprint, and Grim's SprintC flags
  sprinting while slowed, so leave it that way.

## Vanilla (`VanillaNoSlow`)

Cancels every `SlowdownEvent`. Full walking speed while using an item, with no
attempt at an anticheat. On Grim, two fast slowed ticks in a row flag NoSlow, so
it flags within two ticks of the first step and is set back once the VL passes 5.

## Grim (`GrimNoSlow`)

Full speed every other tick, which the NoSlow check never flags (see below). No
packets, no settings.

All in `onSlowdown`, so only on ticks that are slowed, with this tick's state:

1. **A new use starts slowed.** If `Players.itemUseTicks()` is not one more than
   at the last slowdown, this is a new use (or one the mode didn't see all of),
   and Grim may still hold a fast tick from before.
2. **`slowedOwed`** counts the slowed ticks Grim must see before the next fast
   one. While it is above 0, or while there's no movement input, the tick stays
   slowed and pays one off. A tick with no input is the same move either way, so
   Grim rewards it.
3. **Otherwise it cancels** the slowdown and owes one slowed tick.
4. **A `ServerTeleportEvent` sets it to 2.** Grim doesn't check the tick around a
   teleport, and an unchecked tick leaves its latch as it was.
5. **`onSelected`** starts over (owes 1, no use seen).

Steady walking while eating is fast, slowed, fast, slowed: **0.6 of walking
speed, 3× vanilla's** (movement is linear in the input, so the average speed is
the average multiplier). Sprint stays off, as in vanilla.

### Before 2026-10-30

Two ticks fast, one slowed, decided at `TickEvent` PRE, with `USE_ITEM` re-sent on
each fast tick. Removed: it flagged every cycle, the re-send probably restarted
the eat, and it broke `Grim.md` rule 7 (details below).

## What reading Grim found (2026-10-30)

Read from 2.3.73 bytecode (`NoSlow`, `PredictionEngine`, `PacketPlayerDigging`,
`Check`, Paper's `BukkitItemResetHandler`) and the dev server's `config.yml`. The
facts that hold beyond this module are in `Grim.md`, "Item use".

**Simulation never catches No Slow.** Every tick, `PredictionEngine.loopVectors`
tries each candidate both slowed and unslowed (`Flip_Use_Item`). A full-speed
move always has a matching candidate, so only the NoSlow check can see it.

**The NoSlow check is a two-tick latch.** After each checked prediction where
Grim believes you slowed (`isSlowedByUsingItem`):

| Best *non-flipped* candidate | What happens |
|---|---|
| Offset > 0.001 (you moved faster than slowed) | If `flaggedLastTick`: flag, alert, +1 VL, setback once VL > 5. Then `flaggedLastTick = true` |
| Offset ≤ 0.001 | `reward()`: VL − 0.05. `flaggedLastTick = false` |

Ticks where Grim does **not** think you are slowed leave `flaggedLastTick` as it
was. So it carries over from one use to the next: end a use on a fast tick and
the next use's first fast tick flags.

**What that meant for the old Grim mode:**

| Problem | Why |
|---|---|
| **It flags every cycle** | Fast, fast, slowed: the second fast tick finds `flaggedLastTick` set. That is +1 VL per 3 ticks against −0.05 decay, so a setback after about 6 cycles (under a second of walking) |
| **It probably stops you finishing the item** | `reset-item-usage-on-item-use` is `true` by default (and on the dev server). Every `USE_ITEM` makes Grim call `resetItemUsage`, which on Paper is `clearActiveItem()`, and the server then starts the use again from zero. A re-send every 3 ticks restarts a 32-tick eat before it ends. Untested |
| **The re-send buys nothing** | `USE_ITEM` only sets `isSlowedByUsingItem` true again. It never touches `flaggedLastTick` |
| **The re-send breaks `Grim.md` rule 7** | "No fabricated packets: no fake slot or use packets" |
| **It decides a tick late** | `usingItem()` at `TickEvent` PRE is last tick's. The first tick of a use is never free, and the cycle runs one tick past the end of the use |

## Further ideas

### Free near a hard collider (optional, a setting; not built)

NoSlow's offset goes through `UncertaintyHandler.reduceOffset` as a **squared**
offset. Within 1.0 of an unridden boat or shulker, that tick and the 3 after,
1.2 is forgiven (about 1.095 blocks; `Grim.md`, "`reduceOffset`"). So the NoSlow
offset is 0 and the tick is rewarded even at full speed: every tick can be
fast. Strafe's Boat Burst uses the same allowance. It needs a seam for "hard
collider near".

### What it should not try

| Idea | Why not |
|---|---|
| A multiplier slightly above 0.2 on the slowed tick | The threshold is 0.001 blocks; about 0.008 more input is all that fits. Not worth a setting |
| Letting go of use, or switching slot, to clear Grim's "slowed" | Ends the use on the server (`RELEASE_USE_ITEM`; reset on slot change) |
| Riding the VL budget (flag, decay, stay under 5) | Each flag is an alert. Hardened modules don't flag |

### Situations

`SlowdownEvent` is only posted on foot, while using, so gliding and riding never
reach it. Water, lava and climbing are slowed by vanilla the same way and checked
by Grim the same way, so the alternation holds there too. To confirm in testing.

## Known to check

Found by reading on 2026-10-30; none confirmed in game.

| What | Why it matters |
|---|---|
| **Ticks Grim doesn't check, other than a teleport** | They leave the latch alone, so fast, unchecked, fast is two in a row to Grim. Teleports are covered (owe 2); anything else Grim skips is not. Watch the log for NoSlow alerts with no teleport near |
| **Grim slowed while the client isn't** | At a use's end (Grim drops "slowed" only when the client acks the server's metadata) a vanilla tick can be fast to Grim and set the latch without the mode seeing it. Covered: every new use starts slowed |
| **Server-side refusals** | If the server ends the use and the client hasn't heard yet, the client is slowed and Grim isn't. Harmless: the flip candidate covers it |
| **`itemUseTicks()` counts once a tick** | The new-use test assumes `getItemUseTime()` goes up by one between slowdowns. If it doesn't, every tick looks like a new use and the mode never goes fast: safe, but slow. The log will show it |

## TODO

- [x] Rewrite `GrimNoSlow` to alternate at the slowdown, with no packets (2026-10-30).
- [ ] Test it on the dev server, with a temporary log (each slowdown: moving,
      useTicks, owed, cancelled; teleports; Grim alerts). Expect no NoSlow alerts
      walking, strafing, jumping and in water while eating, speed near 0.6 of
      walking, and the item finishing on time.
- [ ] Decide on the hard-collider setting (step 2).
- [ ] Work through the README's checklist and update this file.
