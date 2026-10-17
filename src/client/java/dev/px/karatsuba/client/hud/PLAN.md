# HUD plan

The order for building HUD presets, as `DESIGN.md` (beside this file) describes
them. **One step at a time.** Each step builds and is checked before the next
starts, and **Legacy keeps working at the end of every step**: a player on Legacy
never sees the HUD break or change unasked. Tick a step off here when it is done
and verified.

The research behind the HUD's rules, and how the 2026-10-09 rework was built, are
kept at the bottom under **History: 2026-10-09 rework**.

**Verifying in game is the developer's.** Steps are checked in a harness where one
can show the change; anything that needs a world (backgrounds, cues, avoidance,
the in-game matrix of `DESIGN.md` §8.5) is checked in game by the developer, and
each step says which.

**Before a step starts**, the `DESIGN.md` §9 questions it depends on are settled
(listed per step under *Needs*). The four answers pending sign-off (Switching,
Apple appearance, Reduce Transparency, Custom Style) are treated as settled unless
the developer says otherwise.

---

## 1. Roles and preset plumbing, Legacy only

Nothing visible changes. The HUD is drawn through a preset, and the only preset
is Legacy, drawing exactly what it draws today.

- **Preset seam:** a preset type (tokens plus a way of drawing every role,
  `DESIGN.md` §1.2, §3), a registry of presets, and Legacy as the first, built
  from today's `HudStyle` and `StyledElement` drawing.
- **Roles:** `StyledElement`'s helpers produce the roles of §3.2 (`label` →
  label, `fixed` → value, `bars` → meterFill and meterTrack, and so on), and the
  preset draws them. New helpers for roles no element uses yet (icon, iconWell,
  toggleOn, track, titleBlock), each with Legacy's simplified drawing (§3.3).
- **Settings untouched:** `hud-style.json`, `hud.json` and every element's
  settings load as before. No settings page changes.
- *Done when:* every element looks the same as before in a harness screenshot
  taken before and after, in the default and a light theme, and the client starts
  with no errors.
- *Needs:* nothing.

## 2. Health colours and ArrayList Tint into Legacy

Still nothing visible changes.

- **Health:** `element/Health`'s good, fair and bad become the good, fair and bad
  colour roles, mapped by Legacy to today's values (§3.4). No element names a
  colour.
- **ArrayList Tint:** the setting moves from the ArrayList element to Legacy's
  settings (§1.7, §5). A saved Tint in `hud.json` carries over to its new place.
- *Done when:* Ping, TPS, Server and ArrayList look as before; a profile saved
  before the move keeps its Tint; no element source contains a literal colour.
- *Needs:* nothing.

## 3. The new settings pages

The settings take their new shape (`DESIGN.md` §1.4, §1.8), in both GUIs' HUD
pages. Legacy still looks the same with every setting at its default.

- **Appearance card:** Preset (Match GUI, Apple, Legacy; §1.5) and Accent (the
  theme picker). With Apple not built yet, the Apple choice is not offered.
  **Legacy Settings…** opens a page of Plate, Text, ArrayList (Tint) and a Reset.
- **Accessibility card:** Reduce Transparency (following the Apple UI's until set),
  Increase Contrast, Text Size, Motion (§1.6, §2.9–§2.11), each working in Legacy:
  - Reduce Transparency: Legacy's plates fully opaque.
  - Increase Contrast: a 1px border, and every colour lifted to 7:1.
  - Text Size: Large and Larger scale the chosen font.
  - Motion: Reduced keeps fades only; Off is instant.
- **Behaviour card:** today's Context settings, Idle Opacity included (§2.8), and
  Hide with F3.
- **Element pages:** the element's own settings, then **Display** (Align, Fade When
  Idle, Custom Style), then Layout.
- **Migration:** saved values keep their meaning. Animate off becomes Motion: Off;
  the Context values move with their settings; nothing resets.
- *Done when:* every setting changes Legacy as `DESIGN.md` says, in a harness; old
  profiles load unchanged; the client starts with no errors.
