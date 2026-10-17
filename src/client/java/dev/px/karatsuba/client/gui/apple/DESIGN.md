# DESIGN — Apple UI

Rules for the Apple UI: the client reskinned in the style of macOS System
Settings with Liquid Glass. **Obey them at all times** for any work on this UI.
If a rule gets in the way, change this file first, on purpose, then the code.

This UI is entirely separate from the Classic UI (`client/gui/stable`, governed by
the root `DESIGN.md`). They share no Palette, Animations, look or settings page.
Neither one ever edits the other's files.

| What | Where |
|---|---|
| These rules | `client/gui/apple/DESIGN.md` |
| The code | `client/gui/apple` (this folder) |
| Tokens | `apple/shell/style/Palette`, `Animations` (the Apple UI's own) |
| All drawing | `apple/shell/look/*` |
| Glass and material passes | `effect/glass.fsh` and friends, drawn through `client/render/Glass` |
| Its HUD editor | its own overlay, separate from the Classic HUD editor |
| Its look settings | the **Appearance** page in its own sidebar, own config section |

---

## 1. Architecture

These rules carry over unchanged from the root `DESIGN.md` §1:

1.1 **Widgets hold state and draw nothing.** All drawing lives in `look/`.

1.2 **What is drawn and what is clicked are the same rectangle** (named parts).

1.3 **The game never draws inside a NanoVG frame.**

1.4 **Shader passes are never drawn inside a clip, transform or alpha push.**
`ShaderPasses.rect` refuses there. So no widget renderer draws glass. A widget
marks where its glass goes with a named part (`"glass"`), and the host draws the
pass at that part's screen rectangle, applying any scale itself.

1.5 **Layers are separate Screens, drawn in order.** One `Screen.draw` can't be
interleaved with shader passes, so each surface that needs a pass gets its own
Screen. The host draws, bottom to top:

1. **The backdrop**: the game, dimmed (`BACKDROP_DIM`, about 35% black) and
   blurred, like a macOS sheet's background (§5.1).
2. **The window**: a solid surface, no pass.
3. **Content Screen**: the cards, rows and controls, in the content column.
4. **Glass passes**: the sidebar and the floating controls (‹ ›, later the Edit
   HUD pill), frosting the window under them: one capture, taken over the window
   and its content.
5. **Control Screen**: the sidebar and the header.
6. **Popover Screen**: menus and popovers, solid and elevated.

1.6 **Two UIs, switched by a key.** The Classic UI and the Apple UI are separate
overlays, each with its own HUD editor overlay.
- The **"Switch Interface"** bind (unbound by default) flips which UI is active.
- **Click GUI**, **HUD Editor** and the other open binds always open the active
  UI.
- The choice is saved.

1.7 **Look settings live in the Apple UI, not in Client Settings.** The Appearance
page has its own config section (`apple-appearance`) and is reached from its own
sidebar. Client Settings never shows them.

## 2. Layout: System Settings

2.1 **Window = sidebar + content.**
- The sidebar is a glass pane floating inside the window, inset `INSET_WINDOW`
  from its top, left and bottom edges.
- The content fills the rest, in a column capped at `CONTENT_MAX_WIDTH` and
  centred in any room beyond it (`ContentColumn`), inset `INSET_CONTENT` from
  the window. Search fields never stretch past it.
- Behind the window the game is dimmed and blurred while the GUI is open. It is
  never legible through any content surface.

2.2 **The sidebar, top to bottom:**
- A search field.
- The module categories as rows (Combat, Movement, Building, Mining, Inventory,
  Render, Utility, Automation).
- A gap, then HUD and Friends.
- A gap, then Appearance and Settings.

Each row is a coloured icon tile (`ICON_SIZE`, white filled glyph, §4.7) plus a
label. No divider lines: groups are separated by space alone. The selected row
is a capsule in the control accent (§4.4) with white text in the regular weight;
never dark text here. Hover is a neutral tint, never the accent.

2.3 **The header** is the window's top area, not a bar: the page title as plain
text (semibold, one line), aligned with the content column's left edge, with ‹ ›
in a small glass capsule to its left (page history; plain chevrons, dimmed when
there is nowhere to go). Content starts below it.

2.4 **Content is inset grouped lists.**
- Solid, borderless rounded cards (`R_CARD` = window radius − pane padding): one
  step lighter than the window in Dark, white in Light.
- A section header above each card, outside it and flush with its left edge:
  semibold, sentence case. It may end with a trailing action ("Show All (36)"):
  secondary text, regular weight, no chevron or background, brightening and
  underlined on hover. A folding group's header is "Show All (n)" / "Show Less".
- Cards fill the content column and are `GAP_CARD` apart.
- Every settings row sits in a card, in every section; rows are separated by
  hairlines that start at the label's left edge, never at the card's edge. There
  is no hairline after the last row.

2.5 **Every row: label left, control right.** The control is one of:
- a toggle;
- a value and a stepper (a number with few values, 21 at most);
- a value and a slider (a number with many values, or a range with two knobs);
- a segmented control;
- a row of options (circles, swatches);
- a value and a ›.

A row that opens a page ends with ›, and the whole row is the target. Every row
is `CELL_HEIGHT` whatever its control, or more when it has a secondary line, with
`CELL_INSET` padding on both sides and its control vertically centred.

**One look per control, everywhere** (rows, the module list, the detail header):
- **Switch:** a capsule about twice as wide as tall, neutral off and the control
  accent on, a white knob inset 2 with a soft shadow, 120ms ease-out. No
  "Enabled" word.
- **Segmented:** a neutral capsule shorter than a row, equal segments, a raised
  lighter pill sliding 180ms ease-out; unselected labels secondary.
- **Slider:** thin track, accent fill, a white elongated knob with a shadow.
- **Pop-up:** the value and small up/down chevrons, no box.
- **Choosing one of several cards** (the Themes grid): click the card; an accent
  ring and a tick mark the one chosen. No switches on cards.
- **A list whose row opens a detail pane** (the module list): the switch alone
  shows on or off; the open row gets a neutral highlight (`SELECTED`), never the
  accent, and no colour bar.

2.5b **Push buttons** (keybinds included): a bright filled face (white in Light, a
lighter grey in Dark), a 1px subtle edge, a soft shadow, `R_BUTTON`, shorter than
a row and never clipped. A button that opens something ends in "…". Standalone
actions sit below their card, right-aligned (`CardActions`). A keybind shows its
key or "None", and "Press a key…" with an accent edge while it listens.

2.5c **Scroll bars are overlays**: hidden at rest, shown while the list moves.

2.6 **Modules are pages, as in System Settings** (no third column). A category is
a page with one card of its modules; each row is the name, its switch and a ›.
The switch toggles; anywhere else on the row opens the module's page: a first
card with its switch and description, then its settings in grouped cards, its
bind and ArrayList switch in a "Module" card last. While the sidebar search holds
anything, the list page shows every match from every category, its category
beside it, or an empty state.

**Modules Layout** (Appearance, 7.6) can instead show the list and the open
module side by side (Split): the list `SPLIT_LIST_WIDTH` wide, the module filling
the rest, the first listed module open until another is chosen, the open row a
neutral highlight and no ›. Nothing slides in Split. Pages is the default and the
System Settings way.

2.6b **Friends is pages too**, like Modules.
- **The list page:** a card of friends online, then a card of those offline. Each
  row has the player's monogram (`AVATAR`), the name the list shows (with their
  real name beside it when they have an alias), where they are, and a ›. The whole
  row opens their page. "Add Player…" sits below the last card. With no friends,
  an empty state takes the page ("No friends yet" / "Add Player…").
