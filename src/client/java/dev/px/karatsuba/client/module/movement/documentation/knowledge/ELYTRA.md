# Elytra and fireworks

What holds for anything that opens, flies or boosts an elytra: Elytra Fly, Armor
Manager's elytra swap and Take Off, and anything after. Read from Grim 2.3.73
(decompiled with Vineflower) and vanilla 1.21.4 on 2026-10-10, unless dated
otherwise. `Grim.md` has no elytra section yet; these are the facts.

## Glide physics (vanilla 1.21.4)

| Fact | So |
|---|---|
| Per tick, a glide keeps **99%** of its horizontal speed, plain air 91%, a tick that starts on the ground about 55% | Every ground or plain-air tick in a bounce is expensive |
| `travelMidAir` reads ground friction **before** the move, so a jump from the ground pays ×0.546 too | A hop out of a finished glide is capped near walking pace |
| A sprint jump adds 0.2 along the yaw, but vanilla clears sprint on every gliding tick before `jump()` | Only a hop sprinted at the jump itself, inside the glide, gains it (Bounce's Sprint Jump) |
| Near level, the glide turns a tenth of the fall speed into forward speed each tick (`calcGlidingVelocity`, `vy × -0.1 × cos²(pitch)`); at 85° under a hundredth | Smart Pitch: straight down while rising, 4° once falling |
| The client never clears the gliding flag itself; the server does, on landing (`doTick`, if on ground). A take-off sent while already gliding makes `tryToStartFallFlying` fail and **stops** the glide | Never re-send a take-off mid-glide |
| The client sends the take-off on a jump press edge, inside `tickMovement`, and stops sprinting while gliding, both after `Input.tick()` | A jump key press is the only take-off needed |

## Taking off (Grim)

| Check | Flags |
|---|---|
| **`PacketEntityAction`** | Cancels START_FALL_FLYING and force-resyncs unless Grim's onGround and lastOnGround (the last two movement packets) are both false |
| **ElytraB** | The last input packet must have jump released, and the next held: a fresh press |
| **ElytraC** | Take-offs on consecutive ticks |
| **ElytraA–I** (others) | Already gliding, no elytra, flying, on the ground, levitating, in a vehicle, in water |
| **Post** | An entity action after the tick packet |

**Rule:** take off with a real jump press, after two airborne movement packets and
a released jump in the last input packet. Bounce's `jump()` does exactly this.

## Sprinting while gliding (Grim)

- **SprintF** flags a 1.21.4 client sprinting across two gliding ticks, but it is
  **experimental**: with `experimental-checks: false` (the default), `Check.flag`
  skips it, so it neither alerts nor sets back.
- `exploit.allow-sprint-jumping-when-using-elytra` defaults to **true**, and only
  sets back sprint jumps made while gliding when false.
- So the sprinted hop inside a glide passes on a default config. A server with
  either setting changed will set it back: turn Sprint Jump off there.
- **GroundSpoof** flags any on-ground mismatch, so spoofing on-ground is out.

## Fireworks (Grim)

- **Not simulated.** While Grim counts a rocket on the player
  (`CompensatedFireworks`: added on the spawn's transaction, removed a tick late),
  it accepts any glide velocity inside a box built from the current and last
  packet looks, ×1.7 along each (`UncertaintyHandler.tickFireworksBox`).
- **The box always contains zero:** standing still mid-air is accepted while a
  rocket counts, and only then.
- **The rocket pushes along the shooter's look in its own tick,** outside the
  player's, where a silent rotation is no longer in place. The adapter's
  `FireworkRocketEntityMixin` makes a rocket boosting the local player read the
  last **sent** look (`Rotations.sentSilent()`), so the push matches the box.
  Any silent rotation during a boosted glide is consistent through it.
- Fireworks go out as item uses through `Services.actions()`, never after the
  tick's end (Post).

## Adapter seams

| Seam | What |
|---|---|
| `Players.canGlide()` | The game would open an elytra now, the ground aside: something worn that glides and isn't about to break; not riding, levitating, in water, climbing or flying. Vanilla's take-off conditions, and Grim's (ElytraD, E, G, H, I) |
| `Players.gliding()` | `isGliding()`: the flag the server's metadata sets |
| `Players.fireworkBoosting()` | A live rocket in the world whose shooter is the local player. Walks every entity |
| `Inputs.reportedJump()` | Jump in the last input packet sent: what ElytraB reads |
| `MovementPacketEvent.isOnGround()` | What the movement packets said: what `PacketEntityAction` reads |
| `PacketHold.hold()` / `release()` | The shared everything-hold (5 ticks max) Bounce's Hold Glide uses; see [PACKET_HOLD.md](PACKET_HOLD.md) |
| `Options.fovEffectScale()` | The FOV Effects option, which sprint toggling pulses |
