package dev.px.karatsuba.client.gui.stable;

import dev.px.karatsuba.adapter.screen.Gui;
import dev.px.core.Core;
import dev.px.gui.legacy.GuiStyle;
import dev.px.gui.legacy.Panel;
import dev.px.gui.legacy.Screen;
import dev.px.core.render.Render;
import dev.px.karatsuba.client.gui.stable.component.Button;

/**
 * Switches between saved config profiles.
 *
 * <p>A screen is a component that fills the display. It owns the two things only
 * one component can hold &mdash; keyboard focus and the in-flight drag &mdash; and
 * otherwise gets out of the way: {@link #layoutChildren()} is the only method it
 * has to implement, because a panel measures its own height.
 */
public final class ProfilesScreen extends Screen {

    private final Panel body = add(new Panel());

    public ProfilesScreen() {
        super("Profiles");
        body.setBackground(true);
        rebuild();
    }

    /**
     * Rebuilds the list from the config service.
     *
     * <p>Called again after a save, because saving a new profile is the one action
     * here that changes what the list should contain.
     */
    private void rebuild() {
        body.clearChildren();

        for (String profile : Core.config().listProfiles()) {
            body.add(new Button(profile, () -> {
                Core.config().load(profile);
                Core.notifications().success("Profiles", "Loaded " + profile);
            }).tooltip("Load " + profile));
        }

        body.add(new Button("Save current", () -> {
            Core.config().save();
            Core.notifications().success("Profiles", "Saved");
            rebuild();
        }));

        body.add(new Button("Close", () -> Gui.service().close()));
    }

    @Override
    protected void layoutChildren() {
        // Read rather than held in a constant: GuiStyle's metrics are replaceable at
        // runtime, so a field initialised at class-load would go stale.
        float width = GuiStyle.windowWidth() * 1.5f;
        body.layout((getScreenWidth() - width) / 2f, getScreenHeight() / 3f, width);
    }

    /**
     * Dims the world behind the screen.
     *
     * <p>{@code renderBackdrop} rather than content: it has to cover the whole
     * display, and content is measured before the children are stacked so it does
     * not know the final height.
     */
    @Override
    protected void renderBackdrop(float x, float y, float w, float h) {
        // Without this the screen draws over a live world. Core does not dim for you,
        // because the HUD editor is a screen that must not dim.
        Render.rect(0f, 0f, getScreenWidth(), getScreenHeight(), GuiStyle.backdrop());
    }
}
