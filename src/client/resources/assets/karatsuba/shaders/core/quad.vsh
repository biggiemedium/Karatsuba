#version 150

// The vertex stage every ShaderPasses pass fits: rect, screen and sky.
// Attribute names and locations are bound by GlShaderBackend (0, 1, 2).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

// Scaled screen units to clip space for 2D passes; identity for sky.
uniform mat4 uProjection;

out vec2 vUv;
out vec4 vColor;

void main() {
    gl_Position = uProjection * vec4(Position, 1.0);
    vUv = UV0;
    vColor = Color;
}
