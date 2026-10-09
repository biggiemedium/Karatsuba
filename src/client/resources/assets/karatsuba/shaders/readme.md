# Shaders

GLSL for Core's `ShaderService`, compiled by the adapter's `GlShaderBackend`
(`adapter/render/shader`). Paths given to `ShaderSource` and `#include` are relative
to this folder.

```java
Core.shaders().register(ShaderSource.named("frosted")
        .vertex("core/quad.vsh")
        .fragment("effect/frosted.fsh")
        .build());

// in Render2DEvent or an overlay:
ShaderPasses.rect("frosted", x, y, width, height,
        u -> u.sampler("uScene", ShaderPasses.scene(), 0));
```

## Rules

- **GLSL 150** (the game's 3.2 core profile). A stage with no `#version` gets
  `#version 150`.
- **Vertex inputs** are `Position` (vec3), `UV0` (vec2), `Color` (vec4), by those
  names. `core/quad.vsh` covers every pass, so most effects are a fragment shader.
- **UV:** 2D passes run (0, 0) top left to (1, 1) bottom right, the way NanoVG images
  are stored. The sky pass runs (0, 0) bottom left (clip space * 0.5 + 0.5).
- **The screen behind** (`ShaderPasses.scene()`) is a whole-window copy with GL's
  bottom-left origin: sample it with `gl_FragCoord.xy / uViewport`.

## Uniforms you get

| Name | Type | Set by |
|---|---|---|
| `uTime` | float, seconds | Core, every pass |
| `uResolution` | vec2, scaled screen units | Core, every pass |
| `uMouse` | vec2, scaled screen units | Core, every pass |
| `uViewport` | vec2, framebuffer pixels | adapter, every pass |
| `uPartialTicks` | float | adapter, every pass |
| `uProjection` | mat4 | each pass (used by `core/quad.vsh`) |
| `uRect`, `uScale` | vec4 (x, y, w, h), float | `rect` / `screen` |
| `uInverseProjection`, `uInverseView`, `uCameraPosition` | mat4, mat4, vec3 | `sky`; `core/sky.glsl` declares them |

## Editing live

Files in `.minecraft/karatsuba/shaders/` win over the ones here, path for path.
Copy one there, edit, press F3+T: every program rebuilds on its next draw. A
compile error is logged once with the shader's name; the full numbered source is
at debug level.
