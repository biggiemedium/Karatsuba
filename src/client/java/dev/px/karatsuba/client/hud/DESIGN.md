# DESIGN — HUD

Rules for Karatsuba's HUD: the elements drawn over the game, how they are drawn,
and how a player switches between **presets**. **Obey them at all times** for HUD
work. If a rule gets in the way, change this file first, on purpose, then the
code.

> **Status: draft, started 2026-10-17.** The direction is set: one HUD, drawn by a
> preset the player picks (Apple first, Legacy kept), with an accent theme,
> accessibility settings and behaviour settings chosen alongside it (§1.8). Much
> of the detail is still open, or proposed but not signed off; both kinds are in
> §9. The build order is `PLAN.md`. Grouping (the old docking stacks) was deleted
> on 2026-10-17 to be redesigned (§6).

| What | Where |
|---|---|
| These rules | `client/hud/DESIGN.md` |
| The build plan for presets, and the history of the 2026-10-09 rework | `client/hud/PLAN.md` |
| Which elements exist, ideas worth building | `client/hud/HUD.md` |
| The elements | `client/hud/element/` |
| The base class every element extends | `client/hud/style/StyledElement` |
| Layout engine: anchors, scale, clamping, z-order, editing, saving | Core, `dev.px.core.hud` (`HudService`, `HudLayout`, `Anchor`, `HudEditor`) |
| The GUIs' HUD and Layout pages | `gui/stable/shell/section/{hud,layout}`, `gui/apple/shell/section/{hud,layout}` |

The root `DESIGN.md` §8 and the Apple `DESIGN.md` §10 say how each GUI presents
the HUD. This file is the HUD itself and overrides them where they disagree.

**Words.** A **preset** is a whole way of drawing the HUD (Apple, Legacy). The
**HUD Style** is the name of today's settings page and of each element's Custom
Style: from now on those are **Legacy's settings** (the "Legacy Settings…" page,
§1.8), and "style" is never used to mean a preset.

**Two meanings of "Clear", never to be confused.** Apple's Liquid Glass has a
*Clear variant*, a separate kind of glass, which this HUD never uses (§4.1).
Apple's *Clear | Tinted* setting (iOS 26.1) is something else: two opacity levels
of the *Regular* glass. The Apple preset's **Glass** setting (§1.4) uses those
words in that second sense only.

---

## 1. One HUD, chosen in four places

1.1 **Elements say what they show; the preset says how it is drawn.** An element
describes roles (§3): text in a role, a value, a meter, an icon, a row, a group.
It never picks a colour, a font, a radius, a plate or a timing. The active preset
turns that description into pixels.

1.2 **A preset is a set of tokens plus a way of drawing every role** (§3). At the
least: the plate (shape, material, edge, shadow), typography (font, sizes,
weights), the colour roles, spacing, motion, and the editor's chrome. Adding a
preset is writing those and registering it; no element changes.

1.3 **The presets:**

| Preset | What it is | State |
|---|---|---|
| **Apple** | Regular Liquid Glass plates over the game, SF Pro, vibrant text (§4) | To build: the first new one |
| **Legacy** | Today's drawing: a dark rounded plate per element, accent labels, white values (§5) | Exists; becomes one preset among several |
| Others | Room for more | Not planned yet |

1.4 **What the player chooses, and where** (each is independent; **any
combination must work**):

| Card | Setting | Values | In which presets |
|---|---|---|---|
| **Appearance** | Preset | Match GUI (default), Apple, Legacy (§1.5) | all |
| | Accent | the theme picker, unchanged (§5b) | all |
| | Glass | Clear, Tinted (§4.1); follows the Apple UI's Glass slider until set here | Apple only |
| | Appearance | Auto, Light, Dark (§4.14) | Apple only |
| | Legacy Settings… | a page of Legacy's settings (§5) | Legacy only |
| **Accessibility** | Reduce Transparency | on, off; follows the Apple UI's by default (§1.6) | all |
| | Increase Contrast | on, off (§2.11) | all |
| | Text Size | Default, Large, Larger (§2.9) | all |
| | Motion | Full, Reduced, Off (§2.10) | all |
| **Behaviour** | In Screens, Hide with F3, Change Cues, Avoid Vanilla HUD, Dim Hidden in Editor (off: hidden elements are left out of both editors, §7.1c) | §2.8 | all |

