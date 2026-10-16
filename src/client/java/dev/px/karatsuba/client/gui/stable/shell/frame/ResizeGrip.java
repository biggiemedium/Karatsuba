package dev.px.karatsuba.client.gui.stable.shell.frame;

import dev.px.core.input.MouseButton;
import dev.px.core.layout.Bounds;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.stable.shell.style.Motion;

/**
 * A handle for resizing the window: its right edge, its bottom edge, or the
 * corner between them. Dragging resizes from the top left, which stays put; a
 * right click on the corner puts the window back to its default size.
 */
public final class ResizeGrip extends Widget implements Floating {

    public enum Edge {
        RIGHT, BOTTOM, CORNER
    }

    /** How thick an edge handle is, and how big the corner. */
    private static final float EDGE = 5f;
    private static final float CORNER = 12f;

    private final WindowFrame frame;
    private final Edge edge;
    private final Motion hover = Motion.hover();

    private float startMouseX;
    private float startMouseY;
    private float startWidth;
    private float startHeight;

    public ResizeGrip(WindowFrame frame, Edge edge) {
        this.frame = frame;
        this.edge = edge;
        if (edge == Edge.CORNER) {
            tooltip("Drag to resize. Right click for the default size.");
        }
    }

    public Edge edge() {
        return edge;
    }

    /** @return the hover highlight, 0 to 1, lit while dragging too */
    public float lit() {
        return hover.toward(isHovered() || isPressed());
    }

    @Override
    public Bounds placeIn(Bounds window) {
        switch (edge) {
            case RIGHT:
                return Bounds.of(window.getRight() - EDGE, window.getY() + CORNER,
                        EDGE, window.getHeight() - CORNER * 2f);
            case BOTTOM:
                return Bounds.of(window.getX() + CORNER, window.getBottom() - EDGE,
                        window.getWidth() - CORNER * 2f, EDGE);
            default:
                return Bounds.of(window.getRight() - CORNER, window.getBottom() - CORNER, CORNER, CORNER);
        }
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button == MouseButton.RIGHT && edge == Edge.CORNER) {
            frame.reset();
            return true;
        }
        if (button != MouseButton.LEFT) {
            return false;
        }
        startMouseX = x;
        startMouseY = y;
        startWidth = frame.getWidth();
        startHeight = frame.getHeight();
        capture();
        return true;
    }

    @Override
    protected void mouseDragged(float x, float y) {
        float width = edge == Edge.BOTTOM ? startWidth : startWidth + (x - startMouseX);
        float height = edge == Edge.RIGHT ? startHeight : startHeight + (y - startMouseY);
        frame.resizeTo(width, height);
    }

    @Override
    protected void mouseReleased(float x, float y, MouseButton button) {
        frame.save();
    }
}
