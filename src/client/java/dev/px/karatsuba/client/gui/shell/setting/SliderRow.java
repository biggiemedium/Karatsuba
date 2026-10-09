package dev.px.karatsuba.client.gui.shell.setting;

import dev.px.core.layout.Bounds;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.client.gui.shell.style.Animations;
import dev.px.karatsuba.client.gui.shell.style.Motion;

/**
 * A number: its value beside its name, where the cursor never covers it, and a
 * track under both. Pressing the track sets the value there and dragging
 * follows; the setting snaps to its own step.
 */
public final class SliderRow extends SettingRow<NumberSetting<?>> {

    /** The fill, eased, so a reset or a config load slides rather than jumps. */
    private final Motion fill;
    private final Motion grip = new Motion(Animations.SWITCH, 0f);

    public SliderRow(NumberSetting<?> setting) {
        super(setting);
        this.fill = new Motion(Animations.SWITCH, setting.progress());
    }

    public String value() {
        return setting.displayValue();
    }

    /** @return the fill, 0 to 1. Follows the cursor directly while dragged. */
    public float fill() {
        if (isPressed()) {
            fill.snap(setting.progress());
        }
        return fill.toward(setting.progress());
    }

    /** @return how big the knob is drawn, 0 resting to 1 hovered or held */
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

    private void follow(float x) {
        Bounds track = part("track");
        if (track != null && track.getWidth() > 0f) {
            setting.setProgress(Math.max(0f, Math.min(1f, (x - track.getX()) / track.getWidth())));
        }
    }
}
