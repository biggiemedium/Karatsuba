# Apple UI plan

The order for building the Apple UI (rules: `DESIGN.md` beside this file) in
`client/gui/apple`. **One step at a time.** Each step builds and is checked
before the next starts, and the UI still opens at the end of every step. Tick a
step off here when it is done and verified.

**The approach:** keep the logic and replace the shell.
- **Kept:** setting rows' state, `SettingList`'s mapping, search, keys, `Motion`
  and `Easing`, and section logic.
- **Rewritten:** frame, layering, sidebar, header, cards, `look/`, `Palette` and
  `Animations`.

Old pieces stay working until the step that replaces them, then are deleted in
that same step.

---

## Foundation

- [x] **1. Switching.** *(2026-10-16, checked in game)*
  - An "active interface" choice (Classic / Apple), saved.
  - An unbound "Switch Interface" bind.
  - Click GUI, HUD Editor, HUD Elements and Client Settings open the active UI.
  - The "Experimental GUI" bind goes.
  - *Done when:* in game, the key flips between the two and each opens on its own
    binds.

- [x] **2. Appearance settings, no UI yet.** *(2026-10-16, checked in game)*
  - A config section `apple-appearance` with Appearance (Light/Dark), Glass
    (Clear–Tinted), Reduce Transparency and Reduce Motion.
  - Kept out of Client Settings.
  - The Apple `Animations` reads Reduce Motion.
  - *Done when:* the values persist across a restart.

- [x] **3. Tokens.** *(2026-10-16: builds; radius check passes; SF Pro regular, semibold and italic wired through the `FONT` typeface)*
  - New `Palette`:
    - neutral sets for both appearances;
    - vibrant text;
    - the accent lifted per appearance, darkened in Light;
    - concentric radii derived in code (§3);
    - `FONT` = SF Pro;
    - the sidebar icon colour table.
  - New timings in `Animations` (PRESS, PAGE_SHIFT...).
  - The old token names stay as aliases so the current look still draws.
  - *Done when:* it builds, the UI looks the same, and a unit check proves every
    derived radius is ≥ `R_MIN`.

## Glass

- [x] **4. Glass shader v2.** *(2026-10-16, checked in game)* Rewrite `effect/glass.fsh` to §6:
  - thin rim band lensing;
  - fixed top-left specular;
  - light tint and saturation;
  - optional CA;
  - `uLensing` (0–1) and `uPress` uniforms;
  - material mode (lensing, rim and specular at 0).

  `Glass` gains capture-once/draw-many (`Glass.capture()` → texture, then draw
  each shape with it) and a Reduce Transparency solid fallback. Nothing else
  changes.
  - *Done when:* in-game screenshots of glass and material over a bright and a
    dark world look right, and Reduce Transparency draws solid.

## Shell

- [x] **5. Layered shell skeleton.** *(2026-10-16, checked in game)*
  - The new `ClientGui`: window frame, then content material pass, content
    Screen, control glass passes, control Screen, popover glass, popover Screen
    (§1.5).
  - Glass is placed from `"glass"` parts.
  - No scrim.
  - Open and close animate lensing, not opacity.
  - Today's sections are hosted unchanged in the content pane.
  - The old rail goes.
  - *Done when:* it opens and closes, the window moves and resizes, every old
    section still works inside it, and captures per frame are ≤ 4.

- [x] **6. Sidebar.** *(2026-10-16, checked in game. Icons are placeholders, to be redrawn later. Appearance opens Themes until step 10)*
  - Glass pane inset in the window.
  - Search field at the top.
  - Entry rows with coloured rounded-square icons, in groups: the categories,
    then HUD and Friends, then Appearance and Settings.
  - Accent selection fill and neutral hover.
  - Icons only below the collapse width.
  - Categories open their module list.
  - *Done when:* every entry opens its page, at the default and minimum window
    size.

- [x] **7. Header and navigation.** *(2026-10-16, checked in game. Modules lists scroll at SCROLL_STEP_FINE)*
  - A page stack with back/forward history.
  - The ‹ › buttons and the title on glass.
  - Content scrolls under the header.
  - Ctrl+[ and Ctrl+] go back and forward.
  - Push/pop slide, and a crossfade on sidebar switches.
  - *Done when:* going deep and back restores each page and its scroll.

