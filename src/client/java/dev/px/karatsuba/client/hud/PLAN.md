# HUD rework plan

The HUD elements in `element/` were proofs of concept. This is the plan for giving
them a real design: what the research says, what was decided, the rules every
element follows, the order to build in, and what is still open. `HUD.md` remains
the list of what exists and what is worth building; this file is how it should look
and behave.

Decided on 2026-10-09. Now Playing is the reference for motion: it sizes to its
content and eases between sizes.

---

## What the research says

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

## Decisions

| Topic | Decision |
|---|---|
| Overall look | **Every element on a plate** (changed 2026-10-09 from hybrid). Not a setting: one look reads as one HUD, and a plate protects text over any scene. Plate Opacity cannot go below 30% |
| Style control | **One global HUD Style** that every element inherits, with per-element overrides |
| Grouping | **Docking stacks.** Drop elements onto each other in the editor to form a stack sharing one panel and spacing |
| Font | **A list setting** fed by the fonts in `assets/karatsuba/font` |
| Text protection | **Removed** with floating text: nothing draws without a plate. `Render2D.textOutlined` stays in Core for anything that needs it |
| Colour | **Accent labels, white values:** `Ping` in the accent, `42ms` in white |
| Alignment | **Automatic by anchor,** overridable per element |
| Context | **All four:** fade when idle, hide or dim in screens, value-change cues, avoid the vanilla HUD |

---

## Design rules

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

## Edge cases to handle

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

## Build order

### 1. Foundation (done)
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

### 2. Font list (done)
- **`client/setting/ChoiceSetting`:** one of a list of options read at runtime;
  the value is kept even when its option disappears, and falls back to the
  first option until it returns. `ChoiceRow` now serves both it and Core's
  `EnumSetting` through one small interface, so it gets the segmented control
  and the menu for free.
- **`Assets.fontFiles()`** lists `assets/karatsuba/font` through Fabric's mod
  container (a folder in dev, the jar's file system when built), `.ttf` and
  `.otf`. `HudFonts` puts "Default" (the client's own font) first and labels
  files readably ("Inter-SemiBold.ttf" is "Inter SemiBold"). Fonts load lazily
  at the client font's size.
- **Font** is the first setting in the style's Text group, global and per element.
- Untested with a real font file: the folder is still empty. Drop one in and it
  appears on the next launch.

### 3. Migrate the elements (done)
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

### 4. Docking stacks (done)
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

### 5. Context (done)
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

## Open questions

Asked when each step is reached.

- **Stacks:** settled: spacing by default, dividers per stack.
- **Idle fade:** how long before fading, and which elements fade by default?
  Probably not Coordinates.
- **Vanilla avoidance:** move elements out of the way automatically, or only warn
  in the editor?
- **High contrast:** should there be a mode (7:1 for everything)?
- **Plate opacity default,** and whether the plate takes a tint of the accent.

---

## Sources

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
