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
| **Automation** | Modules built from task flow: they plan and move like a task, but stay a toggle | Live |

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

**Automation** is the category for modules built from task flow: a module that runs
Core flows made of the automation's steps rather than a tick handler, so it plans,
moves and composes like a task while staying a toggle. The task system itself
(`.task`, the `Automation` service) is still a client system, not a module.

## What exists today

| Category | Modules |
|---|---|
| Combat | Auto Totem, Auto XP, Bed Aura, Crystal Aura, Hole Fill, Kill Aura, Phase (Pearl, Walk), Surround, Velocity, Web Aura |
| Movement | Blink, Elytra Fly (Bounce, Firework), Sprint, Step, Strafe (Vanilla, Grim) |
| Building | Scaffold |
| Mining | Auto Tool, Ender Chest Farmer, Smart Mine |
| Inventory | Armor Manager, Hotbar Replenish, Inventory Cleaner, XCarry |
| Render | Block Overlay (crosshair + breaking), Combat Info, ESP, Free Cam, Hole ESP, Name Tags, No Block Render, Predict ESP, Projectiles, Render Adjustments, Search, Shaders |
| Utility | Anti AFK, Anti Hunger, Anti Sound, Auto Eat, Fake Player, Middle Click, Recorder |
| Automation | Auto Combat (holes) |

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

- **Step** · done: when a tick of walking barely moves you and a simulated jump
  lands higher than vanilla's 0.6 step, presses jump for one tick. No step-height
  change.
- **Elytra swap** · part of **Armor Manager** (Inventory), needs in-game testing. Its bind works with the module off (pressing it turns the module on): one bind between chestplate and
  elytra (Take Off jumps and opens it with the real jump key), a fresher elytra
  swapped in mid-flight before the worn one gives out, and **Crystal Guard**: while
  in an elytra, each crystal an enemy could set off is scored with the 1.21.4
  explosion maths (`adapter.combat.SelfBlast`) at where you will be from when a
  chestplate could be on to Look Ahead ticks later; too much and the chestplate goes
  on, Blast Protection first. Re-Equip puts the elytra back once safe while still
  falling. The chest slot never empties mid-swap (pieces in the main inventory go
  via a borrowed hotbar slot, which is put back after).
- **Elytra Fly** · done (needs in-game testing), modal like Velocity. **Bounce** (Meteor's, i.e. Elytra Recast): forward held, head locked low, jump held on the ground and re-pressed in the air so vanilla re-opens the elytra; waits for two airborne movement packets and a released jump (Grim PacketEntityAction/ElytraF/ElytraB), never forces sprint (SprintF). **Firework** (Lambda's): keys steer a silent look, a rocket kept burning; hover freezes the body only while a rocket is attached, inside Grim's firework box (`UncertaintyHandler.tickFireworksBox`). Rockets push along the sent look (`FireworkRocketEntityMixin`). Packet-stall hover and velocity sets left out.
- Shared pieces, in `movement/control/`: `Ledges` (simulation questions) and `TickKey` (a one-tick key
  press, always released at tick end).
- **Speed** · removed 2026-10-30. It scaled the ground velocity through
  `Services.motion()`, which Grim's simulation flags. Good sprint-jumping is
  Strafe's Grim mode.

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

