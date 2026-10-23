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

## Strafe extras: where Grim widens instead of simulating

Read from the jar on 2026-10-17. All four are settings on
`client.module.movement.strafe.GrimStrafe`, off by default, and **untested in
game** until this line says otherwise.

| Trick | What Grim does (bytecode) | Karatsuba methods |
|---|---|---|
| **Entity Collide** | No entity-push simulation. `MovementTicker.handleEntityCollisions` (before the travel, same tick) counts pushable entities touching the player's box at the old and new positions grown by 0.2, into `UncertaintyHandler.collidingEntities`. `PredictionEngine.handleStartingVelocityUncertainty` then widens the starting velocity by `0.08 × max(collidingEntities)` on X and on Z, and keeps whichever vector in that box fitted, so the client's velocity carries on from it | `adapter.movement.EntityPush`: `afterTravel()` (from `LivingEntityMixin.karatsuba$travelled`, `travel` RETURN) records the velocity the move left; `serverVelocity()` (velocity, explosion and teleport hooks in `ClientPlayNetworkHandlerMixin`) voids it; `sinceTravel()` is the push since. `GrimStrafe.collide()` at `PlayerMoveEvent` PRE swaps that push for `0.95 × 0.08 × pushers(0.1)` along the heading through `Services.motion().setHorizontal()`. Counted with a smaller box than Grim's, this tick only |
| **Boat Burst** | `UncertaintyHandler.checkForHardCollision()` resets `lastHardCollidingLerpingEntity` when a boat or shulker (not ridden) is within 1.0 of the box at the **start** of the move. `reduceOffset()` takes 1.2 off the offset while `hasOccurredSince(3)`: that tick and the three after. Position only; Grim's velocity is still its own prediction | `GrimStrafe.onMove()` times the burst from `EntityPush.nearHardCollider(0.8)` and asks `adapter.movement.Displacement.next()` for `Burst Distance` (default 1.0, max 1.15) along the heading for 3 ticks. `Displacement.apply()` (from `EntityMixin`, `Entity.move` HEAD, own moves only) adds it to the move, never to the velocity, and refuses it if a block is in the way. Unused bursts are cancelled at `PlayerMoveEvent` POST |
| **Tap Jump** | Vanilla, nothing to read: `LivingEntity.tickMovement` zeroes `jumpingCooldown` on any tick jump is not held, and only counts it down while held | `Inputs.letGoOfJump()` → `Inputs.applyJumpLetGo()` in `ClientPlayerEntityMixin.karatsuba$afterInput`, after hold-still: the move and the input packet both see jump released. `GrimStrafe.tapsJump()` only does it in the air, out of water, lava and climbables, and not with an elytra on |
| **Keep Sprint** | `PacketPlayerAttack`: for a 1.21+ client on a 1.9+ server with no knockback enchant it only raises `maxAttackSlow`; `minAttackSlow` stays 0, so `PredictionEngine.addAttackSlowToPossibilities` keeps the unslowed velocity as a candidate | `adapter.movement.AttackSlowdown.skipWhen()`, registered by `GrimStrafe`'s constructor; `PlayerEntityMixin` skips the `setVelocity(×0.6)` and `setSprinting(false)` in `PlayerEntity.attack` for the local player |

These are the only velocity or position edits meant to pass Grim. Each must
stay inside an allowance Grim grants itself; a new one needs the same bytecode
evidence before it goes in.

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
| **AirLiquidBreak** | See **Breaking** | |

---

## Holding packets (Blink)

Read from 2.3.73's `checks/impl/timer` and its `config/en.yml` on 2026-10-22. Held packets let go together are read by the server in one go, so every check below runs over the whole batch at once.

