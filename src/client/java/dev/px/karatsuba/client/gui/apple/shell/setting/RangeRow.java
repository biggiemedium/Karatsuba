package dev.px.karatsuba.client.gui.apple.shell.setting;

import dev.px.core.layout.Bounds;
import dev.px.core.math.Range;
import dev.px.core.setting.impl.RangeSetting;

/**
 * A range: like a slider with two knobs. A press moves whichever knob is nearer,
 * and that knob follows the drag; the two never cross.
 */
public final class RangeRow extends SettingRow<RangeSetting> {

    /** Which knob the current drag holds. */
    private boolean upper;

    public RangeRow(RangeSetting setting) {
        super(setting);
    }

    /** @return where {@code value} sits between the setting's limits, 0 to 1 */
    public float progressOf(double value) {
        double span = setting.getMax() - setting.getMin();
        return span <= 0d ? 0f : (float) Math.max(0d, Math.min(1d, (value - setting.getMin()) / span));
    }

    @Override
    protected boolean pressed(float x, float y) {
        Bounds track = part("track");
        if (track == null) {
            return false;
        }
        Range range = setting.get();
        double value = valueAt(track, x);
        upper = Math.abs(value - range.getUpper()) < Math.abs(value - range.getLower())
                || (value > range.getUpper());
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
        if (track == null) {
            return;
        }
        Range range = setting.get();
        double value = valueAt(track, x);
        if (upper) {
            setting.setUpper(Math.max(value, range.getLower()));
        } else {
            setting.setLower(Math.min(value, range.getUpper()));
        }
    }

    private double valueAt(Bounds track, float x) {
        float progress = track.getWidth() <= 0f ? 0f
                : Math.max(0f, Math.min(1f, (x - track.getX()) / track.getWidth()));
        return setting.getMin() + (setting.getMax() - setting.getMin()) * progress;
    }
}
