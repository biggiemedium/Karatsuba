package dev.px.karatsuba.client.gui.shell.frame;

import dev.px.core.input.MouseButton;
import dev.px.core.layout.Bounds;
import dev.px.core.layout.Size;
import dev.px.gui.Container;
import dev.px.gui.Widget;

import java.util.List;

/**
 * The GUI's window: exactly the size its {@link WindowFrame} says, whatever is
 * inside it.
 *
 * <p>A column is as tall as its content, so a long settings list would grow the
 * window off the screen. This one holds its size, so the lists inside get exactly
 * the room that is left and scroll the rest. Its first child fills it; the
 * {@link Floating} ones (resize grips, veils) go where they ask, over it.
 */
public final class Panel extends Container {

    private final WindowFrame frame;

    public Panel(WindowFrame frame) {
        this.frame = frame;
    }

    public WindowFrame frame() {
        return frame;
    }

    @Override
    protected float childrenHeight(List<Widget> visible, float available) {
        return frame.getHeight();
    }

    @Override
    protected Size childrenNatural(List<Widget> visible) {
        return Size.of(frame.getWidth(), frame.getHeight());
    }

    @Override
    protected void arrange(List<Widget> visible, Bounds slot) {
        // The body first, so the veils can read where its columns landed.
        for (Widget child : visible) {
            if (!(child instanceof Floating)) {
                place(child, slot);
            }
        }
        for (Widget child : visible) {
            if (child instanceof Floating floating) {
                place(child, floating.placeIn(slot));
            }
        }
    }

    /** Presses that land on the window's own background stop here rather than reaching the game. */
    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        return true;
    }
}
