# Original assets

Full-quality sources for assets the mod ships in a lighter form. Nothing here
goes into the jar.

| File | Shipped as | Why |
|---|---|---|
| `cherry-trees.png` (3840×2160, 11.2 MB) | `assets/karatsuba/texture/background/cherry-trees.jpg` (quality 92, 1.8 MB) | The loading screen showed its base colour while the PNG decoded: 241 ms in stb against 45 ms for the JPEG (2026-10-29) |
| `zeldagameovertext.png` (18541×2677, 509 KB) | `assets/karatsuba/texture/death_screen/zeldagameovertext.png` (2048×295, 97 KB) | Wider than many GPUs' largest texture (16384), and about 200 MB once decoded, for an image Game Over's Zelda mode draws a few hundred pixels wide. Halved repeatedly with bilinear filtering (2026-10-30) |

The loading screen's blurred placeholders (`cherry-trees-placeholder.png` and
`background-night-placeholder.png`, 64×36, next to the photos) are made from these
originals: area-averaged down to 64×36, then a 3×3 box blur. Remake them if a photo
changes.
