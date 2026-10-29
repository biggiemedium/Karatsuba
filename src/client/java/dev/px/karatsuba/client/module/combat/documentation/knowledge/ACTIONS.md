# Actions: when they go out, and what the outcome means

Every attack, click and item use goes through `Services.actions()` (`ActionService`).
This is how it times them, and how to read what it reports back.

## When an action goes out

`ActionService` sends at the **start of the next tick**. That is the earliest
point Grim's Post check allows. For an action decided in tick N:

1. **Tick N:** the rotation is claimed and goes out in tick N's movement packet.
2. **Start of tick N+1:** the action is checked against the look the server **has**.
   - If it passes, it is sent.
   - If not, it is `TURNING` and dropped, and the module must ask again.

What follows from that:

- **One look per tick.** When a tick's attack and click point different ways,
  one of them waits. Design so that one look serves both (see
  [AIMING.md](AIMING.md)), or accept a tick.
- **Actions submitted between ticks get no turn.** That covers anything sent
  from a packet event, such as `CrystalSpawnEvent`. It only goes out if the
  server's look already hits. So when a module knows what it will act on next,
  it should **hold the look there while it waits** (Crystal Aura's `watchPending`).
- **There is a tick between deciding and sending.** The kit's `AttackLog` stamps a
  hit with the tick it was *sent*, one after it was decided. Anything timed to
  a window must judge it one tick early, or each cycle loses a tick.
- **Don't spread your own targets around.** Several things out at once, each
  needing its own look, thrash the rotation. Crystal Aura with 3 crystals out
  was slower than with 1.
- **Order within a tick:** offhand, then attack, then click. The outcome of each
  is reported before the next, so a click's callback can rely on the attack's
  having run.

## What each outcome means

| Outcome | Meaning | How to treat it |
|---|---|---|
| `SENT` | The packet went out | Record what was done (`placed()`, `attacked()`), and only now |
| `REFUSED` | The client's own check failed (`ActionResult` not accepted) | **The packet still went out.** See below |
| `TURNING` | The server's look doesn't hit the target yet | Expected for an action submitted between ticks. From the tick path, it is a bug: an aim that can't land ([AIMING.md](AIMING.md)) |
| `BUSY` | Eating or drinking, a container open, or packets being held | Deliberate. The user decided 2026-10-29 that nothing goes out while an item is in use. Not lag |
| `WAITING` | A delay, a slot claim, or an `afterAttack` click whose attack didn't go | It asks again next tick |
| `OUTRANKED` | Another module's claim won the button | `getAttackHolderName()` / `getClickHolderName()` say who |

### `REFUSED` doesn't mean "not sent"

- **The packet goes out regardless.** `Interactions.click` reports the client's
  prediction, but the click packet is sent whatever the client predicts; only a
  world-border miss stops it.
- **The client predicts wrong whenever it hasn't yet seen the server act on one
  of our earlier packets.** For example, a crystal we just broke is still shown,
  so a place on its base "fails" on the client and succeeds on the server.
- **Treating that as unsent repeats the click every tick** until the removal
  arrives: wasted packets, and a wrong count of what is out.
- **So decide per module when a `REFUSED` really went.** Crystal Aura: nothing
  but crystals we hit stands in the room. Surround: the same, per cell. The
  rule is in [PLACEMENT.md](PLACEMENT.md).

## Dependent actions

A click that only works once our own attack has landed, such as placing on the
base of a crystal being broken, is marked `Action.afterAttack()`.
- It goes out only in a tick whose attack is the same owner's and was sent.
- Otherwise it is `WAITING`.

With the Interaction settings' Same Tick on (the default), the two go out
together.

## Waits follow ping

How long something placed is waited for is one round trip plus slack,
`ceil(ping / 50) + 3` ticks, not a fixed number. A fixed half second is outlasted
by a high ping.