A setting a preset does not use is hidden, not greyed.

1.5 **Preset follows the GUI unless the player says otherwise.** By default it is
**Match GUI**: the Apple UI active means the Apple preset, the Classic UI means
Legacy, switching with the GUI. Choosing Apple or Legacy by hand fixes it there,
and it stops following the GUI until the player chooses Match GUI again. The whole
HUD changes at once.

1.6 **Accessibility settings combine freely, in every preset:**
- **Reduce Transparency:** solid plates, and no glass passes run; in Legacy,
  which has no glass, its plates draw fully opaque. It follows the Apple UI's own
  Reduce Transparency (Apple rules §7.4) until the player sets it here.
- **Increase Contrast:** a visible 1px border on every plate, and everything at
  7:1 (§2.11).
- **Text Size:** Default, Large or Larger (§2.9); three steps, none below the
  minimum. In Legacy it scales the font chosen in Legacy Settings.
- **Motion:** Full, Reduced or Off (§2.10). It follows the Apple UI's Reduce
  Motion (Apple rules §7.5: on means Reduced) until the player sets it here.

1.7 **Settings belong to a preset or to the HUD, never to an element's drawing.**
An element's own settings say what it shows (Ping's Quality Bars, Coordinates'
Precision). How it is drawn comes from the preset.
- **Breaks this today:** ArrayList's **Tint** (Theme Gradient, Label, Value) is a
  drawing setting on an element. It moves to Legacy Settings (§5); Apple has no
  equivalent.

1.8 **The settings pages.** Each GUI's HUD page shows, top to bottom: the
**Appearance** card, the **Accessibility** card, the **Behaviour** card, then the
elements. Each element's page holds its own settings, then a **Display** card
(Align, and Custom Style in Legacy only), then Layout. Legacy
Settings… is a page of its own, reached from Appearance.

## 2. What stays the same whatever the preset

2.1–2.9 carry over from the design rules in `PLAN.md`'s History; every preset
honours them all.

2.1 **Core owns geometry.** No element or preset computes a screen position.
Anchors, offsets, scale, clamping, z-order, hit-testing and saving stay in
`HudService`. Element ids never change: they are the config keys. No preset adds a
screen-edge inset of its own.

2.2 **Legible over any world.** Text reaches 4.5:1 against the brightest
background its plate can show through (3:1 for large text). A see-through preset
proves it against a bright scene (snow, sky), not an average one.

2.3 **Never colour alone.** State shown in colour (good, fair or bad ping) is
also shown by shape or words: bars lit, "Lag 1.2s".

2.4 **Stable widths.** Anything that changes every tick is sized by its widest
value (`fixed`), so no box jitters.

2.5 **Animate structure, not values.** A box eases when a row appears or the
content changes kind, never because a number ticked over.

2.6 **Alignment follows the anchor.** Right-anchored elements right-align and
grow leftwards; centre-anchored ones centre. Per element, Align overrides it. (Apple's
exception is §4.16.)

2.7 **Nothing to show, nothing drawn**, and a sample in the editor so there is
always something to grab (`isEditing()`).

