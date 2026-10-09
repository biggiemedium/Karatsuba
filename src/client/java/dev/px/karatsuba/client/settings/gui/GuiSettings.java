package dev.px.karatsuba.client.settings.gui;

import dev.px.core.Core;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.client.gui.shell.style.Animations;
import dev.px.karatsuba.client.settings.ClientSection;

/**
 * How the module GUI behaves: whether it animates, and where its window was
 * left and how big it was, so it opens the same way next time.
 *
 * <p>The window's place and size are saved here rather than shown: they are set
 * by dragging the window, not by typing numbers, so they are hidden settings.
 * A width of zero means "never resized", and the window opens at its default
 * size in the middle of the screen.
 */
public final class GuiSettings extends ClientSection {

    private final BooleanSetting animations = bool("Animations", true)
            .describe("Turn off and the GUI changes instantly, with no motion");

    private final NumberSetting<Double> frameX = hidden(decimal("Frame X", 0d, 0d, 10000d));
    private final NumberSetting<Double> frameY = hidden(decimal("Frame Y", 0d, 0d, 10000d));
    private final NumberSetting<Double> frameWidth = hidden(decimal("Frame Width", 0d, 0d, 10000d));
    private final NumberSetting<Double> frameHeight = hidden(decimal("Frame Height", 0d, 0d, 10000d));

    public GuiSettings() {
        // Not "gui": the legacy click GUI saves its window positions under that id.
        super("interface", "Interface", "How the module GUI looks and moves");
    }

    @Override
    protected void onRegister(Core core) {
        Animations.readFrom(animations::isOn);
    }

    /** @return whether the window has been moved or resized and saved */
    public boolean hasFrame() {
        return frameWidth.getDouble() > 0d && frameHeight.getDouble() > 0d;
    }

    /** @return x, y, width and height, as last saved */
    public float[] frame() {
        return new float[]{frameX.getFloat(), frameY.getFloat(), frameWidth.getFloat(), frameHeight.getFloat()};
    }

    public void saveFrame(float x, float y, float width, float height) {
        frameX.set((double) Math.max(0f, x));
        frameY.set((double) Math.max(0f, y));
        frameWidth.set((double) width);
        frameHeight.set((double) height);
    }

    /** Forgets the window's place and size: it opens at the default again. */
    public void clearFrame() {
        frameWidth.set(0d);
        frameHeight.set(0d);
    }

    private static NumberSetting<Double> hidden(NumberSetting<Double> setting) {
        return setting.visibleWhen(() -> false);
    }
}
