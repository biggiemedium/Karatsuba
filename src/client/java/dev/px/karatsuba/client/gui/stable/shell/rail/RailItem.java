package dev.px.karatsuba.client.gui.stable.shell.rail;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.stable.shell.Section;
import dev.px.karatsuba.client.gui.stable.shell.Shell;
import dev.px.karatsuba.client.gui.stable.shell.style.Motion;

/**
 * One section on the rail. The one showing has two signs, not one: a pill in
 * the accent behind its icon, and its name brighter. Pressing the one already
 * showing does nothing; the tooltip names the keys that get here, Ctrl and the
 * item's number.
 */
public final class RailItem extends Widget {

    private final Section section;
    private final Shell shell;
    private final Rail rail;
    private final int number;

    private final Motion hover = Motion.hover();
    private final Motion chosen;

    public RailItem(Section section, Shell shell, Rail rail, int number) {
        this.section = section;
        this.shell = shell;
        this.rail = rail;
        this.number = number;
        this.chosen = Motion.toggle(shell.active() == section);
        tooltip(() -> number <= 9 ? section.title() + "    Ctrl+" + number : section.title());
    }

    public Section section() {
        return section;
    }

    public boolean labelled() {
        return rail.isLabelled();
    }

    public boolean isChosen() {
        return shell.active() == section;
    }

    public float hover() {
        return hover.toward(isHovered() && !isChosen());
    }

    public float chosen() {
        return chosen.toward(isChosen());
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        shell.show(section.id());
        return true;
    }

    @Override
    protected boolean activated() {
        shell.show(section.id());
        return true;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
