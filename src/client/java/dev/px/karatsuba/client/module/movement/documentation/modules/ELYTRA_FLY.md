# Elytra Fly

`client/module/movement/ElytraFlyModule.java`, with its modes in
`movement/elytrafly/`. Flies your elytra for you. A `ModalModule` with two modes;
the module file has no logic. Movement rather than Travel: neither mode has a
destination.

**Status:** not yet hardened. Bounce was tested on the Paper + Grim dev server on
2026-10-10: about 30 b/s, no alerts, accepted by the user. Firework is untested
in game.

The glide physics and Grim's elytra and firework rules both modes rely on are in
[ELYTRA.md](../knowledge/ELYTRA.md).

## Bounce (`BounceElytraFly`)

Skims the ground: land, hop, glide, over and over. Meteor's Bounce (Luna's Elytra
Recast), with Lambda's Smart Pitch and its idea for keeping the glide through a
landing. Never touches velocity.

### Each tick (`TickEvent` PRE)

1. **Stands down** when `Players.canGlide()` is false (no usable elytra, water,
   climbing, riding, levitating, flying) or during a rubberband pause. Gives the FOV option back.
2. **Auto Take Off off:** waits until the player has opened the elytra once.
3. **Aims** (`aim()`): the course from Heading, the pitch from Pitch Mode, at
   `RotationPriority.HIGH` so an aura can't steer the glide into the ground. Silent
   with Free Look and Move Fix, otherwise the camera turns. Smart snaps; the rest
   turn 30° a tick.
4. **Claims the keys** through `Core.controls().move()` at `NORMAL`: W along the
   course, sprint when `Players.canSprint()` (hunger, Grim SprintA), and jump from
   `jump()`:
   - **on the ground:** held, so it hops the first tick it touches down;
   - **gliding:** released, so the cooldown resets and a later press is new;
   - **falling, not gliding** (the server ended the glide): pressed once jump was
     released in the last input packet and the last two movement packets were
     airborne. The game opens the elytra on that press (Grim ElytraB/F,
     `PacketEntityAction`).

### Where the speed comes from

A sprint jump **inside** the glide: +0.2 a bounce against 1% a tick lost, with no
ground tick or plain-air tick in between. Leaving the glide on every landing (v1)
pays the ground's ×0.546 and the air's ×0.91, which capped it near walking pace.

| Piece | How |
|---|---|
| **Sprint Jump** | `PlayerJumpEvent`: while gliding, `Players.setSprinting(true)` at the jump itself, after vanilla cleared sprint for the glide. Vanilla ends it the next tick |
| **Hold Glide** | `MovementPacketEvent` (`LOWEST`, always listening): a landing packet while gliding starts the shared `PacketHold.hold()`; the next airborne packet marks it for release at `TickEvent` POST. The server should read landing and hop together and never end the glide. Let go early if the glide ends, the mode stops, or a teleport arrives |
| **Smart Pitch** | 90° while `vy > -0.2`, else 4°, snapped. Near level, the glide turns a tenth of the fall speed into forward speed each tick |
| **Recast** | When the glide ends anyway: the two-airborne-packets press above |

### Settings

| Setting | Default | What |
|---|---|---|
| Heading | Nearest 45 | Facing, the nearest 45° line, or Fixed |
| Yaw | 0 | Compass yaw for Fixed |
| Pitch Mode | Smart | Smart, Fixed or Free |
| Pitch | 72 | For Fixed |
| Free Look | on | The bounce angle is silent; needs Move Fix |
| Sprint Jump | on | Sprinted hop inside the glide |
| Hold Glide | on | Landing and hop sent together |
| Auto Take Off | on | Off waits for you to open the elytra |
| Rubberband Pause | 7 ticks | Steps back after a setback (`ServerTeleportEvent`, not a spawn) |
| Steady FOV | on | Borrows FOV Effects at 0 while bouncing; given back on stop, world change, shutdown |

### The ArrayList

`Bounce X b/s`: horizontal speed smoothed over about a second (`measure()`).

## Firework (`FireworkElytraFly`)

Flies on fireworks and steers with the keys like creative flight. Lambda's
firework elytra fly. Does nothing without Move Fix.

### Each tick (`TickEvent` PRE), while gliding

1. **The keys pick a look** relative to the camera (`heading()`): W/A/S/D along
   the ground; jump climbs (Climb Angle, straight up with no direction); sneak
   dives (Dive Angle, straight down with none). Sneak wins over jump.
2. **Silent look** at that rotation, `HIGH`, snapped. The camera stays yours.
3. **A rocket kept burning:** with none on you (`Players.fireworkBoosting()`), a
   firework use through `Services.actions()` (`HIGH`, no rotate). After a `SENT`
   use it waits up to 20 ticks for the rocket before trying again. With none in
   the hotbar, Inventory shift-clicks one up through `Services.inventory()`.
