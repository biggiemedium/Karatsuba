# Grim

What Grim checks, what it flags, and exactly which code in Karatsuba keeps each
check clean. Grim is on about 90% of the servers this client plays on, so treat
this file as the rulebook for any new module, service or adapter hook.

**Version:** GrimAC **2.3.73**, the dev server's copy
(`~/.gradle/caches/run-task-jars/plugins/paper/modrinth/grimac/grimac-1FIGlM6Q.jar`).
Everything here was read from that jar's bytecode (`javap -c`), not from wikis or
old client code, except what is listed under **Unverified**. Re-check after a
Grim upgrade: the classes are in `ac/grim/grimac/checks/impl/`.

**Paths** below are relative to `dev.px.karatsuba`. `adapter.*` may touch
Minecraft; `client.*` may not. `core:` means the vendored Core library.

---

## How Grim works

- **It simulates you.** Every movement packet, Grim re-runs vanilla movement
  physics from the previous position and tries to reproduce the position you sent.
  The gap is the *offset*. Too big and it's a Simulation flag, usually with a
  setback (a teleport back to the last good position).
- **From discrete keys.** It only tries inputs of -1, 0 or 1 per axis, the eight
  directions plus none (`PredictionEngine.loopVectors`).
- **From the keys you report (1.21.2+).** Since 1.21.2 the client sends which
  keys it holds (`PlayerInputC2SPacket`: forward, back, left, right, jump, shift,
  sprint), and Grim narrows its guesses to those (`PacketStateData.knownInput`).
- **Along the yaw in the movement packet** (`GrimPlayer.yaw`), not the camera's.
- **With pings (transactions)** around server events (knockback, explosions,
  block changes, teleports), so it knows the exact tick your client applied them.
- **With uncertainty where vanilla is ambiguous**: fishing-rod pulls, fireworks,
  pistons and similar widen what it accepts.

---

## Post: tick order

| Flag | What it flags | Karatsuba methods |
|---|---|---|
| **Post** | For 1.21.2+ the tick packet is `CLIENT_TICK_END`. A block placement, entity interaction, item use or dig sent **after** it is held, and flagged if a transaction reply arrives before the next tick-end | `client.service.ActionService.start()` subscribes `run()` to `TickEvent` **PRE at `Priority.HIGHEST`**, which is where vanilla's `handleInputEvents` presses buttons. Every attack, click, use and offhand swap goes out from `run()` → `execute()` / `executeOffhand()` → `send()`. Modules never send these themselves; they call `Services.actions().submit(owner, Action...)` |

The first version sent at the end of the tick and was flagged. The assumption
that "end of tick looks the same to the server" was wrong.

---

## Simulation: movement

| What would flag | Karatsuba methods |
|---|---|
| **Walking under a silent rotation**: the move uses the camera's yaw, the packet says another | `adapter.game.player.Rotations.beforeMovementTick()` resolves the rotation at the head of `tickMovement` and calls `swapInSilent()`, so the silent yaw is in place for the move **and** the packets. `afterMovementPackets()` → `restore()` puts the camera back. Switched by `Rotations.movementCorrection(BooleanSupplier)`, wired in `client.settings.interaction.InteractionSettings.onRegister()` to `moveFix()` (default on) |
| **Reported keys don't match the yaw** (Grim narrows to `knownInput`) | `Rotations.correctInput(Input)`, called right after `Input.tick()`, rewrites `input.playerInput` and `movementForward/Sideways` using `core: MovementCorrection.correct()` (only when `MovementCorrection.isNeeded()`, more than 22.5° off). STRICT mode: the result is always -1, 0 or 1 per axis |
| **Drift from STRICT's eight directions** (up to 22.5°) | Scaffold picks yaws Move Fix maps exactly: `client.module.building.ScaffoldModule.lookAt()` tries the camera's yaw + 180°, then 45° steps (`YAW_ORDER`), so W becomes exactly S |
| **A jump the keys don't explain** (`player.jump()`, editing velocity) | Real jump key for one tick: `client.module.movement.TickKey.press()` → `adapter.game.player.Inputs.hold(JUMP)` at `TickEvent` PRE, released by `TickKey.release()` from a plain `TickEvent` POST subscription. Used by `ParkourModule.onTickStart()`, `StepModule.onTickStart()`, `ScaffoldModule.telly()`. `JumpResetVelocity.onTickStart()` / `letGo()` do the same by hand |
| **A crouch the keys don't explain** (faking sneak for the ledge check only) | Real sneak key for one tick: `TickKey(SNEAK).press()` in `SafeWalkModule.onTickStart()` and `ScaffoldModule.normal()` (Edge Sneak). Vanilla's own `clipAtLedge` does the stopping, and Grim sees shift |
| **Predicting edges and jumps from guesses** | `client.module.movement.Ledges.ahead()` / `held()` step `core: Core.simulation().trace()` with the player's own physics (`adapter.movement.MotionBridge.currentState()`), so a module only presses a key when vanilla physics says it matters |
| **Silent multi-tick item use** (eating while showing another slot) | Not built, on purpose. Grim tracks `USE_ITEM`, `RELEASE_USE_ITEM` and slot changes and applies the use slowdown. `InventoryService.silently()` swaps and swaps back inside one call, so no silent hold outlives a tick |
| **No Push liquids / blocks** | Not built. Grim simulates fluid pushing and push-out-of-blocks (`PlayerBaseTick.updateFluidHeightAndDoFluidPushing`, `moveTowardsClosestSpace`) |

