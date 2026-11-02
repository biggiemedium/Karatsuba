# No Slow

`client/module/movement/NoSlowModule.java`, with its modes in `movement/noslow/`.
Keeps your speed while using an item: eating, drinking, blocking, drawing a bow.
A `ModalModule` with two modes; the module file has no logic. Grim has one
setting (Sprint) and a panel; Vanilla has none.

**Status:** not yet hardened. Grim mode was rewritten on 2026-10-30 after reading
Grim 2.3.73's bytecode (see "What reading Grim found"). It compiles; it is
untested in game.

## The seam: `SlowdownEvent`

`adapter.event.game.player.SlowdownEvent`, posted by `ClientPlayerEntityMixin`:

- **Where:** `decideUseSlowdown()`, last thing in `karatsuba$afterInput`: after
  `Input.tick()`, Move Fix, the movement claims, hold-still and jump let-go, and
  before vanilla's first sprint check (step 6 in
  [TICK_ORDER.md](../knowledge/TICK_ORDER.md)). So "using an item" is this tick's
  answer there, unlike at `TickEvent` PRE. Moved there from the slowdown's own
  `isUsingItem()` check on 2026-10-30, because `shouldStopSprinting` runs before
  that check.
- **When:** once a tick, only while `isUsingItem() && !hasVehicle()`, vanilla's
  own guard for the slowdown. A tick it isn't posted is a tick nothing is slowed.
- **What:** cancel it and the inputs are not scaled; `setMultiplier()` scales
  them by something else. The mixin swaps both `0.2f` constants for the answer.
- **`isMoving()`:** whether this tick's movement input is not zero. With no input
  the slowdown changes nothing.
- **`allowSprint()`:** this tick, `shouldStopSprinting` and `canStartSprinting`
  skip their item-use test (`karatsuba$usingForSprint`). No other sprint rule
  reads item use. Every other rule still applies, and sprint still has to be
  asked for. On a tick that doesn't allow it, vanilla stops sprint itself.
  Grim's SprintC flags two sprinting slowed ticks in a row, so only allow it on
  alternate ticks.

## Vanilla (`VanillaNoSlow`)

Cancels every `SlowdownEvent`. Full walking speed while using an item, with no
attempt at an anticheat. On Grim, two fast slowed ticks in a row flag NoSlow, so
it flags within two ticks of the first step and is set back once the VL passes 5.

## Grim (`GrimNoSlow`)

Full speed every other tick, which the NoSlow check never flags (see below), and
sprint on those ticks. No packets of its own.

| Setting | Default | What |
|---|---|---|
| Sprint | on | Sprints on the fast ticks without the key: `allowSprint()`, and `Players.setSprinting(true)` while moving forward with food for it |

All in `onSlowdown`, so only on ticks that are slowed, with this tick's state:

1. **A new use starts slowed.** If `Players.itemUseTicks()` is not one more than
   at the last slowdown, this is a new use (or one the mode didn't see all of),
   and Grim may still hold a fast tick from before.
2. **`slowedOwed`** counts the slowed ticks Grim must see before the next fast
   one. While it is above 0, or while there's no movement input, the tick stays
   slowed and pays one off. A tick with no input is the same move either way, so
   Grim rewards it.
3. **Otherwise it cancels** the slowdown and owes one slowed tick. With Sprint on,
   it also allows sprint and starts it (`Players.setSprinting`, while
   `movingForward()` and `canSprint()`). That isn't a key press: a Toggle Sprint
   binding flips on a press ([KEYS.md](../knowledge/KEYS.md)). The event is
   posted before vanilla's sprint checks, so they still stop a sprint that isn't
   allowed (sneaking, a wall, water, blindness). On that slowed tick vanilla's `shouldStopSprinting` drops
   sprint, which clears SprintC's latch the same tick NoSlow's clears.
4. **A `ServerTeleportEvent` sets it to 2.** Grim doesn't check the tick around a
   teleport, and an unchecked tick leaves its latch as it was.
5. **`onSelected`** starts over (owes 1, no use seen).

Steady walking while eating is fast, slowed, fast, slowed: **0.6 of walking
speed, 3× vanilla's** with Sprint off (movement is linear in the input, so the
average speed is the average multiplier). With Sprint on: 0.75× walking, and
**1.25× walking sprint-jumping** (table under "Further ideas").

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

Researched 2026-10-30 (second pass), from the same bytecode and vanilla 1.21.4's
`ClientPlayerEntity`. Numbers from [`sim/noslow_sim.py`](../sim/noslow_sim.py):
1D, flat ground, W held, the latch obeyed.

| Strategy | Speed | × walking |
|---|---|---|
| Vanilla eating | 0.86 b/s | 0.20 |
| **Alternate (the mode now)**, walking or jumping | 2.6 b/s | 0.60 |
| Alternate + sprint on fast ticks, walking | 3.24 b/s | 0.75 |
| **Alternate + sprint on fast ticks, sprint-jumping** | **5.38 b/s** | **1.25** |
| Same, but every air tick slowed (to save the fast tick for the jump) | 3.91 b/s | 0.91 |
| No item: walking / sprinting / sprint-jumping | 4.32 / 5.61 / 7.13 b/s | 1.00 / 1.30 / 1.65 |

### Sprint on the fast ticks (built 2026-10-30 as the Sprint setting; untested)

