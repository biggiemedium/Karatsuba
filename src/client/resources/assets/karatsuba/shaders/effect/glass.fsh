// Liquid Glass and the standard material over a rounded rect (gui/apple/DESIGN.md §6).
//
// The screen behind, blurred from its mip levels, saturated a little and tinted.
// Glass also bends what is behind it in a thin band at the rim (lensing, along the
// SDF's gradient, toward the middle) and catches one fixed light from the top left
// along its edge. The standard material is the same pass with refraction and
// specular at 0. Everything scales with uPresence, so glass forms and melts by its
// lensing rather than fading. Sample uScene from ShaderPasses.sceneMipmapped().

uniform sampler2D uScene;
uniform vec2 uViewport;

uniform vec4 uRect;       // x, y, w, h in screen units, from ShaderPasses.rect
uniform float uScale;     // framebuffer pixels per screen unit

uniform float uRadius;    // corner radius, screen units
uniform float uRim;       // width of the lensing band at the rim, screen units
uniform float uRefract;   // 1 glass, 0 material: how far the rim bends
uniform float uSpecular;  // 1 glass, 0 material: the lit edge
uniform float uBlur;      // mip level the blur reads, at full presence
uniform float uSaturation;// 1 is none; a little over 1 for glass's richer colour
uniform float uAberration;// colour split at the rim, framebuffer pixels; 0 is off
uniform vec4 uTint;       // rgb, and how strongly it is mixed in
uniform float uPresence;  // 0 nothing, 1 fully formed
uniform float uPress;     // 0 to 1: a press brightening the rim

in vec2 vUv;
out vec4 fragColor;

const vec2 LIGHT = vec2(-0.70710678, 0.70710678); // top left, in window space (y up)

float sdRoundedRect(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Nine taps on a small ring, turned per pixel by a fixed hash: grain that does
// not crawl from frame to frame, and no banding from the mip's own blockiness.
vec3 blurred(vec2 uv, float level) {
    if (level <= 0.01) {
        return texture(uScene, uv).rgb;
    }
    float spread = exp2(level) / uViewport.x;
    float turn = hash12(gl_FragCoord.xy) * 6.2831853;
    vec3 sum = textureLod(uScene, uv, level).rgb;
    for (int i = 0; i < 8; i++) {
        float a = turn + float(i) * 0.78539816;
        vec2 o = vec2(cos(a), sin(a) * uViewport.x / uViewport.y) * spread;
        sum += textureLod(uScene, uv + o, level).rgb;
    }
    return sum / 9.0;
}

void main() {
    vec2 size = uRect.zw * uScale;
    vec2 halfSize = size * 0.5;
    // vUv runs top-down; flip to window space so gradients match the scene texture.
    vec2 p = (vUv - 0.5) * size * vec2(1.0, -1.0);
    float radius = min(uRadius * uScale, min(halfSize.x, halfSize.y));
    float sdf = sdRoundedRect(p, halfSize, radius);

    // Outward normal from central differences: exact for the shape, and no
    // derivative functions in non-uniform control flow.
    vec2 e = vec2(0.5, 0.0);
    vec2 normal = vec2(sdRoundedRect(p + e.xy, halfSize, radius) - sdRoundedRect(p - e.xy, halfSize, radius),
                       sdRoundedRect(p + e.yx, halfSize, radius) - sdRoundedRect(p - e.yx, halfSize, radius));
    normal = length(normal) > 0.0 ? normalize(normal) : vec2(0.0);

    float depth = max(-sdf, 0.0);                       // pixels in from the edge
    float rimPx = max(uRim * uScale, 1.0);
    float band = clamp(1.0 - depth / rimPx, 0.0, 1.0);  // 1 at the edge, 0 past the band
    float lens = band * band * band * uRefract * uPresence;

    // Bend toward the middle, as the curved edge of a lens does.
    vec2 offset = -normal * lens * rimPx * 0.85;
    vec2 uv = (gl_FragCoord.xy + offset) / uViewport;

    float level = uBlur * uPresence;
    vec3 colour;
    if (uAberration > 0.0 && lens > 0.0) {
        vec2 split = normal * uAberration * band / uViewport;
        colour = vec3(blurred(uv + split, level).r, blurred(uv, level).g, blurred(uv - split, level).b);
    } else {
        colour = blurred(uv, level);
    }

    float grey = dot(colour, vec3(0.299, 0.587, 0.114));
    colour = mix(vec3(grey), colour, mix(1.0, uSaturation, uPresence));
    colour = mix(colour, uTint.rgb, uTint.a * uPresence);

    // The lit edge: brightest facing the light, fainter on the far side, as light
    // passing through the glass catches the opposite rim.
    float edge = exp(-depth / (1.2 * uScale));
    float facing = max(dot(normal, LIGHT), 0.0);
    float away = max(dot(normal, -LIGHT), 0.0);
    float shine = (pow(facing, 2.0) + 0.35 * pow(away, 2.0)) * edge + 0.05 * band;
    colour += shine * uSpecular * uPresence * (0.55 + 0.6 * uPress);

    float mask = 1.0 - clamp(sdf + 0.5, 0.0, 1.0);
    fragColor = vec4(colour, mask * step(0.001, uPresence));
}
