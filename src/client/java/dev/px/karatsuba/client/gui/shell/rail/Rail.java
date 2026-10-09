package dev.px.karatsuba.client.gui.shell.rail;

import dev.px.core.input.MouseButton;
import dev.px.gui.container.Column;
import dev.px.karatsuba.client.gui.shell.Section;
import dev.px.karatsuba.client.gui.shell.Shell;
import dev.px.karatsuba.client.gui.shell.frame.Spacer;
import dev.px.karatsuba.client.gui.shell.frame.WindowFrame;
import dev.px.karatsuba.client.gui.shell.style.Palette;

/**
 * The navigation rail: one item per section, an icon over its name, the things
 * you work on at the top and the things about the GUI itself at the foot.
 *
 * <p>Always on the left and always still, so wherever you are in the GUI there is
 * one place that says where you are and gets you anywhere else. Labels go once
 * the window is too narrow for them, leaving the icons with their names as
 * tooltips. Dragging the rail anywhere that is not an item moves the window.
 */
public final class Rail extends Column {

    private final WindowFrame frame;
    private boolean labelled = true;

    private float grabX;
    private float grabY;

    public Rail(Shell shell, String clientName, WindowFrame frame) {
        this.frame = frame;
        gap(Palette.GAP_ROW);
        add(new Brand(clientName));
        add(new Spacer(Palette.INSET));
        int number = 1;
        for (Section section : shell.sections()) {
            if (section.group() == Section.Group.TOP) {
                add(new RailItem(section, shell, this, number++));
            }
        }
        add(new Spacer(0f)).grow(1f);
        for (Section section : shell.sections()) {
            if (section.group() == Section.Group.BOTTOM) {
                add(new RailItem(section, shell, this, number++));
            }
        }
    }

    /** @return whether the items show their names under their icons */
    public boolean isLabelled() {
        return labelled;
    }

    public void labelled(boolean show) {
        this.labelled = show;
    }

    /** @return how wide the rail is: narrower with icons alone */
    public float width() {
        return labelled ? Palette.RAIL_WIDTH : Palette.RAIL_WIDTH_COMPACT;
    }

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
