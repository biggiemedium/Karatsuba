# DESIGN

Rules for every piece of UI in Karatsuba: the client GUI, its sections, setting
rows, popups, the HUD editor and HUD elements. **Obey them at all times.** If a
rule gets in the way, change this file first, on purpose, then the code.

Where things are:

| What | Where |
|---|---|
| Widgets, containers, layout, input, popups, tooltips (headless, no look) | `gui/` (`dev.px.gui`), vendored Core library; see `gui/README.md` |
| Box model, `Content`, `Render`, fonts, themes | `core/` (`dev.px.core.layout`, `.render`) |
| The client GUI | `src/client/java/dev/px/karatsuba/client/gui/shell/` |
| Design tokens | `shell/style/Palette` (colour, size, font), `shell/style/Animations` (timing), `Motion`, `Easing` |
| All drawing | `shell/look/*Look` (one file per area), `Shapes`, `Glyphs` |
| Hosting in game | `client/screen/ClientGuiOverlay`, `Overlays` |
| HUD style | `client/hud/style/` (`HudStyle`, `Style`, `StyledElement`) |
| Game overlays (the loading screen, the menu panorama) | `client/gui/overlay/`, tokens in `OverlayStyle`; laid out against the window's height, not the GUI scale |

---

## 1. Architecture

1.1 **Widgets hold state and draw nothing.** A widget never calls `Render`,
never picks a colour and never listens to the event bus. Its look is a
`WidgetRenderer` registered in a `look/*Look` file and gathered in `ModuleLook.build`.

1.2 **All drawing lives in `look/`.** The only exceptions are full-screen
sections that draw over the game (`section/layout/EditorLook`). Don't add more.

1.3 **What is drawn and what is clicked are the same rectangle.** Interactive
parts are named with `c.custom("part", ...)` and hit-tested with `widget.part("part")`.
Never recompute a hit area by hand.

1.4 **`update` measures, `draw` paints.** Nothing reads geometry from a draw
call. Input reads the last update's layout.

1.5 **New UI goes on the shell, never on the legacy click GUI**
(`dev.px.gui.legacy`) or `client/gui/component`. Profiles and Accounts are still
legacy and are due to move.

1.6 **Extend, don't fork.**
- A new section implements `Section` and gets one line in `Sections`. It never
  gets a placeholder before it exists, so the rail never leads nowhere.
- A new setting type gets a row in `shell/setting`, a renderer in `look/SettingLook`
  and a case in `SettingList.rowFor`.
- A new shared widget goes in `shell/widget` and is used everywhere it fits.
  A section doesn't get its own version of something `widget/` already has.

1.7 **Leaves are leaves.** A button's icon and label are parts its renderer
describes, not child widgets. Only containers hold children.

1.8 **The game never draws inside a NanoVG frame.** Item icons and any other
vanilla draw go through `GuiFrame.defer`, or `NanoVGRender2D.suspend()/resume()`.
Breaking this corrupts GL state; items appear thrown in front of the player.

1.9 **The gui library stays headless and Minecraft-free.** Anything
client-specific belongs in the client. A change to `gui/` or `core/` is a local
edit to the vendored copy: record it in the core-local-edits notes.

## 2. Tokens

2.1 **No literal colours, sizes, radii, fonts or durations outside `Palette` and
`Animations`.** If no token fits, add one with a comment saying what it is for.
Name it after its role (`TEXT_MUTED`, `RADIUS_CONTROL`), not its value.

2.2 **Fonts come from `Palette.body()`, `strong()`, `title()` and `small()` only.**
Four steps, no others.

2.3 **Spacing is on a 4-point grid** (2 is allowed for hairline gaps): 4, 8, 12,
16. More room between groups (`GAP_SECTION`) than inside them (`GAP_ROW`).
Content sits `INSET` from its container's edge.

2.4 **Two radii:** `RADIUS_PANEL` for windows, cards and popups, and
`RADIUS_CONTROL` for rows, fields and buttons. Fully round (`h / 2`) only for
switches, pills and dots. A rounded child inside a rounded parent uses a smaller
radius, or `rounded()` when both are fully round.

2.5 **Every control row is `ROW_HEIGHT` tall** and every section's title row is
`TITLE_HEIGHT` tall, so labels line up and switching sections never moves the
title.

## 3. Colour

3.1 **Dark, quiet surfaces** (Material dark). Elevation is shown by lighter
surfaces: `BASE`, then `SIDEBAR`, then `RAISED`/`RAISED_HIGH`, then `POPUP`. Use
shadows only for windows and popups that float.

3.2 **Text is white at three opacities:** `TEXT` (87%) is what you read,
`TEXT_MUTED` (60%) is labels and descriptions, and `TEXT_FAINT` (38%) is
disabled, placeholder and caption text. Never pure white body text.

3.3 **The accent is for state only:** on, selected, focused, filled, primary.
Never use it for decoration, headings or borders that mean nothing. Always get
it through `Palette.accent()`, `accent(t)` or `accentTint(a)` so it is lifted to
3:1 against the panel and crossfades when the theme changes. Text or knobs on
the accent use `Palette.onAccent(accent)`.

3.4 **One accent per element.** The only other colours are `WARN` (amber) for
warnings and `ONLINE` for presence, and both need a word beside them.

3.5 **Never colour alone.** Every colour that means something also has a word,
an icon, a shape or a position (a dot plus "Online", a bar plus the number).

3.6 **Hover is a faint tint that fades in** (`HOVER`, through `Motion.hover()`),
never the accent.

3.7 **Render modules offer `Theme Colours`.** A module with colours of its own
has one `Theme Colours` switch and draws through `client.render.ThemeColors`. It
hides its own pickers while that is on.

## 4. Layout and structure

