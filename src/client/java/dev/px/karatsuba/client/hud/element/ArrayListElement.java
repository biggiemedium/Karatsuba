package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.module.Module;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.StringSetting;
import dev.px.karatsuba.client.hud.style.HudStyle;
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
 *
 * <p>Each name is a list item ({@code hud/DESIGN.md} §3.2): its colour is the
 * preset's, by its place in the list. Legacy colours it by its Tint.
 */
public final class ArrayListElement extends StyledElement {

    private final BooleanSetting showInfo = bool("Show Info", true)
            .describe("The extra text a module puts after its name, like a mode");

    /**
     * Where a Tint saved before 2026-10-17 arrives, when Tint was this element's
     * setting: never shown, and emptied once it has been passed to the HUD Style,
     * which now holds Tint for the Legacy preset.
     */
    private final StringSetting movedTint = text("Tint", "");

    public ArrayListElement() {
        super("arraylist", "ArrayList", HudLayout.at(Anchor.TOP_RIGHT, -4f, 4f));
        movedTint.visibleWhen(() -> false);
    }

    /** Names hug the side the list is anchored to, even in a preset that left-aligns (hud/DESIGN.md §4.16). */
    @Override
    protected boolean keepsAnchorAlignment() {
        return true;
    }

    @Override
    protected void describe(Content body) {
        if (!movedTint.get().isEmpty()) {
            // After every section has loaded, whichever order they loaded in.
            HudStyle.get().takeMovedListTint(movedTint.get());
            movedTint.set("");
        }
        List<String> entries = entries();
        watch(entries);
        boolean marked = preset().marksListItems();
        // The marker sits on the side the list hugs: before the name, or after it anchored right.
        boolean trailing = alignment() == Align.END;
        for (int i = 0; i < entries.size(); i++) {
            String name = entries.get(i);
            Color colour = tokens().listItem(i, entries.size());
            if (!marked) {
                text(body, name, colour);
                continue;
            }
            body.row(row -> {
                row.align(Align.CENTER);
                if (!trailing) {
                    preset().listMarker(row, tokens());
                    row.space(LABEL_GAP, 0f);
                }
                text(row, name, colour);
                if (trailing) {
                    row.space(LABEL_GAP, 0f);
                    preset().listMarker(row, tokens());
                }
            });
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
}
