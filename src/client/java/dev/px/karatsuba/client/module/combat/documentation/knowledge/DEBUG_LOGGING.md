# Debug logging a module

How Crystal Aura was measured during its hardening (2026-10-29), written so the
same approach can be rebuilt for any module. The code was deleted once the aura
was done. This file is what remains of it, including the logger's full source.

Every fix in [CRYSTAL_AURA.md](../modules/CRYSTAL_AURA.md) came from reading these logs, not
from guessing. Its first finding was that the search, which everyone suspected,
cost under 1 ms. See [PERFORMANCE.md](PERFORMANCE.md) for why to measure first.

## The shape

The logging was three pieces, all temporary:

| Piece | What it did |
|---|---|
| **The log file** (`CrystalDebugLog`) | One line per event to `<game>/karatsuba/debug/<module>-<yyyyMMdd-HHmmss>.log`, a new file each time the module is switched on. Flushed once a second and on close; a write error stops the log, never the module |
| **The summary** (`CrystalTimings`) | Averages and peaks every 5 s (100 ticks): search costs, place→spawn and spawn→break times, outcome counts per action kind, ticks capped or with nothing worth doing, and ticks spent using an item. Written to the log as `SUMMARY`, and to chat behind a temporary `Timings` setting |
| **Hooks in the module** | A `log(kind, format, args...)` helper that does nothing when the log is closed, called at every decision and every outcome |

The log was always on while the module was enabled, with no setting, so every
test the user ran produced one. The folder is `karatsuba/debug/`, **not** under
`configs/`: the profile loader could mistake a folder there for a profile.

## Line format

```
   ms-since-enable  module-tick  KIND     details
      1513.2           31        SUBMIT   break id 12494382 aim nearest point (crystal) BreakOption(...)
```

The milliseconds come from `System.nanoTime()` since the log opened. The tick is the module's own counter, so lines can be grouped by tick with awk (`$2`) and by kind (`$3`).

## What to log, and where

**At open:**
- `SERVER`: host, software, `Core.anticheat().getPrimary()`, ping, jitter, TPS.
- `SETTINGS`: every setting, `setting.getName() + "=" + setting.toJson()` over `getSettings()`.
- `PROFILE`: the Interaction settings, from `Services.actions().getProfile()`.

Without these, a log can't be compared with the next one.

**Every tick, one `TICK` line** with the state that explains a decision:
- **Held items:** `Inventories.heldItem()` against `Inventories.serverHeldItem()`.
- **Item use and look:** `Players.usingItem()`, and `Players.serverRotation()` (the look the server has, not the camera).
- **The module's own counts:** crystals out, the log's pending count, crystals attacked and not yet gone.
- **This tick's decisions,** with their search times.
- **Ping and TPS.**
- **The target:** name, `vitals().pool`, `vitals().isRecentlyHurt`, distance, and the hurt window left (`kit.log().cooldownLeft`).

**Every decision:**
- `PSEARCH`: time, result, and `search.getLastPlaceStats()` (`SearchStats.toString()` holds everything).
- `CAPPED`, `WAIT`, `STALE`, `SKIP`: why something was not done.
- `SUBMIT`: what was asked of `ActionService`, including which aim it used.

**Every outcome**, from the action's `then`:
- `BREAK` / `PLACE`: the `Outcome`.
- **For `OUTRANKED`:** who won (`getAttackHolderName()` / `getClickHolderName()`).
- **The server's look and the eye position.** This is how the reach and degenerate-look bugs were found.

**What happens afterwards:**
- `SPAWN` (`CrystalSpawnEvent`): whether it's ours, and how long after the place.
- `EXPLODE` (`ExplosionEvent`).
- `GONE` / `STILL`: an attacked entity followed until it disappears; `STILL` after 1.5 s means the server did not take the hit.
- `SETBACK` (`ServerTeleportEvent`), `POP` (`TotemPopEvent`).

## Timing a cycle

Stamp `System.nanoTime()` on the module's own record of what it sent (here `Out.sentNanos`), then fill in later stamps as events arrive:

| Interval | From | To | Who owns it |
|---|---|---|---|
| place → spawn | place `SENT` | its `CrystalSpawnEvent` | the server and network |
| spawn → break | the spawn | the break `SENT` | the module |

Time each search call with `System.nanoTime()` around it.

## Reading a log

Read with awk over line kinds, never the whole file. These answered every question so far:

