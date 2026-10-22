package dev.px.karatsuba.client.settings.mining;

import dev.px.core.Core;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.dig.DigProfile;
import dev.px.karatsuba.client.settings.ClientSection;

/**
 * How every dig goes: Smart Mine's, Ender Chest Farmer's, a task's. The rules
 * {@code Services.digging()} applies to whoever is digging, the way Interaction
 * is the rules for every click.
 *
 * <p>Here rather than on a module because they are the client's way of mining, not
 * one module's: the early allowance they spend is one balance with Grim for the
 * whole connection, and two modules with their own switches would spend it twice.
 */
public final class MiningSettings extends ClientSection implements DigProfile {

    private final BooleanSetting quiet = bool("Quiet Mining", false)
            .describe("Look at the block and swing only as a dig starts and finishes; head and arm are free between");

    private final BooleanSetting earlyBreak = bool("Early Break", false)
            .describe("Finish blocks early, within the allowance Grim gives for finishing early");

    private final NumberSetting<Integer> earlyBudget = integer("Early Budget", 600, 100, 900)
            .step(50)
            .describe("Milliseconds of Grim's 1000 ms early allowance to spend; it refills slowly with on-time blocks")
            .visibleWhen(earlyBreak);

    private final NumberSetting<Double> minProgress = decimal("Min Progress", 0.8d, 0.75d, 0.95d)
            .step(0.05d)
            .describe("Never finish before this share of the dig: the server refuses a finish under 70%")
            .visibleWhen(earlyBreak);

    public MiningSettings() {
        super("mining", "Mining", "How every dig goes: when it looks and swings, and finishing early");
    }

    @Override
    protected void onRegister(Core core) {
        Services.digging().setProfile(this);
    }

    @Override
    public boolean quiet() {
        return quiet.isOn();
    }

    @Override
    public boolean earlyBreak() {
        return earlyBreak.isOn();
    }

    @Override
    public int earlyBudget() {
        return earlyBudget.getInt();
    }

    @Override
    public double minProgress() {
        return minProgress.getDouble();
    }
}