The biggest gain left: **6× vanilla, faster than walking with no item.** The
sprint-jump's +0.2 boost is not scaled by the item slowdown at all. Vanilla only
loses it because it won't sprint while using.

- **Grim allows it on alternate ticks.** SprintC ("sprinting while slowed") uses
  the same two-tick latch as NoSlow: sprinting on a tick Grim thinks slowed sets
  it, a slowed tick without sprint clears it, and two in a row flag. A 1.21.4
  client is checked (the version gate is "older than 1.14.2, or exactly 1.21.4").
  So fast-and-sprinting, slowed-and-not, over and over, sets and clears both
  latches on the same ticks.
- **The packets are vanilla's.** `sendMovementPackets` sends START/STOP_SPRINTING
  only when the state changes, before the position. BadPacketsF flags only a
  repeated state, and PacketOrderO flags a sprint packet sent after the flying
  packet. One toggle a tick, in vanilla's place, fits both. Not a fabricated
  packet (rule 7): it is the real state.
- **Vanilla's rules, read from 1.21.4:** `canStartSprinting()` and
  `shouldStopSprinting()` both refuse while `isUsingItem()`. In `tickMovement`,
  `shouldStopSprinting` runs **before** the slowdown's `isUsingItem()` check and
  `canStartSprinting` after. No later sprint-stop check reads item use.
- **What was built** (as planned):
  1. Post `SlowdownEvent` from `karatsuba$afterInput` (straight after
     `Input.tick()` and the corrections, before the first sprint check) while
     `isUsingItem() && !hasVehicle()`, and cache the answer for the two `0.2f`
     constants.
  2. Add a "sprint" permission to the event. On a tick that grants it, the mixin
     answers `false` for `isUsingItem()` inside `shouldStopSprinting` and
     `canStartSprinting`. On every other tick, vanilla stops sprint itself.
  3. In `GrimNoSlow`, grant it on the fast ticks behind a **Sprint** setting.
     Sprint still needs the key, Sprint mode or Strafe. Sprint mode sets it at
     `PlayerUpdateEvent` POST, which is why the stop check needs the override.
- **Keep alternating in the air.** Sprinting air ticks matter: slowing every air
  tick to save the fast tick for the landing jump is slower (table above).
- **Unverified:** whether Grim's prediction takes the sprint speed modifier on
  the exact tick it toggles. That is what the dev-server test must show (no
  Simulation either).

### Wider near other entities (situational; not built)

NoSlow's offset is measured against Grim's widened box of start velocities
(`handleStartingVelocityUncertainty`, then `cutBoxToVector`). Entity pushing
widens it by `0.08 × pushers` on X and on Z (`Grim.md`, "Strafe extras"). The gap
between full and slowed input is about 0.10 blocks a tick on the ground, and
0.016 in the air. So with one pusher touching (box grown by 0.2), air ticks can
all be fast, and with two, ground ticks can too. That is rare in practice, and
Strafe's Entity Collide spends the same box.

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
| Sprinting on every tick | SprintC is the same latch; only the alternate ticks pass |
| Unchecked ticks, or ticks without a movement packet | A 1.21.4 client sends `CLIENT_TICK_END` every tick; the "point-three" allowance (0.03 + 2·0.546·0.03) only follows a tick of almost no movement |
| Making Grim think the item can't be used | Grim's view (`canUse`, its food level, cooldowns) matches the server's; where it doesn't, the server won't feed you either |
| Items with their own speed (`USE_EFFECTS`) | Grim reads the component only for 1.21.11+ clients; we are 1.21.4, so it is always 0.2 |

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
| **Sprint on the toggle tick** | Whether Grim's prediction applies the sprint speed on the exact tick sprint starts or stops. If not, Simulation flags it, and Sprint should be turned off until it is understood |
| **Sprint mode's timing** | It sets sprint at `PlayerUpdateEvent` POST, after the packets, so the stop on a slowed tick is never sent as sprinting. On the fast tick that follows, the override keeps vanilla's early stop check from dropping it |
| **`itemUseTicks()` counts once a tick** | The new-use test assumes `getItemUseTime()` goes up by one between slowdowns. If it doesn't, every tick looks like a new use and the mode never goes fast: safe, but slow. The log will show it |

## TODO

- [x] Rewrite `GrimNoSlow` to alternate at the slowdown, with no packets (2026-10-30).
- [ ] Test it on the dev server, with a temporary log (each slowdown: moving,
      useTicks, owed, cancelled; teleports; Grim alerts). Expect no NoSlow alerts
      walking, strafing, jumping and in water while eating, speed near 0.6 of
      walking, and the item finishing on time.
- [x] Build "Sprint on the fast ticks" as the Sprint setting (2026-10-30). The
      user reported it working the same day (with the sprint key held). It then
      started sprint itself, without the key; that part is untested.
- [ ] Test Sprint fully, without the key and with Toggle Sprint: no SprintC, BadPacketsF, PacketOrderO or Simulation; sprint
      toggling every tick while eating (F3 or the log); about 5.4 b/s
      sprint-jumping with Strafe, about 3.2 b/s walking with Sprint mode.
- [ ] Check that Vanilla mode, and Grim with Sprint off, behave as before the
      `SlowdownEvent` move (it now posts from `afterInput`).
- [ ] Decide on the hard-collider setting.
- [ ] Work through the README's checklist and update this file.
