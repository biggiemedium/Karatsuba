package dev.px.karatsuba.client.hud.style;

import dev.px.core.render.Render;
import dev.px.core.render.font.Font;
import dev.px.karatsuba.adapter.asset.Assets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The faces HUD text can be set in: the client's own, then every font bundled in
 * {@code assets/karatsuba/font}.
 *
 * <p>Dropping a {@code .ttf} or {@code .otf} into that folder is all it takes to
 * offer one; nothing lists fonts by hand. Each is loaded the first time something
 * draws in it, at the client font's size, so it lines up with everything else.
 */
public final class HudFonts {

    /** The client's own font, the one the GUI uses; always the first option. */
    public static final String DEFAULT = "Default";

    private static List<String> options;

    private HudFonts() {
    }

    /** @return {@link #DEFAULT}, then each bundled font file */
    public static List<String> options() {
        if (options == null) {
            List<String> all = new ArrayList<>();
            all.add(DEFAULT);
            all.addAll(Assets.fontFiles());
            options = Collections.unmodifiableList(all);
        }
        return options;
    }

    /** @return the option as a person reads it: "mona-sans-italic.ttf" is "Mona Sans Italic" */
    public static String label(String option) {
        if (DEFAULT.equals(option)) {
            return DEFAULT;
        }
        int dot = option.lastIndexOf('.');
        String stem = dot > 0 ? option.substring(0, dot) : option;
        StringBuilder label = new StringBuilder();
        for (String word : stem.split("[-_ ]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    /** @return the font for an option; the client font for {@link #DEFAULT} or anything unknown */
    public static Font resolve(String option) {
        Font base = Render.getDefaultFont();
        if (base == null || DEFAULT.equals(option) || !isFontFile(option)) {
            return base;
        }
        return Assets.fontFile(option, base.getSize());
    }

    private static boolean isFontFile(String option) {
        String lower = option.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }
}
