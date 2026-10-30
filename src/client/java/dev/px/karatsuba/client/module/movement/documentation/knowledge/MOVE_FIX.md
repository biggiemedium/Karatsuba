# Silent rotations and Move Fix

Grim moves the player along the yaw in the movement packet, not the camera's. A
movement module that turns the head silently (Strafe's Grim mode, Elytra Fly's
Firework, Scaffold) depends on Move Fix to keep the keys, the move and the sent
yaw in agreement.

## What Move Fix does

- **`Rotations.beforeMovementTick()`** resolves the rotation at the head of
  `tickMovement` and swaps a silent yaw in, so the move and the packets both use
  it. The camera's is put back after the packets.
- **`Rotations.correctInput()`** rewrites the keys just read against that yaw,
  only when it is more than 22.5° from the camera's (`MovementCorrection.isNeeded`).
- **STRICT:** the result is -1, 0 or 1 on each axis, the eight directions Grim
  tries. The cost is up to 22.5° of drift from where the player steered.
- **The switch** is Client Settings > Interaction > Move Fix (on by default),
  wired by `InteractionSettings` to `Rotations.movementCorrection()`.

## Rules

- **No silent rotation while walking without Move Fix.** Every tick is a
  Simulation flag. Check `ClientSettings.interaction().moveFix()` and do nothing
  without it; show "Move Fix off" in the ArrayList (Strafe, Scaffold).
- **Pick yaws STRICT maps exactly.** A yaw on the keys' heading, or 45° steps from
  it, maps with no drift. Strafe asks for the exact heading; Scaffold tries the
  camera + 180° and then 45° steps.
- **Ask at the lowest priority that works.** A movement module's look is a
  preference; an aura's or Scaffold's is a requirement. At `LOW`, the head goes to
  them and Move Fix maps the keys against their yaw instead.
- **Keep the camera's pitch** unless the pitch is the point: a silent pitch is a
  look other modules' clicks are checked against.
- **A silent yaw in a glide steers the elytra.** Gliding goes along the look, so a
  movement module that is not an elytra module stands down while gliding
  ([SITUATIONS.md](SITUATIONS.md)).

## Grim

AimModulo360 and AimDuplicateLook are handled in `Rotations` for every caller
(`Grim.md`, "Aim").