- *Needs:* the Text Size fourth-step question; the no-italics proposal (whether
  Legacy's Font menu keeps the italic fonts).

## 4. The Apple preset

Built in parts, each one usable before the next. Legacy is untouched throughout;
Match GUI starts choosing Apple once part 4a is done.

- **4a. Glass plates:** Regular glass behind each element over the game (§4.1),
  drawn by the host at the element's scaled bounds, never inside its transform
  (§4.2), from one shared capture (§4.3), thicker for larger plates (§4.4), solid
  with Reduce Transparency (§4.5). Includes finding how the HUD overlay orders its
  passes against NanoVG's frame (Apple rules §1.4).
- **4b. Text and shapes:** SF Pro, primary semibold, the vibrancy levels, labels
  secondary and values primary (§4.8); fixed, capsule and concentric shapes
  (§4.9); no glass on glass (§4.10); every role drawn (§3.3).
- **4c. Glass and Appearance settings:** Clear and Tinted (§4.1); Auto, Light and
  Dark, with Auto's capsule hysteresis (§4.14); adaptive shadow and tint from the
  shared capture (§4.6).
- **4d. Emphasis and icons:** the accent only where §4.7 allows, an original glyph
  set (§4.11), and the accent variants of §5b.2.
- **4e. Motion and focus:** springs from the source, the rim cue (§4.12), glass
  receding in screens (§4.13), and each Motion setting.
- *Done when:* every element draws in Apple in every Glass, Appearance and
  Accessibility combination (§8.4), in a harness; the capture budget holds with
  every element showing (§8.2); in game, the developer has seen it over a bright
  and a dark world.
- *Needs:* Apple sizing, Apple alignment, Debug's exemption, the screen-edge inset,
  SF Pro's licence (before any public build), and whether Connection exists as an
  element (§4.9 names it).

## 5. The Apple editor

Apple plan step 15, with the Apple preset's editor chrome (`DESIGN.md` §4.15,
§7): the Edit HUD pill, elements at full quality with an accent glow on the
selected one, a circular minus badge to hide, snap guides and undo. The Classic
editor is untouched.

- *Done when:* editing works in the Apple UI, in either preset, and the Classic
  editor behaves as before.
- *Needs:* the grouping mechanism, if grouping is to be edited here.

## 6. Verify

The whole of `DESIGN.md` §8: every combination (§8.4) in a harness, and the
in-game matrix (§8.5) in game by the developer.

- *Done when:* every combination passes, or each failure has a fix planned here.

---

## History: 2026-10-09 rework

The plan the HUD was reworked to on 2026-10-09, kept as it was then (apart from notes
marking what has since changed). Where it disagrees with `DESIGN.md`, `DESIGN.md` wins.


The HUD elements in `element/` were proofs of concept. This is the plan for giving
them a real design: what the research says, what was decided, the rules every
element follows, the order to build in, and what is still open. `HUD.md` remains
the list of what exists and what is worth building; this file is how it should look
and behave.

Decided on 2026-10-09. Now Playing is the reference for motion: it sizes to its
content and eases between sizes.

---

### What the research says

**Legibility comes first, and the background is the enemy.** A HUD sits over a world
the client does not control: white text that reads in a forest disappears against
snow. The ways to protect it, from strongest to lightest:

| Protection | Strength | Notes |
|---|---|---|
| Solid plate | Most reliable | Xbox guidelines: let players put a solid backing behind text *and adjust its opacity* |
| Scrim / gradient | Strong | Material: the midpoint sits about 3/10 toward the dark end, so there is no hard edge |
| Outline / halo | Strong, light | Borrowed from avionics: a thin dark edge around each glyph, almost invisible but effective over bright scenes |
| Drop shadow | Weakest | Measurably better than plain text in an eye-tracking study; test it against the brightest scenes |

**Contrast** (XAG 102):
- 4.5:1 for normal text, 3:1 for large text, 7:1 in a high-contrast mode.
- For anything that is not a solid backing, measure against the *brightest* part of
  what is behind it.
- Never signal with colour alone. Red against green fails common colourblindness,
  and Ping and TPS currently rely on exactly that.

