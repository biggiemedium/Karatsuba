package dev.px.karatsuba.client.gui.stable.shell.section.home;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.stable.shell.style.Motion;

/**
 * A search result on Home: what it is, where it lives ("Settings · Interaction"),
 * and a press away from being shown there.
 */
public final class ResultRow extends Widget {

    private final String name;
    private final String where;
    private final Runnable open;
    private final Motion hover = Motion.hover();

    public ResultRow(String name, String where, Runnable open) {
        this.name = name;
        this.where = where;
        this.open = open;
    }

    public String name() {
        return name;
    }

    public String where() {
        return where;
    }

    public float hover() {
        return hover.toward(isHovered() || isFocused());
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        open.run();
        return true;
    }

    @Override
    protected boolean activated() {
        open.run();
        return true;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
