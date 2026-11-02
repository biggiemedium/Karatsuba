# Game Over: plan

Ideas for `GameOverModule`, drafted 2026-10-30. Nothing here is built yet.

A new death screen is a `SubMode` (it opens on `DeathScreenEvent`) plus a
`GameOverScene` (it draws, takes input, and hands back what it borrowed in
`end()`). These are the seams the ideas below use:

| Seam | Gives |
|---|---|
| `Render` | Rects, gradients, text, textures, `blur`, `shadow`, `worldToScreen` |
| `Assets.texture` / `Assets.fontFile` | Bundled images and fonts |
| `Sounds.play` | Bundled clips (`assets/karatsuba/sounds/` plus a `sounds.json` line) |
| `CameraEvent` / `CameraDistanceEvent` | Move and turn the camera (Siege's death cam) |
| `Options.view` | Borrow third person |
| `ShaderPasses.scene()` | The screen behind, for a full-screen effect such as desaturation |
| `PlayerFaces`, `PlayerList`, `Entities` | A killer's head, name and position |

## Siege: making it look like the game

### What we know (searched 2026-10-30)

| Fact | Source | How sure |
|---|---|---|
| After a death you see a **death replay**, the kill from the killer's point of view. A Report Player button appears there after a few seconds | [Steam](https://steamcommunity.com/app/359550/discussions/0/1727575977558638118/) | Confirmed |
| Then support mode: drones and cameras | [Wikipedia](https://en.wikipedia.org/wiki/Tom_Clancy%27s_Rainbow_Six_Siege) | Confirmed |
| A kill-feed line is "name, weapon icon, name", right-anchored, killer and victim told apart only by team colour, no words | [cutpoint #433](https://github.com/FerventOrange/cutpoint/issues/433) (an OCR tool reading Siege footage) | Confirmed |
| A **greyscale** spectator screen and a **darkened** kill cam are filed as **bugs**, so the intended look is full colour and not darkened | [R6 Fix LIVE-43559](https://r6fix.ubi.com/projects/RAINBOW6-SIEGE-LIVE/issues/LIVE-43559), [LIVE-61228](https://r6fix.ubi.com/projects/RAINBOW6-SIEGE-LIVE/issues/LIVE-61228) | Confirmed |
| Your team blue, the enemy orange | Memory | Likely; colours matched by eye |
| A "killed by" card naming the killer, operator and weapon | Memory | Likely; the exact layout is unknown |
| A big "You Died" title | Only Leapfrog's version | Not in the game, so removed |
| **The old death (before Y6S2, 2021)**: a first-person death animation, then a death replay "in slow motion" with an "opponent closeup". Y6S2 made the animation skippable and removed both "to help keep players in the flow of action" | [North Star notes](https://www.ubisoft.com/en-us/game/rainbow-six/siege/news-updates/seasons/northstar) | Confirmed |
| The old screen's banner: "ELIMINATED" at the **top** | The user, from r/Rainbow6 threads (old_death_screen, when_did_they_remove_this_screen_and_why). Reddit can't be fetched from here | Confirmed by the user; the rest of the layout is still unknown |

### Built (2026-10-30), after the user's screenshot of the old screen

The screenshot (an old Siege death) shows:
- "Beaulo. KILLED YOU" a quarter of the way down, with a red skull after it;
- the killer in close-up;
- the operator card on the left: a blue header with the portrait over its top right, the operator ("DOC") and the name, and four white health segments;
- a dark loadout grid under it, with primary, secondary, gadget, and the ability highlighted blue.

| Part | In Minecraft |
|---|---|
| Timeline | 1.5 s pull back from the body, then a 1.5 s close-up in front of the killer's face, pushing in, with the face right of centre. Then it holds until respawn |
| Headline | "NAME KILLED YOU" plus a skull drawn from shapes; "ELIMINATED" when nobody killed you |
| Card | Face for the portrait (an initial block for mobs, or while it loads); the entity type ("PLAYER", "ZOMBIE") for the operator; the name; health live from the killer |
| Grid | Main hand, off hand, chestplate, and **what killed you** highlighted (sword, end crystal, bow, TNT, or a stand-in for a cause: a feather for a fall, a lava bucket) |
| No killer, or your own crystal | The card and the close-up are yours |
| Killer and weapon | `adapter.game.player.DeathCause`. The last hit's damage type, attacker and direct entity come from `LivingEntity.onDamaged`, which the server's damage packet calls. The death message's hover cards add the killer's UUID (for "doomed to fall by") and the exact weapon ("using [item]") |
| Left out | The kill-feed row (not on the screenshot), the red outline around the killer, and the top score strip |

### Still open

| # | Idea | Work |
|---|---|---|
| 1 | Red outline around the killer in the close-up, as the screenshot has | Medium: the Shaders module's silhouette mask could do it |
| 2 | Headshot mark | Can't be known from the client |
| 3 | Confirm the card layout and colours against a video | Check by eye |
| 4 | "Report"-style countdown ring before Continue can be pressed | Small: `Render.arc` |

## Other deaths that are easy to recreate

Ranked by look per effort. "Assets" means what has to be supplied, as with the
Zelda and Siege clips. Their fonts and sounds belong to their games; fine for a
personal build, not for one shared around.

| Game | What it looks like | Work | Assets | Notes |
|---|---|---|---|---|
| **Dark Souls / Elden Ring**: "YOU DIED" | A dark band across the middle fades in, the red serif text slowly grows and fades in, the sound plays. The world stays visible above and below | **Very easy**: a gradient band, a slow `Render.scaled`, text | A serif font (Optimus Princeps style) and the sound | The most recognised death screen there is. Elden Ring is the same with a different tint and timing, so one mode with a variant setting covers both |
| **GTA V**: "WASTED" | The world turns grey and blurs, a white flash, the camera drifts out, "wasted" in red Pricedown | **Easy** with a desaturate shader (see "Shared work") and the Siege camera | Pricedown-style font, the sound | Reuses most of Siege |
| **Resident Evil**: "YOU ARE DEAD" | Fade to black, dripping red text | **Very easy** | Font, sound (optional) | Drip is a few `gradientV` strips sliding down |
| **Undertale**: the heart breaks | Black screen, a red heart cracks in two, then shatters into shards that fall; "GAME OVER", then "Stay determined..." typed out | **Easy**: heart and shards from triangles, plain gravity | Sound (optional) | Very charming; no font needed |
| **Super Mario**: iris wipe | A black circle closes in on the player's body, the jingle plays | **Easy**: `Render.worldToScreen` of the body, a ring drawn with a huge stroke (or four rects plus a circle outline) | Jingle | Pairs with a little "pop up and fall" camera bob |
| **Metal Gear Solid**: "SNAKE? SNAKE!!" | Screen goes sepia/grey, "GAME OVER", the codec cry | **Easy** with desaturation | Voice clip | Mostly the sound doing the work |
| **Halo / Call of Duty**: respawn timer | Body cam plus "Respawn in 5..." counting down, then respawn by itself | **Very easy** | None | The timer, not the respawn, is the point; respawning at 0 keeps the screen |
| **Skyrim**: slow pull away | Slow camera pull-back and rise from the body, the HUD gone, then fade to black | **Easy**: the Siege camera with longer, higher timings | None | Could be a Siege setting ("Cinematic") instead of its own mode |

Not easy, left out: Hollow Knight's shade (needs world entities), Fortnite's
placement card (we can't know the placement), Cuphead's level-progress bar (no
progress to show).

## Shared work worth doing first

- **Desaturate shader.** GTA and MGS want it. Siege no longer does: its greyscale is a bug.
- **Killer lookup**: done, `Killer` on `DeathScreenEvent`.
- **A scene timeline helper**: "after N ms, start this animation". Each scene
  times its stages by hand now (Zelda's fade, then the buttons). Three more modes
  would repeat that.

## Settings to consider for every mode

- **Volume.** Siege and Zelda have it already.
- **Respawn delay** before input is taken. Siege fixes it at 1 s.
- **Hide HUD.** The hotbar is still drawn under the scene. It would need an
  adapter seam: `InGameHudMixin` already hides parts of the HUD.
