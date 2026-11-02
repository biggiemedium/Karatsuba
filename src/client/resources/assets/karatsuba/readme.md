# Karatsuba assets

Anything in here is packed into the mod jar and reachable on the classpath at
`/assets/karatsuba/...`. That path is what the loaders take — not a Minecraft
`Identifier`, because NanoVG keeps its own GL texture and its own font atlas and
never goes through the game's resource manager.

| Folder | Holds | Loaded with |
|---|---|---|
| `font/<family>/` | `.ttf` and `.otf` typefaces, one folder per family | `Assets.fontFile(path, size)`, or `Core.fonts().load(name, path, size)` |
| `texture/icon/` | small UI images: category icons, badges | `Assets.texture(path)` |
| `texture/background/` | larger images: GUI backdrops, banners | `Assets.texture(path)` |
| `sounds/` | `.ogg` clips, each named in `sounds.json` | `Sounds.play(name, volume)`; the game's sound system, not Core |
| `shaders/` | GLSL programs and includes | `Core.shaders()`, paths relative to `shaders/`; see `shaders/readme.md` |

`Assets` is `dev.px.karatsuba.adapter.asset.Assets`.

## Fonts

NanoVG rasterises TrueType outlines itself, so a `.ttf` is all it needs — drop one
in its family's folder under `font/` and load it once at boot:

```java
Core.fonts().load("ui", "/assets/karatsuba/font/inter/inter-regular.ttf", 9f);
Render.setDefaultFont(Core.fonts().get("ui"));
```

Until you do, the default font is whichever system typeface the adapter could find
(Segoe UI on Windows), which is fine to develop against but is not the same on
every machine. Bundling one is how the client looks identical everywhere.

A face is registered per size, so load the sizes you draw at rather than scaling a
single handle — a 9pt face scaled to 18pt is blurry, a 18pt face is not.

## Textures

`Assets.texture` decodes with stb, so PNG, JPEG, TGA and BMP all work; PNG with an
alpha channel is what you want for icons. Every texture is decoded once and cached,
and the cache is released when the client stops.

```java
Texture icon = Assets.texture("/assets/karatsuba/texture/icon/sword.png");
Render.texture(icon, x, y, 16f, 16f);
```

## Sounds

Core has no audio at all, by design — it is a rendering and state library. Clips
are played through the game with `dev.px.karatsuba.adapter.asset.Sounds.play(name,
volume)`, as interface sounds (not placed in the world).

The game only finds them as `assets/karatsuba/sounds/<name>.ogg`, and only once
`sounds.json` (next to this file) names them. A new clip is the file plus one line
there: `"name": { "sounds": ["karatsuba:name"] }`. No registry entry is needed: the
client never sends these to a server.
