#version 150

// An aurora over a clear night: folded curtains of light, sharp and bright along
// their lower edge, fading up into a second colour, rippling and drifting. Stars
// show through where the aurora is faint. Drawn with ShaderPasses.sky in place of
// the vanilla sky.

#include "../../core/sky.glsl"
#include "../../core/stars.glsl"

in vec2 vUv;

uniform float uTime;
// The curtains' lower edge, and their tops.
uniform vec4 uLower;
uniform vec4 uUpper;
// How bright the aurora is; 1 is the default.
uniform float uIntensity;
// The star count against the default; 0 hides them.
uniform float uStarDensity;
// How fast the aurora moves; 1 is the default.
uniform float uSpeed;

out vec4 fragColor;

// Slices through the curtains' height. Each is the same pattern on a higher plane,
// which is what stretches it into vertical rays converging overhead.
const int LAYERS = 24;

// The curtain pattern on one plane: thin bright lines where a warped noise field
// crosses one half, a softer haze around them, rays rippling along them, and broad
// patches where the aurora is out at all.
float curtain(vec2 p, float time) {
    vec2 warp = vec2(noise(p * 0.6 + vec2(time * 0.11, 0.0)), noise(p * 0.6 + vec2(3.7, -time * 0.09)));
    vec2 q = p + (warp - 0.5) * 2.4;
    float field = noise(q * 0.45 + vec2(time * 0.03, 0.0)) * 0.65 + noise(q * 1.1 - vec2(0.0, time * 0.05)) * 0.35;
    float line = abs(field - 0.5);
    float body = 1.0 - smoothstep(0.0, 0.035, line);
    float haze = 1.0 - smoothstep(0.0, 0.12, line);
    float rays = 0.55 + 0.45 * noise(vec2(dot(q, vec2(5.0, 3.0)), time * 1.3));
    float patches = smoothstep(0.3, 0.6, noise(p * 0.12 + time * 0.01));
    return (body * rays + haze * 0.3) * patches;
}

vec3 aurora(vec3 direction, float time) {
    if (direction.y <= 0.0) {
        return vec3(0.0);
    }
    // A different start height per pixel hides the steps between slices.
    float jitter = hash12(gl_FragCoord.xy);
    vec3 light = vec3(0.0);
    for (int i = 0; i < LAYERS; i++) {
        float f = (float(i) + jitter) / float(LAYERS);
        float height = 1.0 + f * 1.6;
        vec2 p = direction.xz * (height / max(direction.y, 0.03)) * 1.6;
        vec3 colour = mix(uLower.rgb, uUpper.rgb, smoothstep(0.05, 0.8, f));
        float fade = (1.0 - f) * (1.0 - f);
        light += colour * curtain(p, time) * fade;
    }
    // Fades into the haze before the horizon, where the pattern would alias.
    return light * (3.0 / float(LAYERS)) * smoothstep(0.0, 0.15, direction.y);
}

void main() {
    vec3 direction = skyDirection(vUv);
    float height = direction.y;

    vec3 zenith = vec3(0.004, 0.008, 0.02);
    vec3 horizon = vec3(0.02, 0.045, 0.06);
    vec3 colour = height >= 0.0
            ? mix(horizon, zenith, sqrt(height))
            : horizon * mix(1.0, 0.4, clamp(-height * 3.0, 0.0, 1.0));
    // The aurora lights the haze along the horizon.
    colour += uLower.rgb * 0.06 * uIntensity * exp(-abs(height) * 10.0);

    vec3 light = aurora(direction, uTime * uSpeed) * uIntensity;
    float glare = clamp(dot(light, vec3(0.3, 0.59, 0.11)), 0.0, 1.0);
    colour += stars(direction, uStarDensity, uTime) * (1.0 - glare * 0.8) * smoothstep(0.0, 0.1, height);

    // Soft rolloff, so the brightest folds saturate instead of clipping flat.
    colour += 1.0 - exp(-light * 1.5);
    fragColor = vec4(colour, 1.0);
}
