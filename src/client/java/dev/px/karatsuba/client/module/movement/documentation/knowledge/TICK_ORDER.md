# Tick order

Where each event a movement module uses fires in the client tick, and what is
already this tick's there. Most timing bugs in a movement module are a decision
made a step too early, on state the game has not updated yet.

## One client tick, in order

| Step | Fires / runs | Hook |
|---|---|---|
| 1 | **`TickEvent` PRE.** `ActionService` sends clicks and attacks (`HIGHEST`); modules press `TickKey`s here | `GameEventBridge`, Fabric `START_CLIENT_TICK` |
| 2 | The game reads the keyboard and mouse (`handleInputEvents`): item use starts or stops, the hotbar changes | vanilla |
| 3 | **`PlayerUpdateEvent` PRE**, the player's `tick` | `ClientPlayerEntityMixin.karatsuba$preTick` |
| 4 | The rotation resolves: a silent one is swapped in for the move (`Rotations.beforeMovementTick`) | `karatsuba$preMove`, `tickMovement` HEAD |
| 5 | **`PlayerMoveEvent` PRE.** `MotionService` applies the winning claims last (`LOWEST`) | same |
| 6 | `Input.tick()`: the keys become this tick's input. Then, in order: Move Fix (`Rotations.correctInput`), movement claims (`MovementKeys.apply`), `Inputs.applyHoldStill`, `Inputs.applyJumpLetGo` | `karatsuba$afterInput` |
| 7 | Vanilla scales the input for item use and sneaking, decides sprint, and calls `travel` | vanilla |
| 8 | `Entity.move`: a pending `Displacement` is added (own moves only) | `EntityMixin`, HEAD |
| 9 | `travel` returns: `EntityPush.afterTravel` records the velocity; entity cramming pushes after | `LivingEntityMixin` |
| 10 | **`PlayerMoveEvent` POST** | `karatsuba$postMove`, `tickMovement` TAIL |
| 11 | Movement packets go out with the silent rotation; the camera's is put back | `sendMovementPackets` HEAD/RETURN |
| 12 | **`PlayerUpdateEvent` POST** | `karatsuba$postTick` |
| 13 | **`TickEvent` POST.** Every `TickKey` lets go | Fabric `END_CLIENT_TICK` |

## What is still last tick's

- **At `TickEvent` PRE** (step 1): the keyboard is not read yet, so item use,
  sneak and the hotbar are as last tick left them. `Players.usingItem()` there
  does not see an eat that starts this tick. Key presses made here **are** read
  this tick, which is why they belong here.
- **At `PlayerMoveEvent` PRE** (step 5): `Input.tick()` has not run, so the game's
  input is last tick's. Read the keys with `Inputs.pressed()` instead.
  The velocity is last tick's after friction: what this tick's move starts from.
- **Anything after step 6** sees the keys Move Fix and the claims wrote, not the
  player's own.

## Rules

- **Press keys at `TickEvent` PRE.** Pressed later, they miss this tick's read.
- **Change velocity at `PlayerMoveEvent` PRE,** through `Services.motion()`, which
  applies after every module has filed.
- **A decision that depends on item use or sneaking** made at `TickEvent` PRE is a
  tick late. Either accept that and say so, or decide where the state is current.
