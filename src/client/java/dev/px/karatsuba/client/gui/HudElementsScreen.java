package dev.px.karatsuba.client.gui;

import dev.px.core.Core;
import dev.px.core.gui.Component;
import dev.px.core.gui.GuiStyle;
import dev.px.core.gui.Screen;
import dev.px.core.hud.HudElement;
import dev.px.core.render.Render;
import dev.px.karatsuba.client.config.AutoSave;
import dev.px.karatsuba.client.gui.component.Button;
import dev.px.karatsuba.client.gui.component.DragWindow;
import dev.px.karatsuba.client.gui.component.HudElementButton;
import dev.px.karatsuba.client.screen.Overlays;

/**
 * The HUD's own settings screen, apart from the click GUI: every HUD element in
 * one window, to show or hide and to configure.
 *
 * <ul>
 *   <li><b>Left-click</b> an element to show or hide it.</li>
 *   <li><b>Right-click</b> it for its settings, drawn with the click GUI's own rows.</li>
 *   <li><b>Edit Layout</b> leaves for the drag editor, where elements are moved and
 *       scaled on screen.</li>
 * </ul>
 *
 * <p>The world and the HUD stay visible behind it, lightly dimmed, so a change is
 * seen as it is made. The window's position is kept for the session, so it opens
 * where it was last dragged.
 */
public final class HudElementsScreen extends Screen {

    /** Where the window was left, across openings. NaN until first placed. */
    private static float lastX = Float.NaN;
    private static float lastY = Float.NaN;

    /** Lighter than the usual backdrop: the HUD behind is what is being edited. */
    private static final int DIM_ALPHA = 70;

    private final DragWindow window;

    public HudElementsScreen() {
        super("HUD Elements");
        window = add(new DragWindow("HUD", lastX, lastY));
        build();
    }

    /** Toggles the screen: closes it if showing, otherwise opens a fresh one. */
    public static void toggle() {
        if (Core.gui().getCurrent() instanceof HudElementsScreen) {
            Core.gui().close();
        } else {
            Overlays.clickGui().open(new HudElementsScreen());
        }
    }

    private void build() {
        window.clearChildren();
        for (HudElement element : Core.hud().getElements().all()) {
            window.add(new HudElementButton(element, Core.gui().getRenderers()));
        }
        window.add(new Button("Show All", () -> setAll(true)).tooltip("Show every element"));
        window.add(new Button("Hide All", () -> setAll(false)).tooltip("Hide every element"));
        window.add(new Button("Reset Layout", () -> {
            Core.hud().resetAll();
            AutoSave.markDirty();
        }).tooltip("Put every element back where it started"));
        window.add(new Button("Edit Layout", () -> {
            Core.gui().close();
            Overlays.hud().toggleEditor();
        }).tooltip("Move and scale elements on screen"));
    }

    private void setAll(boolean shown) {
        for (Component child : window.getChildren()) {
            if (child instanceof HudElementButton button) {
                button.setShown(shown);
            }
        }
    }

    @Override
    protected void layoutChildren() {
        float width = GuiStyle.windowWidth() * 1.2f;
        if (Float.isNaN(window.getX())) {
            // First opening: centred across, a sixth of the way down.
            window.moveTo((getScreenWidth() - width) / 2f, getScreenHeight() / 6f);
        }
        window.layout(window.getX(), window.getY(), width);
        lastX = window.getX();
        lastY = window.getY();
    }

    @Override
    protected void renderBackdrop(float x, float y, float w, float h) {
        Render.rect(0f, 0f, getScreenWidth(), getScreenHeight(), GuiStyle.backdrop().withAlpha(DIM_ALPHA));
    }
}
