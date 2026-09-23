package dev.px.karatsuba.adapter.render.nanovg;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Saves and restores every piece of GL state NanoVG touches.
 *
 * <p>Minecraft's {@code RenderSystem} caches what it believes the GL state to be
 * and skips redundant calls. NanoVG goes straight to the driver, so after a frame
 * of it the cache is a lie, and the next {@code RenderSystem.enableBlend()} that
 * should fix things is skipped as a no-op.
 *
 * <p>Reading the real values back with {@code glGet} and writing them back with raw
 * GL avoids the problem instead of fighting it: the cache is never wrong, because
 * by the time Minecraft draws again the driver state is exactly what it was. The
 * {@code glGet} calls stall the pipeline, which is why this happens twice a frame
 * and not twice a draw call.
 */
final class GlStateGuard {

    private int program;
    private int vertexArray;
    private int arrayBuffer;
    private int elementBuffer;
    private int activeTexture;
    private int texture2d;

    private boolean blend;
    private boolean cull;
    private boolean depthTest;
    private boolean stencilTest;
    private boolean scissorTest;
    private boolean depthMask;

    private int blendSrcRgb;
    private int blendDstRgb;
    private int blendSrcAlpha;
    private int blendDstAlpha;
    private int cullMode;
    private int frontFace;

    private final int[] colorMask = new int[4];

    void save() {
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        elementBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);

        // The 2D binding is per texture unit, so unit 0 has to be current to read
        // the one NanoVG is about to replace.
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        texture2d = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        stencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        cullMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        frontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);

        GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, colorMask);
    }

    void restore() {
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2d);
        GL13.glActiveTexture(activeTexture);

        toggle(GL11.GL_BLEND, blend);
        toggle(GL11.GL_CULL_FACE, cull);
        toggle(GL11.GL_DEPTH_TEST, depthTest);
        toggle(GL11.GL_STENCIL_TEST, stencilTest);
        toggle(GL11.GL_SCISSOR_TEST, scissorTest);

        GL11.glDepthMask(depthMask);
        GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        GL11.glCullFace(cullMode);
        GL11.glFrontFace(frontFace);
        GL11.glColorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
    }

    private static void toggle(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }
}
