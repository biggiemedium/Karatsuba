# Module plan

A working document for which modules Karatsuba has, how they are grouped, and what
to build next. Combat stays as it is. Outside it the aim is a few modules per
category that are each worth real effort, not a long list of small toggles.

## What a module is

**A module is something you do in game.** Something that configures how the whole
client behaves is a client setting and lives in the Client Settings screen, not in
a category (see "Client settings" below).

A module is worth building if it does all four:

1. **Solves a problem a player keeps running into**, in a fight, travelling,
   building or mining. Not "it would be cool if".
2. **Builds on something only this client has**: Core's movement simulation and
   prediction, the combat library's placement planner, `Services.actions()` /
   `Services.inventory()`, `PlacementSync`, the Move Fix. A module any client could
   copy in an afternoon is filler.
3. **Is safe on Grim (2.3.73) by construction.** Check it against the Grim rules in
   memory: what keys are reported, which yaw is used for the move and which is sent,
   the packet order within a tick, and no inventory clicks while moving.
4. **Has settings worth having.** A few that change behaviour, not twenty that tweak it.

## Categories

Grouped by what you are doing, not by the old cheat-client drawers (Player, Misc,
Exploit), which only said that a module did not fit anywhere else.

| Category | What belongs | Status |
|---|---|---|
| **Combat** | Fighting players | Live, unchanged |
| **Movement** | How you move right now, wherever you are going: a step, a jump, a sprint, a glide. Never touches the world | Live |
| **Travel** | Getting somewhere: it has a destination, runs for minutes, steers toward a goal. The first users of the pathing layer | Added with its first module |
| **Building** | Placing blocks to make something: Scaffold and build-adjacent tools | Live |
| **Mining** | Breaking blocks to gather: tools, break timing, ores | Live |
| **Farming** | Crops, animals, food | Waits until it has enough modules |
| **Inventory** | Moving and keeping items: armour, refills, carrying | Live |
| **Render** | What you see | Live |
| **Utility** | Small, finished tools, plus chat and dev tools until either earns its own category | Live |

**Two rules for sorting:**

- **Movement or Travel:** does it need a destination? If yes, it is Travel. If it
  changes how you move right now, wherever you are going, it is Movement. Elytra
  splits on this: fine control in flight is Movement, "get me to these coordinates"
  (take-off, fireworks, landing, safety) is Travel.
- **Anything that changes the world goes by what it changes.** Scaffold places
  blocks with the placement planner, so it is Building, even though you use it to
  get across a gap.

**A category ships with its first module.** It is added to `Categories` in the same
change as the first module in it, so the click GUI never shows an empty window.

**Automation** (task queues, macros, scripting) is not a category. It will be a
client system of its own later, with the pathing layer underneath Travel, Mining and
Building.

## What exists today

| Category | Modules |
|---|---|
| Combat | Auto Totem, Auto XP, Bed Aura, Crystal Aura, Hole Fill, Kill Aura, Surround, Velocity, Web Aura |
| Movement | Parkour, Safe Walk, Speed, Sprint, Step |
| Building | Scaffold |
| Mining | Auto Tool |
| Inventory | Elytra Swap, Inventory Manager, XCarry |
| Render | Block Overlay (crosshair + breaking), Combat Info, ESP, Free Cam, Hole ESP, Name Tags, No Block Render, Predict ESP, Render Adjustments, Search |
| Utility | Anti AFK, Anti Hunger, Anti Sound, Fake Player, Middle Click, Recorder |

**What moved in the refactor:**

- Auto Tool from Utility to **Mining**; XCarry from Utility to **Inventory**.
- Anti Hunger (was Exploit) and Fake Player, Recorder, Server Alerts (were Misc) to
  **Utility**.
- **Fullbright** removed. Render Adjustments already had a Full Bright toggle that
  lights the world without touching your brightness setting; its brightness is now
  a setting there.
- **Interact** and **Update Check** removed as modules: they are client settings now.
- The **Player**, **Exploit** and **Misc** categories are gone.

## Client settings

Client-wide settings live in the **Client Settings** screen (bind: Home), apart from
the click GUI, like the HUD Elements screen. They are saved per profile like
everything else.

