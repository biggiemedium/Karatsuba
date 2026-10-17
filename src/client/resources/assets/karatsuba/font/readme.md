# font

TrueType (.ttf) and OpenType (.otf) fonts, **one folder per family**:

```
font/
  sf-pro/   sf-pro.ttf, sf-pro-semibold.otf, sf-pro-italic.ttf
  inter/    inter-italic.ttf
  roboto/   roboto-italic.ttf
  misans/   misans-regular.ttf, misans-medium.ttf, misans-demibold.ttf
```

Name the folder after the family and each file `<family>-<style>.<ext>`
(`mona-sans/mona-sans-italic.ttf`). Every font in these folders appears in the
HUD Style's **Font** list automatically, labelled from its file name
(`mona-sans-italic.ttf` shows as "Mona Sans Italic"). Nothing needs registering.
Folders are read one level deep: a family's files go straight in its folder.

**File and folder names must be lowercase** (letters, digits, `-`, `_`, `.`). This
folder is also part of the mod's resource pack, and Minecraft rejects any resource
path with a capital letter, logging an "Invalid path in mod resource-pack" error
for it on every launch.

In code a font is named by its path under this folder:
`Assets.fontFile("sf-pro/sf-pro.ttf", size)`, or
`Core.fonts().load("name", "/assets/karatsuba/font/sf-pro/sf-pro.ttf", size)`.
