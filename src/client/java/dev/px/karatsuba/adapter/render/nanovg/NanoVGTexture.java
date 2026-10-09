package dev.px.karatsuba.adapter.render.nanovg;

import dev.px.core.render.Texture;
import dev.px.karatsuba.adapter.render.shader.GlTexture;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * An image uploaded into the NanoVG context.
 *
 * <p>NanoVG keeps its own GL texture rather than borrowing Minecraft's, so an
 * {@code Identifier} is not enough &mdash; the bytes have to be decoded and handed
 * over once. {@link #load(NanoVGRender2D, InputStream)} does that from anything on
 * the classpath, which covers icons bundled in the mod jar.
 */
public final class NanoVGTexture implements Texture, GlTexture {

    private final long context;
    private final int width;
    private final int height;

    private int image;

    private NanoVGTexture(long context, int image, int width, int height) {
        this.context = context;
        this.image = image;
        this.width = width;
        this.height = height;
    }

    /**
     * Decodes a PNG, JPEG or other stb-readable image and uploads it.
     *
     * @throws IOException if the stream cannot be read or the bytes are not an image
     */
    public static NanoVGTexture load(NanoVGRender2D backend, InputStream source) throws IOException {
        byte[] bytes = readAll(source);

        ByteBuffer encoded = MemoryUtil.memAlloc(bytes.length);
        try {
            encoded.put(bytes).flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                int[] width = new int[1];
                int[] height = new int[1];
                int[] channels = new int[1];

                ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
                if (pixels == null) {
                    throw new IOException("stb could not decode the image: " + STBImage.stbi_failure_reason());
                }
                try {
                    int image = NanoVG.nvgCreateImageRGBA(backend.handle(), width[0], height[0], 0, pixels);
                    if (image == 0) {
                        throw new IOException("NanoVG rejected the decoded image");
                    }
                    return new NanoVGTexture(backend.handle(), image, width[0], height[0]);
                } finally {
                    // nvgCreateImageRGBA copies into its own GL texture, so the
                    // decoded pixels are ours to release immediately.
                    STBImage.stbi_image_free(pixels);
                }
            }
        } finally {
            MemoryUtil.memFree(encoded);
        }
    }

    private static byte[] readAll(InputStream source) throws IOException {
        try (InputStream stream = source) {
            return stream.readAllBytes();
        }
    }

    /** The NanoVG image id, for {@code nvgImagePattern}. */
    int image() {
        return image;
    }

    /**
     * The GL texture behind the NanoVG image, so a shader can sample an image loaded
     * for the HUD. Rows are stored top first, so v = 0 is the top of the image.
     */
    @Override
    public int glTextureId() {
        return image == 0 ? 0 : NanoVGGL3.nvglImageHandle(context, image);
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public boolean isValid() {
        return image != 0;
    }

    @Override
    public void dispose() {
        if (image != 0) {
            NanoVG.nvgDeleteImage(context, image);
            image = 0;
        }
    }
}