4.1 **Every section has the same shape:** a title row (`SectionTitle`), then a
list, then an inspector for the selected item. Deviate only when the content
truly isn't a list, as Home and Layout are.

4.2 **The rail shows the chosen item twice:** an accent pill and brighter text.
Its labels drop to icons only below a window width of 520.

4.3 **Respond to the window's size, don't assume it.** The window is resizable
down to `FRAME_MIN_WIDTH × FRAME_MIN_HEIGHT`. Every view must work at that size.
Drop secondary words before primary ones (see `InspectorHeader`).

4.4 **A widget's natural width is measured at width 0.** A paragraph reports its
full unwrapped width there and can widen a column. Wrap long text against last
frame's bounds (`Shapes.wrap`), as `FieldMessage` does.

4.5 **Long lists scroll in a `Scroll`/`SmoothScroll`, and are virtualised when
they can be big** (items, players). Keep a list's order still while the user is
reading it; only re-sort when the query changes.

## 5. Motion

5.1 **Timings come from `Animations`:** about 120ms for feedback (hover, switch),
180–250ms for arrivals (popup, view, fold, open), and leaving is quicker than
arriving. No overshoot, no bounce.

5.2 **Every animation goes through `Motion` (or `Reveal`/`Fold`)** so it can be
interrupted from wherever it is and never jumps.

5.3 **Animations off means instant.** Every motion honours
`Animations.enabled()`. Only the theme colour crossfade still runs, because a
colour change isn't motion.

5.4 **Switching sections is a fade with no slide.** Sections have no order to
slide along. Clicking the active item does nothing.

5.5 **How to fade, since `Widget.draw()` can't fade a subtree:**
- New content fades in under a `Veil` (empty hit shape).
- Slide it through `arrange` in a `RisingColumn`.
- Animate a height in `childrenHeight` (`Fold`, `Unroll`).
- Fade a whole screen with `pushAlpha` around `Screen.draw`.
- Fade a leaf inside its own custom draw.

5.6 **Values that change every tick use fixed-width digits**, so sizes don't
animate constantly.

## 6. Interaction

6.1 **Everything works from the keyboard.**
- `Ctrl+1..9` jumps to a section, `Ctrl+Tab` cycles, `Ctrl+E` opens the editor,
  `Ctrl+F` focuses search and `Ctrl+,` opens Settings.
- `Esc` steps back one level at a time: drag, popup, focus, selection, editor,
  GUI.
- `Ctrl+Z`/`Ctrl+Y` undo where there is an undo.

New keys must not clash with these.

6.2 **Interactive things are focusable and act on `activated()`** as well as a
click.

6.3 **One primary button per view at most** (`PillButton.Style.PRIMARY`). Most
buttons are `PLAIN`. A button that toggles is `TOGGLE`.

6.4 **Prefer undo to confirmation.** Reversible removals happen at once with an
`UndoBar` and `Ctrl+Z`. Destructive resets that can't be undone ask for a
second click within 3 seconds, and the label changes to say so ("Reset all" to
"Confirm"). No modal "Are you sure?" dialogs.

6.5 **Search is one box per section,** focused by `Ctrl+F` or by typing with
nothing focused. In a search box, Enter takes the top match and Esc clears, then
blurs.

6.6 **Icon-only buttons have their label as a tooltip.** Tooltips use the
screen's delay (450ms) and max width (190).

6.7 **Show validation on submit (Enter), not on every keystroke.** Messages say
what to do, plainly (GOV.UK style).

6.8 **Show what changed.** A setting that isn't at its default shows an accent
bar and a reset icon. Pages show a count of changed settings.

## 7. Copy

7.1 **Setting labels are Title Case** (they are config keys). Everything else
(descriptions, tooltips, messages, captions) is sentence case.

7.2 **Descriptions say what it does, in one plain sentence, with no trailing
full stop.** Explain an option's effect, including what "off" does, not its
implementation.

7.3 **Section names are one short word.**

7.4 **No filler.** No lorem, no placeholder sections, no decorative numbers.
Every element earns its space by showing something the player would otherwise
have to work out.

## 8. HUD

8.1 **Every element extends `StyledElement`, has a plate, and inherits the
global HUD Style** unless it has Custom Style on. Read style every frame and
never cache it.

8.2 **Labels in the accent, values in white** ("Ping" accent, "42ms" white).

8.3 **Alignment follows the anchor:** right-anchored elements right-align and
grow leftwards, and centre-anchored elements centre.

8.4 **Elements never compute their own coordinates.** `HudService` places them.
Elements keep out of vanilla HUD zones through `VanillaAvoidance`.

8.5 **Legible over any world:** 4.5:1 against the brightest background the plate
can sit on (plate opacity min 30%), and readable at 1080p.

## 9. Verifying UI work

9.1 **Look at it before calling it done.** Render it through the screenshot
harness, at GUI scale 2 in a 1400×800 window, at the default size and at the
minimum window size. Check light themes (White) and dark themes (Midnight).

9.2 **After touching boot registration** (sections, binds, overlays, config ids),
also run `gradlew runClient` and confirm the client starts.

9.3 **Say what wasn't checked.** If something couldn't be seen (in-game only,
item icons, cues in a world), say so rather than implying it works.

---

## Known exceptions to clean up

These break the rules today. Don't copy them; fix them when you're nearby.

- Literal colours outside `Palette`:
  - shadows: `FrameLook`, `LayoutLook`, `Shapes`, `ThemeLook`
  - `HomeLook` BAD red
  - `PopupLook` picker outlines
  - `Shapes` checker greys
  - `EditorLook` guides
- Literal durations: `ClientGui` fullscreen `Motion(250f, 200f, ...)`, `AnchorRow`
  `Motion(160f, ...)`.
- Profiles and Accounts screens are still on the legacy GUI.
