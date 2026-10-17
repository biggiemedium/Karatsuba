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
| | Glass | Clear, Tinted (§4.1) | Apple only |
| | Appearance | Auto, Light, Dark (§4.14) | Apple only |
| | Legacy Settings… | a page of Legacy's settings (§5) | Legacy only |
| **Accessibility** | Reduce Transparency | on, off; follows the Apple UI's by default (§1.6) | all |
| | Increase Contrast | on, off (§2.11) | all |
| | Text Size | Default, Large, Larger (§2.9) | all |
| | Motion | Full, Reduced, Off (§2.10) | all |
| **Behaviour** | In Screens, Hide with F3, Fade When Idle After, Idle Opacity, Change Cues, Avoid Vanilla HUD | §2.8 | all |

A setting a preset does not use is hidden, not greyed.

1.5 **Preset follows the GUI unless the player says otherwise.** By default it is
**Match GUI**: the Apple UI active means the Apple preset, the Classic UI means
Legacy, switching with the GUI. Choosing Apple or Legacy by hand fixes it there,
and it stops following the GUI until the player chooses Match GUI again. The whole
HUD changes at once.

1.6 **Accessibility settings combine freely, in every preset:**
- **Reduce Transparency:** solid plates, and no glass passes run. It follows the
  Apple UI's own Reduce Transparency (Apple rules §7.4) until the player sets it
  here.
- **Increase Contrast:** a visible 1px border on every plate, and everything at
  7:1 (§2.11).
- **Text Size:** Default, Large or Larger (§2.9). In Legacy it scales the font
  chosen in Legacy Settings.
- **Motion:** Full, Reduced or Off (§2.10).

1.7 **Settings belong to a preset or to the HUD, never to an element's drawing.**
An element's own settings say what it shows (Ping's Quality Bars, Coordinates'
Precision). How it is drawn comes from the preset.
- **Breaks this today:** ArrayList's **Tint** (Theme Gradient, Label, Value) is a
  drawing setting on an element. It moves to Legacy Settings (§5); Apple has no
  equivalent.

1.8 **The settings pages.** Each GUI's HUD page shows, top to bottom: the
**Appearance** card, the **Accessibility** card, the **Behaviour** card, then the
elements. Each element's page holds its own settings, then a **Display** card
(Align, Fade When Idle, and Custom Style in Legacy only), then Layout. Legacy
Settings… is a page of its own, reached from Appearance.

## 2. What stays the same whatever the preset

2.1–2.9 carry over from the design rules in `PLAN.md`'s History; every preset
honours them all.

2.1 **Core owns geometry.** No element or preset computes a screen position.
Anchors, offsets, scale, clamping, z-order, hit-testing and saving stay in
`HudService`. Element ids never change: they are the config keys. (A proposed
preset screen-edge inset is in §9.)

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
grow leftwards; centre-anchored ones centre. Per element, Align overrides it. (A
proposed exception for Apple is in §9.)

2.7 **Nothing to show, nothing drawn**, and a sample in the editor so there is
always something to grab (`isEditing()`).

