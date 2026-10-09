package dev.px.karatsuba.client.gui.shell.setting;

import dev.px.core.hud.Anchor;
import dev.px.karatsuba.client.gui.shell.style.Motion;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Which corner, edge or centre of the screen something holds to, picked on a
 * three-by-three grid that looks like the screen it describes, rather than from
 * a list of nine names.
 */
public final class AnchorRow extends ControlRow {

    private static final Anchor[] GRID = {
            Anchor.TOP_LEFT, Anchor.TOP_CENTER, Anchor.TOP_RIGHT,
            Anchor.MIDDLE_LEFT, Anchor.CENTER, Anchor.MIDDLE_RIGHT,
            Anchor.BOTTOM_LEFT, Anchor.BOTTOM_CENTER, Anchor.BOTTOM_RIGHT
    };

    private final String name;
    private final Supplier<Anchor> value;
    private final Consumer<Anchor> set;
    private final Anchor byDefault;
    private final Motion slideX;
    private final Motion slideY;

    public AnchorRow(String name, String description, Anchor byDefault, Supplier<Anchor> value, Consumer<Anchor> set) {
        this.name = name;
        this.value = value;
        this.set = set;
        this.byDefault = byDefault;
        int at = indexOf(value.get());
        this.slideX = new Motion(160f, at % 3);
        this.slideY = new Motion(160f, at / 3);
        if (description != null && !description.isEmpty()) {
            tooltip(description);
        }
    }

    public static Anchor at(int index) {
        return GRID[index];
    }

    @Override
    public String name() {
        return name;
    }

    /** @return the chosen cell's column and row, sliding as the anchor changes */
    public float column() {
        return slideX.toward(indexOf(value.get()) % 3);
    }

    public float row() {
        return slideY.toward(indexOf(value.get()) / 3);
    }

    @Override
    protected boolean pressed(float x, float y) {
        for (int i = 0; i < GRID.length; i++) {
            if (inside("cell" + i, x, y)) {
                set.accept(GRID[i]);
                return true;
            }
        }
        return true;
    }

    @Override
    protected void reset() {
        set.accept(byDefault);
    }

    private static int indexOf(Anchor anchor) {
        for (int i = 0; i < GRID.length; i++) {
            if (GRID[i] == anchor) {
                return i;
            }
        }
        return 0;
    }
}