- **A friend's page:** a first card with a large monogram, their name, and where
  they are. Then Profile (Alias, Note, Username with Copy) and Activity (Now, Last
  seen, Added). "Remove Friend" sits below the last card.
- **Remove asks nothing.** It pops back to the list, which shows "Removed Steve ·
  Undo" at its top for a few seconds; Ctrl+Z also undoes it (9.4).
- **The Add Player page:** a sentence saying names are checked here and never
  looked up, a card with the Username field, the problem under it on Enter (never
  per keystroke), and "Add Friend" below. Under that is a card of players on this
  server who are not friends, each with an Add button, folding with "Show All
  (n)". Adding opens the new friend's page.
- **The sidebar search searches friends**, by name, alias or note. It also lists
  matching players on this server, and offers a name that could be a player but
  is not a friend yet ("Add"). Enter opens the friend it names, or adds that name.

2.7 **The window must work at its minimum size.** Below `SIDEBAR_COLLAPSE_WIDTH`
the sidebar shows icons only, with labels as tooltips. Secondary text stays
readable there.

2.8 **Empty states** (`EmptyState`): centred in the content, an icon, a short
title ("No friends yet"), one sentence of explanation, and one primary button
that does the obvious next thing ("Add Player…"). The primary button is the
control accent with white text.

