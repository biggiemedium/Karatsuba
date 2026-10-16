package dev.px.karatsuba.client.gui.apple.shell.setting;

import java.util.List;

/**
 * A name with one or more buttons beside it: "Position: Reset, Edit on screen".
 * For things done once rather than set, so they never look like a setting.
 */
public final class ActionRow extends ControlRow {

    /** One button: what it says and what it does. */
    public record Action(String label, Runnable run) {
    }

    private final String name;
    private final List<Action> actions;

    public ActionRow(String name, String description, List<Action> actions) {
        this.name = name;
        this.actions = List.copyOf(actions);
        if (description != null && !description.isEmpty()) {
            tooltip(description);
        }
    }

    @Override
    public String name() {
        return name;
    }

    public List<Action> actions() {
        return actions;
    }

    /** @return which button the cursor is on, or -1 */
    public int hovered(float x, float y) {
        for (int i = 0; i < actions.size(); i++) {
            if (inside("action" + i, x, y)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected boolean pressed(float x, float y) {
        int at = hovered(x, y);
        if (at >= 0) {
            actions.get(at).run().run();
        }
        return true;
    }

    @Override
    protected void reset() {
        // Nothing to put back: an action is not a value.
    }
}
