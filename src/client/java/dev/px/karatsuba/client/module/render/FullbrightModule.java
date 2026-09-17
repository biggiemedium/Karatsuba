package dev.px.karatsuba.client.module.render;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.Options;

/**
 * Turns the brightness up.
 *
 * <p>Worth reading for what {@link #onDisable()} does: the module borrows a game
 * setting the player owns, so it remembers the old value and hands it back. A
 * module that changes shared state and does not restore it is the most common way
 * a utility client quietly ruins someone's options file.
 */
@ModuleInfo(name = "Fullbright", description = "Raises the gamma so caves are lit", category = "Render")
public final class FullbrightModule extends Module {

    private final NumberSetting<Double> gamma = decimal("Gamma", 10d, 1d, 20d)
            .describe("Values above 1 are beyond the vanilla slider, which is the point");

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

    @Override
    protected void onDisable() {
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