## 3. Shape: concentric corners

3.1 **Inner radius = outer radius − the padding between them.** Every nested
rounded shape follows this. The radii are tokens **derived in code from this
rule**, never written as literals:

```java
static final float R_WINDOW      = 26f;
static final float R_SIDEBAR     = R_WINDOW  - INSET_WINDOW;    // glass pane in the window: 18
static final float R_HEADER      = R_WINDOW  - INSET_WINDOW;    // header glass, the window's top right: 18
static final float R_SIDEBAR_ROW = R_SIDEBAR - INSET_SIDEBAR;   // 14; the selection is drawn as a capsule within it
static final float R_CARD        = R_WINDOW  - INSET_CONTENT;   // cards, by the pane's padding: 10
static final float R_SEGMENTED   = SEGMENTED_HEIGHT / 2f;       // a capsule
static final float R_SEGMENT     = R_SEGMENTED - INSET_SEGMENT; // its pill, inset 1
static final float R_BUTTON      = 6f;                          // push buttons, pop-ups, fields
static final float R_POPOVER     = 12f;
static final float R_MENU_ITEM   = R_POPOVER - INSET_POPOVER;
static final float R_HUD_TILE    = 14f;                         // large, for edit mode
static final float R_HUD_INNER   = R_HUD_TILE - PAD_HUD_TILE;
```

3.2 **A derived radius never goes below `R_MIN` (2).** If the subtraction goes
under it, the padding is too big for the parent. Fix the padding, don't clamp
quietly.

3.3 **Fully round (`h / 2`)** is only for pills: switches, steppers, segmented
controls and their pill, the sidebar selection, the Edit HUD pill and the search
field. A pill inside a pill uses `h / 2` of its own height,
inset by its padding.

3.4 **The first and last row's highlight follows the card's corners**
(`R_CARD`). Middle rows' highlights are square.

3.5 **Spacing is on a 4-point grid.** There's more room between cards than inside
them.

## 4. Colour

4.1 **Two appearances, Light and Dark,** chosen on the Appearance page. Every
colour token has both values (the sidebar icon colours, 4.7, are the one set
that keeps the same value in both). No code picks a colour by appearance itself;
it asks the Palette.

4.2 **Neutral greys for everything** except the accent, the sidebar icons and the
presence dot (`PRESENCE`, green, on a monogram while that player is online, and
never without the words saying where they are). Monograms (`AVATAR`) are neutral.
The neutral set, per appearance (Dark Mode follows the HIG: elevated surfaces get
lighter, never translucent):