**Size** (XAG 101):
- At least 18px body height on a 1080p PC screen.
- Players can scale to 200% without losing anything.
- Text that scrolls must still be readable. Now Playing's scrolling title pauses at
  the start of each pass, which qualifies.

**Glanceability.** A HUD element gets a look of about 200ms.
- Keep the centre of the screen clear; anchor elements to edges and corners.
- Elements that are not needed every second can fade when idle and animate back in
  when they change.
- The test for every element: does the player need this every second?

**Grouping** (Gestalt, common region):
- An enclosing boundary such as a card overrides spacing as a grouping cue.
- So a card on every element stops related elements reading as related. Too many
  boxes is clutter; let spacing do the grouping wherever it can.

**Other PvP clients.** Lunar-like HUD mods use transparent modules with text shadows
by default. That floating-text look is what players of those clients are used to.

---

### Decisions

| Topic | Decision |
|---|---|
| Overall look | **Every element on a plate** (changed 2026-10-09 from hybrid). Not a setting: one look reads as one HUD, and a plate protects text over any scene. Plate Opacity cannot go below 30% |
| Style control | **One global HUD Style** that every element inherits, with per-element overrides |
| Grouping | ~~Docking stacks~~ **Removed 2026-10-17** to be redesigned; see `DESIGN.md` |
| Font | **A list setting** fed by the fonts in `assets/karatsuba/font` |
| Text protection | **Removed** with floating text: nothing draws without a plate. `Render2D.textOutlined` stays in Core for anything that needs it |
| Colour | **Accent labels, white values:** `Ping` in the accent, `42ms` in white |
| Alignment | **Automatic by anchor,** overridable per element |
| Context | **All four:** fade when idle, hide or dim in screens, value-change cues, avoid the vanilla HUD |

---

### Design rules

Every element follows these, through the shared base class (build step 1).

1. **A plate behind every element**, in the style's plate colour, opacity (30% at
   least), radius and padding. Notifications draws one per toast instead.
2. **Global HUD Style.** Panel colour and opacity, corner radius, padding, row gap,
   font, label colour (the accent), value colour, muted colour.
   An element with **Custom Style** on shows its own copies of these; off, it reads
   the global ones. Never copied at registration: read live, so a change to the
   global style shows at once on everything inheriting it.
3. **Accent labels, white values.** Anything that changes colour with state (good
   ping vs bad ping) also changes shape or wording, never colour alone.
