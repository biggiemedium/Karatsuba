package dev.px.karatsuba.client.gui.apple.shell.setting;

import java.util.function.Supplier;

/**
 * A row that opens a page ({@code gui/apple/DESIGN.md} §2.5): its name, a value
 * in secondary text (how many settings are changed, say), and a ›. The whole row
 * is the target.
 */
public final class LinkRow extends ControlRow {

    private final String name;
    private final Supplier<String> value;
    private final Runnable open;

    public LinkRow(String name, Supplier<String> value, Runnable open) {
        this.name = name;
        this.value = value;
        this.open = open;
    }

    @Override
    public String name() {
        return name;
    }

    public String value() {
        return value.get();
    }

    @Override
    protected boolean pressed(float x, float y) {
        open.run();
        return true;
    }

    @Override
    protected boolean activated() {
        open.run();
        return true;
    }

    /** Nothing to put back: the row goes somewhere, it holds no value. */
    @Override
    protected void reset() {
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