| Token | Dark | Light |
|---|---|---|
| `BACKDROP_DIM` | #000000 @ 35% over the blurred game | same |
| `WINDOW` | #1E1E20, solid | #ECECEC, solid |
| `CARD` | #2A2A2D, solid | #FFFFFF, solid |
| `POPOVER` | #323236, solid, soft shadow | #FFFFFF, solid, soft shadow |
| `SEPARATOR` | #FFFFFF @ 9% | #000000 @ 8% |
| `HOVER_TINT` | #FFFFFF @ 6% | #000000 @ 5% |
| `SELECTED` | #FFFFFF @ 10% | #000000 @ 7% |
| `LABEL` | #FFFFFF @ 88% | #000000 @ 88% |
| `LABEL_SECONDARY` | #FFFFFF @ 64% | #000000 @ 60% |
| `LABEL_TERTIARY` | #FFFFFF @ 28% | #000000 @ 26% |
| `CONTROL_OFF` | #FFFFFF @ 18% | #000000 @ 11% |
| `CONTROL_THUMB` | #707075 | #FFFFFF |
| `BUTTON` / edge | #545458, white @ 9% | #FFFFFF, black @ 12% |

These are starting values, to be tuned on screen. Only the Palette holds them.
Secondary text never drops below 60% emphasis in Dark; tertiary is only for what
is disabled or a placeholder.

4.3 **Accent = the active theme's primary colour, lifted to 3:1** against `CARD`
in the current appearance. That means lightened in Dark and darkened in Light.
The theme's secondary colour is never used as a UI colour. The one exception is
the theme picker (4.6), which shows each theme as it is.

4.4 **The accent is for state only:**
- the selected sidebar row and a switch that is on use the **control accent**
  (`Palette.accentControl()`): the primary darkened and saturated until white (the
  knob, the text) reaches 3:1 on it, so pale themes still show plainly;
- a slider's fill and the focus ring;
- the default button and an empty state's button.

A segmented control's selected segment is a neutral raised thumb, not the
accent. The row open in a detail pane is a neutral highlight, not the accent.

4.5 **No gradients** anywhere in the UI, apart from the theme picker's split
circles and the sidebar icon tiles' faint top-to-bottom shade (4.7).

4.6 **The theme picker** (Appearance) is a row of circles, like macOS Appearance
→ Color. Each circle is split diagonally, primary top-left and secondary
bottom-right. The selected one has a ring, in `LABEL`, offset 2px from the circle.
Choosing a theme sets Core's active theme, which the HUD and render modules
follow too.

4.7 **Sidebar icons are macOS Tahoe tiles.**
- **Colours:** Apple's Light-mode system colours, the same in both appearances
  (never the brighter Dark variants), one `ICON_*` token each, assigned in one
  table (`Palette.iconColor`). No two categories share a hue.

  | Entry | Token | Colour |
  |---|---|---|
  | Combat | `ICON_RED` | #FF3B30 |
  | Movement | `ICON_BLUE` | #007AFF |
  | Building | `ICON_ORANGE` | #FF9500 |
  | Mining | `ICON_BROWN` | #A2845E |
  | Inventory | `ICON_TEAL` | #30B0C7 |
  | Render | `ICON_PURPLE` | #AF52DE |
  | Utility | `ICON_GRAY` | #8E8E93 |
  | Automation | `ICON_INDIGO` | #5856D6 |
  | HUD | `ICON_PINK` | #FF2D55 |
  | Friends | `ICON_GREEN` | #34C759 |
  | Appearance | `ICON_GRAPHITE` | #1C1C1E |
  | Settings | `ICON_GRAY` | #8E8E93 |

- **Tile:** `ICON_SIZE` square, corners `R_ICON` (22.5% of the side); a
  vertical shade from the colour lightened `ICON_SHADE` (8%) at the top to
  darkened 8% at the bottom; a 1px inner highlight along the top edge
  (`ICON_HIGHLIGHT`, white at 25%); in Dark, a 1px outline (`ICON_EDGE`, white
  at 10%) so dark tiles stay visible.
