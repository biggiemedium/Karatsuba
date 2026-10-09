#version 150

// A sky in two colours: uHorizon at the horizon to uZenith overhead, a soft band
// of light along the horizon, and optional twinkling stars. Drawn with
// ShaderPasses.sky in place of the vanilla sky.

#include "../core/sky.glsl"

in vec2 vUv;

uniform float uTime;
uniform vec4 uZenith;
uniform vec4 uHorizon;
// 0 hides the stars; 1 is full brightness.
uniform float uStars;

out vec4 fragColor;

// Cells across the sky for star placement; more cells, smaller and denser stars.
const float STAR_CELLS = 180.0;
// The share of cells that hold a star.
const float STAR_CHANCE = 0.005;

float hash(vec3 cell) {
    cell = fract(cell * 0.3183099 + 0.1);
    cell *= 17.0;
    return fract(cell.x * cell.y * cell.z * (cell.x + cell.y + cell.z));
}

vec3 stars(vec3 direction) {
    vec3 scaled = direction * STAR_CELLS;
    vec3 cell = floor(scaled);
    float seed = hash(cell);
    if (seed < 1.0 - STAR_CHANCE) {
        return vec3(0.0);
    }
    float distance = length(scaled - (cell + 0.5));
    float twinkle = 0.6 + 0.4 * sin(uTime * (1.5 + seed * 3.0) + seed * 40.0);
    // Faded in from the horizon, where vanilla's stars thin out too.
    float height = smoothstep(0.0, 0.25, direction.y);
    return vec3(smoothstep(0.5, 0.0, distance) * twinkle * height);
}

void main() {
    vec3 direction = skyDirection(vUv);
    float height = direction.y;

    vec3 colour;
    if (height >= 0.0) {
        colour = mix(uHorizon.rgb, uZenith.rgb, pow(height, 0.6));
    } else {
        // Below the horizon: the horizon colour, dimming downwards, as the void
        // under vanilla's sky does.
        colour = mix(uHorizon.rgb, uHorizon.rgb * 0.35, clamp(-height * 3.0, 0.0, 1.0));
    }
    colour += uHorizon.rgb * 0.25 * exp(-abs(height) * 18.0);

    if (uStars > 0.0) {
        colour += stars(direction) * uStars;
    }
    fragColor = vec4(colour, 1.0);
}
