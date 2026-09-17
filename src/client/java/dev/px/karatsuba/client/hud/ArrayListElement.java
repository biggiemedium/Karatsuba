package dev.px.karatsuba.client.hud;

import dev.px.core.Core;
import dev.px.core.gui.GuiStyle;
import dev.px.core.hud.AbstractHudElement;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.module.Module;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;

import java.util.List;

/**
 * The list of enabled modules.
 *
 * <p>Its width changes as modules toggle and as their display info changes, which
 * is the normal case for a HUD element rather than an edge case &mdash; nothing
 * caches {@code content}, so growing and shrinking needs no invalidation.
 *
 * <p>Settings register by being declared, exactly as on a module, and are persisted
 * alongside the layout.
 */
public final class ArrayListElement extends AbstractHudElement {

    private static final float PADDING = 3f;
    private static final float LINE_GAP = 1f;

    private final BooleanSetting background = bool("Background", true);
    private final BooleanSetting showInfo = bool("Show Info", true)
            .describe("The extra text a module puts after its name, like a mode");
    private final EnumSetting<Tint> tint = enumOf("Tint", Tint.THEME_GRADIENT);

    public ArrayListElement() {
        super("arraylist", "ArrayList", HudLayout.at(Anchor.TOP_RIGHT, -4f, 4f));
    }

    @Override
    public void content(Content content) {
        List<String> entries = entries();
        if (entries.isEmpty()) {
            // Describing nothing measures to nothing, so an empty list takes up no
            // space and catches no clicks. Only reachable outside the editor:
            // entries() substitutes a sample while positioning, which is what keeps
            // an empty ArrayList grabbable.
            return;
        }

        if (background.isOn()) {
            content.background(GuiStyle.surface(), GuiStyle.radius());
        }
        content.padding(PADDING).gap(LINE_GAP);

        // Right-anchored grows leftwards, so the text has to hug the right edge or
        // it detaches from the corner it was placed against.
        content.align(Core.hud().layoutOf(this).getAnchor().isRight() ? Align.END : Align.START);

        for (int i = 0; i < entries.size(); i++) {
            content.text(entries.get(i), colorFor(i, entries.size()));
        }
    }

    private List<String> entries() {
        if (isEditing() && Core.modules().enabled().isEmpty()) {
            // A frozen sample while positioning, so the box is not a sliver.
            return List.of("Sprint", "Fullbright");
        }
        return Core.modules().arrayListEntries().stream()
                .map(showInfo.isOn() ? Module::getDisplayName : Module::getName)
                .toList();
    }

    private Color colorFor(int index, int total) {
        return switch (tint.get()) {
            case THEME_GRADIENT -> GuiStyle.accent(total <= 1 ? 0f : index / (float) (total - 1));
            case ACCENT -> GuiStyle.accent();
            case WHITE -> Color.WHITE;
        };
    }

    public enum Tint {
        THEME_GRADIENT,
        ACCENT,
        WHITE
    }
}
