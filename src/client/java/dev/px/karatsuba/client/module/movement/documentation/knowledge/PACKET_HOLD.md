# Holding packets

What holds for anything that holds outgoing packets back: Blink, Elytra Fly's Hold
Glide, and anything after. The Grim rows and the bytecode are in `Grim.md`,
"Holding packets"; this is how to use the hold.

## What a hold is (`adapter/network/PacketHold`)

- **Nothing dropped or rewritten.** Every send handler saw the packet when the game
  sent it; letting go only puts it on the wire, in order, in one flush.
- **The server reads every packet that has arrived, then ticks its players.**
  Packets let go together are read together, and the server acts only on how
  things stand after the last of them. That is what Hold Glide uses.
- **A hold always ends:** at its limit (`lasting(ticks)`; it lets go at the end of
  the tick after the limit), or on leaving a world.
- **Any number at once.** A packet is held while any active hold takes it;
  releasing one sends only what no other hold still takes.
- **Thread-safe:** sends can come from Netty's thread (a ping answered inline),
  so the queue and every hold's state are guarded.

| API | For |
|---|---|
| `PacketHold.hold()` / `release()` | The shared everything-hold, for anything short-lived (max 5 ticks). Bounce's Hold Glide |
| `PacketHold.create(name)` → `Hold` | A module's own hold, so another module letting go never lets go of its packets. Blink |
| `Hold.everything()` / `only(kinds)` | Everything (the default), or only some `PacketKind`s |
| `Hold.lasting(ticks)`, `start()`, `release()`, `isActive()`, `getTicks()`, `size()` | Driving it |
| `PacketHold.isSplitting()` | Holds are on and none takes everything: packets are going out of order |
| `HoldPackets.with(hold)` | The automation step: the hold on while the step runs |

## Rules

- **Hold everything on Grim.** Grim reads its transaction pongs against your
  movement. Held together, in order, it is latency, which Grim absorbs. A hold of
  some kinds sends the rest ahead of them: TickTimer flags flying packets without
  their tick end, and Simulation and Post can follow.
- **Stay inside TimerLimit:** about `ping-abuse-limit-threshold` (1000 ms default)
  + 120 − ping ms. Longer flags every extra tick, with a setback. Servers lower the
  threshold; don't trust 1000.
- **Burst, don't stretch.** A long blink is a run of holds, each let go before the
  limit, with a tick between. Each new hold starts from a fresh clock.
- **Don't move far in one burst.** The vanilla server sets back a batch that moves
  the player about 10 blocks in one of its ticks (unverified). Blink lets go at 8.
- **Services wait while a hold splits.** `ActionService` and `DigService` hold off
  while `isSplitting()`. `InventoryService` does not yet.
- **Let go on every way out:** switching off, a teleport, the glide ending, leaving
  a world. Holding with nothing to gain is only lag.
