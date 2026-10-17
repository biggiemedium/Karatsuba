package dev.px.karatsuba.client.gui.apple.shell.sidebar;

import dev.px.core.input.MouseButton;
import dev.px.core.render.Color;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * One place in the sidebar: a coloured icon tile and a name, filled with the
 * accent while it is the one showing ({@code gui/apple/DESIGN.md} §2.2). With the
 * sidebar collapsed it is the icon alone, its name a tooltip.
 */
public final class SidebarEntry extends Widget {

    private final String id;
    private final String label;
    private final Glyph glyph;
    private final BooleanSupplier selected;
    private final Runnable open;
    private final SidebarPane pane;
    private final Motion hover = Motion.hover();

    /**
     * @param id       what it is, for its icon colour ({@link Palette#iconColor})
     * @param selected whether it is the one showing
     * @param open     goes there
     */
    SidebarEntry(SidebarPane pane, String id, String label, Glyph glyph, BooleanSupplier selected, Runnable open) {
        this.pane = pane;
        this.id = id;
        this.label = label;
        this.glyph = glyph;
        this.selected = selected;
        this.open = open;
        visibleWhen(() -> matches(pane.query()));
        tooltip(() -> pane.isCollapsed() ? label : "");
    }

    public String label() {
        return label;
    }

    public Glyph glyph() {
        return glyph;
    }

    public Color iconColor() {
        return Palette.iconColor(id);
    }

    public boolean isSelected() {
        return selected.getAsBoolean();
    }

    public boolean isCollapsed() {
        return pane.isCollapsed();
    }

    public float hover() {
        return hover.toward(isHovered() && !isSelected());
    }

    /** Goes there, as a press would. */
    void go() {
        open.run();
    }

    /** @return whether its name holds the search, ignoring case; everything matches no search */
    boolean matches(String query) {
        return query.isEmpty() || label.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
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
