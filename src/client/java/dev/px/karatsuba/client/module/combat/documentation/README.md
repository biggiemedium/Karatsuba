# Combat documentation

What we know about the combat modules, and how each one stands. Read the
relevant files before changing a module, and update them when a test settles
something.

## Where things are

| Folder | Holds |
|---|---|
| [`modules/`](modules/) | One file per module: how it works, its settings and decisions, what testing found, and its TODOs |
| [`knowledge/`](knowledge/) | What holds for every combat module, by topic |

**`knowledge/`**

| File | Read it when |
|---|---|
| [ACTIONS.md](knowledge/ACTIONS.md) | sending anything through `Services.actions()`: when actions go out, what each outcome means, dependent actions |
| [AIMING.md](knowledge/AIMING.md) | a module attacks or turns: reach vs aim, eyes inside a box, sharing one look |
| [PLACEMENT.md](knowledge/PLACEMENT.md) | placing crystals, blocks, beds or anchors: the game's rules, what the kit counts as in the way, the same-tick break-and-place |
| [HURT_COOLDOWN.md](knowledge/HURT_COOLDOWN.md) | a module deals damage: the 10-tick cooldown and how the searches time to it |
| [PERFORMANCE.md](knowledge/PERFORMANCE.md) | something feels slow: measured costs, and what not to spend |
| [DEBUG_LOGGING.md](knowledge/DEBUG_LOGGING.md) | you need to find out why: a temporary debug log, with its full source and the awk to read it |

The Grim rulebook is `client/anticheat/Grim.md`.

## Hardening a module

Begun 2026-10-29: make each combat module less laggy and more reliable.

1. **Measure first.** Add a temporary debug log ([DEBUG_LOGGING.md](knowledge/DEBUG_LOGGING.md))
   and test against the Fake Player. To check damage, test against a real player on
   a server that shows health.
2. **Read it** with awk: outcomes split by path and aim, time from cause to
   action, the same action repeated tick after tick.
3. **Fix what the log shows,** against the checklist below.
4. **Write it up** in the module's file in `modules/`. Put anything that holds
   beyond that module into `knowledge/`.
5. **Delete the debug log.**

### Checklist

1. Does every attack aim at the nearest point of the box, or at a shared look
   checked against reach with a margin? Does every computed look handle eyes
   inside the box? ([AIMING.md](knowledge/AIMING.md))
2. Is every action submitted between ticks backed by a look already held on its
   target? ([ACTIONS.md](knowledge/ACTIONS.md))
3. Does each tick need at most one look? If not, which action waits, and is that
   the right one?
4. Is state recorded only on `SENT`, and is `REFUSED` handled where the client's
   prediction is known to be wrong? ([ACTIONS.md](knowledge/ACTIONS.md))
5. Does an action that needs another's effect use `afterAttack()`, and does a
   crystal the module hits itself get inhibited? ([PLACEMENT.md](knowledge/PLACEMENT.md))
6. Are waits tied to ping? Is anything timed to a window judged a tick early?
7. Does it model the target's hurt cooldown, or at least not assume more hits
   means more damage? ([HURT_COOLDOWN.md](knowledge/HURT_COOLDOWN.md))
8. Does it skip work it can't act on? ([PERFORMANCE.md](knowledge/PERFORMANCE.md))

## The combat modules

| Module | State | Known to check |
|---|---|---|
| [Crystal Aura](modules/CRYSTAL_AURA.md) | **Hardened**, five test logs | Hurt Sync's damage unmeasured; a refused hit opens a window |
| [Surround](modules/SURROUND.md) | **Hardened** from the checklist, untested | Test on Grim |
| [Breach](modules/BREACH.md) | Planned, step 1 of 4 built | Steps 2–4 |
| Bed Aura | Not yet | `hurtWindow` on its search; bed uses predicted `REFUSED`; aims at the edge of reach |
| Kill Aura | Not yet | Aims at the target's eyes through its own rotation: reach at the edge, eyes in the box; no hurt-cooldown model |
| Hole Fill | Not yet | A `REFUSED` block place repeated every tick |
| Web Aura | Not yet | The same |
| Auto Totem | Not yet | — |
| Auto XP | Not yet | — |
| Phase | Not yet | Attacks whatever is in the way every tick until it is gone: a crystal there should be inhibited once hit |
| Velocity | Not yet | — |
