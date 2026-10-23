# HUD plan

A working document for Karatsuba's HUD elements, like `module/MODULES.md`: what
exists, what is worth keeping, and what to build. How elements should look and
behave is in `DESIGN.md`; the history and research of the 2026-10-09 rework are in `PLAN.md`.

## The bar for an element

An element earns screen space if it shows something you would otherwise have to
stop and work out, at a glance, in a fight or out exploring. Decoration and
numbers you never act on are filler.

## How the HUD works today

- **Elements** describe their content. Core measures, anchors, clamps, scales,
  orders, hit-tests and saves them (`HudService`). No element computes a coordinate.
- **Two editors:**
  - **HUD Editor** (Right Ctrl): drag, scale, hide, lock, nudge and re-order
    elements on screen.
  - **HUD Elements** screen (Insert): show or hide each element and edit its
    settings, apart from the click GUI.
- **Saved** per profile in `hud.json`: layout and each element's settings.

## What exists today

| Element | What it shows | Data from | Settings | Default spot |
|---|---|---|---|---|
| **Watermark** | Client name (label) and version (muted) | `Core` | Show Version | top left |
| **ArrayList** | Enabled modules and their display info | `Core.modules()` | Show Info, Tint | top right |
| **Coordinates** | Position, fixed-width numbers | the player | Arrangement (stacked or inline), Labels, Precision | bottom left |
| **Notifications** | Toast queue, slid and faded, a card per toast | `Core.notifications()` | Width | middle right |
| **TPS** | Server tick rate against the server's own target, with quality bars | `Core.tps()` | Quality Bars, Colour by Health | bottom right, 24 up |
| **Ping** | Ping, or how long the server has been silent, with quality bars | `Core.lag()` | Show Lag, Quality Bars, Colour by Health, Good Below / Bad Above | bottom right |
| **Server** | Address, software, probable anticheat with its confidence | `Core.server()`, `Core.anticheat()` | Address, Software, Anticheat, Raw Brand | bottom right, 44 up |
| **Now Playing** | Cover art, title, artist, album, progress bar and time; fades in and out, nothing when idle | `Core.integrations()`, fed by the adapter's Spotify provider; covers via `RemoteImages` | Layout (Cover auto/left/right/off, Fit Cover To Text, Cover Size, Auto Width, Width, Long Titles), Show (Artist, Album, Progress Bar, Time, While Paused) | middle left |
| **Compass** | Which way you face: a strip of N/E/S/W (and fainter NE/SE/SW/NW) with 15-degree ticks scrolling under a centre mark, the heading in degrees below; a quarter turn across its width | `Players.rotation()` | Width, Show Degrees | top centre, under Task |
| **Potions** | The status effects on the player: icon, name and level, time left, soonest to run out on top; rows grow in and shrink away | `adapter/game/player/Effects`, icons through `adapter/asset/GameTextures` | Show Icon, Show Amplifier, Show Duration | bottom left, above Coordinates |
| **Debug** | Connection, movement, server, busiest packets, packet feed, alert log | `DebugMonitor`, Core network services, simulation | sections, packet and alert filters | top left, 24 down |

## Problems found while listing

| Problem | Detail | Suggestion |
|---|---|---|
| ~~Default layouts overlap~~ | Fixed 2026-10-09: Notifications moved to middle right, Server to bottom right above TPS, Now Playing to middle left. Every element now also has a Style group (Background, Align, Custom Style) and follows the HUD Style (see PLAN.md) | Done |
| Debug is a dev tool | 355 lines, packet feeds and filters: invaluable while building, noise in a fight | Keep, but treat as a dev tool. Hide by default, or move to a dev-only screen with Recorder and Fake Player |
| Watermark is filler | No settings, nothing to act on | Keep it small, or merge into a client info element (name, version, profile, FPS) |
| TPS, Ping and Server are three elements for one subject | All "how is the connection" | Merge into one **Connection** element with toggleable rows, like Debug's sections |
| Nothing for combat | The combat set (auras, Auto Totem, Auto XP, Surround) has no HUD at all; you learn its state from ArrayList display info | See the ideas below |

---

## Ideas worth building

Effort: **S** about a day, **M** a few days.

### 1. Combat Info · M
One panel for the fight: totems left, enemy pops this fight, gapples, crystals and
obsidian counts, and which combat modules are acting this tick (from
`Services.actions()` holders). Rows hide when they do not apply.
- **Builds on:** `Inventories.count`, `TotemPopEvent`, `ActionService` holder names,
  Auto Totem's mode.

### 2. Target HUD · M
The current target of the crystal or bed aura: name, health (and whether it is
trusted), armour wear, the damage the last place or break did, and whether they
are in a hole or heading for one.
- **Builds on:** the combat kit's `vitals()` and `armourWear`, the aura's last
  `PlaceOption` and `BreakOption`, `kit.holeWatch()`.
- **Open:** one element for every aura, showing whichever has a target?

### 3. Armour · S
Your four armour pieces with durability bars, Mending marked, and the lowest
Mending percentage that Auto XP works towards. Colour-coded as pieces wear.
- **Builds on:** `Armor`, `Mending`, `ItemIcons`.

### 4. Connection · S
TPS, Ping and Server merged into one element with rows you choose, plus lag-spike
and silence state from `Core.lag()`. Replaces three elements.

### 5. Speed · S
Blocks per second, horizontal and vertical, with your Grim-relevant movement
state: sprinting, sneaking, and whether the Move Fix is correcting your keys this
tick (useful while tuning Scaffold and the auras).
- **Builds on:** `Players.velocity()`, `Rotations`.

### 6. Effects · S
Active potion effects with time left, sorted by what matters in a fight (Strength,
Resistance, Weakness, Fire Resistance) and flashing when about to run out.

### 7. Radar · M
A minimap of nearby players and their predicted heading, using
`Core.prediction()`, with friends coloured apart. Pairs with Predict ESP.

### 8. Inventory Preview · S
Your main inventory as a grid, showing what Inventory Manager would move next and
what the hotbar refill is waiting on.

---

## Suggested order

1. **Fix the overlapping defaults.** Small, and every fresh config hits it.
2. **Combat Info and Armour:** the combat set already exists and has no HUD.
3. **Connection:** merge TPS, Ping and Server.
4. **Target HUD**, then the rest as the modules they show arrive (Radar with
   Predict ESP, Inventory Preview with Inventory Manager).

## Questions to settle

- Merge TPS, Ping and Server into Connection, or keep them separate for layout freedom?
- Debug: keep it as a HUD element, or move it into a dev-only screen?
- Should the HUD Elements screen group elements (Combat, Connection, World) the
  way the click GUI groups modules?
