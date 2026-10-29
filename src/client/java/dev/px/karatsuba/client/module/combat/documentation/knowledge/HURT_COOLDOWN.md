# The hurt cooldown

Why more hits a second is not more damage, and how the explosive searches time
to it. Built 2026-10-29 for Crystal Aura (Hurt Sync).

## The game rule *(bytecode, 1.21.4)*

- **A full hit sets `timeUntilRegen = 20`** (`LivingEntity.damage`).
- **While it is above 10, a hit deals only `amount − lastDamageTaken`.** It raises
  `lastDamageTaken`, or deals nothing if it isn't bigger. It does **not** restart
  the window.
- **A player counts it down once a server tick** (`ServerPlayerEntity.tick()`;
  other living entities in `baseTick`). So a hit lands in full again 10 ticks
  after the last full hit.
- **Nothing is exempt in vanilla.** `#bypasses_cooldown` has no data file, so
  explosions and melee both obey it.
- **The client sets the same counter in `onDamaged`,** but only when the server
  sends damage events. Some servers don't (crystalpvp.cc). Health may be hidden
  too.

So against one target, about **2 full-strength hits a second** is the most that
counts. This applies to Crystal Aura, Bed Aura, anchors and Kill Aura alike.

## How the library models it

- **`AttackLog` keeps each target's hits for 40 ticks** (`HISTORY`) and replays
  them the way the game takes them. `dealtTo(target, window)` gives what a new
  hit must beat; `cooldownLeft(target, window)` gives the ticks until a full hit.
  A window of 1 is the old rule of one explosion a tick.
- **`ExplosiveSearch` judges a counted option by what lands:** damage minus
  `dealtTo` against min damage, face place, armour break and lethal. It ranks by
  that too. `Option.getDamage()` stays the raw damage.
- **Placing isn't judged by it:** a placed explosive can wait. Devices that fire
  at once (beds, anchors) are judged by it.
- **Self damage keeps the one-tick rule.**
- **Turning it on:** `hurtWindow(IntSupplier)` on the search builder, from
  `kit.hurtCooldown()` (10). Default 1. Smoke checks: `SearchTests.hurtWindowLog()`
  and `hurtWindow()`.

## Lessons from building it

- **Time the ammunition; don't hold it.** A crystal placed with a break waited
  out the cooldown while the target moved and went stale. Place the next one so
  it arrives as the window closes (Crystal Aura `waitToPlace`). Stop counting one
  that has gone stale (`forgetStale`).
- **Judge the window one tick early.** The log stamps the send tick, and a
  decision is sent a tick later ([ACTIONS.md](ACTIONS.md)). Judged on the full
  window, hits came 12 ticks apart instead of 11.
- **A margin of one tick** (Sync Margin) keeps a hit that lands a tick early to
  jitter from being blocked. It costs about 5% of the rate.
- **Breaks a second is the wrong measure once timed.** It drops by design.
  Judge by pops and kills against a target whose health you can see.
- **Our Fake Player can't test damage.** It exists only on the client, so it
  takes no server cooldown and shows no real damage.

## Open

- **A hit the server refuses still opens a window** (it is logged at `SENT`), so
  the next break waits for nothing.
- **Hits that aren't ours** (other players, melee) aren't seen. Where the server
  sends damage events, the client's `timeUntilRegen` could start a window, but
  the amount is unknown.
- **Kill Aura** has no model of it yet.
- **Bed Aura** should set `hurtWindow` too.
