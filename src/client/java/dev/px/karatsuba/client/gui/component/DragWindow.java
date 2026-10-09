package dev.px.karatsuba.client.gui.component;

import dev.px.gui.legacy.GuiStyle;
import dev.px.gui.legacy.Panel;
import dev.px.gui.legacy.Screen;
import dev.px.core.input.MouseButton;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.render.Render;

/**
 * A titled window that stacks its rows under a title bar: left-drag the title to
 * move it, right-click the title to fold it away.
 *
 * <p>Core's {@code CategoryWindow} does this for a category of modules and is
 * welded to that job; this is the same window holding anything. Its position is
 * its own, so a screen that keeps the window across openings keeps where it was
 * dragged.
 */
public final class DragWindow extends Panel {

    private final String title;

    private float x;
    private float y;
    private boolean collapsed;

    /** Cursor position relative to the window's corner when the drag began. */
    private float grabX;
    private float grabY;

    public DragWindow(String title, float x, float y) {
        this.title = title;
        this.x = x;
        this.y = y;
        setBackground(true);
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    /** Where the window is, for a screen to lay it out at. */
    public void moveTo(float newX, float newY) {
        this.x = newX;
        this.y = newY;
    }

    @Override
    protected boolean showsChildren() {
        return !collapsed;
    }

    /** The title bar; its measured height is what the panel reserves above the rows. */
    @Override
    protected void content(Content c) {
        float height = GuiStyle.titleHeight();
        c.height(height).padding(GuiStyle.padding(), 0f).align(Align.CENTER);
        c.backdrop((bx, by, w, h) -> Render.roundGradient(bx, by, w, h,
                Math.min(GuiStyle.radius(), height / 2f),
                GuiStyle.accent(0f), GuiStyle.accent(1f),
                GuiStyle.accent(1f), GuiStyle.accent(0f)));
        c.text(title, GuiStyle.text());
    }

    @Override
    protected boolean onClick(float pointerX, float pointerY, MouseButton button) {
        // Only the title bar is grabbable; a press below it was declined by a row.
        if (pointerY > getBounds().getY() + GuiStyle.titleHeight()) {
            return false;
        }
        if (button == MouseButton.RIGHT) {
            collapsed = !collapsed;
            return true;
        }
        if (button != MouseButton.LEFT) {
            return false;
        }
        grabX = pointerX - x;
        grabY = pointerY - y;
        Screen screen = getScreen();
        if (screen != null) {
            screen.beginDrag(this);
        }
        return true;
    }

    @Override
    protected void onDrag(float pointerX, float pointerY) {
        this.x = pointerX - grabX;
        this.y = pointerY - grabY;
    }
}