| Section | What | Was |
|---|---|---|
| **Interaction** | How every module's attacks, clicks, swaps and rotations are checked and sent: Checks, Rotate, Rotation Mode, Turn Speed, Move Fix, Raytrace, Swap, Swing, delays, Same Tick, Multitask, Air Place | the Interact module. Its on/off toggle is now the explicit **Checks** setting |
| **Updates** | Check for a newer release, how often, notify once | the Update Check module |
| **Alerts** | Notify on lag spikes, server software, anticheat changes | the Server Alerts module |

Later: other client-wide settings that live in a service with no screen today (the
command prefix, notifications).

---

## What to build

Effort: **S** about a day, **M** a few days, **L** a week or more.

### Movement

- **Safe Walk** · done: presses the real sneak key for the tick Core's simulation
  says you would walk off a drop of at least Min Drop, so vanilla's ledge clip
  stops you and Grim sees shift held. Steps aside while jump is held.
- **Step** · done: when a tick of walking barely moves you and a simulated jump
  lands higher than vanilla's 0.6 step, presses jump for one tick. No step-height
  change.
- **Parkour** · done: presses jump on the last ground tick before a drop of at
  least Min Drop. Runs before Safe Walk, so both can be on together.
- **Elytra Swap** · in Inventory (it manages gear), needs in-game testing. The bind works with the module off (pressing it turns the module on): one bind between chestplate and
  elytra (Take Off jumps and opens it with the real jump key), a fresher elytra
  swapped in mid-flight before the worn one gives out, and **Crystal Guard**: while
  in an elytra, each crystal an enemy could set off is scored with the 1.21.4
  explosion maths (`adapter.combat.SelfBlast`) at where you will be from when a
  chestplate could be on to Look Ahead ticks later; too much and the chestplate goes
  on, Blast Protection first. Re-Equip puts the elytra back once safe while still
  falling. The chest slot never empties mid-swap (pieces in the main inventory go
  via a borrowed hotbar slot, which is put back after).
- Shared pieces: `Ledges` (simulation questions) and `TickKey` (a one-tick key
  press, always released at tick end).
- **Speed** · rework: Grim simulates every tick from your keys, so a "speed" there
  is good sprint-jumping, not a velocity edit. Either rework it into that or retire it.

### Travel (category added with its first module)

- **Elytra Assist** · M: take-off, firework timing, cruise-altitude hold, swap to a
  fresh elytra before one breaks, landing. Everything is a real rotation, use or
  swap through the services; Grim simulates elytra flight, so no velocity edits.
- **AutoWalk** · S: hold forward with the Move Fix steering, stop on danger
  (players, lava, a drop), eat when needed.
- **Goto / Waypoints** · L: walk to coordinates, a waypoint or a player, on the
  pathing layer (below).

### Building

- **Scaffold** · done: Normal (faces the build the whole time; jump without Keep Y
  to tower) and Telly (forward sprint jump, turn to place after Telly Ticks in the
  air, turn back before the simulated landing). Strict-style planner, clicks through
  `Services.actions()`, its own silent rotation with a random aim point per face
  snapped yaws (camera + 180, then 45° steps) with a pitch scan, as Rise does,
  so Move Fix maps the keys exactly. Checked against Grim 2.3.73 RotationPlace,
  DuplicateRotPlace, PositionPlace, MultiPlace, FabricatedPlace and AimModulo360.
  Needs Move Fix on. Shoreline V2's burst placement was reviewed and rejected
  (MultiPlace).
- **Air Place / Build Assistant** · S–M: place against nothing where the server
  allows it; show what to place next and where.
- Later: blueprint printer, material planner. A project rather than a module:
  schematic parsing, and orientation-correct placement that Grim accepts.

### Mining

- **Packet Mine** · M: queue blocks, break them at exactly the server's break rate
  with the best tool (silent swap), rebreak when replaced. Grim checks break timing,
  so it mines at the server's rate, never faster. Absorbs Auto Tool.
- **Ore Scanner** · M: chunk scanning on a background thread (the one place
  threading clearly pays), updated on block changes, not by rescanning.
- **Vein Miner** · S: mine connected ore with Packet Mine's timing.

### Inventory

- **Inventory Manager** · done (needs in-game testing): one module, three switchable
  jobs that decide together. **Armour** wears the best pieces (Protection or Blast
  preference, replaces nearly broken ones, never Binding, keeps a worn elytra) and
  may stop you a tick to fill a bare slot. **Refill** has no kit: it tops up hotbar
  stacks and refills a slot whose item was used up, never junk. **Cleaner** throws a
  blacklist (an `ItemSetting`) or everything outside the hotbar not on a whitelist,
  with its own bind for looting. All clicks go through `Services.inventory().click()`:
  one a tick, only while the server believes you are still, armour > refill > cleaner,
  all below Auto Totem. Kits per profile were dropped: they break the moment you
  rearrange your inventory to go base hunting.
