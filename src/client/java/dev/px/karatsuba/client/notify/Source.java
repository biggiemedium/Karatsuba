package dev.px.karatsuba.client.notify;

/**
 * Where a notification comes from, so the player can choose which show
 * ({@code hud/DESIGN.md} §2.13b): one switch per source in the Notifications
 * element's Show group.
 */
public enum Source {
    MODULE_TOGGLES("Module Toggles", "A module switched on or off"),
    MODULE_MESSAGES("Module Messages", "What a module reports, such as Auto Totem refilling or the Recorder saving"),
    TASKS("Tasks", "An automation task finishing, failing or stopping"),
    SERVER("Server", "Lag, the server's software, a detected anticheat"),
    CLIENT("Client", "Profiles, config, accounts, updates and the interface switch");

    private final String label;
    private final String description;

    Source(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** @return the switch's name, which is also its config key */
    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