2.8 **Behaviour is the HUD's, not the preset's.** Dim or hide in screens, hide
with F3, fade when idle (after Fade When Idle After, down to Idle Opacity),
change cues, and avoiding the vanilla HUD happen the same in every preset, from
the Behaviour card. A preset only chooses how they are drawn (Apple's in §4).

2.9 **Readable size.** Text Size **Default** already meets the minimum: body text
at least 18px tall at 1080p at the default scale, and still whole at 200%. Large
and Larger only go up from there. Text Size applies in every preset.

2.10 **Motion follows the Motion setting:**
- **Full:** the preset's own motion.
- **Reduced:** fades only; no springs, no scaling, no movement.
- **Off:** no animation at all; every change is instant.

It replaces the old Animate switch and a separate Reduce Motion switch.

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

## 3. Roles: what elements describe

3.1 Every element extends `StyledElement` and writes `describe(body)` with its
helpers (`label`, `text`, `fixed`, `stat`, `bars`, `watch`). Each helper produces
a role. New kinds of part become new roles and helpers there, so every preset can
draw them.

3.2 **The roles:**

| Kind | Roles |
|---|---|
| **Text** | primary, secondary, tertiary (inactive things only), value (fixed-width), title, subtitle |
| **Colour** | accent, meterFill, meterTrack, good, fair, bad, separator |
| **Structure** | icon, iconWell, toggleOn, track (a thick rounded progress bar), titleBlock |

The `label` helper produces a label. "Label in the accent" is **Legacy's mapping**
of that role, not the role itself: Apple draws labels as secondary text (§4.8).

3.3 **Every preset draws every role,** even if only simply. A preset with no drawing
of its own for a structural role simplifies it: Legacy draws an iconWell as the
bare icon, or leaves it out. **An element never needs a preset-specific version.**

3.4 **No literal colours in elements.** Health's good, fair and bad
(`element/Health`) and any other colour an element uses become colour roles,
mapped by each preset, before the second preset ships.

3.5 An element that draws something no helper covers (Now Playing's cover, a
radar) asks the preset for its roles inside its custom part, so it still changes
with the preset.

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
- Sizes are scaled by Text Size (§1.6).
- SF Pro's licence must be checked before it ships in a public client (§9).

4.9 **Three kinds of shape:**
- **Fixed radius** (`R_HUD_TILE`) for tiles with several rows.
- **Capsule** (radius half the height) for single-row standout elements: the
  Watermark, Connection, notification toasts.
- **Concentric** (the parent's radius less the padding) for anything nested, with
  a fallback radius when it stands alone.

Small controls inside a plate are rounded rectangles, not capsules, as on macOS.

4.10 **No glass on glass.** Parts inside a plate use vibrant fills, transparency
and separators. The material goes on the plate, never on its inner parts.

4.11 **Icons** only where they are clear at once; otherwise a text label. One icon
per concept, never tweaked variants of it. An original glyph set, not SF Symbols
assets.

4.12 **Motion** (at Motion: Full; §2.10 for Reduced and Off): a soft, gel-like
spring.
- Elements arrive from their source: a notification springs in from its anchor
  edge, and a new row expands from where it sits.
- The change cue (§2.8) is a brief brightening of the rim.
- Values never animate (§2.5).

4.13 **Focus:** when a game screen opens, HUD glass recedes and turns more opaque.
This is how the Apple preset draws §2.8's "dim in screens".

4.14 **Appearance** (Auto, Light, Dark). **Auto:** single-row capsules switch
light or dark by themselves from what is behind them, with hysteresis so they
never flicker; tiles with several rows follow the Apple UI's appearance setting
(Apple rules §7.1). Light and Dark fix every plate.

4.15 **Editor chrome** (§7; Apple plan step 15): elements at full quality, an
accent glow on the selected element, and a circular minus badge to hide one.

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
- ArrayList's Tint (§1.7).

Its settings live on the **Legacy Settings…** page (Plate, Text, ArrayList, and a
Reset), shown from Appearance only while Legacy is the preset, and in each
element's Custom Style (§9). Whether its Font menu keeps the italic-only fonts
depends on the no-italics proposal (§9).

## 5b. Accent themes

5b.1 **The accent is used only where the active preset's emphasis rules allow it**
(Apple's in §4.7; Legacy's labels in §5).

5b.2 **Every theme gets checked variants, adjusted automatically until they pass**,
never rejected:
- the accent on glass: 3:1 at the glass's brightest;
- the accent on the white toggleOn well: 3:1;
- the accent on a Legacy plate: 3:1.

5b.3 **The accent is never the only signal of state** (§2.3), so near-white and
low-saturation accents still work.

5b.4 Whether a theme is one colour, a pair or a palette is open (§9).

## 6. Grouping

6.1 **The docking stacks were deleted** on 2026-10-17 (`HudStacks`, Shift-drop
in the editors, the HUD pages' Stack section, `hud-stacks.json`). An old
`hud-stacks.json` in a profile is now ignored.

6.2 What replaces them is open; a proposed rule for how groups are drawn is in §9.
Whatever it is works the same in every preset, and leaves Core's layout model
alone unless this file says otherwise.

## 7. Editing

7.1 Each GUI keeps its own HUD page and Layout editor. The Apple UI gets its own
editor (Apple plan step 15: the Edit HUD pill, glass tiles, snap guides and undo),
with the Apple preset's editor chrome (§4.15). The Classic editor stays as it is.

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

**Open:**
- **Text Size:** whether there is a fourth, smaller step for a compact HUD, given
  that Default is already the §2.9 minimum.
- **Grouping mechanism:** what replaces stacks: snapping elements into shared
  plates, layout groups that move together, a few fixed panels, or nothing?
- **Other presets:** is a third wanted now, or just room for one?
- **SF Pro's licence** must be checked before it ships in a public client.
- **Legacy's italic fonts:** depends on the no-italics proposal below.

**Proposed:**
- **Per element:** the preset is HUD-wide. There is no per-element preset.
- **Apple sizing:** the Apple preset uses its own padding and type sizes, not
  Legacy's, scaled by Text Size.
- **Debug:** stays a plain dev readout, exempt from presets.
- **Screen-edge inset:** a preset may define a distance from the screen's edges
  that Core's clamping respects. This touches §2.1 (Core owns geometry), so it
  needs a Core change and is not yet a rule.
- **No italics in any preset.** No element draws italics today, but Legacy's Font
  list offers italic-only bundled families (Inter Italic, Roboto Italic), so a
  Legacy player can choose one. Settling this means Legacy Settings' Font menu
  stops offering them, which §5's "kept as it is" would have to allow.
- **Apple alignment** (changes §2.6 for Apple only): text inside a plate is
  left-aligned whatever the anchor, as Control Center's tiles are. The anchor still
  decides which way the plate grows. If settled, Align is hidden in Apple except on
  ArrayList, which may keep anchor alignment.
- **Grouping drawing:** whatever replaces stacks draws one plate per group. In
  Apple, the items inside are divided by separators or flat translucent fills,
  never nested glass. The mechanism stays open (above).
- **Theme shape:** themes today are a pair (a primary and a secondary colour, with
  a gradient between them). If they stay a pair, Apple uses the first colour only,
  as the Apple UI already does (Apple rules §4.3).