4. **No key held:** When Still decides.

| When Still | What |
|---|---|
| Glide (default) | Nothing; the glide carries on |
| Hover | Faces 0° then 180° on alternate ticks so the pushes cancel, freezes the body while a rocket is on you, and launches rockets to keep one |
| Hover On Boost | The same, but launches none: hovers only while a rocket already burns |

**Frozen** means `PlayerMoveEvent` PRE is cancelled, as Free Cam's body is. Only
while the client sees its rocket, which is when Grim counts it.

### Settings

| Setting | Default | What |
|---|---|---|
| Inventory | on | Bring fireworks to the hotbar; needs a free slot |
| Climb Angle | 45 | Degrees up with jump and a direction |
| Dive Angle | 20 | Degrees down with sneak and a direction |
| When Still | Glide | See above |

## Left out on purpose

| From | What | Why |
|---|---|---|
| Meteor | Raw take-off packets; take-off the tick you land with jump held | Grim cancels it (two airborne packets) and ElytraB (a fresh press) |
| Meteor | Forced sprint while gliding | SprintF |
| Meteor | Stop gliding on the client after a rubberband, then re-send the take-off | Client and server simulate two different players. Rubberband Pause steps back instead |
| Lambda | Hover by withholding movement packets ("None", "Packet Gap") | With no rocket Grim simulates the glide: a player who did not fall |
| Lambda | Velocity set to the rocket's top speed on resume | A velocity edit; the rocket gets there in about three ticks |
| Lambda | Every packet rewritten as a full one with a 0.0001° yaw wobble | The game sends looks when they change; the wobble only dodges AimDuplicateLook on forced packets |
| Lambda | Fake Lag (withholding pings so Grim keeps the glide) | A lag exploit, and unverified against ElytraB |

## Switching off

`ElytraFlyModule.onDisable()` calls `bounce.stop()`: lets go of a packet hold and
gives back FOV Effects. Rotation and key claims lapse on their own. Firework has
no `stop()`; its freeze only acts while the mode listens.

## What testing found

| Date | Finding |
|---|---|
| 2026-10-10 | v1 slower than walking: it hopped out of the glide. Fixed by the in-glide sprinted hop |
| 2026-10-10 | v2 measured 3 b/s, "hops then redeploys, no slide". The model had read ground friction after the move; it is read before. Led to Sprint Jump and Hold Glide |
| 2026-10-10 | Shipped at about 30 b/s on the dev server, no alerts. A trace showed **Hold Glide kept the glide on 1 bounce in 42**: the server still ended it 1–2 ticks after each hop, cause unknown. Paper matches vanilla there. The speed comes from the sprinted hop and a fast redeploy; a working hold is modelled at 40–48 b/s |

## Known to check

Found reading the code on 2026-10-30; none confirmed in game yet.

| What | Why it matters |
|---|---|
| **Hold Glide is on by default but almost never works** (1 in 42) | Every bounce still holds packets for a tick or two: latency for nothing. Find why the server ends the glide, or default it off |
| **Firework doesn't say "Move Fix off"** | It silently does nothing without Move Fix. Strafe and Scaffold show it (`Grim.md` rule 3); the ArrayList here only shows the mode name |
| **Firework's launch wait is 20 ticks, not tied to ping** | A lost rocket costs a second at any ping; a high ping could re-launch before the first rocket appears |
| **`Players.fireworkBoosting()` walks every entity in the world** | Asked every gliding tick in Firework mode. Cheap alone, but a busy world pays it each tick; a record of our own rocket's id would make it one lookup |
| **Firework's Hover flips the look 180° every tick** | Inside Grim's box, but the most unusual look pattern in the client. Check against the aim checks if Grim adds any |
| **Bounce asks for the head at `HIGH`** | On purpose, but it beats Strafe and most auras while bouncing. Combat modules at `HIGHEST` still win |
| **Steady FOV writes a game option** | Given back on stop, world change and shutdown. A crash in between leaves FOV Effects at 0 |
| **Strafe's silent yaw while gliding** | Strafe's Grim mode still turns the head while gliding ([STRAFE.md](STRAFE.md)); with Elytra Fly on, Elytra Fly's `HIGH` wins, but Strafe alone would steer a glide |

## TODO

- [ ] Test Firework on the dev server: steering, climb, dive, both hovers, refill.
- [ ] Find why the server ends the glide despite Hold Glide (a packet trace again),
  or turn it off by default.
- [ ] Work through "Known to check" against the README's checklist.
