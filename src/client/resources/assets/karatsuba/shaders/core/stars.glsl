// A starfield on the unit sphere, for sky shaders. #include "../../core/stars.glsl"
// (relative to the including file) after the version line; brings noise.glsl with it.

#include "noise.glsl"

// One depth of stars: at most one per cell of a grid `cells` across, in `chance` of
// the cells. `pixel` is one screen pixel in cell units: a star smaller than that is
// drawn a pixel wide and dimmer instead, so it never flickers in and out.
vec3 starLayer(vec3 direction, float cells, float chance, float time, float pixel) {
    vec3 scaled = direction * cells;
    vec3 cell = floor(scaled);
    vec3 random = hash33(cell);
    if (random.x > chance) {
        return vec3(0.0);
    }
    vec3 jitter = hash33(cell + 19.19) - 0.5;
    // On the sphere, near the cell's middle: a star never reaches the cell's edge.
    vec3 centre = normalize(cell + 0.5 + jitter * 0.4) * cells;
    float distance = length(scaled - centre);

    float size = mix(0.05, 0.18, random.y * random.y);
    float radius = max(size, pixel * 0.75);
    float energy = (size / radius) * (size / radius);
    float shape = 1.0 - smoothstep(0.0, radius, distance);
    float halo = 1.0 - smoothstep(0.0, 0.3, distance);

    float brightness = mix(0.25, 1.0, pow(random.z, 3.0)) * energy;
    float twinkle = 0.75 + 0.25 * sin(time * (1.0 + random.y * 3.0) + random.z * 40.0);
    // Blue-white to warm yellow.
    vec3 tint = mix(vec3(0.7, 0.8, 1.0), vec3(1.0, 0.85, 0.7), jitter.x + 0.5);
    return tint * (shape * shape * 1.6 + halo * halo * 0.08) * brightness * twinkle;
}

// Three depths: a few bright stars, more fainter ones, and a fine dust of the faintest.
// density 1 is the default count, 0 none. Call from uniform control flow (fwidth).
vec3 stars(vec3 direction, float density, float time) {
    float pixel = length(fwidth(direction));
    return starLayer(direction, 90.0, 0.012 * density, time, pixel * 90.0)
            + starLayer(direction, 220.0, 0.02 * density, time, pixel * 220.0) * 0.6
            + starLayer(direction, 480.0, 0.02 * density, time, pixel * 480.0) * 0.35;
}
