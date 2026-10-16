package dev.px.karatsuba.client.gui.apple.shell.widget;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;

/**
 * What a page shows with nothing in it ({@code gui/apple/DESIGN.md} §2.8):
 * centred, an icon, a short title, one sentence saying why it is empty and what
 * to do, and one primary button that does it.
 */
public final class EmptyState extends Widget {

    private final Glyph glyph;
    private final String title;
    private final String sentence;
    private final String button;
    private final Runnable action;

    public EmptyState(Glyph glyph, String title, String sentence, String button, Runnable action) {
        this.glyph = glyph;
        this.title = title;
        this.sentence = sentence;
        this.button = button;
        this.action = action;
    }

    public Glyph glyph() {
        return glyph;
    }

    public String title() {
        return title;
    }

    public String sentence() {
        return sentence;
    }

    public String button() {
        return button;
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton pressed) {
        var part = part("button");
        if (pressed != MouseButton.LEFT || part == null || !part.contains(x, y)) {
            return false;
        }
        action.run();
        return true;
    }

    @Override
    protected boolean activated() {
        action.run();
        return true;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
