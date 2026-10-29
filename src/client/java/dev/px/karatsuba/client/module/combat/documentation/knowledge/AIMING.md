# Aiming and reach

The largest single cause of lost actions in Crystal Aura. A look that is
"almost right" waits on `TURNING` forever.

## Reach is measured to the nearest point; a look lands where the ray enters

- **A search accepts a target when the nearest point of its box is in reach**
  (`ReachPoint.NEAREST`).
- **A look at the box's centre enters the box further out than that.** At the
  edge of reach (Break Range = entity reach = 3.0), it lands just past the reach.
  The look is "right", yet the attack never goes.

**Rule: aim at the point of the box nearest the eyes, a little inside it.**
- Give `Action.attack` no aim (`aimAt`), and `ActionService` does this itself
  (`AIM_INSET` 0.1).
- Never pass `option.getAim()` or `box.getCenter()` for an attack.

## Eyes inside the box

A crystal's box is 2 × 2, so one placed beside you reaches around your head.
- **The nearest point is then the eye itself.** A look toward it has no
  direction: pitch ±90 or 0, on a yaw snapped to an exact multiple of 90°.
- **The client check passes** (the eye is inside the box).
- **The server often doesn't take the hit,** and no player sends that rotation.

**Rule: when the nearest point is under 0.5 blocks from the eyes, aim at the box's centre.**
- `ActionService.aimOf` does this for every default attack aim (`MIN_AIM_DISTANCE`).
- **Any look a module computes itself needs the same rule.** Crystal Aura's held
  look (`watchPending`) needed it too.

## One look for two actions

To break and place in one tick, the attack can aim at the place's click point,
if that look also passes through the target.
- **Check where the look enters the box,** not just whether it crosses it:
  `box.clip(eye, point) × eye.distanceTo(point)`.
- **It must be within `min(range setting, Players.entityReach()) − margin`.**
  The margin (0.25 in Crystal Aura and Surround, `AIM_MARGIN`) covers the
  rotation being rounded and the move.
- **Otherwise don't share.** The attack aims at its nearest point and the click
  waits a tick.

## Where the server will see the look from

Measure every look from `Players.eyePosition() + Players.velocity()`, where the
eyes will be after this tick's move, which is where the server holds the rotation.

## Grim

The placement and aim checks (RotationPlace, DuplicateRotPlace, AimModulo360,
AimDuplicateLook, and others) are in the rulebook, `client/anticheat/Grim.md`.
