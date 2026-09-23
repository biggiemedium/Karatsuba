package dev.px.karatsuba.client.module.render;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.ClientLifecycleEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.Options;

/**
 * Turns the brightness up, past where the slider stops.
 *
 * <p>Worth reading for what {@link #onDisable()} does: the module borrows a game
 * setting the player owns, so it remembers the old value and hands it back. A
 * module that changes shared state and does not restore it is the most common
 * way a utility client quietly ruins someone's options file.
 *
 * <p>Handing it back matters more here than for most settings, because
 * {@code options.txt} is written from whatever is in memory when the game shuts
 * down. Disabling covers the ordinary case; {@link #onClientStopping} covers
 * quitting with the module still on, which is otherwise how someone finds their
 * brightness slider pinned to the far end the next time they launch vanilla.
 */
@ModuleInfo(name = "Fullbright", description = "Raises the gamma so caves are lit", category = "Render")
public final class FullbrightModule extends Module {

    private final NumberSetting<Double> gamma = decimal("Gamma", 10d, 1d, 20d)
            .describe("Values above 1 are beyond the vanilla slider, which is the point");

    /** The player's own gamma, held while it is borrowed. Null when it is not. */
    private Double borrowedGamma;

    @Override
    protected void onEnable() {
        borrowedGamma = Options.gamma();
    }

    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        // Applied every tick rather than once on enable: the options screen writes
        // the slider's value straight back, so a single assignment would not stick.
        Options.gamma(gamma.getDouble());
    }

    /**
     * Hands the setting back before the game saves its options.
     *
     * <p>{@code ignoreListening} because a module's handlers go quiet while it is
     * disabled, and this one has to run for a module that is still <em>on</em> at
     * shutdown &mdash; which is exactly the case it exists for. Restoring when
     * nothing was borrowed is a no-op, so the handler is safe to run regardless.
     */
    @Subscribe(ignoreListening = true)
    private void onClientStopping(ClientLifecycleEvent event) {
        if (event.getPhase() == ClientLifecycleEvent.Phase.STOPPING) {
            restore();
        }
    }

    @Override
    protected void onDisable() {
        restore();
    }

    private void restore() {
        if (borrowedGamma != null) {
            Options.gamma(borrowedGamma);
            borrowedGamma = null;
        }
    }

    @Override
    public String getDisplayInfo() {
        return gamma.displayValue();
    }
}