- **Smart Mine** · done (was Auto Mine) (needs in-game testing, then the Grim dev server). Left click queues
  a block and right click on a queued block unqueues it, neither reaching the server
  (`ClickEvent`, `ClickMixin`); Cancel Animation (default on) also skips the
  arm swing, so no one sees the click. Select VEIN marks the whole touching vein
  (deepslate variants folded in, Vein Limit). It mines the nearest (or first-marked)
  queued block that is in reach and in sight. It uses a silent rotation, and digs only
  while the rotation the server has lands on the block. The best tool is held openly
  and must be in hand before the start. Breaking goes through the new
  `adapter.game.player.Digging`: vanilla's `updateBlockBreakingProgress` called from
  `handleBlockBreaking` (`BlockBreakingMixin`), so the speed, packets, swing and 5-tick
  pause are the game's. It pauses (no packet, progress kept) in a tick where
  `ActionService` sent something. While it is on, holding the attack key never digs by
  hand (`Digging.holdOffHand`). Queued blocks draw as `CellBoxes`; the one being mined grows from
  its middle with eased progress, Start to Finish colour (`mining/render/MiningBox`, after
  Shoreline's Speed Mine). Queued blocks past Dissolve Range drop out. It steps aside
  (no claims, vanilla clicks) when you pick a slot (`HotbarPickEvent`), hit, use or
  place, and resumes after Resume After seconds. Digging is `Services.digging()`
  (`DigService`), shared with Ender Chest Farmer and tasks; Quiet Mining and Early Break
  are Client Settings > Mining (see `service/DigService`). See Grim.md
  "Breaking". Packet Mine below can build on `Digging`.
- **Ender Chest Farmer** · done (needs in-game testing): after Lambda's. Places an ender chest in
  the cell in front of your feet (placement planner, strict, no supports, through
  `Services.actions()`) and breaks it through `Digging` with the fastest pickaxe that has
  no Silk Touch (`Inventories.bestHarvestingTool`), 8 obsidian a chest. Swaps a chest or
  pickaxe up from the main inventory. Keep (chests left alone), Stop At; stops on a full
  inventory, no pickaxe, a blocked cell or being out of reach, and reports what it made.
  Waits while you click or pick a slot. Shares `service/dig/Sightline` and `mining/render/MiningBox`
  with Smart Mine.
- **Packet Mine** · M: queue blocks, break them at exactly the server's break rate
  with the best tool (silent swap), rebreak when replaced. Grim checks break timing,
  so it mines at the server's rate, never faster. Absorbs Auto Tool.
- **Ore Scanner** · M: chunk scanning on a background thread (the one place
  threading clearly pays), updated on block changes, not by rescanning.
- **Vein Miner** · S: mine connected ore with Packet Mine's timing.

### Inventory

- **Armor Manager, Hotbar Replenish, Inventory Cleaner** · done (needs in-game
  testing). Split from Inventory Manager on 2026-10-29, with Elytra Swap folded into
  Armor Manager. Planners in `inventory/armor/` and `inventory/replenish/`.
  **Armor Manager** wears the best pieces (Protection or Blast preference, replaces
  nearly broken ones, never Binding, keeps a worn elytra), may stop you a tick to
  fill a bare slot, waits while its own elytra swap is under way, and holds the
  elytra swap above. **Hotbar Replenish** has no kit: it tops up hotbar stacks and
  refills a slot whose item was used up, never with what Inventory Cleaner calls junk
  (`inventory/cleaner/Junk`, published while the cleaner is on). **Inventory
  Cleaner** throws what is on its list (an `ItemSetting`), from the hotbar too with
  Clean Hotbar; blacklist only, no whitelist (user, 2026-10-29). Toggle it with its
  module bind for looting. All clicks go through `Services.inventory().click()`: one a tick, only
  while the server believes you are still, armour > refill > cleaner, all below Auto
  Totem. Kits per profile were dropped: they break the moment you rearrange your
  inventory to go base hunting.
- **Auto Eat** · done in **Utility** (needs in-game testing): the first module built
  from flow steps. Two Core reflexes around the automation's `Eat` step: a golden
  apple whenever health (absorption not counted) is at or below Apple Health, a
  meal at or below Hunger Below (or hurt below regen hunger, out of a fight), never
  started and given up with an enemy in melee range. Apple settings in an Apples group. Real held
  right-click, so Grim sees vanilla item use; only flows needing the use button pause.
  Hardened 2026-10-28: an eat counts only on the server's consume status, a use the
  server never finishes is let go after its use time at server TPS plus ping, no
  silent swaps while eating. **Silent Swap** keeps the hotbar and hand showing your
  slot (visual only; see Grim.md "Item use").
- **Shulker Preview** · S: a shulker's contents on hover.

### Render

- **Shaders** · done (tested in game 2026-10-09: outline, glow, through walls off, hands, self, skybox): the
  chosen entities and your hands drawn again into an offscreen mask (`adapter.render.shader.Silhouettes`,
  by the game's own renderers) and shaded by `effect/silhouette.fsh`: a fill plus a hard Outline or a
  fading Glow. Targets: Hands, Self, Players, Hostiles, Passives, Neutrals, Crystals, Items, Pearls,
  Experience, Projectiles; Range; Through Walls. Theme Colours supported.
  Skybox (needs in-game testing) replaces the overworld sky (`SkyRenderEvent`) with
  one of two shaders, and cancels all fog (`EffectRenderEvent` FOG) while the shader sky
  was drawn this or last frame, so the Nether/End keep theirs. Space (`effect/sky/space.fsh`): stars at three depths, a galaxy band with
  core and dust lanes, two-colour domain-warped nebulae, slowly turning about a tilted pole.
  Aurora (`effect/sky/aurora.fsh`): 24 stacked slices of a warped ribbon pattern make curtains,
  bright lower edge to second colour, rippling rays, stars through faint parts. Shared
  `core/noise.glsl`, `core/stars.glsl`. Stars, Speed; Theme Colours supported.
- **Projectiles** · done (needs in-game testing): a camera-facing ribbon trail (tapering,
  glow, core line, head dot) behind every arrow, pearl, potion and the rest in the air;
  with Landing, where each will land from `Projectiles.kit().follow` (1.21.4 physics from
  bytecode): the hit face highlighted with snapping corner brackets and a cross on the
  point, flowing dashes along the path, the face filling as it closes in, the entity box if
  it will hit one, and a retract-and-flare on impact. Held: the item in your hand's full
  path and landing before you use it, per frame from `kit.you/view(partialTicks)`.
  Theme Colours supported.
- **Predict ESP** · done, its own module (needs in-game testing): the likeliest
  predicted hitbox `Ticks` ahead from `Core.prediction()` (the auras' Predict), its
  path, optional faint alternative futures by weight, and Reliable/Unsure colour.
- **Block Overlay** · now also Break ESP (needs in-game testing): every block being
  broken (`BlockBreakingEvent` from `ClientWorld.setBlockBreakingInfo`), a fill that
  grows with progress smoothed between the server's 10 steps, labels with who, % and
  time left, and Surround Alert for blocks next to your feet.
- **Theme Colours** convention: a render module offers one `Theme Colours` switch and
  draws every colour through `client.render.ThemeColors`, each colour role at its own
  point on the theme's accent gradient. Block Overlay, Predict ESP and Projectiles have it.
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

Not a module: built (2026-10-09) as `client/automation/`, on Core's flow engine and the
navigation library. See `automation/package-info.java`.

- **Tasks** (`automation/task/`, listed in `Tasks`, run by `.task <kind>`): goto, follow,
  climb, mine, open, close, eat, offhand, fight, retreat, pearl. Built from steps in
  `automation/step/` (Eat, UseBlock, CloseContainer, BreakBlock, Climb, Melee, Throw,
  Equip), each doing one thing the way a player does. Run one at a time by
  `Automation`, stopped by `.task stop` or the Stop Task bind, shown by the Task HUD
  element and the route drawn in the world. Rules: Client Settings > Automation.
- **Planning (2026-10-11), one algorithm per job:** short range is the library's
  `LocalPlanner` (every move simulated) on a worker (`SegmentPlanner`, world read from
  `WorldSnapshot` copies). Walking is the client's own `Driver` per `Trip`, which plans
  the next segment while the current one is walked and splices it in (no stop between
  plans). Long range is D* Lite (`pathing/` module, our own, tested by `:pathing:smoke`)
  over `ChunkTerrain` (2 bits a block per seen chunk), repaired on chunk loads and block
  changes, on its own thread (`LongRangePlanner`). Baritone would replace it via
  `Navigation.setLongRange`.
- **The keys and buttons:** Core's `controls()`, written by the adapter's `MovementKeys`
  (Move Fix consistent) and the client's `service/ControlClicks` (through ActionService).
- **Next:** a long-range provider (Baritone as a `PathProvider` in the adapter, given to
  `Navigation.setLongRange`); then mine-until and build-region tasks, which need breaking
  and placing steps. Letting Baritone drive would fight our rotations, the Move Fix and
  Grim, so prefer it as a planner of coarse routes the local planner walks.

## Suggested order

1. **Inventory modules** (Armor Manager, Hotbar Replenish, Inventory Cleaner): Auto Eat, Packet Mine and Elytra Assist all want items in
   the right place first.
2. **Packet Mine:** finishes the combat loop (break surround → crystal), and opens Mining.
3. **Scaffold:** opens Building, and proves the Move Fix under load.
4. **Trajectories and Predict ESP:** quick wins that show off Core.
5. **The pathing layer, then Travel** (AutoWalk, Elytra Assist, Goto).