## Content

- [x] **8. Grouped lists.** *(2026-10-16, works in game; its look redone in 8b)*
  - Widgets: `Card`, section header, and rows: toggle, value + stepper,
    segmented, options and disclosure ›.
  - Inset hairlines and concentric first/last highlights.
  - Card pages scroll under the header (moved here from step 7: the old
    sections own their scroll lists, so they start below it).
  - A card-based `SettingList` that reuses the existing setting models, with
    every setting type mapped to a row.
  - *Done when:* one test page shows every row type in both appearances.

- [x] **8b. Control look pass.** *(2026-10-16; finished in 8d: every settings row now sits in a card)* Restyle only: `look/SettingLook`, the pane, header
  and sidebar looks, and control tokens in the Apple Palette. No row behaviour
  changes. Targets: `reference/UI/tahoe-wallpaper.png` (real Tahoe: layout, cards,
  rows, toggles, headers, sidebar) and `reference/UI/concept-bluetooth.png` (a fan
  concept: the push-button look only).
  - **Materials:** the content pane is a near-opaque solid in the window colour
    (about 92%), not a blurred material; the sidebar stays glass; rows never sit
    on glass.
  - **Header:** plain ‹ › with no chrome, dimmed when unavailable; a
    heavier title on one line.
  - **Cards:** no border; a little darker than the pane in Light, lighter in
    Dark; radius = pane radius − pane padding; one row height for every control;
    the same padding both sides.
  - **Card headers:** semibold, outside the card, flush with its left edge; an
    optional trailing action ("Show All (9)") in secondary text that brightens
    and underlines on hover. A folding group's "9 settings ›" becomes that.
  - **Switch:** one style everywhere (rows, module list, detail header): a capsule
    about twice as wide as tall, white knob inset 2, soft shadow, 120ms ease-out.
    The detail header loses its "Enabled/Disabled" word.
  - **Push buttons, keybind included:** a filled bright surface with a 1px border
    and soft shadow, shorter than a row. Buttons that open something end in
    "…". Standalone actions sit below their card, right-aligned.
  - **Segmented:** a capsule track shorter than a row, equal segments, a raised
    lighter pill with a shadow sliding in 180ms; unselected labels secondary.
  - **Sliders:** Control Center style: thin track, accent fill, white elongated
    knob with a shadow.
  - **Sidebar:** selection is an accent capsule with white text, the fill
    darkened until white passes 4.5:1.
  - **Scroll bars:** overlay style, hidden at rest, shown while scrolling.
  - *Done when:* the Gallery and every rebuilt page read like the Tahoe reference
    in both appearances.

