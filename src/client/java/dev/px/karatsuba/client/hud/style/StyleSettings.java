package dev.px.karatsuba.client.hud.style;

import dev.px.core.render.Color;
import dev.px.core.setting.Setting;
import dev.px.core.setting.Settings;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.client.setting.ChoiceSetting;

import java.util.function.BooleanSupplier;

/**
 * Every setting a style is made of, as one set.
 *
 * <p>Made twice over: once for the global {@link HudStyle}, always shown, and once
 * inside every {@link StyledElement} for its Custom Style, shown only while that is
 * on. One class for both is what keeps an override from ever lacking a setting the
 * global style has.
 *
 * <p>Not a {@code SettingHolder}: the settings are listed by whoever holds this,
 * inside groups of their own, through {@link #plate()} and {@link #text()}.
 */
public final class StyleSettings {

    private final ColorSetting plateColor;
    private final NumberSetting<Integer> plateOpacity;
    private final NumberSetting<Integer> radius;
    private final NumberSetting<Integer> padding;
    private final NumberSetting<Integer> gap;

    private final ChoiceSetting font;
    private final ColorSetting labelColor;
    private final ColorSetting valueColor;
    private final ColorSetting mutedColor;

    /** @param shown whether the settings show at all: always for the global style, while Custom Style is on for an element */
    public StyleSettings(BooleanSupplier shown) {
        plateColor = Settings.color("Plate Colour", Color.of(16, 17, 22)).opaque()
                .describe("The panel behind every element");
        plateColor.visibleWhen(shown);
        plateOpacity = Settings.integer("Plate Opacity", 78, 30, 100)
                .describe("How solid the panel behind every element is, in percent. Never below 30, so text stays readable over bright scenes");
        plateOpacity.visibleWhen(shown);
        radius = Settings.integer("Corner Radius", 3, 0, 10);
        radius.visibleWhen(shown);
        padding = Settings.integer("Padding", 4, 1, 10)
                .describe("Between the panel's edge and what is on it");
        padding.visibleWhen(shown);
        gap = Settings.integer("Row Gap", 2, 0, 8);
        gap.visibleWhen(shown);

        font = new ChoiceSetting("Font", HudFonts::options).labelledBy(HudFonts::label)
                .describe("Default is the client's own. Fonts added to the mod's font folder appear here");
        font.visibleWhen(shown);
        labelColor = Settings.color("Label Colour", Color.of(120, 170, 255)).syncToTheme(true)
                .describe("What a value is, such as \"Ping\". Follows the theme's accent unless changed");
        labelColor.visibleWhen(shown);
        valueColor = Settings.color("Value Colour", Color.WHITE);
        valueColor.visibleWhen(shown);
        mutedColor = Settings.color("Muted Colour", Color.of(150, 160, 180))
                .describe("Secondary text, and the empty part of bars");
        mutedColor.visibleWhen(shown);
    }

    /** @return the panel's settings, for a group of their own */
    public Setting<?>[] plate() {
        return new Setting<?>[]{plateColor, plateOpacity, radius, padding, gap};
    }

    /** @return the text's settings, for a group of their own */
    public Setting<?>[] text() {
        return new Setting<?>[]{font, labelColor, valueColor, mutedColor};
    }

    /** @return the style these settings describe, now */
    public Style resolve() {
        Color plate = plateColor.resolve().withAlpha(plateOpacity.getInt() / 100f);
        return new Style(plate, radius.getInt(), padding.getInt(), gap.getInt(),
                labelColor.resolve(), valueColor.resolve(), mutedColor.resolve(), HudFonts.resolve(font.resolved()));
    }
}
