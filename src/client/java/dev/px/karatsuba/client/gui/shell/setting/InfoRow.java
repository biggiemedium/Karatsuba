package dev.px.karatsuba.client.gui.shell.setting;

import java.util.function.Supplier;

/**
 * A fact, not a control: a name on the left and its value, read live, on the
 * right ("Added: 3 May"), with an optional small action beside the value
 * ("Copy").
 *
 * <p>The action's label can be read live too, for a row whose one button changes
 * with what it reports: "Not connected: Connect", "Connected: Disconnect". A label
 * of null hides the button for as long as it stays null.
 */
public final class InfoRow extends ControlRow {

    private final String name;
    private final Supplier<String> value;
    private final Supplier<String> actionLabel;
    private final Runnable action;
    private String description = "";

    public InfoRow(String name, Supplier<String> value) {
        this(name, value, (Supplier<String>) null, null);
    }

    public InfoRow(String name, Supplier<String> value, String actionLabel, Runnable action) {
        this(name, value, actionLabel == null ? null : () -> actionLabel, action);
    }

    public InfoRow(String name, Supplier<String> value, Supplier<String> actionLabel, Runnable action) {
        this.name = name;
        this.value = value;
        this.actionLabel = actionLabel == null ? () -> null : actionLabel;
        this.action = action;
    }

    /** Gives the row a sentence under its name, as a setting's description would be on a detailed row. */
    public InfoRow describe(String text) {
        this.description = text == null ? "" : text;
        return this;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    public String value() {
        return value.get();
    }

    /** @return the action's label now, or null when the row shows no button */
    public String actionLabel() {
        return actionLabel.get();
    }

    @Override
    protected boolean pressed(float x, float y) {
        if (action != null && actionLabel() != null && inside("action", x, y)) {
            action.run();
            return true;
        }
        return false;
    }

    @Override
    protected void reset() {
        // A fact has no default to go back to.
    }
}
