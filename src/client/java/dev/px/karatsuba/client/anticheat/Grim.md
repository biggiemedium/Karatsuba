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

## Transactions: how to recognise Grim

Read from the 2.3.73 bytecode on 2026-10-27 (`GrimPlayer`, `TickRunner`,
`ClientVersionSetter`, `PacketEntityReplication`, `AbstractTickEndEvent`). This is
the fingerprint `core: network/anticheat` would match; not yet confirmed in game.

| What | Bytecode | So the client sees |
|---|---|---|
| **Packet** | `GrimPlayer.sendTransaction(boolean)`: `WrapperPlayServerPing(id)` on 1.17+ servers, `WindowConfirmation(0, id, false)` before | `CommonPingS2CPacket`, which the describer already gives `withTransaction` |
| **Ids** | `id = (short) (-1 * (transactionIDCounter.getAndIncrement() & 0x7FFF))`, counter a new `AtomicInteger(0)` per `GrimPlayer` | **0, -1, -2 … -32767, then 0 again**: steps of exactly -1, never positive, never below -32767. Starts at 0 on every join (a new connection is a new `GrimPlayer`) |
| **Idle rate** | `TickRunner` runs `ClientVersionSetter.tick()` async at a fixed rate of 1 tick; it calls `pollData()`, which sends one when `lastTransSent + 80 < currentTimeMillis()` | **A floor of one every ~100 ms (~10/s)** whatever the player does, on wall-clock time, so a lagging server does not slow it. Starts once the first event ping has set `lastTransSent` |
| **Event pings** | `sendTransaction()` around: velocity and explosions (`KnockbackHandler`, `ExplosionHandler`), teleports and setbacks, world border, abilities, game state, respawn, own metadata, entity replication, block changes (`BasePacketWorldReader`, `CompensatedWorld`), camera, mount and dismount | Extra pings on top of the floor, in bursts around those packets: more than 10/s in a busy area, about 10/s standing alone |
| **End-of-tick ping** | `AbstractTickEndEvent.onEndOfTick` sends one per tick, but only if `Reach.enable-post-packet` (default **false**; the dev server's is false) | Usually absent. A server that turns it on sends at least 20/s |
| **Who gets them** | Only in `PLAY` state. `grim.exempt` defaults to `false`, so ops are **not** exempt. A player with Grim disabled stops getting pings 15 s after their clock falls behind | The dev player, opped, gets the full stream |
| **Brand, channels** | `ClientBrand` only *reads* the client's `minecraft:brand`; no Grim class writes a brand or registers a `grim:` channel | **No name to match.** Grim can only be recognised by its transactions, so a detection is `LIKELY` at most |

What this means for `core: TransactionTracker` and `client.anticheat.AntiCheats`:

- **Signature:** decrementing, every id in `[-32767, 0]`, about 10/s or more. No
  other evidence is available, and a fork or copy would look the same.
- **Zero is the problem.** The first id after a join, and every id after a wrap,
  is `0`, which the tracker counts as positive. Any sample that includes it reads
  `Sign.MIXED`, not `NEGATIVE`. Match on "never positive" (`highestId <= 0`), not
  on `Sign.NEGATIVE`.
- **`LIKELY_RATE = 10` is exactly Grim's idle floor.** A player standing alone
  sits at about 10/s and can flip between `POSSIBLE` and `LIKELY` as scheduler
  jitter moves it. Put the generic threshold below the floor (e.g. 8), or let the
  numbering decide instead of the rate.
- **The wrap:** -32767 → 0 is one odd step in thousands, which the 80% majority
  already ignores. At ~10/s it takes about 55 minutes to get there.

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
| **A jump the keys don't explain** (`player.jump()`, editing velocity) | Real jump key for one tick: `client.module.movement.TickKey.press()` → `adapter.game.player.Inputs.hold(JUMP)` at `TickEvent` PRE, released by `TickKey.release()` from a plain `TickEvent` POST subscription. Used by `ParkourModule.onTickStart()`, `StepModule.onTickStart()`, `ScaffoldModule.telly()`, and Velocity's `client.module.combat.velocity.JumpReset.onTickStart()` |
| **A crouch the keys don't explain** (faking sneak for the ledge check only) | Real sneak key for one tick: `TickKey(SNEAK).press()` in `SafeWalkModule.onTickStart()` and `ScaffoldModule.normal()` (Edge Sneak). Vanilla's own `clipAtLedge` does the stopping, and Grim sees shift |
| **Predicting edges and jumps from guesses** | `client.module.movement.Ledges.ahead()` / `held()` step `core: Core.simulation().trace()` with the player's own physics (`adapter.movement.MotionBridge.currentState()`), so a module only presses a key when vanilla physics says it matters |
| **Silent multi-tick item use** (eating with the server holding another slot than the client) | Not built, on purpose. Grim tracks `USE_ITEM`, `RELEASE_USE_ITEM` and slot changes and applies the use slowdown. `InventoryService.silently()` swaps and swaps back inside one call, so no silent hold outlives a tick. Auto Eat's **Silent Swap** is visual only: the food is really selected on both sides (`InventoryService.conceal()`), and only the hotbar highlight, item popup and first-person hand draw the player's slot (`adapter.game.item.ShownSlot`, `InGameHudMixin`, `HeldItemRendererMixin`) |
| **Eating out of step with Grim** (see **Item use** below) | `client.automation.step.Eat`: done only on the server's `CONSUME_ITEM`; a deadline lets go of a use the server never finishes; `ActionService` refuses a silent swap while the main hand is in use; `InventoryService` lets a player's pick win outright mid-use |
| **No Push liquids / blocks** | Not built. Grim simulates fluid pushing and push-out-of-blocks (`PlayerBaseTick.updateFluidHeightAndDoFluidPushing`, `moveTowardsClosestSpace`) |

---

## Being inside blocks (Phase)

Read from 2.3.73 (`checks/impl/prediction/Phase`) and vanilla 1.21.4 bytecode on 2026-10-28.

| Fact | Where |
|---|---|
| **Phase** flags, with setback, only a movement whose new box meets a block collision box the **previous tick's box did not**. Skipped for teleports (`isTeleport`) and while setbacks block offsets. Ignored for anvils and chests only on 1.8 clients | `Phase.onPredictionComplete` |
| So being inside blocks is not flagged in itself: a server teleport into them, or a block whose shape grows around you (a door, trapdoor or fence gate closing), meets boxes the old box already met, or comes with a teleport | same |
| Vanilla collision ignores a block whose cell already holds the moving box's leading face: `VoxelShape.calculateMaxDistance` only checks cells past `getCoordIndex(box.max − 1e-7)` (and the mirror for negative moves). A box already in a block moves freely through it, and is stopped only by the next solid cell. Inside a one-thick wall, that means out the other side. Grim's collision is the same, so this is simulated, not flagged | `VoxelShape.calculateMaxDistance` |
| The client pushes itself out: each tick, for each of four points ±0.35 × width round the box, a column that collides sets the velocity on one axis to 0.1 toward the nearest free neighbour. Client-side only; the server never pushes | `ClientPlayerEntity.tickMovement` → `pushOutOfBlocks`, `wouldCollideAt` |
| **Grim does not require the push.** For 1.14+ clients, out of a vehicle, where a column *suffocates* it only adds ±0.15 uncertainty on that axis (and marks the 0.03 estimator as pushing); its base prediction has no push. Skipping the push is legal, and closer to Grim's prediction than vanilla. Karatsuba: `adapter.movement.BlockPushOut`, `ClientPlayerEntityMixin.karatsuba$pushOutOfBlocks`, used by Phase's **Stay** | `PlayerBaseTick.moveTowardsClosestSpace` → `moveTowardsClosestSpaceModern` |

| A landing pearl teleports its thrower to **where the pearl was at the start of the tick it hit** (`getLastRenderPos()`), not the hit point, on 1.21.2+ servers. A pearl thrown at your feet hits in its first tick and puts you back where it left your hand. Older servers used the hit point, which is what Alien's pearl phase aims for | `EnderPearlEntity.onCollision` (1.21.4) |

So: **the only way into a wall is a server teleport**, and an ender pearl is the only one a player can aim (chorus fruit picks a random spot the player fits). **Once in, real movement keys may walk through the blocks already overlapped**; entering any new solid block is a Phase flag. Position-packet clips (vclip, NoClip, "packet phase") are a Phase flag and a TickTimer flag.

## Item use

Read from 2.3.73 (`PacketPlayerDigging`, `NoSlow`) and vanilla 1.21.4 bytecode on 2026-10-28.

| Fact | Where |
|---|---|
| Grim marks the player slowed on `USE_ITEM` when *its* view says the item can be used: food only below 20 hunger (Grim's own `food`, from the server's health packets) unless `canAlwaysEat`; never an item on its compensated cooldown | `PacketPlayerDigging.handleUseItem` |
| It stops on `RELEASE_USE_ITEM`; on `HELD_ITEM_CHANGE` to another slot (main hand, and resets the server's use where the platform does); and at a tick end when the use slot is no longer the last slot sent or the hand is empty | `PacketPlayerDigging.onPacketReceive` |
| **NoSlow** flags, with setback, the second tick in a row that Grim believes the player slowed but the best prediction needed full speed (offset > 0.001) | `NoSlow.onPredictionComplete` |
| The vanilla server ends a main-hand use on any held-slot packet to a different slot | `ServerPlayNetworkHandler.onUpdateSelectedSlot` → `clearActiveItem` |
| The client never finishes an item: `tickItemStackUsage` only consumes when `!world.isClient`. It finishes on the server's entity status 9 (`ServerPlayerEntity.consumeItem` → `PlayerEntity.handleStatus(9)`), and follows the server's own living-flags metadata for using or not (`ClientPlayerEntity.onTrackedDataSet`) | so "the client stopped using" means finished or cut short, and status 9 tells which |
| Letting go of use calls `ClientPlayerInteractionManager.stopUsingItem`: `syncSelectedSlot()` then `RELEASE_USE_ITEM` | release before any slot change, in the same tick |

So: the client and Grim must agree on "using" every tick. Hazards are a use the server refused without saying (client stuck slowed, Grim not: Simulation), and a slot change the server sees but the client does not act on (Grim stops, client still slowed). `Eat` covers both.

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
| **KnockbackHandler** ("ignored knockback") | Your movement after a velocity packet doesn't contain it. Flags and **sets you back with the knockback applied again** | `client.module.combat.velocity.JumpResetVelocity` passes (and Blast Protection mode, which shares it): `JumpReset.onKnockback()` marks the hit (ground only, Chance %, velocity packets only), `JumpReset.onTickStart()` presses a `TickKey(JUMP)` at the start of the tick it applies to, released at POST. The knockback is taken in full. `CancelVelocity` drops it and is flagged. The `KnockbackEvent` hook (`Source.VELOCITY`) in `adapter.mixin.network.ClientPlayNetworkHandlerMixin.karatsuba$onEntityVelocityUpdate` runs **after `forceMainThread`**, on the game thread |
| **ExplosionHandler** | Same, for explosion knockback | `CancelVelocity` drops it (its **Explosions** setting, on by default) and is flagged. The hook is `ClientPlayNetworkHandlerMixin.karatsuba$explosionKnockback`, a `@ModifyExpressionValue` on `ExplosionS2CPacket.playerKnockback()` in `onExplosion`, posting `KnockbackEvent` with `Source.EXPLOSION`. Vanilla *adds* that push (`addVelocityInternal`), so a cancel removes the push but keeps the sound and particles. Jump Reset ignores explosions. **Passes:** `BlastProtectionVelocity` wears Blast Protection while a crystal could go off (`ArmorPlanner.preferBlastFor()`, which Inventory Manager honours through `ArmorPlanner.effective()`), so the server sends a smaller push; see **Less knockback that Grim never sees** |

Grim exempts knockback only in the cases listed under **How the two handlers
score a push**. Nothing a client can send in between (extra position packets,
digging packets) earns an exemption.

Cancelling and scaling knockback are **allowed** in this client (see rule 6),
but outside the allowances below none of them pass Grim. Cancel, Scaled,
Buffer, Shoreline's Normal / Grim V2 / Walls / Jump / Conceal all drop or shrink
the push, and all are flagged.

### How the two handlers score a push (read 2026-10-27)

- **Offset.** `PredictionEngine.doPredictions` scores every candidate vector
  that includes the push: `sqrt(UncertaintyHandler.reduceOffset(distanceSquared))`
  against the real movement, into `KnockbackHandler` / `ExplosionHandler.handlePredictionAnalysis`,
  which keep the minimum. Candidates *without* the push are scored too, so a
  cancelled push still leaves the movement itself clean (no Simulation flag):
  only the two velocity handlers flag.
- **The `o:` in the alert means different things.** AntiKB prints the
  knockback offset (`formatOffset(likelyKB.offset)`). AntiExplosion prints the
  *whole move's* offset (`PredictionComplete.getOffset()`), so a cancelled
  explosion shows `o: 1.38E-14`: the move fit perfectly without it, and the
  explosion candidates did not. "ignored knockback" / "ignored explosion" means
  the offset was never scored (still `Integer.MAX_VALUE`).
- **Thresholds (dev server config).** Both flag above `threshold: 0.001`.
  Knockback (`onPredictionComplete`): every flag alerts; a setback comes when
  that hit's offset is at least `immediate-setback-threshold` (0.1), or when the
  running advantage (offsets summed, capped at `max-ceiling` 4) reaches
  `max-advantage` (1). Clean knockback ticks decay the advantage by ×0.999 while
  it is above 0.05. Explosion uses `flagAndAlertWithSetback` with
  `setbackvl: 3`: a setback from the third violation.
- **Small pushes.** If a push could hide inside the 0.03 movement threshold
  (`PointThreeEstimator.determineCanSkipTick`), its offset becomes the whole
  move's offset, so ignoring a tiny push passes. Same if a velocity packet came
  after an explosion (it replaces the motion anyway).
- **Exempt (`forceExempt`, offset set to 0):** an accepted teleport, riding
  anything (`MovementCheckRunner`, `PacketPlayerSteer`), and ticks Grim does not
  simulate (dead, spectator, abilities-flying, or gliding with Grim's
  elytra exemption).

### `reduceOffset`: the allowance that matters

Three places call it (read 2026-10-27). `PredictionEngine.doPredictions`
passes the **squared** offset for knockback, explosions and NoSlow, so the
error forgiven is the square root of the amount below. `MovementCheckRunner`
passes the main Simulation offset as a **plain distance**
(`predictedVelocity.vector.distance(actualMovement)`), so there the amount is
forgiven in blocks: 1.2 for a hard collider (Boat Burst). For a 1.21.4 client:

| Condition (`UncertaintyHandler`) | Squared offset forgiven | Error forgiven |
|---|---|---|
| A hard collider (unridden boat or shulker, or strider/boat cases while riding) within 1.0 of the box at the start of the move, that tick and the 3 after (`lastHardCollidingLerpingEntity.hasOccurredSince(3)`) | 1.2 | **≈1.095 blocks**: a whole normal hit, and most explosions |
| Near a glitchy block | 0.25 | Only for clients **older than 1.9**: never us |
| Stuck speed (web, berries) while point-three or riding | 0.01 | 0.1 blocks |
| Bouncy block (slime, bed) while point-three or riding | 0.03 | ≈0.17 blocks |

Seen on the dev server 2026-10-27, Cancel with no collider: `AntiKB (x5) o: 0.47`
and `AntiExplosion (x3)`.

### Less knockback that Grim never sees (vanilla 1.21.4, server side)

The server works the push out before it is sent, so anything that shrinks it
there is simply the push Grim expects.

- **Airborne hits add no vertical.** `LivingEntity.takeKnockback`: Y becomes
  `onGround ? min(0.4, y/2 + strength) : y`. Off the ground (as the server
  last heard it), the vertical is left as it was, so a hit while descending
  keeps you descending. Horizontal is `x/2 - push` either way. `strength` is
  already × `(1 - knockback_resistance)`; netherite gives 0.1 a piece.
  Grim's GroundSpoof/Simulation make faking `onGround` a flag, so the real way
  is a real jump: being in the air when the hit lands.
- **Explosions × `(1 - explosion_knockback_resistance)`** (`ExplosionImpl`,
  attribute range 0–1). Blast Protection adds 0.15 a level **per piece**
  (`AttributeEnchantmentEffect.getModifierId` suffixes the slot, so pieces
  stack): one Blast Protection IV piece takes 60% off, vertical included, and
  two take all of it. Cancel,
Scaled, Buffer, Shoreline's Normal / Grim V2 / Walls / Jump / Conceal all drop
or shrink the push, and all are flagged. Anything shipped as passing Grim still
needs the bytecode evidence.

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

These are the only velocity or position edits known to pass Grim. Each stays
inside an allowance Grim grants itself. Other velocity edits are allowed (rule
6) but are expected to flag. A new one claimed to pass needs the same bytecode
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
| **TimerLimit** | Same balance, but `limitFallBehind` treats the clock as no older than `now − ping-abuse-limit-threshold` (1000 ms by default, -1 off). Once the oldest unanswered transaction is over that, the balance jumps to `now − 1120 ms` and every tick packet after it in the batch adds 50 ms, so a hold longer than about **1000 + 120 − ping ms** flags each extra tick, **with a setback** | `client.module.movement.BlinkModule.holdLimit()`: with Grim Safe on, each hold lasts at most `(Ping Limit + 120 − ping − 150) / 50` ticks (ping from the tab list). Blink always runs as bursts (Mode: Continuous until turned off, Fixed until Max Timeout): each hold is sent when it runs out and another starts a tick later; each new hold starts from a fresh clock, as the batch's last pongs answer recent transactions. Servers change the threshold (PvP servers often lower it), so **Adaptive** (default on, `client.module.movement.blink.BurstBudget`) learns the real limit: bursts start at 400 ms and grow 100 ms per burst that lands with no setback within a round trip; a setback cuts the length to 60% and caps it there, per server address, for the session |
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
6. **Velocity may be touched** (since 2026-10-27). Knockback and explosion
   pushes go through `KnockbackEvent` (cancel, `scale()` or `setVelocity()`),
   never by writing the player's velocity directly. Know which edits pass and
   which flag: only the allowances under **Strafe extras** and Jump Reset are
   known to pass, and cancelling or shrinking knockback is set back (see
   **Knockback and explosions**). Say so in the mode's description.
7. **No fabricated packets**: no extra flying packets, no fake slot or use
   packets, no cancelling server packets to hide state. Dropping a knockback
   push through `KnockbackEvent` is the one allowed exception (rule 6).
8. **Break only through `adapter.game.player.Digging`**, looking at the block
   with the rotation the server already has, tool in hand before the start.
   Never send digging packets by hand.
9. **Inventory clicks only through `Services.inventory().click()`**: standing
   still by what was *reported* (no movement keys or jump, no sprint), one a tick,
   whole stacks only. Never repeat a slot packet.

## Tried and rejected

| Idea | Why it fails |
|---|---|
| Velocity Cancel / Scaled / Buffer, as a way past Grim (Cancel ships anyway; rule 6) | KnockbackHandler / ExplosionHandler setback re-applies the knockback |
| Shoreline V2 Velocity (all modes but fishhook) | Same; NoPush liquids/blocks are simulated |
| Shoreline V2 Scaffold (burst + gap fill) | MultiPlace; one rotation per tick can't serve several clicks |
| Leapfrog Scaffold | `player.jump()` (Simulation), click before rotate (RotationPlace), centre-of-block hit, equal turn deltas (DuplicateRotPlace) |
| Rise Scaffold extras | Direct `jump()`, cancelled slot packets, random use packets, timer, fake sneak slowdown |
| MeteorPlus Grim Cancel (re-read on branch 26.3, 2026-10-27; unchanged) | Drops the velocity (KnockbackHandler), then sends an extra position packet (TickTimer: a flying packet with no `CLIENT_TICK_END` since the last one is flagged with setback) and a finish-digging packet on the air at your feet (AirLiquidBreak), from another thread at any point in the tick (Post). The digging packet was meant to earn lenience. In 2.3.73 the only knockback exemptions are the `forceExempt` sites (teleport, riding, unsimulated ticks), and the glitchy-block `reduceOffset` is pre-1.9 clients only |
| MeteorPlus Grim Cancel v2 | Same packets on every movement packet. Its `canCancel` flag can never become true, so as written it does nothing at all |
| MeteorPlus Grim Skip (26.3, unchanged) | Drops the velocity and the next 6 movement packets: Grim then sees 7 ticks of movement in one packet without the knockback (Simulation, KnockbackHandler). Explosions are also only dropped after a damage packet arms it |
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
