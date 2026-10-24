# Multi-screen plan

Small floating screens the player can drag around, by default in a top corner,
each showing something to watch or do while playing (an entertainment system).
More than one can be open at once.

> **Status: idea, started 2026-10-23.** Nothing is built. What each screen shows
> is still open (§5); this file settles where the screens live and how long they
> last first, because that decides everything else.

---

## 1. What the player gets

- A **screen** is a small window: a picture area, a thin title bar and a few
  controls. It sits over the game, in a corner to start with.
- It can be **dragged** anywhere and **resized** within limits. Where it was left
  is remembered.
- The player can **open several** and close any of them. Each shows its own
  content.
- A screen **keeps playing across screen changes.** Opening the GUI, the
  inventory, the pause menu, a server list or the title screen does not stop it.
- Playback **may stop when the game is quit**. That is the only time a screen has
  to end. Whether leaving a world (disconnect) stops it is §6.1.

## 2. HUD element or not: not

A screen should **not** be a HUD element. It should be its own overlay, beside the
HUD.

| Need | HUD element (`StyledElement` in `HudService`) | Own overlay |
|---|---|---|
| Show outside a world (title screen, server list) | No. `GameEventBridge.renderFrame2D` returns early with no world unless an overlay is capturing, and the HUD is a world thing. | Yes, after one adapter change (§3.2). |
| Several of the same kind open at once | No. One element per id, registered once in `HudElements`. | Yes. The overlay owns a list. |
| Its own look (video, controls) | Fights the preset: an element describes roles and the preset draws them, with a plate (`hud/DESIGN.md` §3, root `DESIGN.md` §8.1). | Free to draw what it needs. |
| Live while it has nothing on screen | No. An element with nothing to describe draws nothing, and its state is the HUD's. | Yes. Playback is owned by a session, not by drawing. |
| Dragging, anchors, snapping | Free from `HudService` and the HUD editor. | Has to be done, but `dev.px.core.hud` `Anchor`/`Placement` can be reused for the maths. |

So the HUD's layout engine is the one thing worth borrowing. Everything else
pulls the other way.

## 3. Shape

### 3.1 Pieces

| Piece | Where | What it does |
|---|---|---|
| `MultiScreenOverlay` | `client/screen/`, one line in `Overlays` | Draws every open screen and takes input when one is being used. Registered **after the HUD and before the GUIs**, so screens sit over the HUD and under the client GUI. |
| `Screens` (manager) | `client/gui/multiscreen/` | The open screens, in z-order: open, close, raise to front, save and load. Exists for the client's whole life, not a world's. |
| `MiniScreen` | `client/gui/multiscreen/` | One window: bounds, anchor, title, and the app it hosts. Its chrome (title bar, close, resize grip) is drawn through a look file, per root `DESIGN.md` §1.2. |
| `ScreenApp` | `client/gui/multiscreen/app/` | What a screen shows (§5). An interface: `update`, `draw(bounds)`, input, `start`/`stop`. Its **playback lives in the app, not in the draw**, so it keeps running when the screen isn't drawn. |
| A media seam | `adapter/integration/` (or `adapter/media/`) | Whatever decodes video or audio (§5.2). The client package may not touch the game or a native library directly (`verifyClientIndependence`), so this goes through the adapter, as Spotify already does behind Core's `MediaProvider`. |

### 3.2 Adapter changes

- **Draw with no world.** `GameEventBridge.renderFrame2D` currently skips the top
  layer outside a world unless something is capturing. It needs to ask the
  overlays whether any has something to show (for example a new
  `Overlay.isVisibleOffWorld()`, default false) instead. The HUD keeps its
  current behaviour.
- **Video as a NanoVG texture.** Frames are uploaded into a `NanoVGTexture` and
  drawn with `Render2D.texture`/`roundTexture`. That keeps the root `DESIGN.md`
  §1.8 rule: no game draw inside a NanoVG frame.

## 4. Interaction

- **Dragging needs a free cursor.** In game the cursor is locked, and an overlay
  only gets input while it is capturing. Options, to pick one or more (§6.2):
  - only while a game screen is open (chat, inventory, pause), as other clients
    do for their HUD
  - a bind that frees the cursor over the screens ("Interact with Screens")
  - from the HUD's Layout editor, alongside HUD elements
- **Click-through.** When the player isn't interacting, a click on a screen goes
  to the game, never to the screen.
- **Snapping.** Screens snap to screen edges and to each other, and keep out of
  vanilla HUD zones the way `VanillaAvoidance` does for elements.
