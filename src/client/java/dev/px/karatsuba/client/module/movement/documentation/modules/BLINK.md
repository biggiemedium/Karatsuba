# Blink

`client/module/movement/BlinkModule.java`, with its helpers in `movement/blink/`.
Holds your packets back while on, then lets them all go at once: to the server you
stood still, then arrived.

**Status:** not yet hardened. Reworked 2026-10-22 (Grim compliance) and
2026-10-28 (Adaptive). Not tested on the dev server since either.

How holding packets works, and what Grim allows, is in
[PACKET_HOLD.md](../knowledge/PACKET_HOLD.md).

## How it runs

1. **On enable** it starts its own hold (`PacketHold.create("Blink")`) of
   **everything**, before the flow's first tick, so nothing slips out. It records
   where you started and the hole you started in, if any.
2. **A Core flow runs the blink** as a loop of bursts, at `RotationPriority.NORMAL`:
   - `budget`: sets the hold's limit (`holdLimit()`) and notes where the burst began;
   - `HoldPackets.with(hold).until(tooFarForOneBurst)`: holds until the limit runs
     out, or you are 8 blocks from where the server last had you;
   - `delivered`: the hold has let go; the burst is noted for judging, and the
     trail delivered.
   - The loop ends when `letGo()` says so: an Auto Toggle event, or Fixed's Max
     Timeout. Each new hold starts a tick after the last.
3. **At tick end,** once the flow is done, the module switches itself off.
4. **On disable** the flow is cancelled, the hold let go, and a burst still held is
   noted for judging.

The ArrayList shows the time blinked, and with Adaptive on, the current burst
length (`2.3s 600ms`).

## How long a burst holds (`holdLimit()`)

In ticks, the smallest of:

| Limit | When |
|---|---|
| `(Ping Limit + 120 − ping − 150) / 50` | Grim Safe on: TimerLimit's budget at the tab-list ping, 150 ms margin |
| `Max Timeout × 20` | Grim Safe off |
| `BurstBudget.ticks() + 1` | Adaptive on |
| What is left of Max Timeout | Fixed |

Then one less (a hold lets go at the end of the tick after its limit), and never
below 1.

## Adaptive: judging bursts

- **A burst is judged** for a round trip plus 10 ticks after it lands
  (`judgeWindow()`, ping from the tab list, 10 ticks if unknown).
- **A setback** (`ServerTeleportEvent`, not a spawn) in that window:
  `BurstBudget.setBack(held)`, and a notice says the new length.
- **No setback** by the end of the window: `BurstBudget.landed(held)`.
- Judging carries on after Blink switches off (`ignoreListening`), so the last
  burst is judged too. The budget follows the server through `WorldEvent`.

## Auto Toggle (`letGo()`, read each tick by the flow)

| Setting | Default | Ends the blink when |
|---|---|---|
| Hole Reached | on | You stand wholly in a hole (combat kit `holeFinder`) other than the one you started in, on the ground |
| Totem Pop | on | One of your totems pops |
| Explosion | on | A crystal within 12 blocks would deal at least Explosion Damage (default 8, after armour), or leave you within a heart (`CrystalRules.damage`) |
| Setback | on | The server puts you back |
| Distance | off | You are Max Distance (default 4) from where you started |

The user rejected a plain "Damaged" toggle: only critical damage should end a blink.

## Settings

| Setting | Default | What |
|---|---|---|
| Mode | Fixed | Continuous: bursts until turned off. Fixed: bursts until Max Timeout, then off |
| Max Timeout | 5 s (3–10) | Fixed: the whole blink. Continuous without Grim Safe: each burst |
| Grim → Grim Safe | on | Each burst inside TimerLimit's budget |
| Grim → Ping Limit | 1000 ms | The server's `ping-abuse-limit-threshold` |
| Grim → Adaptive | on | Learn the server's real limit |
| Auto Toggle → … | see above | |
| Appearance → Trail, Server Box, Theme Colours, Trail Colour, Server Colour, Thickness, Animations, Through Walls | Trail and Server Box on | What is drawn |

## The helper classes

| Class | What it does |
|---|---|
| **`blink/BurstBudget`** | How long a burst may hold on this server, learned per server address for the session (static map, not saved). Starts at 8 ticks (400 ms); **+2 ticks** after each clean burst near the current length; a setback cuts to **60%** of the burst that was set back (never below 3) and caps growth just under it. A ceiling on top of Grim Safe, never above it |
| **`blink/BlinkTrail`** | What is drawn. Points are the positions in **held movement packets** (closer than a small step are merged), so the line is exactly what the server will be told. `sent()` records where the server has you with nothing held; `held()` adds a held point; `deliver()` hands the line to a fade-out and sends the ghost hitbox gliding to its last point. Time-based, so frame-rate independent. Drawn with `ignoreListening` so the last fade finishes after Blink ends |
| **`automation/step/HoldPackets`** | The flow step: turns a caller's hold on at start and off at stop, whatever stops it (an `until`, a cancel, a pause). Done when the hold is no longer on. Uses no controls, so no reflex pauses it |
| **`adapter/network/PacketHold`** | The hold itself: named holds, kind filters, limits, ordered flush. See [PACKET_HOLD.md](../knowledge/PACKET_HOLD.md) |

## What testing found, and the decisions

| Date | Finding or decision |
|---|---|
| 2026-10-22 | The user wanted "hold only certain packets". `PacketHold` got kind filters, but **Blink always holds everything**: a partial hold breaks TickTimer (flying packets with no tick end between), Simulation and Post. Its Packets setting was removed |
| 2026-10-22 | Grim TimerLimit read from the jar: a hold longer than about 1000 + 120 − ping ms flags each extra tick, with a setback. Grim Safe and Ping Limit added; Blink always runs as bursts. Mode (Continuous, Fixed) replaced Pulse at the user's request |
| 2026-10-28 | **Rubberbanded on Crystal Velocity, not on the Grim dev server.** Most likely a lower `ping-abuse-limit-threshold` there, or ping jitter. Adaptive (`BurstBudget`) added, and the 8-block early burst (inside vanilla's ~10-block "moved too quickly", itself unverified) |

## Known to check

Found reading the code on 2026-10-30; none confirmed in game yet.

| What | Why it matters |
|---|---|
| **Adaptive is untested on the server that rubberbanded** | The fix for the one real finding has not been tried where it was found |
| **Ping is the tab list's** | It updates slowly and lags a spike. Grim Safe's budget and the judge window both use it; a spike can make a burst hold past TimerLimit |
| **`InventoryService` doesn't check `isSplitting()`** | Harmless for Blink (it never splits), but a split hold from another module would let inventory clicks out of order |
| **Explosion runs a crystal damage estimate every tick** | `letGo()` asks for every crystal within 12 blocks, every tick of a blink. Fine with few crystals; a crystal fight pays it ([combat PERFORMANCE.md](../../../combat/documentation/knowledge/PERFORMANCE.md)) |
| **What `BurstBudget` learns is lost on restart** | Each session starts at 400 ms again on a server already known to need less |
| **The 8-block burst and vanilla's ~10 blocks are unverified** | From memory of the vanilla server, not read from this one (`Grim.md`) |

## TODO

- [ ] Test on the dev server: Fixed and Continuous, each Auto Toggle, no flags.
- [ ] Test Adaptive on Crystal Velocity, where it rubberbanded.
- [ ] Work through "Known to check" against the README's checklist.