- **Glyph:** solid opaque white, filled shapes, one stroke weight
  (`ICON_STROKE`), centred, at `ICON_GLYPH_SIZE` (62% of the tile), matched in
  visual size across icons (`look/IconTile`). Never an outline glyph on a tile.
- These colours appear nowhere else.

4.8 **Text on glass uses vibrant colours** (`VIBRANT_LABEL`,
`VIBRANT_SECONDARY`), which have more contrast than the content colours, because
the backdrop behind glass is unpredictable. Any text on glass must reach 4.5:1
against the brightest backdrop the tint allows. Never colour alone (root rule
3.5 applies).

## 5. Materials

Following the HIG's Materials guidance: glass belongs to the navigation and
control layer that floats over content, never to the content itself.

5.1 **What is glass, and what is solid:**

| | Liquid Glass | Solid |
|---|---|---|
| Where | the sidebar (one inset panel, radius = window radius − inset); small floating controls: the ‹ › capsule, the Edit HUD pill; HUD elements over the game | the window, cards, rows and every control in them, popovers and menus |
| Looks like | blurred backdrop, rim lensing, specular rim, light tint, slight saturation | an opaque fill from the Palette; popovers add a soft shadow |

Behind the open GUI the game is dimmed (`BACKDROP_DIM`) and blurred, like a macOS
sheet's background. Nothing reads the game except that backdrop. The sidebar and
floating controls frost the window under them.

5.2 **Never glass on glass.** Nothing glass is drawn inside the sidebar except its
selection, and nothing glass sits on a card.

5.3 **Never glass in the content layer, and never on controls inside rows.**
Toggles, steppers and segmented controls are solid fills.

5.4 **No pass for the content layer.** The window and cards are plain fills.
Rows never sit on glass.

5.5 **Capture as late as you can and as rarely as you must.**
- The backdrop captures the game.
- The sidebar and floating controls share one capture taken over the window and
  its content, since none overlap; all HUD tiles will share one too.
- Budget: **at most 4 captures per frame** (2 today).

5.6 **Blur is the existing mip chain** (`ShaderPasses.sceneMipmapped()` with
`textureLod`). Dual-Kawase at ¼ size is the planned upgrade if quality or cost
calls for it. It replaces only the blur, not these rules.

## 6. The glass shader

6.1 **Shape:** a rounded-rect SDF matching the widget's part exactly, with its
own radius from §3.

6.2 **Lensing in a thin rim band only** (`GLASS_RIM`, about 10px): refraction
offset along the SDF gradient, strongest at the edge and zero inside the band.
The middle of the shape is not distorted.

6.3 **Specular rim highlight** from one fixed light direction (top-left), the same
for every shape, so all glass reads as lit by one light.

6.4 **Light tint and a slight saturation boost.** Optional chromatic aberration
at the rim, 1–2px at most, off by default.

6.5 **Glass appears and disappears by animating lensing strength, not opacity.**
Lensing goes from 0 to 1, and the blur and tint follow it. Its content (labels,
icons) fades on its own Screen.

6.6 **A press briefly raises rim brightness:** up over `Animations.PRESS`, back
over `Animations.PRESS_OUT`.

6.7 **One shader serves glass and plain frost.** A frosted shape with no rim is
the same pass with lensing, rim and specular at 0 (`Glass.Optics.material`).

## 7. Appearance page (the Apple UI's own settings)

7.1 **Appearance:** a Light / Dark segmented control.

7.2 **Color:** the theme picker circles (4.6).

7.3 **Glass:** a slider from **Clear** to **Tinted**, labelled at both ends. It
sets the tint's strength (and the blur with it) for every glass shape.

7.4 **Reduce Transparency:** replaces every glass shape with a solid fill in its
tint colour at full opacity, and the backdrop's blur with a deeper dim. No
passes run while it is on. Every
layout and colour rule still holds.