```sh
# outcome counts by kind
grep "BREAK " F | awk '{print $6}' | sort | uniq -c
# split by path: an action submitted between ticks (from a packet) vs from the tick
awk '/SPAWN .*judged/{s=$5} /SUBMIT   break/{src=($6==s)?"instant":"tick"; s=""; id=$6; aim=($0~/shared/)?"shared":"nearest"} /BREAK  /{if($5==id) c[src" "aim" "$6]++} END{for(k in c) print c[k],k}' F
# spacing between sent breaks, in ticks
grep "BREAK .*SENT" F | awk '{print $2}' | awk 'NR>1{print $1-p} {p=$1}' | sort -n | uniq -c
# everything but TICK lines around one tick
awk '$2>=31 && $2<=40 && $3!="TICK"' F
# the same thing repeated: sent but not recorded
grep "PLACE .*REFUSED" F | awk '{print $4,$5,$6}' | sort | uniq -c | sort -rn | head
```

## Gotchas found the hard way

- **Follow an entity by identity, not by position.** The replacement crystal stands exactly where the broken one stood, so a position check reported `STILL` for crystals that were long gone (10 of 11 false).
- **Don't pass a computed string as the format.** Use `log("KIND", "%s", text)` for any text that might contain `%`.
- **The first search of a session is slow** (19 ms vs under 1 ms) while the JIT warms up. Ignore the first `SUMMARY`.
- **A target can be unmeasurable:**
  - **Fake Player targets.** Our Fake Player (Barnaby) exists only on the client. Its health is whatever the Fake Player module does with it, and the server's hurt cooldown never applies to it. Use it for timing, aim and outcomes, never for damage.
  - **Servers that hide health** (crystalpvp.cc sends no damage events either).
  - **To measure damage,** fight a real player on a server that shows health, and count pops.
- **An outcome is the client's view.** `REFUSED` doesn't mean "not sent" ([ACTIONS.md](ACTIONS.md)). Log the state the outcome was judged against.

## The logger, in full

Copy it into the module's package as a temporary class, renamed for the module. It needs only Core:

```java
package dev.px.karatsuba.client.module.combat;

import dev.px.core.Core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Temporary: one line an event to <game>/karatsuba/debug/<name>-<time>.log. Delete after testing. */
final class DebugLog {

    private static final int FLUSH_EVERY = 20;          // ticks: a second

    private final long started = System.nanoTime();
    private BufferedWriter writer;
    private File file;
    private int sinceFlush;

    static DebugLog open(String name) {
        DebugLog log = new DebugLog();
        try {
            File folder = new File(Core.platform().getDataDirectory(), "debug");
            Files.createDirectories(folder.toPath());
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
            log.file = new File(folder, name + "-" + stamp + ".log");
            log.writer = Files.newBufferedWriter(log.file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException failure) {
            Core.get().getLogger().error("Debug log could not be opened", failure);
            log.writer = null;
        }
        return log;
    }

    File getFile() {
        return file;
    }

    void line(long tick, String kind, String format, Object... args) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(String.format(Locale.ROOT, "%9.1f %6d %-8s ", (System.nanoTime() - started) / 1e6d, tick, kind)
                    + String.format(Locale.ROOT, format, args));
            writer.newLine();
        } catch (IOException failure) {
            fail(failure);
        }
    }

    /** Once a tick. */
    void tick() {
        if (writer != null && ++sinceFlush >= FLUSH_EVERY) {
            sinceFlush = 0;
            try {
                writer.flush();
            } catch (IOException failure) {
                fail(failure);
            }
        }
    }

    void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException failure) {
                Core.get().getLogger().error("Debug log could not be closed", failure);
            }
            writer = null;
        }
    }

    private void fail(IOException failure) {
        Core.get().getLogger().error("Debug log stopped", failure);
        try {
            writer.close();
        } catch (IOException ignored) {
            // the first error is the one reported
        }
        writer = null;
    }
}
```

Wire it into the module:
- **`onEnable`:** `debug = DebugLog.open("my-module")`, then the header lines.
- **`onDisable`:** `debug.close()`.
- **First thing in the tick handler:** `debug.tick()`.
- **Everywhere else:** a `log(kind, format, args...)` helper that checks `debug != null`.

The summary is a small class of running stats (`total`, `max`, `count` in nanoseconds, printed as `avg/max (n)` in ms). It keeps `EnumMap<Outcome, Integer>` counts per action kind, returns its report every 100 ticks, then clears.

**Before deleting,** record what the logs showed in the module's documentation file. The logs themselves are not kept.
