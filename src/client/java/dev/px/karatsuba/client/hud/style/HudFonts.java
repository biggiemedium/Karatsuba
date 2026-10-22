package dev.px.karatsuba.client.hud.style;

import dev.px.core.render.Render;
import dev.px.core.render.font.Font;
import dev.px.karatsuba.adapter.asset.Assets;
import dev.px.karatsuba.adapter.render.nanovg.NanoVGFont;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The faces HUD text can be set in: the client's own, then every font bundled in
 * {@code assets/karatsuba/font}, one folder per family.
 *
 * <p>Dropping a {@code .ttf} or {@code .otf} into a family's folder there is all it takes to
 * offer one; nothing lists fonts by hand. Each is loaded the first time something
 * draws in it, at the client font's size times Text Size, so it lines up with
 * everything else.
 *
 * <p>No italic-only fonts are offered ({@code hud/DESIGN.md} §2.14). A profile set
 * to one keeps its choice saved but draws in Default, as for any font not there.
 */
public final class HudFonts {

    /** The client's own font, the one the GUI uses; always the first option. */
    public static final String DEFAULT = "Default";

    private static List<String> options;

    /** The client font at each Text Size it has been asked for: the same face, resized. */
    private static final Map<String, Font> SIZED_DEFAULT = new HashMap<>();

    private HudFonts() {
    }

    /** @return {@link #DEFAULT}, then each bundled font file that is not italic */
    public static List<String> options() {
        if (options == null) {
            List<String> all = new ArrayList<>();
            all.add(DEFAULT);
            for (String file : Assets.fontFiles()) {
                if (!file.toLowerCase(Locale.ROOT).contains("italic")) {
                    all.add(file);
                }
            }
            options = Collections.unmodifiableList(all);
        }
        return options;
    }

    /**
     * @return the option a saved value means now: a bare file name saved before the
     *         fonts moved into a folder per family ("inter-italic.ttf") is the bundled
     *         font of that name ("inter/inter-italic.ttf"); anything else as it was
     */
    public static String current(String saved) {
        if (saved == null || saved.indexOf('/') >= 0) {
            return saved;
        }
        for (String option : Assets.fontFiles()) {
            if (option.endsWith("/" + saved)) {
                return option;
            }
        }
        return saved;
    }

    /** @return the option as a person reads it: "mona-sans/mona-sans-italic.ttf" is "Mona Sans Italic" */
    public static String label(String option) {
        if (DEFAULT.equals(option)) {
            return DEFAULT;
        }
        // The file's own name: the family's folder only repeats it.
        String file = option.substring(option.lastIndexOf('/') + 1);
        int dot = file.lastIndexOf('.');
        String stem = dot > 0 ? file.substring(0, dot) : file;
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

    /**
     * @return the font for an option at the HUD's Text Size; the client font for
     *         {@link #DEFAULT} or anything unknown
     */
    public static Font resolve(String option) {
        Font base = Render.getDefaultFont();
        if (base == null) {
            return null;
        }
        HudStyle style = HudStyle.get();
        float size = base.getSize() * (style == null ? 1f : style.textSize().scale());
        if (DEFAULT.equals(option) || !isFontFile(option)) {
            return sized(base, size);
        }
        return Assets.fontFile(option, size);
    }

    /** @return the client font at {@code size}: itself at its own size, else the same face resized */
    private static Font sized(Font base, float size) {
        if (size == base.getSize() || !(base instanceof NanoVGFont face)) {
            return base;
        }
        return SIZED_DEFAULT.computeIfAbsent(base.getName() + "@" + size, key -> face.resized(size));
    }

    private static boolean isFontFile(String option) {
        String lower = option.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }
}
