package dev.px.karatsuba.client.gui.stable.shell.frame;

import dev.px.core.layout.Bounds;
import dev.px.karatsuba.client.gui.stable.shell.style.Palette;

/**
 * Where the GUI's window is and how big: moved by dragging the sidebar, resized
 * by its right and bottom edges and its corner, and kept on screen whatever the
 * screen does.
 *
 * <p>Saved through a {@link Store} when a drag ends, so the window opens where it
 * was left. Until it has been moved or resized it sits in the middle of the
 * screen at its default size, and a right click on the corner puts it back there.
 */
public final class WindowFrame {

    /** Keeps the window's place between openings and sessions. */
    public interface Store {

        /** @return x, y, width and height, or null if it was never moved */
        float[] load();

        void save(float x, float y, float width, float height);

        void clear();
    }

    /** How close to the screen's edge the window may go. */
    private static final float MARGIN = 4f;

    private final Store store;

    private float x;
    private float y;
    private float width;
    private float height;

    /** Whether the window has a place of its own, rather than the middle of the screen. */
    private boolean placed;
    private boolean loaded;

    private float screenWidth;
    private float screenHeight;

    public WindowFrame(Store store) {
        this.store = store;
    }

    /** Fits the window to the screen, once a frame, before anything reads it. */
    public void fit(float newScreenWidth, float newScreenHeight) {
        screenWidth = newScreenWidth;
        screenHeight = newScreenHeight;
        if (!loaded) {
            loaded = true;
            float[] saved = store == null ? null : store.load();
            if (saved != null) {
                x = saved[0];
                y = saved[1];
                width = saved[2];
                height = saved[3];
                placed = true;
            }
        }
        if (!placed) {
            width = Math.min(Palette.FRAME_WIDTH, screenWidth - 32f);
            height = Math.min(Palette.FRAME_HEIGHT, screenHeight - 32f);
        }
        width = clamp(width, Math.min(Palette.FRAME_MIN_WIDTH, maxWidth()), maxWidth());
        height = clamp(height, Math.min(Palette.FRAME_MIN_HEIGHT, maxHeight()), maxHeight());
        if (!placed) {
            x = (screenWidth - width) / 2f;
            y = (screenHeight - height) / 2f;
        }
        x = clamp(x, MARGIN, screenWidth - width - MARGIN);
        y = clamp(y, MARGIN, screenHeight - height - MARGIN);
    }

    public Bounds bounds() {
        return Bounds.of(x, y, width, height);
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    /** Moves the window's top left corner to {@code (left, top)}, kept on screen. */
    public void moveTo(float left, float top) {
        placed = true;
        x = clamp(left, MARGIN, screenWidth - width - MARGIN);
        y = clamp(top, MARGIN, screenHeight - height - MARGIN);
    }

    /** Resizes from the top left corner, no smaller than the minimum and no larger than fits. */
    public void resizeTo(float newWidth, float newHeight) {
        placed = true;
        width = clamp(newWidth, Palette.FRAME_MIN_WIDTH, screenWidth - x - MARGIN);
        height = clamp(newHeight, Palette.FRAME_MIN_HEIGHT, screenHeight - y - MARGIN);
    }

    /** Remembers the window's place, at the end of a drag. */
    public void save() {
        if (store != null && placed) {
            store.save(x, y, width, height);
        }
    }

    /** Back to the default size, in the middle of the screen. */
    public void reset() {
        placed = false;
        if (store != null) {
            store.clear();
        }
        fit(screenWidth, screenHeight);
    }

    private float maxWidth() {
        return Math.max(1f, screenWidth - MARGIN * 2f);
    }

    private float maxHeight() {
        return Math.max(1f, screenHeight - MARGIN * 2f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(Math.max(min, max), value));
    }
}
