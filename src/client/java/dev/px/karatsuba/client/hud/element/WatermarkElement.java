package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.Texture;
import dev.px.core.render.font.Font;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.karatsuba.adapter.asset.Assets;
import dev.px.karatsuba.client.hud.preset.color.Contrast;
import dev.px.karatsuba.client.hud.preset.role.TextRole;
import dev.px.karatsuba.client.hud.style.StyledElement;

/**
 * The client's logo: the K mark beside the client's name in SF Pro Semibold, on the
 * HUD's plate like every element.
 *
 * <p>The mark comes in white or black, or follows the plate (Auto: white where the
 * preset's text is light, black where it is dark), and the name with it.
 *
 * <p>The image is large and drawn small, so it loads off the render thread with
 * mipmaps ({@link Assets#backdrop}); until it arrives the element keeps its room, so
 * nothing shifts when it appears.
 */
public final class WatermarkElement extends StyledElement {

    public enum LogoColour { AUTO, WHITE, BLACK }

    private static final String LOGO = Assets.ROOT + "texture/logo/";
    private static final String MARK_WHITE = LOGO + "mark_transparent-white-mono.png";
    private static final String MARK_BLACK = LOGO + "mark_transparent-black-mono.png";

    /** The mark's own proportions, width over height, so its room is right before it loads. */
    private static final float MARK_ASPECT = 896f / 1024f;

    /** How tall the mark is against a line of text. */
    private static final float MARK_LINES = 1.1f;

    /** The name beside the mark: SF Pro's heaviest bundled weight, at the HUD's text size. */
    private static final String NAME_FONT = "sf-pro/sf-pro-semibold.otf";

    private final EnumSetting<LogoColour> colour = enumOf("Logo Colour", LogoColour.AUTO)
            .describe("Auto picks white or black for the plate it sits on");

    public WatermarkElement() {
        super("watermark", "Watermark", HudLayout.at(Anchor.TOP_LEFT, 4f, 4f));
    }

    @Override
    protected void describe(Content body) {
        Font text = tokens().font(TextRole.PRIMARY);
        boolean white = white();
        body.row(row -> {
            row.align(Align.CENTER);
            mark(row, white ? MARK_WHITE : MARK_BLACK, text.getHeight() * MARK_LINES);
            row.space(LABEL_GAP, 0f);
            Font name = Assets.fontFile(NAME_FONT, text.getSize());
            row.text(name, Core.get().getClientName(), ink());
        });
    }

    /** The mark, {@code height} tall at its own proportions, its room kept while it loads. */
    private static void mark(Content into, String path, float height) {
        into.custom(height * MARK_ASPECT, height, (x, y, w, h) -> {
            Texture texture = Assets.backdrop(path);
            if (texture != null) {
                Render.texture(texture, x, y, w, h);
            }
        });
    }

    /** @return whether the mark is white: as set, or at Auto where the preset's text is light */
    private boolean white() {
        return switch (colour.get()) {
            case WHITE -> true;
            case BLACK -> false;
            case AUTO -> {
                Color primary = tokens().text(TextRole.PRIMARY);
                yield Contrast.ratio(primary, Color.BLACK) >= Contrast.ratio(primary, Color.WHITE);
            }
        };
    }

    /**
     * @return the name's colour: the preset's primary text at Auto; at White or Black,
     *         the mark artwork's own colour, which the player chose (hud/DESIGN.md §3.4b)
     */
    private Color ink() {
        return switch (colour.get()) {
            case AUTO -> tokens().text(TextRole.PRIMARY);
            case WHITE -> Color.WHITE;
            case BLACK -> Color.BLACK;
        };
    }
}