---

## Aim

| Flag | What it flags | Karatsuba methods |
|---|---|---|
| **AimModulo360** | A raw yaw change of more than 320° in one packet while \|yaw\| < 360 (unless the previous change was ≥ 30°) | `Rotations.unwrapped(yaw, reference)` writes every yaw as the same angle nearest the reference. `swapInSilent()` uses `lastSentYaw` (recorded in `afterMovementPackets()`), and `Sink.apply()` (CLIENT mode) uses the current yaw. The sent yaw only ever changes by the turn itself, however many times the player has spun round |
| **AimDuplicateLook** | A rotation packet identical to the previous one | `Rotations.swapInSilent()` sets the player's fields **before** the game builds its packets, so vanilla's own "did the look change?" choice decides whether a look is sent. Packets are never edited in flight |

---

## Sprint (none of these are about direction)

| Flag | What it flags | Karatsuba methods |
|---|---|---|
| SprintA | Sprinting with food < 6 | Vanilla's sprint rules are left alone; `SprintModule` only calls `Players.setSprinting()` while `Players.movingForward()` |
| SprintB | Sprinting while sneaking, with the sneak slowdown below 0.8 | `SprintModule` (While Sneaking off by default) |
| SprintC | Sprinting while slowed by using an item | Vanilla |
| SprintD | Starting to sprint while blind | Vanilla |
| SprintE | Still sprinting after a hard horizontal collision | Vanilla |
| SprintF | Sprinting while gliding (1.21.4) | Vanilla |
| SprintG | Sprinting while touching water but not swimming | Vanilla |

Under a backwards silent yaw the corrected keys are "back", so vanilla drops
sprint by itself. That is consistent and fine.

---

## Knockback and explosions

| Flag | What it flags | Karatsuba methods |
|---|---|---|
| **KnockbackHandler** ("ignored knockback") | Your movement after a velocity packet doesn't contain it. Flags and **sets you back with the knockback applied again** | `client.module.combat.velocity.JumpResetVelocity`: `onKnockback(KnockbackEvent)` marks the hit (ground only, Chance %), `onTickStart()` holds the real jump key at the start of the tick it applies to, and `letGo()` releases it at POST. The knockback is taken in full and bled off by the jump. The `KnockbackEvent` hook in `adapter.mixin.network.ClientPlayNetworkHandlerMixin` runs **after `forceMainThread`**, on the game thread |
| **ExplosionHandler** | Same, for explosion knockback | Nothing cancels or scales it |

Grim exempts knockback only on an accepted teleport or while in a vehicle
(`MovementCheckRunner` → `KnockbackHandler.forceExempt()`). Nothing a client can
send in between (extra position packets, digging packets) earns an exemption.

`CancelVelocity` still exists, but on Grim the setback re-applies the knockback,
so it can't win there. Scaled, Buffer, Shoreline's Normal / Grim V2 / Walls / Jump
/ Conceal are all forms of dropping or shrinking the packet, and all are flagged.

Fishing-rod pulls are an uncertainty box built from zero
(`UncertaintyHandler.fishingRodPullBox`), so ignoring the client-side pull is
probably accepted. Not built; see **Unverified**.

---

## Placing blocks

