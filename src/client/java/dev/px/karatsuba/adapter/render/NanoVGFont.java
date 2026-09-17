package dev.px.karatsuba.adapter.render;

import dev.px.core.render.font.Font;
import org.lwjgl.nanovg.NanoVG;

import java.nio.ByteBuffer;

/**
 * A typeface registered with the NanoVG context.
 *
 * <p>Core measures text far more often than it draws it &mdash; every HUD element
 * sizes itself from {@code textWidth} each frame &mdash; so measurement goes
 * straight to {@code nvgTextBounds} rather than through the render facade. That
 * works outside a frame, which is what lets an element size itself before anything
 * has been drawn.
 *
 * <p>An instance with {@link #isValid()} false is the honest answer to "no font
 * could be loaded": it measures as zero and draws nothing, which is exactly the
 * behaviour Core already tolerates from a backend with no font installed.
 */
public final class NanoVGFont implements Font {

    /** nvgCreateFontMem's failure value. */
    static final int NO_HANDLE = -1;

    private final long context;
    private final String name;
    private final float size;
    private final int handle;

    /**
     * NanoVG does not copy the TTF bytes, it keeps the pointer. Dropping this
     * reference would let the GC free the buffer out from under the rasteriser.
     */
    @SuppressWarnings("unused")
    private final ByteBuffer retainedData;

    private float height = -1f;
    private float lineHeight = -1f;

    NanoVGFont(long context, String name, float size, int handle, ByteBuffer retainedData) {
        this.context = context;
        this.name = name;
        this.size = size;
        this.handle = handle;
        this.retainedData = retainedData;
    }

    /** @return whether a typeface actually loaded; false means every draw is dropped. */
    public boolean isValid() {
        return handle != NO_HANDLE;
    }

    /** Makes this the active face for the next text or measurement call. */
    void apply() {
        NanoVG.nvgFontFaceId(context, handle);
        NanoVG.nvgFontSize(context, size);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getSize() {
        return size;
    }

    @Override
    public float widthOf(String text) {
        if (!isValid() || text == null || text.isEmpty()) {
            return 0f;
        }
        apply();
        // The return value is the horizontal advance, which is what a caret and a
        // layout want. The bounds array is the inked extent, which is narrower.
        return NanoVG.nvgTextBounds(context, 0f, 0f, text, (float[]) null);
    }

    @Override
    public float getHeight() {
        measureOnce();
        return height;
    }

    @Override
    public float getLineHeight() {
        measureOnce();
        return lineHeight;
    }

    /** Metrics depend only on the face and the size, both fixed, so this runs once. */
    private void measureOnce() {
        if (height >= 0f) {
            return;
        }
        if (!isValid()) {
            height = 0f;
            lineHeight = 0f;
            return;
        }
        apply();
        float[] ascender = new float[1];
        float[] descender = new float[1];
        float[] lineh = new float[1];
        NanoVG.nvgTextMetrics(context, ascender, descender, lineh);
        height = ascender[0] - descender[0];
        lineHeight = lineh[0];
    }

    @Override
    public String toString() {
        return name + "@" + size + (isValid() ? "" : " (unloaded)");
    }
}