2.8 **Behaviour is the HUD's, not the preset's.** Dim or hide in screens, hide
with F3, change cues, and avoiding the vanilla HUD happen the same in every
preset, from the Behaviour card. A preset only chooses how they are drawn
(Apple's in §4).

2.8b **Nothing fades for being idle.** An element shows until the player hides it;
the HUD never decides on its own that something has been on screen long enough.
An element with nothing to show draws nothing (§2.7), which is not the same.

2.9 **Readable size.** Text Size **Default** already meets the minimum: body text
at least 18px tall at 1080p at the default scale, and still whole at 200%. Large
and Larger only go up from there. Text Size applies in every preset.

2.10 **Motion follows the Motion setting:**
- **Full:** the preset's own motion.
- **Reduced:** fades only; no springs, no scaling, no movement.
- **Off:** no animation at all; every change is instant. A change cue is a steady
  highlight that holds for the cue's time, then switches off with no fade.

It replaces the old Animate switch and a separate Reduce Motion switch.

**Notifications always animate.** A toast slides and fades in and out at every
Motion setting, so one that arrives or goes is never missed; the rest of the HUD
follows Motion.

2.11 **Increase Contrast means 7:1** for every part of the HUD (text, meters,
borders and icons), and a visible 1px border on every plate.

2.12 **Attention-grabbing motion is for notifications and change cues only.**
Sudden appearance, flashing and bouncing belong to those two. Static readouts
stay still.

2.13 **The kind of data decides how it is shown:**
- **Ordered data** (TPS, ping, health, durability) uses meters, bars or fills.
- **Small fixed sets of categories** use icons, or colour plus shape.
- **Open-ended text** (packet names, server strings) only appears in elements
  meant to be read deliberately, such as Debug; never in glanceable ones.

2.13b **The player chooses which notifications show.** Every notification comes
from a source (`client/notify/Notices`): **Module Toggles** (a module switched on
or off), **Module Messages** (what a module reports: Auto Totem, Armor Manager,
Recorder...), **Tasks** (automation finishing, failing, stopping), **Server**
(lag, server software, anticheat) and **Client** (profiles, config, accounts,
updates, the interface switch). The Notifications element's **Show** group has a
switch for each; a source switched off posts nothing. Prompts the player must act
on (signing in, a browser that would not open) always show.

2.14 **No italics in any preset.** No element draws italic text, and the Font
  menu offers no italic-only fonts.

## 3. Roles: what elements describe

3.1 Every element extends `StyledElement` and writes `describe(body)` with its
helpers (`label`, `text`, `fixed`, `stat`, `bars`, `watch`). Each helper produces
a role. New kinds of part become new roles and helpers there, so every preset can
draw them.

3.2 **The roles:**

| Kind | Roles |
|---|---|
| **Text** | primary, secondary, tertiary (inactive things only), value (fixed-width), title, subtitle |
| **Colour** | accent, meterFill, meterTrack, good, fair, bad, separator; listItem (the colour of the n-th entry in a ranked list, such as ArrayList's) |
| **Structure** | icon, iconWell, toggleOn, track (a thick rounded progress bar), titleBlock; categoryMarker (which of a small fixed set something is: a notification's info, success, warning or error, §2.13) |

The `label` helper produces a label. "Label in the accent" is **Legacy's mapping**
of that role, not the role itself: Apple draws labels as secondary text (§4.8).

3.3 **Every preset draws every role,** even if only simply. A preset with no drawing
of its own for a structural role simplifies it: Legacy draws an iconWell as the
bare icon, or leaves it out. **An element never needs a preset-specific version.**

3.4 **No literal colours in elements.** Health's good, fair and bad
(`element/Health`) and any other colour an element uses become colour roles,
mapped by each preset, before the second preset ships.

3.4b **The one exception: the Watermark's logo** (2026-10-23): the K mark beside the
client's name in SF Pro Semibold (no wordmark image, no version). Its artwork comes in
white and in black. At Logo Colour: Auto it picks whichever reads on the plate (white
where the preset's primary text is light), and the name beside the mark is the
preset's primary text. At White or Black the player has chosen the logo's own
colour, and the name matches it. That is the artwork's colour, not one the element
picked.

3.5 An element that draws something no helper covers (Now Playing's cover, a
radar) asks the preset for its roles inside its custom part, so it still changes
with the preset.

3.6 **Debug is exempt.** It stays a plain dev readout, drawn as Legacy draws, in
every preset.

## 4. The Apple preset

The HUD as the Apple UI draws it, after Apple's WWDC25 sessions "Meet Liquid
Glass" and "Get to know the new design system", and the Apple `DESIGN.md` §5, §6
and §10. It follows those rules unless this section says otherwise.

4.1 **Regular glass only.** Never the Clear variant, and never the two mixed. The
Clear variant needs bold, bright media behind it and can take a dimming layer;
Minecraft satisfies neither. Plates frost the game behind them: blur, rim lensing,
specular rim, light tint (Apple rules §6).
- The **Glass** setting picks between two opacity levels of that Regular glass,
  in Apple's own words: **Clear** (the lighter level) and **Tinted**, which raises
  the opacity floor and mutes the rim. Neither is the Clear variant.
- Until the player sets it here, Glass follows the Apple UI's own Glass slider
  (Apple rules §7.3): Tinted from halfway up, Clear below.

4.2 **Glass follows the element's scale.** The host draws each plate's glass at the
element's scaled screen bounds, and scales the radius, rim and highlight by the
same factor. The pass is never drawn inside the scale transform.

4.3 **One capture for every HUD plate**, shared, within the Apple UI's budget of
4 captures a frame (Apple rules §5.5, checked as §12.2 says).

4.4 **Thickness follows size.** Larger plates get thicker glass, more scattering
and a stronger shadow. There is no per-element thickness setting.

4.5 **Reduce Transparency** (§1.6) gives solid plates in the glass's tint colour,
and no passes run.

4.6 **Adaptive.** A plate's shadow and tint strengthen when what is behind it is
busy or bright, sampled from the one shared capture (§4.3), inside the capture
budget.

4.7 **Emphasis comes from grouping, layout and type weight**, not decorative
colour.
- Tint is for emphasis on primary elements only.
- The accent appears only on active toggles (toggleOn), meter fills (meterFill),
  the enabled-module indicator, and the editor's primary button.
- No gradients.

4.8 **Text:**
- SF Pro (`Palette.FONT`), regular and semibold only; primary text is semibold.
- Vibrancy levels: primary, secondary, tertiary. Tertiary is only for inactive
  things, and there is never a fourth, lower level on glass.
- **Labels are secondary** (muted white); **values are primary.**
- The Apple preset has its own padding and type sizes, not Legacy's, scaled by
  Text Size (§1.6).

4.9 **Three kinds of shape:**
- **Fixed radius** (`R_HUD_TILE`) for tiles with several rows.
- **Capsule** (radius half the height) for every single-row element: the
  Watermark, TPS, Ping, a notification toast.
- **Concentric** (the parent's radius less the padding) for anything nested, with
  a fallback radius when it stands alone.

Small controls inside a plate are rounded rectangles, not capsules, as on macOS.

4.10 **No glass on glass.** Parts inside a plate use vibrant fills, transparency
and separators. The material goes on the plate, never on its inner parts.

4.11 **Icons** only where they are clear at once; otherwise a text label. One icon
per concept, never tweaked variants of it. An original glyph set, not SF Symbols
assets (`preset/apple/HudGlyphs`). Today that is the categoryMarker's four: info (an i),
success (a tick), warning (an exclamation mark), error (a cross), each in a rounded
well tinted by its category's colour, the glyph in that colour.

4.11b **Notifications** are a capsule each: the category marker, the title in
primary text, semibold, and the message under it in primary text, regular. No
countdown line.  ArrayList carries no marker beside its names (removed 2026-10-23).

4.12 **Motion** (at Motion: Full; §2.10 for Reduced and Off): a soft, gel-like
spring, damped to overshoot by about 2% (the Apple rules' limit, §8.1 there).
- Elements arrive from their source: a notification springs in from its anchor
  edge, and a new row expands from where it sits (the plate springs to its new
  size).
- The change cue (§2.8) is a brief brightening of the rim, not of the text. With
  solid plates (Reduce Transparency, Increase Contrast) there is no rim, so the
  text lights as Legacy's does.
- Values never animate (§2.5).

4.13 **In screens:** when a game screen opens (In Screens: Dim), the whole element,
glass and text alike, dims as Legacy's does (§2.8). (A receding glass, more opaque
and flatter, was tried on 2026-10-17 and dropped at the developer's request.)

4.14 **Appearance** (Auto, Light, Dark). **Auto:** single-row capsules switch
light or dark by themselves from what is behind them, with hysteresis so they
never flicker; tiles with several rows follow the Apple UI's appearance setting
(Apple rules §7.1). Light and Dark fix every plate.

4.15 **Editor chrome** (§7; Apple plan step 15): elements at full quality, an
accent glow on the selected element, and a circular minus badge to hide one.

4.16 **Text is left-aligned inside a plate**, whatever the anchor, as Control
Center's tiles are; the anchor still decides which way the plate grows. ArrayList
is the exception, in every preset. Its Align on Auto follows where it is on screen,
as drawn (groups included): left half left-aligned, right half right-aligned. Its
Sort on Auto puts the widest line against the screen's nearer edge: on top in the
top half, at the bottom in the bottom half (Longest First keeps the old order).
Each name may be followed by its module's metadata in secondary text, set off as
[ ], ( ), a bar, a dash or a space; there is no marker beside the names. Each
element's Align setting is hidden in Apple, except ArrayList's.

4.16b **ArrayList animates its lines**, in every preset. (The motion is `hud/style/AnimatedRows`, shared with Potions, whose rows grow and fade the same way without the slide.) A module switched on grows
its line in (260ms, ease-out): the line's height opens from nothing, pushing the
lines below down; its width opens from the side the list lines up on; and its text
slides in from the screen's nearer edge, fading in, clipped to the plate. Switched
off, the same backwards and quicker (200ms). The lines are always exactly one gap
apart while they move. The plate follows the lines exactly rather than easing after
them. What is on when the list first draws is simply there. At Motion: Reduced lines
only fade; at Off they appear and go at once.

## 5. The Legacy preset

Today's drawing, kept as it is, as a preset:
- a plate in Plate Colour at Plate Opacity (30% at least), Corner Radius,
  Padding and Row Gap;
- text in the chosen Font, scaled by Text Size (§1.6);
- **accent labels, white values:** labels in Label Colour (the theme accent by
  default), values in Value Colour (white), and Muted Colour. **This rule is
  Legacy's only**; other presets map the label role their own way (§3.2);
- its good, fair and bad colour roles are today's `element/Health` colours
  (§3.4);
- ArrayList's Tint (§1.7): Legacy's answer to the listItem role, a gradient
  through the theme's accents or one of its text colours. Presets without a Tint
  draw list items as primary text;
- notifications as they are: the categoryMarker is a coloured edge bar (colour
  plus shape), the title in the category's colour, the message in white, and a
  countdown line along the bottom. Category colours are Core's notification
  colours.

Its settings live on the **Legacy Settings…** page (Plate, Text, ArrayList, and a
Reset), shown from Appearance only while Legacy is the preset, and in each
element's Custom Style (§9). Its Font menu offers no italic-only fonts (§2.14).

## 5b. Accent themes

5b.1 **The accent is used only where the active preset's emphasis rules allow it**
(Apple's in §4.7; Legacy's labels in §5).

5b.2 **Every theme gets checked variants, adjusted automatically until they pass**,
never rejected:
- the accent on glass: 3:1 at the glass's brightest, which is Clear glass over a
  white world;
- the accent on the white toggleOn well: 3:1 (the same pair as a white knob on an
  accent fill, since contrast is symmetric);
- the accent on a Legacy plate: 3:1 against the plate made opaque. Only while
  Label Colour follows the theme: a colour the player picked is theirs.

Each variant is the theme's first colour moved toward black or white until it
passes (`preset/color/Accents`); a theme that already passes is unchanged. Where the
accent is text (Legacy's labels, ArrayList's names) it reaches §2.2's 4.5:1
instead, since text needs more than a fill does.

5b.3 **The accent is never the only signal of state** (§2.3), so near-white and
low-saturation accents still work.

5b.4 Whether a theme is one colour, a pair or a palette is open (§9).

## 6. Grouping

6.1 **The docking stacks were deleted** on 2026-10-17 (`HudStacks`, Shift-drop
in the editors, the HUD pages' Stack section, `hud-stacks.json`). An old
`hud-stacks.json` in a profile is now ignored.

6.2 **Element groups replace them** (decided 2026-10-23), so elements never sit
inside one another (`hud/group/HudGroups`, `hud-groups.json` per profile):
- **Attached, edge to edge.** An element in a group is attached to one other, on
  one of its four sides, lined up with that side's start, centre or end. The
  element at the top of the chain (the root) is placed by its own layout; the rest
  follow it. Groups grow in any direction and **touch, with no gap**.
- **They follow.** A group re-stacks every frame, in game and in the editor (Core's
  `PlacementArranger`): when a member grows or shrinks, what is attached to it
  moves to stay against it. A hidden member keeps its place at no size, so the
  chain closes up across it.
- **Snapping on, live.** In either editor, an element dragged over another, or
  with an edge within a few units of one and sharing some of its length, is held
  against the side it needs to move least to reach **on every frame of the drag**
  (Core's `HudEditor.DragConstraint`), lined up with the nearest of start, centre
  and end, so it is never shown inside another. A side with no room on screen is
  skipped, and so is one that would overlap a third element: the nearest spot
  that is clear of everything wins. Letting go attaches it there. Dropping on a
  side that already has something attached inserts it there; what was there moves
  out past it.
- **Pulling out.** Dragging an attached element takes it out of its group once the
  cursor has really moved (a click only selects); whatever was attached to it
  closes up into its place. Dragging the root moves the whole group.
- **Undo and reset.** Attaching and pulling out are undone with the layout (one
  snapshot holds both). Reset All ungroups everything; an element's reset (R, Reset
  Position) takes it out of its group first.
- **Drawing is unchanged:** each member keeps its own plate. Groups are layout
  only, the same in every preset.
- **Not covered:** a group does not push unrelated elements aside; it keeps its
  own members apart, and a drop never leaves one element inside another.

## 7. Editing

7.1 Each GUI keeps its own HUD page and editor. The Classic editor stays as it is.

7.1b **The Apple UI's HUD page** is rebuilt as Tahoe pages, like Settings and
Friends: a list page (a card of HUD Settings and Legacy Settings…, then a card of
the elements, each with its Shown switch and a ›), and each pushed in: HUD Settings
as its Appearance, Accessibility and Behaviour cards; Legacy Settings as its Plate,
Text and ArrayList cards with a two-press Reset below; an element as its own
settings, its Display card and a Layout card, with "Edit on Screen…" below. The
**Edit HUD…** push button (Apple rules §10.1) sits below the elements' card,
right-aligned, as every standalone action does. Not a floating glass pill: one was
built and taken out on 2026-10-23 at the developer's request.

7.1c **The Apple editor replaces the Layout section** in the Apple UI (Apple plan
step 15), opened by the Edit HUD… button, an element's "Edit on Screen…" or Ctrl+E:
- elements drawn at full quality in the active preset, glass and all;
- the selected element ringed by an accent glow;
- a circular minus badge at a shown element's corner hides it, and a hidden
  element is then **gone from the editor entirely** (decided 2026-10-23), coming
  back from its Shown switch on the HUD page. Behaviour's **Dim Hidden in Editor**
  (off by default) keeps hidden elements in the editor instead, faint, with a
  circular plus badge that shows them again;
- dragged by the element itself, with snap guides to the screen's edges and
  centre and to other elements (hold Alt to skip), the corner to scale, and the
  Classic editor's keys (arrows nudge, Ctrl+Z and Ctrl+Y undo and redo, Escape
  steps back);
- a glass toolbar pill at the bottom centre: Undo, Redo, Snap, Reset All, Done.

7.2 An element looks the same in the editor as over the game, in the active
preset, plus the editor's own outlines and guides.

## 8. Verifying

8.1 Screenshot every change in each preset, over a bright world and a dark one, at
the default and 200% scale.

8.2 For a glass preset, check the capture budget (Apple rules §12.2) with every
element showing.

8.3 Say what was not seen (in-world cues, avoidance, a server's player list).

8.4 **Every combination works.** Each preset (and, in Apple, each Glass and
Appearance value) with every combination of the Accessibility settings: Reduce
Transparency on and off, Increase Contrast on and off, each Text Size, and each
Motion setting.

8.5 **The in-game matrix**, checked in game by the developer; a harness cannot
make these backgrounds. Every preset, with Reduce Transparency and Increase
Contrast each on and off, over each of:
- snow at noon;
- desert;
- open sky;
- dense jungle;
- a cave;
- the Nether;
- underwater.

For each combination, check:
- the contrast targets are met (§2.2, §2.11);
- rims are crisp at GUI scales 1 to 4;
- the change cue is visible over snow;
- glass stays readable where vanilla avoidance moves it over the hotbar;
- every accent theme passes (§5b.2).

## 9. Open questions

Settle each before building what depends on it, and move the answer into the
sections above. A **proposed** answer is a recommendation, not a rule, until it is
settled.

**Answered, pending sign-off** (written into the sections above):
- **Switching:** Match GUI by default, with a manual override that holds until
  Match GUI is chosen again (§1.5).
- **Apple appearance:** an Appearance setting, Auto, Light or Dark; Auto lets
  single-row capsules adapt with hysteresis and multi-row tiles follow the Apple
  UI (§4.14).
- **Reduce Transparency and the Apple UI:** the HUD's own setting, in every
  preset, following the Apple UI's Reduce Transparency until the player sets it
  (§1.6).
- **Custom Style:** survives for Legacy only, and is hidden in Apple, whose
  thickness follows size (§1.8, §4.4).

**Settled 2026-10-17:**
- **Text Size:** three steps (Default, Large, Larger); none below §2.9's minimum.
  A compact HUD comes from each element's Scale instead.
- **No italics** in any preset (§2.14); Legacy's Font menu drops Inter Italic and
  Roboto Italic, and a profile set to one falls back to Default.
- **Reduce Transparency in Legacy:** fully opaque plates (§1.6).
- **Motion and the Apple UI:** Motion follows the Apple UI's Reduce Motion until
  set here; under Off a cue is a steady highlight (§1.6, §2.10).
- **HUD Glass:** the HUD's own Clear/Tinted, following the Apple UI's Glass slider
  until set here (§1.4, §4.1).
- **Apple sizing:** its own padding and type sizes, scaled by Text Size (§4.8).
- **Apple alignment:** left-aligned text inside a plate, ArrayList excepted (§4.16).
- **Debug:** exempt from presets (§3.6).
- **Capsules:** every single-row element, not a Connection element (§4.9).
- **No screen-edge inset:** Core's clamping stays as it is (§2.1).
- **No idle fade:** Fade When Idle, Fade When Idle After and Idle Opacity are gone;
  an element shows until the player hides it (§2.8b).

**Settled 2026-10-23:**
- **Grouping mechanism:** element groups, attached edge to edge in any direction,
  touching, following one another as they resize; dragging a member pulls it out,
  dragging the root moves the group; each member keeps its own plate (§6.2).

**Open:**
- **Other presets:** is a third wanted now, or just room for one?

**Proposed:**
- **Per element:** the preset is HUD-wide. There is no per-element preset.
- **Theme shape:** themes today are a pair (a primary and a secondary colour, with
  a gradient between them). If they stay a pair, Apple uses the first colour only,
  as the Apple UI already does (Apple rules §4.3).
