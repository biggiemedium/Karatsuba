// For fragment shaders drawn with ShaderPasses.sky. #include "../core/sky.glsl"
// (relative to the including file) after the version line.

uniform mat4 uInverseProjection;
uniform mat4 uInverseView;
uniform vec3 uCameraPosition;

// The world-space direction the camera looks along through this pixel, normalised.
// uv is the sky pass's vUv: (0, 0) bottom left, (1, 1) top right.
vec3 skyDirection(vec2 uv) {
    vec4 view = uInverseProjection * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 direction = (uInverseView * vec4(view.xyz / view.w, 0.0)).xyz;
    return normalize(direction);
}
