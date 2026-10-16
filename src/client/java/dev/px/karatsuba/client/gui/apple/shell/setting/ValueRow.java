package dev.px.karatsuba.client.gui.apple.shell.setting;

import dev.px.core.layout.Bounds;
import dev.px.karatsuba.client.gui.apple.shell.style.Animations;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/** A number that is not a {@code Setting}: a HUD element's scale, say. Looks like a {@link SliderRow}. */
public final class ValueRow extends ControlRow {

    private final String name;
    private final double min;
    private final double max;
    private final double step;
    private final double byDefault;
    private final DoubleSupplier value;
    private final DoubleConsumer set;
    private final DoubleFunction<String> format;
    private final Motion fill;
    private final Motion grip = new Motion(Animations.SWITCH, 0f);

    public ValueRow(String name, double min, double max, double step, double byDefault,
                    DoubleSupplier value, DoubleConsumer set, DoubleFunction<String> format) {
        this.name = name;
        this.min = min;
        this.max = max;
        this.step = step;
        this.byDefault = byDefault;
        this.value = value;
        this.set = set;
        this.format = format;
        this.fill = new Motion(Animations.SWITCH, progress());
    }

    @Override
    public String name() {
        return name;
    }

    public String text() {
        return format.apply(value.getAsDouble());
    }

    public float progress() {
        return (float) Math.max(0d, Math.min(1d, (value.getAsDouble() - min) / (max - min)));
    }

    public float fill() {
        if (isPressed()) {
            fill.snap(progress());
        }
        return fill.toward(progress());
    }

    public float grip() {
        return grip.toward(isHovered() || isPressed());
    }

    @Override
    protected boolean pressed(float x, float y) {
        if (part("track") == null) {
            return false;
        }
        capture();
        follow(x);
        return true;
    }

    @Override
    protected void mouseDragged(float x, float y) {
        follow(x);
    }

    @Override
    protected void reset() {
        set.accept(byDefault);
    }

    private void follow(float x) {
        Bounds track = part("track");
        if (track == null || track.getWidth() <= 0f) {
            return;
        }
        double t = Math.max(0f, Math.min(1f, (x - track.getX()) / track.getWidth()));
        double raw = min + (max - min) * t;
        set.accept(step > 0d ? Math.round(raw / step) * step : raw);
    }
}
