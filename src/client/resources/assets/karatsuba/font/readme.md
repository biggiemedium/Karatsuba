# font

TrueType (.ttf) and OpenType (.otf) fonts. Every font in this folder appears in the
HUD Style's **Font** list automatically, labelled from its file name
(`mona-sans-italic.ttf` shows as "Mona Sans Italic"). Nothing needs registering.

**File names must be lowercase** (letters, digits, `-`, `_`, `.`). This folder is
also part of the mod's resource pack, and Minecraft rejects any resource path with
a capital letter, logging an "Invalid path in mod resource-pack" error for it on
every launch.

To load one in code: `Core.fonts().load("name", "/assets/karatsuba/font/<file>.ttf", size)`.
