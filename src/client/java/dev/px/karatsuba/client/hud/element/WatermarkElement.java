package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.client.hud.style.StyledElement;

/**
 * The client's name, and its version if wanted.
 *
 * <p>The name in the style's label colour, the version muted, on the HUD's plate
 * like every element.
 */
public final class WatermarkElement extends StyledElement {

    private final BooleanSetting showVersion = bool("Show Version", true);

    public WatermarkElement() {
        super("watermark", "Watermark", HudLayout.at(Anchor.TOP_LEFT, 4f, 4f));
    }

    @Override
    protected void describe(Content body) {
        watch("", null);
        body.row(row -> {
            row.align(Align.CENTER);
            label(row, Core.get().getClientName());
            if (showVersion.isOn()) {
                row.space(LABEL_GAP, 0f);
                text(row, Core.get().getClientVersion(), style().muted());
            }
        });
    }
}
