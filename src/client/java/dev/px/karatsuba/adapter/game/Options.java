package dev.px.karatsuba.adapter.game;

import net.minecraft.client.MinecraftClient;

/**
 * The game's own settings, for the modules that borrow one.
 *
 * <p>A small seam, and it earns its place for the same reason {@link Players} does:
 * a module that wants the brightness turned up should say so, not reach through
 * {@code MinecraftClient.getInstance().options.getGamma().setValue(...)} and thereby
 * pin itself to one game version's option plumbing.
 *
 * <p>These are the player's settings, not the client's. Anything that changes one
 * is borrowing it and owes it back &mdash; read the old value on enable, restore it
 * on disable. Losing someone's brightness because a module was toggled off is the
 * most common way a utility client quietly ruins an options file, and the borrow is
 * the module's to honour; this class only makes it two obvious calls.
 */
public final class Options {

    private Options() {
    }

    /** @return the current gamma, or 1 (the vanilla default) with no game up */
    public static double gamma() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.options == null ? 1d : client.options.getGamma().getValue();
    }

    /**
     * Sets the gamma.
     *
     * <p>Values above 1 are beyond what the vanilla slider allows, which is the
     * entire point of a fullbright. The game logs a complaint about the value being
     * out of range and then honours it.
     */
    public static void gamma(double value) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            client.options.getGamma().setValue(value);
        }
    }
}