- **Auto Eat** · S–M: real held right-click through the game's input, so it slows
  you and reports item use as vanilla does (silent eating fails Grim). Picks quiet
  moments and gives combat priority.
- **Shulker Preview** · S: a shulker's contents on hover.

### Render

- **Trajectories** · S–M: where a pearl, arrow, potion or bottle will land, and where
  incoming pearls from other players will land. Server physics, block collision
  against our `BlockView`.
- **Predict ESP** · done, its own module (needs in-game testing): the likeliest
  predicted hitbox `Ticks` ahead from `Core.prediction()` (the auras' Predict), its
  path, optional faint alternative futures by weight, and Reliable/Unsure colour.
- **Block Overlay** · now also Break ESP (needs in-game testing): every block being
  broken (`BlockBreakingEvent` from `ClientWorld.setBlockBreakingInfo`), a fill that
  grows with progress smoothed between the server's 10 steps, labels with who, % and
  time left, and Surround Alert for blocks next to your feet.
- **Theme Colours** convention: a render module offers one `Theme Colours` switch and
  draws every colour through `client.render.ThemeColors`, each colour role at its own
  point on the theme's accent gradient. Block Overlay and Predict ESP have it.
- **Search** · done (needs in-game testing): a block `ItemSetting` (block ids, so
  portals and redstone wire work), each chunk scanned once on arrival via
  `adapter.world.BlockScan` (palette skip), kept current by `BlockChangeEvent`,
  scanned a few chunks a tick; draws the nearest Max Shown within Range as boxes in
  map colour or one colour, optional tracers. Ore Scanner can build on `BlockScan`.
- **No Block Render** · done (needs in-game testing): draws only the blocks on a
  block `ItemSetting` (ores, debris, spawners, liquids by default), through
  `adapter.render.BlockVisibility` and the chunk mixins (`BlockRenderManagerMixin`,
  `BlockMixin`, `ChunkOcclusionDataBuilderMixin`, `BlockEntityRenderDispatcherMixin`,
  hidden blocks report `INVISIBLE` via `BlockStateMixin`, which Indigo cannot bypass; full bright via `WorldRendererMixin` + `BlockStateMixin`). Vanilla renderer only.

### Utility

- **Chat Filter & Mentions** · S–M: collapse repeated messages instead of hiding
  them, highlight and sound on chosen words or players. Moves to a **Chat** category
  if it grows (history, search, a per-player log).
- **Logout Spots & Notifier** · S: where players logged out, visual range, totem pop
  counts, pearls thrown.

## The pathing layer

Not a module: the layer Travel, Mining and Building modules will run on, built so
any planner (ours, or Baritone as a path provider) can plug in. Core already ships
the bottom of it: `util.spatial.AStar` over a `PathSpace` (diagonals, vertical
moves, step-up, maximum fall, a node budget, a weighted heuristic), with the
adapter's `WorldPathSpace` (walking and flying).

1. **Goals and movements:** goals beyond one cell ("near", "any of", "on this Y")
   and movements with costs (walk, jump, fall, pillar, bridge, break by tool and
   break time).
2. **Search:** behind a `PathProvider` interface (goal in, path out). Core's A* is
   the default.
3. **Executor:** a path turned into per-tick keys (`Inputs`), rotations
   (`Rotations`) and block actions (`Services.actions()`), each step checked against
   Core `Simulation` before committing. This is what makes it Grim-safe.
4. **Tasks:** goto, follow, mine-until, build-region. Automation later queues these.

Layers 1–2 as a version-independent library like Combat; the world view and the
executor in the adapter. Baritone can stand in as a path provider; letting it drive
the player would fight our rotations, the Move Fix and Grim.

## Suggested order

1. **Inventory Manager:** Auto Eat, Packet Mine and Elytra Assist all want items in
   the right place first.
2. **Packet Mine:** finishes the combat loop (break surround → crystal), and opens Mining.
3. **Scaffold:** opens Building, and proves the Move Fix under load.
4. **Trajectories and Predict ESP:** quick wins that show off Core.
5. **The pathing layer, then Travel** (AutoWalk, Elytra Assist, Goto).