| Flag | What it flags | Karatsuba methods |
|---|---|---|
| **TimerA** (`Timer`) | Each tick packet adds 50 ms to a balance; flagged when the balance passes the server's clock. A pong arriving after a movement moves `lastMovementPlayerClock` to the send time of the transaction it answers, and the balance is never allowed below that minus `drift` (120 ms). Held **with** their pongs, in order, the balance only catches up to real time: latency, not timer | `adapter.network.PacketHold`: one queue, sent in order, one flush. Blink holds everything (`hold.everything()`) |
| **TimerLimit** | Same balance, but `limitFallBehind` treats the clock as no older than `now − ping-abuse-limit-threshold` (1000 ms by default, -1 off). Once the oldest unanswered transaction is over that, the balance jumps to `now − 1120 ms` and every tick packet after it in the batch adds 50 ms, so a hold longer than about **1000 + 120 − ping ms** flags each extra tick, **with a setback** | `client.module.movement.BlinkModule.holdLimit()`: with Grim Safe on, each hold lasts at most `(Ping Limit + 120 − ping − 150) / 50` ticks (ping from the tab list). Blink always runs as bursts (Mode: Continuous until turned off, Fixed until Max Timeout): each hold is sent when it runs out and another starts a tick later; each new hold starts from a fresh clock, as the batch's last pongs answer recent transactions |
| **NegativeTimer** | The balance more than 1200 ms (`drift`) behind `lastMovementPlayerClock`, while predictions are being checked. With setback | Never reached by a hold inside TimerLimit's budget |
| **TickTimer** | See above: a flying packet before its `CLIENT_TICK_END`. A hold that took movement and not the tick end (Blink's old "Packets: Movement") delivers a run of flying packets with no tick end between: flagged on every one | Blink no longer holds by kind. `PacketHold.isSplitting()` makes `ActionService` and `DigService` wait while any module does |

Not Grim, and **unverified** (from memory of the vanilla server, not read from this one): the vanilla server's "moved too quickly" counts at most 5 move packets a server tick (more is treated as 1), so a batch moving the player over about 10 blocks (17 gliding) from where the server tick began is set back. A hold within the TimerLimit budget on foot stays far inside that.

---

## Breaking

Read from the 2.3.73 bytecode on 2026-10-18. Grim gets every `PLAYER_DIGGING`
packet (START, FINISHED, CANCELLED = ABORT). Some checks run on the packet. The
others are queued and run on the **next flying packet**, before its prediction
("post-flying"). Post-flying uses:
- the position of the flying packet **before** the dig (`lastClaimedPosition`);
- `lastYaw/lastPitch` = the rotation of the previous look packet;
- `yaw/pitch` = the new packet's rotation, but only if it arrives under 15 ms after the dig packet; otherwise the old one.

Eye heights for 1.14+ are always {0.4, 1.27, 1.62}. The movement threshold is 0.0002 (1.18.2+).

All breaking goes through `adapter.game.player.Digging`. It calls vanilla
`updateBlockBreakingProgress` from `BlockBreakingMixin` at the head of
`MinecraftClient.handleBlockBreaking`, the game's own place in the tick, and then
swings. So the packets, the speed, the 5-tick pause after a block, and the
abort/restart on a held-item change are all the game's.

| Flag | What it flags | Karatsuba methods |
|---|---|---|
| **AirLiquidBreak** | START/FINISHED on air, water, lava, bubble column or moving piston; on a light block unless one is held; FINISHED on hardness -1 | `Digging.onHandleBlockBreaking()` refuses any block whose outline is empty (as the crosshair does). `Digging.breakable()` also refuses hardness < 0 |
| **FarBreak** | START/FINISHED where the nearest eye height is more than `BLOCK_INTERACTION_RANGE` from the **full cube**. Measured from the position before the next movement packet. No buffer | `DigService` / `Sightline.meets()` require the aim point itself within `Players.blockReach()` of the current eyes. That is stricter |
| **FastBreak** | Milliseconds. FINISHED earlier than `ceil(1/damage)·50` ms after START, over a balance of 1000 ms (−10% per on-time dig, only digs ≥25 ms early count). START less than 275 ms after the last FINISHED adds `300 − d` to a second balance (limit 1000). Damage is taken from the item held at START, raised (never lowered) by the item held at each swing. Air /5 and water /5 as vanilla | The game's own progress. The tool is in hand before START (`SmartMineModule.holdTool()` waits for the selection). The game's 5-tick `blockBreakingCooldown` gives 300 ms between blocks |
| **MultiBreak** | A second START/FINISHED in one tick with a different position **or face**. CANCELLED is ignored | One `updateBlockBreakingProgress` call a tick. The face is the one the server's look enters, kept for the dig |
| **NoSwingBreak** | A tick with a START or FINISHED and no arm-swing packet | `Digging` swings after every call that returns true, as the game does. `DigService` raises Interaction's Swing NONE to PACKET |
| **PositionBreakA** | Eye segment (y+0.4 to y+1.62) strictly behind the dug face's plane | The face is the one a ray from the eyes enters, so the eyes are in front of it |
| **PositionBreakB** | After an ABORT with a face other than DOWN, any dig packet other than a START with that face | The game's `cancelBlockBreaking` sends DOWN. Its switch sends ABORT(old, new face) then START(new, new face) |
| **RotationBreak** | Post-flying: no ray from the eye heights along (lastYaw, pitch), (yaw, pitch) or (lastYaw, lastPitch) meets the full cube, within reach. No occlusion. After one failure it also checks pre-flying, and cancels | Dig only while `Players.serverRotation()` (the last sent) already lands on the block from the current eyes (`Sightline.faceLookedAt`). Keep requesting the same aim through the dig, so the packet straight after it looks there too |
| **WrongBreak** | FINISHED on a block other than the last STARTed one. Also cancelling the same block twice with no START between | The game's own state machine |
| **InvalidBreak** | Face id outside 0–5 | Only `Direction` values reach the game |
| Not read | BadPackets, MultiActions, PacketOrder (B/E/F/I/L), Post checks that touch digging | The game never digs in a tick in which an attack, click, use or swap went out: `ActionService.actedThisTick()`. `DigService` then calls `Digging.pause()`, which neither digs nor cancels. No digging with a screen open or an item in use |

**FastBreak in full** (re-read 2026-10-22). On FINISHED: `early = ceil(1/maxDamage)·50 − (now − startBreak)`, in
server wall-clock ms between the START and FINISHED packets arriving. If `early < 25` the balance is ×0.9;
otherwise `early` is added. Over 1000 → flag, and the break is **cancelled** (`shouldModifyPackets`). Both
balances are clamped to ±max(1000, transaction ping) before each update. `startBreak` is START's arrival
time, less 50 ms if there was no earlier target. `maxDamage` is the best damage seen on any flying packet
since START (the held item and ground state at each), never lowered. The position of FINISHED is not compared.
So finishing early is a **budget**: just under 1000 ms of total earliness, which each on-time finish shrinks
by 10%. Vanilla's server accepts a finish at ≥ 70% progress; Grim is the only limit.

**Only START and FINISHED are checked.** NoSwingBreak and RotationBreak look only at those two dig packets
(CANCELLED is skipped), and the game sends no dig packet on the ticks in between. So the swing and the look
matter only on the start tick and the finish tick. Between them the head can be anywhere, and no swing is
needed, as far as the breaking checks go. (BadPackets and PacketOrder are still unread.)

None of the breaking checks reads held-slot changes. A slot change mid-dig makes
the game restart the dig itself.

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
6. **Don't touch velocity**, not even knockback, explosions or currents. The one
   exception is staying inside an allowance Grim grants itself, with bytecode
   to show it: see **Strafe extras**.
7. **No fabricated packets**: no extra flying packets, no fake slot or use
   packets, no cancelling server packets to hide state.
8. **Break only through `adapter.game.player.Digging`**, looking at the block
   with the rotation the server already has, tool in hand before the start.
   Never send digging packets by hand.
9. **Inventory clicks only through `Services.inventory().click()`**: standing
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
| Shoreline V2 SpeedMine "Speed" < 1 (finish at that share of progress, every block) | FastBreak: each finish adds `(1 − Speed) · dig time` to the balance. Obsidian with netherite Efficiency V (43 ticks) at 0.7 is about 650 ms early, so the second one is cancelled |
| Shoreline V2 SpeedMine "GRIM" packets (a second START at `pos.below(2000)`) | Points FastBreak at a void block that breaks at once, but MultiBreak (a second START in the tick, other position) and FarBreak (no world-bounds exemption) flag it |
| Shoreline V2 SpeedMine "Rotate" off (its default) | RotationBreak on START and FINISHED |
| End-of-tick clicks | Post |

## Unverified

- Entity pushing (No Push Entities): not simulated; Grim widens by 0.08 per
  entity (see **Strafe extras**). Ignoring a push is inside that box, untested.
- Strafe extras (Entity Collide, Boat Burst, Tap Jump, Keep Sprint): read from
  the jar, not yet tested in game.
- Fishing-rod pull ignoring: looks accepted, untested in game.
- Anything not listed above has not been read from the jar yet; check the class
  in `checks/impl/` before relying on it.
