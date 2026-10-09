package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Content;
import dev.px.core.module.Module;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.gui.legacy.GuiStyle;
import dev.px.karatsuba.client.hud.style.StyledElement;
import dev.px.karatsuba.client.module.ClientModule;

import java.util.Comparator;
import java.util.List;

/**
 * The modules switched on, longest first.
 *
 * <p>Aligned to the side it is anchored to like every
 * other element: anchored right, the names hug the right edge and the list grows
 * leftwards. As modules are switched on and off the list eases to its new size.
 * An empty list takes no space; while positioning, a sample keeps it grabbable.
 */
public final class ArrayListElement extends StyledElement {

    public enum Tint { THEME_GRADIENT, LABEL, VALUE }

    private final BooleanSetting showInfo = bool("Show Info", true)
            .describe("The extra text a module puts after its name, like a mode");
    private final EnumSetting<Tint> tint = enumOf("Tint", Tint.THEME_GRADIENT)
            .describe("A gradient through the theme's accents, or one of the style's text colours");

    public ArrayListElement() {
        super("arraylist", "ArrayList", HudLayout.at(Anchor.TOP_RIGHT, -4f, 4f));
    }

    @Override
    protected void describe(Content body) {
        List<String> entries = entries();
        watch(entries, entries);
        for (int i = 0; i < entries.size(); i++) {
            text(body, entries.get(i), colorFor(i, entries.size()));
        }
    }

    private List<String> entries() {
        if (isEditing() && Core.modules().enabled().isEmpty()) {
            // A frozen sample while positioning, so the box is not a sliver.
            return List.of("Sprint", "Fullbright");
        }
        // Core 2.3 keeps no "visible" flag: the switch is ClientModule's, and the
        // longest-first order the one Core's arrayListEntries() used to give.
        return Core.modules().enabled().stream()
                .filter(module -> !(module instanceof ClientModule client) || client.getVisible().isOn())
                .sorted(Comparator.comparingInt((Module module) -> module.getDisplayName().length()).reversed())
                .map(showInfo.isOn() ? Module::getDisplayName : Module::getName)
                .toList();
    }

    private Color colorFor(int index, int total) {
        return switch (tint.get()) {
            case THEME_GRADIENT -> GuiStyle.accent(total <= 1 ? 0f : index / (float) (total - 1));
            case LABEL -> style().label();
            case VALUE -> style().value();
        };
    }
}
