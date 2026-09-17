package dev.px.karatsuba.client.theme;

import dev.px.core.Core;
import dev.px.core.render.Color;
import dev.px.core.render.theme.Theme;

import java.util.List;

/**
 * The accent palette.
 *
 * <p>A Core {@link Theme} is two accent colours plus the roles derived from them,
 * so each entry here is exactly the pair the old client's {@code ColorManager}
 * held: a main colour and an alternative. {@code GuiStyle.accent(progress)} walks
 * between them, which is what gives the click GUI and the ArrayList their gradient.
 *
 * <p>Values are the literal RGB triples from {@code ColorManager}, kept in decimal
 * and in the original order so the two lists stay diffable.
 *
 * <p>{@link #DEFAULT} is registered first and deliberately: when a saved config
 * names a theme that no longer exists, or names nothing at all, Core falls back to
 * the first one registered rather than to whatever was last active.
 */
public final class Themes {

    /** The client's own colours, and the fallback for a config that names no theme. */
    public static final Theme DEFAULT =
            theme("Karatsuba", 122, 162, 247, 187, 154, 247);

    private Themes() {
    }

    /**
     * Registers the default followed by the palette, in order.
     *
     * <p>Call before {@code Core.start()}: the config load inside {@code start()}
     * looks up the saved theme by name, so every theme has to exist by then.
     */
    public static void registerAll(Core core) {
        var themes = core.getThemeService();
        themes.setActive(themes.register(DEFAULT));
        palette().forEach(themes::register);
    }

    /** Every accent pair carried over from the old client, in its original order. */
    public static List<Theme> palette() {
        return List.of(
                theme("Froggy", 179, 248, 129, 91, 225, 115),
                theme("Tenacity", 39, 179, 206, 236, 133, 209),
                theme("Opal", 39, 179, 206, 219, 219, 229),
                theme("Sunset", 253, 145, 21, 245, 106, 230),
                theme("Pink", 226, 0, 70, 255, 166, 200),
                theme("Titanium", 133, 147, 152, 40, 48, 72),
                theme("Blaster", 255, 172, 94, 199, 121, 208),
                theme("Blossom", 226, 208, 249, 49, 119, 115),
                theme("White", 255, 255, 255, 255, 255, 255),
                theme("Neon Dreams", 255, 0, 153, 73, 50, 255),
                theme("Dark Blue", 61, 79, 143, 1, 19, 63),
                theme("Lime", 18, 255, 247, 179, 255, 171),
                theme("Future", 136, 15, 15, 73, 73, 73),
                theme("Orange Juice", 252, 74, 26, 247, 183, 51),
                theme("Watermelon", 236, 68, 155, 153, 244, 67),
                theme("Amethyst", 144, 99, 205, 98, 67, 140),
                theme("Purple Fire", 104, 71, 141, 177, 162, 202),
                theme("Pastel", 255, 109, 106, 191, 82, 80),
                theme("Tropical Ice", 102, 255, 209, 6, 149, 255),
                theme("Magenta", 213, 63, 119, 157, 68, 110),
                theme("Lavender", 219, 166, 247, 152, 115, 172),
                theme("Spearmint", 97, 194, 162, 65, 130, 108),
                theme("Jade Green", 0, 168, 107, 0, 105, 66),
                theme("ThunderHack", 101, 1, 171, 206, 91, 220),

                theme("Cherry Blossom", 255, 183, 197, 255, 123, 137),
                theme("Midnight", 45, 52, 54, 19, 15, 64),
                theme("Golden Hour", 250, 208, 44, 255, 141, 0),
                theme("Arctic", 214, 234, 248, 155, 193, 228),
                theme("Ruby", 224, 17, 95, 165, 0, 52),
                theme("Deep Sea", 0, 119, 182, 3, 4, 94),
                theme("Cosmic", 71, 23, 246, 179, 18, 246),
                theme("Volcano", 252, 70, 107, 63, 94, 251),
                theme("Northern Lights", 4, 225, 148, 128, 64, 252),
                theme("Cotton Candy", 255, 182, 193, 173, 216, 230)

                // Carried over still commented, exactly as they were in ColorManager.
                // Uncomment a line and it is in the GUI on the next launch; a theme
                // nobody has selected costs nothing but the object.
                //
                // theme("Forest", 34, 139, 34, 0, 100, 0),
                // theme("Desert Sand", 237, 201, 175, 189, 154, 130),
                // theme("Emerald Dream", 0, 230, 118, 0, 148, 50),
                // theme("Cyber Punk", 250, 5, 123, 17, 248, 255),
                // theme("Royal Gold", 255, 215, 0, 184, 134, 11),
                // theme("Blood Moon", 178, 31, 31, 253, 187, 45),
                // theme("Electric Violet", 148, 0, 211, 75, 0, 130),
                // theme("Ocean Storm", 0, 180, 216, 72, 149, 239),
                // theme("Molten Core", 255, 69, 0, 255, 140, 0),
                // theme("Matrix", 0, 255, 0, 0, 100, 0),
                // theme("Nebula", 203, 45, 246, 12, 56, 241),
                // theme("Solar Flare", 255, 69, 0, 255, 215, 0),
                // theme("Frozen", 161, 196, 253, 194, 233, 251)
        );
    }

    private static Theme theme(String name,
                               int primaryRed, int primaryGreen, int primaryBlue,
                               int secondaryRed, int secondaryGreen, int secondaryBlue) {
        return Theme.of(name,
                Color.of(primaryRed, primaryGreen, primaryBlue),
                Color.of(secondaryRed, secondaryGreen, secondaryBlue));
    }
}
