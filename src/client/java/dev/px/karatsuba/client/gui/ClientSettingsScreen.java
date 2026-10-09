package dev.px.karatsuba.client.gui;

import dev.px.karatsuba.adapter.screen.Gui;
import dev.px.core.Core;
import dev.px.gui.legacy.Component;
import dev.px.gui.legacy.GuiStyle;
import dev.px.gui.legacy.Screen;
import dev.px.core.render.Render;
import dev.px.core.setting.Setting;
import dev.px.karatsuba.client.gui.component.DragWindow;
import dev.px.karatsuba.client.screen.Overlays;
import dev.px.karatsuba.client.settings.ClientSection;
import dev.px.karatsuba.client.settings.ClientSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's own settings, apart from the click GUI: how the whole client
 * behaves, as opposed to what a module does in game.
 *
 * <p>One draggable window per section ({@link ClientSettings#all()}), each holding
 * that section's settings drawn with the click GUI's own rows, so every setting
 * type works and edits save to the config as usual. Windows open side by side and
 * keep where they were dragged for the session.
 */
public final class ClientSettingsScreen extends Screen {

    /** Where each window was left, across openings, by section name. */
    private static final Map<String, float[]> LAST = new HashMap<>();

    private final List<DragWindow> windows = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    public ClientSettingsScreen() {
        super("Client Settings");
        for (ClientSection section : ClientSettings.all()) {
            float[] at = LAST.getOrDefault(section.getName(), new float[]{Float.NaN, Float.NaN});
            DragWindow window = add(new DragWindow(section.getName(), at[0], at[1]));
            for (Setting<?> setting : section.getSettings()) {
                Component row = Gui.service().getRenderers().create(setting);
                if (row != null) {
                    window.add(row);
                }
            }
            windows.add(window);
            names.add(section.getName());
        }
    }

    /** Toggles the screen: closes it if showing, otherwise opens a fresh one. */
    public static void toggle() {
        if (Gui.service().getCurrent() instanceof ClientSettingsScreen) {
            Gui.service().close();
        } else {
            Overlays.clickGui().open(new ClientSettingsScreen());
        }
    }

    @Override
    protected void layoutChildren() {
        float width = GuiStyle.windowWidth() * 1.2f;
        float gap = GuiStyle.padding() * 2f;
        float row = (getScreenWidth() - (width * windows.size() + gap * (windows.size() - 1))) / 2f;
        for (int i = 0; i < windows.size(); i++) {
            DragWindow window = windows.get(i);
            if (Float.isNaN(window.getX())) {
                // First opening: side by side, centred, a sixth of the way down.
                window.moveTo(row + i * (width + gap), getScreenHeight() / 6f);
            }
            window.layout(window.getX(), window.getY(), width);
            LAST.put(names.get(i), new float[]{window.getX(), window.getY()});
        }
    }

    @Override
    protected void renderBackdrop(float x, float y, float w, float h) {
        Render.rect(0f, 0f, getScreenWidth(), getScreenHeight(), GuiStyle.backdrop());
    }
}
