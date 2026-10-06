package dev.px.karatsuba.client.debug;

import dev.px.core.render.Color;
import lombok.Getter;

/** What a {@link DebugAlert} is about, and the colour it is shown in. */
@Getter
public enum AlertType {

    /** The server pulled the player back a short distance: a setback. */
    RUBBERBAND("Rubberband", Color.ORANGE),

    /** The server moved the player further than a setback would: a command, a portal, a plugin. */
    TELEPORT("Teleport", Color.CYAN),

    /** The server stopped responding, and when it came back. */
    LAG("Lag", Color.RED),

    /** The most likely anticheat changed. */
    ANTICHEAT("Anticheat", Color.MAGENTA),

    /** The server pushed the player, and what the client let through. */
    KNOCKBACK("Knockback", Color.YELLOW),

    /** Joining a server, or it announcing its software. */
    SERVER("Server", Color.GRAY);

    private final String label;
    private final Color color;

    AlertType(String label, Color color) {
        this.label = label;
        this.color = color;
    }
}
