#version 150

// Night in deep space: stars at three depths, the band of a galaxy with a bright
// core and dark dust lanes, and nebula clouds in two colours. The whole sky turns
// slowly about a tilted pole. Drawn with ShaderPasses.sky in place of the vanilla sky.

#include "../../core/sky.glsl"
#include "../../core/stars.glsl"

in vec2 vUv;

uniform float uTime;
uniform vec4 uNebulaA;
uniform vec4 uNebulaB;
// 0 hides the nebula; 1 is full strength.
uniform float uNebula;
// 0 hides the galaxy; 1 shows it.
uniform float uGalaxy;
// The star count against the default; 0 hides them.
uniform float uStarDensity;
// How fast the sky turns: at 1, once round in about ten minutes.
uniform float uSpeed;

out vec4 fragColor;

// The pole the sky turns about, 15 degrees off vertical.
const vec3 POLE = vec3(0.2588, 0.9659, 0.0);
// The galaxy's plane, by its normal, and the direction of its core (in that plane).
const vec3 GALAXY = vec3(0.3995, 0.7990, 0.4494);
const vec3 CORE = vec3(-0.8944, 0.4472, 0.0);

mat3 turn(vec3 axis, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    float k = 1.0 - c;
    return mat3(
            c + axis.x * axis.x * k, axis.y * axis.x * k + axis.z * s, axis.z * axis.x * k - axis.y * s,
            axis.x * axis.y * k - axis.z * s, c + axis.y * axis.y * k, axis.z * axis.y * k + axis.x * s,
            axis.x * axis.z * k + axis.y * s, axis.y * axis.z * k - axis.x * s, c + axis.z * axis.z * k);
}

void main() {
    vec3 direction = turn(POLE, uTime * uSpeed * 0.0105) * skyDirection(vUv);

    // Never quite black: a deep blue, lifted a little along the galaxy.
    float plane = dot(direction, GALAXY);
    float band = exp(-plane * plane * 18.0) * uGalaxy;
    vec3 colour = vec3(0.006, 0.008, 0.022) + vec3(0.01, 0.012, 0.03) * band;

    if (uNebula > 0.0) {
        vec3 p = direction * 2.3;
        // Domain-warped: the clouds billow and stream instead of sitting in blobs.
        vec2 warp = vec2(fbm(p + vec3(1.7, 9.2, 0.0)), fbm(p + vec3(8.3, 2.8, 5.1)));
        float cloud = fbm(p * 1.3 + vec3(warp, warp.x * warp.y) * 2.6);
        float density = smoothstep(0.42, 0.78, cloud);
        vec3 tint = mix(uNebulaA.rgb, uNebulaB.rgb, smoothstep(0.35, 0.65, warp.x));
        colour += tint * (density * density * 0.35 + pow(density, 5.0) * 0.5) * uNebula;
    }

    float lanes = 0.0;
    if (uGalaxy > 0.0) {
        float glow = band * (0.35 + 0.65 * fbm(direction * 3.2 + 11.0));
        float core = pow(max(dot(direction, CORE), 0.0), 10.0) * exp(-plane * plane * 40.0);
        lanes = smoothstep(0.48, 0.66, fbm(direction * 7.0 + 3.1)) * exp(-plane * plane * 70.0);
        // Cool blue-white along the band, warming to gold at the core.
        vec3 tint = mix(vec3(0.55, 0.62, 0.85), vec3(1.0, 0.86, 0.7), clamp(core * 1.5 + band * 0.2, 0.0, 1.0));
        colour += tint * (glow * 0.16 + core * 0.45) * (1.0 - lanes * 0.85);
    }

    // More stars along the galaxy; fewer through its dust.
    colour += stars(direction, uStarDensity * (1.0 + band * 1.5), uTime) * (1.0 - lanes * 0.7);
    fragColor = vec4(colour, 1.0);
}
