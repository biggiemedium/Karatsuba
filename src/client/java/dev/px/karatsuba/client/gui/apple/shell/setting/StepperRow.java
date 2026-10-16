package dev.px.karatsuba.client.gui.apple.shell.setting;

import dev.px.core.setting.impl.NumberSetting;

/**
 * A number with few values (a count, a level): its value, and a stepper of − and +
 * beside it ({@code gui/apple/DESIGN.md} §2.5). Each press moves it one step,
 * stopping at either end. Numbers with many values get a slider instead
 * ({@link #suits}).
 */
public final class StepperRow extends SettingRow<NumberSetting<?>> {

    /** The most values a number may have and still be stepped through rather than slid. */
    private static final int MOST_VALUES = 21;

    public StepperRow(NumberSetting<?> setting) {
        super(setting);
    }

    /** @return whether {@code setting} has few enough values to step through */
    public static boolean suits(NumberSetting<?> setting) {
        double step = stepOf(setting);
        double span = setting.getMax().doubleValue() - setting.getMin().doubleValue();
        return step > 0d && span >= 0d && span / step + 1d <= MOST_VALUES;
    }

    public String value() {
        return setting.displayValue();
    }

    public boolean canLower() {
        return setting.getDouble() > setting.getMin().doubleValue();
    }

    public boolean canRaise() {
        return setting.getDouble() < setting.getMax().doubleValue();
    }

    @Override
    protected boolean pressed(float x, float y) {
        if (inside("minus", x, y)) {
            move(-1);
            return true;
        }
        if (inside("plus", x, y)) {
            move(1);
            return true;
        }
        return false;
    }

    private void move(int direction) {
        double next = setting.getDouble() + direction * stepOf(setting);
        double low = setting.getMin().doubleValue();
        double high = setting.getMax().doubleValue();
        double clamped = Math.max(low, Math.min(high, next));
        setting.setProgress(high == low ? 0f : (float) ((clamped - low) / (high - low)));
    }

    /** The setting's step, or 1 for a whole number without one. */
    private static double stepOf(NumberSetting<?> setting) {
        if (setting.getStep() > 0d) {
            return setting.getStep();
        }
        return setting.get() instanceof Integer || setting.get() instanceof Long ? 1d : 0d;
    }
}
