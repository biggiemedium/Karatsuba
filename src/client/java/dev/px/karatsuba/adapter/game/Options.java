package dev.px.karatsuba.adapter.game;

import dev.px.karatsuba.adapter.mixin.SimpleOptionAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

/**
 * The game's own settings, for the modules that borrow one.
 *
 * <p>A small seam, and it earns its place for the same reason {@link dev.px.karatsuba.adapter.game.player.Players Players}
 * does: a module that wants the brightness turned up should say so, not reach
 * through {@code MinecraftClient.getInstance().options.getGamma().setValue(...)}
 * and thereby pin itself to one game version's option plumbing.
 *
 * <p>These are the player's settings, not the client's. Anything that changes
 * one is borrowing it and owes it back &mdash; read the old value on enable,
 * restore it on disable. Losing someone's brightness because a module was
 * toggled off is the most common way a utility client quietly ruins an options
 * file, and the borrow is the module's to honour; this class only makes it two
 * obvious calls.
 *
 * <p><b>Game thread only.</b>
 */
public final class Options {

    /** Vanilla's own default, and the floor this will write. */
    private static final double MIN_GAMMA = 0d;

    private Options() {
    }

    /** @return the current gamma, or 1 (the vanilla default) with no game up */
    public static double gamma() {
        GameOptions options = options();
        return options == null ? 1d : options.getGamma().getValue();
    }

    /**
     * Sets the gamma, including past the vanilla slider.
     *
     * <p>Values above 1 are beyond what the options screen allows, which is the
     * entire point of a fullbright &mdash; and it is also why this does not go
     * through {@code setValue}. That method validates against the slider's own
     * range and, for anything outside it, logs
     * {@code "Illegal option value ... for options.gamma"} and assigns the
     * <em>default</em> instead. A module setting gamma every tick would write one
     * error line per tick and change nothing.
     *
     * <p>So the field is written directly, past the validator. See
     * {@link SimpleOptionAccessor} for what that skips.
     *
     * <p>Nothing here writes {@code options.txt}: the value lives in memory and
     * the lightmap reads it fresh each frame. A module still has to restore what
     * it borrowed, because the game saves its options from whatever is in memory
     * when it shuts down.
     */
    public static void gamma(double value) {
        GameOptions options = options();
        if (options == null) {
            return;
        }
        double clamped = Math.max(MIN_GAMMA, value);
        ((SimpleOptionAccessor) (Object) options.getGamma()).karatsuba$setValue(clamped);
    }

    /**
     * @return the mouse sensitivity slider, 0..1, or 0.5 (the vanilla default)
     *         with no game up
     *
     * <p>Read rather than written. Core uses it to round a resolved rotation onto
     * the grid of turns the player's own mouse could actually produce, which it
     * cannot do for itself because it has no options to read.
     */
    public static double mouseSensitivity() {
        GameOptions options = options();
        return options == null ? 0.5d : options.getMouseSensitivity().getValue();
    }

    private static GameOptions options() {
        return MinecraftClient.getInstance().options;
    }
}
