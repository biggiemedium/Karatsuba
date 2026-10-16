package dev.px.karatsuba.client.gui.apple.shell.header;

import dev.px.core.input.MouseButton;
import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.core.layout.Size;
import dev.px.gui.Container;
import dev.px.gui.Widget;
import dev.px.gui.container.Row;
import dev.px.karatsuba.client.gui.apple.shell.Shell;
import dev.px.karatsuba.client.gui.apple.shell.frame.WindowFrame;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;

import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * The window's top area ({@code gui/apple/DESIGN.md} §2.3): ‹ › in a small glass
 * capsule, then the page title as plain text, all aligned with the content's left
 * edge. No bar of its own behind them.
 *
 * <p>Holds its size: {@link Palette#HEADER_HEIGHT} tall and as wide as the content
 * column. Dragging it anywhere that is not a button moves the window, as a title
 * bar does.
 */
public final class HeaderBar extends Container {

    private final WindowFrame frame;
    private final DoubleSupplier width;
    private final NavCapsule capsule;

    private float grabX;
    private float grabY;

    /** @param width how wide it is each frame: the content column's width */
    public HeaderBar(Shell shell, WindowFrame frame, DoubleSupplier width) {
        this.frame = frame;
        this.width = width;
        Row row = add(new Row().gap(10f).align(Align.CENTER));
        this.capsule = row.add(new NavCapsule(shell));
        row.add(new HeaderTitle(() -> shell.active().heading())).grow(1f);
    }

    /** @return the ‹ › capsule, whose glass the host draws */
    public NavCapsule capsule() {
        return capsule;
    }

    public float width() {
        return (float) Math.max(0d, width.getAsDouble());
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected float childrenHeight(List<Widget> visible, float available) {
        return Palette.HEADER_HEIGHT;
    }

    @Override
    protected Size childrenNatural(List<Widget> visible) {
        return Size.of(width(), Palette.HEADER_HEIGHT);
    }

    @Override
    protected void arrange(List<Widget> visible, Bounds slot) {
        for (Widget child : visible) {
            place(child, slot);
        }
    }

    // ----------------------------------------------------------------- input

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