- [x] **8d. Materials and structure pass.** *(2026-10-16, checked in game; the sidebar and ‹ › frost the window, not the game)* Replaces glass-everywhere: glass is
  only the sidebar and small floating controls; everything else is solid.
  - **Behind the GUI:** the game is dimmed (black at about 35%) and blurred, like
    a macOS sheet's background, and never legible through a content surface.
  - **Solid surfaces** (Palette tokens): the window (about #1E1E20 Dark, #ECECEC
    Light), cards one step lighter in Dark (about #2A2A2D) and white in Light,
    borderless; popovers and menus elevated and solid with a soft shadow.
  - **Glass:** the inset sidebar (radius = window radius − inset) and the ‹ ›
    pair. No glass on glass, and none on a card.
  - **Header:** no full-width capsule; the title is plain text in the window's top
    area, aligned with the content's left edge, with ‹ › in a small glass capsule
    to its left.
  - **Controls:** the Themes grid loses its switches (click a card; accent ring
    and tick show the one chosen). The module list loses its colour bar; the open
    module's row gets a neutral highlight. A switch's "on" and the sidebar's
    selection use a control accent: the theme primary darkened and saturated until
    a white knob reaches 3:1. Sidebar selection text is white, regular weight.
  - **Type:** regular and semibold only; secondary text at 60% or more in Dark.
    The content column is capped (`CONTENT_MAX_WIDTH`) and centred, search fields
    included.
  - **Empty states:** centred icon, short title, one sentence, one primary
    button (Friends: "No friends yet" / "Add Player…").
  - **8b, finished here:** every settings row sits in a solid grouped card with
    inset hairlines and its header above the card, in every section.
  - *Done when:* the game is dimmed and blurred behind every page, only the
    sidebar and the ‹ › capsule are glass, and every page's rows are in cards.

- [x] **8c. One column of modules.** *(2026-10-16, checked in game. The sidebar search is the one search: on Modules it searches every module)* The Modules section's own category column
  repeats the sidebar, and it has a second search field beside the sidebar's.
  - Sidebar = categories (as now).
  - Middle column = the modules in the chosen category, with one search field
    that searches every module.
  - Right = the chosen module's detail.
  - *Done when:* there is one list of categories and one search field on screen.

- [x] **9. Modules.** *(2026-10-16 as pushed pages, option B; Split (option A) also available from Appearance → Modules Layout; checked in game 2026-10-17)*
  - Category pages as cards of module rows (switch, plus ›); the row opens the
    module's page, pushed in from the right (`PageStack`, 240ms, sharp ease-out).
  - The module page as grouped cards, its switch and description in the first.
  - ‹ ›, Escape and the history pop and push the same way.
  - The old two-column Modules section goes.
  - *Done when:* every module in every category can be toggled and configured.

- [x] **10. Appearance page.** *(2026-10-16, checked in game 2026-10-17. Also holds Modules Layout: Pages or Split)*
  - Light/Dark segmented control.
  - Theme circles split diagonally, the selected one ringed.
  - The Glass slider.
  - Reduce Transparency and Reduce Motion.
  - All live.
  - *Done when:* each control visibly changes the UI at once.

- [x] **11. Settings page.** *(2026-10-16 as pushed pages like Modules; checked in game 2026-10-17)*
  Client Settings as grouped cards, including keybinds with their clash warnings:
  a list page (a card of pages with their changed counts, and About), each page
  pushed in with its sentence, its cards and a two-press Reset below; the sidebar
  search shows matches from every page. The old Settings section goes.

- [~] **12. Friends page.** *(built 2026-10-17 as pushed pages like Modules (DESIGN.md §2.6b); checked in a harness without the game; in-game check pending, with friends online and players on a server)* Search/add, grouped list, detail page, undo. The old
  Friends section goes.

- [ ] **13. Search.** The sidebar search shows matching modules and settings in
  the content pane, and Enter opens the top result.

## Layers above

- [ ] **14. Popovers and menus.**
  - Dropdowns, the colour picker and the item picker move to the popover Screen
    on glass.
  - Never glass on glass.
  - *Done when:* each opens over the window and over the sidebar correctly.

- [ ] **15. Edit HUD button and the Apple HUD editor.**
  - Edit HUD… stays a push button under the elements' card (§10.1). *A glass pill
    was built 2026-10-22 and taken out 2026-10-23 at the developer's request.*
  - The editor's elements on glass, not the solid stand-in (§10.1b). *Fixed
    2026-10-23: the shell's fade had been an alpha push around the whole editor,
    so every pass refused; unseen in game.*
  - Its own HUD editor overlay, with glass tiles at each element's scale
    (§10.3), dragged with snap guides and undo.
  - The glass toolbar at the bottom centre and the −/+ badges (§10.2b). *Built
    2026-10-23; glass unseen in game.*
  - One shared capture for all tiles.
  - The old HUD and Layout sections go from this UI.
  - *Done when:* editing works and the Classic editor is untouched.

- [ ] **16. HUD over the game.** Glass plates for HUD elements while the Apple UI
  is active, fitted to each element's scale. Reduce Transparency gives solid
  plates.
  - *Done when:* plates look right in a world and the capture budget holds.

## Finish

- [ ] **17. Clean-up.**
  - Delete every leftover Classic look, widget and alias in `apple`.
  - Run the full §12 verification.
  - Note what wasn't seen.

---
