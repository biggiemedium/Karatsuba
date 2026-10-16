package dev.px.karatsuba.client.gui.stable.shell.look;

import dev.px.core.util.CoreLogger;
import dev.px.gui.render.WidgetRendererRegistry;

/**
 * What the module GUI looks like: one renderer per widget, gathered from one
 * file per part of the GUI. The only place shapes are drawn; colours, sizes and
 * fonts come from {@code Palette}, timings from {@code Animations}.
 *
 * <p>Every control is one row tall with the same inset, so labels line up down
 * the whole list and the eye can run straight down them. Hover is a faint tint
 * that fades in; the accent appears only where something is on or chosen.
 */
public final class ModuleLook {

    private ModuleLook() {
    }

    public static WidgetRendererRegistry build(CoreLogger logger) {
        WidgetRendererRegistry look = new WidgetRendererRegistry(logger);
        FrameLook.register(look);
        RailLook.register(look);
        WidgetLook.register(look);
        ModulesLook.register(look);
        LayoutLook.register(look);
        SettingLook.register(look);
        PopupLook.register(look);
        FieldLook.register(look);
        ThemeLook.register(look);
        FriendsLook.register(look);
        HomeLook.register(look);
        return look;
    }
}