| Flag | What it flags | Karatsuba methods |
|---|---|---|
| **RotationPlace** | After the **next** movement packet, rays for (yaw, pitch), (lastYaw, pitch) and (lastYaw, lastPitch) are cast from the **new** position over every possible eye height. A click none of them hit is flagged ("post-flying"); after one failure it also checks before the flying packet ("pre-flying") | **Turn, then click a tick later:** `ActionService.claimRotation()` (or the module's own `Core.rotations().request()`) turns at `RotationClaimEvent`; the click goes out next tick from `run()`. **Only if the server's look hits:** `ActionService.execute()` (CLICK) raycasts `Targets.raycast(eye, Players.serverRotation(), reach)` and returns `TURNING` unless it lands on that block and face, then sends the hit where the ray actually lands. **Robust looks:** `ScaffoldModule.lookAt()` keeps the held rotation if it already hits; otherwise it scans pitch 90→30 on snapped yaws, and only accepts a look that `hits()` from both `eye + velocity` (where the server holds the rotation from) and `eye + 2·velocity` (where Grim re-casts it). The fallback is `aimOn()`, a random point in the inner half of the face, never an edge. `ScaffoldModule.hitPoint()` sends where the look lands even with raytrace off |
| **DuplicateRotPlace** | The yaw change before a click (over 2°) equals the previous click's to 0.0001° | `ScaffoldModule.turnToPlace()`: if this turn would match `lastPlacedTurn` within 0.5°, it stops a degree short (`step = turn - 1`) and finishes next tick. `lastPlacedTurn` is recorded in the action's `then(Outcome.SENT)` from `Players.serverRotation()` minus `sentBefore`. Holding the current look when it already hits (`lookAt()`) means a turn of zero, which never counts |
| **PositionPlace** | The eyes (plus the movement threshold) are not past the plane of the clicked face | Plan with the strict style: `adapter.combat.MinecraftCombatKit.strictStyle()` → `combat: PlacementStyle.strict()`, which includes `FaceRule.facingEye()`. Built by `ScaffoldModule.build()`, `SurroundModule.onEnable()` (Strict on) and the other placing modules. Scaffold's Normal mode holds the player at the ledge with Edge Sneak (`ScaffoldModule.normal()` → `walksOff()` → `sneak.press()`) until their eyes are past the face |
| **MultiPlace** | A second, **different** placement before the next tick packet | `ActionService.run()` sends at most one right-click per tick; `Claims` keeps one winner per button. Planners are built with `.perTick(() -> 1)` (`ScaffoldModule.build()`). No burst placement anywhere |
| **FabricatedPlace** | Cursor (hit inside the block) outside 0–1 (0–1.5 for blocks taller than a cube) | The hit point is a real ray hit (`ActionService.execute()` → `Targets.raycast()` → `seen.getPoint()`), sent by `adapter.game.player.Interactions.click(block, face, hit, hand, swing, settled)` |
| **AirLiquidPlace** | Placing against air or liquid | `combat: PlacementPlanner.plan()` only clicks real supports (`Clicks` built from `GameBlocks::isSupport`); `supports(1)` adds a block to click first when there is none. Air place is refused by `ActionService.execute()` unless `InteractionSettings.airPlace()` |
| **FarPlace** | Out of reach | `ActionService.execute()` checks `hit.distanceTo(eye) > Players.blockReach()` from the eyes the server knows (`OUT_OF_REACH`) |
| **InvalidPlaceA/B** | Invalid face or cursor values | No placement packet is ever hand-built: `Interactions.click()` goes through the game's sequenced interaction (`interactBlock`) |

Ghost blocks are not a flag, but they cause the next ones: `adapter.world.PlacementSync`
follows each placed block until the server confirms it and takes back any it never
answers (`Action.settled()` hears which), so a module never plans from a block the
server doesn't have.

Scaffolding-block placements are exempt from RotationPlace and PositionPlace.

---

## Inventory and packets

| Flag | What it flags | Karatsuba methods |
|---|---|---|
| **BadPacketsA** | A held-slot packet repeating the last slot sent | `client.service.InventoryService.silently()` and slot claims select through `adapter.game.item.Inventories.selectSlotNow()`, which calls vanilla `syncSelectedSlot` (via `ClientPlayerInteractionManagerInvoker.karatsuba$syncSelectedSlot()`). That sends only when the slot differs from `lastSelectedSlot` |
| **MultiActionsC** | A `CLICK_WINDOW` while Grim believes the player is sprinting (unless swimming in the air), or, for 1.21.2+, while the last input packet held forward, back, left, right **or jump** (`KnownInput.moving()`). Sneaking counts only for clients older than 1.15. Skipped the tick the server opened a window | Every click waits in `InventoryService.standingStill()`: `!Inputs.reportedMovement() && !Players.sprinting()`. `Inputs.reportedMovement()` reads `player.input.playerInput` at the start of the tick, which is exactly the input packet sent at the end of the last one. Used by `InventoryService.runClick()` and `toOffhand()`. A click marked `InventoryClick.stopping()` calls `Inputs.holdStill()`, which `ClientPlayerEntityMixin.karatsuba$afterInput` applies after the Move Fix: one tick of no movement keys and no sprint, then the click goes out at the start of the next tick. Hotbar items reach the offhand by the swap-hands key (`Inventories.swapHotbarWithOffhand()`), which is not a click |
| **MultiActionsD** | A `CLOSE_WINDOW` under the same conditions as MultiActionsC | Nothing sends a close by hand. Clicks are made without opening the screen, as the server keeps the player's own inventory open anyway |
| **PacketOrderA** | A pick-up click in the same tick as a shift-click, or the other way round | `InventoryClick` has only swap, quick-move and throw kinds; no pick-up clicks, so nothing is ever left on the cursor. At most one click a tick: `InventoryService.runClick()` checks `clickedThisTick()`, and the offhand's click counts |
| Not checked in 2.3.73 | Clicks in the same tick as an attack, place or use; clicks with the inventory screen closed; how fast clicks come. `Post` counts clicks only for clients older than 1.13. `PacketOrderK` only reads the old "opened inventory" status packet, which 1.21 never sends | Still one click a tick, `Inventory Delay` ticks apart (Client Settings → Interaction), and **Open Inventory Only** for anticheats that do want the screen open |
| **PacketOrderG** | A swap-hands packet in the same tick as an attack, dig or right-click | `ActionService.run()` runs `executeOffhand()` first; if it sent (`swapped`), the tick's attack and click are reported `WAITING` and go next tick |
| **TickTimer** (1.21.2+) | A movement packet before a `CLIENT_TICK_END` has arrived since the last one, or more than one movement packet per tick end. Flags **with a setback** | Never send a movement packet of our own. Rotations ride in vanilla's own packet (`Rotations.swapInSilent()` before `sendMovementPackets`), so there is exactly one per tick |
| **AirLiquidBreak** | A start or finish digging packet on air or liquid | Never send digging packets by hand; no module digs a block that isn't there |

---

## Rules for new code

1. **Actions at the start of the tick**, through `Services.actions().submit()`.
   One click and one attack per tick.
2. **Real keys only.** Jump, sneak and sprint are key presses held for the tick
   (`TickKey`), released at tick end.
3. **Silent rotations need Move Fix.** Without it, every walked tick under a
   silent yaw is a Simulation flag. Show it (Scaffold displays "Move Fix off").
4. **Look before you click.** Turn this tick, click next tick, and make sure the
   look hits from where the eyes will be after the next move.
5. **Never repeat a turn size before consecutive placements.**
6. **Don't touch velocity**, not even knockback, explosions or currents.
7. **No fabricated packets**: no extra flying packets, no fake slot or use
   packets, no cancelling server packets to hide state.
8. **Inventory clicks only through `Services.inventory().click()`**: standing
   still by what was *reported* (no movement keys or jump, no sprint), one a tick,
   whole stacks only. Never repeat a slot packet.

## Tried and rejected

| Idea | Why it fails |
|---|---|
| Velocity Cancel / Scaled / Buffer | KnockbackHandler setback re-applies the knockback |
| Shoreline V2 Velocity (all modes but fishhook) | Same; NoPush liquids/blocks are simulated |
| Shoreline V2 Scaffold (burst + gap fill) | MultiPlace; one rotation per tick can't serve several clicks |
| Leapfrog Scaffold | `player.jump()` (Simulation), click before rotate (RotationPlace), centre-of-block hit, equal turn deltas (DuplicateRotPlace) |
| Rise Scaffold extras | Direct `jump()`, cancelled slot packets, random use packets, timer, fake sneak slowdown |
| MeteorPlus Grim Cancel | Drops the velocity (KnockbackHandler), then sends an extra position packet (TickTimer) and a finish-digging packet on the air at your feet (AirLiquidBreak), from another thread at any point in the tick (Post) |
| MeteorPlus Grim Cancel v2 | Same packets on every movement packet. Its `canCancel` flag can never become true, so as written it does nothing at all |
| MeteorPlus Grim Skip | Drops the velocity and the next 6 movement packets: Grim then sees 7 ticks of movement in one packet without the knockback (Simulation, KnockbackHandler) |
| Silent eating | Item use tracked from packets |
| End-of-tick clicks | Post |

## Unverified

- Entity pushing (No Push Entities): no handling found in the bytecode either way.
- Fishing-rod pull ignoring: looks accepted, untested in game.
- Anything not listed above has not been read from the jar yet; check the class
  in `checks/impl/` before relying on it.
