package dev.px.karatsuba.adapter.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * The fixed-function state a pass changes, read before and put back after.
 *
 * <p>Read with {@code glGet} and written through {@code RenderSystem}, both ways.
 * {@code RenderSystem} skips a call its cache says is redundant, so changing state
 * behind it (as NanoVG does, hence its own guard) would leave the cache wrong. Going
 * through it for the change and for the restore keeps the cache and the driver in
 * step the whole time. The {@code glGet}s stall, which is fine at one pass's scale.
 */
final class PassState {

    private boolean blend;
    private boolean depthTest;
    private boolean cull;
    private boolean depthMask;
    private int depthFunc;
    private int srcRgb;
    private int dstRgb;
    private int srcAlpha;
    private int dstAlpha;

    void save() {
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    }

    /** Straight alpha over what is there, no culling, no depth writes. */
    static void blended(boolean depthTested) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        if (depthTested) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        } else {
            RenderSystem.disableDepthTest();
        }
    }

    void restore() {
        if (blend) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        if (cull) {
            RenderSystem.enableCull();
        } else {
            RenderSystem.disableCull();
        }
        RenderSystem.depthMask(depthMask);
        RenderSystem.depthFunc(depthFunc);
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
    }
}