7.5 **Reduce Motion:** the Apple UI's own animations switch. On means every
motion is instant, glass included (lensing jumps straight to its end). Only
colour crossfades still run.

7.6 **Modules Layout:** Pages (default: a module opens over its list, §2.6) or
Split (the list and the module side by side). Switching back to Pages returns to
the list.

The page is four cards: Appearance (Light / Dark, and the theme circles, 4.6,
wrapping to the card), Glass (the slider labelled Clear and Tinted at its ends,
and Reduce Transparency), Motion, and Modules.

## 8. Motion

8.1 Timings come from the Apple UI's `Animations` only. About 120ms for feedback,
200–300ms for arrivals, and leaving is quicker than arriving. Spring-like
easing without overshoot past 2%.

8.2 Every animation goes through `Motion` and can be interrupted from wherever it
is.

8.3 **Pages push and pop, fast.** Opening a page (a module) pushes it in from
the right the full width while the page it leaves slides out to the left
(`PageStack`), in `Animations.PUSH` (240ms) on `Easing.PUSH`, a sharp ease-out
that covers most of the way in the first tenth of a second. Back (‹, Escape, the
history) pops the other way in `POP` (220ms). Pages are clipped to the content
column and stay pressable while they move. Within a section that pushes its own
pages the shell adds no shift of its own; across sections the content crossfades
with a small `PAGE_SHIFT`. A sidebar switch never slides, it only crossfades or
jumps. Clicking the selected sidebar row does nothing. Reduce Motion makes every
push and pop instant.

## 9. Interaction

9.1 The root `DESIGN.md` §6 keys hold, with these added:
- `⌘[` / `⌘]` (Ctrl+[ / Ctrl+]) go back and forward.
- `Esc` steps back: popover, search, focus, then the GUI.

9.2 Every row and control is focusable and works from `activated()`.

9.3 Search is the sidebar's field. It filters the sidebar and shows matching
modules and settings in the content pane. Enter opens the top result.

9.4 Prefer undo to confirmation (root rule 6.4).

## 10. HUD

10.1 **The Edit HUD pill** sits at the bottom centre of the window on glass, like
Control Center's Edit Controls. It opens the Apple UI's own HUD editor.

10.2 **In edit mode, HUD elements are glass tiles** with large concentric corners
(`R_HUD_TILE`). Each one is dragged by its tile, with snap guides to screen
edges, centres and other tiles.

10.3 **Glass follows the element's scale.** The host draws each tile's glass at
the element's scaled screen bounds, and multiplies the radius, rim and highlight
by the same scale. The pass is never drawn inside the scale transform (1.4).

10.4 HUD elements over the game are glass (5.1). Reduce Transparency gives them
solid plates.

## 11. Copy and font

11.1 **Font: SF Pro, as one token** (`Palette.FONT`, a `Typeface` naming
`sf-pro/sf-pro.ttf`, `sf-pro/sf-pro-semibold.otf` and `sf-pro/sf-pro-italic.ttf`
under `assets/karatsuba/font`), so it
can be swapped in one line. Nothing else names a font file.
- Two weights only: **regular** for body text and **semibold** for titles and
  section headers. No light or thin weights anywhere; the sidebar selection stays
  regular.
- Captions: one step smaller, regular.

11.2 Setting labels stay in Title Case (they are config keys). Everything else is
sentence case: page titles, section headers, descriptions.

11.3 Section headers are short nouns ("Behaviour", "Targets"), and descriptions
are one plain sentence under the row's label.

## 12. Verifying

12.1 Screenshot every change in both appearances, with Reduce Transparency both
on and off. Use a bright world and a dark one, at the default and minimum window
size.

12.2 Check the capture budget (5.5) by counting `ShaderPasses.rect` calls per
frame in a debug build.

12.3 Say what wasn't seen.
