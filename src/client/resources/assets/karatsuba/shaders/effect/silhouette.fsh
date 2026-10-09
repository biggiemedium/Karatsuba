#version 150

// Fill, outline or glow around whatever Silhouettes drew into uMask.
// Drawn with ShaderPasses.overlay, so vUv addresses the mask directly.

in vec2 vUv;

uniform sampler2D uMask;
uniform vec2 uViewport;
uniform float uTime;

uniform vec4 uColorA;
uniform vec4 uColorB;
// 0: everything in uColorA. 1: a slow wave between uColorA and uColorB.
uniform float uGradient;
// 0: a hard outline uWidth wide. 1: a glow fading out over uWidth.
uniform int uMode;
// In pixels at 1080p; scaled with the framebuffer so it reads the same at any size.
uniform float uWidth;
uniform float uFillOpacity;
uniform float uEdgeOpacity;

out vec4 fragColor;

const int GLOW_RAYS = 16;
const float TAU = 6.2831853;

vec3 tint() {
    if (uGradient <= 0.0) {
        return uColorA.rgb;
    }
    float wave = 0.5 + 0.5 * sin((gl_FragCoord.x + gl_FragCoord.y) / uViewport.y * 4.0 - uTime * 2.0);
    return mix(uColorA.rgb, uColorB.rgb, wave);
}

bool covered(vec2 offsetPixels) {
    return texture(uMask, vUv + offsetPixels / uViewport).a > 0.0;
}

// Pixels to the nearest covered pixel within width, searched over the whole disc,
// or -1. Exact, so thin parts (a sword, a bow string) still get their edge.
float discDistance(float width) {
    int reach = int(ceil(width));
    float limit = width * width;
    float best = -1.0;
    for (int x = -reach; x <= reach; x++) {
        for (int y = -reach; y <= reach; y++) {
            float squared = float(x * x + y * y);
            if (squared > limit || (best >= 0.0 && squared >= best)) {
                continue;
            }
            if (covered(vec2(x, y))) {
                best = squared;
            }
        }
    }
    return best < 0.0 ? -1.0 : sqrt(best);
}

// The same along a ring of rays, for the wide reach a glow wants: a disc search
// that wide is hundreds of samples a pixel, and a soft glow hides the gaps.
float rayDistance(float width) {
    float best = -1.0;
    for (int i = 0; i < GLOW_RAYS; i++) {
        float angle = TAU * float(i) / float(GLOW_RAYS);
        vec2 direction = vec2(cos(angle), sin(angle));
        for (float step = 1.0; step <= width; step += 1.0) {
            if (best >= 0.0 && step >= best) {
                break;
            }
            if (covered(direction * step)) {
                best = step;
                break;
            }
        }
    }
    return best;
}

void main() {
    vec3 colour = tint();

    if (texture(uMask, vUv).a > 0.0) {
        if (uFillOpacity <= 0.0) {
            discard;
        }
        fragColor = vec4(colour, uFillOpacity);
        return;
    }
    if (uEdgeOpacity <= 0.0) {
        discard;
    }

    float width = uWidth * max(1.0, uViewport.y / 1080.0);
    if (uMode == 1) {
        float distance = rayDistance(width);
        if (distance < 0.0) {
            discard;
        }
        float fade = 1.0 - (distance - 1.0) / max(width, 1.0);
        fragColor = vec4(colour, uEdgeOpacity * fade * fade);
    } else {
        if (discDistance(width) < 0.0) {
            discard;
        }
        fragColor = vec4(colour, uEdgeOpacity);
    }
}
