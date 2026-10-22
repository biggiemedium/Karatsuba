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

    private static final SceneTexture INSTANCE = new SceneTexture(false);

    /** A second copy with its mip chain built, kept apart so plain captures never sample stale levels. */
    private static final SceneTexture MIPMAPPED = new SceneTexture(true);

    private final boolean mipmapped;
    private Framebuffer copy;

    private SceneTexture(boolean mipmapped) {
        this.mipmapped = mipmapped;
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

    /**
     * {@link #capture()}, with every mip level rebuilt from it, for shaders that blur
     * cheaply by reading a smaller level: {@code textureLod(uScene, uv, level)}.
     */
    public static Texture captureMipmapped() {
        RenderSystem.assertOnRenderThread();
        MIPMAPPED.blit(MinecraftClient.getInstance().getFramebuffer());
        return MIPMAPPED;
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

        if (mipmapped) {
            // The filter goes back to plain LINEAR whenever the framebuffer is resized,
            // so it is set again each time; the levels are allocated by the generate.
            int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager._bindTexture(copy.getColorAttachment());
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager._bindTexture(bound);
        }
    }

    /**
     * The last {@link #captureMipmapped()}, read back at the smallest mip level no
     * narrower than {@code minWidth}: a few dozen pixels across, for judging how
     * bright or busy parts of the screen are (the HUD's adaptive glass). A read back
     * waits for the GPU, so call it sparingly; null before any capture.
     */
    public static Sample sampleMipmapped(int minWidth) {
        RenderSystem.assertOnRenderThread();
        Framebuffer copy = MIPMAPPED.copy;
        if (copy == null) {
            return null;
        }
        int level = 0;
        while ((copy.textureWidth >> (level + 1)) >= minWidth && (copy.textureHeight >> (level + 1)) >= 1) {
            level++;
        }
        int width = Math.max(1, copy.textureWidth >> level);
        int height = Math.max(1, copy.textureHeight >> level);
        java.nio.ByteBuffer pixels = org.lwjgl.BufferUtils.createByteBuffer(width * height * 4);
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        GlStateManager._bindTexture(copy.getColorAttachment());
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
        GlStateManager._bindTexture(bound);
        return new Sample(width, height, pixels);
    }

    /**
     * A small read-back picture of the screen: row 0 is the bottom, as GL keeps it.
     *
     * @param rgba {@code width * height} pixels, four bytes each
     */
    public record Sample(int width, int height, java.nio.ByteBuffer rgba) {

        /** @return the luminance of a pixel, 0 to 1; {@code row} 0 is the top of the screen */
        public float luminance(int column, int row) {
            int x = Math.max(0, Math.min(width - 1, column));
            int y = Math.max(0, Math.min(height - 1, height - 1 - row));
            int at = (y * width + x) * 4;
            float r = (rgba.get(at) & 0xFF) / 255f;
            float g = (rgba.get(at + 1) & 0xFF) / 255f;
            float b = (rgba.get(at + 2) & 0xFF) / 255f;
            return 0.2126f * r + 0.7152f * g + 0.0722f * b;
        }
    }

    /** Frees the copies. From {@code MinecraftAdapter.shutdown}, on the render thread. */
    public static void release() {
        INSTANCE.free();
        MIPMAPPED.free();
    }

    private void free() {
        if (copy != null) {
            copy.delete();
            copy = null;
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
