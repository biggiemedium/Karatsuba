package dev.px.karatsuba.client.gui.stable.shell.frame;

import dev.px.core.input.MouseButton;
import dev.px.gui.container.Column;
import dev.px.karatsuba.client.gui.stable.shell.style.Palette;

/**
 * The categories, on the left, and the window's handle: dragging anywhere on
 * the sidebar that is not a button moves the window.
 *
 * <p>Its width is set each frame, narrower in a small window.
 */
public final class Sidebar extends Column {

    private final WindowFrame frame;
    private float width = Palette.SIDEBAR_WIDTH;

    private float grabX;
    private float grabY;

    public Sidebar(WindowFrame frame) {
        this.frame = frame;
    }

    public float width() {
        return width;
    }

    public void width(float newWidth) {
        this.width = newWidth;
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        grabX = x - frame.getX();
        grabY = y - frame.getY();
        capture();
        return true;
    }

    @Override
    protected void mouseDragged(float x, float y) {
        frame.moveTo(x - grabX, y - grabY);
    }

    @Override
    protected void mouseReleased(float x, float y, MouseButton button) {
        frame.save();
    }
}
