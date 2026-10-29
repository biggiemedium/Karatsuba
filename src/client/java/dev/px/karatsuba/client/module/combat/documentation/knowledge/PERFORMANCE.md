# Performance

What combat modules cost per tick, measured, and how to keep it low.

## Measured costs (Crystal Aura, crystalpvp.cc, 2026-10-29)

Place range 4.5 scans 1331 cells and about 60 bases.

| Work | Average per call |
|---|---|
| Place search | 0.25–0.9 ms; 1.2–1.6 ms with Predict on |
| Break search | 0.06–0.3 ms; 1.1–1.6 ms with Predict on and a crystal held for the cooldown |
| Judging one spawned crystal | about 0.15 ms; about 1 ms with Predict on |
| First search of a session | about 19 ms, while the JIT warms up. Ignore it |

**The calculation was never the delay.** Every slowdown found came from actions
waiting on looks that couldn't land, or from sends not being recorded. That's
why the rule is to measure first ([DEBUG_LOGGING.md](DEBUG_LOGGING.md)).

## Where the cost goes

- **Raycasts.** One damage estimate casts about 45 rays (a player's hitbox
  sampled 3 × 5 × 3), each walking about 12–20 cells. The searches' bound and
  pruning keep the exact estimates to a few spots.
- **Prediction (Predict).** Looking ahead runs Core's movement prediction per
  target, per search.
- **Re-reading armour.** It is re-read on every damage estimate, which is small
  next to the rays. Not worth caching yet.

## Rules

- **Skip the search when you can't act.** If nothing can be sent this tick
  (capped, on delay, no item, waiting for the cooldown), don't search. Crystal
  Aura skips the place search then, which is most of a tick's work.
- **Measure before caching.** Don't add a cache until a log shows the time goes
  there.
