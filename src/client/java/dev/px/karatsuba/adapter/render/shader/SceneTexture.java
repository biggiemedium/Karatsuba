package dev.px.karatsuba.adapter.render.shader;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.px.core.render.Texture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A copy of what is on screen, as a texture a shader can sample: for blur, glass,
 * distortion and anything else that reads the picture behind it.
 *
 * <p>A copy, not the screen itself. Sampling the texture a draw is writing to is a
 * feedback loop, which GL leaves undefined. So {@link #capture()} blits the game's
 * main framebuffer into one of our own, and that is what gets sampled.
 *
 * <p>Sample it by fragment position rather than by the quad's UV, since the copy is
 * the whole window and GL's origin is the bottom left:
 * {@code texture(uScene, gl_FragCoord.xy / uViewport)}.
 *
 * <p>One texture shared by every caller and kept between frames, resized with the
 * window. Each capture overwrites the last, so capture inside the pass that samples
 * it ({@link ShaderPasses} runs uniform providers after the 2D frame has been
 * painted so far, which is the moment to take it).
 *
 * <p><b>Render thread only.</b>
 */
public final class SceneTexture implements Texture, GlTexture {

    private static final SceneTexture INSTANCE = new SceneTexture();

    private Framebuffer copy;

    private SceneTexture() {
    }

    /**
     * Copies the main framebuffer's colour into the shared texture.
     *
     * @return the texture, holding the screen as it is now
     */
    public static Texture capture() {
        RenderSystem.assertOnRenderThread();
        INSTANCE.blit(MinecraftClient.getInstance().getFramebuffer());
        return INSTANCE;
    }

    private void blit(Framebuffer main) {
        int width = main.textureWidth;
        int height = main.textureHeight;
        if (copy == null) {
            copy = new SimpleFramebuffer(width, height, false);
            copy.setTexFilter(GL11.GL_LINEAR);
        } else if (copy.textureWidth != width || copy.textureHeight != height) {
            copy.resize(width, height);
        }

        // Through GlStateManager, which caches both bindings; put back exactly what
        // was bound so the draw that follows lands where it would have.
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.fbo);
        GlStateManager._glBlitFrameBuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
    }

    /** Frees the copy. From {@code MinecraftAdapter.shutdown}, on the render thread. */
    public static void release() {
        if (INSTANCE.copy != null) {
            INSTANCE.copy.delete();
            INSTANCE.copy = null;
        }
    }

    @Override
    public int glTextureId() {
        return copy == null ? 0 : copy.getColorAttachment();
    }

    @Override
    public int getWidth() {
        return copy == null ? 0 : copy.textureWidth;
    }

    @Override
    public int getHeight() {
        return copy == null ? 0 : copy.textureHeight;
    }

    @Override
    public boolean isValid() {
        return copy != null;
    }

    /** Shared by every caller, so disposing one handle does nothing; see {@link #release()}. */
    @Override
    public void dispose() {
    }
}