- **Focus.** The last screen clicked comes to the front. Only that one takes keys.
- **Keyboard.** Esc unfocuses, and must not clash with root `DESIGN.md` §6.1.

## 5. What a screen shows (open)

To be decided. Candidates so far:

- **Now Playing.** The existing Spotify integration, but bigger: artwork,
  progress and controls. Cheapest, because the seam already exists.
- **Video.** A local file, a stream or YouTube. This is the expensive one (§5.2).
- **Orbit Camera.** A live second view that circles the player, or a target
  such as a far-off player, to size them up (§5.3).
- **Utilities.** A mini map, a chat log, notes, a timer.
- **Mini games.** Simple games drawn in Render2D.

### 5.2 Video: reuse, don't build (researched 2026-10-23)

**Rule: don't build a player or a streaming service.** Use a library that
already does it, and keep the client's part to the window around it.

| Option | What it gives | Catches |
|---|---|---|
| **[MCEF](https://github.com/CinemaMod/mcef)** (CinemaMod): Chromium in Minecraft, built on java-cef. Fabric build for 1.21.4 (`2.1.6-1.21.4`). | A real web browser in a screen: YouTube, Twitch, any web page, web apps, Spotify's web player. One integration, endless content. | A separate mod the player installs (`modCompileOnly` against `com.cinemamod:mcef`, with `mcef-fabric` at runtime). It downloads the CEF binaries on first start from `mcef-download.cinemamod.com`, so it needs the network then, and antivirus may block it. LGPL-2.1, fine if we only depend on it. Chromium 116. **DRM services (Netflix, Disney+, Prime) will most likely not play**, because embedded Chromium builds usually ship without a working Widevine module. To verify, along with whether H.264/AAC are included. |
| **[WATERMeDIA](https://modrinth.com/mod/watermedia)**: a media API on LibVLC. Forge, NeoForge and Fabric; lists 1.21.4–1.21.5. | A plain player: give it a URL or file and it plays. Understands YouTube, Twitch, Kick, Google Drive, OneDrive, Dropbox, Streamable links. Lighter than a browser for "play this link". | **PolyForm Strict licence: no redistribution, non-commercial only.** So it can only be a separately installed dependency, never in our jar. VLC is bundled on Windows x64 only; macOS and Linux players install VLC themselves. Can't coexist with other VLC mods. |
| vlcj or JavaCV/FFmpeg directly | Full control | This is building our own player: codecs, URL resolving, sync, native jars per platform, GPL licences (vlcj is GPL or commercial; JavaCV ships GPL code). **Rejected.** |
| Spotify (already built, `adapter/integration`) | Music: artwork, progress, controls | Audio only. Becomes the Now Playing app. |

**Recommendation: MCEF first, as a "Browser" app.** It's the most content for
the least work. The player uses the real YouTube and Twitch sites, logins
included, and we only build the window, the address bar and input forwarding.
Add WATERMeDIA later only if a lighter "paste a link" player is wanted.

**How it fits our rules:**
- **Optional.** Both are separate mods. The adapter checks whether each is
  loaded and the app only appears when it is. The client never sees MCEF or
  WATERMeDIA types (`verifyClientIndependence`); a seam in `adapter/integration`
  hands the client a texture to draw and takes input from it, like `MediaProvider`
  for Spotify.
- **No game draw inside a NanoVG frame.** MCEF paints into its own GL texture.
  NanoVG can wrap an existing GL texture id (`NanoVGGL3.nvglCreateImageFromHandle`),
  so the screen draws it as an ordinary `Render2D` texture. Check that MCEF's
  paint happens outside our frame (root `DESIGN.md` §1.8).
- **Input.** Mouse, scroll and keys are forwarded to the browser only while the
  player is interacting with that screen (§4).
- **Audio** comes from Chromium or VLC themselves. Decide whether it follows the
  game's master volume and whether it mutes when the window loses focus.
- **If the mod is ever distributed publicly,** re-read both licences first.

### 5.3 Orbit Camera (added 2026-10-23)

A screen showing a live view from a camera that **slowly circles a subject**.

- **Self.** It circles the player, as a third-person view that doesn't take over
  the main camera.
- **Investigate.** Pick a target, such as a player far away, and the camera
  pans around them so you can see what you're dealing with: armour, held items,
  what they're standing on, who is near them.
- **Controls.** Drag inside the screen to orbit by hand, scroll to zoom, and it
  returns to auto-orbit after a moment. The title bar shows the target's name
  and distance.
- **Choosing a target.** Look at an entity and press a bind, middle-click, pick
  from a list of nearby players, or follow the current combat target
  (`CombatKit`). Undecided (§6.8).
- **Lifetime.** Unlike media, this only means anything in a world. Off-world the
  screen shows "No world" or hides itself.

**What it can see.** Only what the client already knows. Entities are known only
within the server's tracking range (often about 48–128 blocks for players), and
blocks only within loaded chunks. A target beyond that can't be shown, and the
screen says so ("Out of range") rather than showing an empty void. It is purely
client-side: it sends no packets, so it's invisible to Grim.

**How to render it.** This is the hard part, and it lives in the adapter
(`adapter/render/`), next to `SceneTexture` and the shader passes, which already
deal with framebuffers.

| Option | What you get | What it costs |
|---|---|---|
| Full second world render into a framebuffer, from the orbit camera | The real thing: terrain, entities, lighting | Roughly doubles world render cost at full size (much less at thumbnail size and a lower frame rate). The world renderer assumes one camera: frustum culling, chunk visibility and sorting are computed for the main view, so chunks behind the player may be culled or not built. Needs mixins around `WorldRenderer`, like portal/camera mods do. |
| Entity-only preview (as the inventory draws the player) | The target's model, armour and held items, spinning | Cheap and simple. No surroundings. A game draw, so it goes through `GuiFrame.defer` or `suspend()`/`resume()` (root `DESIGN.md` §1.8). |
| Entities plus nearby blocks, a hand-built mini scene | Target and the blocks around them | In between. Rebuilds a small chunk mesh. Lighting and entities have to be reproduced. |

**The camera must never sit in a block, and must always see the subject.**
This is a hard requirement, not a nicety. Every frame the camera's position is
checked and fixed before it renders:

- **Never inside a block.** The camera point, plus a small margin for the near
  plane (about 0.2 blocks), must be in non-solid space. Otherwise the view shows
  the inside of a block, or the world clipped open.
- **A clear line of sight.** Raycast from the subject (eye height and body
  centre) to the camera. If a block is in the way, the camera does something
  rather than staring at a wall:
  1. Pull in along the ray to just in front of the obstruction, as vanilla's
     third-person camera does (`Camera.clipToSpace`), but never closer than a
     minimum distance.
  2. If that would be too close, **skip ahead around the orbit** to the next
     angle that is clear, and/or rise in pitch. Sample the circle ahead of time
     so it glides to the clear arc instead of popping.
  3. If nothing on the circle is clear (the subject is in a 1×2 hole or a
     tunnel), go overhead, or say "No clear view".
- **Smooth.** Distance and angle changes are eased, so walking past a tree
  doesn't make the camera jitter. A camera that gets pulled in recovers slowly.
- **Glass and leaves** count as see-through for line of sight; fences and
  slabs count as their real shape.
- **Optionally,** entities between the camera and the subject are faded out or
  ignored so the subject stays visible.

The same rules apply when the player orbits by hand: dragging can't push the
camera into a wall.

A sensible route: ship the entity-only preview first, then try the full
framebuffer render at a reduced size and frame rate (for example 15 fps,
½ resolution) and measure it.

**Reuse.** `FreeCamModule` and its body double already move a camera away from
the player, and `CameraMixin`/`CameraEvent` hook the camera. Worth reading before
building the framebuffer route.

## 6. Open questions

1. **Leaving a world.** Does disconnecting stop playback, or only quitting the
   game? (Read so far: keep playing across menus and screens, and stopping on
   quit is fine.)
2. **How screens are interacted with** in game (§4).
3. **Restore on start.** After a restart, are the screens reopened where they
   were, and is playback resumed or left paused?
4. **Limits.** Maximum number of screens open, and a minimum and maximum size.
5. **Performance.** Video decoding off the render thread, and whether hidden or
   covered screens pause their decoding.
6. **Which GUI owns the settings.** Classic, Apple or both, and whether there is
   a "Screens" section in the rail.
7. **Recording and streaming.** Should screens hide from screenshots or a
   streamer mode?
8. **Orbit Camera target.** How a target is picked, what happens when it leaves
   tracking range or dies (hold the last view, fall back to self, or close), and
   whether more than one camera screen can run at once given the render cost.

## 7. Build order (draft)

1. Adapter: overlays can draw with no world (§3.2). Nothing visible changes.
2. `MultiScreenOverlay`, `Screens` and `MiniScreen` with an empty test app:
   open, drag, resize, close, several at once, saved positions.
3. The first real app, probably Now Playing (cheapest).
4. Browser app on MCEF (§5.2), behind an adapter seam, optional.
5. More apps, one at a time.