4. **Mirroring by anchor.** Right-anchored elements right-align their text and grow
   leftwards (and Now Playing's cover moves right); centre-anchored elements
   centre; left-anchored stay as they are. An Align setting (Auto / Left / Centre /
   Right) overrides it.
5. **Stable widths.** Numbers are sized by their widest possible value (the trick
   Now Playing uses for its time row), so ping, TPS and coordinates never make a box
   jitter.
6. **Animate structure, not values.** The resize animation runs when a row turns on
   or off, or the content changes kind (a new track, a podcast after a song). A
   value ticking over does not resize anything.
7. **Respect the global Animations switch** (Interface settings) as well as each
   element's own Animate.
8. **Empty states.** An element with nothing to show draws nothing in game and a
   sample in the editor, so there is always something to grab.
9. **Size.** Body text meets 18px at 1080p at the default scale. Element scale
   snaps to steps that keep NanoVG text crisp.

---

### Edge cases to handle

| Case | Problem | Answer |
|---|---|---|
| Values that change every tick | With auto-sizing plus easing, a coordinates box would never stop moving | Rules 5 and 6 |
| Text over a bright scene | Nothing controls what is behind the HUD | Every element sits on a plate of at least 30% opacity |
| Light themes | A plate that follows the theme turns light, and white text vanishes | Value and label colours derive from the plate's luminance when the plate is on |
| Right side of the screen | Elements laid out left to right everywhere look wrong on the right edge | Rule 4 |
| Neighbouring elements | Stacked cards with gaps read as a list; touching ones look like a mistake. HUD.md already flags overlapping defaults (Ping and Notifications, ArrayList and Server) | Docking stacks; distinct defaults |
| Vanilla HUD collisions | Hotbar, boss bars, scoreboard, effect icons, chat when open, F3 | Avoidance zones (build step 5) |
| Empty element | Nothing to show, nothing to grab in the editor | Rule 8 |
| Fractional scale | NanoVG text goes soft at odd scales | Rule 9 |
| Blur behind panels | Looks great, but NanoVG cannot do it | Out of scope; it would need Core's shader pipeline |

---

### Build order

#### 1. Foundation (done)
- **HUD Style** (`style/HudStyle`, config `hud-style.json`, per profile): Plate,
  Text and Motion groups, edited from the Style entry at the top of the HUD
  section's list.
- **`style/StyleSettings`:** the one set of style settings, made once for the
  global style and once inside every element for Custom Style.
- **`style/StyledElement`:** the base class. An element writes `describe(body)`
  and gets the plate or floating text, the resolved style, alignment by anchor,
  size easing, and the helpers `text`, `label`, `fixed` (stable width), `stat` and
  `bars` (a signal meter, so state is shape as well as colour). Its Style group
  (Background, Align, Custom Style) shows after its own settings.
- **Outline text:** a Core addition, `Render2D.textOutlined` with
  `Content.textOutlined`. Eight copies half a unit out. Not `nvgFontBlur` as first
  planned: a blurred glyph is rasterised at the unscaled size and turns blocky once
  the HUD scales it, which a harness test showed plainly.
- Also in Core: `Content.include` (nest a box built and measured on its own) and
  `Content.isEmpty`.
- **First migrated:** Ping and TPS.

#### 2. Font list (done)
- **`client/setting/ChoiceSetting`:** one of a list of options read at runtime;
  the value is kept even when its option disappears, and falls back to the
  first option until it returns. `ChoiceRow` now serves both it and Core's
  `EnumSetting` through one small interface, so it gets the segmented control
  and the menu for free.
- **`Assets.fontFiles()`** lists `assets/karatsuba/font` (one folder per family since 2026-10-17, so options are paths like `inter/inter-italic.ttf`; old bare names in saved configs are mapped by `HudFonts.current`) through Fabric's mod
  container (a folder in dev, the jar's file system when built), `.ttf` and
  `.otf`. `HudFonts` puts "Default" (the client's own font) first and labels
  files readably ("Inter-SemiBold.ttf" is "Inter SemiBold"). Fonts load lazily
  at the client font's size.
- **Font** is the first setting in the style's Text group, global and per element.
- Untested with a real font file: the folder is still empty. Drop one in and it
  appears on the next launch.

#### 3. Migrate the elements (done)
Every element is on `StyledElement`:

| Kind | Elements |
|---|---|
| Plate | Watermark, Coordinates, ArrayList, Server, Ping, TPS, Now Playing, Debug |
| A plate per item | Notifications (a card per toast, `platesItself`) |

- Their own colour and background settings are gone; the style and each
  element's Style group replace them.
- Now Playing lost its own easing (the base eases the box), fades its plate with
  it (`opacity()`), and its Cover gained Auto, which follows the anchored side.
- Coordinates gained Arrangement (stacked or inline) and fixed-width numbers.
- Default positions no longer overlap (see `HUD.md`).
- Debug still draws its text in the default font; it is a dev tool.
- Future elements from `HUD.md` (Target, Armour, Combat Info) start on the base
  class.

#### 4. Docking stacks (built 2026-10-09, removed 2026-10-17)
Deleted so grouping can be redesigned (`DESIGN.md`). What follows is how it worked, for reference.
- **`stack/HudStacks`** (config `hud-stacks.json`, per profile): each stack is a
  list of element ids, the first the **host**. A client idea laid over Core's
  elements, so Core's layout model and editor are unchanged.
- **Drawing:** the host draws every follower under itself on its own plate and
  alignment (`StyledElement.stacked`); a follower draws nothing of its own. Members
  keep their own settings and style colours. Sections are spaced by the style's
  padding, with an optional thin divider per stack. The whole stack eases as one.
- **Host hand-over:** when the host leaves or another member moves to the top, the
  new host takes the old one's layout, so the stack never jumps. An element taken
  out is set down just clear of the stack (below, or above for a bottom anchor).
- **Editor:** hold Shift while dropping one element on another to stack them; the
  target is outlined with "Stack here". Uses `HudEditor.isDragging()`, a one-line
  Core addition, so a resize never docks.
- **HUD section:** a Stack section before Layout: Stacked With (or Nothing), Order
  (Up, Down) and Dividers. A follower shows what places it instead of Layout.
- Notifications cannot stack: it draws a card per toast (`dockable()` is false).
- Docking is not undone by the editor's Ctrl+Z, which snapshots layouts only.

#### 5. Context (done)
All in the HUD Style's new **Context** group, and the base class:
- **In Screens** (Show / Dim / Hide, default Dim): chat and every game screen; the
  client's own GUI does not count, so the HUD section previews live.
- **With F3** (Show / Hide, default Hide).
- **Fade When Idle**, per element in its Style group (on by default for Now
  Playing only), with the global **Idle After** (seconds) and **Idle Opacity**.
  Elements say what counts as a change through `watch(idle, cue)`.
- **Change Cues:** when an element's cue key changes (Ping or TPS health, a lag
  spike, a module switched on, a new track, an anticheat detected) its text lights
  towards the accent and settles over 0.7s. Coordinates never cue.
- **Avoid Vanilla HUD:** `adapter/game/GameHud.vanillaZones()` reports the hotbar
  and status bars, boss bars (through a new `BossBarHudAccessor` mixin), effect
  icons and the scoreboard, in the game's own layout numbers.
  `style/VanillaAvoidance` is a Core `PlacementAdjuster` (a new `HudService` hook,
  skipped in the editor) that pushes an element the shortest way out, eased, and
  only while the obstacle shows. Nothing is written to its layout.
- Whole-element fades use a new Core `Content.opacity`, so plate and text fade
  together.
- Verified in the harness: stacks, dividers, idle fade. Cues and avoidance need a
  world and have not been seen in game yet.

---

### Open questions

Asked when each step is reached.

- **Stacks:** settled: spacing by default, dividers per stack.
- **Idle fade:** how long before fading, and which elements fade by default?
  Probably not Coordinates.
- **Vanilla avoidance:** move elements out of the way automatically, or only warn
  in the editor?
- **High contrast:** should there be a mode (7:1 for everything)?
- **Plate opacity default,** and whether the plate takes a tint of the accent.

---

### Sources

- [Xbox Accessibility Guideline 102: Contrast](https://devdocs.xbox.com/build/game-principles/accessibility/xag-deep-dives/xag-102-contrast.md)
- [Xbox Accessibility Guideline 101: Text display](https://devdocs.xbox.com/build/game-principles/accessibility/xag-deep-dives/xag-101-text-display.md)
- [Nielsen Norman Group: The principle of common region](https://www.nngroup.com/articles/common-region/)
- [Material Design imagery and scrims (mirror)](https://www.mdui.org/en/design/1/style/imagery.html)
- [HUD design: readable against every background](https://abratabia.com/game-ui-design/hud-design.php)
- [How to design a readable HUD](https://bugnet.io/blog/how-to-design-a-readable-hud)
- [Game UI design best practices](https://generalistprogrammer.com/tutorials/game-ui-design-best-practices)
- [Text halo for readability against bright backgrounds](https://forums.frontier.co.uk/threads/text-halo-outline-to-improve-readability-against-bright-backgrounds.297712)
- [TU Delft: legibility, font size and drop shadow over complex backgrounds](https://repository.tudelft.nl/file/File_15420783-93bb-4277-8305-e4f285d0ea27)
- [HudBase: a Lunar-inspired HUD mod](https://www.curseforge.com/minecraft/mc-mods/hud-base)
- [Lunar Client: the best HUD setup](https://www.lunarclient.com/news/the-best-hud-setup-for-lunar-client)
